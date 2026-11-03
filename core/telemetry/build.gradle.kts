plugins {
    id("mode.kmp.library")
    alias(libs.plugins.androidKmp)
}

kotlin {
    android {
        namespace = "com.piyush.mode.core.telemetry"
        compileSdk = 36
        minSdk = 26
        // Run the exact same common contract tests through the Android library target.
        withHostTestBuilder { sourceSetTreeName = "test" }.configure {}
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.storage)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.otel.core)
            implementation(libs.otel.implementation)
            implementation(libs.otel.protobuf)
        }
    }
}
