package com.jev.probe.advisor

import org.junit.Assert.*
import org.junit.Test

class StructuredGenerationTest {
    @Test fun retriesInvalidFormatExactlyOnce() {
        var attempts = 0
        assertThrows(InvalidAdvisorResult::class.java) {
            StructuredGeneration.run { attempts++; "不是 JSON" }
        }
        assertEquals(2, attempts)
    }
    @Test fun doesNotRetryTransportFailuresAsFormatFailures() {
        var attempts = 0
        assertThrows(IllegalStateException::class.java) {
            StructuredGeneration.run { attempts++; throw IllegalStateException("transport") }
        }
        assertEquals(1, attempts)
    }
}
