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
}

public object HelperAnswer {
    public const val READY: String = "ready"

    /** The display id from "display 7"; null for an error. */
    public fun displayId(answer: String): Int? = answer.takeIf { it.startsWith("display ") }?.substringAfter(' ')?.trim()?.toIntOrNull()

    public fun ok(answer: String): Boolean = !answer.startsWith("error")
}
