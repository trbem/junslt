package com.jev.probe.advisor

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jev.probe.MainActivity
import com.jev.probe.SettingsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Anonymous in-process fixtures only. These tests never contact a model service. */
@RunWith(AndroidJUnit4::class)
class AdvisorUiTest {
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun texts(activity: Activity) = views(activity.window.decorView).filterIsInstance<TextView>()
    private fun button(activity: Activity, text: String) = texts(activity).filterIsInstance<Button>().first { it.text.toString() == text }
    private fun capture(activity: Activity, name: String) {
        val view = activity.window.decorView
        if (view.width == 0 || view.height == 0) return
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val dir = File(activity.getExternalFilesDir(null), "qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    @Test fun homeAndSettingsLaunchWithExpectedIdentity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(texts(activity).any { it.text.toString() == "聊天军师" })
                assertEquals("com.ltrj.chatadvisor", activity.packageName)
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { capture(it, "home") }
        }
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(texts(activity).any { it.text.toString() == "保存接口设置" })
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { capture(it, "settings") }
        }
    }
    @Test fun unconfirmedConversationCannotSubmitAndUnknownSpeakerStaysBlocked() {
        ManualSession.replace().apply { messages = listOf(ReviewMessage("unknown", "今天好累")); ocrDone = true }
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<ReviewActivity>(ReviewActivity.intent(context, ReviewActivity.RESUME)).use { scenario ->
            scenario.onActivity { activity ->
                button(activity, "确认并生成军师建议").performClick()
                assertTrue(texts(activity).any { it.text.toString().contains("请先确认") })
                texts(activity).filterIsInstance<CheckBox>().first { it.text.toString().startsWith("我已校对") }.isChecked = true
                button(activity, "确认并生成军师建议").performClick()
                assertTrue(texts(activity).any { it.text.toString().contains("请为每条消息选择") })
            }
        }
        ManualSession.clear()
    }
    @Test fun resultCopiesOnlyCandidateBodyAndEditingInvalidatesIt() {
        val reply = "今天辛苦了，你先好好休息。"
        ManualSession.replace().apply {
            messages = listOf(ReviewMessage("other", "今天好累")); ocrDone = true; confirmed = true
            result = AdvisorResult("累的时候可以先给空间。", listOf("对方说今天累"), listOf("可能想休息"),
                listOf("关系阶段未知"), "先关心，别追问。", "发一条后等待。", "对方拒绝时停止联系。",
                listOf(Candidate("承接", reply), Candidate("降压", "先歇会儿，想聊的时候再找我。"), Candidate("关心", "今天挺辛苦的，早点休息。")),
                warnings = listOf("测试样例 · 非真实模型结果"))
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<ReviewActivity>(ReviewActivity.intent(context, ReviewActivity.RESUME)).use { scenario ->
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(500)
            scenario.onActivity { activity ->
                capture(activity, "result-fixture")
                button(activity, "复制这条回复").performClick()
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                assertEquals(reply, clipboard.primaryClip!!.getItemAt(0).text.toString())
                val inputs = views(activity.window.decorView).filterIsInstance<android.widget.EditText>()
                inputs.first { it.text.toString() == "今天好累" }.setText("今天好累，先不聊了")
                assertNull(ManualSession.current?.result)
                assertFalse(ManualSession.current!!.confirmed)
            }
        }
        ManualSession.clear()
    }
}
