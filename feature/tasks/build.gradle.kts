plugins {
    id("mode.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.engine.agent)
            api(projects.feature.connection)
            api(projects.feature.settings)
            api(projects.feature.runs)
            api(projects.feature.conversations)
            api(projects.feature.memory)
            api(projects.core.platform)
            api(projects.core.common)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
