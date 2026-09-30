package com.jev.probe.advisor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.jev.probe.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Immutable snapshots. Never persist bitmaps, keys, running requests, or view state. */
data class SessionRecord(val id: String, val source: String, val title: String,
    val contactId: String?, val messages: List<ReviewMessage>, val relationship: String,
    val goal: String, val useBackground: Boolean, val result: AdvisorResult?,
    val updatedAt: Long = System.currentTimeMillis(), val requestId: String = "") {
    companion object {
        fun snapshot(s: ManualSession.Session, id: String = s.id, request: String = "") = SessionRecord(
            id, s.source, s.title, s.contactId, s.messages.toList(), s.relationship, s.goal,
            s.useBackground, s.result, requestId = request)
    }
}

object SessionCodec {
    private fun strings(values: List<String>) = JSONArray(values)
    private fun choice(c: Choice?) = c?.let { JSONObject().put("choice", it.choice)
        .put("confidence", it.confidence).put("probabilities", JSONObject(it.probabilities)) }
    fun result(r: AdvisorResult): JSONObject = JSONObject().put("emotion", r.emotion)
        .put("facts", strings(r.facts)).put("inferences", strings(r.inferences))
        .put("unknowns", strings(r.unknowns)).put("recommendation", r.recommendation)
        .put("next_step", r.nextStep).put("stop_condition", r.stopCondition)
        .put("warnings", strings(r.warnings)).put("candidates", JSONArray().apply {
            r.candidates.forEach { put(JSONObject().put("strategy", it.strategy).put("text", it.text)
                .put("probability", it.probability ?: JSONObject.NULL)) }
        }).apply { r.judgment?.let { a -> put("judgment", JSONObject()
            .put("trueIntent", choice(a.trueIntent)).put("sheNeeds", choice(a.sheNeeds))
            .put("bestAction", choice(a.bestAction)).put("shouldReplyNow", a.shouldReplyNow)
            .put("tensionResolved", a.tensionResolved).put("literalQuestion", a.literalQuestion)
            .put("latencyMs", a.latencyMs).put("error", a.error)
            .put("dangerLevel", a.dangerLevel?.let { JSONObject().put("score", it.score)
                .put("confidence", it.confidence).put("maxLevel", it.maxLevel) })
            .put("rankedReplies", JSONArray().apply { a.rankedReplies.forEach {
                put(JSONObject().put("text", it.text).put("prob", it.prob))
            } })) } }
    private fun list(j: JSONObject, key: String) = j.optJSONArray(key)?.let { a ->
        (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    fun readResult(j: JSONObject): AdvisorResult {
        val parsed = AdvisorParser.parse(j.toString())
        fun c(a: JSONObject?) = a?.let { Choice(it.getString("choice"), it.getDouble("confidence"),
            it.getJSONObject("probabilities").let { p -> p.keys().asSequence().associateWith { k -> p.getDouble(k) } }) }
        val judgment = j.optJSONObject("judgment")?.let { a ->
            fun number(k: String) = if (a.isNull(k)) null else a.getDouble(k)
            Analysis(c(a.optJSONObject("trueIntent")), a.optJSONObject("dangerLevel")?.let {
                Score(it.getDouble("score"), it.getDouble("confidence"), it.getInt("maxLevel")) },
                c(a.optJSONObject("sheNeeds")), number("shouldReplyNow"), c(a.optJSONObject("bestAction")),
                number("tensionResolved"), number("literalQuestion"), a.getJSONArray("rankedReplies").let { rs ->
                    (0 until rs.length()).map { rs.getJSONObject(it).let { r -> RankedReply(r.getString("text"), r.getDouble("prob")) } }
                }, a.getLong("latencyMs"), if (a.isNull("error")) null else a.getString("error"))
        }
        return parsed.copy(judgment = judgment, warnings = list(j, "warnings"), candidates = parsed.candidates.mapIndexed { i, c ->
            val item = j.getJSONArray("candidates").getJSONObject(i)
            val p = if (item.isNull("probability")) null else item.getDouble("probability")
            require(p == null || (p.isFinite() && p in 0.0..1.0))
            c.copy(probability = p)
        })
    }
    fun encode(r: SessionRecord): String = JSONObject().put("schema", 1).put("id", r.id)
        .put("source", r.source).put("title", r.title).put("contactId", r.contactId ?: JSONObject.NULL)
        .put("relationship", r.relationship).put("goal", r.goal).put("useBackground", r.useBackground)
        .put("updatedAt", r.updatedAt).put("requestId", r.requestId)
        .put("messages", JSONArray().apply { r.messages.forEach {
            put(JSONObject().put("side", it.side).put("text", it.text)) } })
        .put("result", r.result?.let { result(it) } ?: JSONObject.NULL).toString()
    fun decode(text: String): SessionRecord {
        val j = JSONObject(text); require(j.getInt("schema") == 1)
        val id = j.getString("id"); UUID.fromString(id)
        val messages = j.getJSONArray("messages").let { a -> (0 until a.length()).map {
            a.getJSONObject(it).let { m -> ReviewMessage(m.getString("side"), m.getString("text")) }
        } }
        return SessionRecord(id, j.getString("source"), j.getString("title"),
            if (j.isNull("contactId")) null else j.getString("contactId"), messages,
            j.getString("relationship"), j.getString("goal"), j.getBoolean("useBackground"),
            j.optJSONObject("result")?.let { readResult(it) }, j.getLong("updatedAt"), j.optString("requestId"))
    }
}

/** All reads/writes share one lock; AtomicFile preserves the previous complete write. */
class SessionStore private constructor(context: Context) {
    internal val root = File(context.filesDir, "advisor-records")
    private val lock = Any()
    @Volatile var readFailed = false; private set
    private val draft get() = File(root, "draft.json")
    private fun historyFile(id: String): File { UUID.fromString(id); return File(root, "history/$id.json") }
    private fun read(file: File): SessionRecord? {
        val atomic = AtomicFile(file)
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        return try { SessionCodec.decode(atomic.openRead().bufferedReader().use { it.readText() }) }
        catch (_: Exception) { readFailed = true; null } // Preserve corrupt files; never overwrite silently.
    }
    private fun write(file: File, r: SessionRecord): Boolean {
        if ((file.exists() || File(file.path + ".bak").exists()) && read(file) == null) return false
        // AtomicFile writes a new transaction file; NIO then replaces the destination atomically.
        // This also gives host tests the same replace semantics as Android's filesystem.
        val pending = File(file.parentFile, file.name + ".pending-" + UUID.randomUUID())
        val atomic = AtomicFile(pending)
        var stream: java.io.FileOutputStream? = null
        return try {
            require(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
            val data = SessionCodec.encode(r).toByteArray(Charsets.UTF_8)
            stream = atomic.startWrite(); stream.write(data)
            atomic.finishWrite(stream); stream = null
            require(pending.isFile && pending.readBytes().contentEquals(data))
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            file.readBytes().contentEquals(data)
        } catch (_: Exception) { stream?.let { atomic.failWrite(it) }; false }
        finally { atomic.delete() }
    }
    fun loadDraft(): SessionRecord? = synchronized(lock) { readFailed = false; read(draft) }
    fun saveDraft(r: SessionRecord): Boolean = synchronized(lock) { write(draft, r) }
    fun saveResult(r: SessionRecord): Boolean = synchronized(lock) {
        require(r.result != null); write(historyFile(r.id), r)
    }
    fun history(contactId: String? = null): List<SessionRecord> = synchronized(lock) {
        readFailed = false
        File(root, "history").listFiles()?.filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
            ?.map { File(it.path.removeSuffix(".bak")) }?.distinct()?.mapNotNull { read(it) }
            ?.filter { contactId == null || it.contactId == contactId }?.sortedByDescending { it.updatedAt } ?: emptyList()
    }
    fun clearDraft(id: String? = null): Boolean = synchronized(lock) {
        if (id != null && read(draft)?.id != id) return@synchronized true
        delete(draft)
    }
    private fun delete(file: File): Boolean {
        val atomic = AtomicFile(file); atomic.delete()
        return !file.exists() && !File(file.path + ".bak").exists() && !File(file.path + ".new").exists()
    }
    fun deleteResult(id: String): Boolean = synchronized(lock) { delete(historyFile(id)) }
    fun clearHistory(): Boolean = synchronized(lock) {
        File(root, "history").listFiles()?.filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
            ?.map { File(it.path.removeSuffix(".bak")) }?.distinct()?.map { delete(it) }?.all { it } ?: true
    }
    fun unlinkContact(id: String): Boolean = synchronized(lock) {
        val records = history(id)
        if (readFailed) return@synchronized false
        val outcomes = records.map { write(historyFile(it.id), it.copy(contactId = null)) }.toMutableList()
        loadDraft()?.takeIf { it.contactId == id }?.let { outcomes.add(write(draft, it.copy(contactId = null))) }
        outcomes.all { it }
    }
    companion object {
        @Volatile private var instance: SessionStore? = null
        fun get(context: Context): SessionStore {
            val expected = File(context.applicationContext.filesDir, "advisor-records")
            return instance?.takeIf { it.root == expected } ?: synchronized(this) {
                instance?.takeIf { it.root == expected } ?: SessionStore(context.applicationContext).also { instance = it }
            }
        }
    }
}

object SessionWriter {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    fun run(write: () -> Boolean, done: (Boolean) -> Unit = {}) {
        executor.execute { val ok = try { write() } catch (_: Exception) { false }; main.post { done(ok) } }
    }
}

class LocalSaveException : IllegalStateException("本机保存失败")
