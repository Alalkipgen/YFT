package com.alal.yft.detection.master

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.master.solver.OwnSolverBreaker
import com.alal.yft.extractor.master.solver.PendingCheck
import com.alal.yft.extractor.master.solver.SignatureVerdicts
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Decision 30 (i): Settings' "Backup finder", on by default; off, no lookup asks Master and
 * nothing goes to GitHub raw (each of Master's own requests fails before it is sent).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupFinderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theSwitchIsOnByDefaultAndKeepsTheUsersChoice() {
        val preferences = context.getSharedPreferences("backup-finder-test", Context.MODE_PRIVATE)
        val first = BackupFinder(preferences, available = true)
        assertTrue(first.isOn())
        assertTrue(first.on.value)

        first.set(false)

        assertFalse(first.isOn())
        assertFalse(first.on.value)
        assertFalse(BackupFinder(preferences, available = true).isOn())
    }

    @Test
    fun offTheBrowsersBackupAnswersWithMainsOwnOutcome() = runTest {
        val finder = BackupFinder.inMemory(on = true)
        val sites = SiteAdapterCoordinator(SiteExtractorRegistry(emptyList()))
        val fallback = AndroidBrowserMasterFallback.create(
            enabled = true,
            client = OkHttpClient(),
            sites = sites,
            youtubeRouteB = false,
            routeBTrial = false,
            socialLadder = false,
            socialTrial = false,
            on = finder::isOn,
        )
        assertTrue(fallback.enabled)

        finder.set(false)

        assertFalse(fallback.enabled)
        val primary = SiteAdapterOutcome.Failed(
            adapterId = "tiktok",
            reason = SiteExtractionFailure.NO_MEDIA_FOUND,
            message = "No video was found.",
            allowsGenericFallback = false,
        )
        assertSame(primary, fallback.recover(PAGE, primary, NOW, genericOnDemand = true))
        assertSame(
            SiteAdapterOutcome.NotHandled,
            fallback.recover(PAGE, SiteAdapterOutcome.NotHandled, NOW, genericOnDemand = true),
        )
        assertNull(fallback.mainAndMore(listOf(row())))

        finder.set(true)
        assertTrue(fallback.enabled)
    }

    @Test
    fun offEveryOneOfMastersRequestsFailsBeforeItIsSent() {
        val server = MockWebServer()
        server.start()
        try {
            var on = false
            val client = BackupFinder.gate(OkHttpClient()) { on }
            val request = Request.Builder().url(server.url("/recipes.json")).build()
            try {
                client.newCall(request).execute().close()
                fail("a request went out while the Backup finder was off")
            } catch (error: IOException) {
                assertEquals(BackupFinder.OFF, error.message)
            }
            assertEquals(0, server.requestCount)

            on = true
            server.enqueue(MockResponse().setBody("{}"))
            client.newCall(request).execute().use { assertEquals(200, it.code) }
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun offYouTubesReaderRunsOnMainsSolverAndTheOwnSolverIsNeverMade() = runTest {
        var on = false
        val made = AtomicInteger()
        val own = FakeOwnSolver()
        val main = FakeRunner("main")
        val scripts = BackupFinderPlayerScripts(
            on = { on },
            own = { made.incrementAndGet(); own },
            main = { main },
        )

        assertEquals(PlayerScriptResult.Success(mapOf("by" to "main")), scripts.resolve(REQUEST))
        assertTrue(scripts.isAvailable)
        assertNull(scripts.pendingCheck(REQUEST))
        scripts.record(CHECK, SignatureVerdicts.Verdict.PASS)
        assertEquals(0, made.get())
        assertEquals(0, own.recorded)

        on = true
        assertEquals(PlayerScriptResult.Success(mapOf("by" to "own")), scripts.resolve(REQUEST))
        assertEquals(1, made.get())
        assertEquals(CHECK, scripts.pendingCheck(REQUEST))
        scripts.record(CHECK, SignatureVerdicts.Verdict.PASS)
        assertEquals(1, own.recorded)

        // Off again: main answers; the own solver's verdicts stay its own.
        on = false
        assertEquals(PlayerScriptResult.Success(mapOf("by" to "main")), scripts.resolve(REQUEST))
        assertEquals(1, made.get())
    }

    private open class FakeRunner(private val name: String) : PlayerScriptRunner {
        override val isAvailable = true

        override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult =
            PlayerScriptResult.Success(mapOf("by" to name))
    }

    private class FakeOwnSolver : FakeRunner("own"), SignatureVerdicts {
        var recorded = 0

        override fun pendingCheck(request: PlayerScriptRequest): PendingCheck = CHECK

        override fun record(check: PendingCheck, verdict: SignatureVerdicts.Verdict) {
            recorded++
        }
    }

    private fun row() = MediaCandidate(
        PAGE,
        "https://cdn.test/v/file.mp4",
        setOf(CandidateSource.REQUEST),
        MediaKind.DIRECT,
        mimeType = "video/mp4",
    )

    private companion object {
        const val PAGE = "https://www.tiktok.com/@fixture/video/7300000000000000001"
        const val NOW = 1_800_000_000_000L
        val REQUEST = PlayerScriptRequest(
            playerScriptUrl = "https://www.youtube.com/s/player/abc/base.js",
            pageUrl = "https://www.youtube.com/watch?v=fixture0001",
            challenges = listOf(
                PlayerScriptChallenge("sig", PlayerScriptChallengeKind.SIGNATURE, "fixture-input"),
            ),
        )
        val CHECK = PendingCheck(playerId = "abc", step = OwnSolverBreaker.Step.entries.first())
    }
}
