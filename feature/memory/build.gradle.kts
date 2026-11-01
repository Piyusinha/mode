plugins {
    id("mode.kmp.feature")
    alias(libs.plugins.kotlinxSerialization)
}

// What Mode remembers between tasks: facts about the user, skills (routes that
// worked) and app tips; how they are found for a task, and how a finished task
// teaches new ones. Everything stays in the on-device database.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.database)
            api(projects.engine.agent)
            implementation(projects.core.common)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
