plugins {
    id("mode.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
        }
        androidMain.dependencies {
            // The process lifecycle (foreground/background) and permission prompts, which need an Activity.
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.activity)
        }
    }
}
