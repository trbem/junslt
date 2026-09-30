package com.jev.probe.advisor

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.jev.probe.padForSystemBars

class AdvisorUi(val activity: Activity) {
    val ink = Color.rgb(24, 45, 47)
    val muted = Color.rgb(98, 119, 118)
    val accent = Color.rgb(17, 109, 105)
    fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    fun page(title: String, subtitle: String): LinearLayout {
        activity.window.decorView.setBackgroundColor(Color.rgb(242, 246, 243))
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            padForSystemBars()
        }
        activity.setContentView(ScrollView(activity).apply { addView(column); isSaveEnabled = false })
        column.addView(text(title, 26f, true))
        column.addView(text(subtitle, 13f).apply { setPadding(0, dp(8), 0, dp(14)) })
        return column
    }
    fun text(value: String, size: Float = 14f, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(if (bold) ink else muted)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
        isSaveEnabled = false
    }
    fun card(parent: LinearLayout, title: String? = null): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        isSaveFromParentEnabled = false
        parent.addView(this)
        if (title != null) addView(text(title, 17f, true))
    }
    fun button(label: String, primary: Boolean = false, click: () -> Unit) = Button(activity).apply {
        text = label; isAllCaps = false; textSize = 14f
        setTextColor(if (primary) Color.WHITE else accent)
        background = rounded(if (primary) accent else Color.rgb(231, 241, 237))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setOnClickListener { click() }
        isSaveEnabled = false
    }
    fun edit(value: String = "", hintValue: String = "", multiline: Boolean = false) = EditText(activity).apply {
        setText(value); hint = hintValue; textSize = 14f; setTextColor(ink)
        inputType = android.text.InputType.TYPE_CLASS_TEXT or if (multiline)
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
        minLines = if (multiline) 2 else 1
        maxLines = if (multiline) 6 else 1
        background = rounded(Color.rgb(244, 247, 245))
        setPadding(dp(10), dp(8), dp(10), dp(8))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        isSaveEnabled = false
    }
    fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }
    fun toast(message: String) { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
    fun copy(text: String) {
        val clip = ClipData.newPlainText("候选回复", text)
        if (Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        toast("已复制，回到微信粘贴后由你发送")
    }
}
