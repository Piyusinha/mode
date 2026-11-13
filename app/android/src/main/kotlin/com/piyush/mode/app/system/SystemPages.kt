package com.piyush.mode.app.system

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.piyush.mode.core.platform.PhoneQuirks
import com.piyush.mode.core.platform.QuirkPage
import androidx.browser.customtabs.CustomTabsIntent
import com.piyush.mode.core.common.AppLog

/**
 * Android's own pages Mode sends the user to during setup, each with fallbacks: phone makers move
 * or rename them, so the first page that opens wins and the last is always Mode's App info.
 */
class SystemPages(private val activity: Activity) {
    private val pkg = activity.packageName
    private val appInfo get() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))

    /** App info, where ⋮ › Allow restricted settings lives. */
    fun appInfo() = open("app info", appInfo)

    /** Mode's own switch in Accessibility when Android can open it directly, else the Accessibility list. */
    fun accessibility() {
        val service = ComponentName(pkg, ACCESSIBILITY_SERVICE)
        open(
            "accessibility",
            Intent(ACTION_ACCESSIBILITY_DETAILS).putExtra(Intent.EXTRA_COMPONENT_NAME, service.flattenToString()),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        )
    }

    /** Default apps › Digital assistant. */
    fun defaultApps() = open(
        "default apps",
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
    )

    /**
     * The battery setting: Android's one-tap "let Mode run in the background" question where this build
     * may ask it, else App info (› Battery › Unrestricted).
     */
    fun battery() {
        val ask = activity.checkSelfPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) == PackageManager.PERMISSION_GRANTED
        val exempt = activity.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)
        // Xiaomi, Samsung, OPPO, vivo, Huawei… stop apps with their own savers too: once Android lets Mode run,
        // the next tap opens the maker's page (battery, then autostart), whichever this phone has.
        val brand = brand()
        val brandPages = brand?.let { (it.battery + it.autostart).map(::page) }.orEmpty()
        open(
            "battery",
            *listOfNotNull(
                if (ask && !exempt) Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", pkg, null)) else null,
                *(if (exempt || !ask) brandPages.toTypedArray() else emptyArray()),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            ).toTypedArray(),
        )
    }

    /** Samsung's "Never sleeping apps" picker (board D03), else its battery page. */
    fun sleeping() = open("never sleeping apps", *brand()?.sleeping.orEmpty().map(::page).toTypedArray())

    /** The maker's Autostart list (board D04). */
    fun autostart() = open("autostart", *brand()?.autostart.orEmpty().map(::page).toTypedArray())

    /** Xiaomi's permission editor for Mode, where pop-ups in the background live (board D05). */
    fun popups() = open("background pop-ups", *brand()?.popups.orEmpty().map(::page).toTypedArray())

    /** An app's page in the store (the Google app, for speech recognition). */
    fun store(packageName: String) = open(
        "store",
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")),
    )

    /** Wi-Fi settings: background mode's link needs a network. */
    fun wifi() = open("wi-fi", Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))

    /** This phone's maker (the debug override first, so one phone can show another maker's pages). */
    private fun brand(): PhoneQuirks.Brand? = com.piyush.mode.app.graph.brandOf(activity)

    /** A maker's own page: a component, or an action its app answers; with Mode's name for the pages that take it. */
    private fun page(p: QuirkPage): Intent {
        val intent = if (p.action != null) Intent(p.action).setPackage(p.packageName) else Intent().setClassName(p.packageName, p.className)
        p.intExtras.forEach { (key, value) -> intent.putExtra(key, value) }
        p.appPackageExtra?.let { intent.putExtra(it, pkg) }
        return intent.putExtra(EXTRA_PACKAGE_NAME, pkg).putExtra(EXTRA_PACKAGE_LABEL, activity.applicationInfo.loadLabel(activity.packageManager))
    }

    /** Mode's notification settings, once Android stops asking. */
    /** About phone, where tapping Build number turns on Developer options. */
    fun aboutPhone() = open("about phone", Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))

    /** Developer options, scrolled to Wireless debugging. */
    fun wirelessDebugging() = open(
        "wireless debugging",
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).putExtra(HIGHLIGHT, WIRELESS_DEBUGGING),
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    )

    fun notifications() = open("notifications", Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))

    /** A web page in a browser tab inside Mode. */
    fun web(url: String) {
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, Uri.parse(url))
        } catch (e: ActivityNotFoundException) {
            open("browser", Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun open(name: String, vararg intents: Intent) {
        for (intent in intents.toList() + appInfo) {
            try {
                activity.startActivity(intent)
                AppLog.log("setup", "opened $name: ${intent.action}")
                return
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
    }

    private companion object {
        /** Settings › Accessibility › one service (public on Android 13+, missing on some phones). */
        const val ACTION_ACCESSIBILITY_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
        const val ACCESSIBILITY_SERVICE = "com.piyush.mode.android.ModeAccessibilityService"

        /** Settings scrolls to (and highlights) the preference with this key. */
        const val HIGHLIGHT = ":settings:fragment_args_key"
        const val WIRELESS_DEBUGGING = "toggle_adb_wireless"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_PACKAGE_LABEL = "package_label"
    }
}
