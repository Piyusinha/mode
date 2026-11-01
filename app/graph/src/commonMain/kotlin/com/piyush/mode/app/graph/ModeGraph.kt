package com.piyush.mode.app.graph

import com.piyush.mode.core.common.ActivityTracker
import com.piyush.mode.core.platform.AppInfo
import com.piyush.mode.core.platform.AppLifecycle
import com.piyush.mode.core.platform.BackgroundWork
import com.piyush.mode.core.platform.Notifier
import com.piyush.mode.core.storage.FileStore
import com.piyush.mode.core.storage.KeyValueStore
import com.piyush.mode.core.storage.SecretStore
import com.piyush.mode.feature.compare.presentation.CompareRunner
import com.piyush.mode.feature.connection.domain.PhoneLink
import com.piyush.mode.feature.connection.presentation.IdleDisconnectPolicy
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.diagnostics.presentation.HandsCheck
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.feature.settings.data.AgentSettingsRepository
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import com.piyush.mode.llm.OpenRouterClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    val settings: AgentSettingsRepository = AgentSettingsRepository(platform.secrets, platform.prefs)
    val session: PhoneSession = PhoneSession(platform.phoneLink)
    val runs: RunRepository = RunRepository(platform.files)

    val tasks: TaskRunner = TaskRunner(
        session = session,
        settings = settings,
        llm = OpenRouterClient(platform.http, apiKey = { settings.apiKey }),
        files = platform.files,
        notifier = platform.notifier,
        background = platform.background,
        appInfo = platform.appInfo,
        activity = activity,
        scope = scope,
    )

    val handsCheck: HandsCheck = HandsCheck(session, platform.background, platform.appInfo, activity)
    val compare: CompareRunner = CompareRunner(tasks, session, settings, platform.files, activity)

    init {
        IdleDisconnectPolicy(session, activity, scope).install(platform.lifecycle)
    }
}
