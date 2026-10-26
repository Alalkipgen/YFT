package com.alal.yft.detection.master

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.social.VkClient
import com.alal.yft.extractor.master.social.VkClientValues
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * VK-Master wave 2 (agent D, item 7): the trial's VK value break. Off, or outside the trial: main's
 * client and values as they are. Each choice breaks one value only — the token request's client
 * id or secret, or `video.get`'s version — and Master's V2 gets the same change of main's values,
 * each of VK's shape. The choice a tap keeps waits for a restart. Made-up values only; no live call.
 */
class VkTrialBreakTest {
    @Test
    fun offAndOutsideTheTrialMainsClientAndValuesAsTheyAre() {
        val http = Recording()
        val off = VkTrialBreak.inMemory()
        assertSame(http, off.client(http))
        assertNull(off.mutate())

        val notTrial = VkTrialBreak.inMemory(VkTrialBreak.Break.CLIENT_ID, available = false)
        assertEquals(VkTrialBreak.Break.OFF, notTrial.active)
        assertSame(http, notTrial.client(http))
        assertNull(notTrial.mutate())
        notTrial.next()
        assertEquals(VkTrialBreak.Row(VkTrialBreak.Break.OFF, VkTrialBreak.Break.OFF), notTrial.row.value)
    }

    @Test
    fun theClientIdBreaksOnlyTheTokenRequestsClientId() = runTest {
        val http = Recording()
        val client = VkTrialBreak.inMemory(VkTrialBreak.Break.CLIENT_ID).client(http)
        client.postJson(TOKEN, BODY)
        client.get(VIDEO_GET)
        client.postJson(OTHER, BODY)
        assertEquals(BODY.replace("\"client_id\":\"100000\"", "\"client_id\":\"999999999\""), http.bodies[0])
        assertEquals(VIDEO_GET, http.urls[1])
        assertEquals("another host's ask as it is", BODY, http.bodies[2])
    }

    @Test
    fun theApiVersionBreaksOnlyVideoGetsVersion() = runTest {
        val http = Recording()
        val client = VkTrialBreak.inMemory(VkTrialBreak.Break.API_VERSION).client(http)
        client.get(VIDEO_GET)
        client.postJson(TOKEN, BODY)
        client.get(PAGE)
        assertEquals(VIDEO_GET.replace("?v=5.000&", "?v=5.0&"), http.urls[0])
        assertEquals(BODY, http.bodies[1])
        assertEquals(PAGE, http.urls[2])
    }

    @Test
    fun theSecretOnlyBreaksOnlyTheTokenRequestsSecret() = runTest {
        val http = Recording()
        val client = VkTrialBreak.inMemory(VkTrialBreak.Break.CLIENT_SECRET).client(http)
        client.postJson(TOKEN, BODY)
        client.get(VIDEO_GET)
        assertEquals(
            BODY.replace("\"client_secret\":\"FixtureSecret01\"", "\"client_secret\":\"YftTrialBrokenSecret\""),
            http.bodies[0],
        )
        assertEquals(VIDEO_GET, http.urls[1])
    }

    @Test
    fun mastersV2GetsTheSameChangeOfMainsValuesInVksShape() = runTest {
        val changes = mapOf(
            VkTrialBreak.Break.CLIENT_ID to { values: VkClient.Values -> values.clientId },
            VkTrialBreak.Break.API_VERSION to { values: VkClient.Values -> values.apiVersion },
            VkTrialBreak.Break.CLIENT_SECRET to { values: VkClient.Values -> values.clientSecret },
        )
        val broken = mapOf(
            VkTrialBreak.Break.CLIENT_ID to VkTrialBreak.BROKEN_CLIENT_ID,
            VkTrialBreak.Break.API_VERSION to VkTrialBreak.BROKEN_API_VERSION,
            VkTrialBreak.Break.CLIENT_SECRET to VkTrialBreak.BROKEN_CLIENT_SECRET,
        )
        for ((choice, field) in changes) {
            // A change of the wrong shape would leave main's values only (one set).
            val sets = VkClientValues.bundled(VkTrialBreak.inMemory(choice).mutate()).values()
            assertEquals(choice.name, 2, sets.size)
            assertEquals(choice.name, VkClient.Source.DEBUG, sets[0].source)
            assertEquals(choice.name, broken.getValue(choice), field(sets[0]))
            assertSame(choice.name, VkClient.BUNDLED, sets[1])
        }
    }

    @Test
    fun aTapKeepsTheNextChoiceForTheNextRun() {
        val vkBreak = VkTrialBreak.inMemory(VkTrialBreak.Break.OFF)
        val seen = (1..4).map { vkBreak.next(); vkBreak.row.value.chosen }
        assertEquals(
            listOf(
                VkTrialBreak.Break.CLIENT_ID,
                VkTrialBreak.Break.API_VERSION,
                VkTrialBreak.Break.CLIENT_SECRET,
                VkTrialBreak.Break.OFF,
            ),
            seen,
        )
        assertEquals("this run's choice stays", VkTrialBreak.Break.OFF, vkBreak.active)
        assertEquals(
            listOf("Off", "Client id", "API version", "Client secret only"),
            VkTrialBreak.Break.entries.map { it.label },
        )
    }

    private class Recording : ExtractorHttpClient {
        val urls = mutableListOf<String>()
        val bodies = mutableListOf<String?>()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            urls += url
            bodies += null
            return ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult {
            urls += url
            bodies += body
            return ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }
    }

    private companion object {
        const val TOKEN = "https://login.vk.ru/?act=get_anonym_token"
        const val OTHER = "https://example.com/token"
        const val PAGE = "https://vkvideo.ru/?v=5.000"
        const val BODY = "{\"client_id\":\"100000\",\"client_secret\":\"FixtureSecret01\"," +
            "\"scopes\":\"video_anonymous\",\"version\":\"1\",\"app_id\":\"200000\"}"
        const val VIDEO_GET = "https://api.vkvideo.ru/method/video.get?v=5.000&client_id=100000" +
            "&owner_id=-100000001&videos=-100000001_456000001&extended=1"
    }
}
