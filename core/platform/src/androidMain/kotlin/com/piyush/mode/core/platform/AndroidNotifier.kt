package com.piyush.mode.core.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Plain notifications (a finished task, a schedule): one channel, tapping opens
 * Mode. Questions answerable in place, the live progress and exact-time schedules
 * come with the hands-free work and replace the defaults here.
 */
public class AndroidNotifier(
    context: Context,
    /** The small icon every notification shows (a drawable resource of the app). */
    private val smallIcon: Int,
    /** The intent that opens Mode (its main activity). */
    private val openApp: () -> Intent,
) : Notifier {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var nextId = 1000

    init {
        manager.createNotificationChannel(
            NotificationChannel(RESULTS, "Results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a task is done"
            },
        )
    }

    override fun requestPermission() {
        scope.launch { ActivityBridge.requestPermission(Manifest.permission.POST_NOTIFICATIONS) }
    }

    override fun post(title: String, body: String) {
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(app, 0, openApp(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(app, RESULTS)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(nextId++, notification)
    }

    private companion object {
        const val RESULTS = "mode.results"
    }
}
