package com.piyush.mode.feature.conversations.domain

/** A thread of turns between the user and Mode; follow-ups continue it. */
public data class Conversation(
    val id: String,
    val title: String,
    /** Epoch milliseconds. */
    val createdAt: Long,
    /** Epoch milliseconds of the latest turn. */
    val updatedAt: Long,
)

public enum class TurnRole { User, Mode }

/** One message: what the user asked, or what Mode answered. */
public data class Turn(
    val id: String,
    val conversationId: String,
    val role: TurnRole,
    val text: String,
    /** The task this turn asked for or reports on. */
    val taskId: String?,
    val createdAt: Long,
)
