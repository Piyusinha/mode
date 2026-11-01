plugins {
    id("mode.kmp.library")
}

// Fakes for tests across modules. Outside the layers: only test source sets use it.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.engine.ops)
            api(projects.engine.llm)
            api(projects.core.storage)
        }
    }
}
