package com.jev.probe

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.jev.probe.advisor.AdvisorUi
import com.jev.probe.advisor.ReviewActivity
import com.jev.probe.capture.ChatCaptureService
import com.jev.probe.core.Prefs

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onResume() {
        super.onResume()
        buildHome()
    }
    private fun buildHome() {
        val ui = AdvisorUi(this)
        val prefs = Prefs(this)
        val page = ui.page("聊天军师", "接住情绪，看清关系，把下一句说得自然。")
        val setup = ui.card(page, "你的聊天副驾")
        setup.addView(ui.text("微信里点击悬浮球 → 校对截图 → 看建议 → 复制回复。\n截图仅在本机识别，确认后才向你配置的模型接口发送文字。"))
        val granted = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.mapNotNull { ComponentName.unflattenFromString(it) }
            ?.contains(ComponentName(this, ChatCaptureService::class.java)) == true
        setup.addView(ui.text("无障碍：${if (granted) "已开启" else "未开启"}　悬浮窗：${if (Settings.canDrawOverlays(this)) "已开启" else "未开启"}\n回复密钥：${if (prefs.effectiveReplyKey().isNotBlank()) "已配置" else "未配置"}", 13f))
        setup.addView(ui.button("${if (prefs.enabled) "关闭" else "开启"}悬浮助手", true) {
            prefs.enabled = !prefs.enabled; buildHome()
        })
        val input = ui.card(page, "先试一段聊天")
        input.addView(ui.button("粘贴聊天文本") { startActivity(ReviewActivity.intent(this, ReviewActivity.PASTE)) })
        input.addView(ui.button("导入已有截图") { startActivity(ReviewActivity.intent(this, ReviewActivity.IMPORT)) })
        input.addView(ui.text("这两个入口不依赖无障碍或悬浮窗权限。", 12f))
        val permission = ui.card(page, "开启微信悬浮入口")
        permission.addView(ui.button("1 · 开启无障碍「聊天军师」") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        permission.addView(ui.button("2 · 允许显示悬浮窗") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        permission.addView(ui.button("3 · 通知、自启动和电池设置") {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11)
            else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        permission.addView(ui.text("微信直接截图需在你的手机验证；系统拒绝时，可导入截图或粘贴文本。", 12f))
        val options = ui.card(page, "配置与资料")
        options.addView(ui.button("模型接口设置") { startActivity(Intent(this, SettingsActivity::class.java)) })
        options.addView(ui.button("我的联系人与笔记") { startActivity(Intent(this, KnowledgeActivity::class.java)) })
        options.addView(ui.button("关于、隐私与开源许可") {
            val licenses = listOf("LICENSE-jev.txt", "NOTICE-jev.txt", "LICENSE-goutoujunshi.txt")
                .joinToString("\n\n") { assets.open("advisor/$it").bufferedReader().use { r -> r.readText() } }
            AlertDialog.Builder(this).setTitle("聊天军师 · 0.1.0")
                .setMessage("基于 Jev 聊天助手与狗头军师二次开发，不代表原作者出品。\n" +
                    "https://github.com/jev-chat/jev-chat-jarvis\nhttps://github.com/shengjidaguai-china/goutoujunshi\n\n" +
                    "截图、识别文本和分析默认仅在内存中；不记录聊天历史。联系人与笔记仅在你手动保存后写入本机。" +
                    "确认分析会把文本、关系背景及启用的笔记发送到你配置的模型服务商，由其政策决定处理方式。" +
                    "密钥存放在 App 私有设置，禁用系统备份。不代发消息。\n\n" + licenses)
                .setPositiveButton("知道了", null).show()
        })
    }
}
