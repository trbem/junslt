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
import com.jev.probe.advisor.ManualSession
import com.jev.probe.advisor.SessionStore
import com.jev.probe.advisor.HistoryActivity
import com.jev.probe.advisor.ContactImportActivity
import com.jev.probe.capture.KeepAliveService
import com.jev.probe.capture.ChatCaptureService
import com.jev.probe.core.Prefs

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra("recoverOverlay", false)) {
            intent.removeExtra("recoverOverlay"); recoverOverlay()
        }
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
        setup.addView(ui.text("悬浮服务：${if (ChatCaptureService.instance != null) "已连接" else "未连接"}" +
            (ChatCaptureService.instance?.lastError?.let { "\n$it" } ?: ""), 13f))
        setup.addView(ui.button("恢复悬浮入口") { recoverOverlay(); buildHome() })
        setup.addView(ui.button("${if (prefs.enabled) "关闭" else "开启"}悬浮助手", true) {
            prefs.enabled = !prefs.enabled
            if (!prefs.enabled) stopService(Intent(this, KeepAliveService::class.java))
            buildHome()
        })
        val input = ui.card(page, "先试一段聊天")
        input.addView(ui.button("粘贴聊天文本") { startActivity(ReviewActivity.intent(this, ReviewActivity.PASTE)) })
        input.addView(ui.button("导入已有截图") { startActivity(ReviewActivity.intent(this, ReviewActivity.IMPORT)) })
        input.addView(ui.text("截图导入存在已知故障，完整修复留待后续版本。", 12f))
        input.addView(ui.button("继续上次聊天草稿") {
            val record = SessionStore.get(this).loadDraft()
            val active = ManualSession.current
            if (record != null && (active == null || (active.messages.isEmpty() && active.title.isBlank() &&
                    active.relationship.isBlank() && active.goal.isBlank() && active.result == null))) ManualSession.restore(record, true)
            if (ManualSession.current == null) android.widget.Toast.makeText(this, "没有可恢复的草稿", android.widget.Toast.LENGTH_SHORT).show()
            else startActivity(ReviewActivity.intent(this, ReviewActivity.RESUME))
        })
        input.addView(ui.button("聊天与建议记录") { startActivity(Intent(this, HistoryActivity::class.java)) })
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
        options.addView(ui.button("文本智能导入联系人") { startActivity(Intent(this, ContactImportActivity::class.java)) })
        options.addView(ui.button("关于、隐私与开源许可") {
            val licenses = listOf("LICENSE-jev.txt", "NOTICE-jev.txt", "LICENSE-goutoujunshi.txt")
                .joinToString("\n\n") { assets.open("advisor/$it").bufferedReader().use { r -> r.readText() } }
            AlertDialog.Builder(this).setTitle("聊天军师 · 0.1.1")
                .setMessage("基于 Jev 聊天助手与狗头军师二次开发，不代表原作者出品。\n" +
                    "https://github.com/jev-chat/jev-chat-jarvis\nhttps://github.com/shengjidaguai-china/goutoujunshi\n\n" +
                    "聊天草稿和建议自动保存在本机，原截图不保存；历史不会自动发送给模型。联系人和笔记经你确认后保存。文本导入使用配置的回复模型。" +
                    "确认分析会把文本、关系背景及启用的笔记发送到你配置的模型服务商，由其政策决定处理方式。" +
                    "密钥存放在 App 私有设置，禁用系统备份。不代发消息。\n\n" + licenses)
                .setPositiveButton("知道了", null).show()
        })
    }
    private fun recoverOverlay() {
        val prefs = Prefs(this); prefs.enabled = true
        val service = ChatCaptureService.instance
        if (service != null) { service.recover(); return }
        runCatching { KeepAliveService.start(this) }
        android.widget.Toast.makeText(this, "悬浮服务尚未连接，请在无障碍设置中重新开启聊天军师", android.widget.Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
