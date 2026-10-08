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

rootProject.name = "YFT"

include(
    ":app",
    ":core-model",
    ":core-data",
    ":core-browser",
    ":core-media",
    ":core-download",
    ":extractor-api",
    ":extractor-generic",
    ":extractor-sites",
    // Owner-approved backup prototype; no production module depends on it.
    ":extractor-master",
)
