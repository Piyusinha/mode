package com.piyush.mode.core.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillTerminateNotification
import platform.UIKit.UIBackgroundTaskInvalid

public class IosAppInfo : AppInfo {
    override val bundleId: String = NSBundle.mainBundle.bundleIdentifier ?: "app.heymode.mode"
}

public class IosAppLifecycle : AppLifecycle {
    override fun onBackground(action: () -> Unit) {
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue,
        ) { _ -> action() }
    }

    override fun onForeground(action: () -> Unit) {
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue,
        ) { _ -> action() }
    }

    override fun onTerminate(action: () -> Unit) {
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillTerminateNotification, null, NSOperationQueue.mainQueue,
        ) { _ -> action() }
    }
}

/**
 * Silent mixable audio keeps Mode running for as long as the work takes
 * (see [KeepAwake]); a background task is a second, shorter safety net.
 */
public class IosBackgroundWork : BackgroundWork {
    override suspend fun <T> run(work: suspend () -> T): T {
        withContext(Dispatchers.Main) { KeepAwake.start() }
        val app = UIApplication.sharedApplication
        var task = UIBackgroundTaskInvalid
        task = app.beginBackgroundTaskWithName("mode.drive") {
            app.endBackgroundTask(task)
            task = UIBackgroundTaskInvalid
        }
        try {
            return work()
        } finally {
            if (task != UIBackgroundTaskInvalid) app.endBackgroundTask(task)
            withContext(Dispatchers.Main) { KeepAwake.stop() }
        }
    }
}
