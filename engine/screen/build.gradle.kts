plugins {
    id("mode.kmp.library")
}

// What every phone engine shares: the screen in points, text with boxes, the
// app list, and the JSON shapes the agent reads. No platform code here.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.engine.ops)
        }
    }
}
