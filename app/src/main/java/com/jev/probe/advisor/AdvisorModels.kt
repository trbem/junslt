package com.jev.probe.advisor

import com.jev.probe.core.Analysis
import com.jev.probe.core.Msg
import java.util.concurrent.atomic.AtomicLong

data class ReviewMessage(val side: String, val text: String)

object ReviewValidation {
    fun error(messages: List<ReviewMessage>, confirmed: Boolean): String? = when {
        !confirmed -> "请先确认每条消息的说话人和顺序"
        messages.isEmpty() -> "请添加至少一条聊天消息"
        messages.any { it.side !in listOf("me", "other") } -> "请为每条消息选择我或对方"
        messages.any { it.text.isBlank() } -> "存在空消息，请填写或删除"
        messages.sumOf { it.text.length } > 12000 -> "聊天超过 12000 字，请保留本轮相关内容"
        else -> null
    }
}

data class Candidate(val strategy: String, val text: String, val probability: Double? = null)
data class AdvisorResult(
    val emotion: String,
    val facts: List<String>,
    val inferences: List<String>,
    val unknowns: List<String>,
    val recommendation: String,
    val nextStep: String,
    val stopCondition: String,
    val candidates: List<Candidate>,
    val judgment: Analysis? = null,
    val warnings: List<String> = emptyList()
)

/** Every OCR / network callback must match both this request and its session. */
class RequestGate {
    private val generation = AtomicLong()
    fun next(): Long = generation.incrementAndGet()
    fun accepts(id: Long): Boolean = generation.get() == id
    fun cancel() { generation.incrementAndGet() }
}

/** Coordinates are image coordinates; side is only a suggestion for review. */
data class RecognizedLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
object OcrReview {
    fun messages(lines: List<RecognizedLine>, width: Int): List<ReviewMessage> =
        lines.sortedWith(compareBy<RecognizedLine> { it.top }.thenBy { it.left })
            .filter { it.text.isNotBlank() }
            .map {
                // Ambiguous central lines must remain unmapped, never assumed to be the other party.
                val side = when {
                    it.left < width * .22 -> "other"
                    it.right > width * .78 -> "me"
                    else -> "unknown"
                }
                ReviewMessage(side, it.text.trim())
            }
}
