plugins {
    id("mode.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.engine.agent)
            api(projects.core.storage)
            implementation(projects.core.common)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
