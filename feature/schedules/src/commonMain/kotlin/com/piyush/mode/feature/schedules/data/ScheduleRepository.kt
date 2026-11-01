package com.piyush.mode.feature.schedules.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.piyush.mode.core.database.ModeDatabase
import com.piyush.mode.feature.schedules.domain.Schedule
import com.piyush.mode.feature.schedules.domain.ScheduleRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.piyush.mode.core.database.Schedule as ScheduleRow

/** Repeating tasks, saved in the app's database. */
@OptIn(ExperimentalUuidApi::class)
public class ScheduleRepository(
    private val db: ModeDatabase,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    private val q get() = db.scheduleQueries

    public fun add(text: String, rule: ScheduleRule, nextRunAt: Long, createdAt: Long): Schedule {
        val id = newId()
        q.insert(id, text.trim(), rule.hour.toLong(), rule.minute.toLong(), ScheduleRule.bitsOf(rule.days).toLong(), 1, nextRunAt, createdAt)
        return get(id)!!
    }

    public fun get(id: String): Schedule? = q.byId(id).executeAsOneOrNull()?.toDomain()

    public fun all(): List<Schedule> = q.all().executeAsList().map { it.toDomain() }

    public fun observe(): Flow<List<Schedule>> =
        q.all().asFlow().mapToList(Dispatchers.Default).map { rows: List<ScheduleRow> -> rows.map { row -> row.toDomain() } }

    /** Enabled schedules due at or before [nowMs], soonest first. */
    public fun due(nowMs: Long): List<Schedule> = q.due(nowMs).executeAsList().map { it.toDomain() }

    public fun setEnabled(id: String, enabled: Boolean, nextRunAt: Long?) {
        q.setEnabled(if (enabled) 1 else 0, nextRunAt, id)
    }

    /** After a run (or a skipped one): when it ran, when it is next due; a one-off switches itself off. */
    public fun ran(id: String, ranAt: Long, nextRunAt: Long?) {
        q.ran(ranAt, nextRunAt, if (nextRunAt == null) 0 else 1, id)
    }

    public fun delete(id: String) {
        q.delete(id)
    }

    private fun ScheduleRow.toDomain() = Schedule(
        id = id,
        text = text,
        rule = ScheduleRule(hour.toInt(), minute.toInt(), ScheduleRule.daysFromBits(days.toInt())),
        enabled = enabled != 0L,
        nextRunAt = next_run_at,
        lastRunAt = last_run_at,
    )
}
