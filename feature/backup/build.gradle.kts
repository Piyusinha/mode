plugins {
    id("mode.kmp.feature")
    alias(libs.plugins.kotlinxSerialization)
}

// Backup and restore: everything Mode keeps (chats, tasks, runs, memory, repeats, settings and,
// when asked, the sign-ins) in one file, locked with a password when it holds sign-ins.
kotlin {
    // The tests also run on the iOS simulator (CryptoKit, CommonCrypto, the native SQLite driver).
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.configureEach { linkerOpts("-lsqlite3") }
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.core.database)
            api(projects.core.storage)
            implementation(projects.core.common)
            // Which Keychain entries make up each sign-in.
            implementation(projects.feature.settings)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
    }
}
