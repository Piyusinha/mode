package com.piyush.mode.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.piyush.mode.core.platform.ActivityBridge

/** The one activity; every screen is a Compose destination inside it. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ActivityBridge.attach(this)
        val graph = ModeApplication.graph(this)
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Placeholder until the design system and screens land. literal-ok
                BasicText("Mode ${if (graph.screens.needsOnboarding) "· first run" else "· ready"}")
            }
        }
    }
}
