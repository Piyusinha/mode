plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            // The one framework Swift imports: the UI plus every shared module it exports.
            baseName = "ModeKit"
            isStatic = true
            export(projects.engine.ops)
            export(projects.engine.phoneIos)
            export(projects.engine.agent)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.engine.ops)
            api(projects.engine.phoneIos)
            api(projects.engine.agent)
            implementation(projects.core.common)
            implementation(projects.core.storage)
            implementation(projects.core.platform)
            implementation(projects.core.network)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
        }
    }
}
