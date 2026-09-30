package com.jev.probe.advisor

import com.jev.probe.core.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionStoreTest {
    private val store get() = SessionStore.get(RuntimeEnvironment.getApplication())
    private fun clean() {
        val parent = RuntimeEnvironment.getApplication().filesDir.canonicalPath + File.separator
        check(store.root.canonicalPath.startsWith(parent))
        store.root.deleteRecursively()
    }
    @Before fun before() { clean(); ManualSession.clear() }
    @After fun after() { clean(); ManualSession.clear() }
    private fun result() = AdvisorResult("先缓一缓", listOf("对方说累"), listOf("可能需要休息"), listOf("关系未知"),
        "先承接", "等回复", "拒绝时停止", listOf(Candidate("承接", "先休息吧", .6), Candidate("等待", "晚点聊", .3), Candidate("关心", "辛苦了", .1)),
        Analysis(Choice("casual_chat", .6, mapOf("casual_chat" to .6)), Score(1.0, .8, 9), null, .5, null, null, null, emptyList(), 200), listOf("测试提示"))
    @Test fun diskRoundTripSurvivesRamLossAndPreservesFullResult() {
        val session = ManualSession.replace().apply { title = "小林"; messages = listOf(ReviewMessage("other", "今天好累")); result = result() }
        val record = SessionRecord.snapshot(session)
        assertTrue(store.saveDraft(record)); assertTrue(store.saveResult(record))
        ManualSession.clear()
        val loaded = store.loadDraft()!!
        assertEquals(record, loaded)
        ManualSession.restore(loaded, true)
        assertEquals(record.id, ManualSession.current!!.id)
        assertEquals(record.result, ManualSession.current!!.result)
        assertFalse(ManualSession.current!!.confirmed)
        assertEquals(1, store.history().size)
    }
    @Test fun editingAndReanalysisKeepPreviousHistory() {
        val first = SessionRecord.snapshot(ManualSession.replace().apply { result = result() })
        assertTrue(store.saveResult(first))
        val next = first.copy(id = UUID.randomUUID().toString(), messages = listOf(ReviewMessage("me", "新一轮")))
        assertTrue(store.saveResult(next)); assertEquals(2, store.history().size)
        assertTrue(store.deleteResult(next.id)); assertEquals(first, store.history().single())
    }
    @Test fun contactDeletionUnlinksAndClearHistoryPreservesDraft() {
        val s = ManualSession.replace().apply { contactId = "contact"; result = result() }
        val record = SessionRecord.snapshot(s)
        assertTrue(store.saveDraft(record)); assertTrue(store.saveResult(record))
        assertTrue(store.unlinkContact("contact")); assertNull(store.history().single().contactId)
        assertNull(store.loadDraft()!!.contactId)
        assertTrue(store.clearHistory()); assertTrue(store.history().isEmpty()); assertNotNull(store.loadDraft())
    }
    @Test fun corruptDraftIsPreservedAndWriteReportsFailure() {
        store.root.mkdirs(); val file = File(store.root, "draft.json"); file.writeText("broken")
        assertNull(store.loadDraft()); assertTrue(store.readFailed)
        assertFalse(store.saveDraft(SessionRecord.snapshot(ManualSession.replace())))
        assertEquals("broken", file.readText())
    }
    @Test fun failedWriteAndWrongDraftIdDoNotClaimSuccessOrDeleteOtherDraft() {
        val r = SessionRecord.snapshot(ManualSession.replace())
        assertTrue(store.saveDraft(r)); assertTrue(store.clearDraft(UUID.randomUUID().toString())); assertNotNull(store.loadDraft())
        clean(); store.root.parentFile!!.mkdirs(); store.root.writeText("blocked")
        assertFalse(store.saveDraft(r)); assertFalse(store.saveResult(r.copy(result = result())))
    }
    @Test fun bitmapIsExcludedAndHistoryRestorationClonesIdentity() {
        val r = SessionRecord.snapshot(ManualSession.replace().apply { result = result() })
        val text = SessionCodec.encode(r)
        assertFalse(text.contains("bitmap")); assertFalse(text.contains("gate"))
        val old = ManualSession.current!!; val request = old.gate.next()
        val restored = ManualSession.restore(r)
        assertNotEquals(old.id, restored.id); assertFalse(ManualSession.accepts(old, request))
    }
    @Test fun repeatedDraftWritesReplaceContentAndBackupRecovers() {
        val first = SessionRecord.snapshot(ManualSession.replace().apply { title = "第一次" })
        assertTrue(store.saveDraft(first))
        assertTrue(store.saveDraft(first.copy(title = "第二次")))
        assertEquals("第二次", store.loadDraft()!!.title)
        val file = File(store.root, "draft.json")
        assertTrue(file.renameTo(File(file.path + ".bak")))
        assertEquals("第二次", store.loadDraft()!!.title)
        assertFalse(store.readFailed)
    }
}
