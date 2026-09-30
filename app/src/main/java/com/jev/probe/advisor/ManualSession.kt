package com.jev.probe.advisor

import android.graphics.Bitmap
import java.util.UUID

/** Only RAM. Process death drops the screenshot, messages, and result. */
object ManualSession {
    class Session(val id: String = UUID.randomUUID().toString(), var bitmap: Bitmap? = null,
                  val source: String = "手动文本") {
        var messages: List<ReviewMessage> = emptyList()
        var title = ""
        var relationship = ""
        var goal = ""
        var confirmed = false
        var useBackground = false
        var result: AdvisorResult? = null
        var ocrDone = false
        val gate = RequestGate()
    }
    @Volatile var current: Session? = null
        private set
    @Synchronized fun replace(bitmap: Bitmap? = null, source: String = "手动文本"): Session {
        current?.gate?.cancel()
        // Do not recycle here: an old OCR task may still be reading the bitmap.
        return Session(bitmap = bitmap, source = source).also { current = it }
    }
    @Synchronized fun clear() { current?.gate?.cancel(); current = null }
    fun accepts(session: Session, request: Long) = current === session && session.gate.accepts(request)
}
