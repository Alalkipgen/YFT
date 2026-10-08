plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":extractor-api"))
    implementation(project(":extractor-generic"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
}

tasks.test {
    // Explicit live evidence is an input; an old cached run must not pose as a fresh smoke.
    val snapshot = providers.environmentVariable("YFT_MASTER_SMOKE_SNAPSHOT")
    inputs.property("hasLivePlaybackSnapshot", snapshot.isPresent)
    if (snapshot.isPresent) {
        inputs.file(snapshot.get()).withPathSensitivity(PathSensitivity.NONE)
    }
}