package com.jev.probe.advisor

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.jev.probe.core.Prefs
import com.jev.probe.core.kb.Contact
import com.jev.probe.core.kb.KbStore
import java.util.concurrent.Executors
import java.util.concurrent.Future

class ContactImportActivity : AppCompatActivity() {
    private lateinit var ui: AdvisorUi
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var previews: LinearLayout
    private lateinit var identify: Button
    private val worker = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    private val gate = RequestGate()
    private val draftPrefs by lazy { getSharedPreferences("contact_import_draft", MODE_PRIVATE) }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            cancel(); val id = gate.next(); status.text = "正在读取文本文件…"
            task = worker.submit {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { stream ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                        while (true) { val n = stream.read(buffer); if (n < 0) break
                            out.write(buffer, 0, n); require(out.size() <= ContactText.MAX_BYTES) { "文件过大，请分批导入" } }
                        out.toByteArray()
                    } ?: throw IllegalArgumentException("无法读取文件")
                    val text = ContactText.decode(bytes)
                    runOnUiThread { if (alive(id)) { input.setText(text); status.text = "已读取，请点击模型识别" } }
                } catch (_: Exception) { runOnUiThread { if (alive(id)) status.text = "读取失败，请确认文件不超过256KB、12000字，编码为UTF-8或GB18030；原输入保留" } }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        ui = AdvisorUi(this)
        val page = ui.page("文本智能导入联系人", "粘贴资料或选择 TXT，模型提取后由你核对保存。")
        val card = ui.card(page, "原始资料")
        input = ui.edit(draftPrefs.getString("text", "").orEmpty(), "例如：小林，昵称林林，是我的同事。喜欢爬山。\n阿陈，是大学同学。", true)
        input.maxLines = 12; card.addView(input)
        card.addView(ui.text("点击识别会将这里的文本发送到你配置的回复模型。仅提取原文；结果不是已经核实的事实，保存前请核对。", 12f))
        card.addView(ui.button("选择 TXT 文件") { cancel(); picker.launch(arrayOf("text/plain", "application/octet-stream")) })
        identify = ui.button("模型识别", true) { startIdentify() }; card.addView(identify)
        card.addView(ui.button("取消识别") { cancel(); status.text = "已取消，原输入保留" })
        card.addView(ui.button("清除导入草稿") { cancel(); input.setText(""); draftPrefs.edit().remove("text").apply() })
        status = ui.text(""); card.addView(status)
        previews = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; page.addView(previews)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { cancel(); previews.removeAllViews() }
        })
    }
    private fun alive(id: Long) = !isDestroyed && !isFinishing && gate.accepts(id)
    private fun cancel() { gate.cancel(); task?.cancel(true); if (::identify.isInitialized) identify.isEnabled = true }
    private fun startIdentify() {
        val source = input.text.toString()
        try { ContactText.validate(source) } catch (e: Exception) { status.text = e.message; return }
        if (Prefs(this).effectiveReplyKey().isBlank()) { status.text = "请先在主页配置回复接口密钥"; return }
        draftPrefs.edit().putString("text", source).apply()
        cancel(); val id = gate.next(); identify.isEnabled = false; previews.removeAllViews(); status.text = "模型识别中…"
        task = worker.submit {
            try {
                val contacts = ContactExtractor(Prefs(this)).extract(source)
                runOnUiThread { if (alive(id)) {
                    identify.isEnabled = true
                    status.text = if (contacts.isEmpty()) "未识别到联系人，请补充原文后重试" else "识别到 ${contacts.size} 位，请逐位核对后保存"
                    contacts.forEach { preview(it) }
                } }
            } catch (_: InterruptedException) { }
            catch (e: Exception) { runOnUiThread { if (alive(id)) { identify.isEnabled = true; status.text = safeError(e) + "；原输入保留，可重试" } } }
        }
    }
    private fun preview(c: ImportedContact) {
        val card = ui.card(previews, "核对联系人")
        card.addView(ui.text("原文依据：${c.evidence}", 12f))
        val name = ui.edit(c.name, "姓名或代号（必填）")
        val aliases = ui.edit(c.aliases.joinToString("\n"), "别名，每行一个", true)
        val relationship = ui.edit(c.relationship, "明确关系，可留空", true)
        val notes = ui.edit(c.notes.joinToString("\n"), "备注原文，可编辑或删除", true)
        listOf(name, aliases, relationship, notes).forEach { card.addView(it) }
        val save = ui.button("保存此联系人", true) {
            val label = name.text.toString().trim()
            if (label.isBlank()) { name.error = "请补充名字或代号"; return@button }
            val candidate = Contact(KbStore.newId(), label, aliases.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                relationship = relationship.text.toString().trim(), notes = notes.text.toString().trim())
            val store = KbStore.get(this)
            val names = (candidate.aliases + candidate.name).map { KbStore.normalizeName(it) }.toSet()
            val matches = store.contacts().filter { existing -> (existing.aliases + existing.name).any { KbStore.normalizeName(it) in names } }
            if (matches.isEmpty()) saveContact(candidate, card)
            else AlertDialog.Builder(this).setTitle("发现同名或别名匹配，请选择")
                .setItems((listOf("新增独立联系人（默认）") + matches.map { "合并到：${it.name} · ${it.relationship}" }).toTypedArray()) { _, index ->
                    if (index == 0) saveContact(candidate, card) else mergeDialog(matches[index - 1], candidate, card)
                }.setNegativeButton("取消", null).show()
        }
        card.addView(save); card.addView(ui.button("删除此项预览") { previews.removeView(card) })
    }
    private fun mergeDialog(old: Contact, incoming: Contact, card: LinearLayout) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), 0, ui.dp(16), 0) }
        box.addView(ui.text("已有关系：${old.relationship.ifBlank { "未填写" }}\n导入关系：${incoming.relationship.ifBlank { "未填写" }}\n默认保留已有关系，请核对合并后的备注。", 12f))
        val relation = ui.edit(old.relationship.ifBlank { incoming.relationship }, "最终关系", true)
        val notes = ui.edit(listOf(old.notes, incoming.notes).filter { it.isNotBlank() }.distinct().joinToString("\n"), "合并后的备注", true)
        box.addView(relation); box.addView(notes)
        AlertDialog.Builder(this).setTitle("核对合并到 ${old.name}").setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("取消", null).setPositiveButton("确认合并") { _, _ ->
                saveContact(old.copy(aliases = (old.aliases + incoming.aliases + incoming.name).filter { it != old.name }.distinct(),
                    relationship = relation.text.toString().trim(), notes = notes.text.toString().trim(), updatedAt = System.currentTimeMillis()), card)
            }.show()
    }
    private fun saveContact(c: Contact, card: LinearLayout) {
        if (KbStore.get(this).saveContact(c)) { previews.removeView(card); ui.toast("已保存：${c.name}") }
        else ui.toast("本机保存失败，预览保留，请重试")
    }
    override fun onPause() { draftPrefs.edit().putString("text", input.text.toString()).apply(); super.onPause() }
    override fun onDestroy() { cancel(); worker.shutdownNow(); super.onDestroy() }
}
