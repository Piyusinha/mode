package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.Permission
import com.piyush.mode.core.platform.PermissionStatus
import com.piyush.mode.core.platform.Permissions
import com.piyush.mode.feature.connection.domain.CheckState
import com.piyush.mode.feature.connection.domain.PhoneCheckId
import com.piyush.mode.feature.connection.domain.PhoneChecks
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.feature.settings.data.ClaudeAccount
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.SpeechLanguage
import com.piyush.mode.feature.settings.domain.VoiceKind
import com.piyush.mode.feature.settings.voice.DeviceVoice
import com.piyush.mode.feature.settings.voice.Voices
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** One part of Mode, in its group (phone, ai, voice, iphone). */
public enum class HealthItem(public val group: String) {
    Paired("phone"), Vpn("phone"), Reach("phone"), Screen("phone"),
    Ai("ai"), Actions("ai"),
    Microphone("voice"), Listening("voice"), Speaking("voice"),
    Notifications("iphone"),
}

/** Waiting: not checked because a part before it failed (not a problem of its own). */
public enum class HealthState { Checking, Ok, Warn, Repaired, Busy, Waiting }

/** What fixes a problem: Mode repairs it ([Repair]), or the user opens a page ([Open]) such as Settings → AI. */
public enum class HealthFix { None, Repair, Open }

/** [detail]: what it is now ("0.9 s", "Signed in", the reason it fails); [opens]: the page for [HealthFix.Open]. */
public data class HealthRow(val item: HealthItem, val state: HealthState, val detail: String, val fix: HealthFix = HealthFix.None, val opens: String? = null)

public data class HealthUiState(
    val rows: List<HealthRow> = HealthItem.entries.map { HealthRow(it, HealthState.Checking, "") },
    val checking: Boolean = false,
    val repairing: Boolean = false,
) {
    val problems: Int get() = rows.count { it.state == HealthState.Warn }
    val repairable: Int get() = rows.count { it.state == HealthState.Warn && it.fix == HealthFix.Repair }
}

/**
 * Settings → Health & repair: every part Mode needs, checked on this iPhone, and what repairs it. Repairs are
 * the safe ones Mode can do itself (reconnect phone control, refresh the AI sign-in, ask for a permission);
 * the rest open the page where the user fixes it.
 */
public class HealthPresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val session: PhoneSession,
    private val phoneChecks: PhoneChecks,
    private val runner: TaskRunner,
    private val voices: Voices?,
    private val permissions: Permissions,
    private val llm: LlmClient?,
    private val chatGpt: ChatGptAccount,
    private val claude: ClaudeAccount?,
    /** The last lines of Mode's log, for Share report. */
    private val logTail: () -> String = { "" },
) : Presenter<HealthUiState>(parent, HealthUiState()) {
    private var job: Job? = null
    private val repaired = mutableSetOf<HealthItem>()

    /** Checks everything again. */
    public fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            mutableState.value = HealthUiState(checking = true)
            runChecks()
            doneWithThePhone()
            mutableState.value = mutableState.value.copy(checking = false)
        }
    }

    /** Repairs what Mode can, then checks again; what was repaired says so. */
    public fun repair() {
        if (job?.isActive == true) return
        job = scope.launch {
            mutableState.value = mutableState.value.copy(repairing = true)
            val todo = mutableState.value.rows.filter { it.state == HealthState.Warn && it.fix == HealthFix.Repair }.map { it.item }.toSet()
            for (item in todo) {
                val worked = runCatching { repairOne(item) }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)
                AppLog.log("health", "repair ${item.name}: ${if (worked) "done" else "did not help"}")
            }
            runChecks()
            doneWithThePhone()
            val now = mutableState.value.rows
            repaired += todo.filter { item -> now.first { it.item == item }.state == HealthState.Ok }
            mutableState.value = mutableState.value.copy(
                repairing = false,
                rows = now.map { if (it.item in repaired && it.state == HealthState.Ok) it.copy(state = HealthState.Repaired) else it },
            )
        }
    }

    /**
     * Checks and repairs bring up Mode's VPN and the session; with no task to use them, both go off again
     * (an open session keeps iOS's indicator and the screen on; an idle VPN stays in the status bar).
     */
    private suspend fun doneWithThePhone() {
        if (!runner.state.value.running) session.disconnectAndRelease()
    }

    /** One repair; true when it did something. */
    private suspend fun repairOne(item: HealthItem): Boolean = when (item) {
        HealthItem.Paired, HealthItem.Vpn, HealthItem.Reach, HealthItem.Screen -> { session.reconnect(); true }
        HealthItem.Actions, HealthItem.Ai -> when (settings.settings.value.provider) {
            ModelProvider.ChatGpt -> chatGpt.accessToken() != null
            ModelProvider.ClaudeOAuth -> claude?.accessToken(forceRefresh = true) != null
            ModelProvider.OpenRouter -> false
        }
        HealthItem.Microphone -> { DeviceVoice.prepare(); true }
        HealthItem.Notifications -> permissions.request(Permission.Notifications) == PermissionStatus.Allowed
        HealthItem.Listening, HealthItem.Speaking -> { voices?.clearProblem(); false }
    }

    private fun set(row: HealthRow) {
        mutableState.value = mutableState.value.copy(rows = mutableState.value.rows.map { if (it.item == row.item) row else it })
    }

    private suspend fun runChecks() {
        checkPhone()
        checkAi()
        checkVoice()
        val notes = permissions.status(Permission.Notifications)
        set(when (notes) {
            PermissionStatus.Allowed -> HealthRow(HealthItem.Notifications, HealthState.Ok, "On, for questions and results")
            PermissionStatus.NotAsked -> HealthRow(HealthItem.Notifications, HealthState.Warn, "Not allowed yet: Mode can't ask you while it works", HealthFix.Repair)
            else -> HealthRow(HealthItem.Notifications, HealthState.Warn, "Off: turn them on in iPhone Settings › Mode", HealthFix.Open, "iphone-settings")
        })
    }

    /** Phone control, from the step-by-step checks; skipped while a task drives the phone. */
    private suspend fun checkPhone() {
        val phone = listOf(HealthItem.Paired, HealthItem.Vpn, HealthItem.Reach, HealthItem.Screen)
        if (runner.state.value.running) {
            phone.forEach { set(HealthRow(it, HealthState.Busy, "Mode is working on a task: check after it ends")) }
            return
        }
        phone.forEach { set(HealthRow(it, HealthState.Checking, "")) }
        var results = emptyList<com.piyush.mode.feature.connection.domain.PhoneCheckResult>()
        runCatching { phoneChecks.run { results = it } }.onFailure { if (it is CancellationException) throw it }
        // Which failures Mode can repair itself (reconnecting); pairing needs the Mac, Developer Mode the user.
        fun fixFor(id: PhoneCheckId): Pair<HealthFix, String?> = when (id) {
            PhoneCheckId.PairingFile, PhoneCheckId.Paired -> HealthFix.None to null
            PhoneCheckId.DeveloperMode -> HealthFix.Open to "iphone-settings"
            else -> HealthFix.Repair to null
        }
        fun row(item: HealthItem, ids: List<PhoneCheckId>, ok: String): HealthRow {
            val states = ids.map { id -> id to (results.firstOrNull { it.id == id }?.state ?: CheckState.NotChecked) }
            val failed = states.firstOrNull { it.second is CheckState.Failed }
            return when {
                failed != null -> {
                    val f = failed.second as CheckState.Failed
                    val (fix, opens) = fixFor(failed.first)
                    HealthRow(item, HealthState.Warn, f.fix ?: f.reason, fix, opens)
                }
                states.all { it.second is CheckState.Passed } -> HealthRow(item, HealthState.Ok, ok)
                else -> HealthRow(item, HealthState.Waiting, "Checked once the part above works")
            }
        }
        val reachMs = (results.firstOrNull { it.id == PhoneCheckId.Reach }?.state as? CheckState.Passed)?.ms
        set(row(HealthItem.Paired, listOf(PhoneCheckId.PairingFile, PhoneCheckId.Paired), "Pairing file is here"))
        set(row(HealthItem.Vpn, listOf(PhoneCheckId.Vpn), "On"))
        set(row(HealthItem.Reach, listOf(PhoneCheckId.Reach), reachMs?.let { "${(it / 100) / 10.0} s" } ?: "Reached"))
        set(row(HealthItem.Screen, listOf(PhoneCheckId.DeveloperMode, PhoneCheckId.DiskImage, PhoneCheckId.Screen, PhoneCheckId.Hands), "Checked just now"))
    }

    /** Signed in, and the model answers with an action when asked to (this morning's failure). */
    private suspend fun checkAi() {
        val s = settings.settings.value
        val provider = s.provider
        val who = when (provider) {
            ModelProvider.ChatGpt -> "ChatGPT"
            ModelProvider.ClaudeOAuth -> "Claude"
            ModelProvider.OpenRouter -> "OpenRouter"
        }
        if (!settings.hasAccess(provider)) {
            set(HealthRow(HealthItem.Ai, HealthState.Warn, "$who isn't connected", HealthFix.Open, "providers"))
            set(HealthRow(HealthItem.Actions, HealthState.Warn, "Connect your AI first", HealthFix.Open, "providers"))
            return
        }
        set(HealthRow(HealthItem.Ai, HealthState.Ok, "$who · signed in"))
        set(HealthRow(HealthItem.Actions, HealthState.Checking, ""))
        val client = llm ?: return set(HealthRow(HealthItem.Actions, HealthState.Ok, "Ready"))
        val result = try {
            withTimeoutOrNull(ACTION_TEST_MS) {
                client.forProvider(provider).chat(s.model, listOf(ChatMessage("system", TEST_SYSTEM), ChatMessage("user", "Check.")), TEST_TOOLS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return set(HealthRow(HealthItem.Actions, HealthState.Warn, e.message?.take(140) ?: "$who didn't answer", HealthFix.Repair))
        }
        set(when {
            result == null -> HealthRow(HealthItem.Actions, HealthState.Warn, "$who took too long to answer", HealthFix.Repair)
            result.toolCall?.name == TEST_TOOL -> HealthRow(HealthItem.Actions, HealthState.Ok, "Test passed just now")
            else -> HealthRow(HealthItem.Actions, HealthState.Warn, "$who answered without choosing an action", HealthFix.Repair)
        })
    }

    private fun checkVoice() {
        val v = voices
        val mic = DeviceVoice.hearingState()
        set(when (mic) {
            "denied" -> HealthRow(HealthItem.Microphone, HealthState.Warn, "Off: turn it on in iPhone Settings › Mode", HealthFix.Open, "iphone-settings")
            "permission" -> HealthRow(HealthItem.Microphone, HealthState.Warn, "Not allowed yet", HealthFix.Repair)
            else -> HealthRow(HealthItem.Microphone, HealthState.Ok, "Allowed")
        })
        if (v == null) return
        val problem = v.problem.value
        val language = SpeechLanguage.fromId(settings.settings.value.language)
        val listener = v.listener() ?: VoiceKind.Apple
        val hearing = problem?.takeIf { it.hearing }
        set(if (hearing != null) {
            HealthRow(HealthItem.Listening, HealthState.Warn, "${name(hearing.kind)}: ${trouble(hearing.trouble)}", HealthFix.Open, "listening")
        } else if (listener == VoiceKind.Apple && mic == "asset") {
            HealthRow(HealthItem.Listening, HealthState.Warn, "Apple's speech model isn't downloaded yet", HealthFix.Repair)
        } else {
            HealthRow(HealthItem.Listening, HealthState.Ok, name(listener) + " · " + language.english)
        })
        val speaker = v.speaker()
        val speaking = problem?.takeIf { !it.hearing }
        set(if (speaking != null) {
            HealthRow(HealthItem.Speaking, HealthState.Warn, "${name(speaking.kind)}: ${trouble(speaking.trouble)}", HealthFix.Open, "voice")
        } else {
            HealthRow(HealthItem.Speaking, HealthState.Ok, listOfNotNull(speaker?.first?.let(::name), speaker?.second?.name).joinToString(" · ").ifEmpty { "Apple" })
        })
    }

    private fun name(kind: VoiceKind): String = when (kind) {
        VoiceKind.Apple -> "Apple"
        VoiceKind.ElevenLabs -> "ElevenLabs"
        VoiceKind.Fish -> "Fish Audio"
        VoiceKind.Deepgram -> "Deepgram"
        VoiceKind.Soniox -> "Soniox"
        else -> kind.id
    }

    private fun trouble(t: com.piyush.mode.feature.settings.voice.VoiceTrouble): String = when (t.name) {
        "NoKey" -> "no key"
        "KeyRejected" -> "the key was refused"
        "PlanLimit" -> "your plan doesn't allow it"
        "Unreachable" -> "couldn't be reached"
        else -> "not ready"
    }

    /** Everything on this screen as text, with the last lines of Mode's log, to share when asking for help. */
    public fun report(): String = buildString {
        append("Mode health report\n\n")
        mutableState.value.rows.forEach { r ->
            val mark = when (r.state) { HealthState.Ok, HealthState.Repaired -> "OK"; HealthState.Warn -> "!!"; else -> ".." }
            append("[").append(mark).append("] ").append(r.item.name).append(": ").append(r.detail).append('\n')
        }
        val tail = logTail()
        if (tail.isNotBlank()) append("\nRecent log:\n").append(tail)
    }

    private companion object {
        const val ACTION_TEST_MS = 25_000L
        const val TEST_TOOL = "ok"
        const val TEST_SYSTEM = "This checks that you can call tools. Call ok."
        val TEST_TOOLS = buildJsonArray {
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", TEST_TOOL)
                    put("description", "Say you are ready.")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") { putJsonObject("ready") { put("type", "boolean") } }
                        putJsonArray("required") { add(JsonPrimitive("ready")) }
                    }
                }
            })
        }
    }
}
