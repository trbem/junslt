package com.jev.probe.advisor

import android.graphics.Bitmap
import java.util.UUID

/** Active request state stays in RAM; SessionStore persists immutable text snapshots. */
object ManualSession {
    class Session(val id: String = UUID.randomUUID().toString(), var bitmap: Bitmap? = null,
                  val source: String = "手动文本") {
        var messages: List<ReviewMessage> = emptyList()
        var title = ""
        var contactId: String? = null
        var historyId: String? = null
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
    @Synchronized fun restore(record: SessionRecord, preserveId: Boolean = false): Session {
        current?.gate?.cancel()
        return Session(id = if (preserveId) record.id else UUID.randomUUID().toString(), source = record.source).also { current = it }.apply {
        title = record.title; contactId = record.contactId; messages = record.messages
        relationship = record.relationship; goal = record.goal; useBackground = record.useBackground
        result = record.result; ocrDone = true; confirmed = false
        }
    }
    fun accepts(session: Session, request: Long) = current === session && session.gate.accepts(request)
}
