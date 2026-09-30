package com.jev.probe.advisor

import com.jev.probe.core.Analysis
import com.jev.probe.core.RankedReply

/** Injectable stages make failures testable without calling a paid model. */
object AdvisorPipeline {
    fun run(
        judge: () -> Analysis,
        generate: (Analysis?) -> AdvisorResult,
        rank: (List<String>) -> List<RankedReply>,
        cancelled: () -> Boolean = { Thread.currentThread().isInterrupted }
    ): AdvisorResult {
        fun check() { if (cancelled()) throw InterruptedException() }
        check()
        val warnings = mutableListOf<String>()
        val judgment = try {
            judge().also {
                if (it.error != null || it.trueIntent?.choice.isNullOrBlank() || it.dangerLevel == null)
                    warnings.add("Jev 判断不可用，以下为生成模型建议")
            }.takeIf { it.error == null && !it.trueIntent?.choice.isNullOrBlank() && it.dangerLevel != null }
        } catch (e: InterruptedException) { throw e }
        catch (_: Exception) { warnings.add("Jev 判断不可用，以下为生成模型建议"); null }
        check()
        val generated = generate(judgment)
        check()
        val candidates = if (judgment == null) {
            warnings.add("排序不可用，候选按生成顺序展示")
            generated.candidates
        } else try {
            val ranked = rank(generated.candidates.map { it.text })
            require(ranked.size == 3 && ranked.map { it.text }.toSet() == generated.candidates.map { it.text }.toSet())
            require(ranked.all { it.prob.isFinite() && it.prob in 0.0..1.0 } &&
                kotlin.math.abs(ranked.sumOf { it.prob } - 1.0) < .05)
            ranked.map { r -> generated.candidates.first { it.text == r.text }.copy(probability = r.prob) }
        } catch (e: InterruptedException) { throw e }
        catch (_: Exception) {
            warnings.add("Jev 排序不可用，候选按生成顺序展示")
            generated.candidates
        }
        check()
        return generated.copy(candidates = candidates, judgment = judgment, warnings = warnings)
    }
}
