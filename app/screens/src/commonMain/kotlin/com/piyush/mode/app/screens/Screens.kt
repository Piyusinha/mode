package com.piyush.mode.app.screens

import com.piyush.mode.feature.settings.data.ClaudeAccount

import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.app.screens.conversation.ConversationPresenter
import com.piyush.mode.app.screens.home.HomePresenter
import com.piyush.mode.app.screens.repeat.RepeatPresenter
import com.piyush.mode.app.screens.run.LiveActivityPresenter
import com.piyush.mode.app.screens.run.LiveRunPresenter
import com.piyush.mode.app.screens.run.RunScreensPresenter
import com.piyush.mode.app.screens.memory.MemoryPresenter
import com.piyush.mode.app.screens.onboarding.OnboardingPresenter
import com.piyush.mode.app.screens.settings.SettingsPresenter
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.core.platform.AppLifecycle
import com.piyush.mode.core.platform.Permissions
import com.piyush.mode.feature.connection.domain.PhoneChecks
import com.piyush.mode.llm.OpenRouterAuth
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.feature.settings.data.ModelCatalog
import com.piyush.mode.app.screens.tasks.TasksPresenter
import com.piyush.mode.feature.conversations.data.ConversationRepository
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.schedules.presentation.Scheduler
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.presentation.TaskQueue
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import kotlinx.coroutines.CoroutineScope

/**
 * Makes each screen's presenter. A native screen asks for its presenter when
 * it appears and closes it when it goes away.
 */
public class Screens(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val runner: TaskRunner,
    private val queue: TaskQueue,
    private val scheduler: Scheduler,
    private val tasks: TaskRepository,
    private val conversations: ConversationRepository,
    private val memory: MemoryRepository,
    private val runs: RunRepository,
    private val catalog: ModelCatalog,
    private val session: PhoneSession,
    private val permissions: Permissions,
    private val phoneChecks: PhoneChecks,
    private val auth: OpenRouterAuth,
    private val lifecycle: AppLifecycle,
    private val chatGpt: com.piyush.mode.feature.settings.data.ChatGptAccount,
    private val chatGptLlm: com.piyush.mode.llm.LlmClient,
    private val clock: ScreenClock = ScreenClock(),
    private val claude: ClaudeAccount? = null,
    private val claudeLlm: com.piyush.mode.llm.LlmClient? = null,
    /** Every provider's client (tasks pick one per provider), for measuring models. */
    private val providers: com.piyush.mode.llm.LlmClient? = null,
    private val bench: com.piyush.mode.feature.settings.data.ModelBench? = null,
    private val claudeApi: com.piyush.mode.llm.ClaudeAccountApi? = null,
    private val openRouterApi: com.piyush.mode.llm.OpenRouterAccountApi? = null,
    /** The Claude account's model list (null: Mode's two defaults). */
    private val claudeModels: com.piyush.mode.feature.settings.data.ClaudeModels? = null,
    private val voices: com.piyush.mode.feature.settings.voice.Voices? = null,
    private val telemetry: com.piyush.mode.core.telemetry.Telemetry = com.piyush.mode.core.telemetry.Telemetry.None,
    /** The last lines of Mode's log (Health → Share report, debugging a task). */
    private val logTail: () -> String = { "" },
    /** Whether the iPhone is locked (Unlock by voice's check). */
    private val deviceLock: com.piyush.mode.core.platform.DeviceLock = com.piyush.mode.core.platform.DeviceLock.None,
    private val background: com.piyush.mode.core.platform.BackgroundWork? = null,
) {
    /** The models on the plan in use (ChatGPT, Claude): phone control, brain, effort, measuring. */
    /** How much Mode does on its own (Settings → Access). */
    /** [hearing] is the transcription list; otherwise the speech list. */
    public fun voice(hearing: Boolean): com.piyush.mode.app.screens.settings.VoicePresenter =
        com.piyush.mode.app.screens.settings.VoicePresenter(scope, settings, voices!!, hearing)

    /** Choose a voice, starting on [provider] (Apple, ElevenLabs, Fish Audio). */
    public fun voicePicker(provider: String): com.piyush.mode.app.screens.settings.VoicePickerPresenter =
        com.piyush.mode.app.screens.settings.VoicePickerPresenter(
            scope, settings, voices!!,
            com.piyush.mode.feature.settings.domain.VoiceKind.fromId(provider) ?: com.piyush.mode.feature.settings.domain.VoiceKind.Apple,
        )

    /** Talking mode; [conversationId] (from a chat's mic) puts the spoken task in that chat, else a new one. */
    /** What went wrong in a task that couldn't finish, and questions about it. */
    public fun taskDebug(taskId: String): com.piyush.mode.app.screens.debug.TaskDebugPresenter =
        com.piyush.mode.app.screens.debug.TaskDebugPresenter(
            scope, taskId, tasks, runs, telemetry, settings, session, phoneChecks, queue, providers, chatGpt, claude,
        )

    /** Settings → Health & repair. */
    public fun health(): com.piyush.mode.app.screens.settings.HealthPresenter =
        com.piyush.mode.app.screens.settings.HealthPresenter(
            scope, settings, session, phoneChecks, runner, voices, permissions, providers, chatGpt, claude, logTail,
        )

    /** Settings → Listening → Language. */
    public fun language(): com.piyush.mode.app.screens.settings.LanguagePresenter =
        com.piyush.mode.app.screens.settings.LanguagePresenter(scope, settings, voices!!)

    public fun listen(conversationId: String?, entry: String): com.piyush.mode.app.screens.settings.ListenPresenter =
        com.piyush.mode.app.screens.settings.ListenPresenter(
            scope, voices!!, queue, settings, runner, clock, conversationId = conversationId, telemetry = telemetry, entry = entry,
            acknowledger = acknowledger,
        )

    /** What Mode says when handed a spoken task, written by the fast model (phone control's). */
    private val acknowledger: com.piyush.mode.feature.memory.data.Acknowledger? by lazy {
        providers?.let { llm ->
            com.piyush.mode.feature.memory.data.Acknowledger(llm, memory) {
                val s = settings.settings.value
                // One spoken line needs no thinking: the model's lowest effort, so it comes back in time.
                com.piyush.mode.feature.settings.domain.ProviderModels.quick(
                    s.model, s.provider, chatGpt.modelInfo.value + claudeModels?.known?.value.orEmpty(),
                )
            }
        }
    }

    /** Records that the app followed a link (Siri's listen, a mic, a Live Activity), for the trace. */
    public fun linkOpened(entry: String) {
        runCatching { telemetry.event(com.piyush.mode.core.telemetry.Event.LinkOpened, fields = mapOf(com.piyush.mode.core.telemetry.Field.Entry to entry)) }
    }

    /** Records that Siri or Shortcuts ran one of Mode's intents, and what Mode did ([outcome]). */
    public fun intentRan(listen: Boolean, outcome: String) {
        runCatching {
            telemetry.event(
                if (listen) com.piyush.mode.core.telemetry.Event.IntentStartListening else com.piyush.mode.core.telemetry.Event.IntentAsk,
                fields = mapOf(com.piyush.mode.core.telemetry.Field.Outcome to outcome),
            )
        }
    }

    /** Talking mode for a question in a spoken task: Mode came forward to ask it out loud. */
    public fun talkQuestion(): com.piyush.mode.app.screens.settings.ListenPresenter =
        com.piyush.mode.app.screens.settings.ListenPresenter(scope, voices!!, queue, settings, runner, clock, forQuestion = true,
            telemetry = telemetry, entry = "question")

    public fun access(): com.piyush.mode.app.screens.settings.AccessPresenter =
        com.piyush.mode.app.screens.settings.AccessPresenter(scope, settings)

    public fun planModels(): com.piyush.mode.app.screens.settings.PlanModelsPresenter =
        com.piyush.mode.app.screens.settings.PlanModelsPresenter(scope, settings, chatGpt, bench!!, providers!!, claudeModels)

    /** One provider's page: account, usage, models. [id] is a ModelProvider id. */
    public fun providerDetail(id: String): com.piyush.mode.app.screens.settings.ProviderDetailPresenter =
        com.piyush.mode.app.screens.settings.ProviderDetailPresenter(
            scope, id, settings, chatGpt, claude, claudeApi!!, openRouterApi!!, runs, catalog, clock,
        )

    public fun home(): HomePresenter = HomePresenter(scope, settings, runner, queue, tasks, conversations, memory, clock)

    public fun tasks(): TasksPresenter = TasksPresenter(scope, runner, queue, scheduler, tasks, clock)

    public fun conversation(conversationId: String): ConversationPresenter =
        ConversationPresenter(scope, conversationId, conversations, runner, queue, tasks, runs, clock, teacher, memory, voices)

    /** Turns what the user teaches into tips, written by the brain's model. */
    private val teacher: com.piyush.mode.feature.memory.data.Teacher? by lazy {
        providers?.let { llm ->
            com.piyush.mode.feature.memory.data.Teacher(llm, memory) {
                val s = settings.settings.value
                (s.strongModel ?: s.model) to s.provider
            }
        }
    }

    public fun liveRun(): LiveRunPresenter = LiveRunPresenter(scope, runner, tasks)

    /** The Live Activity: one for the app's lifetime. */
    public fun liveActivity(): LiveActivityPresenter = LiveActivityPresenter(scope, runner, tasks, queue, settings)

    /** The "Mode is connected" Live Activity, with Disconnect: one for the app's lifetime. */
    public fun sessionActivity(): com.piyush.mode.app.screens.run.SessionActivityPresenter =
        com.piyush.mode.app.screens.run.SessionActivityPresenter(scope, runner, session, clock)

    /** The first run: Welcome, Connect your AI, a first job (asking for the VPN and notifications as it starts). */
    public fun firstRun(): com.piyush.mode.app.screens.firstrun.FirstRunPresenter =
        com.piyush.mode.app.screens.firstrun.FirstRunPresenter(scope, settings, connectChatGpt(), queue) {
            if (!phoneChecks.vpnAllowed()) {
                try {
                    session.reconnect()
                } finally {
                    session.disconnect()
                }
            }
            permissions.request(com.piyush.mode.core.platform.Permission.Notifications)
        }

    /** Settings → Unlock by voice: the guide, and the check that it works. */
    public fun unlockVoice(): com.piyush.mode.app.screens.start.UnlockVoicePresenter =
        com.piyush.mode.app.screens.start.UnlockVoicePresenter(scope, settings, deviceLock, background, telemetry)

    public fun runScreens(taskId: String): RunScreensPresenter = RunScreensPresenter(scope, taskId, runs, tasks, runner, queue, teacher)

    public fun memory(): MemoryPresenter = MemoryPresenter(scope, memory, settings, clock)

    public fun settings(): SettingsPresenter = SettingsPresenter(
        scope, settings, catalog, claude, claudeLlm, claudeModels = { claudeModels?.known?.value.orEmpty() },
        chatGptModels = { chatGpt.knownModels.value }, voices = voices,
    )

    /** The AI providers: connected ones, the one in use, and connecting more (setup and Settings). */
    public fun providers(): com.piyush.mode.app.screens.settings.ProvidersPresenter =
        com.piyush.mode.app.screens.settings.ProvidersPresenter(scope, settings, chatGpt, auth, claude, claudeLlm)

    public fun connectChatGpt(): com.piyush.mode.app.screens.settings.ConnectChatGptPresenter =
        com.piyush.mode.app.screens.settings.ConnectChatGptPresenter(scope, chatGpt, chatGptLlm) {
            settings.selectProvider(com.piyush.mode.llm.ModelProvider.ChatGpt)
        }

    public fun onboarding(): OnboardingPresenter = OnboardingPresenter(
        scope, settings, catalog, session, runner, permissions, phoneChecks, auth, lifecycle,
        claudeModels = { claudeModels?.known?.value.orEmpty() }, chatGptModels = { chatGpt.knownModels.value }, voices = voices,
    )

    /** Whether first-run setup still has to be shown. */
    public val needsOnboarding: Boolean get() = !settings.settings.value.onboardingDone

    /** The Repeat sheet, filled with [text] (the task to repeat) or empty. */
    public fun repeat(text: String = ""): RepeatPresenter = RepeatPresenter(scope, text, scheduler, clock)
}
