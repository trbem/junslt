package com.jev.probe.capture

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.content.res.Configuration
import android.os.*
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.jev.probe.advisor.*
import com.jev.probe.capture.ocr.ScreenCapture
import com.jev.probe.core.Prefs

/** Reads window identity only. Screenshots and OCR require an explicit user click. */
class ChatCaptureService : AccessibilityService() {
    private lateinit var prefs: Prefs
    private var overlay: BubbleOverlay? = null
    private var screen: ScreenCapture? = null
    private var currentPkg: String? = null
    private var foregroundVerified = false
    private var lastVerifiedAt = 0L
    private var capturing = false
    private var captureHidden = false
    private var destroyed = false
    private var pendingResult: ManualSession.Session? = null
    private val main = Handler(Looper.getMainLooper())
    private val chats = setOf("com.tencent.mm", "com.tencent.mobileqq", "com.twitter.android", "com.ss.android.lark")
    var lastError: String? = null; private set
    private val reconcile = Runnable { updateVisibility() }
    private val heartbeat = object : Runnable {
        override fun run() { if (!destroyed && ::prefs.isInitialized && prefs.enabled) {
            updateVisibility(); main.postDelayed(this, 1500)
        } }
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "enabled") recover()
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this; destroyed = false; prefs = Prefs(this)
        prefs.contextEnabled = false; prefs.autoAnalyze = false; prefs.ocrAutoAnalyze = false
        getSharedPreferences(Prefs.PREFS_MAIN, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
        overlay?.destroy()
        overlay = BubbleOverlay(this, prefs, { capture() }, { open(it) })
        screen = ScreenCapture(this, { captureHidden = true; overlay?.hideForCapture() }, {
            captureHidden = false; updateVisibility()
        })
        recover()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::prefs.isInitialized || event == null || event.eventType !in listOf(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED)) return
        main.removeCallbacks(reconcile); main.postDelayed(reconcile, 100)
    }
    @Suppress("DEPRECATION")
    private fun refreshPackage() {
        foregroundVerified = false
        val apps = runCatching { windows }.getOrDefault(emptyList()).mapNotNull { window ->
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@mapNotNull null
            val root = runCatching { window.root }.getOrNull() ?: return@mapNotNull null
            try { root.packageName?.toString()?.let { AppWindow(it, window.layer, window.isFocused) } }
            finally { root.recycle() }
        }
        currentPkg = ForegroundPolicy.resolve(apps, currentPkg)
        if (apps.isNotEmpty()) foregroundVerified = true
        if (apps.isEmpty()) {
            val root = runCatching { rootInActiveWindow }.getOrNull()
            if (root != null) try {
                if (root.window?.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    currentPkg = root.packageName?.toString()
                    foregroundVerified = currentPkg != null
                }
            } finally { root.recycle() }
        }
        if (foregroundVerified) lastVerifiedAt = SystemClock.elapsedRealtime()
        else if (SystemClock.elapsedRealtime() - lastVerifiedAt > 2000) currentPkg = null
    }
    private fun awake(): Boolean = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive &&
        !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
    private fun updateVisibility() {
        if (destroyed || !::prefs.isInitialized) return
        refreshPackage()
        if (prefs.enabled && Settings.canDrawOverlays(this) && currentPkg in chats && awake()) {
            if (captureHidden) overlay?.hideForCapture() else {
                val ok = overlay?.show() == true
                lastError = if (ok) null else "悬浮窗口挂载失败，可从主页恢复入口"
                pendingResult?.let { target -> pendingResult = null
                    if (ManualSession.current === target) target.result?.let { overlay?.result(it) }
                }
            }
        } else overlay?.hide()
    }
    fun recover() {
        if (destroyed || !::prefs.isInitialized) return
        main.removeCallbacks(heartbeat)
        if (prefs.enabled) {
            runCatching { KeepAliveService.start(this) }.onFailure { lastError = "常驻通知服务未启动，请从主页重试" }
            main.post(heartbeat)
        } else { stopService(Intent(this, KeepAliveService::class.java)); updateVisibility() }
    }
    private fun capture() {
        if (capturing) { overlay?.menu("正在识别，请稍候"); return }
        refreshPackage(); val pkg = currentPkg
        if (!prefs.enabled || pkg !in chats || !awake() || !foregroundVerified) { overlay?.menu("请先打开聊天界面"); return }
        val activeBefore = ManualSession.current
        capturing = true
        screen?.capture(shouldCapture = {
            refreshPackage(); !destroyed && prefs.enabled && currentPkg == pkg && awake() && foregroundVerified
        }) screenshotCallback@{ result ->
            captureHidden = false; refreshPackage()
            if (destroyed || !prefs.enabled || currentPkg != pkg || !awake() || ManualSession.current !== activeBefore) {
                capturing = false
                if (result is ScreenCapture.Result.Ok) result.bitmap.recycle()
                updateVisibility(); return@screenshotCallback
            }
            when (result) {
                is ScreenCapture.Result.Ok -> {
                    val old = ManualSession.current?.let { SessionRecord.snapshot(it) }
                    SessionWriter.run({ ManualSession.current !== activeBefore || old == null || SessionStore.get(this).saveDraft(old) }) { ok ->
                        capturing = false
                        refreshPackage()
                        if (destroyed || currentPkg != pkg || !prefs.enabled || ManualSession.current !== activeBefore) { result.bitmap.recycle(); return@run }
                        if (!ok) { result.bitmap.recycle(); overlay?.menu("当前草稿保存失败，已保留原会话"); return@run }
                        pendingResult = null
                        ManualSession.replace(result.bitmap, "点击悬浮球截图")
                        open(ReviewActivity.CAPTURE)
                    }
                }
                is ScreenCapture.Result.Failed -> { capturing = false; overlay?.menu(result.humanMessage + "。可粘贴文本；截图导入待修复。") }
            }
        }
    }
    private fun open(entry: String) {
        overlay?.hide()
        runCatching { startActivity(ReviewActivity.intent(this, entry)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
            .onFailure { overlay?.show(); overlay?.menu("无法打开校对页，请从聊天军师主页进入") }
    }
    fun showResult(target: ManualSession.Session) { pendingResult = target }
    override fun onInterrupt() { main.postDelayed(reconcile, 200) }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig); overlay?.destroy(); main.post(reconcile)
    }
    override fun onDestroy() {
        destroyed = true; main.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        if (::prefs.isInitialized) getSharedPreferences(Prefs.PREFS_MAIN, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(listener)
        overlay?.destroy(); overlay = null
        super.onDestroy()
    }
    companion object { @Volatile var instance: ChatCaptureService? = null; private set }
}
