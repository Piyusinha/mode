plugins {
    alias(libs.plugins.androidLibrary)
}

// Background mode's link: Mode connects to its own phone's Wireless debugging (pair once with a code, then
// connect by itself), and runs the helper (engine:adb-helper) as the shell user over it.
// Built on libadb-android (Apache-2.0 option) with Conscrypt for TLS 1.3, as its README describes.
android {
    namespace = "com.piyush.mode.adb"
    compileSdk = 37
    defaultConfig { minSdk = 33 }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.libadb.android)
    implementation(libs.conscrypt.android)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test-junit"))
}
