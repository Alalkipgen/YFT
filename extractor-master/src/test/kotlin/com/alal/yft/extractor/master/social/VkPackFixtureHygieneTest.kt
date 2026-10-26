package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.fixture
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * VK-Master plan §11 (S8), agent A's draft rules (docs/VK_MASTER_CENSUS.md §8), as main's
 * `VkFixtureHygieneTest`: no fixture of the VK pack holds a real-looking signature, an address of
 * the fetcher, a session or a token answer, a real ID or title, or the web client's own values
 * (they live only in [VkClient], with provenance). Every query value of an address is `FIXTURE`;
 * a test that reads an expiry puts its own number in ([vkPack]). Failures name counts only, never
 * the value found. JVM only.
 */
class VkPackFixtureHygieneTest {
    @Test
    fun `every vk-pack fixture is listed here`() {
        val directory = File(requireNotNull(VkPackFixtureHygieneTest::class.java.getResource("/fixtures/vk-pack")).toURI())

        assertEquals(FIXTURES, directory.list().orEmpty().filter { it != "README.md" }.sorted())
    }

    @Test
    fun `no vk-pack fixture holds a signature, an address of the fetcher, a session, a token or a real value`() {
        FIXTURES.forEach { name ->
            val text = fixture("vk-pack/$name")

            assertEquals("$name: leaks", emptyMap<String, Int>(), leaks(text))
        }
    }

    @Test
    fun `the rules catch what they look for`() {
        val leaky = "https:\\/\\/vkvd1.okcdn.ru\\/expires\\/1761800000\\/mp4?srcIp=10.20.30.40" +
            "&sig=Ab3dE5gH7jK9mN1pQ3sT5vX7zB9dF1hJ\\u0026hash=0123456789abcdef0123456789abcdef&s=1 " +
            "remixsid {\"owner_id\":-123456789,\"id\":987654321,\"title\":\"Real title\"} " +
            "{\"key\":\"client_id\",\"value\":\"${VkClient.CLIENT_ID}\"}"

        assertEquals(
            mapOf(
                "IP-like values" to 1,
                "signed query values other than FIXTURE" to 3,
                "signed path values other than FIXTURE" to 1,
                "query values other than FIXTURE" to 4,
                "session or token names" to 1,
                "long hex runs" to 1,
                "key-like runs" to 1,
                "the web client's own values" to 1,
                "IDs other than the made-up ones" to 2,
                "titles not made up" to 1,
            ),
            leaks(leaky),
        )
        assertEquals(
            emptyMap<String, Int>(),
            leaks(
                "{\"owner_id\":-100000001,\"id\":400000001,\"title\":\"Fixture video\"," +
                    "\"url\":\"https://vk1-1.vkuser.net/fixture/720.mp4?expires=FIXTURE&srcAg=FIXTURE\"}",
            ),
        )
    }

    /** Each rule [text] breaks, with how many times (the rule's name only, never the value). */
    private fun leaks(text: String): Map<String, Int> = mapOf(
        "IP-like values" to IPV4.findAll(text).count(),
        "signed query values other than FIXTURE" to SIGNED_QUERY.findAll(text).count { it.groupValues[2] != PLACEHOLDER },
        "signed path values other than FIXTURE" to SIGNED_PATH.findAll(text).count { it.groupValues[2] != PLACEHOLDER },
        "query values other than FIXTURE" to QUERY_VALUE.findAll(text).count { it.groupValues[1] != PLACEHOLDER },
        "session or token names" to SESSION_NAMES.count { text.contains(it, ignoreCase = true) },
        "long hex runs" to HEX_RUN.findAll(text).count(),
        "key-like runs" to TOKEN_RUN.findAll(text).count { it.value.looksLikeKey() },
        "the web client's own values" to CLIENT_VALUES.count { it in text },
        "IDs other than the made-up ones" to ID_FIELD.findAll(text).count { it.groupValues[1] !in MADE_UP_IDS } +
            VIDEO_KEY.findAll(text).count { it.groupValues[1] !in MADE_UP_IDS || it.groupValues[2] !in MADE_UP_IDS },
        "titles not made up" to TITLE.findAll(text).count { !it.groupValues[1].startsWith(PLACEHOLDER, ignoreCase = true) },
    ).filterValues { it > 0 }

    private fun String.looksLikeKey(): Boolean =
        !contains(PLACEHOLDER, ignoreCase = true) && count(Char::isDigit) >= 3 && any(Char::isLowerCase) &&
            any(Char::isUpperCase)

    private companion object {
        const val PLACEHOLDER = "FIXTURE"

        val FIXTURES = listOf(
            "embed_adult.html",
            "embed_files.html",
            "embed_not_found.html",
            "embed_shell.html",
            "token_captcha.json",
            "token_no_value.json",
            "video_get_captcha.json",
            "video_get_deleted.json",
            "video_get_error_13300.json",
            "video_get_error_5.json",
            "video_get_error_8.json",
            "video_get_live.json",
            "video_get_mp4.json",
            "video_get_other_video.json",
            "video_get_renamed.json",
            "video_get_unknown_host.json",
        )

        /** Main's rules (`VkFixtureHygieneTest`). */
        val IPV4 = Regex("(?<![0-9.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9.])")
        val SIGNED_QUERY = Regex(
            "(?:[?&;]|\\\\u0026)(sig|srcIp|srcAg|expires|hash|user_id|token|access_token)=" +
                "([^&\"'\\s\\\\<]*)",
        )
        val SIGNED_PATH = Regex("(?:/|\\\\/)(sig|srcIp|srcAg|expires)(?:/|\\\\/)([^/\"\\s\\\\]+)")
        val SESSION_NAMES = listOf("remixsid", "remixstid", "access_token", "Set-Cookie")
        val HEX_RUN = Regex("(?i)(?<![0-9a-z])[0-9a-f]{32,}")
        val TOKEN_RUN = Regex("[A-Za-z0-9+/_-]{32,}")

        /** A's: every query value of an address is FIXTURE. */
        val QUERY_VALUE = Regex("(?:[?&;]|\\\\u0026)[A-Za-z_][A-Za-z0-9_]*=([^&\"'\\s\\\\<]*)")

        /** A's: the web client's own values live only in [VkClient]. */
        val CLIENT_VALUES = listOf(VkClient.CLIENT_ID, VkClient.CLIENT_SECRET, VkClient.APP_ID, VkClient.API_VERSION)

        /** A's: owner `-100000001`, video `400000001` (and the other video's), made up. */
        val MADE_UP_IDS = setOf("-100000001", "-100000002", "400000001", "400000002")
        val ID_FIELD = Regex("\"(?:owner_id|id)\"\\s*:\\s*(-?[0-9]+)")
        val VIDEO_KEY = Regex("(-?[0-9]{6,})_([0-9]{6,})")
        val TITLE = Regex("\"title\"\\s*:\\s*\"([^\"]*)\"")
    }
}
