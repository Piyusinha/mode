package com.piyush.mode.app.graph

import com.piyush.mode.core.telemetry.Operation
import com.piyush.mode.core.telemetry.Field
import com.piyush.mode.core.telemetry.trace
import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.llm.ObservedLlmClient

import com.piyush.mode.core.common.ActivityTracker
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.database.DatabaseFactory
import com.piyush.mode.core.database.ModeDatabase
import com.piyush.mode.core.platform.AppInfo
import com.piyush.mode.core.platform.AppLifecycle
import com.piyush.mode.core.platform.BackgroundWork
import com.piyush.mode.core.platform.Notifier
import com.piyush.mode.core.storage.FileStore
import com.piyush.mode.core.storage.KeyValueStore
import com.piyush.mode.core.storage.SecretStore
import com.piyush.mode.feature.compare.presentation.CompareRunner
import com.piyush.mode.feature.connection.domain.PhoneLink
import com.piyush.mode.feature.conversations.data.ConversationRepository
import com.piyush.mode.feature.connection.presentation.IdleDisconnectPolicy
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.diagnostics.presentation.HandsCheck
import com.piyush.mode.core.common.startOfTodayMillis
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.data.MemoryRetrieval
import com.piyush.mode.feature.memory.data.Reflector
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.feature.tasks.data.TaskMemory
import com.piyush.mode.feature.settings.data.ModelCatalog
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.app.screens.Screens
import com.piyush.mode.feature.schedules.data.ScheduleRepository
import com.piyush.mode.feature.schedules.presentation.Scheduler
import com.piyush.mode.feature.tasks.presentation.TaskQueue
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import com.piyush.mode.llm.OpenRouterClient
import com.piyush.mode.llm.OpenRouterModels
import com.piyush.mode.llm.ClaudeOAuth
import com.piyush.mode.llm.ClaudeOAuthClient
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.ProviderClients
import com.piyush.mode.feature.settings.data.ClaudeAccount
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What each platform provides; everything else is built from these. */
class PlatformServices(
    val secrets: SecretStore,
    val prefs: KeyValueStore,
    /** Logs, runs and screenshots. */
    val files: FileStore,
    val notifier: Notifier,
    val background: BackgroundWork,
    val lifecycle: AppLifecycle,
    val appInfo: AppInfo,
    val http: HttpClient,
    val phoneLink: PhoneLink,
    /** Opens the app's database. */
    val database: DatabaseFactory,
    val permissions: com.piyush.mode.core.platform.Permissions,
    /** Each stage of reaching this iPhone, for the Diagnose screen. */
    val phoneChecks: com.piyush.mode.feature.connection.domain.PhoneChecks,
    val telemetry: Telemetry = Telemetry.None,
    /** Links, the calendar and contacts from the app itself (None where the platform has nothing). */
    val device: com.piyush.mode.ops.DeviceData = com.piyush.mode.ops.DeviceData.None,
    /** Whether the iPhone is locked: tasks wait for an unlock, and Unlock by voice is tested with it. */
    val deviceLock: com.piyush.mode.core.platform.DeviceLock = com.piyush.mode.core.platform.DeviceLock.None,
    /** This build's version (CFBundleShortVersionString), written into backups. */
    val appVersion: String = "unknown",
    /** Where backups are written and picked ones read. */
    val backupFiles: com.piyush.mode.feature.backup.data.BackupFiles,
    /** Which phone this is: the agent's words, status bar and tools. */
    val profile: com.piyush.mode.agent.PhoneProfile = com.piyush.mode.agent.PhoneProfile.IPhone,
)

/**
 * Every feature, built once and wired together. Screens (SwiftUI, Compose)
 * take what they need from here; nothing else constructs features.
 */
class ModeGraph(platform: PlatformServices) {
    /** Lives as long as the app; work started here outlives any one screen. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val activity: ActivityTracker = ActivityTracker()
    val telemetry: Telemetry = platform.telemetry
    val files: FileStore = platform.files
    val database: ModeDatabase = platform.database.create()
    private val secrets: SecretStore = platform.secrets
    private val prefs: com.piyush.mode.core.storage.KeyValueStore = platform.prefs

    val settings: SettingsRepository = SettingsRepository(platform.secrets, platform.prefs)
    /** Speaks and hears (one instance: the runner and the Settings lists share it). */
    private val voices = com.piyush.mode.feature.settings.voice.Voices(settings, platform.http, telemetry = telemetry)
    /** The models to pick from: OpenRouter's live list, cached for a day. */
    val models: ModelCatalog = ModelCatalog(fetch = { OpenRouterModels(platform.http).fetchRaw() }, files = platform.files)
    val session: PhoneSession = PhoneSession(platform.phoneLink, telemetry = telemetry)
    val runs: RunRepository = RunRepository(platform.files, database)
    val conversations: ConversationRepository = ConversationRepository(database)
    val taskHistory: TaskRepository = TaskRepository(database)
    val claude: ClaudeAccount = ClaudeAccount(settings.claudeCredentials, ClaudeOAuth(platform.http))
    private val router = OpenRouterClient(platform.http, apiKey = { settings.apiKey })
    private val claudeClient = ClaudeOAuthClient(platform.http, token = { force ->
        telemetry.trace(Operation.AccessToken, mapOf(Field.Provider to ModelProvider.ClaudeOAuth.name)) {
            claude.accessToken(force)
        }
    })
    /** "Continue with ChatGPT": the account and the plan's Responses API. */
    val chatGpt: com.piyush.mode.feature.settings.data.ChatGptAccount = com.piyush.mode.feature.settings.data.ChatGptAccount(
        com.piyush.mode.llm.ChatGptAuth(platform.http), platform.secrets, onChange = { settings.accountsChanged() },
    )
    private val chatGptLlm = com.piyush.mode.llm.ResponsesClient(
        platform.http,
        accessToken = {
            telemetry.trace(Operation.AccessToken, mapOf(Field.Provider to ModelProvider.ChatGpt.name)) {
                chatGpt.accessToken()
            }
        },
        onLimit = { chatGpt.limitReached() },
    )
    private val llm = ObservedLlmClient(ProviderClients(
        mapOf(ModelProvider.OpenRouter to router, ModelProvider.ClaudeOAuth to claudeClient, ModelProvider.ChatGpt to chatGptLlm),
    ), telemetry)
    /** What Mode remembers about the user, skills and app tips; on this iPhone only. */
    val memory: MemoryRepository = MemoryRepository(database)

    val tasks: TaskRunner = TaskRunner(
        session = session,
        settings = settings,
        llm = llm,
        files = platform.files,
        runs = runs,
        conversations = conversations,
        tasks = taskHistory,
        notifier = platform.notifier,
        background = platform.background,
        appInfo = platform.appInfo,
        activity = activity,
        scope = scope,
        memory = TaskMemory(memory, MemoryRetrieval(memory), Reflector(llm, memory), taskHistory, ::startOfTodayMillis),
        voices = voices,
        telemetry = telemetry,
        narrator = com.piyush.mode.feature.tasks.presentation.Narrator(llm, ::quickModel),
        translator = com.piyush.mode.feature.tasks.presentation.Translator(llm, ::quickModel),
        titler = com.piyush.mode.feature.tasks.presentation.ChatTitler(llm, ::quickModel),
        device = platform.device,
        profile = platform.profile,
    )

    /** The one way in for tasks from the app, Siri, Shortcuts, the Watch and schedules. */
    val deviceLock: com.piyush.mode.core.platform.DeviceLock = platform.deviceLock
    val inbox: TaskQueue = TaskQueue(tasks, taskHistory, conversations, scope, lock = platform.deviceLock, background = platform.background, telemetry = telemetry).also { queue ->
        tasks.onFollowUp = { text, conversation -> queue.submit(text, com.piyush.mode.feature.tasks.domain.TaskSource.Voice, conversation, false) }
    }

    /** Tasks that repeat; due ones go to the inbox, with a notification for when Mode is closed. */
    val scheduler: Scheduler = Scheduler(ScheduleRepository(database), inbox, platform.notifier, scope)

    /** A routine about due is suggested (never run) when Mode comes to the front, if the setting is on. */
    val routines: com.piyush.mode.feature.tasks.presentation.RoutineSuggester =
        com.piyush.mode.feature.tasks.presentation.RoutineSuggester(taskHistory, settings, platform.notifier, platform.prefs)

    private val claudeModels = com.piyush.mode.feature.settings.data.ClaudeModels(platform.prefs, list = { claudeClient.models() })

    /** The phone-control model at its lowest effort: quick lines, updates, translation. */
    private fun quickModel(): Pair<String, ModelProvider>? {
        val s = settings.settings.value
        return com.piyush.mode.feature.settings.domain.ProviderModels.fast(s.model, s.provider, chatGpt.modelInfo.value + claudeModels.known.value)
    }

    /**
     * Settings → Backup and restore. After a restore, what was written is read again: settings, the ChatGPT
     * and Claude accounts, and the added repeats' next times and reminders.
     */
    private val backup = com.piyush.mode.feature.backup.data.BackupService(
        database, platform.prefs, platform.secrets, platform.backupFiles,
        deviceName = { settings.settings.value.userName?.let { "$it’s iPhone" } ?: "iPhone" },
        appVersion = platform.appVersion,
        onRestored = { report ->
            settings.reload()
            chatGpt.reload()
            settings.claudeCredentials.reload()
            scheduler.rearm(report.addedRepeatIds)
        },
    )

    val handsCheck: HandsCheck = HandsCheck(session, platform.background, platform.appInfo, activity)
    /** Each screen's presenter, for the native UI. */
    val screens: Screens = Screens(
        scope, settings, tasks, inbox, scheduler, taskHistory, conversations, memory, runs, models, session,
        platform.permissions, platform.phoneChecks, com.piyush.mode.llm.OpenRouterAuth(platform.http), platform.lifecycle,
        claude = claude,
        claudeLlm = claudeClient,
        chatGpt = chatGpt, chatGptLlm = chatGptLlm,
        providers = llm,
        bench = com.piyush.mode.feature.settings.data.ModelBench(platform.prefs),
        claudeApi = com.piyush.mode.llm.ClaudeAccountApi(platform.http),
        openRouterApi = com.piyush.mode.llm.OpenRouterAccountApi(platform.http),
        claudeModels = claudeModels,
        voices = voices,
        telemetry = telemetry,
        deviceLock = platform.deviceLock,
        background = platform.background,
        device = platform.device,
        backupService = backup,
        logTail = { platform.files.read("events.log")?.decodeToString()?.lines()?.takeLast(LOG_TAIL_LINES)?.joinToString("\n").orEmpty() },
    )

    /**
     * Settings → Reset Mode: stops what is running, then deletes everything Mode keeps on this iPhone (chats,
     * tasks, runs and screenshots, memory, schedules, sign-ins and keys, settings, logs). Setup starts again.
     * Phone control (the pairing file, outside these folders) and the VPN stay, so no Mac is needed.
     */
    suspend fun resetEverything() {
        AppLog.log("app", "reset: deleting everything")
        inbox.clear()
        tasks.stop()
        kotlinx.coroutines.withTimeoutOrNull(RESET_WAIT_MS) { tasks.state.first { !it.running } }
        scheduler.deleteAll()
        chatGpt.signOut()
        claude.signOut()
        database.transaction {
            database.turnQueries.deleteAll()
            database.runQueries.deleteAll()
            database.taskQueries.deleteAll()
            database.memoryQueries.deleteAll()
            database.scheduleQueries.deleteAll()
            database.conversationQueries.deleteAll()
        }
        files.delete("")
        secrets.clear()
        prefs.clear()
        settings.reload()
        claudeModels.clear()
        voices.clearProblem()
        AppLog.log("app", "reset: done, setup starts again")
    }

    val compare: CompareRunner = CompareRunner(tasks, session, settings, platform.files, activity)

    init {
        IdleDisconnectPolicy(session, activity, scope).install(platform.lifecycle)
        scope.launch { models.refresh() }
        // Tasks left from the last session: interrupted ones are marked, fresh ones run.
        inbox.recover()
        scheduler.start(platform.lifecycle)
        platform.lifecycle.onForeground { runCatching { routines.check() } }
        runCatching { routines.check() }
        scope.launch {
            runCatching { runs.backfill() }
                .onSuccess { if (it > 0) AppLog.log("runs", "listed $it earlier runs") }
                .onFailure { AppLog.error("runs", "could not list earlier runs", it) }
        }
    }

    private companion object {
        /** How long Reset waits for a stopped task to finish writing its last step. */
        const val RESET_WAIT_MS = 5_000L
        /** Lines of Mode's log a health report or a task's debug includes. */
        const val LOG_TAIL_LINES = 80
    }
}
