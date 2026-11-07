package com.piyush.mode.app

import android.app.Application
import android.content.Intent
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
    }

    companion object {
        fun graph(context: android.content.Context): ModeGraph = (context.applicationContext as ModeApplication).graph
    }
}
