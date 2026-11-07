plugins {
    id("mode.kmp.library")
}

// The Android engine: the phone driven through Mode's accessibility service.
// The backend and its tree-to-text logic are common code with a fake in tests;
// androidMain holds the service itself.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.engine.ops)
            api(projects.engine.screen)
            implementation(projects.core.telemetry)
            implementation(projects.core.common)
        }
    }
}
