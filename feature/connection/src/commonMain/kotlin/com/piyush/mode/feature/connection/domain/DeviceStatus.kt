package com.piyush.mode.feature.connection.domain

import com.piyush.mode.core.platform.ManualStep
import com.piyush.mode.core.platform.PhoneQuirks

/** Why background mode can't be set up on this phone right now (boards D06–D08). */
public enum class BackgroundBlock {
    /** The hidden display needs Android 14: below it, it steals the keyboard and focus from the user's screen. */
    AndroidTooOld,
    /** The link to the phone runs over Wi-Fi; none is connected. */
    NoWifi,
    /** A work or school policy turned Developer options off. */
    DebuggingBlocked,
}

/**
 * The parts of a phone's status that are plain rules (tested here; the Android side only reads the inputs):
 * which settings the maker makes the user confirm, whether the install locks the accessibility switch, and how
 * background mode's link stands.
 */
public object DeviceStatus {
    /** Installers whose apps Android doesn't lock behind "restricted settings": the stores. */
    public val STORES: Set<String> = setOf(
        "com.android.vending", "com.google.android.packageinstaller.store",
        "com.sec.android.app.samsungapps", "com.xiaomi.market", "com.huawei.appmarket",
        "com.heytap.market", "com.oppo.market", "com.bbk.appstore",
    )

    /**
     * Whether Android 13+ locks the accessibility switch: only for an app a user sideloaded (a downloaded APK).
     * An adb install has no installer (or the shell), and Android doesn't lock those.
     */
    public fun locked(installer: String?, switchOn: Boolean): Boolean =
        !switchOn && installer != null && installer != SHELL && installer !in STORES

    /** The maker's manual steps for this phone, each with whether the user confirmed it. */
    public fun manual(brand: PhoneQuirks.Brand?, confirmed: Set<String>): List<PhoneCheckResult> =
        brand?.manualSteps.orEmpty().map { step ->
            PhoneCheckResult(id(step), CheckState.Unverifiable(confirmed = step.name in confirmed))
        }

    public fun id(step: ManualStep): PhoneCheckId = when (step) {
        ManualStep.Sleeping -> PhoneCheckId.Sleeping
        ManualStep.Autostart -> PhoneCheckId.Autostart
        ManualStep.Popups -> PhoneCheckId.Popups
    }

    public fun step(id: PhoneCheckId): ManualStep? = when (id) {
        PhoneCheckId.Sleeping -> ManualStep.Sleeping
        PhoneCheckId.Autostart -> ManualStep.Autostart
        PhoneCheckId.Popups -> ManualStep.Popups
        else -> null
    }

    /** What blocks background mode, the most permanent first; null when nothing does. */
    public fun backgroundBlock(sdkInt: Int, wifi: Boolean, debuggingAllowed: Boolean): BackgroundBlock? = when {
        sdkInt < BACKGROUND_MIN_SDK -> BackgroundBlock.AndroidTooOld
        !debuggingAllowed -> BackgroundBlock.DebuggingBlocked
        !wifi -> BackgroundBlock.NoWifi
        else -> null
    }

    /** Background mode's link, for a phone where it's switched on. */
    public fun backgroundLink(wifi: Boolean, lastConnectFailed: Boolean): CheckState = when {
        !wifi -> CheckState.Failed(NO_WIFI, null)
        lastConnectFailed -> CheckState.Failed(LINK_LOST, null)
        else -> CheckState.Passed(null)
    }

    /** Android 14: a hidden display with its own focus. */
    public const val BACKGROUND_MIN_SDK: Int = 34
    public const val NO_WIFI: String = "waiting for Wi-Fi"
    public const val LINK_LOST: String = "not reachable"
    private const val SHELL = "com.android.shell"
}
