pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ProofDrop"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:model")
include(":core:evidence")
include(":core:designsystem")
include(":core:database")
include(":core:network")
include(":core:data")
include(":core:ble")
include(":feature:auth")
include(":feature:orders")
include(":feature:capture")
include(":feature:checkout")
include(":feature:fleet")
