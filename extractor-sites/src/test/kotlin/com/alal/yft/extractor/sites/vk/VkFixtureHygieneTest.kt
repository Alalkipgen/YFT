package com.alal.yft.extractor.sites.vk

import com.alal.yft.extractor.sites.testing.Fixtures
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * VK_PLAN §5.5: no fixture holds a real-looking signature, the fetcher's address or a session.
 * Failures name counts only, never the value found.
 */
class VkFixtureHygieneTest {
    @Test
    fun `every vk fixture is listed here`() {
        val directory =
            File(requireNotNull(Fixtures::class.java.getResource("/fixtures/vk")).toURI())

        assertEquals(FIXTURES, directory.list().orEmpty().filter { it != "README.md" }.sorted())
    }

    @Test
    fun `no vk fixture holds a signature, an address of the fetcher or a session`() {
        FIXTURES.forEach { name ->
            val text = Fixtures.read("vk/$name")

            assertEquals("$name: IP-like values", 0, IPV4.findAll(text).count())
            assertEquals(
                "$name: signed query values other than FIXTURE",
                0,
                SIGNED_QUERY.findAll(text).count { it.groupValues[2] != PLACEHOLDER },
            )
            assertEquals(
                "$name: signed path values other than FIXTURE",
                0,
                SIGNED_PATH.findAll(text).count { it.groupValues[2] != PLACEHOLDER },
            )
            assertEquals(
                "$name: session names",
                0,
                SESSION_NAMES.count { text.contains(it, ignoreCase = true) },
            )
            assertEquals("$name: long hex runs", 0, HEX_RUN.findAll(text).count())
            assertEquals(
                "$name: key-like runs",
                0,
                TOKEN_RUN.findAll(text).count { it.value.looksLikeKey() },
            )
        }
    }

    @Test
    fun `the rules catch what they look for`() {
        val leaky = "https:\\/\\/vkvd1.okcdn.ru\\/expires\\/1761800000\\/mp4?srcIp=10.20.30.40" +
            "&sig=Ab3dE5gH7jK9mN1pQ3sT5vX7zB9dF1hJ\\u0026hash=0123456789abcdef0123456789abcdef"

        assertEquals(1, IPV4.findAll(leaky).count())
        assertEquals(3, SIGNED_QUERY.findAll(leaky).count { it.groupValues[2] != PLACEHOLDER })
        assertEquals(1, SIGNED_PATH.findAll(leaky).count { it.groupValues[2] != PLACEHOLDER })
        assertEquals(1, HEX_RUN.findAll(leaky).count())
        assertEquals(1, TOKEN_RUN.findAll(leaky).count { it.value.looksLikeKey() })
        assertEquals(0, SIGNED_QUERY.findAll("?sig=FIXTURE&expires=FIXTURE").count {
            it.groupValues[2] != PLACEHOLDER
        })
    }

    private fun String.looksLikeKey(): Boolean =
        PLACEHOLDER !in this && count(Char::isDigit) >= 3 && any(Char::isLowerCase) &&
            any(Char::isUpperCase)

    private companion object {
        const val PLACEHOLDER = "FIXTURE"

        val FIXTURES = listOf(
            "api-error-auth.json",
            "api-video-get-adult.json",
            "api-video-get-deleted.json",
            "dash-sep-video.mpd",
            "embed-adult-content.html",
            "embed-clip-public.html",
            "embed-not-found.html",
            "embed-postlive.html",
            "embed-spa-shell-vkvideo.html",
            "embed-video-public.html",
            "hls-audio-rendition.m3u8",
            "hls-fmp4-master-video.m3u8",
            "hls-master-video.m3u8",
            "page-vkvideo-desktop.html",
        )

        val IPV4 = Regex("(?<![0-9.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9.])")
        val SIGNED_QUERY = Regex(
            "(?:[?&;]|\\\\u0026)(sig|srcIp|srcAg|expires|hash|user_id|token|access_token)=" +
                "([^&\"'\\s\\\\<]*)",
        )
        val SIGNED_PATH = Regex("(?:/|\\\\/)(sig|srcIp|srcAg|expires)(?:/|\\\\/)([^/\"\\s\\\\]+)")
        val SESSION_NAMES = listOf("remixsid", "remixstid", "access_token", "Set-Cookie")
        val HEX_RUN = Regex("(?i)(?<![0-9a-z])[0-9a-f]{32,}")
        val TOKEN_RUN = Regex("[A-Za-z0-9+/_-]{32,}")
    }
}
