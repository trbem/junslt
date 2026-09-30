package com.jev.probe

import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.jev.probe.advisor.AdvisorUi
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Msg
import com.jev.probe.core.Prefs
import com.jev.probe.jev.JudgeClient
import com.jev.probe.jev.ReplyClient
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var ui: AdvisorUi
    private lateinit var prefs: Prefs
    private lateinit var judgeProvider: Spinner
    private lateinit var judgeBase: EditText
    private lateinit var judgeKey: EditText
    private lateinit var judgeModel: EditText
    private lateinit var replyBase: EditText
    private lateinit var replyKey: EditText
    private lateinit var replyModel: EditText
    private val providers = listOf(Prefs.PROVIDER_OPENROUTER, Prefs.PROVIDER_BOCHA, Prefs.PROVIDER_TYPESAFE,
        Prefs.PROVIDER_VERCEL, Prefs.PROVIDER_ZEN, Prefs.PROVIDER_CUSTOM)
    private val bases = listOf(Prefs.DEFAULT_JUDGE_BASE_OPENROUTER, Prefs.DEFAULT_JUDGE_BASE_BOCHA,
        Prefs.DEFAULT_JUDGE_BASE_TYPESAFE, Prefs.DEFAULT_JUDGE_BASE_VERCEL, Prefs.DEFAULT_JUDGE_BASE_ZEN, "")
    private val models = listOf(Prefs.DEFAULT_JUDGE_MODEL_OPENROUTER, Prefs.DEFAULT_JUDGE_MODEL_BOCHA,
        Prefs.DEFAULT_JUDGE_MODEL_TYPESAFE, Prefs.DEFAULT_JUDGE_MODEL_VERCEL, Prefs.DEFAULT_JUDGE_MODEL_ZEN, "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keys and draft inputs must not enter automatic view state or recents previews.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        ui = AdvisorUi(this); prefs = Prefs(this)
        val page = ui.page("接口设置", "使用自己的 API Key。截图在本机识别，不需要视觉模型。")
        fun field(card: LinearLayout, label: String, value: String, secret: Boolean = false): EditText {
            card.addView(ui.text(label, 13f, true))
            return ui.edit(value).also { edit ->
                if (secret) edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                card.addView(edit)
            }
        }
        val judge = ui.card(page, "判断与排序 · Jev")
        judge.addView(ui.text("可以先只配置回复接口；Jev 未配置或失败时仍能生成军师建议，界面会标明。", 12f))
        judgeProvider = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("OpenRouter", "博查 Jev", "TypeSafe", "Vercel", "OpenCode Zen", "自定义完整端点"))
            setSelection(providers.indexOf(prefs.judgeProvider).coerceAtLeast(0))
        }
        judge.addView(judgeProvider)
        judgeBase = field(judge, "Base URL（自定义时填完整 POST 地址）", prefs.judgeBaseUrl)
        judgeKey = field(judge, "API Key", prefs.judgeKey, true)
        judgeModel = field(judge, "模型", prefs.judgeModel)
        judge.addView(ui.button("应用所选 Jev 预设") {
            val index = judgeProvider.selectedItemPosition
            if (index != 5) { judgeBase.setText(bases[index]); judgeModel.setText(models[index]) }
        })
        val judgeStatus = ui.text("", 12f); judge.addView(judgeStatus)
        judge.addView(ui.button("测试当前填写的判断配置") { test(judgeStatus, true) })

        val reply = ui.card(page, "军师分析与话术 · 生成模型")
        reply.addView(ui.text("支持 /chat/completions 兼容接口。回复 Key 留空时继承判断 Key，地址和模型仍需对应。", 12f))
        replyBase = field(reply, "Base URL（包括 /v1，不含 /chat/completions）", prefs.replyBaseUrl)
        replyKey = field(reply, "API Key", prefs.replyKey, true)
        replyModel = field(reply, "模型", prefs.replyModel)
        val replyPreset = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("OpenRouter", "DeepSeek 官方", "通义兼容"))
        }
        reply.addView(replyPreset)
        reply.addView(ui.button("应用所选回复预设") {
            when (replyPreset.selectedItemPosition) {
                0 -> { replyBase.setText(Prefs.DEFAULT_REPLY_BASE); replyModel.setText(Prefs.DEFAULT_REPLY_MODEL) }
                1 -> { replyBase.setText(Prefs.DEEPSEEK_BASE); replyModel.setText(Prefs.DEEPSEEK_MODEL) }
                2 -> { replyBase.setText(Prefs.DASHSCOPE_BASE); replyModel.setText(Prefs.DASHSCOPE_MODEL) }
            }
        })
        val replyStatus = ui.text("", 12f); reply.addView(replyStatus)
        reply.addView(ui.button("测试当前填写的回复配置") { test(replyStatus, false) })
        page.addView(ui.button("保存接口设置", true) {
            if (!validEndpoints()) return@button
            fill(prefs)
            prefs.contextEnabled = false
            prefs.autoAnalyze = false
            prefs.ocrAutoAnalyze = false
            ui.toast("已保存，下次分析生效")
        })
        page.addView(ui.text("模型费用由所选服务商收取。接口预设仅用于填写地址，不保证模型名称、价格或可用性。", 12f))
    }
    private fun validEndpoints(): Boolean {
        if (listOf(judgeBase.text.toString(), replyBase.text.toString()).any {
            it.isNotBlank() && !it.trim().startsWith("https://")
        }) { ui.toast("请使用 HTTPS 模型接口"); return false }
        return true
    }
    private fun fill(target: Prefs) {
        target.judgeProvider = providers[judgeProvider.selectedItemPosition]
        target.judgeBaseUrl = judgeBase.text.toString()
        target.judgeKey = judgeKey.text.toString()
        target.judgeModel = judgeModel.text.toString()
        target.replyBaseUrl = replyBase.text.toString()
        target.replyKey = replyKey.text.toString()
        target.replyModel = replyModel.text.toString()
    }
    private fun test(status: TextView, judgment: Boolean) {
        if (!validEndpoints()) return
        val name = "advisor_test_${System.nanoTime()}"
        val draft = Prefs(this, name).also { fill(it) }
        status.text = "测试中…（使用示例，不发送你的聊天）"
        worker.execute {
            val result = try {
                if (judgment) {
                    require(draft.judgeKey.isNotBlank()) { "请填写判断密钥" }
                    val a = JudgeClient(draft).judge(ChatSnapshot("接口测试", listOf(Msg("other", "你好"))), "", null)
                    if (a.error != null) a.error
                    else if (a.trueIntent == null || a.dangerLevel == null) "接口返回了不完整的 Jev 判断"
                    else "判断接口已连通"
                } else {
                    require(draft.effectiveReplyKey().isNotBlank()) { "请填写回复密钥" }
                    if (ReplyClient(draft).ping().contains("收到")) "回复接口已连通" else "接口有响应，但测试回复不符合预期"
                }
            } catch (_: InterruptedException) { "测试已取消" }
            catch (e: IllegalArgumentException) { e.message ?: "配置不完整" }
            catch (e: Exception) { com.jev.probe.advisor.safeError(e) }
            finally { getSharedPreferences(name, MODE_PRIVATE).edit().clear().commit(); deleteSharedPreferences(name) }
            runOnUiThread { if (!isDestroyed && !isFinishing) status.text = result }
        }
    }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
