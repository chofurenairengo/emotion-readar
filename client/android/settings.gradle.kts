pluginManagement {
    repositories {
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
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        flatDir {
            dirs("unity-plugin/unityLibrary/libs")
        }
    }
}

rootProject.name = "android"
include(":app")
include(":unity-plugin:unityLibrary")
include(":unity-plugin:unityLibrary:xrmanifest.androidlib")
