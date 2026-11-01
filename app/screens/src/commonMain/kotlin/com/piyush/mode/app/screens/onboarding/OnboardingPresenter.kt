package com.piyush.mode.app.screens.onboarding

import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.settings.ModelChoice
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
    /** The model used when Mode gets stuck: the strongest pick, on or off. */
    val stuckModel: ModelChoice? = null,
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
) : Presenter<OnboardingUiState>(parent, OnboardingUiState()) {
    private var verifier: String? = null
    private var selected: String? = settings.settings.value.model
    private var stuck: String? = settings.settings.value.strongModel

    init {
        val s = settings.settings.value
        set { it.copy(paired = phoneChecks.hasPairingFile(), callName = s.userName.orEmpty(), hasSavedKey = s.hasApiKey) }
        scope.launch { catalog.refresh() }
        scope.launch { catalog.state.collect { refreshModels() } }
        scope.launch { refreshPermissions() }
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
            refreshPermissions()
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
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        val watch = scope.launch {
            var wifiScreen: ByteArray? = null
            runner.state.collect { s ->
                val actions = s.steps.map { it.action to it.millis }
                fun doneAt(test: (String) -> Boolean) = actions.firstOrNull { test(it.first) }?.second
                val opened = doneAt { it.startsWith("open Settings") }
                val tapped = doneAt { "wi-fi" in it.lowercase() }
                if (tapped != null && wifiScreen == null && s.latestScreen != null) wifiScreen = s.latestScreen
                set { st ->
                    st.copy(
                        connectedMs = st.connectedMs ?: s.latestScreen?.let { started.elapsedNow().inWholeMilliseconds },
                        testShot = wifiScreen ?: st.testShot,
                        testItems = listOf(
                            item(TestItemKind.Connected, st.connectedMs ?: s.latestScreen?.let { started.elapsedNow().inWholeMilliseconds }, running = s.latestScreen == null),
                            item(TestItemKind.OpenSettings, opened, running = s.latestScreen != null && opened == null),
                            item(TestItemKind.TapWifi, tapped, running = opened != null && tapped == null),
                            item(TestItemKind.ComeBack, null, running = tapped != null),
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
                    testSummary = summary,
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
        val models = catalog.state.value
        fun choice(o: ModelOption) = ModelChoice(o.id, o.name, o.costPerTaskUsd, o.tag, o.note, o.id == selected, o.id == stuck)
        val strongest = strongest()
        set { st ->
            st.copy(
                models = models.recommended.filter { it.tag != ModelTag.Strongest }.map(::choice),
                stuckModel = strongest?.let(::choice),
                results = if (st.query.isBlank()) emptyList() else catalog.search(st.query).take(MAX_RESULTS).map(::choice),
            )
        }
    }

    private fun strongest(): ModelOption? = catalog.state.value.recommended.firstOrNull { it.tag == ModelTag.Strongest }

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
