plugins {
    id("mode.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.connection)
            api(projects.core.platform)
            api(projects.core.common)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
