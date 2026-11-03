plugins {
    id("mode.kmp.feature")
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.telemetry)
            api(projects.engine.agent)
            api(projects.core.storage)
            api(projects.engine.llm)
            implementation(projects.core.common)
        }
        commonTest.dependencies {
            implementation(projects.testing)
            implementation(libs.ktor.client.mock)
        }
    }
}
