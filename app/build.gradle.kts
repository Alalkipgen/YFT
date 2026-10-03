import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

// Release identity lives in gradle.properties so scripts/release-prep.sh reads the same values.
val yftVersionCode = providers.gradleProperty("yft.versionCode").get().toInt()
val yftVersionName = providers.gradleProperty("yft.versionName").get()
require(yftVersionCode > 0) { "yft.versionCode must be a positive integer" }
require(Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?""").matches(yftVersionName)) {
    "yft.versionName must look like 1.2.3 or 1.2.3-beta.1"
}

class ReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

// Release signing comes from YFT_RELEASE_* environment variables (CI) or, per key, from the
// untracked keystore.properties file (local builds); see docs/RELEASE.md. Without either the
// release APK stays unsigned: it never falls back to the debug key. Values are never logged.
val releaseSigning: ReleaseSigning? = run {
    val localFile = rootProject.file("keystore.properties")
    val local = Properties()
    if (localFile.isFile) localFile.inputStream().use(local::load)
    fun setting(env: String, key: String): String? =
        (providers.environmentVariable(env).orNull ?: local.getProperty(key))
            ?.takeIf { it.isNotBlank() }
    val storePath = setting("YFT_RELEASE_STORE_FILE", "storeFile")?.trim()
    val storePassword = setting("YFT_RELEASE_STORE_PASSWORD", "storePassword")
    val keyAlias = setting("YFT_RELEASE_KEY_ALIAS", "keyAlias")?.trim()
    val keyPassword = setting("YFT_RELEASE_KEY_PASSWORD", "keyPassword")
    val values = listOf(storePath, storePassword, keyAlias, keyPassword)
    when {
        values.all { it == null } -> null
        storePath == null || storePassword == null || keyAlias == null || keyPassword == null ->
            throw GradleException(
                "Release signing is only partly configured. Provide all of " +
                    "YFT_RELEASE_STORE_FILE, YFT_RELEASE_STORE_PASSWORD, YFT_RELEASE_KEY_ALIAS " +
                    "and YFT_RELEASE_KEY_PASSWORD or the matching keystore.properties keys " +
                    "(docs/RELEASE.md).",
            )
        else -> {
            val store = rootProject.file(storePath)
            if (!store.isFile) throw GradleException("Release keystore not found: ${store.path}")
            ReleaseSigning(store, storePassword, keyAlias, keyPassword)
        }
    }
}

// scripts/release-prep.sh and the release workflow pass -Pyft.requireReleaseSigning=true so a
// missing key fails the build instead of producing an unsigned "release".
val requireReleaseSigning = providers.gradleProperty("yft.requireReleaseSigning").orNull == "true"
if (requireReleaseSigning && releaseSigning == null) {
    throw GradleException(
        "Release signing is required but not configured. Set the YFT_RELEASE_* variables or " +
            "create keystore.properties (docs/RELEASE.md).",
    )
}

android {
    namespace = "com.alal.yft"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alal.yft"
        minSdk = 24
        targetSdk = 35
        versionCode = yftVersionCode
        versionName = yftVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The YouTube adapter can be switched off per build without touching the adapter list.
        buildConfigField("boolean", "YOUTUBE_ADAPTER_ENABLED", "true")
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = releaseSigning.storeFile
                storePassword = releaseSigning.storePassword
                keyAlias = releaseSigning.keyAlias
                keyPassword = releaseSigning.keyPassword
                // v2 covers Android 7.0+; v3 adds key-rotation support on Android 9+.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-data"))
    implementation(project(":core-browser"))
    implementation(project(":core-media"))
    implementation(project(":core-download"))
    implementation(project(":extractor-api"))
    implementation(project(":extractor-generic"))
    implementation(project(":extractor-sites"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    kapt(libs.hilt.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
