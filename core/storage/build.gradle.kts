plugins {
    id("mode.kmp.feature")
    alias(libs.plugins.androidKmp)
}

kotlin {
    android {
        namespace = "com.piyush.mode.core.storage"
        compileSdk = 36
        minSdk = 26
    }
}
