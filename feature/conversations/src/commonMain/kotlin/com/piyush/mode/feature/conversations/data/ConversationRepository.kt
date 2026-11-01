package com.piyush.mode.feature.conversations.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.piyush.mode.core.database.ModeDatabase
import com.piyush.mode.feature.conversations.domain.Conversation
import com.piyush.mode.feature.conversations.domain.Turn
import com.piyush.mode.feature.conversations.domain.TurnRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.piyush.mode.core.database.Conversation as ConversationRow
import com.piyush.mode.core.database.Turn as TurnRow

/** Conversations and their turns, saved in the app's database. */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
public class ConversationRepository(
    private val db: ModeDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { Uuid.random().toString() },
) {
    public fun create(title: String): Conversation {
        val time = now()
        val conversation = Conversation(newId(), titleFrom(title), time, time)
        db.conversationQueries.insert(conversation.id, conversation.title, time, time)
        return conversation
    }

    public fun addTurn(conversationId: String, role: TurnRole, text: String, taskId: String? = null): Turn {
        val turn = Turn(newId(), conversationId, role, text, taskId, now())
        db.transaction {
            db.turnQueries.insert(turn.id, conversationId, role.name.lowercase(), text, taskId, turn.createdAt)
            db.conversationQueries.touch(turn.createdAt, conversationId)
        }
        return turn
    }

    public fun rename(id: String, title: String) {
        db.conversationQueries.rename(titleFrom(title), now(), id)
    }

    /** Deletes the conversation and its turns; its tasks stay, unlinked. */
    public fun delete(id: String) {
        db.conversationQueries.delete(id)
    }

    public fun get(id: String): Conversation? = db.conversationQueries.byId(id).executeAsOneOrNull()?.toDomain()

    /** The most recently active conversations first. */
    public fun recent(limit: Long = 100): Flow<List<Conversation>> =
        db.conversationQueries.recent(limit).asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toDomain() } }

    /** The turns of one conversation as they are now, oldest first. */
    public fun turnsNow(conversationId: String): List<Turn> =
        db.turnQueries.forConversation(conversationId).executeAsList().map { it.toDomain() }

    /** The turns of one conversation, oldest first, updated as they change. */
    public fun turns(conversationId: String): Flow<List<Turn>> =
        db.turnQueries.forConversation(conversationId).asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toDomain() } }

    private fun ConversationRow.toDomain() = Conversation(id, title, created_at, updated_at)

    private fun TurnRow.toDomain() = Turn(
        id = id,
        conversationId = conversation_id,
        role = if (role == "mode") TurnRole.Mode else TurnRole.User,
        text = text,
        taskId = task_id,
        createdAt = created_at,
    )

    private companion object {
        const val MAX_TITLE = 60

        /** A title from the first line of [text], shortened to fit a list. */
        fun titleFrom(text: String): String {
            val line = text.trim().lineSequence().firstOrNull().orEmpty().ifBlank { "Untitled" }
            return if (line.length <= MAX_TITLE) line else line.take(MAX_TITLE - 1).trimEnd() + "…"
        }
    }
}
