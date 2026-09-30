package com.jev.probe.advisor

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date

class HistoryActivity : AppCompatActivity() {
    private val selection = RequestGate()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        val ui = AdvisorUi(this); val store = SessionStore.get(this)
        val contact = intent.getStringExtra("contactId")
        val page = ui.page("聊天与建议记录", "文字与建议保存在本机，历史不会自动发送给模型。")
        val records = store.history(contact)
        if (store.readFailed) page.addView(ui.text("部分记录无法读取，原文件已保留。"))
        if (records.isEmpty()) page.addView(ui.text("还没有已保存的建议。"))
        records.forEach { r ->
            val card = ui.card(page, r.title.ifBlank { "未关联联系人" })
            card.addView(ui.text(DateFormat.getDateTimeInstance().format(Date(r.updatedAt)), 12f))
            card.addView(ui.text(r.messages.lastOrNull()?.text.orEmpty().take(100)))
            r.result?.candidates?.firstOrNull()?.let { c ->
                card.addView(ui.text("推荐：${c.text}")); card.addView(ui.button("复制推荐回复") { ui.copy(c.text) })
            }
            card.addView(ui.button("查看分析与全部回复") {
                // Save an existing draft before adopting a history snapshot.
                val old = ManualSession.current
                val snapshot = old?.let { SessionRecord.snapshot(it) }
                val request = selection.next()
                SessionWriter.run({ snapshot == null || store.saveDraft(snapshot) }) { ok ->
                    if (isFinishing || isDestroyed || !selection.accepts(request) || ManualSession.current !== old) return@run
                    if (!ok) { ui.toast("当前草稿保存失败，请先处理后再打开记录"); return@run }
                    ManualSession.restore(r)
                    startActivity(ReviewActivity.intent(this, ReviewActivity.RESUME))
                }
            })
            card.addView(ui.button("删除此条记录") {
                AlertDialog.Builder(this).setTitle("删除记录？").setMessage("仅删除这条建议记录，联系人保留。")
                    .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                        SessionWriter.run({ store.deleteResult(r.id) }) { ok ->
                            if (!isDestroyed) { if (!ok) ui.toast("删除失败"); render() }
                        }
                    }.show()
            })
        }
        if (contact == null && records.isNotEmpty()) page.addView(ui.button("清空所有建议记录") {
            AlertDialog.Builder(this).setTitle("清空所有建议记录？").setMessage("联系人、笔记、草稿和接口设置保留。")
                .setNegativeButton("取消", null).setPositiveButton("清空") { _, _ ->
                    SessionWriter.run({ store.clearHistory() }) { ok -> if (!isDestroyed) {
                        if (!ok) ui.toast("部分记录删除失败"); render() } }
                }.show()
        })
    }
    override fun onDestroy() { selection.cancel(); super.onDestroy() }
}
