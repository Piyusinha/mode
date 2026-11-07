package com.piyush.mode.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.piyush.mode.design.ModeCatalog
import com.piyush.mode.design.ModeTheme

/**
 * Debug builds only: the design system's catalogue.
 *     adb shell am start -n com.piyush.mode/com.piyush.mode.app.CatalogActivity --ez dark true
 */
class CatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dark = intent.getBooleanExtra("dark", false)
        setContent { ModeTheme(dark = dark) { ModeCatalog() } }
    }
}
