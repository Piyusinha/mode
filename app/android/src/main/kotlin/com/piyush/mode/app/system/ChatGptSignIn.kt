package com.piyush.mode.app.system

import android.app.Activity
import android.content.Intent
import com.piyush.mode.app.MainActivity
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.network.LoopbackCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * "Continue with ChatGPT" on Android: listen on 127.0.0.1 first, then open OpenAI's page in a browser
 * tab. The page answers on the loopback; Mode comes back to the front and hands the URL over. Coming
 * back to Mode without an answer means the page was closed: that is a cancel.
 */
class ChatGptSignIn(private val activity: Activity, private val scope: CoroutineScope, private val pages: SystemPages) {
    private var callback: LoopbackCallback? = null
    private var waiting: Job? = null

    /** Opens [url]; [onAnswer] gets the callback URL, [onCancelled] a closed page or a busy port. */
    fun start(port: Int, url: () -> String, onAnswer: (String) -> Unit, onCancelled: () -> Unit) {
        stop()
        val listening = try {
            LoopbackCallback(port)
        } catch (e: IOException) {
            AppLog.log("chatgpt", "port $port busy: ${e.message}")
            onCancelled()
            return
        }
        callback = listening
        this.onCancelled = onCancelled
        waiting = scope.launch {
            val answer = listening.await()
            callback = null
            if (answer != null) {
                AppLog.log("chatgpt", "signed in on the page")
                activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                onAnswer(answer)
            }
        }
        pages.web(url())
    }

    private var onCancelled: (() -> Unit)? = null

    /** Mode is in front again: if the page didn't answer soon after, it was closed. */
    fun resumed() {
        val pending = callback ?: return
        scope.launch {
            delay(CLOSED_GRACE_MS)
            if (callback === pending) {
                AppLog.log("chatgpt", "page closed without signing in")
                stop()
                onCancelled?.invoke()
            }
        }
    }

    fun stop() {
        callback?.close()
        callback = null
        waiting?.cancel()
        waiting = null
    }

    private companion object {
        /** The answer can land just after Mode comes back. */
        const val CLOSED_GRACE_MS = 800L
    }
}
