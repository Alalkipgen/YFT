package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.contract.CountingCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.fixture
import com.alal.yft.extractor.master.layers.ContractLayer
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.recipes.ContractRecipes
import com.alal.yft.extractor.sites.vk.VkExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VK-Master plan §4 (V1, V2), Appendix B steps 4–5: VK's recipe ([VkRecipe]) in Master's engine,
 * with the engine's recipes and VK's (agent D adds it to `ContractRecipes.ALL`). Its endpoint
 * keeps a placeholder the contract step never fills, so R8 asks VK nothing on its own — also with
 * VK's ladder outside its lookup's record: VK's asks are main's and V2's ([VkLadderTest]). Its
 * reading of the video's answer [VkReader] writes: the video's files in VK's order with their
 * kinds and sizes, the title, the length, the picture and the expiry; an answer naming another
 * video gives no row. JVM only, no network.
 */
class VkRecipeTest {
    @Test
    fun `VK's recipe asks VK nothing on its own - no endpoint for this video, then the shared capture`() = runTest {
        val request = contractRequest(PAGE, identify(PAGE), SiteExtractionFailure.NO_MEDIA_FOUND, COOKIE)
        val asked = FakeExtractorHttpClient()
        val master = FakeExtractorHttpClient()
        // Without VK's ladder, and with it outside a lookup's record (it claims nothing there).
        listOf(null, VkLadder(master, ::contracts)).forEach { social ->
            val validator = RecordingValidator()
            val capture = CountingCapture()
            val engine = MasterFallbackEngine(
                validator,
                capture,
                MasterPolicy(enabled = true),
                contracts = contracts(asked),
                social = social,
            )
            val result = engine.extract(request)

            assertTrue(result.toString(), result is MasterResult.NeedsPlayback)
            assertEquals(
                listOf(
                    "master: vk contract: no endpoint for this video",
                    "master: one browser-assisted capture requested",
                    "Open the page, play its video, and supply a fresh capture.",
                ),
                (result as MasterResult.NeedsPlayback).details,
            )
            assertEquals(1, capture.calls)
            assertTrue(validator.seen.isEmpty())
        }
        listOf(asked, master).forEach { http ->
            assertTrue(http.requestedUrls.toString(), http.requestedUrls.isEmpty())
            assertTrue(http.postedUrls.isEmpty())
            assertTrue(http.probedUrls.isEmpty())
        }
    }

    @Test
    fun `VK's recipe reads the video's own answer only - its files with their kinds and sizes, another video's answer none`() {
        val answer = checkNotNull(VkReader.api(fixture("vk-pack/video_get_mp4.json"), LINK).answer(LINK, API))
        val layer = ContractLayer(contracts(FakeExtractorHttpClient())::recipeOf)
        val request = contractRequest(PAGE, identify(PAGE), SiteExtractionFailure.NO_MEDIA_FOUND)

        val read = layer.collect(request, PageSnapshot(PAGE, request.generation, contract = answer))
        assertEquals(listOf("master: vk contract: 4 files from the key table"), read.details)
        val rows = read.candidates
        assertEquals(
            listOf("$FILES/720.mp4", "$FILES/360.mp4", "$FILES/video.m3u8", "$FILES/video.mpd"),
            rows.map { it.mediaUrl.substringBefore('?') },
        )
        assertEquals(listOf(MediaKind.DIRECT, MediaKind.DIRECT, MediaKind.HLS, MediaKind.DASH), rows.map { it.kind })
        assertEquals(listOf(720, 360, null, null), rows.map { it.height })
        rows.forEach { row ->
            assertEquals(KEY, row.videoId)
            assertEquals("Fixture video & its title", row.title)
            assertEquals(61_000L, row.durationMillis)
            assertEquals("https://iv.okcdn.ru/fixture/large.jpg", row.thumbnailUrl)
            assertEquals(1_900_000_000_000L, row.expiresAtEpochMs)
        }

        val other = contractRequest(OTHER_PAGE, identify(OTHER_PAGE), SiteExtractionFailure.NO_MEDIA_FOUND)
        val none = layer.collect(other, PageSnapshot(OTHER_PAGE, other.generation, contract = answer))
        assertEquals(listOf("master: vk contract: the answer names another video"), none.details)
        assertTrue(none.candidates.isEmpty())
        assertNull(none.terminalFailure)
    }

    private companion object {
        const val PAGE = "https://vk.com/video-100000001_400000001"
        const val OTHER_PAGE = "https://vk.com/video-100000001_400000002"
        const val KEY = "vk:-100000001_400000001"
        const val COOKIE = "remixsid=FIXTURE"
        const val FILES = "https://vk1-1.vkuser.net/fixture"
        const val API = "https://api.vkvideo.ru/method/video.get?v=5.289&owner_id=-100000001" +
            "&videos=-100000001_400000001&extended=1"
        val LINK = VkClient.Link(-100000001, 400000001)

        /** The engine's recipes with VK's (step 4's [VkRecipe]). */
        fun contracts(http: ExtractorHttpClient) = SiteContracts(http, ContractRecipes.ALL + VkRecipe.RECIPE)

        fun identify(url: String): SitePageIdentity = checkNotNull(VkExtractor(FakeExtractorHttpClient()).identify(url))
    }
}
