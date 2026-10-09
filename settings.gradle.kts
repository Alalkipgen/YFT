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
    // Owner-approved backup branch; Android integration is explicitly opt-in.
    ":extractor-master",
    ":extractor-master-android",
)
