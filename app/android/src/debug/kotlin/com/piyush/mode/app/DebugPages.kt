package com.piyush.mode.app

import com.piyush.mode.app.shell.AppTab
import com.piyush.mode.app.shell.Navigator
import com.piyush.mode.app.shell.Route

/**
 * Debug builds only: opens a page by name, so each screen can be checked without tapping through the app.
 *
 *     adb shell am start -n app.heymode.mode/com.piyush.mode.app.MainActivity --es mode.page voice
 *
 * Names: home, tasks, settings, voice, listening, language, health, phonesetup, access, memory, aboutyou, apps,
 * routines, developer, backup, background, liverun, models, stuckmodels, planmodels, brainmodels, talk,
 * voicepicker:<provider>, provider:<id>, app:<name>, conversation:<id>, debug:<taskId>, screens:<taskId>,
 * repeat:<text>.
 */
object DebugPages {
    @JvmStatic
    fun open(nav: Navigator, page: String) {
        val (name, arg) = page.split(':', limit = 2).let { it[0] to it.getOrNull(1) }
        val route: Route? = when (name) {
            "home" -> null.also { nav.select(AppTab.Home) }
            "tasks" -> null.also { nav.select(AppTab.Tasks) }
            "talk" -> null.also { nav.talk(com.piyush.mode.app.talk.TalkRequest()) }
            "settings" -> Route.Settings
            "voice" -> Route.Voice
            "listening" -> Route.Listening
            "language" -> Route.Language
            "health" -> Route.Health
            "phonesetup" -> Route.PhoneSetup
            "access" -> Route.Access
            "memory" -> Route.Memory
            "aboutyou" -> Route.MemoryAboutYou
            "apps" -> Route.MemoryApps
            "routines" -> Route.MemoryRoutines(arg)
            "app" -> Route.MemoryApp(arg.orEmpty())
            "developer" -> Route.Developer
            "backup" -> Route.Backup
            "background" -> Route.Background
            "liverun" -> Route.LiveRun
            "models" -> Route.ModelPicker(false)
            "stuckmodels" -> Route.ModelPicker(true)
            "planmodels" -> Route.PlanModels(false)
            "brainmodels" -> Route.PlanModels(true)
            "voicepicker" -> Route.VoicePicker(arg ?: "apple")
            "provider" -> Route.ProviderDetail(arg ?: "chatgpt")
            "conversation" -> arg?.let { Route.Conversation(it) }
            "debug" -> arg?.let { Route.TaskDebug(it) }
            "screens" -> arg?.let { Route.RunScreens(it) }
            "repeat" -> Route.Repeat(arg ?: "Turn on Do Not Disturb")
            else -> null
        }
        if (route != null) nav.open(AppTab.Home, route)
    }
}
