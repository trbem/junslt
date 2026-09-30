package com.jev.probe.advisor

object StructuredGeneration {
    /** Retry only invalid structured content once. HTTP failures belong to the transport layer. */
    fun run(request: (Int) -> String): AdvisorResult {
        for (attempt in 0..1) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val content = request(attempt)
            try { return AdvisorParser.parse(content) }
            catch (e: InvalidAdvisorResult) { if (attempt == 1) throw e }
        }
        throw InvalidAdvisorResult()
    }
}
