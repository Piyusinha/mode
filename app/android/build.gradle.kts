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
    implementation(projects.core.platform)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
