import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Every shared module: the iPhone and Android targets that ship, plus a JVM
 * target whose only job is running the common tests in seconds.
 *
 * Per-module switches, set in the module's `gradle.properties`:
 * - `mode.android=false`: no Android target (the iPhone engine, which carries a cinterop).
 * - `mode.ios=false`: no iPhone targets (Android-only engines).
 * - `mode.android.hostTests=true`: also run the common tests through the Android
 *   library target on the host JVM (`testAndroidHostTest`).
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val android = findProperty("mode.android") != "false"
        val ios = findProperty("mode.ios") != "false"
        val hostTests = findProperty("mode.android.hostTests") == "true"
        if (android) pluginManager.apply("com.android.kotlin.multiplatform.library")
        extensions.configure<KotlinMultiplatformExtension> {
            jvm()
            if (ios) {
                iosArm64()
                iosSimulatorArm64()
            }
            if (android) {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension>("android") {
                    namespace = "com.piyush.mode" + path.replace(':', '.').replace('-', '_')
                    compileSdk = COMPILE_SDK
                    minSdk = MIN_SDK
                    if (hostTests) withHostTestBuilder { sourceSetTreeName = "test" }.configure {}
                }
            }
            sourceSets.getByName("commonTest").dependencies {
                implementation(kotlin("test"))
                implementation(libs.findLibrary("kotlinx-coroutines-test").get())
            }
        }
        // A failed test prints its whole exception: CI's log then shows the line that failed, not only its name.
        tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
            testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    companion object {
        /** Built against Android 17 (API 37), the newest platform in the local SDK. */
        const val COMPILE_SDK = 37
        /** Android 13: wireless debugging, restricted settings and the virtual display all exist from here. */
        const val MIN_SDK = 33
    }
}
