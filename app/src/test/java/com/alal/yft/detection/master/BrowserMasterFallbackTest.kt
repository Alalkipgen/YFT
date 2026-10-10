package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.CapturedRequest
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.android.CodecSteering
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserMasterFallbackTest {
    private val generic = "https://page.test/watch"
    private val tiktok = "https://www.tiktok.com/@fixture/video/11111"
    private val media = "https://cdn.test/main.mp4"

    @Test
    fun codecSteeringFollowsTheDeviceMergeRules() {
        // R5: VP9/Opus WebM merges from Android 10; AV1 merges are off everywhere.
        assertEquals(
            CodecSteering(vp9 = false, av1 = false),
            AndroidBrowserMasterFallback.codecSteering(28),
        )
        assertEquals(
            CodecSteering(vp9 = true, av1 = false),
            AndroidBrowserMasterFallback.codecSteering(29),
        )
        assertFalse(AndroidBrowserMasterFallback.codecSteering(35).av1)
    }

    @Test
    fun successAndOrdinaryGenericPageNeverCallCapture() = runTest {
        val fixture = Fixture(generic)
        val success = SiteAdapterOutcome.Detected("generic", listOf(candidate()))
        assertSame(success, fixture.bridge.recover(generic, success, 100))
        assertSame(
            SiteAdapterOutcome.NotHandled,
            fixture.bridge.recover(generic, SiteAdapterOutcome.NotHandled, 100),
        )
        assertEquals(0, fixture.factories.get())
        assertEquals(0, fixture.captures.get())
    }

    @Test
    fun explicitDisabledMatchDoesNotBecomeAGenericBackup() = runTest {
        val fixture = Fixture(tiktok, enabledSite = false)
        assertSame(
            SiteAdapterOutcome.NotHandled,
            fixture.bridge.recover(tiktok, SiteAdapterOutcome.NotHandled, 100, true),
        )
        assertEquals(0, fixture.factories.get())
    }

    @Test
    fun terminalAccessAndNetworkFailuresNeverSample() = runTest {
        val fixture = Fixture(tiktok)
        for (reason in listOf(
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            SiteExtractionFailure.DRM_PROTECTED,
            SiteExtractionFailure.GEO_RESTRICTED,
            SiteExtractionFailure.NETWORK,
            SiteExtractionFailure.RATE_LIMITED,
        )) {
            val primary = failed(reason)
            assertSame(primary, fixture.bridge.recover(tiktok, primary, 100))
        }
        assertEquals(0, fixture.factories.get())
        assertEquals(0, fixture.captures.get())
    }

    @Test
    fun walledSitesBotLoginAndPlayerFailuresNeverSample() = runTest {
        for (page in listOf(
            "https://www.youtube.com/watch?v=fixture",
            "https://youtu.be/fixture",
            "https://www.reddit.com/r/fixture/comments/abc/clip/",
        )) {
            val fixture = Fixture(page)
            for (reason in listOf(
                SiteExtractionFailure.BOT_CHECK,
                SiteExtractionFailure.LOGIN_REQUIRED,
                SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
            )) {
                val primary = failed(reason)
                assertSame(primary, fixture.bridge.recover(page, primary, 100))
            }
            assertEquals(0, fixture.factories.get())
            assertEquals(0, fixture.captures.get())
            assertEquals(0, fixture.probes.get())
        }
    }

    @Test
    fun recoverableSiteFailureUsesOneCaptureAndKnownFocusedIdentity() = runTest {
        val fixture = Fixture(tiktok)
        val result = fixture.bridge.recover(
            tiktok, failed(SiteExtractionFailure.BOT_CHECK), 100,
        ) as SiteAdapterOutcome.Detected
        assertEquals("tiktok:11111", result.candidates.single().videoId)
        assertEquals(1, fixture.captures.get())
        assertEquals(1, fixture.probes.get())
    }

    @Test
    fun genericBackupOnlyRunsOnDemandAndDoesNotInventAnId() = runTest {
        val fixture = Fixture(generic)
        val result = fixture.bridge.recover(
            generic, SiteAdapterOutcome.NotHandled, 100, true,
        ) as SiteAdapterOutcome.Detected
        assertNull(result.candidates.single().videoId)
        assertEquals(1, fixture.captures.get())
    }

    @Test
    fun pausedBrowserCannotRecoverABotOrLoginFailure() = runTest {
        val fixture = Fixture(tiktok, authorized = false)
        val result = fixture.bridge.recover(
            tiktok, failed(SiteExtractionFailure.BOT_CHECK), 100,
        ) as SiteAdapterOutcome.Failed
        assertTrue(result.message.startsWith("Play the video"))
        assertEquals(0, fixture.probes.get())
    }

    @Test
    fun differentNamedPostAndUnrelatedTabAreNotUsed() = runTest {
        val fixture = Fixture(tiktok)
        val other = tiktok.replace("11111", "22222")
        val primary = failed(SiteExtractionFailure.RESPONSE_CHANGED)
        assertSame(primary, fixture.bridge.recover(other, primary, 100))
        assertSame(primary, fixture.bridge.recover(generic, primary, 100))
        assertEquals(0, fixture.captures.get())
    }

    @Test
    fun deviceMergeGateStillAppliesToMasterResults() = runTest {
        val fixture = Fixture(generic, canMerge = false)
        val result = fixture.bridge.recover(
            generic, SiteAdapterOutcome.NotHandled, 100, true,
        ) as SiteAdapterOutcome.Failed
        assertTrue(result.message.contains("cannot merge"))
    }

    @Test
    fun detachWhileCaptureRunsCannotReturnAStaleSuccess() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val fixture = Fixture(generic, beforeCapture = {
            entered.complete(Unit)
            finish.await()
        })
        val work = async {
            fixture.bridge.recover(generic, SiteAdapterOutcome.NotHandled, 100, true)
        }
        entered.await()
        fixture.capture.session.clear()
        finish.complete(Unit)
        assertSame(SiteAdapterOutcome.NotHandled, work.await())
    }

    @Test
    fun disabledBuildKeepsPrimaryAndNoCaptureFactory() = runTest {
        val fixture = Fixture(tiktok, enabledBuild = false)
        val primary = failed(SiteExtractionFailure.NO_MEDIA_FOUND)
        assertSame(primary, fixture.bridge.recover(tiktok, primary, 100))
        assertEquals(0, fixture.factories.get())
    }

    private fun failed(reason: SiteExtractionFailure) =
        SiteAdapterOutcome.Failed("tiktok", reason, "Fixture failure", true)

    private fun candidate() = MediaCandidate(
        generic, media, setOf(CandidateSource.REQUEST), MediaKind.DIRECT, mimeType = "video/mp4",
    )

    private inner class Fixture(
        page: String,
        enabledSite: Boolean = true,
        enabledBuild: Boolean = true,
        authorized: Boolean = true,
        canMerge: Boolean = true,
        beforeCapture: suspend () -> Unit = {},
    ) {
        val capture = WebViewPlaybackCapture(enabledBuild)
        val factories = AtomicInteger()
        val captures = AtomicInteger()
        val probes = AtomicInteger()
        val sites = SiteAdapterCoordinator(
            SiteExtractorRegistry(listOf(FixtureExtractor()), SiteAdapterFlags { enabledSite }),
            MergeSupport { true },
        )
        val scope = capture.session.navigate(page)!!
        val engine = MasterFallbackEngine(
            validator = MasterMediaValidator { found, _ ->
                probes.incrementAndGet()
                ValidationResult.Valid(found.copy(contentLengthBytes = 4096))
            },
            capture = PlaybackCaptureProvider { request ->
                captures.incrementAndGet()
                beforeCapture()
                CaptureResult.Available(
                    PageSnapshot(
                        request.pageUrl, request.generation,
                        requests = listOf(
                            CapturedRequest(
                                media, mimeType = "video/mp4",
                                context = BrowserRequestContext(page, null, null),
                            ),
                        ),
                        playingMediaUrl = media,
                        authorizedPlayback = authorized,
                    ),
                )
            },
            policy = MasterPolicy(enabled = true),
        )
        val bridge = AndroidBrowserMasterFallback(
            capture, sites, engine, MergeSupport { canMerge },
            requestFactory = { reason, now, expected ->
                factories.incrementAndGet()
                MasterRequest(page, scope.generation, now, reason, expected)
            },
        )
    }

    private class FixtureExtractor : SiteExtractor {
        override val id = "tiktok"
        override val displayName = "Fixture"
        override fun identify(pageUrl: String): SitePageIdentity? {
            if (!pageUrl.startsWith("https://www.tiktok.com/@fixture/video/")) return null
            return SitePageIdentity(id, pageUrl.substringAfterLast('/'), pageUrl)
        }
        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
    }
}
