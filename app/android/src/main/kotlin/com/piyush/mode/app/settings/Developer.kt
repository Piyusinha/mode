package com.piyush.mode.app.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import com.piyush.mode.app.App
import com.piyush.mode.app.R
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.design.ModePageBar

/** Whether Settings shows Developer: in debug builds only. The release APK people install hides it. */
object DeveloperFeature {
    fun enabled(context: android.content.Context): Boolean =
        context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
}

/** Settings › Developer: the shared developer screens (Agent, Compare, Logs, Settings, Hands), as on the iPhone. */
@Composable
fun DeveloperRoute(graph: ModeGraph, close: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ModePageBar(stringResource(R.string.common_back), close)
        App(graph) { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap() }
    }
}
