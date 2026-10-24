package com.alal.yft.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.alal.yft.BuildConfig
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import com.alal.yft.update.UpdatePolicy.toHex
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Collections
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * U2 on a real Android (§6.2): the debug build (test mode) runs the in-app update with the real
 * PackageManager reading the APK and Android's real installer, hosted in the test's activity with
 * the app's own Updates screen and confirmation starter. GitHub is played by an OkHttp interceptor
 * on GitHub's own hosts and paths, its redirect included; the APK streams from its file and
 * nothing leaves the device. Android's installer is always cancelled, never confirmed: an install
 * would replace the app under test. Installs from YFT start not allowed on a fresh install, so that
 * case runs first; they are then allowed and never taken back (Android may end the app then).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UpdateInstallInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val github = FakeGitHub()
    private val ownApk = File(app.applicationInfo.sourceDir)
    private val cached = File(app.cacheDir, "updates/${UpdateDownloader.FILE_NAME}")
    private val changed = File(app.cacheDir, "update-test-changed.apk")
    private val update = checkNotNull(ReleaseVersion.parse(TAG))
    private lateinit var flow: UpdateInstallFlow
    private lateinit var receiver: BroadcastReceiver

    @Before
    fun setUp() {
        val environment = UpdateEnvironment(
            mode = UpdatePolicy.mode(app),
            installedCode = BuildConfig.VERSION_CODE.toLong(),
            installedName = BuildConfig.VERSION_NAME,
        )
        assertEquals(UpdateMode.TEST, environment.mode)
        flow = UpdateInstallFlow(
            environment = environment,
            source = UpdateDownloader(
                OkHttpClient.Builder().addInterceptor(github).build(),
                app.cacheDir,
            ),
            reader = PackageApkReader(app.packageManager),
            system = PackageInstallSystem(app, Intent(STATUS_ACTION).setPackage(app.packageName)),
            scope = scope,
        )
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) =
                InstallStatusReceiver.deliver(intent, flow)
        }
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(STATUS_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    @After
    fun tearDown() {
        if (device.hasObject(By.pkg(INSTALLER))) device.pressBack()
        app.unregisterReceiver(receiver)
        scope.cancel()
        val installer = app.packageManager.packageInstaller
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        changed.delete()
        cached.delete()
    }

    @Test
    fun step1InstallsNotAllowedDownloadNothing() {
        assumeFalse(
            "Installs from YFT are already allowed",
            app.packageManager.canRequestPackageInstalls(),
        )
        github.serve(ownApk)
        showUpdate(ownApk)

        composeRule.onNodeWithTag("updates-download").performScrollTo().performClick()

        waitFor("the Allow state") { hasTag("updates-not-allowed") }
        composeRule.onNodeWithTag("updates-open-settings").assertExists()
        assertEquals(emptyList<String>(), github.asked)
        assertFalse(cached.exists())
    }

    @Test
    fun step2TheChecksPassAndAndroidsInstallerIsCancelled() {
        allowInstalls()
        github.serve(ownApk)
        showUpdate(ownApk)

        composeRule.onNodeWithTag("updates-download").performScrollTo().performClick()

        waitFor("the verified line") { hasText("Verified: checksum and signature match") }
        cancelAndroidsInstaller()
        waitFor("the cancel") { hasText("Update cancelled.") }
        composeRule.onNodeWithText("Nothing was installed.").assertExists()
        composeRule.onNodeWithTag("updates-browser").assertExists()
        // The session holds its own copy: the cached APK went at once.
        assertFalse(cached.exists())
        waitFor("no session left") { app.packageManager.packageInstaller.mySessions.isEmpty() }
        assertEquals(
            listOf(
                "github.com $SUMS_PATH",
                "$ASSETS $ASSET_PATH",
                "github.com $APK_PATH",
                "$ASSETS $ASSET_PATH",
            ),
            github.asked,
        )
    }

    @Test
    fun step3OneChangedByteFailsTheChecksumBeforeASession() {
        allowInstalls()
        ownApk.copyTo(changed, overwrite = true)
        RandomAccessFile(changed, "rw").use { file ->
            val middle = file.length() / 2
            file.seek(middle)
            val byte = file.read()
            file.seek(middle)
            file.write(byte xor 0x01)
        }
        github.serve(changed, sumsOf = ownApk)
        showUpdate(changed)

        composeRule.onNodeWithTag("updates-download").performScrollTo().performClick()

        waitFor("the checksum line") {
            hasText("Checksum doesn't match — the download was damaged or changed.")
        }
        composeRule.onNodeWithText("Nothing was installed.").assertExists()
        assertTrue(app.packageManager.packageInstaller.mySessions.isEmpty())
        assertFalse(cached.exists())
    }

    @Test
    fun step4AnotherAppsApkFailsThePackageCheck() {
        allowInstalls()
        // The instrumentation APK: the same debug key, its own checksum, another package.
        val testApk = File(instrumentation.context.applicationInfo.sourceDir)
        github.serve(testApk)
        showUpdate(testApk)

        composeRule.onNodeWithTag("updates-download").performScrollTo().performClick()

        waitFor("the package line") { hasText("This file is a different app.") }
        composeRule.onNodeWithText("Nothing was installed.").assertExists()
        assertTrue(app.packageManager.packageInstaller.mySessions.isEmpty())
        assertFalse(cached.exists())
    }

    @Test
    fun step5ReinstallThisBuildReachesAndroidsInstallerToo() {
        allowInstalls()
        showUpdate(apk = null)

        composeRule.onNodeWithTag("updates-reinstall").performScrollTo().performClick()

        waitFor("the test line") { hasText("Test: this build's own APK, signature matches") }
        cancelAndroidsInstaller()
        waitFor("the cancel") { hasText("Update cancelled.") }
        composeRule.onNodeWithText("Nothing was installed.").assertExists()
        assertEquals(emptyList<String>(), github.asked)
        // Its own APK is never deleted.
        assertTrue(ownApk.exists())
    }

    /** The Updates screen with [apk]'s release found by Check now, or with nothing to find. */
    private fun showUpdate(apk: File?) {
        val found = apk?.let {
            FoundUpdate(
                version = update,
                code = null,
                prerelease = true,
                apkUrl = "https://github.com$APK_PATH",
                apkSize = it.length(),
                sumsUrl = "https://github.com$SUMS_PATH",
                notes = "The emulator test's release.",
                publishedAt = null,
            )
        }
        val controller = UpdateController(
            environment = UpdateEnvironment(
                mode = UpdateMode.TEST,
                installedCode = BuildConfig.VERSION_CODE.toLong(),
                installedName = BuildConfig.VERSION_NAME,
            ),
            checker = object : ReleaseCheck {
                override suspend fun check(installedCode: Long, testMode: Boolean): CheckResult =
                    found?.let { CheckResult.Available(it) } ?: CheckResult.UpToDate
            },
            store = MemoryStore(),
            scope = scope,
            clock = System::currentTimeMillis,
        )
        val viewModel = UpdatesViewModel(controller, flow)
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                UpdatesRoute(onNavigateBack = {}, viewModel = viewModel)
                UpdateAutoOpen(navController = rememberNavController(), viewModel = viewModel)
            }
        }
        if (found != null) {
            composeRule.onNodeWithTag("updates-check-now").performClick()
            waitFor("the found release") { hasTag("updates-download") }
        }
    }

    private fun allowInstalls() {
        shell("appops set ${app.packageName} REQUEST_INSTALL_PACKAGES allow")
        waitFor("installs allowed") { app.packageManager.canRequestPackageInstalls() }
    }

    private fun cancelAndroidsInstaller() {
        val installer = By.pkg(INSTALLER)
        check(device.wait(Until.hasObject(installer), WAIT_MILLIS)) {
            "Android's installer did not open; ${flow.state.value}"
        }
        val cancel = device.wait(
            Until.findObject(By.pkg(INSTALLER).res("android:id/button2")),
            5_000,
        )
            ?: device.findObject(By.pkg(INSTALLER).text(Pattern.compile("(?i)cancel")))
        if (cancel != null) cancel.click() else device.pressBack()
        device.wait(Until.gone(installer), WAIT_MILLIS)
    }

    private fun shell(command: String) {
        val output = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private fun hasText(text: String) =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun hasTag(tag: String) =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(what: String, found: () -> Boolean) {
        try {
            composeRule.waitUntil(WAIT_MILLIS) { found() }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("No $what; ${flow.state.value}; asked ${github.asked}", timeout)
        }
    }

    /** GitHub's release download: `github.com` redirects each asset to its download host. */
    private class FakeGitHub : Interceptor {
        val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

        @Volatile
        private var apk: File? = null

        @Volatile
        private var sums = ""

        @Volatile
        private var lastAsset = ""

        fun serve(file: File, sumsOf: File = file) {
            apk = file
            sums = "${sha256(sumsOf)}  ${checkNotNull(ReleaseVersion.parse(TAG)).apkName}\n"
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val path = request.url.encodedPath
            asked += "${request.url.host} $path"
            return when {
                request.url.host == "github.com" && (path == SUMS_PATH || path == APK_PATH) -> {
                    lastAsset = path
                    answer(request, 302, "".toResponseBody())
                        .newBuilder()
                        .header("Location", "https://$ASSETS$ASSET_PATH?sp=r&sig=test")
                        .build()
                }
                request.url.host == ASSETS && path == ASSET_PATH -> answer(request, 200, asset())
                else -> answer(request, 404, "".toResponseBody())
            }
        }

        private fun asset(): ResponseBody {
            val file = apk
            return if (lastAsset == SUMS_PATH || file == null) {
                sums.toResponseBody(TEXT)
            } else {
                file.source().buffer().asResponseBody(BINARY, file.length())
            }
        }

        private fun answer(request: Request, code: Int, body: ResponseBody): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Status $code")
                .body(body)
                .build()
    }

    private class MemoryStore : UpdateStore {
        private var record = UpdateRecord()

        override fun read(): UpdateRecord = record

        override fun write(record: UpdateRecord) {
            this.record = record
        }
    }

    private companion object {
        const val TAG = "v1.0.0-beta.3"
        const val DOWNLOAD = "/Alalkipgen/YFT/releases/download/$TAG"
        const val SUMS_PATH = "$DOWNLOAD/SHA256SUMS"
        const val APK_PATH = "$DOWNLOAD/video-downloader-1.0.0-beta.3.apk"
        const val ASSETS = "release-assets.githubusercontent.com"
        const val ASSET_PATH = "/github-production-release-asset/1/asset"
        const val STATUS_ACTION = "com.alal.yft.update.test.INSTALL_STATUS"
        const val WAIT_MILLIS = 60_000L
        val INSTALLER: Pattern = Pattern.compile("com\\.(google\\.)?android\\.packageinstaller")
        val TEXT = "text/plain".toMediaType()
        val BINARY = "application/octet-stream".toMediaType()

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().toHex()
        }
    }
}
