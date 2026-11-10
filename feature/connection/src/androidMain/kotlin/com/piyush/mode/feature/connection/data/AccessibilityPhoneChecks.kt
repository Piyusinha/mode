package com.piyush.mode.feature.connection.data

import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.piyush.mode.android.ModeAccessibilityService
import com.piyush.mode.android.ServiceRegistry
import com.piyush.mode.core.platform.PhoneQuirks
import com.piyush.mode.feature.connection.domain.DeviceStatus
import com.piyush.mode.feature.connection.domain.CheckState
import com.piyush.mode.feature.connection.domain.PhoneCheckId
import com.piyush.mode.feature.connection.domain.PhoneCheckResult
import com.piyush.mode.feature.connection.domain.PhoneChecks
import com.piyush.mode.ops.Op
import com.piyush.mode.screen.ScreenInfo
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.TimeSource

/**
 * Each part of phone control on Android, in order: whether Android lets Mode's
 * switch be turned on (apps not from the Play Store are locked until "Allow
 * restricted settings"), the service itself, seeing the screen, touching it,
 * and the battery setting that keeps a task from being cut off.
 */
public class AccessibilityPhoneChecks(
    context: Context,
    private val link: AccessibilityPhoneLink,
    /** Background mode, when this build has it (its link is one of the things checked). */
    private val background: BackgroundMode? = null,
    /** This phone's maker, for the steps only the user can confirm (null: a plain Android phone). */
    private val brand: () -> PhoneQuirks.Brand? = { null },
    /** The manual steps the user confirmed (ManualStep names), from settings. */
    private val confirmed: () -> Set<String> = { emptySet() },
    /** The phone has a speech recognizer (Google's app, usually). */
    private val speechAvailable: () -> Boolean = { true },
) : PhoneChecks {
    private val app = context.applicationContext

    override val steps: List<PhoneCheckId> = listOf(
        PhoneCheckId.RestrictedSettings, PhoneCheckId.Engine, PhoneCheckId.Screen, PhoneCheckId.Hands, PhoneCheckId.Battery,
    )

    override fun ready(): Boolean = enabled()

    override suspend fun permitted(): Boolean = enabled()

    /** The switch, its lock and the battery setting, read from Android's settings without touching the screen. */
    override suspend fun status(): List<PhoneCheckResult> {
        val on = enabled()
        val running = ServiceRegistry.service.value != null
        val power = app.getSystemService(PowerManager::class.java)
        val mode = background
        return listOf(
            PhoneCheckResult(PhoneCheckId.RestrictedSettings,
                if (locked(on)) CheckState.Failed("locked until restricted settings are allowed", null) else CheckState.Passed(null)),
            PhoneCheckResult(PhoneCheckId.Engine, when {
                on && running -> CheckState.Passed(null)
                on -> CheckState.Failed(DeviceStatus.ENGINE_STOPPED, "Turn Mode off and on again in Settings › Accessibility.")
                else -> CheckState.Failed(DeviceStatus.ENGINE_OFF, null)
            }),
            PhoneCheckResult(PhoneCheckId.Battery,
                if (power.isIgnoringBatteryOptimizations(app.packageName)) CheckState.Passed(null) else CheckState.Failed("restricted", null)),
            PhoneCheckResult(PhoneCheckId.Speech, if (speechAvailable()) CheckState.Passed(null) else CheckState.Failed("no speech recognizer", null)),
        ) + DeviceStatus.manual(brand(), confirmed()) + listOfNotNull(
            mode?.takeIf { it.enabled }?.let { PhoneCheckResult(PhoneCheckId.BackgroundLink, DeviceStatus.backgroundLink(it.wifiConnected(), it.linkDown)) },
        )
    }

    override suspend fun run(update: (List<PhoneCheckResult>) -> Unit) {
        val results = steps.associateWith<PhoneCheckId, CheckState> { CheckState.NotChecked }.toMutableMap()
        fun report() = update(results.map { (id, state) -> PhoneCheckResult(id, state) })
        fun pass(id: PhoneCheckId, ms: Long? = null) { results[id] = CheckState.Passed(ms) }
        fun fail(id: PhoneCheckId, reason: String, fix: String?) { results[id] = CheckState.Failed(reason, fix); report() }
        report()

        val on = enabled()
        // The lock only matters while the switch is off; once it is on, Android allowed it.
        if (locked(on)) {
            fail(PhoneCheckId.RestrictedSettings,
                "Android locks phone control for apps that don't come from the Play Store.",
                "Open Mode's App info, tap ⋮ at the top right, then Allow restricted settings.")
            results[PhoneCheckId.Engine] = CheckState.Failed("Turn it on once the setting is unlocked.", null)
            report()
            return
        }
        pass(PhoneCheckId.RestrictedSettings)
        if (!on) {
            fail(PhoneCheckId.Engine, "Mode's phone control is off.", "Turn on Mode in Settings › Accessibility.")
            return
        }
        if (ServiceRegistry.service.value == null) {
            fail(PhoneCheckId.Engine, "Phone control is on but Android isn't running it (this happens after an update or a force stop).",
                "Turn Mode off and on again in Settings › Accessibility.")
            return
        }
        pass(PhoneCheckId.Engine)
        report()

        val phone = try {
            link.connect().backend
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            fail(PhoneCheckId.Screen, e.message ?: "Couldn't reach phone control.", null)
            return
        }
        val screen = try {
            timed(PhoneCheckId.Screen, results, ::report) {
                val shot = phone.send(Op.ScreenCapture(maxSize = PROBE_SIDE)).jsonObject
                val private = shot["private"]?.jsonPrimitive?.boolean == true
                check(private || "jpeg_b64" in shot) { "the screenshot was empty" }
                val bounds = shot.getValue("bounds").jsonObject
                ScreenInfo(bounds.getValue("w").jsonPrimitive.content.toDouble(), bounds.getValue("h").jsonPrimitive.content.toDouble())
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            fail(PhoneCheckId.Screen, e.message ?: "Couldn't see the screen.", null)
            return
        }
        try {
            // A tap on the title area of Mode's own Diagnose page, which does nothing.
            timed(PhoneCheckId.Hands, results, ::report) { phone.send(Op.Tap(screen.widthPt / 2, TAP_Y_DP)) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            fail(PhoneCheckId.Hands, e.message ?: "Couldn't tap.", null)
            return
        }

        val power = app.getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(app.packageName)) pass(PhoneCheckId.Battery)
        else results[PhoneCheckId.Battery] = CheckState.Failed(
            "Android may stop Mode in the middle of a task to save battery.",
            "Set Mode's battery use to Unrestricted.",
        )
        report()
    }

    /** Whether the user has Mode's switch on in Settings › Accessibility. */
    private fun enabled(): Boolean {
        val mine = ComponentName(app, ModeAccessibilityService::class.java)
        val list = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return list.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    /** Android locks the switch for an app the user sideloaded (a downloaded APK); not for a store or adb install. */
    private fun locked(on: Boolean): Boolean {
        val source = runCatching { app.packageManager.getInstallSourceInfo(app.packageName) }.getOrNull()
        return DeviceStatus.locked(source?.initiatingPackageName ?: source?.installingPackageName, on)
    }

    private suspend fun <T> timed(id: PhoneCheckId, results: MutableMap<PhoneCheckId, CheckState>, report: () -> Unit, block: suspend () -> T): T {
        results[id] = CheckState.Running
        report()
        val started = TimeSource.Monotonic.markNow()
        val value = block()
        results[id] = CheckState.Passed(started.elapsedNow().inWholeMilliseconds)
        report()
        return value
    }

    private companion object {
        const val PROBE_SIDE = 400
        const val TAP_Y_DP = 120.0
    }
}
