package com.piyush.mode.agent

/** The panel of quick toggles a phone has, and the tool that opens it. */
enum class QuickPanel { ControlCenter, QuickSettings }

/**
 * What differs between the phones Mode drives, as the agent needs to know it:
 * the words for the prompts, how far down the status bar reaches, whether
 * going back is an op or a gesture, and the OS know-how the model is given.
 * Everything else about a task is the same on every phone.
 */
data class PhoneProfile(
    val id: String,
    /** "iPhone", "Android phone": in prompts and in the backup's device name. */
    val deviceWord: String,
    /** The article before [deviceWord]: "an iPhone", "an Android phone". */
    val article: String,
    /** "iOS", "Android". */
    val osWord: String,
    /** How far down the status bar reaches, in points; text above it is never page content. */
    val statusBarPt: Double,
    /** True when the phone has a `nav.back` op; otherwise back is an edge swipe. */
    val backIsOp: Boolean,
    val quickPanel: QuickPanel,
    /** Whether the user's Shortcuts can be run by URL (iOS only). */
    val shortcuts: Boolean,
    /** What only the user can do on a locked screen: "Face ID", "your fingerprint or face unlock". */
    val biometric: String,
    /** One sentence on how going back behaves, in the orientation rules. */
    val backNote: String,
    /** The "Know <OS>…" paragraph: the fastest routes on this phone, one bullet per line. */
    val knowHow: String,
    /** Why a screen can be black or blank: who hides it. */
    val hiddenScreens: String,
) {
    companion object {
        val IPhone: PhoneProfile = PhoneProfile(
            id = "ios",
            deviceWord = "iPhone",
            article = "an",
            osWord = "iOS",
            statusBarPt = 60.0,
            backIsOp = false,
            quickPanel = QuickPanel.ControlCenter,
            shortcuts = true,
            biometric = "Face ID",
            backNote = "The iOS back swipe does nothing in many apps.",
            knowHow = """
                Know iOS and pick the fastest route (from wherever you are):
                - Settings has a search field at the top of its main page: searching a setting's name ("Dark
                  Mode", "Wi-Fi", "Brightness", "Bluetooth") jumps straight to it.
                - Control Center (control_center tool) has quick toggles: Wi-Fi, Bluetooth, Airplane Mode,
                  brightness, volume, Focus, Flashlight; long-press a control for more (e.g. Dark Mode under
                  brightness). Swipe it closed or press home when done.
                - Spotlight (swipe down in the middle of the Home Screen) finds apps, settings and contacts.
                - Most apps put Search, Back and tabs in the same places: tabs at the bottom, Back at top left.
                - You can start from any screen: you do not need to go home first.
            """.trimIndent(),
            hiddenScreens = "iPhone hides bank and PIN screens from Mode",
        )

        val Android: PhoneProfile = PhoneProfile(
            id = "android",
            deviceWord = "Android phone",
            article = "an",
            osWord = "Android",
            statusBarPt = 48.0,
            backIsOp = true,
            quickPanel = QuickPanel.QuickSettings,
            shortcuts = false,
            biometric = "your fingerprint or face unlock",
            backNote = "The back tool is Android's own Back: it works in every app, and closes dialogs and the keyboard.",
            knowHow = """
                Know Android and pick the fastest route (from wherever you are):
                - Settings has a search field at the top of its main page: searching a setting's name ("Dark
                  theme", "Wi-Fi", "Brightness", "Bluetooth") jumps straight to it.
                - Quick Settings (quick_settings tool) has quick toggles: Wi-Fi, Bluetooth, Airplane mode,
                  brightness, Do Not Disturb, Flashlight, Dark theme; long-press a tile to open its settings
                  page. Use back when done.
                - The notification shade (notifications tool) shows new messages, codes and alerts: tap one
                  to open it, back closes the shade.
                - Most apps put Search at the top, tabs at the bottom and Back (←) at top left; the back tool
                  always works, and once more from an app's first page leaves the app.
                - You can start from any screen: you do not need to go home first.
            """.trimIndent(),
            hiddenScreens = "apps hide bank, PIN and password screens from Mode",
        )
    }
}
