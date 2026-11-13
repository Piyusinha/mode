package com.piyush.mode.feature.connection.data

import com.piyush.mode.feature.connection.domain.CheckState
import com.piyush.mode.feature.connection.domain.PhoneCheckId
import com.piyush.mode.feature.connection.domain.PhoneCheckResult
import com.piyush.mode.feature.connection.domain.PhoneChecks
import com.piyush.mode.feature.connection.domain.PhoneLink
import com.piyush.mode.feature.connection.domain.PhoneConnection
import com.piyush.mode.ops.PhoneUnavailable

/** A build with no phone engine yet (the Play flavour, or before the accessibility engine is wired). */
public class UnavailablePhoneLink(private val reason: String = "This build cannot control the phone yet.") : PhoneLink {
    override suspend fun connect(): PhoneConnection = throw PhoneUnavailable(reason)
    override suspend fun release() {}
}

public class UnavailablePhoneChecks(private val reason: String = "This build cannot control the phone yet.") : PhoneChecks {
    /** Nothing to set up or check: setup skips "Let Mode use your phone" in this build. */
    override val steps: List<PhoneCheckId> = emptyList()

    override suspend fun run(update: (List<PhoneCheckResult>) -> Unit) {
        update(emptyList())
    }

    override fun ready(): Boolean = false
    override suspend fun permitted(): Boolean = false
}
