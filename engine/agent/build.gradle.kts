plugins {
    id("mode.kmp.library")
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.engine.ops)
            api(projects.engine.llm)
        }
    }
}
