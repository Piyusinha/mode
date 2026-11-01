plugins {
    id("mode.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.engine.agent)
            api(projects.core.storage)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
