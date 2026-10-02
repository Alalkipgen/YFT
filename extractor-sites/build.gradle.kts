plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":extractor-api"))
    implementation(project(":core-model"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
