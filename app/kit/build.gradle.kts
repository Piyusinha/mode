plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Swift sees StateFlow as AsyncSequence, sealed classes as enums, suspend as async.
    alias(libs.plugins.skie)
}

// ModeKit.framework: the one Kotlin framework the iOS app imports. It holds
// the graph (every feature) and, for now, the Compose developer screens.
kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ModeKit"
            isStatic = true
            // What Swift sees by name.
            export(projects.app.graph)
            export(projects.feature.connection)
            export(projects.feature.settings)
            export(projects.feature.runs)
            export(projects.feature.tasks)
            export(projects.feature.diagnostics)
            export(projects.feature.compare)
            export(projects.engine.agent)
            export(projects.engine.ops)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.app.graph)
            api(projects.feature.connection)
            api(projects.feature.settings)
            api(projects.feature.runs)
            api(projects.feature.tasks)
            api(projects.feature.diagnostics)
            api(projects.feature.compare)
            api(projects.engine.agent)
            api(projects.engine.ops)
            implementation(projects.app.devUi)
        }
    }
}

skie {
    // An open-source build should not phone home.
    analytics {
        enabled.set(false)
    }
}
