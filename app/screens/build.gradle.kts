plugins {
    id("mode.kmp.feature")
}

// One presenter per screen: StateFlow<UiState> plus intents, every decision made
// here so the native UIs (SwiftUI now, Android later) only draw state and pass events.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.tasks)
            api(projects.feature.memory)
            api(projects.feature.schedules)
            api(projects.feature.settings)
            api(projects.feature.conversations)
            api(projects.feature.runs)
            api(projects.feature.diagnostics)
            api(projects.feature.connection)
            api(projects.feature.backup)
            implementation(projects.core.common)
            implementation(projects.core.telemetry)
        }
        commonTest.dependencies {
            implementation(projects.testing)
            implementation(libs.ktor.client.mock)
        }
    }
}
