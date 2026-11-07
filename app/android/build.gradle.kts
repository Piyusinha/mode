plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// The Android app: Compose screens over the shared presenters, and the system
// pieces (services, receivers, widget) that Android needs in the app itself.
android {
    namespace = "com.piyush.mode.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.piyush.mode"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(projects.app.graph)
    implementation(projects.app.screens)
    implementation(projects.app.devUi)
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

