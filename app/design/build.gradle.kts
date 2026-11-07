plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
}

// Mode's design system on Android: the same tokens and components as the iPhone's
// ModeDesignSystem package (colours, logos and icons generated from it by
// tools/gen-android-design.py). Screens use these and nothing else.
android {
    namespace = "com.piyush.mode.design"
    compileSdk = 37
    defaultConfig { minSdk = 33 }
    buildFeatures { compose = true }
}

kotlin {
    explicitApi()
}

dependencies {
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
