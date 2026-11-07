package com.piyush.mode.adb

/** How Mode starts the helper, and reads its answers (see engine:adb-helper's Helper). */
public object HelperCommand {
    public const val MAIN: String = "com.piyush.mode.helper.Helper"

    /** The shell line that starts the helper from Mode's APK. */
    public fun of(apkPath: String): String {
        require(apkPath.isNotBlank() && apkPath.none { it == '\'' || it == '\n' }) { "bad apk path" }
        return "CLASSPATH='$apkPath' app_process / $MAIN"
    }

    public fun display(width: Int, height: Int, dpi: Int): String = "display $width $height $dpi"
    public fun start(displayId: Int, component: String): String = "start $displayId $component"
    public const val RELEASE: String = "release"
    public const val KEY_BACK: Int = 4
    public const val KEY_HOME: Int = 3
    public fun key(displayId: Int, keyCode: Int): String = "key $displayId $keyCode"
    public fun bring(displayId: Int): String = "bring $displayId"
    public fun send(taskId: Int, displayId: Int): String = "send $taskId $displayId"
}

public object HelperAnswer {
    public const val READY: String = "ready"

    /** The display id from "display 7"; null for an error. */
    public fun displayId(answer: String): Int? = answer.takeIf { it.startsWith("display ") }?.substringAfter(' ')?.trim()?.toIntOrNull()

    public fun ok(answer: String): Boolean = !answer.startsWith("error")

    /** The task moved to the user's screen, from "brought 87". */
    public fun broughtTask(answer: String): Int? = answer.takeIf { it.startsWith("brought ") }?.substringAfter(' ')?.trim()?.toIntOrNull()
}
