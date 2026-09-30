package com.jev.probe.advisor

import org.json.JSONObject

class InvalidAdvisorResult : IllegalArgumentException("军师结果格式不完整，请重新生成")

object AdvisorParser {
    fun parse(content: String): AdvisorResult {
        try {
            val trimmed = content.trim().removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val json = JSONObject(trimmed)
            fun text(key: String): String {
                val raw = json.get(key)
                if (raw !is String || raw.isBlank()) throw InvalidAdvisorResult()
                return raw.trim()
            }
            fun list(key: String): List<String> {
                val arr = json.getJSONArray(key)
                return (0 until arr.length()).map {
                    val raw = arr.get(it)
                    if (raw !is String || raw.isBlank()) throw InvalidAdvisorResult()
                    raw.trim()
                }
            }
            val arr = json.getJSONArray("candidates")
            if (arr.length() != 3) throw InvalidAdvisorResult()
            val candidates = (0 until 3).map {
                val c = arr.getJSONObject(it)
                val strategy = c.get("strategy")
                val reply = c.get("text")
                if (strategy !is String || strategy.isBlank() || reply !is String || reply.isBlank() || reply.trim().length > 80)
                    throw InvalidAdvisorResult()
                Candidate(strategy.trim(), reply.trim())
            }
            if (candidates.map { it.text.replace(Regex("\\s+"), "") }.distinct().size != 3)
                throw InvalidAdvisorResult()
            return AdvisorResult(text("emotion"), list("facts"), list("inferences"), list("unknowns"),
                text("recommendation"), text("next_step"), text("stop_condition"), candidates)
        } catch (_: Exception) { throw InvalidAdvisorResult() }
    }
}
