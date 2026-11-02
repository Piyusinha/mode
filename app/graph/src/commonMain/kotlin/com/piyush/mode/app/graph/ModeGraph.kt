package com.piyush.mode.app.graph

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
)

/**
 * Every feature, built once and wired together. Screens (SwiftUI, Compose)
 * take what they need from here; nothing else constructs features.
 */
class ModeGraph(platform: PlatformServices) {
    /** Lives as long as the app; work started here outlives any one screen. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val activity: ActivityTracker = ActivityTracker()
    val files: FileStore = platform.files
    val database: ModeDatabase = platform.database.create()

    val settings: SettingsRepository = SettingsRepository(platform.secrets, platform.prefs)
    /** The models to pick from: OpenRouter's live list, cached for a day. */
    val models: ModelCatalog = ModelCatalog(fetch = { OpenRouterModels(platform.http).fetchRaw() }, files = platform.files)
    val session: PhoneSession = PhoneSession(platform.phoneLink)
    val runs: RunRepository = RunRepository(platform.files, database)
    val conversations: ConversationRepository = ConversationRepository(database)
    val taskHistory: TaskRepository = TaskRepository(database)
    val claude: ClaudeAccount = ClaudeAccount(settings.claudeCredentials, ClaudeOAuth(platform.http))
    private val router = OpenRouterClient(platform.http, apiKey = { settings.apiKey })
    private val claudeClient = ClaudeOAuthClient(platform.http, token = { force -> claude.accessToken(force) })
    /** "Continue with ChatGPT": the account and the plan's Responses API. */
    val chatGpt: com.piyush.mode.feature.settings.data.ChatGptAccount = com.piyush.mode.feature.settings.data.ChatGptAccount(
        com.piyush.mode.llm.ChatGptAuth(platform.http), platform.secrets, onChange = { settings.accountsChanged() },
    )
    private val chatGptLlm = com.piyush.mode.llm.ResponsesClient(
        platform.http, accessToken = { chatGpt.accessToken() }, onLimit = { chatGpt.limitReached() },
    )
    private val llm = ProviderClients(
        mapOf(ModelProvider.OpenRouter to router, ModelProvider.ClaudeOAuth to claudeClient, ModelProvider.ChatGpt to chatGptLlm),
    )
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
    )

    /** The one way in for tasks from the app, Siri, Shortcuts, the Watch and schedules. */
    val inbox: TaskQueue = TaskQueue(tasks, taskHistory, conversations, scope)

    /** Tasks that repeat; due ones go to the inbox, with a notification for when Mode is closed. */
    val scheduler: Scheduler = Scheduler(ScheduleRepository(database), inbox, platform.notifier, scope)

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
    )

    val compare: CompareRunner = CompareRunner(tasks, session, settings, platform.files, activity)

    init {
        IdleDisconnectPolicy(session, activity, scope).install(platform.lifecycle)
        scope.launch { models.refresh() }
        // Tasks left from the last session: interrupted ones are marked, fresh ones run.
        inbox.recover()
        scheduler.start(platform.lifecycle)
        scope.launch {
            runCatching { runs.backfill() }
                .onSuccess { if (it > 0) AppLog.log("runs", "listed $it earlier runs") }
                .onFailure { AppLog.error("runs", "could not list earlier runs", it) }
        }
    }
}
