package com.jev.probe.capture

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.jev.probe.advisor.BubbleOverlay
import com.jev.probe.advisor.ManualSession
import com.jev.probe.advisor.ReviewActivity
import com.jev.probe.capture.ocr.ScreenCapture
import com.jev.probe.core.Prefs

/** Standard, honestly named service. Events track foreground package only; never analyze new messages. */
class ChatCaptureService : AccessibilityService() {
    private lateinit var prefs: Prefs
    private var overlay: BubbleOverlay? = null
    private var screen: ScreenCapture? = null
    private var currentPkg: String? = null
    private var capturing = false
    private var destroyed = false
    private var pendingResult: ManualSession.Session? = null
    private val chats = setOf("com.tencent.mm", "com.tencent.mobileqq", "com.twitter.android", "com.ss.android.lark")
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "enabled") {
            if (prefs.enabled) runCatching { KeepAliveService.start(this) }
            updateVisibility()
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this; destroyed = false; prefs = Prefs(this)
        prefs.contextEnabled = false; prefs.autoAnalyze = false; prefs.ocrAutoAnalyze = false
        getSharedPreferences(Prefs.PREFS_MAIN, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
        overlay = BubbleOverlay(this, prefs, { capture() }, { open(it) })
        screen = ScreenCapture(this, { overlay?.hideForCapture() }, {
            if (!destroyed && currentPkg in chats && prefs.enabled) overlay?.restoreAfterCapture()
            else overlay?.hide()
        })
        currentPkg = rootInActiveWindow?.packageName?.toString()
        updateVisibility()
        if (prefs.enabled) runCatching { KeepAliveService.start(this) }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::prefs.isInitialized || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Overlay windows must not overwrite the underlying app identity.
        if (event.className?.toString() == "android.widget.PopupWindow") return
        currentPkg = event.packageName?.toString()
        updateVisibility()
    }
    private fun updateVisibility() {
        if (!prefs.enabled) stopService(Intent(this, KeepAliveService::class.java))
        if (!destroyed && prefs.enabled && Settings.canDrawOverlays(this) && currentPkg in chats) {
            overlay?.show()
            pendingResult?.let { target ->
                pendingResult = null
                if (ManualSession.current === target) target.result?.let { overlay?.result(it) }
            }
        } else overlay?.hide()
    }
    private fun capture() {
        if (capturing) { overlay?.menu("正在识别，请稍候"); return }
        val pkg = currentPkg
        if (!prefs.enabled || pkg !in chats) { overlay?.menu("请先打开微信、QQ、X 或飞书的聊天界面"); return }
        // Invalidate the previous analysis before a new capture starts, including a failed capture.
        ManualSession.clear(); pendingResult = null
        capturing = true
        screen?.capture(shouldCapture = { !destroyed && prefs.enabled && currentPkg == pkg }) screenshotCallback@{ result ->
            capturing = false
            if (destroyed || !prefs.enabled || currentPkg != pkg) {
                if (result is ScreenCapture.Result.Ok) result.bitmap.recycle()
                return@screenshotCallback
            }
            when (result) {
                is ScreenCapture.Result.Ok -> {
                    ManualSession.replace(result.bitmap, "点击悬浮球截图")
                    open(ReviewActivity.CAPTURE)
                }
                is ScreenCapture.Result.Failed -> overlay?.menu(result.humanMessage + "。可导入已有截图或粘贴文本。")
            }
        }
    }
    private fun open(entry: String) {
        overlay?.hide()
        runCatching {
            startActivity(ReviewActivity.intent(this, entry).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }.onFailure { overlay?.show(); overlay?.menu("无法打开校对页，请从聊天军师主页导入截图或粘贴文本") }
    }
    fun showResult(target: ManualSession.Session) { pendingResult = target }
    override fun onInterrupt() { overlay?.hide() }
    override fun onDestroy() {
        destroyed = true
        if (instance === this) instance = null
        getSharedPreferences(Prefs.PREFS_MAIN, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(listener)
        overlay?.destroy(); overlay = null
        stopService(Intent(this, KeepAliveService::class.java))
        super.onDestroy()
    }
    companion object { @Volatile var instance: ChatCaptureService? = null; private set }
}
