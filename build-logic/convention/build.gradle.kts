plugins {
    `kotlin-dsl`
}

dependencies {
    // compileOnly: the root build puts the real plugin on the classpath (apply false).
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.android.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "mode.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpFeature") {
            id = "mode.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
        register("moduleRules") {
            id = "mode.module-rules"
            implementationClass = "ModuleRulesPlugin"
        }
    }
}
