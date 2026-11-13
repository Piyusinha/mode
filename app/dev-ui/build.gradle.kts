plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidKmp)
}

// The developer screens (Agent, Compare, Logs, Settings, Hands) in Compose,
// kept until the SwiftUI app replaces them. Reads everything from ModeGraph.
kotlin {
    // Android shows the same developer screens from Settings › Developer.
    android {
        namespace = "com.piyush.mode.app.dev_ui"
        compileSdk = 37
        minSdk = 33
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(projects.app.graph)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
        }
    }
}
