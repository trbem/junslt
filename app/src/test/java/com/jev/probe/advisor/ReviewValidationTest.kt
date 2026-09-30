package com.jev.probe.advisor

import org.junit.Assert.*
import org.junit.Test

class ReviewValidationTest {
    @Test fun requiresConfirmationAndMappedSpeakers() {
        assertNotNull(ReviewValidation.error(listOf(ReviewMessage("me", "你好")), false))
        assertNotNull(ReviewValidation.error(listOf(ReviewMessage("unknown", "你好")), true))
        assertNull(ReviewValidation.error(listOf(ReviewMessage("other", "你好")), true))
    }
    @Test fun rejectsEmptyAndOversizedConversations() {
        assertNotNull(ReviewValidation.error(emptyList(), true))
        assertNotNull(ReviewValidation.error(listOf(ReviewMessage("me", " ")), true))
        assertNotNull(ReviewValidation.error(listOf(ReviewMessage("me", "字".repeat(12001))), true))
    }
    @Test fun ocrOrdersByPositionAndLeavesAmbiguousSpeakerUnknown() {
        val result = OcrReview.messages(listOf(
            RecognizedLine("第二条", 800, 100, 970, 130),
            RecognizedLine("第一条", 40, 20, 300, 50),
            RecognizedLine("可能是时间", 400, 60, 600, 80)
        ), 1000)
        assertEquals(listOf("第一条", "可能是时间", "第二条"), result.map { it.text })
        assertEquals(listOf("other", "unknown", "me"), result.map { it.side })
    }
    @Test fun pasteDoesNotInventSpeakers() {
        assertEquals(listOf("me", "other", "unknown"), PastedChat.parse("我：你好\n对方:今天好累\n原始文本").map { it.side })
    }
    @Test fun oldRequestsAndCancelledRequestsCannotDisplay() {
        val gate = RequestGate(); val first = gate.next(); val second = gate.next()
        assertFalse(gate.accepts(first)); assertTrue(gate.accepts(second))
        gate.cancel(); assertFalse(gate.accepts(second))
    }
    @Test fun routerLoadsAtMostThreeDocumentsAndPrioritizesSafety() {
        val selected = KnowledgeRouter.select("对方威胁我，约我见面")
        assertTrue(selected.size <= 3)
        assertTrue(selected.contains("safety.md")); assertFalse(selected.contains("initiative.md"))
    }
}
