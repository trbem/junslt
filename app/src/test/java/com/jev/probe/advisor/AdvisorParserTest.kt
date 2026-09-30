package com.jev.probe.advisor

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdvisorParserTest {
    private fun valid(): JSONObject = JSONObject()
        .put("emotion", "等回复会让人不安")
        .put("facts", JSONArray().put("对方说今天累"))
        .put("inferences", JSONArray().put("可能想休息"))
        .put("unknowns", JSONArray().put("关系阶段未知"))
        .put("recommendation", "先关心并给空间")
        .put("next_step", "发一条后等对方回应")
        .put("stop_condition", "对方要求别联系时停止")
        .put("candidates", JSONArray().apply {
            listOf("今天辛苦了，你先歇会儿", "想吐槽的时候我在", "先好好休息，晚点再聊").forEach {
                put(JSONObject().put("strategy", "承接").put("text", it))
            }
        })
    @Test fun acceptsCompleteJsonAndFences() {
        assertEquals(3, AdvisorParser.parse(valid().toString()).candidates.size)
        assertEquals("对方说今天累", AdvisorParser.parse("```json\n${valid()}\n```").facts.single())
    }
    @Test fun rejectsMissingFieldsAndWrongTypes() {
        val data = valid(); data.remove("facts")
        assertThrows(InvalidAdvisorResult::class.java) { AdvisorParser.parse(data.toString()) }
        assertThrows(InvalidAdvisorResult::class.java) { AdvisorParser.parse(valid().put("emotion", 12).toString()) }
    }
    @Test fun rejectsDuplicateAndBlankReplies() {
        val duplicate = valid()
        duplicate.getJSONArray("candidates").getJSONObject(1).put("text", "今天辛苦了，你先歇会儿")
        assertThrows(InvalidAdvisorResult::class.java) { AdvisorParser.parse(duplicate.toString()) }
        val blank = valid()
        blank.getJSONArray("candidates").getJSONObject(2).put("text", "   ")
        assertThrows(InvalidAdvisorResult::class.java) { AdvisorParser.parse(blank.toString()) }
    }
    @Test fun rejectsWrongCandidateCountsAndProse() {
        val data = valid(); data.getJSONArray("candidates").remove(2)
        assertThrows(InvalidAdvisorResult::class.java) { AdvisorParser.parse(data.toString()) }
        val extra = valid(); extra.getJSONArray("candidates").put(JSONObject().put("strategy", "承接").put("text", "第四条"))
        assertThrows(InvalidAdvisorResult::class.java) { AdvisorParser.parse(extra.toString()) }
        assertThrows(InvalidAdvisorResult::class.java) { AdvisorParser.parse("分析如下：${valid()}") }
    }
}
