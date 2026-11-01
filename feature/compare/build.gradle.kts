plugins {
    id("mode.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.tasks)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
