package com.piyush.mode.core.platform

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

public class AndroidAppInfo(context: Context) : AppInfo {
    override val bundleId: String = context.applicationContext.packageName
    override val version: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
    override val releaseFile: String = "mode-android.apk"
}

/**
 * The process lifecycle: Mode is in front while any of its activities is started.
 * Android never says when it ends a process, so [onTerminate] actions never run:
 * running them on every trip to the background would end Mode's session while it
 * works in another app, which is most of a task. Nothing needs them on Android
 * (the accessibility service lives on its own; there is no VPN to close).
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

    override fun watchForeground(action: () -> Unit): () -> Unit {
        foreground += action
        return { foreground -= action }
    }

    override fun onTerminate(action: () -> Unit) {}
}
