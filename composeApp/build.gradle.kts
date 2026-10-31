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
            export(projects.shared.coreOps)
            export(projects.shared.phoneCoredevice)
            export(projects.shared.agent)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.coreOps)
            api(projects.shared.phoneCoredevice)
            api(projects.shared.agent)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
        }
    }
}
