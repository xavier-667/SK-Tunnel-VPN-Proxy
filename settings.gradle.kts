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
        // Required for sing-box library
        maven { url = uri("https://maven.matsuridayo.com") }
    }
}
rootProject.name = "SKTunnel"
include(":app")