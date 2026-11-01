plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The developer screens (Agent, Compare, Logs, Settings, Hands) in Compose,
// kept until the SwiftUI app replaces them. Reads everything from ModeGraph.
kotlin {
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
