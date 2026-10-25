pluginManagement {
    repositories {
        removeIf { it.name.contains("microEJ", ignoreCase = true) || it.name.contains("localRepository", ignoreCase = true) }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        removeIf { it.name.contains("microEJ", ignoreCase = true) || it.name.contains("localRepository", ignoreCase = true) }
        google()
        mavenCentral()
    }
}

rootProject.name = "AshuChat"
include(":app")

