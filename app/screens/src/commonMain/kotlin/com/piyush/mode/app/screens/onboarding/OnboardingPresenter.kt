package com.piyush.mode.app.screens.onboarding

import com.piyush.mode.feature.settings.data.ModelSearch
import com.piyush.mode.feature.settings.domain.ProviderModels
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.settings.ModelChoice
import com.piyush.mode.core.common.PlainText
import com.piyush.mode.core.platform.AppLifecycle
import com.piyush.mode.core.platform.Permission
import com.piyush.mode.core.platform.PermissionStatus
import com.piyush.mode.core.platform.Permissions
import com.piyush.mode.feature.connection.domain.CheckState
import com.piyush.mode.feature.connection.domain.PhoneCheckResult
import com.piyush.mode.feature.connection.domain.PhoneChecks
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.settings.data.ModelCatalog
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.ModelOption
import com.piyush.mode.feature.settings.domain.ModelTag
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import com.piyush.mode.llm.OpenRouterAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The setup boards, in order. Name to Test are "Step 1 of 6" to "Step 6 of 6". */
public enum class OnboardingStep(public val number: Int?) {
    Welcome(null), Name(1), Key(2), Model(3), Permissions(4), Voice(5), Test(6), Result(null), Diagnose(null), Finished(null)
}

public enum class PermissionKind { Vpn, Notifications, Siri, Microphone }

public data class PermissionRow(
    val kind: PermissionKind,
    val status: PermissionStatus,
    /** Siri: "Optional". */
    val optional: Boolean,
    /** Microphone: asked the first time a task is spoken, so "Later". */
    val later: Boolean,
)

public enum class TestItemKind { Connected, OpenSettings, TapWifi, ComeBack }

public enum class TestItemState { Pending, Running, Done, Failed }

public data class TestItem(val kind: TestItemKind, val state: TestItemState, val ms: Long?)

public data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val stepCount: Int = STEPS,
    /** The pairing file (made once with Mode Setup on a Mac) is on this iPhone. */
    val paired: Boolean = false,
    /** The full name from the contact card the user picked. */
    val cardName: String? = null,
    /** What Mode will call the user (the first name, editable). */
    val callName: String = "",
    val key: String = "",
    val keyLooksRight: Boolean = false,
    val hasSavedKey: Boolean = false,
    val signingIn: Boolean = false,
    /** Signed in with OpenRouter and the key is saved. */
    val connected: Boolean = false,
    val signInError: String? = null,
    val models: List<ModelChoice> = emptyList(),
    /** "Pick how Mode thinks": how hard phone control and the brain think (null: the provider has no effort). */
    val phoneEffort: String? = null,
    val brainEffort: String? = null,
    /** The efforts offered (Low, Medium, High); empty for providers without them (OpenRouter). */
    val efforts: List<String> = emptyList(),
    /** Each role is on Mode's recommended model and effort. */
    val phoneRecommended: Boolean = false,
    val brainRecommended: Boolean = false,
    /** The models run on the person's plan (ChatGPT, Claude): no cost per task. */
    val onPlan: Boolean = false,
    /** The model used when Mode gets stuck (the strongest pick by default), on or off. */
    val stuckModel: ModelChoice? = null,
    /** What can be picked for when Mode gets stuck. */
    val stuckChoices: List<ModelChoice> = emptyList(),
    val query: String = "",
    val results: List<ModelChoice> = emptyList(),
    val permissions: List<PermissionRow> = emptyList(),
    val connectError: String? = null,
    val testRunning: Boolean = false,
    val testItems: List<TestItem> = TestItemKind.entries.map { TestItem(it, TestItemState.Pending, null) },
    /** How long reaching the phone took. */
    val connectedMs: Long? = null,
    val testSummary: String? = null,
    /** What Mode saw on the Wi-Fi page (JPEG). */
    val testShot: ByteArray? = null,
    val checks: List<PhoneCheckResult> = emptyList(),
    val checking: Boolean = false,
    val canContinue: Boolean = true,
    /** Talk to Mode: a few of Apple's voices on this iPhone to pick from, the one picked, the one playing. */
    val voiceOptions: List<com.piyush.mode.feature.settings.voice.VoiceOption> = emptyList(),
    /** Who speaks ([com.piyush.mode.feature.settings.domain.VoiceKind.id]); the voice cards are its voices. */
    val voiceProvider: String = "apple",
    /** Who listens. */
    val listener: String = "apple",
    val voice: String? = null,
    val voicePlaying: String? = null,
    /** Apple listening: "ready", "permission", "denied", "asset" or "unsupported". */
    val hearing: String = "unsupported",
) {
    public companion object {
        public const val STEPS: Int = 6
        /** Voices offered in setup; the rest are in Settings → Voice. */
        public const val SETUP_VOICES: Int = 3
    }
}

/**
 * First-run setup, board by board: welcome, your name (from your contact
 * card), your AI (Connect OpenRouter, or paste a key), a model, permissions,
 * a test that opens Settings, taps Wi-Fi and comes back, its result, and a
 * step-by-step diagnosis when it didn't work.
 */
public class OnboardingPresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalog,
    private val session: PhoneSession,
    private val runner: TaskRunner,
    private val permissions: Permissions,
    private val phoneChecks: PhoneChecks,
    private val auth: OpenRouterAuth,
    lifecycle: AppLifecycle,
    /** The models the user's Claude account offers, as last listed. */
    private val claudeModels: () -> List<com.piyush.mode.llm.PlanModel> = { emptyList() },
    /** The models on the user's ChatGPT plan, as last asked. */
    private val chatGptModels: () -> List<String> = { emptyList() },
    /** Speaking and listening (null: the voice step only shows what Apple offers). */
    private val voices: com.piyush.mode.feature.settings.voice.Voices? = null,
) : Presenter<OnboardingUiState>(parent, OnboardingUiState()) {
    private var sample: kotlinx.coroutines.Job? = null
    private var verifier: String? = null
    private var signInKeyRevision = 0L
    private var signInAttempt = 0

    /** When the test tapped Wi-Fi, and how long Mode then took to be back in front. */
    private var wifiTappedAt: kotlin.time.TimeSource.Monotonic.ValueTimeMark? = null
    private var cameBackMs: Long? = null
    private var selected: String? = settings.settings.value.model
    private var stuck: String? = settings.settings.value.strongModel

    init {
        // A new setup starts with ChatGPT picked (the user's plan, no cost per task) unless one is connected already.
        if (com.piyush.mode.llm.ModelProvider.entries.none(settings::hasAccess)) settings.selectProvider(com.piyush.mode.llm.ModelProvider.ChatGpt)
        val s = settings.settings.value
        set { it.copy(paired = phoneChecks.hasPairingFile(), callName = s.userName.orEmpty(), hasSavedKey = s.hasApiKey) }
        // "Come back here" ticks the moment Mode is in front again, not when the run ends:
        // the agent reads this screen to know it's finished.
        lifecycle.onForeground { if (scope.isActive) cameBack() }
        scope.launch { catalog.refresh() }
        scope.launch { catalog.state.collect { refreshModels() } }
        // Picking another provider brings its own models.
        scope.launch { settings.settings.collect { refreshModels() } }
        scope.launch { runCatching { refreshPermissions() } }
    }

    // Name

    /** The user picked their contact card. */
    public fun setNameFromCard(given: String, family: String) {
        val full = listOf(given, family).filter { it.isNotBlank() }.joinToString(" ")
        set { it.copy(cardName = full.ifBlank { null }, callName = given.ifBlank { full }) }
    }

    public fun setCallName(name: String): Unit = set { it.copy(callName = name) }

    // Key

    /** Starts "Connect OpenRouter"; returns the page to open. */
    public fun startSignIn(callbackUrl: String): String {
        signInAttempt++
        val start = auth.start(callbackUrl)
        verifier = start.verifier
        signInKeyRevision = settings.openRouterKeyRevision
        set { it.copy(signingIn = true, signInError = null) }
        return start.url
    }

    /** OpenRouter sent the user back to [callbackUrl] (with a code), or [error] if they cancelled. */
    public fun finishSignIn(callbackUrl: String?, error: String? = null) {
        val code = callbackUrl?.let(auth::codeFrom)
        val v = verifier
        verifier = null
        if (code == null || v == null || signInKeyRevision != settings.openRouterKeyRevision) {
            signInAttempt++
            set { it.copy(signingIn = false, signInError = error) }
            return
        }
        val attempt = signInAttempt
        val keyRevision = signInKeyRevision
        scope.launch {
            try {
                val key = auth.exchange(code, v)
                if (attempt != signInAttempt) return@launch
                if (keyRevision != settings.openRouterKeyRevision) {
                    set { it.copy(signingIn = false) }
                    return@launch
                }
                settings.apiKey = key
                set { it.copy(signingIn = false, connected = true, hasSavedKey = true, key = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt != signInAttempt) return@launch
                if (keyRevision != settings.openRouterKeyRevision) {
                    set { it.copy(signingIn = false) }
                    return@launch
                }
                set { it.copy(signingIn = false, signInError = e.message) }
            }
        }
    }

    public fun setKey(key: String): Unit = set { it.copy(key = key.trim(), keyLooksRight = looksLikeKey(key)) }

    // Model

    public fun chooseModel(id: String) {
        selected = keepEffort(id, selected, PHONE_EFFORT)
        refreshModels()
    }

    /** Picks the brain's model, keeping its effort; null turns the brain off. */
    public fun chooseStuck(id: String?) {
        stuck = id?.let { keepEffort(it, stuck, BRAIN_EFFORT) }
        refreshModels()
    }

    /** How hard phone control ([brain] false) or the brain thinks: "low", "medium" or "high". */
    public fun chooseEffort(brain: Boolean, effort: String) {
        if (!ProviderModels.supportsEffort(settings.settings.value.provider)) return
        if (brain) stuck = stuck?.let { ProviderModels.withEffort(it, effort) }
        else selected = selected?.let { ProviderModels.withEffort(it, effort) }
        refreshModels()
    }

    /** [id] with the effort [current] had, or [usual] when it had none (plan providers only). */
    private fun keepEffort(id: String, current: String?, usual: String): String {
        if (!ProviderModels.supportsEffort(settings.settings.value.provider)) return id
        return ProviderModels.withEffort(id, current?.let(ProviderModels::effort) ?: ProviderModels.effort(id) ?: usual)
    }

    /** Turns the when-stuck model on or off. */
    public fun toggleStuck() {
        stuck = if (stuck == null) strongest()?.id else null
        refreshModels()
    }

    public fun search(text: String) {
        set { it.copy(query = text) }
        refreshModels()
    }

    // Permissions

    public fun allow(kind: PermissionKind) {
        scope.launch {
            when (kind) {
                PermissionKind.Vpn -> connectVpn()
                PermissionKind.Notifications -> permissions.request(Permission.Notifications)
                PermissionKind.Siri -> permissions.request(Permission.Siri)
                PermissionKind.Microphone -> permissions.request(Permission.Microphone)
            }
            runCatching { refreshPermissions() }
        }
    }

    // Test

    /**
     * The one fixed task in Mode, agreed for setup only: open Settings, tap
     * Wi-Fi, come back. It proves Mode can see and tap before the user relies on it.
     */
    public fun runTest() {
        set { it.copy(step = OnboardingStep.Test, testRunning = true, testSummary = null, testShot = null, connectedMs = null,
            testItems = TestItemKind.entries.map { k -> TestItem(k, TestItemState.Pending, null) }) }
        wifiTappedAt = null
        cameBackMs = null
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        val watch = scope.launch {
            var wifiScreen: ByteArray? = null
            runner.state.collect { s ->
                val actions = s.steps.map { it.action to it.millis }
                fun doneAt(test: (String) -> Boolean) = actions.firstOrNull { test(it.first) }?.second
                val opened = doneAt { it.startsWith("open Settings") }
                val tapped = doneAt { "wi-fi" in it.lowercase() }
                if (tapped != null && wifiScreen == null && s.latestScreen != null) wifiScreen = s.latestScreen
                if (tapped != null && wifiTappedAt == null) wifiTappedAt = kotlin.time.TimeSource.Monotonic.markNow()
                set { st ->
                    st.copy(
                        connectedMs = st.connectedMs ?: s.latestScreen?.let { started.elapsedNow().inWholeMilliseconds },
                        testShot = wifiScreen ?: st.testShot,
                        testItems = listOf(
                            item(TestItemKind.Connected, st.connectedMs ?: s.latestScreen?.let { started.elapsedNow().inWholeMilliseconds }, running = s.latestScreen == null),
                            item(TestItemKind.OpenSettings, opened, running = s.latestScreen != null && opened == null),
                            item(TestItemKind.TapWifi, tapped, running = opened != null && tapped == null),
                            item(TestItemKind.ComeBack, cameBackMs, running = tapped != null),
                        ),
                    )
                }
            }
        }
        scope.launch {
            val result = runner.runTask(TEST_TASK, settings.agentConfig(), tag = TEST_TAG)
            watch.cancel()
            val worked = (result?.outcome as? AgentOutcome.Done)?.success == true
            val summary = when (val o = result?.outcome) {
                is AgentOutcome.Done -> o.summary
                is AgentOutcome.Stopped -> o.why
                is AgentOutcome.Failed -> o.why
                null -> runner.state.value.status
            }
            set { st ->
                st.copy(
                    testRunning = false,
                    testSummary = summary?.let { PlainText.fromMarkdown(it) },
                    testItems = st.testItems.map {
                        when {
                            it.state == TestItemState.Done -> it
                            it.kind == TestItemKind.ComeBack && worked -> it.copy(state = TestItemState.Done)
                            else -> it.copy(state = TestItemState.Failed)
                        }
                    },
                    step = OnboardingStep.Result,
                )
            }
        }
    }

    private fun cameBack() {
        val tapped = wifiTappedAt ?: return
        if (!state.value.testRunning || cameBackMs != null) return
        val ms = tapped.elapsedNow().inWholeMilliseconds
        cameBackMs = ms
        set { st -> st.copy(testItems = st.testItems.map { if (it.kind == TestItemKind.ComeBack) item(it.kind, ms, running = true) else it }) }
    }

    /** The user saw it work (or not). Not working opens Diagnose and runs the checks. */
    public fun confirmTest(worked: Boolean) {
        if (worked) finish() else {
            go(OnboardingStep.Diagnose)
            runChecks()
        }
    }

    // Diagnose

    public fun runChecks() {
        set { it.copy(checking = true) }
        scope.launch {
            phoneChecks.run { results -> set { it.copy(checks = results) } }
            session.disconnect()
            set { it.copy(checking = false) }
        }
    }

    /** The checks as text, to send. */
    public fun report(): String = state.value.checks.joinToString("\n") { r ->
        val s = when (val st = r.state) {
            CheckState.NotChecked -> "not checked"
            CheckState.Running -> "running"
            is CheckState.Passed -> "ok" + (st.ms?.let { " ($it ms)" } ?: "")
            is CheckState.Failed -> "FAILED: ${st.reason}" + (st.fix?.let { " Fix: $it" } ?: "")
        }
        "${r.id.name}: $s"
    }

    // Moving through

    /** Saves this step's answer and moves on. */
    public fun next() {
        val st = state.value
        if (!st.canContinue) return
        when (st.step) {
            OnboardingStep.Name -> settings.update { it.copy(userName = st.callName) }
            OnboardingStep.Key -> if (st.key.isNotBlank()) settings.apiKey = st.key
            OnboardingStep.Model -> settings.update { it.copy(model = selected ?: it.model, strongModel = stuck) }
            else -> Unit
        }
        when (st.step) {
            OnboardingStep.Welcome -> go(OnboardingStep.Name)
            OnboardingStep.Name -> go(OnboardingStep.Key)
            OnboardingStep.Key -> go(OnboardingStep.Model)
            OnboardingStep.Model -> go(OnboardingStep.Permissions)
            OnboardingStep.Permissions -> { go(OnboardingStep.Voice); loadVoices() }
            OnboardingStep.Voice -> { keepVoice(); go(OnboardingStep.Test) }
            OnboardingStep.Test -> runTest()
            OnboardingStep.Result, OnboardingStep.Diagnose -> finish()
            OnboardingStep.Finished -> Unit
        }
    }

    public fun back() {
        val previous = when (state.value.step) {
            OnboardingStep.Name -> OnboardingStep.Welcome
            OnboardingStep.Key -> OnboardingStep.Name
            OnboardingStep.Model -> OnboardingStep.Key
            OnboardingStep.Permissions -> OnboardingStep.Model
            OnboardingStep.Voice -> OnboardingStep.Permissions
            OnboardingStep.Test -> OnboardingStep.Voice
            OnboardingStep.Diagnose -> OnboardingStep.Result
            else -> return
        }
        go(previous)
    }

    /** Not now: no voice is kept and the microphone is not asked for; Settings → Voice has it later. */
    public fun skipVoice() {
        stopVoice()
        go(OnboardingStep.Test)
    }

    public fun chooseVoice(id: String) { set { it.copy(voice = id) } }

    /** Plays [id] saying the sample; again stops it. */
    public fun playVoice(id: String) {
        val v = voices ?: return
        if (state.value.voicePlaying == id) { stopVoice(); return }
        stopVoice()
        val option = state.value.voiceOptions.firstOrNull { it.id == id } ?: return
        set { it.copy(voicePlaying = id) }
        sample = scope.launch {
            try {
                v.preview(speakingKind(), option)
            } finally {
                set { st -> if (st.voicePlaying == id) st.copy(voicePlaying = null) else st }
            }
        }
    }

    /** Allow mic: the microphone prompt and Apple's speech model; the row follows until it settles. */
    public fun allowMic() {
        com.piyush.mode.feature.settings.voice.DeviceVoice.prepare()
        scope.launch {
            repeat(60) {
                kotlinx.coroutines.delay(1_000)
                val now = com.piyush.mode.feature.settings.voice.DeviceVoice.hearingState()
                set { it.copy(hearing = now) }
                if (now in setOf("ready", "denied", "unsupported")) return@launch
            }
        }
    }

    private fun stopVoice() {
        sample?.cancel()
        voices?.stop()
        set { it.copy(voicePlaying = null) }
    }

    /** Who speaks now: the first ready provider (Apple until another is connected and first). */
    private fun speakingKind(): com.piyush.mode.feature.settings.domain.VoiceKind =
        voices?.speaker()?.first ?: com.piyush.mode.feature.settings.domain.VoiceKind.Apple

    /** Reads who speaks and listens again (after a picker sheet changed them) and that speaker's voices. */
    public fun refreshVoices() { if (state.value.step == OnboardingStep.Voice) loadVoices() }

    private fun loadVoices() {
        val v = voices ?: return
        val kind = speakingKind()
        val changed = state.value.voiceProvider != kind.id
        set { it.copy(hearing = com.piyush.mode.feature.settings.voice.DeviceVoice.hearingState(), voiceProvider = kind.id,
            listener = v.listener()?.id ?: com.piyush.mode.feature.settings.domain.VoiceKind.Apple.id,
            voiceOptions = if (changed) emptyList() else it.voiceOptions, voice = if (changed) null else it.voice) }
        scope.launch {
            // Mode's pick first, then the provider's own order (Apple lists the best for this iPhone's language first).
            val options = com.piyush.mode.feature.settings.voice.Voices.withOurPick(kind, v.options(kind).options).take(OnboardingUiState.SETUP_VOICES)
            val kept = settings.settings.value.voices[kind.id]?.id
            set { it.copy(voiceOptions = options, voice = it.voice?.takeIf { k -> options.any { o -> o.id == k } }
                ?: kept?.takeIf { k -> options.any { o -> o.id == k } } ?: options.firstOrNull()?.id) }
        }
    }

    private fun keepVoice() {
        stopVoice()
        val kind = speakingKind()
        val option = state.value.voiceOptions.firstOrNull { it.id == state.value.voice } ?: return
        settings.update { s -> s.copy(voices = s.voices + (kind.id to com.piyush.mode.feature.settings.domain.VoiceChoice(option.id, option.name))) }
    }

    /** Ends setup; the app opens on Home from now on. */
    public fun finish() {
        settings.update { it.copy(onboardingDone = true) }
        go(OnboardingStep.Finished)
    }

    private suspend fun connectVpn() {
        val error = try {
            session.reconnect()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: e.toString()
        }
        session.disconnect()
        set { it.copy(connectError = error) }
    }

    private suspend fun refreshPermissions() {
        val rows = listOf(
            PermissionRow(PermissionKind.Vpn, if (phoneChecks.vpnAllowed()) PermissionStatus.Allowed else PermissionStatus.NotAsked, optional = false, later = false),
            PermissionRow(PermissionKind.Notifications, permissions.status(Permission.Notifications), optional = false, later = false),
            PermissionRow(PermissionKind.Siri, permissions.status(Permission.Siri), optional = true, later = false),
            PermissionRow(PermissionKind.Microphone, permissions.status(Permission.Microphone), optional = false, later = true),
        )
        set { it.copy(permissions = rows) }
    }

    private fun refreshModels() {
        val s = settings.settings.value
        val provider = s.provider
        // A choice made for another provider does not carry over: that provider's own models come back.
        if (selected?.let { ProviderModels.accepts(provider, it) } != true) selected = s.model
        if (stuck != null && !ProviderModels.accepts(provider, stuck!!)) stuck = s.strongModel
        // Plan providers think at a set effort: Mode's recommendation until the person picks another.
        val withEfforts = ProviderModels.supportsEffort(provider)
        if (withEfforts) {
            selected = selected?.let { if (ProviderModels.effort(it) == null) ProviderModels.withEffort(it, PHONE_EFFORT) else it }
            stuck = stuck?.let { if (ProviderModels.effort(it) == null) ProviderModels.withEffort(it, BRAIN_EFFORT) else it }
        }
        val recommended = pickList()
        val all = if (provider == com.piyush.mode.llm.ModelProvider.OpenRouter) catalog.state.value.all else recommended
        fun same(a: String?, b: String?) = a != null && b != null && ProviderModels.base(a) == ProviderModels.base(b)
        fun choice(o: ModelOption) = ModelChoice(o.id, o.name, o.costPerTaskUsd, o.tag, o.note, same(o.id, selected), same(o.id, stuck))
        val strongest = strongest()
        val phoneEffort = selected?.takeIf { withEfforts }?.let(ProviderModels::effortUsed)
        val brainEffort = stuck?.takeIf { withEfforts }?.let(ProviderModels::effortUsed)
        set { st ->
            st.copy(
                phoneEffort = phoneEffort,
                brainEffort = brainEffort,
                efforts = if (withEfforts) ProviderModels.efforts(provider, recommended.firstOrNull { same(it.id, selected) }) else emptyList(),
                phoneRecommended = same(selected, ProviderModels.main(provider)) && (!withEfforts || phoneEffort == PHONE_EFFORT),
                brainRecommended = same(stuck, ProviderModels.strong(provider)) && (!withEfforts || brainEffort == BRAIN_EFFORT),
                onPlan = provider != com.piyush.mode.llm.ModelProvider.OpenRouter,
                models = recommended.filter { it.tag != ModelTag.Strongest }.ifEmpty { recommended }.map(::choice),
                stuckModel = (stuck?.let { id -> (recommended + all).firstOrNull { same(it.id, id) } } ?: strongest)?.let(::choice),
                stuckChoices = recommended.map(::choice),
                results = if (st.query.isBlank()) emptyList()
                    else (if (all === recommended) ModelSearch.search(all, st.query) else catalog.search(st.query)).take(MAX_RESULTS).map(::choice),
            )
        }
    }

    /** The picks for the provider in use. */
    private fun pickList(): List<ModelOption> = when (settings.settings.value.provider) {
        com.piyush.mode.llm.ModelProvider.OpenRouter -> catalog.state.value.recommended
        com.piyush.mode.llm.ModelProvider.ClaudeOAuth -> ProviderModels.claude(claudeModels())
        com.piyush.mode.llm.ModelProvider.ChatGpt -> ProviderModels.chatGpt(chatGptModels())
    }

    private fun strongest(): ModelOption? = pickList().firstOrNull { it.tag == ModelTag.Strongest }

    private fun item(kind: TestItemKind, ms: Long?, running: Boolean) =
        TestItem(kind, if (ms != null) TestItemState.Done else if (running && state.value.testRunning) TestItemState.Running else TestItemState.Pending, ms)

    private fun go(step: OnboardingStep) = set { it.copy(step = step) }

    private fun set(change: (OnboardingUiState) -> OnboardingUiState) {
        mutableState.update { st ->
            val next = change(st)
            next.copy(
                canContinue = when (next.step) {
                    OnboardingStep.Name -> next.callName.isNotBlank()
                    // OpenRouter (signed in, a key typed, or one saved) or the chosen plan's account
                    // (ChatGPT, Claude): either is enough to go on.
                    OnboardingStep.Key -> next.connected || looksLikeKey(next.key) || (next.key.isBlank() && next.hasSavedKey) ||
                        settings.settings.value.hasModelAccess
                    OnboardingStep.Model -> selected != null
                    OnboardingStep.Test -> !next.testRunning
                    else -> true
                },
            )
        }
    }

    private fun looksLikeKey(key: String) = key.trim().startsWith(KEY_PREFIX) && key.trim().length > MIN_KEY

    public companion object {
        /** Mode's recommended efforts: phone control quick and careful, the brain thorough. */
        public const val PHONE_EFFORT: String = "medium"
        public const val BRAIN_EFFORT: String = "high"
        public const val TEST_TASK: String = "Open Settings, tap Wi-Fi, then come back to Mode."
        public const val TEST_TAG: String = "setup-test"
        /** Where OpenRouter sends the user back after signing in. */
        public const val SIGN_IN_CALLBACK: String = "mode://openrouter"
        private const val KEY_PREFIX = "sk-or-"
        private const val MIN_KEY = 20
        private const val MAX_RESULTS = 20
    }
}
