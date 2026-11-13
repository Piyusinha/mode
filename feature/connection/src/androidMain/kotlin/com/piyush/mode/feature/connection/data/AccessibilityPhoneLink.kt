package com.piyush.mode.feature.connection.data

import com.piyush.mode.android.AccessibilityBackend
import com.piyush.mode.android.AndroidImageTools
import com.piyush.mode.android.ModeAccessibilityService
import com.piyush.mode.android.ServiceRegistry
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.feature.connection.domain.PhoneConnection
import com.piyush.mode.feature.connection.domain.PhoneLink
import com.piyush.mode.ops.PhoneUnavailable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The phone Mode runs on, through Mode's accessibility service ("On your screen").
 * Nothing to set up per session: the service is there while the user has it on.
 */
public class AccessibilityPhoneLink(
    private val telemetry: Telemetry = Telemetry.None,
    /** Background mode, when this build has it: tasks run on a hidden display once the user set it up. */
    private val background: BackgroundMode? = null,
) : PhoneLink {
    override suspend fun connect(): PhoneConnection {
        val service = withTimeoutOrNull(WAIT_MS) { ServiceRegistry.service.filterNotNull().first() }
            ?: throw PhoneUnavailable(
                "Mode's phone control is off. Turn it on in Settings › Accessibility › Mode " +
                    "(on some phones under Downloaded apps or Installed services).",
            )
        val hidden = background?.takeIf { it.enabled }
        if (hidden != null) {
            // The hidden display when it's ready; the user's screen when it isn't, so the task still runs.
            val tried = runCatching { hidden.display() }
            tried.getOrNull()?.let { id ->
                return PhoneConnection(backend(service, id, hidden), route = BACKGROUND_ROUTE, attempts = "$BACKGROUND_ROUTE: display $id")
            }
            val why = tried.exceptionOrNull()?.message
            AppLog.log("background", "not ready ($why); working on the screen")
            return PhoneConnection(backend(service), route = ROUTE, attempts = "$BACKGROUND_ROUTE: $why\n$ROUTE: connected")
        }
        return PhoneConnection(backend(service), route = ROUTE, attempts = "$ROUTE: connected")
    }

    /** Reads screens phone control can't (games, maps, canvases); made once, when first needed. */
    private val textReader by lazy { com.piyush.mode.android.MlKitTextReader() }

    /** The task is over: the helper ends and its hidden display goes with it (the link stays for next time). */
    override suspend fun release() {
        background?.stop()
    }

    private fun backend(service: ModeAccessibilityService, displayId: Int = 0, hidden: BackgroundMode? = null) = AccessibilityBackend(
        hands = service,
        images = AndroidImageTools(service::bitmapFor),
        reader = textReader,
        telemetry = telemetry,
        displayId = displayId,
        displayKeys = hidden?.let { mode -> { action -> mode.press(displayId, action) } },
    )

    public companion object {
        /** How the session shows a task running on the hidden display. */
        public const val BACKGROUND_ROUTE: String = "In the background (hidden screen)"

        /** Android may be starting the service right now (just after boot, or after an update). */
        private const val WAIT_MS = 2_000L
        private const val ROUTE = "On your screen (accessibility)"
    }
}
