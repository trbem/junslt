package com.jev.probe.advisor

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import com.jev.probe.MainActivity
import com.jev.probe.SettingsActivity
import com.jev.probe.core.Prefs
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Actual Android Activity/view interaction on the JVM; no model API, OCR native library, or phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvisorActivityTest {
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun texts(activity: Activity) = views(activity.window.decorView).filterIsInstance<TextView>()
    private fun button(activity: Activity, label: String) = texts(activity).filterIsInstance<Button>().first { it.text.toString() == label }
    private fun context() = RuntimeEnvironment.getApplication()
    @After fun clear() { ManualSession.clear(); context().getSharedPreferences(Prefs.PREFS_MAIN, Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun launchesHomeAndModelSettings() {
        val home = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        assertEquals("com.ltrj.chatadvisor", home.get().packageName)
        assertTrue(texts(home.get()).any { it.text.toString() == "聊天军师" })
        home.pause().stop().destroy()
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().visible()
        assertTrue(texts(settings.get()).any { it.text.toString() == "保存接口设置" })
        settings.pause().stop().destroy()
    }
    @Test fun refusesUnconfirmedOrUnknownSpeakerWithoutNetwork() {
        ManualSession.replace().apply { messages = listOf(ReviewMessage("unknown", "今天好累")); ocrDone = true }
        val controller = Robolectric.buildActivity(ReviewActivity::class.java,
            ReviewActivity.intent(context(), ReviewActivity.RESUME)).setup().visible()
        val activity = controller.get()
        button(activity, "确认并生成军师建议").performClick()
        assertTrue(texts(activity).any { it.text.toString().contains("请先确认") })
        texts(activity).filterIsInstance<CheckBox>().first { it.text.toString().startsWith("我已校对") }.isChecked = true
        button(activity, "确认并生成军师建议").performClick()
        assertTrue(texts(activity).any { it.text.toString().contains("请为每条消息选择") })
        controller.pause().stop().destroy()
    }
    @Test fun copiesOnlyReplyAndEditingCancelsConfirmationAndResult() {
        val reply = "你先歇会儿，想聊的时候我在。"
        ManualSession.replace().apply {
            messages = listOf(ReviewMessage("other", "今天好累")); ocrDone = true; confirmed = true
            result = AdvisorResult("累的时候先给空间。", listOf("对方说累"), emptyList(), listOf("关系未知"),
                "先关心", "等待回应", "明确拒绝时停止", listOf(Candidate("承接", reply),
                    Candidate("降压", "今天辛苦了，先休息吧。"), Candidate("关心", "先忙自己的事，我们晚点再聊。")))
        }
        val controller = Robolectric.buildActivity(ReviewActivity::class.java,
            ReviewActivity.intent(context(), ReviewActivity.RESUME)).setup().visible()
        val activity = controller.get()
        button(activity, "复制这条回复").performClick()
        assertEquals(reply, (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString())
        views(activity.window.decorView).filterIsInstance<EditText>().first { it.text.toString() == "今天好累" }.setText("今天好累，先不聊了")
        assertFalse(ManualSession.current!!.confirmed)
        assertNull(ManualSession.current!!.result)
        controller.pause().stop().destroy()
    }
    @Test fun movesAndDeletesMessagesWithoutLosingEdits() {
        ManualSession.replace().apply {
            messages = listOf(ReviewMessage("me", "第一句"), ReviewMessage("other", "第二句")); ocrDone = true
        }
        val controller = Robolectric.buildActivity(ReviewActivity::class.java,
            ReviewActivity.intent(context(), ReviewActivity.RESUME)).setup().visible()
        val activity = controller.get()
        texts(activity).filterIsInstance<Button>().first { it.text.toString() == "下移" }.performClick()
        assertEquals(listOf("第二句", "第一句"), ManualSession.current!!.messages.map { it.text })
        texts(activity).filterIsInstance<Button>().first { it.text.toString() == "删除" }.performClick()
        assertEquals(listOf("第一句"), ManualSession.current!!.messages.map { it.text })
        controller.pause().stop().destroy()
    }
}
