plugins {
    id("mode.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.database)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
