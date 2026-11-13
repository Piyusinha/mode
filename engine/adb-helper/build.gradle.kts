plugins {
    alias(libs.plugins.androidLibrary)
}

// Background mode's helper: a small program Mode starts as the shell user (through its own wireless
// debugging link) with app_process, from Mode's own APK. It makes the hidden display Mode works on and starts
// apps there. No dependencies: it runs outside Mode's app, with only the Android framework and Kotlin.
// Follows the scrcpy server's approach (Apache-2.0), see PATCHES.md.
android {
    namespace = "com.piyush.mode.helper"
    compileSdk = 37
    defaultConfig { minSdk = 33 }
}

kotlin {
    explicitApi()
}
