plugins {
    id("mode.kmp.library")
}

// The composition root: builds every feature once and wires them together.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.connection)
            api(projects.feature.settings)
            api(projects.feature.runs)
            api(projects.feature.tasks)
            api(projects.feature.diagnostics)
            api(projects.feature.compare)
            api(projects.feature.conversations)
            api(projects.feature.memory)
            api(projects.feature.schedules)
            api(projects.app.screens)
            api(projects.core.database)
            api(projects.engine.llm)
            api(projects.core.storage)
            api(projects.core.platform)
            api(projects.core.common)
        }
        iosMain.dependencies {
            implementation(projects.core.network)
            implementation(projects.core.database)
            implementation(projects.engine.phoneIos)
        }
    }
}
