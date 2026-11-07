package com.piyush.mode.app

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import com.piyush.mode.core.platform.WorkNotice
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.graph.androidModeGraph

/** Owns the one graph for the whole process (activities, services and receivers all read it). */
class ModeApplication : Application() {
    lateinit var graph: ModeGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = androidModeGraph(this, R.drawable.ic_mode_notification) {
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // The working notification: opens Mode, and Stop ends the task.
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        WorkNotice.content = WorkNotice.plain(
            smallIcon = R.drawable.ic_mode_notification,
            title = getString(R.string.working_title),
            stopLabel = getString(R.string.working_stop),
            open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags),
            stop = PendingIntent.getBroadcast(this, 0, Intent(this, StopReceiver::class.java), flags),
        )
    }

    companion object {
        fun graph(context: android.content.Context): ModeGraph = (context.applicationContext as ModeApplication).graph
    }
}
