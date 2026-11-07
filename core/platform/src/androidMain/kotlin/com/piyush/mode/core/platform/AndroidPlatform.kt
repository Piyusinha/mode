package com.piyush.mode.core.platform

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

public class AndroidAppInfo(context: Context) : AppInfo {
    override val bundleId: String = context.applicationContext.packageName
}

/**
 * The process lifecycle: Mode is in front while any of its activities is started.
 * Android never promises a "terminate" call, so [onTerminate] actions also run
 * when Mode goes to the background, which is the last moment they can be sure of.
 */
public class AndroidAppLifecycle : AppLifecycle {
    private val background = mutableListOf<() -> Unit>()
    private val foreground = mutableListOf<() -> Unit>()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = foreground.toList().forEach { it() }
            override fun onStop(owner: LifecycleOwner) = background.toList().forEach { it() }
        })
    }

    override fun onBackground(action: () -> Unit) {
        background += action
    }

    override fun onForeground(action: () -> Unit) {
        foreground += action
    }

    override fun onTerminate(action: () -> Unit) {
        background += action
    }
}

/**
 * Until the working service exists (the accessibility engine brings it), the work
 * simply runs; Android keeps a foreground process alive on its own.
 */
public class PlainBackgroundWork : BackgroundWork {
    override suspend fun <T> run(work: suspend () -> T): T = work()
}
