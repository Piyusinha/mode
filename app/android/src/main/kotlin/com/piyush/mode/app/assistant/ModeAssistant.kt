package com.piyush.mode.app.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.piyush.mode.app.MainActivity
import com.piyush.mode.app.shell.DeepLink
import com.piyush.mode.core.common.AppLog

/**
 * Mode as the phone's digital assistant: holding the power button (or the assistant gesture) opens Mode
 * listening, the way "Start Mode" does on the iPhone. The service itself does nothing; the session does.
 */
class ModeAssistantService : VoiceInteractionService()

class ModeAssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = ModeAssistantSession(this)
}

/** Opens Mode's talking mode and gets out of the way: Mode's own screen listens, not an assistant overlay. */
class ModeAssistantSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        AppLog.log("assistant", "started from the assistant button")
        val listen = Intent(Intent.ACTION_VIEW, Uri.parse("${DeepLink.SCHEME}://listen"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // A plain launch, not startAssistantActivity: an assistant activity goes with the session, and hiding the
        // session (below) hid Mode with it on Android 16. While the session shows, Android lets Mode start.
        runCatching { context.startActivity(listen) }
            .recoverCatching { startAssistantActivity(listen) }
            .onFailure { AppLog.log("assistant", "couldn't open Mode: ${it::class.simpleName}") }
        hide()
    }
}

/** The phone's own speech recognizer: not Mode's (which only forwards to it). */
object PhoneRecognizer {
    fun component(context: Context): ComponentName? =
        context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
            .filter { it.packageName != context.packageName }
            // Google's speech services first: the recognizer most phones use for voice typing.
            .sortedBy { if (it.packageName.startsWith(GOOGLE)) 0 else 1 }
            .firstOrNull()

    private const val GOOGLE = "com.google.android"
}

/**
 * The recognizer an assistant must come with. Choosing Mode as the assistant makes it the phone's default, so
 * it forwards everything to the phone's own recognizer: voice typing in other apps keeps working.
 */
class ModeRecognitionService : RecognitionService() {
    private var inner: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        inner?.destroy()
        val target = PhoneRecognizer.component(this) ?: return listener.error(SpeechRecognizer.ERROR_CLIENT)
        val r = SpeechRecognizer.createSpeechRecognizer(this, target)
        inner = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) = listener.readyForSpeech(params)
            override fun onBeginningOfSpeech() = listener.beginningOfSpeech()
            override fun onRmsChanged(rmsdB: Float) = listener.rmsChanged(rmsdB)
            override fun onBufferReceived(buffer: ByteArray) = listener.bufferReceived(buffer)
            override fun onEndOfSpeech() = listener.endOfSpeech()
            override fun onError(error: Int) = listener.error(error)
            override fun onResults(results: Bundle) = listener.results(results)
            override fun onPartialResults(partialResults: Bundle) = listener.partialResults(partialResults)
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(recognizerIntent)
    }

    override fun onStopListening(listener: Callback) {
        inner?.stopListening()
    }

    override fun onCancel(listener: Callback) {
        inner?.cancel()
        inner?.destroy()
        inner = null
    }

    override fun onDestroy() {
        inner?.destroy()
        inner = null
        super.onDestroy()
    }
}
