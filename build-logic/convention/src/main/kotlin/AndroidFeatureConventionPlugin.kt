import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A feature = Android library + Compose + Hilt + type-safe navigation + the core layers it talks to. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("proofdrop.android.library")
        pluginManager.apply("proofdrop.android.compose")
        pluginManager.apply("proofdrop.android.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:designsystem"))
            add("implementation", libs.lib("androidx-activity-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
        }
    }
}
