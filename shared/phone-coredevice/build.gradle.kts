plugins {
    id("mode.kmp.library")
}

// Builds native/modecore for the iPhone and the simulator (cargo is incremental,
// so this is quick when nothing changed).
val buildModecore by tasks.registering(Exec::class) {
    description = "Builds the modecore static libraries with cargo"
    val root = rootProject.layout.projectDirectory
    inputs.dir(root.dir("native/modecore/src"))
    inputs.file(root.file("native/modecore/Cargo.toml"))
    inputs.file(root.file("native/modecore/Cargo.lock"))
    outputs.dir(root.dir("native/out"))
    workingDir = root.asFile
    commandLine(root.file("tools/build-native.sh").asFile.absolutePath)
}

kotlin {
    mapOf(
        iosArm64() to "ios-arm64",
        iosSimulatorArm64() to "ios-simulator-arm64",
    ).forEach { (target, outDir) ->
        target.compilations.getByName("main").cinterops.create("modecore") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/modecore.def"))
            includeDirs(rootProject.file("native/modecore/include"))
            extraOpts("-libraryPath", rootProject.file("native/out/$outDir").absolutePath)
            tasks.named(interopProcessingTaskName) {
                dependsOn(buildModecore)
                // The .a is embedded in the klib: repack whenever it changes.
                inputs.file(rootProject.file("native/out/$outDir/libmodecore.a"))
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.coreOps)
        }
    }
}
