import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * Keeps the layers honest: app → feature → engine → core. A module may use
 * modules in its own layer or below, never above. `./gradlew checkModuleRules`
 * fails the build when a dependency points the wrong way.
 */
class ModuleRulesPlugin : Plugin<Project> {
    override fun apply(root: Project) {
        val violations = mutableListOf<String>()
        root.gradle.projectsEvaluated { violations += findViolations(root) }
        val check = root.tasks.register("checkModuleRules") {
            group = "verification"
            description = "Fails when a module depends on a layer above it."
            val found = violations
            doLast {
                if (found.isNotEmpty()) {
                    throw GradleException("Module rules broken:\n" + found.joinToString("\n") { "  $it" })
                }
                logger.lifecycle("Module rules: OK")
            }
        }
        root.allprojects {
            if (this != root) tasks.matching { it.name == "check" }.configureEach { dependsOn(check) }
        }
    }

    private fun findViolations(root: Project): List<String> = root.subprojects.flatMap { project ->
        val from = layer(project.path) ?: return@flatMap emptyList()
        project.configurations
            .filter { !it.name.contains("test", ignoreCase = true) }
            .flatMap { it.dependencies.withType(ProjectDependency::class.java) }
            .map { it.path }
            .distinct()
            .mapNotNull { to ->
                val toLayer = layer(to) ?: return@mapNotNull null
                if (toLayer > from) "${project.path} → $to (${LAYERS[from]} may not use ${LAYERS[toLayer]})" else null
            }
    }.distinct()

    private fun layer(path: String): Int? = LAYERS.indexOfFirst { path.startsWith(":$it:") }.takeIf { it >= 0 }

    private companion object {
        /** Lowest first: a module may depend on its own layer or any earlier one. */
        val LAYERS = listOf("core", "engine", "feature", "app")
    }
}
