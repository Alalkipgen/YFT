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

// P7: the preview build (com.alal.yft.preview) is the release build signed with a throwaway test
// key from the YFT_PREVIEW_* variables, which CI creates for preview builds only (docs/RELEASE.md).
// It never reads the release key or keystore.properties; without the variables it stays unsigned.
val previewSigning: ReleaseSigning? = run {
    fun setting(name: String): String? =
        providers.environmentVariable("YFT_PREVIEW_$name").orNull?.takeIf { it.isNotBlank() }
    val storePath = setting("STORE_FILE")?.trim()
    val storePassword = setting("STORE_PASSWORD")
    val keyAlias = setting("KEY_ALIAS")?.trim()
    val keyPassword = setting("KEY_PASSWORD")
    val values = listOf(storePath, storePassword, keyAlias, keyPassword)
    when {
        values.all { it == null } -> null
        storePath == null || storePassword == null || keyAlias == null || keyPassword == null ->
            throw GradleException(
                "Preview signing is only partly configured. Provide all of " +
                    "YFT_PREVIEW_STORE_FILE, YFT_PREVIEW_STORE_PASSWORD, YFT_PREVIEW_KEY_ALIAS " +
                    "and YFT_PREVIEW_KEY_PASSWORD (docs/RELEASE.md).",
            )
        else -> {
            val store = rootProject.file(storePath)
            if (!store.isFile) throw GradleException("Preview keystore not found: ${store.path}")
            if (store.canonicalFile == releaseSigning?.storeFile?.canonicalFile) {
                throw GradleException("The preview build must never use the release keystore.")
            }
            ReleaseSigning(store, storePassword, keyAlias, keyPassword)
        }
    }
}

// CI passes its run number, so each preview names the build it came from: 1.0.0-beta.3-preview.42.
val previewBuild = providers.gradleProperty("yft.previewBuild").orNull?.trim()
require(previewBuild == null || Regex("""\d{1,9}""").matches(previewBuild)) {
    "yft.previewBuild must be a build number"
}

// The Master switches below (-Pyft.*) drive debug builds only (the owner's test APKs). The release
// and both previews never read them: decision 30's fixed flags ([releaseMaster]) — Master on in the
// release, the trials only in #12 trial. "Release and preview false" below predates decision 30.
val masterCapture = providers.gradleProperty("yft.masterCapture").orElse("false").get()
require(masterCapture in setOf("true", "false")) {
    "yft.masterCapture must be true or false"
}
// Phase 1.1 S5: Master's own YouTube n/sig solver instead of main's ejs runner. Used only when
// yft.masterCapture is on too; off by default in debug builds (on in the release: decision 30).
val ownSolver = providers.gradleProperty("yft.ownSolver").orElse("false").get()
require(ownSolver in setOf("true", "false")) {
    "yft.ownSolver must be true or false"
}
// Phase 1.2 N5b: the own solver's ladder (worker P, worker B, page E, live check V) instead of
// Phase 1.1's single worker run. Acts only with yft.ownSolver and yft.masterCapture on; off by
// default, debug builds only (owner test APKs), never in a public release before N8.
val ownSolverNext = providers.gradleProperty("yft.ownSolverNext").orElse("false").get()
require(ownSolverNext in setOf("true", "false")) {
    "yft.ownSolverNext must be true or false"
}
// Phase 1.3 D6: Master's client ladder (MASTER_KEY_PHASE1_3_PLAN.md guardrail 1). Acts only with
// yft.masterCapture on; off by default, debug builds only (owner test APKs), release false.
val clientLadder = providers.gradleProperty("yft.clientLadder").orElse("false").get()
require(clientLadder in setOf("true", "false")) {
    "yft.clientLadder must be true or false"
}
// Phase 1.3 D7: the owner's live-check switches for the ladder (debug only, with the ladder on;
// release and preview: none). Blank = none; one of the two at a time. D5c: h-only = H's row only.
val ladderSkip = providers.gradleProperty("yft.ladderSkip").orElse("").get().trim()
require(ladderSkip in setOf("", "visionos", "visionos-adaptive", "h-only")) {
    "yft.ladderSkip must be blank, visionos, visionos-adaptive or h-only"
}
val ladderMutate = providers.gradleProperty("yft.ladderMutate").orElse("").get().trim()
require(ladderMutate in setOf("", "L1", "L2", "L3", "L5", "L7")) {
    "yft.ladderMutate must be blank, L1, L2, L3, L5 or L7"
}
require(ladderSkip.isEmpty() || ladderMutate.isEmpty()) {
    "yft.ladderSkip and yft.ladderMutate are separate modes"
}
// Phase 1.4 item 7 (YouTube routing B, MASTER_KEY_PHASE1_4_ROUTING_PLAN.md): Master answers a
// YouTube page only after main failed with a recoverable reason. Acts only with yft.masterCapture
// on (debug builds only, the owner's test APKs); release and preview false. Promoted (the owner,
// 2026-10-19, plan section 9 item 4): on by default; -Pyft.youtubeRouteB=false gives P12 as before.
val youtubeRouteB = providers.gradleProperty("yft.youtubeRouteB").orElse("true").get()
require(youtubeRouteB in setOf("true", "false")) {
    "yft.youtubeRouteB must be true or false"
}
// Phase 1.4 item 7 R4: the route-B trial (the D7 APK "routeb"): a YouTube page main found is
// answered as main's failure, so Master's route B answers on the phone. Acts only with
// yft.youtubeRouteB and yft.masterCapture on; debug builds only, release and preview false.
val routeBTrial = providers.gradleProperty("yft.routeBTrial").orElse("false").get()
require(routeBTrial in setOf("true", "false")) {
    "yft.routeBTrial must be true or false"
}
// Social plan S1a (MASTER_KEY_SOCIAL_PLAN.md section 7.3): Master's social ladder, Facebook first
// (the pilot): the per-lookup request record in the app's lookup path, then the judged rungs.
// Acts only with yft.masterCapture on; off by default, debug builds only (the owner's test APKs),
// release and preview false.
val socialLadder = providers.gradleProperty("yft.socialLadder").orElse("false").get()
require(socialLadder in setOf("true", "false")) {
    "yft.socialLadder must be true or false"
}
// Social plan S1a (9): the Facebook trial (the D7 APK "social-trial", S1a's phone proof): a Facebook
// page main found is answered as main's failure, so Master's social ladder answers on the phone.
// Acts only with yft.socialLadder and yft.masterCapture on; debug builds only, release and preview
// false.
val socialTrial = providers.gradleProperty("yft.socialTrial").orElse("false").get()
require(socialTrial in setOf("true", "false")) {
    "yft.socialTrial must be true or false"
}
// R9: the public key (X.509 P-256, hex) for Master's signed recipe config; blank = bundled
// recipes only and no config request. Only read when Master capture is on. Social plan S4: a
// social-ladder debug build (yft.socialLadder with yft.masterCapture) carries the committed key,
// extractor-master/recipes/master-recipes.pub.hex, unless yft.masterRecipeKey names one; every
// other build as before (no key unless given). The config's address: the recipes branch, where
// the master-recipes-publish workflow puts each signed config.
val masterRecipeKey = providers.gradleProperty("yft.masterRecipeKey").orElse("").get()
require(Regex("[0-9a-fA-F]*").matches(masterRecipeKey)) { "yft.masterRecipeKey must be hex" }
val committedRecipeKey =
    providers.fileContents(rootProject.layout.projectDirectory.file("extractor-master/recipes/master-recipes.pub.hex"))
        .asText.map(String::trim)
val socialRecipeKey = providers.gradleProperty("yft.masterRecipeKey").orElse(committedRecipeKey)
// The recipes branch's config (the master-recipes-publish workflow writes it); the release's only.
// VK Master plan §9: from v1.0.1 the schema-2 file (all five sites, VK too); v1.0.0 reads
// master-recipes.json (schema 1), which the same workflow keeps signing.
val releaseRecipeUrl = "https://raw.githubusercontent.com/Alalkipgen/YFT/recipes/master-recipes-2.json"
val masterRecipeUrl = providers.gradleProperty("yft.masterRecipeUrl").orElse(releaseRecipeUrl).get()
require(Regex("""https://[A-Za-z0-9./_-]+""").matches(masterRecipeUrl)) {
    "yft.masterRecipeUrl must be an https address"
}

/**
 * Decision 30 (`MASTER_KEY_SOCIAL_PLAN.md` section 8): the signed release carries main with every
 * fix and Master on, fixed here (no -P property reaches a release build type): YouTube's route B
 * (the own solver, the client ladder and its version lookup on GitHub raw), the four sites'
 * ladders (L3 included), the browser's capture and R9's signed config with decision 5's committed
 * key (`extractor-master/recipes/master-recipes.pub.hex`, each social rung's `rungsOff`). Master
 * acts only after main fails (decision 7) and only while Settings' "Backup finder" is on (30 (i)).
 * Never: the ladder's test hooks (skip, mutate), the own solver's next ladder (not before N8),
 * Instagram's tab-fetch rung (decision 28), v1.0.2 item 26's AV1 merge switch (AV1 stays off) and
 * the owner tools (src/debug: the census, W, the canary, the stream check). The trials ([trials])
 * only in the #12 trial preview ("debug and preview only; the release never reads them"), which
 * also shows every detail; the release and #12 normal are quiet (30 (v)).
 */
fun com.android.build.api.dsl.ApplicationBuildType.releaseMaster(recipeKey: String, recipeUrl: String, trials: Boolean) {
    require(recipeKey.isNotEmpty() && Regex("[0-9a-fA-F]+").matches(recipeKey)) {
        "the release's recipe key (extractor-master/recipes/master-recipes.pub.hex) must be hex"
    }
    buildConfigField("boolean", "MASTER_CAPTURE_ENABLED", "true")
    buildConfigField("boolean", "OWN_SOLVER_ENABLED", "true")
    buildConfigField("boolean", "OWN_SOLVER_NEXT_ENABLED", "false")
    buildConfigField("boolean", "CLIENT_LADDER_ENABLED", "true")
    buildConfigField("String", "LADDER_SKIP", "\"\"")
    buildConfigField("String", "LADDER_MUTATE", "\"\"")
    buildConfigField("boolean", "YOUTUBE_ROUTE_B", "true")
    buildConfigField("boolean", "YOUTUBE_ROUTE_B_TRIAL", trials.toString())
    buildConfigField("boolean", "SOCIAL_LADDER", "true")
    buildConfigField("boolean", "SOCIAL_TRIAL", trials.toString())
    buildConfigField("boolean", "INSTAGRAM_TAB_RUNG", "false")
    buildConfigField("boolean", "AV1_MERGE_SWITCH", "false")
    buildConfigField("String", "MASTER_RECIPE_KEY", "\"$recipeKey\"")
    buildConfigField("String", "MASTER_RECIPE_URL", "\"$recipeUrl\"")
    buildConfigField("boolean", "QUIET_RELEASE", (!trials).toString())
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
        buildConfigField("boolean", "MASTER_CAPTURE_ENABLED", "false")
        buildConfigField("boolean", "OWN_SOLVER_ENABLED", "false")
        buildConfigField("boolean", "OWN_SOLVER_NEXT_ENABLED", "false")
        buildConfigField("boolean", "CLIENT_LADDER_ENABLED", "false")
        buildConfigField("String", "LADDER_SKIP", "\"\"")
        buildConfigField("String", "LADDER_MUTATE", "\"\"")
        buildConfigField("boolean", "YOUTUBE_ROUTE_B", "false")
        buildConfigField("boolean", "YOUTUBE_ROUTE_B_TRIAL", "false")
        buildConfigField("boolean", "SOCIAL_LADDER", "false")
        buildConfigField("boolean", "SOCIAL_TRIAL", "false")
        buildConfigField("String", "MASTER_RECIPE_KEY", "\"$masterRecipeKey\"")
        buildConfigField("String", "MASTER_RECIPE_URL", "\"$masterRecipeUrl\"")
        // Social plan S2d (decision 28): Instagram's tab-fetch rung, a social-ladder debug build's
        // only; never in the release or a preview.
        buildConfigField("boolean", "INSTAGRAM_TAB_RUNG", "false")
        // v1.0.2 item 26: the census screen's "AV1 merges (test)" box, a debug build's only; never
        // in the release or a preview, whose AV1 merges stay off (AV1_MP4_ENABLED).
        buildConfigField("boolean", "AV1_MERGE_SWITCH", "false")
        // Decision 30 (v): the quiet release (one line per failure, no Details or Try again until
        // the hidden Show details); the release and #12 normal only, never debug or #12 trial.
        buildConfigField("boolean", "QUIET_RELEASE", "false")
    }

    signingConfigs {
        // Spike branch only (Phase 1.3): one fixed debug key, so each opt-in debug APK from CI
        // installs as an update over the last one (the runner's own debug key differs per run).
        // The master-optin-debug-apk workflow decodes keystore/spike-debug.keystore.b64 into this
        // untracked file; without it the default debug key signs as before. It signs the debug
        // app (com.alal.yft.debug) and its test APK only; release and preview never use it.
        val spikeDebugStore = file("keystore/spike-debug.keystore")
        if (spikeDebugStore.isFile) {
            getByName("debug") {
                storeFile = spikeDebugStore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
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
        if (previewSigning != null) {
            create("preview") {
                storeFile = previewSigning.storeFile
                storePassword = previewSigning.storePassword
                keyAlias = previewSigning.keyAlias
                keyPassword = previewSigning.keyPassword
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "MASTER_CAPTURE_ENABLED", masterCapture)
            buildConfigField("boolean", "OWN_SOLVER_ENABLED", ownSolver)
            buildConfigField("boolean", "OWN_SOLVER_NEXT_ENABLED", ownSolverNext)
            buildConfigField("boolean", "CLIENT_LADDER_ENABLED", (masterCapture == "true" && clientLadder == "true").toString())
            buildConfigField("String", "LADDER_SKIP", "\"$ladderSkip\"")
            buildConfigField("String", "LADDER_MUTATE", "\"$ladderMutate\"")
            buildConfigField("boolean", "YOUTUBE_ROUTE_B", (masterCapture == "true" && youtubeRouteB == "true").toString())
            buildConfigField(
                "boolean", "YOUTUBE_ROUTE_B_TRIAL",
                (masterCapture == "true" && youtubeRouteB == "true" && routeBTrial == "true").toString(),
            )
            buildConfigField("boolean", "SOCIAL_LADDER", (masterCapture == "true" && socialLadder == "true").toString())
            // S4: the social-ladder build's recipe key (the committed one unless yft.masterRecipeKey names one).
            if (masterCapture == "true" && socialLadder == "true") {
                val key = socialRecipeKey.get()
                require(Regex("[0-9a-fA-F]*").matches(key)) { "the social build's recipe key must be hex" }
                buildConfigField("String", "MASTER_RECIPE_KEY", "\"$key\"")
            }
            buildConfigField(
                "boolean", "SOCIAL_TRIAL",
                (masterCapture == "true" && socialLadder == "true" && socialTrial == "true").toString(),
            )
            buildConfigField("boolean", "INSTAGRAM_TAB_RUNG", (masterCapture == "true" && socialLadder == "true").toString())
            // v1.0.2 item 26: AV1 merges as a test switch (off until the census screen's box is on).
            buildConfigField("boolean", "AV1_MERGE_SWITCH", "true")
            // Owner tools (src/debug, e.g. the N2 stream check): a launcher entry only when both
            // Master flags are on, so a debug build with the flags off stays as before.
            manifestPlaceholders["yftOwnerTools"] =
                (masterCapture == "true" && ownSolver == "true").toString()
            // Phase 1.2 N7: the solver canary needs the ladder (yft.ownSolverNext) as well.
            manifestPlaceholders["yftOwnerToolsNext"] =
                (masterCapture == "true" && ownSolver == "true" && ownSolverNext == "true").toString()
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
            releaseMaster(committedRecipeKey.get(), releaseRecipeUrl, trials = false)
        }
        // The owner's test build (#12 normal): the release exactly (code, shrinking, flags and
        // look), its own app ID so it installs next to the release app, and only ever the test key.
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview" + previewBuild?.let { ".$it" }.orEmpty()
            isDebuggable = false
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.findByName("preview")
            releaseMaster(committedRecipeKey.get(), releaseRecipeUrl, trials = false)
        }
        // Decision 30 (iii): #12 trial, the same release code with the trials on (route B's for
        // YouTube, the social one for the four sites: main still asks, its rows are set aside and
        // compared, so Master answers every lookup on the shrunk build) and every detail shown.
        // Its own app ID and name, so it installs next to #12 normal; only ever the test key.
        create("previewTrial") {
            initWith(getByName("release"))
            applicationIdSuffix = ".trial"
            versionNameSuffix = "-trial" + previewBuild?.let { ".$it" }.orEmpty()
            isDebuggable = false
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.findByName("preview")
            releaseMaster(committedRecipeKey.get(), releaseRecipeUrl, trials = true)
        }
    }

    sourceSets {
        // The release build's code without debug actions (CrashTestTrigger) serves the previews;
        // #12 trial takes the preview's update test mode (src/preview/java) and its own name.
        getByName("preview").java.srcDir("src/release/java")
        getByName("previewTrial").java.srcDirs("src/release/java", "src/preview/java")
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
    implementation(project(":extractor-master-android"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.webkit)
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
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
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
