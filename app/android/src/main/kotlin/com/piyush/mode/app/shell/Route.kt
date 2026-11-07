package com.piyush.mode.app.shell

/** Where a tab's navigation can go (the same places as the iPhone's Route). */
sealed interface Route {
    data class Conversation(val id: String) : Route
    data object LiveRun : Route
    data class RunScreens(val taskId: String) : Route
    data object Settings : Route
    data object ConnectChatGpt : Route
    data object Memory : Route
    data class ModelPicker(val stuck: Boolean) : Route
    data class PlanModels(val brain: Boolean) : Route
    data object Access : Route
    /** Settings › Voice: Mode's voice, Listening, and choosing a voice from a provider. */
    data object Voice : Route
    data object Listening : Route
    /** Settings › Listening › Language. */
    data object Language : Route
    /** Settings › Health and repair. */
    data object Health : Route
    /** Settings › Phone control: the same switches as setup's "Let Mode use your phone". */
    data object PhoneSetup : Route
    /** What went wrong in a task that couldn't finish, and Ask about it. */
    data class TaskDebug(val taskId: String) : Route
    data class TaskDebugAsk(val taskId: String) : Route
    data class VoicePicker(val provider: String) : Route
    data class ProviderDetail(val id: String) : Route
    data object Developer : Route
    data object MemoryAboutYou : Route
    /** Routines, all of them or one app's. */
    data class MemoryRoutines(val app: String?) : Route
    data object MemoryApps : Route
    data class MemoryApp(val name: String) : Route
    data class MemoryEdit(val id: String) : Route
    /** Settings › Unlock by voice: the guide, and the check that it works. */
    data object UnlockVoice : Route
    /** Settings › Backup and restore. */
    data object Backup : Route
}

/** The tabs. Ask is the floating button beside them; Settings opens as a page over the tab showing. */
enum class AppTab { Home, Tasks, Ask, Settings }

/** A mode:// link from a notification, the widget or anywhere: open a conversation, Ask, or just Mode. */
sealed interface DeepLink {
    data class Conversation(val id: String) : DeepLink
    data object Ask : DeepLink
    data object Open : DeepLink
    data object Listen : DeepLink
    /** A mic in the app: talking mode, adding to [conversationId]'s chat (null: a new chat). */
    data class Talk(val conversationId: String?) : DeepLink
    /** Try again: run a task that couldn't finish again, then show its chat. */
    data class Retry(val taskId: String, val conversationId: String?) : DeepLink

    /** For the trace: which kind of link (never the conversation's id). */
    val traceName: String
        get() = when (this) {
            is Conversation -> "conversation"
            Ask -> "ask"
            Open -> "open"
            Listen -> "assistant"
            is Talk -> if (conversationId == null) "ask_mic" else "chat_mic"
            is Retry -> "retry"
        }

    companion object {
        const val SCHEME: String = "mode"

        /** "mode://conversation/<id>", "mode://ask", "mode://retry/<task>/<chat>"…; null for anything else. */
        fun parse(link: String): DeepLink? {
            val prefix = "$SCHEME://"
            if (!link.startsWith(prefix)) return null
            val rest = link.removePrefix(prefix).substringBefore('?').substringBefore('#')
            val parts = rest.split('/').filter { it.isNotEmpty() }
            return when (parts.firstOrNull()) {
                "conversation" -> parts.getOrNull(1)?.let { Conversation(it) } ?: Open
                "ask" -> Ask
                "open" -> Open
                "listen" -> Listen
                "talk" -> Talk(parts.getOrNull(1))
                "retry" -> parts.getOrNull(1)?.let { Retry(it, parts.getOrNull(2)) }
                else -> null
            }
        }
    }
}
