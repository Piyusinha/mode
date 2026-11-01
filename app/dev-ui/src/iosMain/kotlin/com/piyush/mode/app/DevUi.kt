package com.piyush.mode.app

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.ComposeUIViewController
import com.piyush.mode.app.graph.ModeGraph
import org.jetbrains.skia.Image
import platform.UIKit.UIViewController

/** The developer screens for [graph], as a view controller Swift can host. */
fun devUiViewController(graph: ModeGraph): UIViewController = ComposeUIViewController {
    App(graph, decodeImage = { bytes -> Image.makeFromEncoded(bytes).toComposeImageBitmap() })
}
