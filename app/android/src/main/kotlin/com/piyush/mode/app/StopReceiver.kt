package com.piyush.mode.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Stop on Mode's working notification: ends the task that is running. */
class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ModeApplication.graph(context).tasks.stop()
    }
}
