plugins {
    id("mode.kmp.feature")
    alias(libs.plugins.sqldelight)
}

// One on-device SQLite database for conversations, tasks, runs and memory. Features own the repositories; this module owns the schema.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
            api(libs.sqldelight.coroutines)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(projects.testing)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("ModeDatabase") {
            packageName.set("com.piyush.mode.core.database")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
