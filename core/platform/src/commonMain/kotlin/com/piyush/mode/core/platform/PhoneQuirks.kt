package com.piyush.mode.core.platform

/**
 * An Android page to try: by its component, or by an [action] the maker's app answers; the app offers it only when it
 * opens on this phone. [intExtras] go on the intent as they are; [appPackageExtra] names the extra that takes Mode's
 * own package name (the maker's permission editor wants to know which app).
 */
public data class QuirkPage(
    val packageName: String,
    val className: String = "",
    val action: String? = null,
    val intExtras: Map<String, Int> = emptyMap(),
    val appPackageExtra: String? = null,
)

/** A setting Mode can't read back: the user does it and confirms it (rows on the setup hub, boards D01–D05). */
public enum class ManualStep { Sleeping, Autostart, Popups }

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
        /** Where the brand keeps apps it won't put to sleep (Samsung's "Never sleeping apps"). */
        val sleeping: List<QuirkPage> = emptyList(),
        /** Where the brand lets an app open things while in the background (Xiaomi's pop-up permission). */
        val popups: List<QuirkPage> = emptyList(),
        /** The steps this brand's phones need the user to do and confirm, in the order the hub shows them. */
        val manualSteps: List<ManualStep> = emptyList(),
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
        popups = listOf(QuirkPage("com.miui.securitycenter", action = "miui.intent.action.APP_PERM_EDITOR", appPackageExtra = "extra_pkgname")),
        manualSteps = listOf(ManualStep.Autostart, ManualStep.Popups),
    )
    private val SAMSUNG = Brand(
        "Samsung",
        battery = listOf(
            QuirkPage("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            QuirkPage("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ),
        autostart = emptyList(),
        // Samsung's own deep link into the "Never sleeping apps" picker (developer.samsung.com, app management).
        sleeping = listOf(
            QuirkPage("com.samsung.android.lool", action = "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY", intExtras = mapOf("activity_type" to NEVER_SLEEPING)),
            QuirkPage("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        ),
        manualSteps = listOf(ManualStep.Sleeping),
    )
    private val OPPO = Brand(
        "OPPO",
        battery = listOf(QuirkPage("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")),
        autostart = listOf(
            QuirkPage("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            QuirkPage("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            QuirkPage("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ),
        manualSteps = listOf(ManualStep.Autostart),
    )
    private val ONEPLUS = Brand(
        "OnePlus",
        battery = OPPO.battery,
        autostart = listOf(QuirkPage("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")) + OPPO.autostart,
        manualSteps = listOf(ManualStep.Autostart),
    )
    private val VIVO = Brand(
        "vivo",
        battery = listOf(QuirkPage("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        autostart = listOf(
            QuirkPage("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            QuirkPage("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ),
        manualSteps = listOf(ManualStep.Autostart),
    )
    private val HUAWEI = Brand(
        "Huawei",
        battery = listOf(QuirkPage("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        autostart = listOf(
            QuirkPage("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            QuirkPage("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        ),
        manualSteps = listOf(ManualStep.Autostart),
    )

    /** Samsung's activity_type for the never-sleeping list. */
    private const val NEVER_SLEEPING = 2

    private val BRANDS: List<Pair<Set<String>, Brand>> = listOf(
        setOf("xiaomi", "redmi", "poco") to XIAOMI,
        setOf("samsung") to SAMSUNG,
        setOf("oneplus") to ONEPLUS,
        setOf("oppo", "realme") to OPPO,
        setOf("vivo", "iqoo") to VIVO,
        setOf("huawei", "honor") to HUAWEI,
    )
}
