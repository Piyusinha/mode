plugins {
    id("mode.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.engine.agent)
            api(projects.core.storage)
            api(projects.core.database)
            implementation(projects.core.common)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
