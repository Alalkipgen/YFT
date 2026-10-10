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

    // R1 offline parity: main's site fixtures and the frozen parity list are test inputs.
    val siteFixtures = rootProject.file("extractor-sites/src/test/resources/fixtures")
    val parityUrls = rootProject.file("app/src/androidTest/assets/parity/parity-urls.json")
    inputs.dir(siteFixtures).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(parityUrls).withPathSensitivity(PathSensitivity.NONE)
    systemProperty("yft.siteFixtures", siteFixtures.absolutePath)
    systemProperty("yft.parityUrls", parityUrls.absolutePath)
    systemProperty(
        "yft.parityBaselineFile",
        file("src/test/resources/parity/fixture-baseline.json").absolutePath,
    )
    // `-Pyft.parityBaseline=write` rewrites the baseline on purpose (never in CI).
    val baselineMode = providers.gradleProperty("yft.parityBaseline")
    if (baselineMode.isPresent) {
        systemProperty("yft.parityBaseline", baselineMode.get())
        outputs.upToDateWhen { false }
    }
}
