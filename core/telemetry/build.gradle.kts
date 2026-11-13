plugins {
    id("mode.kmp.library")
}

// The Android target also runs the common contract tests (mode.android.hostTests in gradle.properties).
kotlin {
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
