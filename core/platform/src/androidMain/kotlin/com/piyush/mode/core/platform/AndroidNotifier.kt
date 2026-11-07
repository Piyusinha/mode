package com.piyush.mode.core.platform

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The words Mode's notifications use, from the app's strings. */
public data class NotifierWords(
    val needsYou: String,
    val yourTurn: String,
    val question: QuestionWords,
    val resultsChannel: String,
    val questionsChannel: String,
    val schedulesChannel: String,
)

/** What the app's receivers and links are, so taps and answers reach it. */
public class NotifierHooks(
    /** A broadcast that answers [questionId]: with [answer], or the typed reply under [REPLY_KEY] when null. */
    public val answer: (questionId: String, answer: String?) -> Intent,
    /** Opens a chat (a finished task's answer). */
    public val openConversation: (conversationId: String) -> Intent,
    /** A broadcast that shows a scheduled notification when its time comes. */
    public val scheduled: (id: String, title: String, body: String) -> Intent,
) {
    public companion object {
        /** Where a typed reply sits in the answer intent's RemoteInput results. */
        public const val REPLY_KEY: String = "mode.reply"
    }
}

/**
 * Mode's notifications on Android: results (tapping opens their chat), questions answered right on the
 * notification (buttons or a typed reply, so Mode need not come to the front), and reminders at an exact
 * time (AlarmManager; inexact when the user hasn't allowed exact alarms).
 */
public class AndroidNotifier(
    context: Context,
    /** The small icon every notification shows (a drawable resource of the app). */
    private val smallIcon: Int,
    /** The intent that opens Mode (its main activity). */
    private val openApp: () -> Intent,
    private val words: NotifierWords? = null,
    private val hooks: NotifierHooks? = null,
) : Notifier {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var nextId = 1000

    init {
        manager.createNotificationChannel(NotificationChannel(RESULTS, words?.resultsChannel ?: RESULTS, NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(QUESTIONS, words?.questionsChannel ?: QUESTIONS, NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(SCHEDULES, words?.schedulesChannel ?: SCHEDULES, NotificationManager.IMPORTANCE_DEFAULT))
    }

    private val allowed: Boolean
        get() = app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun requestPermission() {
        scope.launch { ActivityBridge.requestPermission(Manifest.permission.POST_NOTIFICATIONS) }
    }

    override fun post(title: String, body: String) {
        postFor(title, body, null)
    }

    override fun postFor(title: String, body: String, conversationId: String?) {
        if (!allowed) return
        val target = conversationId?.let { hooks?.openConversation?.invoke(it) } ?: openApp()
        val open = PendingIntent.getActivity(app, conversationId?.hashCode() ?: 0, target, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(nextId++, base(RESULTS, title, body).setContentIntent(open).setAutoCancel(true).build())
    }

    override fun canAskInPlace(): Boolean = allowed && words != null && hooks != null

    override fun ask(id: String, text: String, kind: QuestionKind, options: List<String>) {
        val w = words
        val h = hooks
        if (!allowed || w == null || h == null) return super.ask(id, text, kind, options)
        val open = PendingIntent.getActivity(app, id.hashCode(), openApp(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = base(QUESTIONS, if (kind == QuestionKind.YourTurn) w.yourTurn else w.needsYou, text)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
        QuestionActions.of(kind, options, w.question).forEachIndexed { i, action ->
            val request = (id.hashCode() * ACTIONS_PER_QUESTION) + i
            val action = if (action.typed) {
                // A typed answer needs a mutable PendingIntent: Android fills in the reply.
                val pending = PendingIntent.getBroadcast(app, request, h.answer(id, null), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                Notification.Action.Builder(null, action.title, pending)
                    .addRemoteInput(RemoteInput.Builder(NotifierHooks.REPLY_KEY).setLabel(action.title).build())
                    .build()
            } else {
                val pending = PendingIntent.getBroadcast(app, request, h.answer(id, action.answer), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                Notification.Action.Builder(null, action.title, pending).build()
            }
            builder.addAction(action)
        }
        manager.notify(tag(id), QUESTION_ID, builder.build())
    }

    override fun withdraw(id: String) {
        manager.cancel(tag(id), QUESTION_ID)
    }

    override fun schedule(id: String, title: String, body: String, atEpochMs: Long) {
        val intent = hooks?.scheduled?.invoke(id, title, body) ?: return
        val pending = PendingIntent.getBroadcast(app, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pending)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pending)
    }

    override fun cancel(id: String) {
        val intent = hooks?.scheduled?.invoke(id, "", "") ?: return
        PendingIntent.getBroadcast(app, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let(alarms::cancel)
        manager.cancel(tag(id), SCHEDULED_ID)
    }

    /** Shows a reminder whose time has come (called by the app's schedule receiver). */
    public fun showScheduled(id: String, title: String, body: String) {
        if (!allowed) return
        val open = PendingIntent.getActivity(app, id.hashCode(), openApp(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(tag(id), SCHEDULED_ID, base(SCHEDULES, title, body).setContentIntent(open).setAutoCancel(true).build())
    }

    private fun base(channel: String, title: String, body: String): Notification.Builder =
        Notification.Builder(app, channel).setSmallIcon(smallIcon).setContentTitle(title).setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))

    private fun tag(id: String) = "mode:$id"

    private companion object {
        const val RESULTS = "mode.results"
        const val QUESTIONS = "mode.questions"
        const val SCHEDULES = "mode.schedules"
        const val QUESTION_ID = 2
        const val SCHEDULED_ID = 3
        const val ACTIONS_PER_QUESTION = 4
    }
}
