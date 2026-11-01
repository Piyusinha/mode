plugins {
    id("mode.kmp.feature")
}

// Tasks that repeat ("every weekday at 8"): when they are next due, handing
// them to the task inbox, and a notification for when Mode isn't open.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.tasks)
            api(projects.core.database)
            api(projects.core.platform)
            api(libs.kotlinx.datetime)
            implementation(projects.core.common)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
