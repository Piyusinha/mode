package com.piyush.mode.app

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import com.piyush.mode.core.platform.NotifierHooks
import com.piyush.mode.core.platform.NotifierWords
import com.piyush.mode.core.platform.QuestionWords
import com.piyush.mode.core.platform.WorkNotice
import com.piyush.mode.core.platform.WorkProgress
import com.piyush.mode.app.screens.run.ActivityUiState
import com.piyush.mode.app.screens.run.PlanStepState
import kotlinx.coroutines.launch
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.graph.androidModeGraph

/** Owns the one graph for the whole process (activities, services and receivers all read it). */
class ModeApplication : Application() {
    lateinit var graph: ModeGraph
        private set

    override fun onCreate() {
        super.onCreate()
        voice.install()
        val words = NotifierWords(
            needsYou = getString(R.string.question_title),
            yourTurn = getString(R.string.question_your_turn),
            question = QuestionWords(
                yes = getString(R.string.question_yes), no = getString(R.string.question_no), always = getString(R.string.question_always),
                entered = getString(R.string.question_entered), reply = getString(R.string.question_reply), somethingElse = getString(R.string.question_something_else),
            ),
            resultsChannel = getString(R.string.channel_results),
            questionsChannel = getString(R.string.channel_questions),
            schedulesChannel = getString(R.string.channel_schedules),
        )
        val hooks = NotifierHooks(
            answer = { id, answer ->
                Intent(this, AnswerReceiver::class.java).setAction("$ACTION_ANSWER$id:$answer")
                    .putExtra(AnswerReceiver.QUESTION, id).apply { if (answer != null) putExtra(AnswerReceiver.ANSWER, answer) }
            },
            openConversation = { id ->
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("${com.piyush.mode.app.shell.DeepLink.SCHEME}://conversation/$id"), this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            scheduled = { id, title, body ->
                Intent(this, ScheduleReceiver::class.java).setAction("$ACTION_SCHEDULE$id")
                    .putExtra(ScheduleReceiver.ID, id).putExtra(ScheduleReceiver.TITLE, title).putExtra(ScheduleReceiver.BODY, body)
            },
        )
        graph = androidModeGraph(this, R.drawable.ic_mode_notification, {
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }, words, hooks)
        // The working notification: opens Mode, and Stop ends the task.
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        WorkNotice.content = WorkNotice.plain(
            smallIcon = R.drawable.ic_mode_notification,
            title = getString(R.string.working_title),
            stopLabel = getString(R.string.working_stop),
            open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags),
            stop = PendingIntent.getBroadcast(this, 0, Intent(this, StopReceiver::class.java), flags),
        )
        followTheRunningTask()
    }

    /** A task is running on the hidden screen: Mode shrinks to a small window when the user leaves it. */
    val backgroundTask = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** The user's turn came during a background task: its app is on their screen until they're done. */
    private var userHasTheApp = false

    /**
     * Background mode and "your turn" (a PIN, a password, a private screen): the app comes from the hidden display
     * to the user's screen, and goes back once Mode carries on.
     */
    private fun handOver(s: ActivityUiState) {
        val mode = com.piyush.mode.app.graph.background ?: return
        val theirTurn = s is ActivityUiState.Waiting && s.yourTurn
        when {
            theirTurn && !userHasTheApp && mode.working -> {
                userHasTheApp = true
                kotlinx.coroutines.MainScope().launch { mode.bringToUser() }
            }
            !theirTurn && userHasTheApp -> {
                userHasTheApp = false
                kotlinx.coroutines.MainScope().launch { mode.giveBack() }
            }
        }
    }

    /** Background mode's setup, made when first opened. */
    val backgroundSetup: com.piyush.mode.app.background.BackgroundSetup? by lazy {
        com.piyush.mode.app.graph.background?.let { com.piyush.mode.app.background.BackgroundSetup(this, it) }
    }

    /** The phone's voice is installed before anything speaks or listens. */
    private val voice by lazy {
        com.piyush.mode.app.voice.AndroidDeviceVoice(
            this,
            com.piyush.mode.app.voice.VoiceWords(
                name = { accent, n -> getString(R.string.device_voice_name, accent, n) },
                offline = getString(R.string.device_voice_offline),
                online = getString(R.string.device_voice_online),
            ),
        )
    }

    /** The working notice follows the task: what it's doing now, the plan as a bar, how long it's been going. */
    private fun followTheRunningTask() {
        val activity = graph.screens.liveActivity()
        kotlinx.coroutines.MainScope().launch {
            activity.state.collect { s ->
                WorkNotice.progress = when (s) {
                    is ActivityUiState.Working -> WorkProgress(
                        s.task,
                        if (s.paused) getString(R.string.run_paused) else if (s.step > 0) getString(R.string.run_step_status, s.step, s.now) else s.now,
                        s.plan.count { it.state == PlanStepState.Done }, s.plan.size, s.startedAtMs,
                    )
                    is ActivityUiState.WaitingForUnlock -> WorkProgress(s.task, getString(R.string.working_unlock), 0, 0, null)
                    else -> null
                }
                WorkNotice.refresh(this@ModeApplication)
                handOver(s)
                // Only a real end closes the small window: between steps the state can briefly read otherwise.
                backgroundTask.value = when (s) {
                    is ActivityUiState.Working -> com.piyush.mode.app.graph.background?.working == true
                    is ActivityUiState.Finished, is ActivityUiState.Ended, ActivityUiState.Hidden -> false
                    else -> backgroundTask.value
                }
                // A task ended: the widget's suggestions may have changed.
                if (s is ActivityUiState.Finished) com.piyush.mode.app.widget.ModeWidget.refresh(this@ModeApplication)
            }
        }
    }

    companion object {
        /** Unique actions keep one PendingIntent per question button and per reminder. */
        private const val ACTION_ANSWER = "mode.answer:"
        private const val ACTION_SCHEDULE = "mode.schedule:"

        fun graph(context: android.content.Context): ModeGraph = (context.applicationContext as ModeApplication).graph

        /** Background mode's setup, in builds that can drive the phone. */
        fun backgroundSetup(context: android.content.Context): com.piyush.mode.app.background.BackgroundSetup? =
            (context.applicationContext as ModeApplication).backgroundSetup
    }
}
