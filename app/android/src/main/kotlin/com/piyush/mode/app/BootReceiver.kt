package com.piyush.mode.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piyush.mode.core.common.AppLog

/** Android forgets alarms on a restart and moves them with the clock: every repeat's reminder is set again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.log("schedule", "re-arming repeats after ${intent.action?.substringAfterLast('.')}")
        ModeApplication.graph(context).scheduler.rearmAll()
    }
}
