package com.piyush.mode.core.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import com.piyush.mode.core.common.AppLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps Mode running, and the screen on, while it works in another app: Android
 * stops background apps and turns the screen off, either of which cuts a task
 * off halfway. A foreground service with its ongoing notification (and Stop),
 * a partial wake lock for the work and a screen-on lock for the taps.
 *
 * Mode starts it from places Android allows: while Mode is in front, from a
 * notification, the assistant, a widget or an exact alarm, or with its
 * accessibility service bound. If Android refuses anyway, the work still runs.
 */
public class WorkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val content = WorkNotice.content
        if (content == null) {
            AppLog.log("app", "working notice has no content; stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching { startForeground(NOTICE_ID, content(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
            .onSuccess { AppLog.log("app", "working notice shown") }
            .onFailure { AppLog.log("app", "working notice failed: ${it::class.simpleName}: ${it.message}"); stopSelf() }
        return START_NOT_STICKY
    }

    public companion object {
        /** The ongoing notification's id; the live progress later updates this same one. */
        public const val NOTICE_ID: Int = 7
    }
}

/** What the working notification shows; the app sets it at launch. */
public object WorkNotice {
    public const val CHANNEL: String = "mode.working"

    @Volatile public var content: ((Context) -> Notification)? = null

    /** The plain "working" notice: title, tapping opens Mode, and Stop. */
    public fun plain(smallIcon: Int, title: String, stopLabel: String, open: PendingIntent, stop: PendingIntent): (Context) -> Notification = { context ->
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Working", NotificationManager.IMPORTANCE_LOW).apply {
            description = "While Mode works in your apps"
        })
        Notification.Builder(context, CHANNEL)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, stopLabel, stop).build())
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

/**
 * [BackgroundWork] on Android: [WorkService] and wake locks for as long as the
 * work runs. Several runs may overlap (a task and a check); the service stops
 * when the last one ends.
 */
public class AndroidBackgroundWork(context: Context) : BackgroundWork {
    private val app = context.applicationContext
    private val power = app.getSystemService(PowerManager::class.java)
    private val lock = Mutex()
    private var running = 0
    private var cpu: PowerManager.WakeLock? = null
    private var screen: PowerManager.WakeLock? = null

    override suspend fun <T> run(work: suspend () -> T): T {
        lock.withLock { if (running++ == 0) begin() }
        try {
            return work()
        } finally {
            lock.withLock { if (--running == 0) end() }
        }
    }

    private fun begin() {
        runCatching { app.startForegroundService(Intent(app, WorkService::class.java)) }
            .onFailure { AppLog.log("app", "working notice not allowed now (${it::class.simpleName}); the task runs anyway") }
        cpu = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mode:work").apply { acquire(MAX_MS) }
        // Taps need the screen on; deprecated, but still the one way a service keeps another app's screen lit.
        @Suppress("DEPRECATION")
        screen = power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "mode:screen").apply { acquire(MAX_MS) }
    }

    private fun end() {
        screen?.takeIf { it.isHeld }?.release()
        cpu?.takeIf { it.isHeld }?.release()
        screen = null
        cpu = null
        app.stopService(Intent(app, WorkService::class.java))
    }

    private companion object {
        /** A task is stopped after 15 minutes; the locks never outlive that by much. */
        const val MAX_MS = 20 * 60_000L
    }
}
