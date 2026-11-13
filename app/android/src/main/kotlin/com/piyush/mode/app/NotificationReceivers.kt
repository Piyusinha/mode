package com.piyush.mode.app

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.AndroidNotifier
import com.piyush.mode.core.platform.NotifierHooks

/** A button or a typed reply on Mode's question notification: answers the task without opening Mode. */
class AnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val questionId = intent.getStringExtra(QUESTION) ?: return
        val answer = intent.getStringExtra(ANSWER)
            ?: RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NotifierHooks.REPLY_KEY)?.toString()
            ?: return
        AppLog.log("notify", "answered from the notification")
        val graph = ModeApplication.graph(context)
        graph.tasks.answer(questionId, answer)
        graph.notifier.withdraw(questionId)
    }

    companion object {
        const val QUESTION = "mode.question"
        const val ANSWER = "mode.answer"
    }
}

/** A repeat's reminder time came: show its notification (exact alarms wake Mode for this). */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ID) ?: return
        (ModeApplication.graph(context).notifier as? AndroidNotifier)?.showScheduled(id, intent.getStringExtra(TITLE).orEmpty(), intent.getStringExtra(BODY).orEmpty())
    }

    companion object {
        const val ID = "mode.schedule"
        const val TITLE = "mode.title"
        const val BODY = "mode.body"
    }
}
