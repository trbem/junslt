package com.jev.probe.advisor

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.jev.probe.SettingsActivity
import com.jev.probe.capture.ChatCaptureService
import com.jev.probe.capture.ocr.MlKitOcr
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Msg
import com.jev.probe.core.Prefs
import com.jev.probe.core.kb.ChatContext
import com.jev.probe.core.kb.ContextBuilder
import java.util.concurrent.Executors
import java.util.concurrent.Future

class ReviewActivity : AppCompatActivity() {
    private lateinit var session: ManualSession.Session
    private lateinit var ui: AdvisorUi
    private lateinit var page: LinearLayout
    private lateinit var rows: LinearLayout
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var confirmed: CheckBox
    private lateinit var background: CheckBox
    private lateinit var titleInput: EditText
    private lateinit var relationshipInput: EditText
    private lateinit var goalInput: EditText
    private lateinit var submit: Button
    private var preview: CropPreview? = null
    private val rowInputs = mutableListOf<Pair<Spinner, EditText>>()
    private val worker = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val action = intent.getStringExtra("entry") ?: PASTE
        session = if (savedInstanceState == null && action != CAPTURE && action != RESUME) ManualSession.replace()
            else ManualSession.current ?: ManualSession.replace()
        ui = AdvisorUi(this)
        build()
        if (savedInstanceState == null && action == IMPORT) pickImage()
        if (savedInstanceState == null && action == PASTE) pasteDialog()
        if (session.bitmap != null && !session.ocrDone) recognize()
    }

    private fun build() {
        page = ui.page("校对这一轮聊天", "只分析你确认的原文。先核对说话人，再让军师给建议。")
        val source = ui.card(page, "${session.source} · 本机识别")
        source.addView(ui.button("导入截图") { pickImage() })
        source.addView(ui.button("粘贴或输入聊天文本") { pasteDialog() })
        session.bitmap?.let { bitmap ->
            source.addView(ui.text("在图片上拖出要识别的矩形，避开昵称、时间和输入框。多行气泡可在下方合并编辑。", 12f))
            preview = CropPreview(this, bitmap).also {
                source.addView(it, LinearLayout.LayoutParams(-1, ui.dp(350)))
            }
            source.addView(ui.button("按当前裁剪重新识别") { recognize() })
            source.addView(ui.button("恢复默认裁剪范围") { preview?.reset() })
        }
        val content = ui.card(page, "消息顺序 · 从上到下")
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(rows)
        content.addView(ui.button("添加一条消息") {
            sync(); session.messages = session.messages + ReviewMessage("unknown", "")
            invalidateInput(); renderRows()
        })
        val context = ui.card(page, "可选背景")
        titleInput = ui.edit(session.title, "联系人名称／代号（用于匹配你保存的笔记）").also { context.addView(it) }
        relationshipInput = ui.edit(session.relationship, "关系与经历：不知道可以留空", true).also { context.addView(it) }
        goalInput = ui.edit(session.goal, "本轮目标：例如自然接话、修复、邀约、退出", true).also { context.addView(it) }
        background = CheckBox(this).apply {
            text = "使用我手动保存的联系人与命中笔记"; isChecked = session.useBackground; isSaveEnabled = false
            setOnCheckedChangeListener { _, checked -> session.useBackground = checked; invalidateInput() }
        }
        context.addView(background)
        listOf(titleInput, relationshipInput, goalInput).forEach { it.addTextChangedListener(watcher { invalidateInput() }) }
        val approval = ui.card(page, "确认后提交")
        confirmed = CheckBox(this).apply {
            text = "我已校对内容、顺序，并确认每条消息的我／对方"; isChecked = session.confirmed; isSaveEnabled = false
            setOnCheckedChangeListener { _, value ->
                session.confirmed = value
                if (!value) cancel()
            }
        }
        approval.addView(confirmed)
        approval.addView(ui.text("点击分析会将校对后的文本、填写的背景，以及勾选启用的联系人和笔记发送到你配置的模型接口。不会发送截图，不保存聊天历史。", 12f))
        status = ui.text("", 13f); approval.addView(status)
        submit = ui.button("确认并生成军师建议", true) { analyze() }
        approval.addView(submit)
        approval.addView(ui.button("取消正在处理的请求") {
            cancel(); status.text = "已取消，旧请求结果不会显示"
        })
        approval.addView(ui.button("模型接口设置") { startActivity(Intent(this, SettingsActivity::class.java)) })
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(results)
        page.addView(ui.button("清除本轮内容并退出") { cancel(); ManualSession.clear(); finish() })
        renderRows()
        session.result?.let { showResult(it) }
    }

    private fun renderRows() {
        rows.removeAllViews(); rowInputs.clear()
        session.messages.forEachIndexed { index, message ->
            val box = ui.card(rows)
            val speaker = Spinner(this).apply {
                adapter = ArrayAdapter(this@ReviewActivity, android.R.layout.simple_spinner_dropdown_item,
                    listOf("请选择说话人", "我", "对方"))
                setSelection(when (message.side) { "me" -> 1; "other" -> 2; else -> 0 })
                isSaveEnabled = false
            }
            box.addView(ui.text("第 ${index + 1} 条", 12f, true)); box.addView(speaker)
            val text = ui.edit(message.text, "请输入这条消息", true)
            box.addView(text); rowInputs.add(speaker to text)
            text.addTextChangedListener(watcher { invalidateInput() })
            var previous = speaker.selectedItemPosition
            speaker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != previous) { previous = position; invalidateInput() }
                }
            }
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun action(label: String, operation: () -> Unit) {
                val button = ui.button(label) { sync(); operation(); invalidateInput(); renderRows() }
                button.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = ui.dp(4) }
                buttons.addView(button)
            }
            action("上移") { if (index > 0) move(index, index - 1) }
            action("下移") { if (index < session.messages.lastIndex) move(index, index + 1) }
            action("删除") { session.messages = session.messages.toMutableList().apply { removeAt(index) } }
            box.addView(buttons)
            if (index > 0) box.addView(ui.button("合并到上一条") {
                sync()
                val list = session.messages.toMutableList()
                val previousMessage = list[index - 1]
                list[index - 1] = previousMessage.copy(text = previousMessage.text + "\n" + list[index].text)
                list.removeAt(index); session.messages = list
                invalidateInput(); renderRows()
            })
        }
        if (session.messages.isEmpty()) rows.addView(ui.text("还没有聊天内容。可以导入截图、粘贴文本或手动添加。", 13f))
    }
    private fun move(from: Int, to: Int) {
        session.messages = session.messages.toMutableList().apply { add(to, removeAt(from)) }
    }
    private fun watcher(changed: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { changed() }
    }
    private fun sync() {
        if (!::titleInput.isInitialized) return
        session.title = titleInput.text.toString().trim()
        session.relationship = relationshipInput.text.toString().trim()
        session.goal = goalInput.text.toString().trim()
        session.messages = rowInputs.map { (speaker, text) ->
            ReviewMessage(when (speaker.selectedItemPosition) { 1 -> "me"; 2 -> "other"; else -> "unknown" }, text.text.toString().trim())
        }
    }
    private fun invalidateInput() {
        cancel()
        session.confirmed = false; session.result = null
        if (::confirmed.isInitialized) confirmed.isChecked = false
        if (::results.isInitialized) results.removeAllViews()
    }
    private fun cancel() {
        session.gate.cancel(); task?.cancel(true); task = null; busy = false
        if (::submit.isInitialized) submit.isEnabled = true
    }
    private fun pickImage() {
        cancel()
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
        }, 20)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != 20 || resultCode != RESULT_OK) return
        sync(); invalidateInput()
        val target = session
        val id = target.gate.next(); status.text = "正在本机读取图片…"
        task = worker.submit {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                var sample = 1
                while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 4096) sample *= 2
                val bitmap = contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: throw IllegalArgumentException()
                runOnUiThread {
                    if (!alive(target, id)) { bitmap.recycle(); return@runOnUiThread }
                    session = ManualSession.replace(bitmap, "导入截图")
                    build(); recognize()
                }
            } catch (_: Exception) {
                runOnUiThread { if (alive(target, id)) status.text = "无法读取图片，请换一张截图或粘贴文本" }
            }
        }
    }
    private fun recognize() {
        val bitmap = session.bitmap ?: return
        val region = preview?.region() ?: return
        sync(); invalidateInput()
        val target = session; val id = target.gate.next()
        status.text = "本机识别中…"; submit.isEnabled = false
        val ocr = MlKitOcr()
        ocr.recognize(bitmap, region) ocrCallback@{ lines ->
            if (!alive(target, id)) return@ocrCallback
            submit.isEnabled = true; target.ocrDone = true
            target.messages = OcrReview.messages(lines.map {
                RecognizedLine(it.text, it.bounds.left - region.left, it.bounds.top - region.top,
                    it.bounds.right - region.left, it.bounds.bottom - region.top)
            }, region.width())
            renderRows()
            status.text = if (lines.isEmpty()) "没有识别到文字，请调整裁剪、导入截图或粘贴文本"
                else "已识别 ${lines.size} 行。左右位置仅是提示，请核对说话人、合并多行气泡并删除时间等杂项。"
        }
    }
    private fun pasteDialog() {
        val edit = ui.edit("", "我：你好\n对方：今天好累", true)
        AlertDialog.Builder(this).setTitle("粘贴聊天文本")
            .setMessage("每行一条消息。用“我：”或“对方：”标明说话人；没有前缀的行需要在校对页选择。")
            .setView(edit).setNegativeButton("取消", null).setPositiveButton("使用文本") { _, _ ->
                val messages = PastedChat.parse(edit.text.toString())
                if (messages.isEmpty()) { ui.toast("请先输入聊天内容"); return@setPositiveButton }
                cancel()
                session = ManualSession.replace(source = "粘贴文本").apply { this.messages = messages; ocrDone = true }
                build()
            }.show()
    }
    private fun analyze() {
        sync()
        val error = ReviewValidation.error(session.messages, confirmed.isChecked)
        if (error != null) { status.text = error; return }
        val prefs = Prefs(this)
        if (prefs.effectiveReplyKey().isBlank()) { status.text = "请在模型接口设置中填写回复 API Key"; return }
        if (busy) return
        cancel(); busy = true; submit.isEnabled = false
        val target = session; val id = target.gate.next()
        target.result = null; results.removeAllViews()
        val snapshot = ChatSnapshot(target.title.ifBlank { null }, target.messages.map { Msg(it.side, it.text) })
        val relationship = target.relationship
        val goal = target.goal
        val includeBackground = target.useBackground
        status.text = "正在判断、生成与排序…你可以取消；聊天不会保存。"
        task = worker.submit {
            try {
                // Never append history, even if preferences from older development builds enabled it.
                prefs.contextEnabled = false
                val ctx = if (includeBackground) ContextBuilder.build(applicationContext, snapshot, "com.tencent.mm", prefs)
                    else ChatContext(null, emptyList(), emptyList())
                val result = AdvisorClient(applicationContext, prefs).analyze(snapshot, relationship, goal, ctx)
                runOnUiThread {
                    if (!alive(target, id)) return@runOnUiThread
                    busy = false; submit.isEnabled = true; target.result = result
                    status.text = "已生成 · 使用笔记 ${ctx.notes.size} 条 · 联系人 ${if (ctx.contact == null) "未匹配" else "已匹配"}"
                    showResult(result)
                }
            } catch (_: InterruptedException) { /* Gate prevents late display. */ }
            catch (e: Exception) {
                runOnUiThread {
                    if (!alive(target, id)) return@runOnUiThread
                    busy = false; submit.isEnabled = true; status.text = safeError(e) + "，可点击生成按钮重试"
                }
            }
        }
    }
    private fun showResult(result: AdvisorResult) {
        results.removeAllViews()
        val heading = ui.card(results, "这一句，先这样说")
        result.warnings.forEach { heading.addView(ui.text(it, 12f)) }
        result.judgment?.let { a ->
            val intent = mapOf("confirm_you_care" to "希望确认你是否在意", "vent_anger" to "表达不满或受伤",
                "request_action" to "希望有具体行动", "seek_explanation" to "希望得到解释",
                "casual_chat" to "轻松聊天", "close_topic" to "希望结束这一话题")[a.trueIntent?.choice] ?: "尚不明确"
            heading.addView(ui.text("Jev 提示：对方可能$intent；冲突风险 ${a.dangerLevel?.score?.toInt() ?: "未知"}/${a.dangerLevel?.maxLevel ?: 9}。这不是对对方内心的事实判断。", 12f))
        }
        result.candidates.forEachIndexed { index, candidate ->
            val card = ui.card(results, "${if (index == 0) "推荐" else "备选 ${index + 1}"} · ${candidate.strategy}")
            candidate.probability?.let { card.addView(ui.text("Jev 排序占比 ${(it * 100).toInt()}%（不是成功率）", 12f)) }
            card.addView(ui.text(candidate.text, 17f, true))
            card.addView(ui.button("复制这条回复", index == 0) { ui.copy(candidate.text) })
        }
        val analysis = ui.card(results)
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        analysis.addView(ui.button("展开／收起军师分析") { details.visibility = if (details.visibility == View.GONE) View.VISIBLE else View.GONE })
        fun section(label: String, content: String) {
            details.addView(ui.text(label, 15f, true)); details.addView(ui.text(content.ifBlank { "无" }))
        }
        section("先接住你的情绪", result.emotion)
        section("已知事实", result.facts.joinToString("\n"))
        section("合理推测（未证实）", result.inferences.joinToString("\n"))
        section("关键未知", result.unknowns.joinToString("\n"))
        section("首选建议", result.recommendation)
        section("现在能做什么", result.nextStep)
        section("停止或改变策略的条件", result.stopCondition)
        analysis.addView(details)
        results.addView(ui.button("返回聊天，在悬浮窗看结果", true) {
            ChatCaptureService.instance?.showResult(target = session)
            finish()
        })
        results.post { (page.parent as? ScrollView)?.smoothScrollTo(0, results.top) }
    }
    private fun alive(target: ManualSession.Session, id: Long) = !isDestroyed && !isFinishing && ManualSession.accepts(target, id)
    override fun onPause() { sync(); super.onPause() }
    override fun onDestroy() { cancel(); worker.shutdownNow(); super.onDestroy() }
    companion object {
        const val PASTE = "paste"
        const val IMPORT = "import"
        const val CAPTURE = "capture"
        const val RESUME = "resume"
        fun intent(context: Context, entry: String) = Intent(context, ReviewActivity::class.java).putExtra("entry", entry)
    }
}

object PastedChat {
    fun parse(text: String): List<ReviewMessage> = text.lines().filter { it.isNotBlank() }.map { raw ->
        val match = Regex("^\\s*(我|对方)\\s*[:：]\\s*(.*)$").find(raw)
        if (match == null) ReviewMessage("unknown", raw.trim())
        else ReviewMessage(if (match.groupValues[1] == "我") "me" else "other", match.groupValues[2].trim())
    }
}
