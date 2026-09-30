package com.jev.probe.advisor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import com.jev.probe.core.Prefs

/** Manual-only overlay. Contains no write-to-input or send callback. */
class BubbleOverlay(private val context: Context, private val prefs: Prefs,
                    private val capture: () -> Unit, private val open: (String) -> Unit) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubble: TextView? = null
    private var panel: View? = null
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    private fun params(width: Int = dp(54), height: Int = dp(54)) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
    private fun round(color: Int, radius: Int = 14) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    fun show() {
        if (bubble != null) { bubble?.visibility = View.VISIBLE; return }
        val metrics = context.resources.displayMetrics
        val lp = params().apply {
            x = prefs.bubbleX.takeIf { it >= 0 }?.coerceIn(0, metrics.widthPixels - dp(54)) ?: metrics.widthPixels - dp(64)
            y = prefs.bubbleY.takeIf { it >= 0 }?.coerceIn(dp(36), metrics.heightPixels - dp(100)) ?: dp(170)
        }
        val view = TextView(context).apply {
            text = "军师"; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
            background = round(Color.rgb(17, 109, 105), 28)
            elevation = dp(8).toFloat(); contentDescription = "点击识别当前屏幕，长按打开军师菜单"
            setOnClickListener { closePanel(); capture() }
            setOnLongClickListener { menu(); true }
        }
        var startX = 0f; var startY = 0f; var baseX = 0; var baseY = 0; var dragging = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = event.rawX; startY = event.rawY; baseX = lp.x; baseY = lp.y; dragging = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX; val dy = event.rawY - startY
                    if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                        dragging = true; view.cancelLongPress()
                        lp.x = (baseX + dx.toInt()).coerceIn(0, (metrics.widthPixels - dp(54)).coerceAtLeast(0))
                        lp.y = (baseY + dy.toInt()).coerceIn(dp(36), (metrics.heightPixels - dp(100)).coerceAtLeast(dp(36)))
                        runCatching { manager.updateViewLayout(view, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> if (dragging) { prefs.bubbleX = lp.x; prefs.bubbleY = lp.y; return@setOnTouchListener true }
            }
            dragging
        }
        try { manager.addView(view, lp); bubble = view } catch (_: Exception) { bubble = null }
    }
    fun hide() { bubble?.visibility = View.GONE; closePanel() }
    fun hideForCapture() { bubble?.visibility = View.INVISIBLE; panel?.visibility = View.INVISIBLE }
    fun restoreAfterCapture() { bubble?.visibility = View.VISIBLE; panel?.visibility = View.VISIBLE }
    fun closePanel() { panel?.let { runCatching { manager.removeView(it) } }; panel = null }
    fun destroy() { closePanel(); bubble?.let { runCatching { manager.removeView(it) } }; bubble = null }
    private fun text(value: String, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = if (bold) 16f else 13f; setTextColor(Color.rgb(24, 45, 47))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }
    private fun button(value: String, action: () -> Unit) = Button(context).apply {
        text = value; isAllCaps = false; textSize = 13f
        setTextColor(Color.rgb(17, 109, 105)); setOnClickListener { action() }
    }
    private fun panel(title: String, body: (LinearLayout) -> Unit) {
        closePanel()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
            background = round(Color.WHITE); alpha = prefs.overlayOpacity / 100f
        }
        column.addView(text(title, true))
        column.addView(button("收起") { closePanel() })
        body(column)
        val scroll = ScrollView(context).apply { addView(column) }
        val lp = params((context.resources.displayMetrics.widthPixels - dp(32)).coerceAtMost(dp(380)),
            (context.resources.displayMetrics.heightPixels * .55).toInt()).apply { x = dp(16); y = dp(60) }
        runCatching { manager.addView(scroll, lp); panel = scroll }
    }
    fun menu(message: String = "长按菜单 · 每次识别均由你触发") = panel("聊天军师") { column ->
        column.addView(text(message))
        column.addView(button("识别当前屏幕") { closePanel(); capture() })
        column.addView(button("导入已有截图") { closePanel(); open(ReviewActivity.IMPORT) })
        column.addView(button("粘贴聊天文本") { closePanel(); open(ReviewActivity.PASTE) })
        ManualSession.current?.let { s ->
            column.addView(button("校对／重试本轮") { closePanel(); open(ReviewActivity.RESUME) })
            s.result?.let { r -> column.addView(button("查看本轮结果") { result(r) }) }
        }
    }
    fun result(result: AdvisorResult) = panel("这一句，先这样说") { column ->
        result.warnings.forEach { column.addView(text(it)) }
        result.candidates.forEachIndexed { index, candidate ->
            column.addView(text("${if (index == 0) "推荐" else "备选"} · ${candidate.strategy}", true))
            column.addView(text(candidate.text))
            column.addView(button("复制回复") {
                val clip = ClipData.newPlainText("候选回复", candidate.text)
                if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                Toast.makeText(context, "已复制，请手动粘贴并发送", Toast.LENGTH_SHORT).show()
                closePanel()
            })
        }
        val analysis = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        analysis.addView(text(result.emotion))
        analysis.addView(text("已知事实\n" + result.facts.joinToString("\n")))
        analysis.addView(text("推测（未证实）\n" + result.inferences.joinToString("\n")))
        analysis.addView(text("关键未知\n" + result.unknowns.joinToString("\n")))
        analysis.addView(text("建议\n" + result.recommendation))
        analysis.addView(text("下一步\n" + result.nextStep + "\n停止条件\n" + result.stopCondition))
        column.addView(button("展开／收起军师分析") { analysis.visibility = if (analysis.visibility == View.GONE) View.VISIBLE else View.GONE })
        column.addView(analysis)
        column.addView(button("校对／重新生成") { closePanel(); open(ReviewActivity.RESUME) })
        column.addView(button("识别下一轮") { closePanel(); capture() })
    }
}
