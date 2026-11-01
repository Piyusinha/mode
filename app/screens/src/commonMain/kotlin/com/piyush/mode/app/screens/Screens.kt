package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.app.screens.home.HomePresenter
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
    private val clock: ScreenClock = ScreenClock(),
) {
    public fun home(): HomePresenter = HomePresenter(scope, settings, runner, queue, tasks, conversations, memory, clock)

    public fun tasks(): TasksPresenter = TasksPresenter(scope, runner, queue, scheduler, tasks, clock)
}
