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
            // The database's native driver uses the system SQLite.
            linkerOpts("-lsqlite3")
            // What Swift sees by name.
            export(projects.app.graph)
            export(projects.feature.connection)
            export(projects.feature.settings)
            export(projects.feature.runs)
            export(projects.feature.tasks)
            export(projects.feature.diagnostics)
            export(projects.feature.compare)
            export(projects.feature.conversations)
            export(projects.feature.memory)
            export(projects.feature.schedules)
            export(projects.app.screens)
            export(projects.engine.agent)
            export(projects.engine.llm)
            export(projects.engine.ops)
            // The notifier's question ids and actions, for the app's notification-answer handler.
            export(projects.core.platform)
            export(projects.core.telemetry)
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
            api(projects.feature.conversations)
            api(projects.feature.memory)
            api(projects.feature.schedules)
            api(projects.app.screens)
            api(projects.engine.agent)
            api(projects.engine.llm)
            api(projects.engine.ops)
            api(projects.core.platform)
            api(projects.core.telemetry)
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
