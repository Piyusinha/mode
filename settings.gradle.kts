rootProject.name = "mode"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// Layers, lowest first: core → engine → feature → app (see docs/architecture.md).
include(":core:common")
include(":core:storage")
include(":core:platform")
include(":core:network")
include(":core:database")
include(":engine:ops")
include(":engine:llm")
include(":engine:agent")
include(":engine:phone-ios")
include(":feature:connection")
include(":feature:settings")
include(":feature:runs")
include(":feature:tasks")
include(":feature:diagnostics")
include(":feature:compare")
// Test fakes shared by every module (test source sets only).
include(":testing")
include(":app:graph")
include(":app:dev-ui")
include(":app:kit")
