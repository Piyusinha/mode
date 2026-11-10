package com.piyush.mode.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.piyush.mode.core.common.AppLog

/**
 * Mode's icon: an invisible front door, so Mode always knows who opened it. Android only brings an open Mode forward
 * when its icon is used again, without saying by whom; this screen is new every time. Opened by the phone's assistant
 * ("Hey Google, open Mode") with Start by voice on, it's Mode's overlay listening over the app the user is in;
 * otherwise Mode opens as usual.
 */
class LaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val from = referrer?.host
        val assistant = from in ASSISTANTS && com.piyush.mode.app.graph.startByVoiceOn(application)
        AppLog.log("link", "opened from ${from ?: "unknown"}${if (assistant) ": the assistant, so the overlay" else ""}")
        startActivity(
            if (assistant) {
                Intent(this, VoiceEntryActivity::class.java).putExtra(VoiceEntryActivity.ENTRY, "assistant")
            } else {
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            },
        )
        finish()
        // No animation of its own: the screen that follows is the one the user sees open.
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    private companion object {
        /** Apps that open Mode when the user asks their assistant to (Google, Gemini, Bixby). */
        val ASSISTANTS = setOf(
            "com.google.android.googlequicksearchbox", "com.google.android.apps.bard", "com.google.android.apps.googleassistant",
            "com.samsung.android.bixby.agent", "com.samsung.android.visionintelligence",
        )
    }
}
