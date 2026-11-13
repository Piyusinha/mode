package com.piyush.mode.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.piyush.mode.android.ServiceRegistry
import com.piyush.mode.app.overlay.OverlayController
import com.piyush.mode.app.shell.DeepLink
import com.piyush.mode.core.common.AppLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Mode's voice entry ("Ask Mode", the widget's mic, the assistant): an invisible screen that opens Mode's overlay over
 * the app the user is in and goes, so they stay where they were. Without phone control (off, or not allowed yet) the
 * overlay can't show, and Mode's own voice screen opens instead.
 */
class VoiceEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return finish()
        val entry = intent.getStringExtra(ENTRY) ?: "ask_mode"
        lifecycleScope.launch {
            // Just after an update (or a restart) Android binds phone control again once Mode's process starts: a moment.
            withTimeoutOrNull(SERVICE_WAIT_MS) { ServiceRegistry.service.first { it != null } }
            if (!OverlayController.summon(application, entry = entry)) {
                AppLog.log("voice entry", "no phone control: Mode's voice screen instead")
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("${DeepLink.SCHEME}://listen"), this@VoiceEntryActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            finish()
        }
    }

    companion object {
        /** Where it was opened from, for the log ("widget", "assistant"). */
        const val ENTRY = "mode.entry"
        private const val SERVICE_WAIT_MS = 2_000L
    }
}
