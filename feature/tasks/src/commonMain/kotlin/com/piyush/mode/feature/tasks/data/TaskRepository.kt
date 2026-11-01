package com.piyush.mode.feature.tasks.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.piyush.mode.core.database.ModeDatabase
import com.piyush.mode.feature.tasks.domain.Task
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.domain.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.piyush.mode.core.database.Task as TaskRow

/** Tasks saved in the app's database, with their status as they run. */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
public class TaskRepository(
    private val db: ModeDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { Uuid.random().toString() },
) {
    public fun create(text: String, source: TaskSource, conversationId: String?): Task {
        val id = newId()
        db.taskQueries.insert(id, conversationId, text, source.stored, TaskStatus.Queued.stored, now())
        return get(id)!!
    }

    public fun markRunning(id: String, runId: String) {
        db.taskQueries.start(TaskStatus.Running.stored, now(), runId, id)
    }

    public fun setStatus(id: String, status: TaskStatus) {
        db.taskQueries.setStatus(status.stored, id)
    }

    public fun finish(id: String, status: TaskStatus, summary: String, steps: Int?, costUsd: Double?) {
        db.taskQueries.finish(status.stored, now(), summary, steps?.toLong(), costUsd, id)
    }

    public fun get(id: String): Task? = db.taskQueries.byId(id).executeAsOneOrNull()?.toDomain()

    /** The newest tasks first. */
    public fun recent(limit: Long = 100): Flow<List<Task>> =
        db.taskQueries.recent(limit).asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toDomain() } }

    private fun TaskRow.toDomain() = Task(
        id = id,
        conversationId = conversation_id,
        text = text,
        source = TaskSource.of(source),
        status = TaskStatus.of(status),
        createdAt = created_at,
        startedAt = started_at,
        endedAt = ended_at,
        runId = run_id,
        summary = summary,
        steps = steps?.toInt(),
        costUsd = cost_usd,
    )
}
