package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.app.screens.conversation.ConversationPresenter
import com.piyush.mode.app.screens.home.HomePresenter
import com.piyush.mode.app.screens.repeat.RepeatPresenter
import com.piyush.mode.app.screens.run.LiveRunPresenter
import com.piyush.mode.app.screens.run.RunScreensPresenter
import com.piyush.mode.app.screens.memory.MemoryPresenter
import com.piyush.mode.app.screens.onboarding.OnboardingPresenter
import com.piyush.mode.app.screens.settings.SettingsPresenter
import com.piyush.mode.feature.connection.presentation.PhoneSession
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
    private val clock: ScreenClock = ScreenClock(),
) {
    public fun home(): HomePresenter = HomePresenter(scope, settings, runner, queue, tasks, conversations, memory, clock)

    public fun tasks(): TasksPresenter = TasksPresenter(scope, runner, queue, scheduler, tasks, clock)

    public fun conversation(conversationId: String): ConversationPresenter =
        ConversationPresenter(scope, conversationId, conversations, runner, queue, tasks, runs, clock)

    public fun liveRun(): LiveRunPresenter = LiveRunPresenter(scope, runner, tasks)

    public fun runScreens(taskId: String): RunScreensPresenter = RunScreensPresenter(scope, taskId, runs, tasks)

    public fun memory(): MemoryPresenter = MemoryPresenter(scope, memory, settings, clock)

    public fun settings(): SettingsPresenter = SettingsPresenter(scope, settings, catalog)

    public fun onboarding(): OnboardingPresenter = OnboardingPresenter(scope, settings, catalog, session, runner, permissions, phoneChecks, auth)

    /** Whether first-run setup still has to be shown. */
    public val needsOnboarding: Boolean get() = !settings.settings.value.onboardingDone

    /** The Repeat sheet, filled with [text] (the task to repeat) or empty. */
    public fun repeat(text: String = ""): RepeatPresenter = RepeatPresenter(scope, text, scheduler, clock)
}
