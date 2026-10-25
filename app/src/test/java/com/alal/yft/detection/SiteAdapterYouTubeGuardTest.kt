package com.alal.yft.detection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.extractor.api.NoPoTokenProvider
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.master.solver.PendingCheck
import com.alal.yft.extractor.master.solver.SignatureVerdicts
import com.alal.yft.extractor.sites.youtube.YouTubeExtractor
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 1.2 N5b A.8: the app's YouTube reader as [SiteAdapterModule] builds it. A runner that
 * hands over V checks (the own solver's, ownSolver with or without ownSolverNext) gets the reader
 * behind `YouTubeSignatureGuard`; main's runner gets main's reader as is. What V does there is
 * `YouTubeSignatureGuardTest`'s (extractor-master).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SiteAdapterYouTubeGuardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the own solver's runner gets the YouTube reader behind the V guard`() {
        val youtube = youtubeOf(OwnRunner())

        assertFalse(youtube is YouTubeExtractor)
        assertNotNull(youtube.identify(WATCH))
    }

    @Test
    fun `main's runner gets main's YouTube reader as is`() {
        assertTrue(youtubeOf(MainRunner()) is YouTubeExtractor)
    }

    private fun youtubeOf(runner: PlayerScriptRunner): SiteExtractor {
        val client = OkHttpClient()
        val extractors = SiteAdapterModule.provideSiteExtractors(
            context = context,
            client = client,
            http = SiteAdapterModule.provideExtractorHttpClient(client),
            playerScripts = runner,
            poTokens = NoPoTokenProvider,
        )
        return extractors.single { it.identify(WATCH) != null }
    }

    /** Main's ejs runner as far as the wiring sees it: no [SignatureVerdicts]. */
    private open class MainRunner : PlayerScriptRunner {
        override val isAvailable: Boolean = true

        override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult =
            PlayerScriptResult.Unavailable
    }

    /** The own solver's runner as far as the wiring sees it: it hands over V checks. */
    private class OwnRunner : MainRunner(), SignatureVerdicts {
        override fun pendingCheck(request: PlayerScriptRequest): PendingCheck? = null

        override fun record(check: PendingCheck, verdict: SignatureVerdicts.Verdict) = Unit
    }

    private companion object {
        const val WATCH = "https://www.youtube.com/watch?v=Yft0Fixture"
    }
}
