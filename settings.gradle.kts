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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Huawei HMS SDK — нужен для HmsLocationTracker (com.huawei.hms:location)
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
}

rootProject.name = "SmartTracker"
include(":app")