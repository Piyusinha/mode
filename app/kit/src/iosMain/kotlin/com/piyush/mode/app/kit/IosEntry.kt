package com.piyush.mode.app.kit

import com.piyush.mode.app.devUiViewController
import com.piyush.mode.app.graph.iosModeGraph
import platform.UIKit.UIViewController

/** The root view controller Swift hosts in its window (the developer screens, for now). */
fun MainViewController(): UIViewController = devUiViewController(iosModeGraph)
