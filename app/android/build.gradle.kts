plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// The Android app: Compose screens over the shared presenters, and the system
// pieces (services, receivers, widget) that Android needs in the app itself.

// One version everywhere: the iPhone's MARKETING_VERSION (iosApp/project.yml) is Mode's version.
val modeVersion: String = rootProject.file("iosApp/project.yml").readLines()
    .firstNotNullOfOrNull { Regex("""^\s*MARKETING_VERSION:\s*"?([0-9.]+)"?""").find(it)?.groupValues?.get(1) } ?: "0.1.0"

/** 1.2.3 → 10203: each release's code is higher than the last. */
fun versionCodeOf(version: String): Int = version.split('.').map { it.toIntOrNull() ?: 0 }.let { (it + listOf(0, 0, 0)).take(3) }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// Release signing from the environment (CI secrets or a local keystore); without it the release APK is unsigned.
val keystore: String? = System.getenv("MODE_ANDROID_KEYSTORE")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "com.piyush.mode.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.heymode.mode"
        minSdk = 33
        targetSdk = 37
        versionCode = versionCodeOf(modeVersion)
        versionName = modeVersion
    }

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("MODE_ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MODE_ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("MODE_ANDROID_KEY_PASSWORD") ?: System.getenv("MODE_ANDROID_KEYSTORE_PASSWORD")
            }
        }
    }

    // full: everything, from GitHub. play: no accessibility engine, for the Play Store.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") { dimension = "distribution" }
        create("play") {
            dimension = "distribution"
            applicationIdSuffix = ".play"
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // Not shrunk: the helper is started by class name from the APK, and the link and storage use reflection.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(projects.app.graph)
    implementation(projects.app.screens)
    implementation(projects.app.devUi)
    implementation(projects.engine.adbHelper)
    // The debug probe drives the engine directly (another display, background mode).
    debugImplementation(projects.engine.phoneAndroid)
    debugImplementation(projects.engine.adbLink)
    implementation(projects.core.network)
    implementation(projects.feature.connection)
    implementation(projects.app.design)
    implementation(projects.core.platform)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(kotlin("test-junit"))
}

// No literals in screens: tools/check-ui-literals-android.sh, part of `check`.
val checkUiLiterals = tasks.register<Exec>("checkUiLiterals") {
    group = "verification"
    description = "Fails when a screen hard-codes colours, sizes, fonts, icons or text."
    val script = rootProject.file("tools/check-ui-literals-android.sh")
    inputs.file(script)
    inputs.dir("src")
    outputs.upToDateWhen { true }
    commandLine(script.absolutePath)
}
tasks.named("check") { dependsOn(checkUiLiterals) }

