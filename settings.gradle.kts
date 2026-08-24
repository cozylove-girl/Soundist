pluginManagement {
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

rootProject.name = "Soundist"
include(":app")
include(":core:model")
include(":core:database")
include(":core:audio")
include(":core:network")
include(":core:designsystem")
include(":feature:productivity")
include(":feature:notes")
include(":feature:records")
include(":feature:listening")
