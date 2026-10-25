package com.alal.yft.update

import android.content.Intent
import android.content.pm.PackageInstaller
import com.alal.yft.update.Installs.OTHER_KEY
import com.alal.yft.update.Updates.found
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The in-app update's steps and their order (§4.4) on fakes of the network and of Android. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateInstallFlowTest {
    private val source = FakeApkSource()
    private val reader = FakeApkReader()
    private val system = FakeInstallSystem()
    private val update = found("v1.0.1")
    private val confirm = Intent(ACTION_CONFIRM)

    private fun TestScope.flow(environment: UpdateEnvironment = Updates.release()) =
        UpdateInstallFlow(
            environment = environment,
            source = source,
            reader = reader,
            system = system,
            scope = backgroundScope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        ).also {
            // The start's cleanup is its own case.
            source.events.clear()
            system.events.clear()
        }

    private val UpdateInstallFlow.now: InstallState get() = state.value

    @Test
    fun aStartRemovesWhatAnEarlierRunLeft() = runTest(UnconfinedTestDispatcher()) {
        UpdateInstallFlow(
            environment = Updates.release(),
            source = source,
            reader = reader,
            system = system,
            scope = backgroundScope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        assertEquals(listOf("discard"), source.events)
        assertEquals(listOf("abandonAll"), system.events)
    }

    @Test
    fun theChecksRunInTheOwnersOrderThenAndroidsInstaller() = runTest(UnconfinedTestDispatcher()) {
        val flow = flow()

        flow.download(update)

        assertEquals(
            listOf("checksum video-downloader-1.0.1.apk", "download ${Installs.SHA256}", "discard"),
            source.events,
        )
        assertEquals(listOf("yft-update.apk"), reader.read)
        assertEquals(listOf("create", "commit 7 yft-update.apk"), system.events)
        assertEquals(InstallState.Installing(test = false), flow.now)

        flow.onStatus(7, PackageInstaller.STATUS_PENDING_USER_ACTION, confirm)
        val waiting = flow.now as InstallState.Installing
        assertEquals(7, waiting.confirm?.sessionId)
        assertSame(confirm, waiting.confirm?.intent)

        flow.confirmStarted(7)
        assertEquals(InstallState.Installing(test = false), flow.now)

        flow.onStatus(7, PackageInstaller.STATUS_FAILURE_ABORTED, null)
        assertEquals(InstallState.Cancelled(test = false), flow.now)
    }

    @Test
    fun notAllowedDownloadsNothing() = runTest(UnconfinedTestDispatcher()) {
        system.allowed = false
        val flow = flow()

        flow.download(update)

        assertEquals(InstallState.NotAllowed(test = false), flow.now)
        assertEquals(emptyList<String>(), source.events)
        assertEquals(emptyList<String>(), system.events)

        system.allowed = true
        flow.download(update)
        assertEquals(InstallState.Installing(test = false), flow.now)
    }

    @Test
    fun notEnoughSpaceStopsBeforeTheChecksum() = runTest(UnconfinedTestDispatcher()) {
        system.free = 2 * update.apkSize + UpdateInstallFlow.SPARE_BYTES - 1
        val flow = flow()

        flow.download(update)

        assertEquals(InstallState.Failed(false, InstallFailure.NoSpace), flow.now)
        assertEquals(emptyList<String>(), source.events)

        system.free = 2 * update.apkSize + UpdateInstallFlow.SPARE_BYTES
        flow.download(update)
        assertEquals(InstallState.Installing(test = false), flow.now)
    }

    @Test
    fun noChecksumNoDownload() = runTest(UnconfinedTestDispatcher()) {
        val flow = flow()
        val answers = mapOf(
            SumsResult.Missing to InstallFailure.NoChecksum,
            SumsResult.Offline to InstallFailure.DownloadStopped,
            SumsResult.Failed to InstallFailure.DownloadFailed,
        )

        for ((sums, failure) in answers) {
            source.events.clear()
            source.sums = sums
            flow.download(update)

            assertEquals(InstallState.Failed(false, failure), flow.now)
            assertEquals(listOf("checksum video-downloader-1.0.1.apk"), source.events)
        }
        assertEquals(emptyList<String>(), reader.read)
        assertEquals(emptyList<String>(), system.events)
    }

    @Test
    fun aBadChecksumStopsBeforeTheApkIsRead() = runTest(UnconfinedTestDispatcher()) {
        val flow = flow()
        val answers = mapOf(
            DownloadResult.Damaged to InstallFailure.ChecksumMismatch,
            DownloadResult.Offline to InstallFailure.DownloadStopped,
            DownloadResult.Failed to InstallFailure.DownloadFailed,
        )

        for ((download, failure) in answers) {
            source.download = download
            flow.download(update)
            assertEquals(InstallState.Failed(false, failure), flow.now)
        }
        assertEquals(emptyList<String>(), reader.read)
        assertEquals(emptyList<String>(), system.events)
    }

    @Test
    fun aBadSignatureStopsBeforeThePackageCheckAndAnyFailureBeforeASession() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = flow()
            val apks = mapOf(
                // Another app with another key: the signature is the check that fails.
                ApkFacts("com.example.other", 1_000_100, setOf(OTHER_KEY)) to
                    ApkVerdict.SignatureMismatch,
                null to ApkVerdict.SignatureMismatch,
                Installs.releaseApk(packageName = "com.example.other") to ApkVerdict.OtherApp,
                Installs.releaseApk(code = 1_000_200) to ApkVerdict.NotThatVersion("v1.0.1"),
            )

            for ((facts, verdict) in apks) {
                source.events.clear()
                reader.facts = facts
                flow.download(update)

                assertEquals(InstallState.Failed(false, InstallFailure.Check(verdict)), flow.now)
                // The file goes at once.
                assertEquals("discard", source.events.last())
            }
            assertEquals(emptyList<String>(), system.events)
        }

    @Test
    fun theProgressShowsAndCancelStopsTheDownload() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val flow = flow()

        flow.download(update)
        assertEquals(InstallState.Downloading(percent = 42), flow.now)

        // One at a time: a second tap starts nothing.
        flow.download(update)
        assertEquals(1, source.events.count { it.startsWith("checksum") })

        flow.cancel()
        assertEquals(InstallState.Idle, flow.now)
        gate.complete(Unit)
        assertEquals(InstallState.Idle, flow.now)
        assertEquals(emptyList<String>(), reader.read)
        assertEquals(emptyList<String>(), system.events)
    }

    @Test
    fun cancelWhileAndroidWaitsGivesTheSessionUp() = runTest(UnconfinedTestDispatcher()) {
        val flow = flow()
        flow.download(update)
        flow.onStatus(7, PackageInstaller.STATUS_PENDING_USER_ACTION, confirm)

        flow.cancel()

        assertEquals(InstallState.Cancelled(test = false), flow.now)
        assertEquals("abandon 7", system.events.last())
        // Android's answer to the given-up session changes nothing.
        flow.onStatus(7, PackageInstaller.STATUS_FAILURE_ABORTED, null)
        assertEquals(InstallState.Cancelled(test = false), flow.now)
    }

    @Test
    fun androidsAnswers() = runTest(UnconfinedTestDispatcher()) {
        val flow = flow()
        val answers = mapOf(
            PackageInstaller.STATUS_SUCCESS to InstallState.Idle,
            PackageInstaller.STATUS_FAILURE_ABORTED to InstallState.Cancelled(false),
            PackageInstaller.STATUS_FAILURE_CONFLICT to
                InstallState.Failed(false, InstallFailure.Android(5)),
            PackageInstaller.STATUS_FAILURE_STORAGE to
                InstallState.Failed(false, InstallFailure.Android(6)),
        )

        for ((status, state) in answers) {
            flow.download(update)
            flow.onStatus(7, status, null)
            assertEquals(state, flow.now)
        }

        // A confirmation without its intent can't be shown: the session is given up.
        flow.download(update)
        system.events.clear()
        flow.onStatus(7, PackageInstaller.STATUS_PENDING_USER_ACTION, null)
        assertEquals(InstallState.Failed(false, InstallFailure.Android(-1)), flow.now)
        assertEquals(listOf("abandon 7"), system.events)
    }

    @Test
    fun aSessionFromAnEarlierRunIsGivenUp() = runTest(UnconfinedTestDispatcher()) {
        val flow = flow()

        flow.onStatus(99, PackageInstaller.STATUS_PENDING_USER_ACTION, confirm)

        assertEquals(InstallState.Idle, flow.now)
        assertEquals(listOf("abandon 99"), system.events)
    }

    @Test
    fun aSessionThatCannotBeWrittenFails() = runTest(UnconfinedTestDispatcher()) {
        system.commitFailure = IOException("no space")
        val flow = flow()

        flow.download(update)

        assertEquals(
            InstallState.Failed(false, InstallFailure.Android(PackageInstaller.STATUS_FAILURE)),
            flow.now,
        )
        assertEquals(listOf("create", "abandon 7"), system.events)
        assertEquals("discard", source.events.last())
    }

    @Test
    fun theConfirmationThatCannotOpenGivesTheSessionUp() = runTest(UnconfinedTestDispatcher()) {
        val flow = flow()
        flow.download(update)
        flow.onStatus(7, PackageInstaller.STATUS_PENDING_USER_ACTION, confirm)

        flow.confirmFailed(7)

        assertEquals(InstallState.Failed(false, InstallFailure.NoInstaller), flow.now)
        assertEquals("abandon 7", system.events.last())
    }

    @Test
    fun reinstallThisBuildOnlyInTestModeWithItsOwnApk() = runTest(UnconfinedTestDispatcher()) {
        system.installed = Installs.debug(code = 3)
        reader.facts = ApkFacts("com.alal.yft.debug", 3, setOf(OTHER_KEY))
        val release = flow(Updates.release())

        release.reinstallThisBuild()
        assertEquals(InstallState.Idle, release.now)
        assertEquals(emptyList<String>(), system.events)

        val test = flow(Updates.test(code = 3))
        test.reinstallThisBuild()

        assertEquals(InstallState.Installing(test = true), test.now)
        assertEquals(listOf("base.apk"), reader.read)
        assertEquals(listOf("create", "commit 7 base.apk"), system.events)
        // No release, no checksum; its own APK is never deleted.
        assertEquals(emptyList<String>(), source.events)

        test.onStatus(7, PackageInstaller.STATUS_FAILURE_ABORTED, null)
        assertEquals(InstallState.Cancelled(test = true), test.now)
    }

    @Test
    fun reinstallThisBuildRefusesAnotherCode() = runTest(UnconfinedTestDispatcher()) {
        system.installed = Installs.debug(code = 3)
        reader.facts = ApkFacts("com.alal.yft.debug", 4, setOf(OTHER_KEY))
        val flow = flow(Updates.test(code = 3))

        flow.reinstallThisBuild()

        assertEquals(
            InstallState.Failed(
                true,
                InstallFailure.Check(ApkVerdict.NotThatVersion("v1.0.0-beta.3-debug")),
            ),
            flow.now,
        )
        assertEquals(emptyList<String>(), system.events)
    }

    @Test
    fun aBuildThatNeverChecksNeverInstalls() = runTest(UnconfinedTestDispatcher()) {
        val flow = UpdateInstallFlow(
            environment = Updates.off(),
            source = source,
            reader = reader,
            system = system,
            scope = backgroundScope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        flow.download(update)
        flow.reinstallThisBuild()

        assertEquals(InstallState.Idle, flow.now)
        assertTrue(source.events.isEmpty() && system.events.isEmpty())
        assertNull(reader.read.firstOrNull())
    }

    private companion object {
        const val ACTION_CONFIRM = "android.content.pm.action.CONFIRM_INSTALL"
    }
}
