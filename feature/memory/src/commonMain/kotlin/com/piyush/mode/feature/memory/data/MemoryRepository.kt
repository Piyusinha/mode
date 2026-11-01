package com.piyush.mode.feature.memory.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.piyush.mode.core.common.SecretFilter
import com.piyush.mode.core.database.ModeDatabase
import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryItem
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.memory.domain.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.piyush.mode.core.database.Memory as MemoryRow

/**
 * What Mode remembers, on this iPhone only. Every write goes through the
 * secret filter and is merged with what is already known, so memory neither
 * stores a secret nor fills up with near-duplicates.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
public class MemoryRepository(
    private val db: ModeDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { Uuid.random().toString() },
) {
    private val q get() = db.memoryQueries

    public fun all(): List<MemoryItem> = q.all().executeAsList().map { it.toDomain() }

    public fun observe(): Flow<List<MemoryItem>> =
        q.all().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toDomain() } }

    public fun get(id: String): MemoryItem? = q.byId(id).executeAsOneOrNull()?.toDomain()

    public fun ofKind(kind: MemoryKind): List<MemoryItem> = q.byKind(kind.stored).executeAsList().map { it.toDomain() }

    /** Items a task added or changed, for "Remembered · Undo". */
    public fun changedByTask(taskId: String): List<MemoryItem> = q.changedByTask(taskId).executeAsList().map { it.toDomain() }

    /**
     * Saves [draft] unless it looks like a secret. Something already known in
     * the same words is left alone; a close match is updated (its old text kept
     * for undo); anything else is added. A skill that differs from a known one
     * is kept beside it as another route that works.
     */
    public fun save(draft: MemoryDraft, sourceTaskId: String? = null): SaveResult {
        val clean = draft.cleaned() ?: return SaveResult.Rejected("empty")
        SecretFilter.reasonOf(listOf(clean.subject, clean.title, clean.text, clean.evidence).joinToString(" "))
            ?.let { return SaveResult.Rejected("looks like a secret ($it)") }

        val sameSubject = ofKind(clean.kind).filter { it.subject == clean.subject }
        sameSubject.firstOrNull { Words.normal(it.text) == Words.normal(clean.text) }?.let { return SaveResult.Unchanged(it) }
        val close = sameSubject.maxByOrNull { Words.similarity(it.text, clean.text) }
            ?.takeIf { Words.similarity(it.text, clean.text) >= mergeAt(clean.kind) }
        if (close != null) return SaveResult.Updated(change(close.id, clean, sourceTaskId)!!)

        val id = newId()
        val time = now()
        q.insert(
            id, clean.kind.stored, clean.subject, clean.title, clean.text, clean.evidence,
            clean.keywords.joinToString(","), clean.app, sourceTaskId, time, time,
        )
        return SaveResult.Added(get(id)!!)
    }

    /** Replaces an item's text, keeping the old text for undo. Null if it no longer exists or the text is a secret. */
    public fun update(id: String, text: String, evidence: String, sourceTaskId: String? = null): MemoryItem? {
        val item = get(id) ?: return null
        if (SecretFilter.isSecret("$text $evidence")) return null
        return change(id, MemoryDraft(item.kind, item.subject, item.title, text, evidence, item.keywords, item.app).cleaned()!!, sourceTaskId)
    }

    public fun forget(id: String) {
        q.delete(id)
    }

    /** Takes back the last change to an item: an item that was added goes; one that was changed gets its old text back. */
    public fun undo(id: String) {
        val item = get(id) ?: return
        if (item.previousText != null) q.restorePrevious(id) else q.delete(id)
    }

    public fun forgetEverything() {
        q.deleteAll()
    }

    /** The items shown to a task, and whether it went well: feeds ranking and, later, expiry. */
    public fun recordUse(ids: Collection<String>, success: Boolean) {
        val time = now()
        db.transaction {
            ids.forEach { q.recordUse(if (success) 1 else 0, if (success) 0 else 1, time, it) }
        }
    }

    private fun change(id: String, draft: MemoryDraft, sourceTaskId: String?): MemoryItem? {
        q.change(draft.title, draft.text, draft.evidence, draft.keywords.joinToString(","), draft.app, sourceTaskId, now(), id)
        return get(id)
    }

    /** Skills are only merged when nearly the same route; facts and tips when about the same thing. */
    private fun mergeAt(kind: MemoryKind) = if (kind == MemoryKind.Skill) 0.7 else 0.5

    private fun MemoryDraft.cleaned(): MemoryDraft? {
        val t = text.trim().take(MAX_TEXT)
        if (t.isEmpty()) return null
        return copy(
            subject = subject.trim().lowercase().take(80).ifEmpty { title.trim().lowercase().take(80) },
            title = title.trim().take(80).ifEmpty { subject.trim().take(80) },
            text = t,
            evidence = evidence.trim().take(MAX_TEXT),
            keywords = keywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() && ',' !in it }.distinct().take(12),
            app = app?.trim()?.ifEmpty { null },
        )
    }

    private fun MemoryRow.toDomain() = MemoryItem(
        id = id,
        kind = MemoryKind.of(kind),
        subject = subject,
        title = title,
        text = text,
        evidence = evidence,
        keywords = keywords.split(',').filter { it.isNotBlank() },
        app = app,
        sourceTaskId = source_task_id,
        previousText = previous_text,
        uses = uses.toInt(),
        successes = successes.toInt(),
        failures = failures.toInt(),
        createdAt = created_at,
        updatedAt = updated_at,
        lastUsedAt = last_used_at,
    )

    public companion object {
        public const val MAX_TEXT: Int = 300
    }
}
