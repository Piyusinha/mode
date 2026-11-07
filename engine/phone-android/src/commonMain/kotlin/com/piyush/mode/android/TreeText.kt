package com.piyush.mode.android

import kotlin.math.abs

/** One piece of on-screen text in dp, its centre ready to tap, and the window it is in. */
data class TextBox(val text: String, val x: Double, val y: Double, val w: Double, val h: Double, val window: String)

/**
 * The accessibility tree as text with positions: what `screen.text` answers on
 * Android, in place of OCR. Visible nodes only; a node's text, else its
 * description, else its hint; reading order; the keyboard's keys marked `ime`.
 */
object TreeText {
    /** The most boxes from the app and system windows; the keyboard's are extra. */
    const val MAX_BOXES = 80

    /** Two boxes with the same text whose centres are this close (dp) are one. */
    private const val SAME_DP = 4.0

    fun boxes(windows: List<WindowSnapshot>, density: Float, max: Int = MAX_BOXES): List<TextBox> {
        val page = mutableListOf<TextBox>()
        val keys = mutableListOf<TextBox>()
        for (window in windows) {
            val name = when (window.kind) {
                WindowKind.App -> "app"
                WindowKind.Ime -> "ime"
                WindowKind.System, WindowKind.Other -> "system"
            }
            val out = if (window.kind == WindowKind.Ime) keys else page
            window.root?.let { walk(it, density, name, out) }
        }
        return (dedupe(page).take(max) + dedupe(keys))
    }

    private fun walk(node: NodeSnapshot, density: Float, window: String, out: MutableList<TextBox>) {
        if (!node.visible || node.bounds.width <= 0 || node.bounds.height <= 0) return
        val label = listOf(node.text, node.contentDescription, node.hint).firstOrNull { !it.isNullOrBlank() }?.trim()
        if (label != null && !node.password) {
            out += TextBox(
                text = label,
                x = node.bounds.centerX / density,
                y = node.bounds.centerY / density,
                w = node.bounds.width / density.toDouble(),
                h = node.bounds.height / density.toDouble(),
                window = window,
            )
        }
        node.children.forEach { walk(it, density, window, out) }
    }

    /** Reading order, and one box where a container repeats its child's text at the same place. */
    private fun dedupe(boxes: List<TextBox>): List<TextBox> {
        val sorted = boxes.sortedWith(compareBy<TextBox> { it.y }.thenBy { it.x })
        val kept = mutableListOf<TextBox>()
        for (b in sorted) {
            val twin = kept.firstOrNull { it.text == b.text && abs(it.x - b.x) <= SAME_DP && abs(it.y - b.y) <= SAME_DP }
            if (twin == null) kept += b
            else if (b.w * b.h < twin.w * twin.h) { kept[kept.indexOf(twin)] = b }
        }
        return kept
    }
}
