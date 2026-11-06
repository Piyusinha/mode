package com.piyush.mode.feature.backup.data

import com.piyush.mode.core.database.ModeDatabase
import kotlinx.serialization.Serializable
import com.piyush.mode.core.database.Memory as MemoryRow
import com.piyush.mode.core.database.Run as RunRow
import com.piyush.mode.core.database.Task as TaskRow

/**
 * What a backup holds: every row of every table (database schema 3), Mode's settings and, when asked for,
 * the sign-ins. A column added later gets a default here, so older backups still read.
 */
@Serializable
internal data class BackupPayload(
    val conversations: List<Conversation> = emptyList(),
    val turns: List<Turn> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val runs: List<Run> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val signIns: List<SignInEntries> = emptyList(),
) {
    @Serializable
    data class Conversation(val id: String, val title: String, val createdAt: Long, val updatedAt: Long)

    @Serializable
    data class Turn(
        val id: String, val conversationId: String, val role: String, val text: String,
        val taskId: String? = null, val createdAt: Long,
    )

    @Serializable
    data class Task(
        val id: String, val conversationId: String? = null, val text: String, val source: String, val status: String,
        val createdAt: Long, val startedAt: Long? = null, val endedAt: Long? = null, val runId: String? = null,
        val summary: String? = null, val steps: Long? = null, val costUsd: Double? = null, val private: Long = 0,
    )

    @Serializable
    data class Run(
        val id: String, val taskId: String? = null, val task: String, val outcome: String, val summary: String,
        val model: String? = null, val steps: Long? = null, val ms: Long? = null, val costUsd: Double? = null,
        val tag: String? = null, val startedAt: Long,
    )

    @Serializable
    data class Memory(
        val id: String, val kind: String, val subject: String, val title: String, val text: String, val evidence: String,
        val keywords: String = "", val app: String? = null, val sourceTaskId: String? = null, val previousText: String? = null,
        val uses: Long = 0, val successes: Long = 0, val failures: Long = 0, val createdAt: Long, val updatedAt: Long,
        val lastUsedAt: Long? = null,
    )

    @Serializable
    data class Schedule(
        val id: String, val text: String, val hour: Long, val minute: Long, val days: Long, val enabled: Long = 1,
        val nextRunAt: Long? = null, val lastRunAt: Long? = null, val createdAt: Long,
    )

    /** One provider's Keychain entries, by [id] (`SignIns`). */
    @Serializable
    data class SignInEntries(val id: String, val name: String, val values: Map<String, String>)

    companion object {
        /** Every row, oldest insert first (so order by rowid survives a restore). Call inside a transaction. */
        fun read(db: ModeDatabase): BackupPayload = BackupPayload(
            conversations = db.conversationQueries.everything().executeAsList().map { Conversation(it.id, it.title, it.created_at, it.updated_at) },
            turns = db.turnQueries.everything().executeAsList().map { Turn(it.id, it.conversation_id, it.role, it.text, it.task_id, it.created_at) },
            tasks = db.taskQueries.everything().executeAsList().map { it.toBackup() },
            runs = db.runQueries.everything().executeAsList().map { it.toBackup() },
            memories = db.memoryQueries.everything().executeAsList().map { it.toBackup() },
            schedules = db.scheduleQueries.everything().executeAsList().map {
                Schedule(it.id, it.text, it.hour, it.minute, it.days, it.enabled, it.next_run_at, it.last_run_at, it.created_at)
            },
        )

        private fun TaskRow.toBackup() = Task(
            id, conversation_id, text, source, status, created_at, started_at, ended_at, run_id, summary, steps, cost_usd, private_,
        )

        private fun RunRow.toBackup() = Run(id, task_id, task, outcome, summary, model, steps, ms, cost_usd, tag, started_at)

        private fun MemoryRow.toBackup() = Memory(
            id, kind, subject, title, text, evidence, keywords, app, source_task_id, previous_text, uses, successes, failures,
            created_at, updated_at, last_used_at,
        )
    }
}
