plugins {
    id("mode.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.telemetry)
            api(projects.engine.ops)
            api(projects.core.platform)
            implementation(projects.core.common)
        }
        iosMain.dependencies {
            implementation(projects.engine.phoneIos)
        }
        androidMain.dependencies {
            implementation(projects.engine.phoneAndroid)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
