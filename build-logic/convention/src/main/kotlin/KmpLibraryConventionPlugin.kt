import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Every shared module: the iPhone targets that ship, plus a JVM target whose
 * only job is running the common tests in seconds.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        extensions.configure<KotlinMultiplatformExtension> {
            jvm()
            iosArm64()
            iosSimulatorArm64()
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
}
