package com.piyush.mode.feature.diagnostics.domain

/** One look at the screen: the picture (JPEG) and the text read off it. */
public class Shot(public val label: String, public val jpeg: ByteArray, public val texts: List<ScreenText>)

/** A piece of text on screen, with its centre in screen points. */
public class ScreenText(public val text: String, public val x: Double, public val y: Double)

public data class HandsCheckState(
    val busy: Boolean = false,
    /** The last check's result; the session closes after every check. */
    val status: String = "Phone: not checked yet",
    val log: List<String> = emptyList(),
    /** Newest first. */
    val shots: List<Shot> = emptyList(),
)
