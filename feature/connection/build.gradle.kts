plugins {
    id("mode.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.engine.ops)
            api(projects.core.platform)
            implementation(projects.core.common)
        }
        iosMain.dependencies {
            implementation(projects.engine.phoneIos)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
