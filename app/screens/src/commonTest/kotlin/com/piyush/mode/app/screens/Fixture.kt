package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.core.common.ActivityTracker
import com.piyush.mode.core.platform.AppInfo
import com.piyush.mode.core.platform.BackgroundWork
import com.piyush.mode.core.platform.Notifier
import com.piyush.mode.feature.connection.domain.PhoneConnection
import com.piyush.mode.feature.connection.domain.PhoneLink
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.conversations.data.ConversationRepository
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.feature.schedules.data.ScheduleRepository
import com.piyush.mode.feature.schedules.presentation.Scheduler
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.presentation.TaskQueue
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.testing.FakePhone
import com.piyush.mode.testing.InMemoryFileStore
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.testDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlinx.coroutines.flow.first

internal val zone = TimeZone.of("Asia/Kolkata")

/** 1 Nov 2026 (a Sunday) at [hour]:[minute] in India. */
internal fun nov(day: Int, hour: Int, minute: Int = 0): Long = LocalDateTime(2026, 11, day, hour, minute).toInstant(zone).toEpochMilliseconds()

private class Link : PhoneLink {
    override suspend fun connect() = PhoneConnection(FakePhone(List(10) { listOf("Settings" to (100.0 to 100.0), "Wi-Fi" to (100.0 to 200.0)) }), "test", "")
    override suspend fun release() {}
}

private object Inline : BackgroundWork {
    override suspend fun <T> run(work: suspend () -> T): T = work()
}

internal class Notes : Notifier {
    val scheduled = mutableMapOf<String, Long>()
    override fun requestPermission() {}
    override fun post(title: String, body: String) {}
    override fun schedule(id: String, title: String, body: String, atEpochMs: Long) { scheduled[id] = atEpochMs }
    override fun cancel(id: String) { scheduled.remove(id) }
}

internal class FakePermissions : com.piyush.mode.core.platform.Permissions {
    val granted = mutableSetOf<com.piyush.mode.core.platform.Permission>()
    override suspend fun status(permission: com.piyush.mode.core.platform.Permission) =
        if (permission in granted) com.piyush.mode.core.platform.PermissionStatus.Allowed else com.piyush.mode.core.platform.PermissionStatus.NotAsked
    override suspend fun request(permission: com.piyush.mode.core.platform.Permission): com.piyush.mode.core.platform.PermissionStatus {
        granted += permission
        return status(permission)
    }
}

internal class FakeChecks(var paired: Boolean = true) : com.piyush.mode.feature.connection.domain.PhoneChecks {
    var vpn = false
    override fun hasPairingFile() = paired
    override suspend fun vpnAllowed() = vpn
    override suspend fun run(update: (List<com.piyush.mode.feature.connection.domain.PhoneCheckResult>) -> Unit) {
        update(listOf(
            com.piyush.mode.feature.connection.domain.PhoneCheckResult(com.piyush.mode.feature.connection.domain.PhoneCheckId.PairingFile, com.piyush.mode.feature.connection.domain.CheckState.Passed(null)),
            com.piyush.mode.feature.connection.domain.PhoneCheckResult(com.piyush.mode.feature.connection.domain.PhoneCheckId.Paired, com.piyush.mode.feature.connection.domain.CheckState.Failed("unpaired", "Pair again from your Mac.")),
        ))
    }
}

/** Every feature a screen may use, on an in-memory database, at a fixed time. */
internal class Fixture(scope: CoroutineScope, model: LlmClient = ScriptedModel(), var now: Long = nov(1, 8)) {
    val db = testDatabase()
    val clock = ScreenClock(now = { now }, zone = { zone })
    val files = InMemoryFileStore()
    // No strong model, so no brain: each test scripts the hands' replies only.
    val settings = SettingsRepository(InMemorySecretStore(), InMemoryKeyValueStore()).also {
        it.apiKey = "sk-or-test"
        it.update { s -> s.copy(strongModel = null) }
    }
    val tasks = TaskRepository(db, now = { now })
    val conversations = ConversationRepository(db, now = { now })
    val memory = MemoryRepository(db, now = { now })
    val runs = RunRepository(files, db)
    val notes = Notes()
    val session = PhoneSession(Link(), Duration.ZERO)
    val catalog = com.piyush.mode.feature.settings.data.ModelCatalog(fetch = { error("offline") }, files = files)
    val permissions = FakePermissions()
    val checks = FakeChecks()
    val runner = TaskRunner(
        session = session,
        settings = settings,
        llm = model,
        files = files,
        runs = runs,
        conversations = conversations,
        tasks = tasks,
        notifier = notes,
        background = Inline,
        appInfo = object : AppInfo { override val bundleId = "com.piyush.mode" },
        activity = ActivityTracker(),
        scope = scope,
    )
    val queue = TaskQueue(runner, tasks, conversations, scope)
    val scheduler = Scheduler(ScheduleRepository(db), queue, notes, scope, now = { now }, zone = { zone })
}

/**
 * Waits (in real time, up to 5 s) for the presenter's state to match: the
 * database flows behind it emit on a background thread, not the test's clock.
 */
internal suspend fun <S> kotlinx.coroutines.flow.StateFlow<S>.awaitState(test: (S) -> Boolean): S =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        kotlinx.coroutines.withTimeout(5_000) { first(test) }
    }
