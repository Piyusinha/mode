@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.piyush.mode.app

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.create
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObjectProtocol

/**
 * Keeps Mode running while it drives other apps: iOS keeps an app that is
 * playing audio awake, so Mode plays silence (the "audio" background mode).
 *
 * The session mixes with others, so music or podcasts keep playing, nothing
 * shows in Now Playing, and nothing is heard. It runs only while a task does;
 * a call or another app's exclusive audio interrupts it, and it restarts
 * when the interruption ends. Main thread only.
 */
object KeepAwake {
    private var holders = 0
    private var player: AVAudioPlayer? = null
    private var interruptions: NSObjectProtocol? = null

    /** True while silence is playing. */
    val active: Boolean get() = player?.playing == true

    fun start() {
        holders++
        if (holders > 1) return
        AppLog.log("awake", "silent audio on")
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionCategoryOptionMixWithOthers, null)
        session.setActive(true, null)
        player = AVAudioPlayer(data = silentWav().let { wav -> wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.convert()) } }, error = null).apply {
            numberOfLoops = -1
            volume = 0f
            prepareToPlay()
            play()
        }
        interruptions = NSNotificationCenter.defaultCenter.addObserverForName(
            AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue,
        ) { note ->
            val type = (note?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
            AppLog.log("awake", "audio interruption " + (if (type == AVAudioSessionInterruptionTypeEnded) "ended" else "began"))
            if (type == AVAudioSessionInterruptionTypeEnded && holders > 0) {
                AVAudioSession.sharedInstance().setActive(true, null)
                player?.play()
            }
        }
    }

    fun stop() {
        if (holders == 0) return
        holders--
        if (holders > 0) return
        player?.stop()
        player = null
        AppLog.log("awake", "silent audio off")
        interruptions?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        interruptions = null
        AVAudioSession.sharedInstance().setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null)
    }

    /** One second of 8 kHz, 16-bit mono silence as a WAV file. */
    private fun silentWav(): ByteArray {
        val rate = 8000
        val dataSize = rate * 2
        val out = ByteArray(44 + dataSize)
        fun ascii(at: Int, s: String) = s.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }
        fun le(at: Int, v: Int, bytes: Int) = repeat(bytes) { out[at + it] = (v shr (8 * it)).toByte() }
        ascii(0, "RIFF"); le(4, 36 + dataSize, 4); ascii(8, "WAVE")
        ascii(12, "fmt "); le(16, 16, 4); le(20, 1, 2); le(22, 1, 2)
        le(24, rate, 4); le(28, rate * 2, 4); le(32, 2, 2); le(34, 16, 2)
        ascii(36, "data"); le(40, dataSize, 4)
        return out
    }
}
