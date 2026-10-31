package com.piyush.mode.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** The root view controller Swift hosts in its window. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
