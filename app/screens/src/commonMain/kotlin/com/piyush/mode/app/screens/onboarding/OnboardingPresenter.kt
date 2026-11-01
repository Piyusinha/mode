package com.piyush.mode.app.screens.onboarding

import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.settings.ModelChoice
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.diagnostics.presentation.HandsCheck
import com.piyush.mode.feature.settings.data.ModelCatalog
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public enum class OnboardingStep { Welcome, Name, Key, Model, Permissions, TestRun, TestResult, Diagnose, Finished }

public data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val name: String = "",
    val key: String = "",
    /** The key looks like an OpenRouter key ("sk-or-…"). */
    val keyLooksRight: Boolean = false,
    /** A key is already saved; leaving the field empty keeps it. */
    val hasSavedKey: Boolean = false,
    val models: List<ModelChoice> = emptyList(),
    val selectedModel: String? = null,
    /** Asking iOS for the VPN and reaching the phone's own services. */
    val connecting: Boolean = false,
    /** Why connecting failed, in the engine's words. */
    val connectError: String? = null,
    val testRunning: Boolean = false,
    /** What the test task said when it finished. */
    val testSummary: String? = null,
    val testPassed: Boolean? = null,
    /** Diagnose: busy, the last check's result, and its log lines. */
    val diagnoseBusy: Boolean = false,
    val diagnoseStatus: String = "",
    val diagnoseLog: List<String> = emptyList(),
    val canContinue: Boolean = true,
)

/**
 * First-run setup, in the order the canvas shows: welcome, name, AI key, model,
 * permissions (the VPN and the phone's services), a test run, its result, and
 * a diagnose page when it didn't work.
 */
public class OnboardingPresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalog,
    private val session: PhoneSession,
    private val runner: TaskRunner,
    private val handsCheck: HandsCheck,
) : Presenter<OnboardingUiState>(parent, OnboardingUiState()) {

    init {
        val s = settings.settings.value
        set { it.copy(name = s.userName.orEmpty(), selectedModel = s.model, hasSavedKey = s.hasApiKey) }
        scope.launch { catalog.refresh() }
        scope.launch {
            catalog.state.collect { models ->
                set { st -> st.copy(models = models.recommended.map { ModelChoice(it.id, it.name, it.costPerTaskUsd, it.tag, it.note, it.id == st.selectedModel, false) }) }
            }
        }
        scope.launch {
            handsCheck.state.collect { h -> set { it.copy(diagnoseBusy = h.busy, diagnoseStatus = h.status, diagnoseLog = h.log) } }
        }
    }

    public fun setName(name: String): Unit = set { it.copy(name = name) }

    public fun setKey(key: String): Unit = set { it.copy(key = key.trim(), keyLooksRight = key.trim().startsWith(KEY_PREFIX) && key.trim().length > MIN_KEY) }

    public fun chooseModel(id: String): Unit = set { st ->
        st.copy(selectedModel = id, models = st.models.map { it.copy(isMain = it.id == id) })
    }

    /** Saves this step's answer and moves on. */
    public fun next() {
        val st = state.value
        if (!st.canContinue) return
        when (st.step) {
            OnboardingStep.Name -> settings.update { it.copy(userName = st.name) }
            OnboardingStep.Key -> if (st.key.isNotBlank()) settings.apiKey = st.key
            OnboardingStep.Model -> st.selectedModel?.let { id -> settings.update { it.copy(model = id) } }
            else -> Unit
        }
        go(
            when (st.step) {
                OnboardingStep.Welcome -> OnboardingStep.Name
                OnboardingStep.Name -> OnboardingStep.Key
                OnboardingStep.Key -> OnboardingStep.Model
                OnboardingStep.Model -> OnboardingStep.Permissions
                OnboardingStep.Permissions -> OnboardingStep.TestRun
                OnboardingStep.TestRun -> OnboardingStep.TestResult
                OnboardingStep.TestResult, OnboardingStep.Diagnose -> OnboardingStep.Finished
                OnboardingStep.Finished -> OnboardingStep.Finished
            },
        )
    }

    public fun back() {
        val previous = when (state.value.step) {
            OnboardingStep.Name -> OnboardingStep.Welcome
            OnboardingStep.Key -> OnboardingStep.Name
            OnboardingStep.Model -> OnboardingStep.Key
            OnboardingStep.Permissions -> OnboardingStep.Model
            OnboardingStep.TestRun -> OnboardingStep.Permissions
            OnboardingStep.Diagnose -> OnboardingStep.TestResult
            else -> return
        }
        go(previous)
    }

    /** iOS asks to add Mode's VPN; then Mode reaches the phone's own services. */
    public fun connect() {
        set { it.copy(connecting = true, connectError = null) }
        scope.launch {
            val error = try {
                session.reconnect()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: e.toString()
            }
            set { it.copy(connecting = false, connectError = error) }
            if (error == null) go(OnboardingStep.TestRun)
        }
    }

    /**
     * The one fixed task in Mode, agreed for setup only: open Settings, tap
     * Wi-Fi, come back. It proves the hands work before the user relies on them.
     */
    public fun runTest() {
        set { it.copy(testRunning = true, testSummary = null, testPassed = null) }
        scope.launch {
            val result = runner.runTask(TEST_TASK, settings.agentConfig(), tag = TEST_TAG)
            val summary = when (val o = result?.outcome) {
                is AgentOutcome.Done -> o.summary
                is AgentOutcome.Stopped -> o.why
                is AgentOutcome.Failed -> o.why
                null -> runner.state.value.status
            }
            set { it.copy(testRunning = false, testSummary = summary) }
            go(OnboardingStep.TestResult)
        }
    }

    /** The user saw it work (or not). Not working opens Diagnose. */
    public fun confirmTest(worked: Boolean) {
        set { it.copy(testPassed = worked) }
        if (worked) finish() else go(OnboardingStep.Diagnose)
    }

    /** Diagnose: reconnect and look at the screen, step by step, with a log. */
    public fun diagnose() {
        scope.launch {
            handsCheck.connect()
            handsCheck.lookHere()
        }
    }

    /** Ends setup (also "Skip for now"); the app opens on Home from now on. */
    public fun finish() {
        settings.update { it.copy(onboardingDone = true) }
        go(OnboardingStep.Finished)
    }

    private fun go(step: OnboardingStep) = set { it.copy(step = step) }

    private fun set(change: (OnboardingUiState) -> OnboardingUiState) {
        mutableState.update { st ->
            val next = change(st)
            next.copy(
                canContinue = when (next.step) {
                    OnboardingStep.Name -> next.name.isNotBlank()
                    OnboardingStep.Key -> next.keyLooksRight || (next.key.isBlank() && next.hasSavedKey)
                    OnboardingStep.Model -> next.selectedModel != null
                    OnboardingStep.Permissions -> !next.connecting
                    OnboardingStep.TestRun -> !next.testRunning
                    else -> true
                },
            )
        }
    }

    public companion object {
        public const val TEST_TASK: String = "Open Settings, tap Wi-Fi, then come back to Mode."
        public const val TEST_TAG: String = "setup-test"
        private const val KEY_PREFIX = "sk-or-"
        private const val MIN_KEY = 20
    }
}
