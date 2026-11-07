package com.piyush.mode.core.platform

/** An Android page to try by its component; the app offers it only when it opens on this phone. */
public data class QuirkPage(val packageName: String, val className: String)

/**
 * What some phone makers add on top of Android, which can stop Mode halfway through a task or switch phone
 * control off: their own battery saver and autostart lists. Each brand's pages, most specific first; Android's
 * standard battery setting is the fallback everywhere.
 */
public object PhoneQuirks {
    public data class Brand(
        val name: String,
        /** Where the brand lets an app run in the background without being stopped. */
        val battery: List<QuirkPage>,
        /** Where the brand lets an app start by itself (Xiaomi, OPPO, vivo, Huawei need it for phone control to stay on). */
        val autostart: List<QuirkPage>,
    )

    /** The brand's pages for a phone that reports [manufacturer] and [brand] (Build.MANUFACTURER, Build.BRAND). */
    public fun of(manufacturer: String, brand: String = manufacturer): Brand? {
        val m = manufacturer.lowercase()
        val b = brand.lowercase()
        return BRANDS.firstOrNull { (names, _) -> names.any { it == m || it == b } }?.second
    }

    private val XIAOMI = Brand(
        "Xiaomi",
        battery = listOf(QuirkPage("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")),
        autostart = listOf(QuirkPage("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
    )
    private val SAMSUNG = Brand(
        "Samsung",
        battery = listOf(
            QuirkPage("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            QuirkPage("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ),
        autostart = emptyList(),
    )
    private val OPPO = Brand(
        "OPPO",
        battery = listOf(QuirkPage("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")),
        autostart = listOf(
            QuirkPage("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            QuirkPage("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            QuirkPage("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ),
    )
    private val ONEPLUS = Brand(
        "OnePlus",
        battery = OPPO.battery,
        autostart = listOf(QuirkPage("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")) + OPPO.autostart,
    )
    private val VIVO = Brand(
        "vivo",
        battery = listOf(QuirkPage("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        autostart = listOf(
            QuirkPage("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            QuirkPage("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ),
    )
    private val HUAWEI = Brand(
        "Huawei",
        battery = listOf(QuirkPage("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        autostart = listOf(
            QuirkPage("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            QuirkPage("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        ),
    )

    private val BRANDS: List<Pair<Set<String>, Brand>> = listOf(
        setOf("xiaomi", "redmi", "poco") to XIAOMI,
        setOf("samsung") to SAMSUNG,
        setOf("oneplus") to ONEPLUS,
        setOf("oppo", "realme") to OPPO,
        setOf("vivo", "iqoo") to VIVO,
        setOf("huawei", "honor") to HUAWEI,
    )
}
