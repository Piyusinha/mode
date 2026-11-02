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

/** The setup boards, in order. Name to Test are "Step 1 of 5" to "Step 5 of 5". */
public enum class OnboardingStep(public val number: Int?) {
    Welcome(null), Name(1), Key(2), Model(3), Permissions(4), Test(5), Result(null), Diagnose(null), Finished(null)
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
) {
    public companion object {
        public const val STEPS: Int = 5
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
    /** The models on the user's ChatGPT plan, as last asked. */
    private val chatGptModels: () -> List<String> = { emptyList() },
) : Presenter<OnboardingUiState>(parent, OnboardingUiState()) {
    private var verifier: String? = null

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
        val start = auth.start(callbackUrl)
        verifier = start.verifier
        set { it.copy(signingIn = true, signInError = null) }
        return start.url
    }

    /** OpenRouter sent the user back to [callbackUrl] (with a code), or [error] if they cancelled. */
    public fun finishSignIn(callbackUrl: String?, error: String? = null) {
        val code = callbackUrl?.let(auth::codeFrom)
        val v = verifier
        if (code == null || v == null) {
            set { it.copy(signingIn = false, signInError = error) }
            return
        }
        scope.launch {
            try {
                settings.apiKey = auth.exchange(code, v)
                set { it.copy(signingIn = false, connected = true, hasSavedKey = true, key = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                set { it.copy(signingIn = false, signInError = e.message) }
            }
        }
    }

    public fun setKey(key: String): Unit = set { it.copy(key = key.trim(), keyLooksRight = looksLikeKey(key)) }

    // Model

    public fun chooseModel(id: String) {
        selected = id
        refreshModels()
    }

    /** Picks the model used when Mode gets stuck; null turns stepping up off. */
    public fun chooseStuck(id: String?) {
        stuck = id
        refreshModels()
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
            OnboardingStep.Permissions -> go(OnboardingStep.Test)
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
            OnboardingStep.Test -> OnboardingStep.Permissions
            OnboardingStep.Diagnose -> OnboardingStep.Result
            else -> return
        }
        go(previous)
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
        val recommended = pickList()
        val all = if (provider == com.piyush.mode.llm.ModelProvider.OpenRouter) catalog.state.value.all else recommended
        fun choice(o: ModelOption) = ModelChoice(o.id, o.name, o.costPerTaskUsd, o.tag, o.note, o.id == selected, o.id == stuck)
        val strongest = strongest()
        set { st ->
            st.copy(
                models = recommended.filter { it.tag != ModelTag.Strongest }.ifEmpty { recommended }.map(::choice),
                stuckModel = (stuck?.let { id -> (recommended + all).firstOrNull { it.id == id } } ?: strongest)?.let(::choice),
                stuckChoices = recommended.map(::choice),
                results = if (st.query.isBlank()) emptyList()
                    else (if (all === recommended) ModelSearch.search(all, st.query) else catalog.search(st.query)).take(MAX_RESULTS).map(::choice),
            )
        }
    }

    /** The picks for the provider in use. */
    private fun pickList(): List<ModelOption> = when (settings.settings.value.provider) {
        com.piyush.mode.llm.ModelProvider.OpenRouter -> catalog.state.value.recommended
        com.piyush.mode.llm.ModelProvider.ClaudeOAuth -> ProviderModels.claude
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
                    OnboardingStep.Key -> next.connected || looksLikeKey(next.key) || (next.key.isBlank() && next.hasSavedKey)
                    OnboardingStep.Model -> selected != null
                    OnboardingStep.Test -> !next.testRunning
                    else -> true
                },
            )
        }
    }

    private fun looksLikeKey(key: String) = key.trim().startsWith(KEY_PREFIX) && key.trim().length > MIN_KEY

    public companion object {
        public const val TEST_TASK: String = "Open Settings, tap Wi-Fi, then come back to Mode."
        public const val TEST_TAG: String = "setup-test"
        /** Where OpenRouter sends the user back after signing in. */
        public const val SIGN_IN_CALLBACK: String = "mode://openrouter"
        private const val KEY_PREFIX = "sk-or-"
        private const val MIN_KEY = 20
        private const val MAX_RESULTS = 20
    }
}
