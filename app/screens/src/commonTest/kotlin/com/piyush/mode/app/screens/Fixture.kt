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
import kotlinx.coroutines.async
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

/** A lifecycle the test moves by hand. */
internal class FakeLifecycle : com.piyush.mode.core.platform.AppLifecycle {
    private val foreground = mutableListOf<() -> Unit>()
    override fun onBackground(action: () -> Unit) {}
    override fun onForeground(action: () -> Unit) { foreground += action }
    override fun onTerminate(action: () -> Unit) {}
    fun cameBack() = foreground.forEach { it() }
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
    override val steps = com.piyush.mode.feature.connection.domain.PhoneCheckId.entries.take(8)
    /** What [status] reads (Android's quick look at its settings). */
    var quick: List<com.piyush.mode.feature.connection.domain.PhoneCheckResult> = emptyList()
    override suspend fun status() = quick
    override fun ready() = paired
    override suspend fun permitted() = vpn
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
 * A model that never answers. A task given to it still ends, and soon: the presenters run on the test's virtual
 * clock, where every timeout comes due the moment nothing else is waiting, so "Mode couldn't reach your AI"
 * arrives after about 10 ms of real time. It only keeps a scripted answer from finishing the task at once;
 * [awaitState] is what makes a passing state ("sent", "working") safe to wait for.
 */
internal val NeverAnswers = LlmClient { _, _, _ -> kotlinx.coroutines.awaitCancellation() }

/**
 * Waits for the presenter's state to match and returns that state, giving up after 5 s of real time (the
 * database flows behind a presenter emit on a background thread, not the test's clock).
 *
 * The watching happens on the test's own dispatcher, in step with the presenter, so a state that only lasts a
 * moment is seen; only the giving up is on a real clock. Watching from a real thread, as this did before, raced
 * the virtual clock: "sent" lasts about 10 ms before a task with no AI fails, a busy CI runner looked too late,
 * and one test or another timed out on most runs (13 Nov, PR 1) while a fast Mac never missed.
 */
internal suspend fun <S> kotlinx.coroutines.flow.StateFlow<S>.awaitState(test: (S) -> Boolean): S =
    kotlinx.coroutines.coroutineScope {
        val seen = async { first(test) }
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                kotlinx.coroutines.withTimeout(5_000) { seen.await() }
            }
        } finally {
            seen.cancel()
        }
    }
