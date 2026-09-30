package com.jev.probe.advisor

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.jev.probe.core.kb.KbStore
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvisorPersistenceUiTest {
    private fun context() = RuntimeEnvironment.getApplication()
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun button(a: Activity, text: String) = views(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == text }
    private fun drainWrites() {
        val barrier = CountDownLatch(1)
        SessionWriter.run({ barrier.countDown(); true })
        assertTrue(barrier.await(5, TimeUnit.SECONDS)); shadowOf(Looper.getMainLooper()).idle()
    }
    @After fun clean() {
        drainWrites(); ManualSession.clear()
        SessionStore.get(context()).clearDraft(); SessionStore.get(context()).clearHistory()
    }
    @Test fun pausingSavesTextAndNewActivityRestoresAfterRamLoss() {
        ManualSession.replace().apply { messages = listOf(ReviewMessage("other", "今天好累")); title = "小林"; ocrDone = true }
        val first = Robolectric.buildActivity(ReviewActivity::class.java, ReviewActivity.intent(context(), ReviewActivity.RESUME)).setup().visible()
        views(first.get().window.decorView).filterIsInstance<EditText>().first { it.text.toString() == "今天好累" }.setText("今天好累，先休息了")
        first.pause().stop().destroy(); drainWrites(); ManualSession.clear()
        val next = Robolectric.buildActivity(ReviewActivity::class.java, ReviewActivity.intent(context(), ReviewActivity.RESUME)).setup().visible()
        assertEquals("小林", ManualSession.current!!.title)
        assertEquals("今天好累，先休息了", ManualSession.current!!.messages.single().text)
        assertFalse(ManualSession.current!!.confirmed)
        next.pause().stop().destroy()
    }
    @Test fun quickContactSaveBindsCurrentChatAndImportScreenDoesNotCallModelOnOpen() {
        ManualSession.replace().apply { title = "小林"; relationship = "同事"; messages = listOf(ReviewMessage("other", "你好")); ocrDone = true }
        val review = Robolectric.buildActivity(ReviewActivity::class.java, ReviewActivity.intent(context(), ReviewActivity.RESUME)).setup().visible()
        button(review.get(), "将当前聊天保存为联系人").performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        shadowOf(Looper.getMainLooper()).idle() // Dialog delivers onShow through its handler.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val contact = KbStore.get(context()).contact(ManualSession.current!!.contactId!!)!!
        assertEquals("小林", contact.name); assertEquals("同事", contact.relationship)
        review.pause().stop().destroy(); drainWrites()
        val importer = Robolectric.buildActivity(ContactImportActivity::class.java).setup().visible()
        button(importer.get(), "模型识别").performClick()
        assertTrue(views(importer.get().window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("请先输入") })
        importer.pause().stop().destroy()
    }
    @Test fun historyCopyAndOpeningClonePreserveOriginalRecord() {
        val result = AdvisorResult("先承接", emptyList(), emptyList(), emptyList(), "等待", "等回应", "拒绝停止",
            listOf(Candidate("承接", "辛苦了"), Candidate("等待", "晚点聊"), Candidate("空间", "先休息吧")))
        val record = SessionRecord.snapshot(ManualSession.replace().apply { title = "测试联系人"; this.result = result; messages = listOf(ReviewMessage("other", "累了")) })
        assertTrue(SessionStore.get(context()).saveResult(record)); ManualSession.clear()
        val history = Robolectric.buildActivity(HistoryActivity::class.java).setup().visible()
        button(history.get(), "复制推荐回复").performClick()
        assertEquals("辛苦了", (context().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString())
        button(history.get(), "查看分析与全部回复").performClick(); drainWrites()
        assertNotEquals(record.id, ManualSession.current!!.id)
        assertEquals(result, ManualSession.current!!.result)
        assertEquals(record, SessionStore.get(context()).history().single())
        history.pause().stop().destroy()
    }
}
