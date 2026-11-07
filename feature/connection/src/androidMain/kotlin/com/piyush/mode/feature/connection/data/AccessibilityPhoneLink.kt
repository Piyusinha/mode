package com.piyush.mode.feature.connection.data

import com.piyush.mode.android.AccessibilityBackend
import com.piyush.mode.android.AndroidImageTools
import com.piyush.mode.android.ModeAccessibilityService
import com.piyush.mode.android.ServiceRegistry
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
public class AccessibilityPhoneLink(private val telemetry: Telemetry = Telemetry.None) : PhoneLink {
    override suspend fun connect(): PhoneConnection {
        val service = withTimeoutOrNull(WAIT_MS) { ServiceRegistry.service.filterNotNull().first() }
            ?: throw PhoneUnavailable(
                "Mode's phone control is off. Turn it on in Settings › Accessibility › Mode " +
                    "(on some phones under Downloaded apps or Installed services).",
            )
        return PhoneConnection(backend(service), route = ROUTE, attempts = "$ROUTE: connected")
    }

    override suspend fun release() {}

    private fun backend(service: ModeAccessibilityService) = AccessibilityBackend(
        hands = service,
        images = AndroidImageTools(service::bitmapFor),
        telemetry = telemetry,
    )

    private companion object {
        /** Android may be starting the service right now (just after boot, or after an update). */
        const val WAIT_MS = 2_000L
        const val ROUTE = "On your screen (accessibility)"
    }
}
