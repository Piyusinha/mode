package com.piyush.mode.app.live

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.design.ModePipView

/** Mode in its small floating window while a background task runs (board R02): the hidden screen and Mo at work. */
@Composable
fun PipRoute(graph: ModeGraph) {
    val presenter = remember { graph.screens.liveRun() }
    DisposableEffect(presenter) { onDispose { presenter.close() } }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val screen = remember(ui.screen) { ui.screen?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    ModePipView(screen, stringResource(R.string.setup_welcome_app_name), ui.running?.text ?: ui.now.orEmpty(), Modifier.fillMaxSize())
}
