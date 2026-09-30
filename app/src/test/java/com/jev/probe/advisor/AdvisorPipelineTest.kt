package com.jev.probe.advisor

import com.jev.probe.core.Analysis
import com.jev.probe.core.RankedReply
import com.jev.probe.core.Choice
import com.jev.probe.core.Score
import org.junit.Assert.*
import org.junit.Test

class AdvisorPipelineTest {
    private fun judgment(error: String? = null) = Analysis(Choice("casual_chat", .8, emptyMap()),
        Score(1.0, .8, 9), null, null, null, null, null, emptyList(), 0, error)
    private fun generated() = AdvisorResult("情绪", listOf("事实"), emptyList(), listOf("关系未知"),
        "建议", "下一步", "停止条件", listOf(Candidate("承接", "A"), Candidate("降压", "B"), Candidate("澄清", "C")))
    @Test fun judgmentFailureStillGeneratesWithoutPretendingToRank() {
        var called = false
        val result = AdvisorPipeline.run({ judgment("失败") }, { a -> assertNull(a); generated() },
            { called = true; emptyList() })
        assertFalse(called); assertEquals(3, result.candidates.size)
        assertTrue(result.candidates.all { it.probability == null }); assertEquals(2, result.warnings.size)
    }
    @Test fun rankingReordersCandidatesAndRetainsStrategy() {
        val result = AdvisorPipeline.run({ judgment() }, { generated() }, {
            listOf(RankedReply("C", .6), RankedReply("A", .3), RankedReply("B", .1))
        })
        assertEquals("C", result.candidates.first().text)
        assertEquals("澄清", result.candidates.first().strategy)
        assertEquals(.6, result.candidates.first().probability!!, .001)
    }
    @Test fun rankingFailurePreservesOrderAndNoProbabilities() {
        val result = AdvisorPipeline.run({ judgment() }, { generated() }, { throw IllegalStateException() })
        assertEquals(listOf("A", "B", "C"), result.candidates.map { it.text })
        assertTrue(result.candidates.all { it.probability == null }); assertEquals(1, result.warnings.size)
    }
    @Test fun missingRankingProbabilitiesDegradesHonestly() {
        val result = AdvisorPipeline.run({ judgment() }, { generated() }, { it.map { s -> RankedReply(s, 0.0) } })
        assertTrue(result.candidates.all { it.probability == null })
    }
    @Test fun incompleteJudgmentIsNotPresentedAsSuccessful() {
        val result = AdvisorPipeline.run({ judgment().copy(trueIntent = null) }, { generated() }, { emptyList() })
        assertNull(result.judgment)
        assertEquals(2, result.warnings.size)
    }
    @Test fun generationFailureIsVisibleAndHasNoPlaceholderCandidates() {
        assertThrows(InvalidAdvisorResult::class.java) {
            AdvisorPipeline.run({ judgment() }, { throw InvalidAdvisorResult() }, { emptyList() })
        }
    }
    @Test fun cancellationSkipsRemainingStages() {
        var cancelled = false; var ranked = false
        assertThrows(InterruptedException::class.java) {
            AdvisorPipeline.run({ judgment() }, { cancelled = true; generated() },
                { ranked = true; emptyList() }, { cancelled })
        }
        assertFalse(ranked)
    }
}
