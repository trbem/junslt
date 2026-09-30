package com.jev.probe.advisor

import android.content.Context

object KnowledgeRouter {
    fun select(text: String): List<String> {
        val selected = mutableListOf("reply.md")
        when {
            Regex("家暴|跟踪|威胁|勒索|自杀|自伤|强迫").containsMatchIn(text) -> selected.add("safety.md")
            Regex("不要联系|别联系|拒绝|不喜欢|忙|冷淡|取消|分手").containsMatchIn(text) -> selected.add("reciprocity.md")
            Regex("吵|生气|道歉|委屈|冲突|忘了").containsMatchIn(text) -> selected.add("conflict.md")
            Regex("约|见面|喜欢|暧昧").containsMatchIn(text) -> selected.add("initiative.md")
            else -> selected.add("emotion.md")
        }
        selected.add("digital.md")
        return selected.distinct().take(3)
    }

    fun load(context: Context, text: String): String = select(text).joinToString("\n\n") { name ->
        context.assets.open("advisor/$name").bufferedReader().use { it.readText() }.take(9000)
    }
}
