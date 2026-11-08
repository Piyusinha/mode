package com.piyush.mode.app.screens

import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.first
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
    /** What Mode reads straight from the iPhone (the calendar), for the greeting's ideas. */
    private val device: com.piyush.mode.ops.DeviceData = com.piyush.mode.ops.DeviceData.None,
    private val background: com.piyush.mode.core.platform.BackgroundWork? = null,
    /** Backup and restore (null where there is none). */
    private val backupService: com.piyush.mode.feature.backup.data.BackupService? = null,
    /** Words about the phone ("this iPhone", where its Settings are). */
    private val profile: com.piyush.mode.agent.PhoneProfile = com.piyush.mode.agent.PhoneProfile.IPhone,
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
            com.piyush.mode.feature.settings.domain.VoiceKind.fromId(provider) ?: com.piyush.mode.feature.settings.domain.VoiceKind.Device,
        )

    /** Talking mode; [conversationId] (from a chat's mic) puts the spoken task in that chat, else a new one. */
    /** What went wrong in a task that couldn't finish, and questions about it. */
    public fun taskDebug(taskId: String): com.piyush.mode.app.screens.debug.TaskDebugPresenter =
        com.piyush.mode.app.screens.debug.TaskDebugPresenter(
            scope, taskId, tasks, runs, telemetry, settings, session, phoneChecks, queue, providers, chatGpt, claude, profile,
        )

    /** Settings → Backup and restore. */
    public fun backup(): com.piyush.mode.app.screens.settings.BackupPresenter =
        com.piyush.mode.app.screens.settings.BackupPresenter(scope, backupService!!, clock)

    /** Settings → Health & repair. */
    public fun health(): com.piyush.mode.app.screens.settings.HealthPresenter =
        com.piyush.mode.app.screens.settings.HealthPresenter(
            scope, settings, session, phoneChecks, runner, voices, permissions, providers, chatGpt, claude, logTail, profile,
        )

    /** Settings → Listening → Language. */
    public fun language(): com.piyush.mode.app.screens.settings.LanguagePresenter =
        com.piyush.mode.app.screens.settings.LanguagePresenter(scope, settings, voices!!)

    public fun listen(conversationId: String?, entry: String): com.piyush.mode.app.screens.settings.ListenPresenter =
        com.piyush.mode.app.screens.settings.ListenPresenter(
            scope, voices!!, queue, settings, runner, clock, conversationId = conversationId, telemetry = telemetry, entry = entry,
            acknowledger = acknowledger, greeter = greeter, day = ::dayContext,
        )

    /** Mode's first words when started by voice, written by the fast model with what's coming up in their day. */
    private val greeter: com.piyush.mode.feature.memory.data.Greeter? by lazy {
        providers?.let { llm ->
            com.piyush.mode.feature.memory.data.Greeter(llm, memory, device = profile.deviceWord, model = {
                val s = settings.settings.value
                com.piyush.mode.feature.settings.domain.ProviderModels.fast(
                    s.model, s.provider, chatGpt.modelInfo.value + claudeModels?.known?.value.orEmpty(),
                )
            })
        }
    }

    /** What's coming up today, read from the iPhone (calendar), the repeats due soon, and the last task. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    private suspend fun dayContext(): com.piyush.mode.feature.memory.data.DayContext {
        val now = clock.now()
        val zone = clock.zone()
        val local = kotlin.time.Instant.fromEpochMilliseconds(now).toLocalDateTime(zone)
        val label = "${local.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} ${local.day} " +
            "${local.month.name.lowercase().replaceFirstChar { it.uppercase() }}, ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        val events = runCatching { device.calendar(1) }.getOrDefault(emptyList()).take(DAY_ITEMS)
        val soon = now + REPEATS_AHEAD_MS
        val repeats = runCatching { scheduler.all.first() }.getOrDefault(emptyList())
            .filter { it.enabled && (it.nextRunAt ?: Long.MAX_VALUE) in now..soon }
            .map { s ->
                val at = kotlin.time.Instant.fromEpochMilliseconds(s.nextRunAt!!).toLocalDateTime(zone)
                "${s.text} at ${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
            }
            .take(DAY_ITEMS)
        val last = tasks.latest(1).firstOrNull()
            ?.takeIf { t -> clock.relativeDay(t.endedAt ?: t.createdAt) == com.piyush.mode.app.screens.common.RelativeDay.Today }
            ?.let { t -> "${t.text} · ${t.status.name.lowercase()}" }
        return com.piyush.mode.feature.memory.data.DayContext(label, events, repeats, last)
    }

    /** What Mode says when handed a spoken task, written by the fast model (phone control's). */
    private val acknowledger: com.piyush.mode.feature.memory.data.Acknowledger? by lazy {
        providers?.let { llm ->
            com.piyush.mode.feature.memory.data.Acknowledger(llm, memory, device = profile.deviceWord, model = {
                val s = settings.settings.value
                // One spoken line needs no thinking: the model's lowest effort, so it comes back in time.
                com.piyush.mode.feature.settings.domain.ProviderModels.fast(
                    s.model, s.provider, chatGpt.modelInfo.value + claudeModels?.known?.value.orEmpty(),
                )
            })
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
            com.piyush.mode.feature.memory.data.Teacher(llm, memory, device = profile.deviceWord, model = {
                val s = settings.settings.value
                (s.strongModel ?: s.model) to s.provider
            })
        }
    }

    public fun liveRun(): LiveRunPresenter = LiveRunPresenter(scope, runner, tasks)

    /** The Live Activity: one for the app's lifetime. */
    public fun liveActivity(): LiveActivityPresenter = LiveActivityPresenter(scope, runner, tasks, queue, settings)

    /** The "Mode is connected" Live Activity, with Disconnect: one for the app's lifetime. */
    public fun sessionActivity(): com.piyush.mode.app.screens.run.SessionActivityPresenter =
        com.piyush.mode.app.screens.run.SessionActivityPresenter(scope, runner, session, clock)

    /** The first run: Welcome, Connect your AI, (on Android) phone control, a first job (asking for what is left as it starts). */
    public fun firstRun(): com.piyush.mode.app.screens.firstrun.FirstRunPresenter {
        val phoneStep = com.piyush.mode.feature.connection.domain.PhoneCheckId.Engine in phoneChecks.steps
        return com.piyush.mode.app.screens.firstrun.FirstRunPresenter(
            scope, settings, connectChatGpt(), queue,
            prepare = {
                // The iPhone asks for its VPN by connecting once; Android set phone control up in its own step.
                if (!phoneStep && !phoneChecks.permitted()) {
                    try {
                        session.reconnect()
                    } finally {
                        session.disconnectAndRelease()
                    }
                }
                permissions.request(com.piyush.mode.core.platform.Permission.Notifications)
            },
            phoneStep = phoneStep,
        )
    }

    /** Setting up phone control (Android): the switches it needs, and a guide for each. */
    public fun phoneSetup(): com.piyush.mode.app.screens.firstrun.PhoneSetupPresenter =
        com.piyush.mode.app.screens.firstrun.PhoneSetupPresenter(scope, phoneChecks, permissions, lifecycle)

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

/** How many calendar events or due repeats the greeting reads. */
private const val DAY_ITEMS = 5

/** Repeats due within this long count as "coming up" for the greeting. */
private const val REPEATS_AHEAD_MS = 3 * 60 * 60 * 1000L
