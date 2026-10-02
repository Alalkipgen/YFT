package com.alal.yft.extractor.api

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScriptRunnerTest {
    @Test
    fun `a request must name HTTPS addresses and ask something once per key`() {
        val challenge = PlayerScriptChallenge("n-18", PlayerScriptChallengeKind.RATE_PARAM, "abc")

        assertThrows(IllegalArgumentException::class.java) {
            PlayerScriptRequest("http://player.example.test/base.js", PAGE, listOf(challenge))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlayerScriptRequest(SCRIPT, "http://page.example.test/watch", listOf(challenge))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlayerScriptRequest(SCRIPT, PAGE, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlayerScriptRequest(SCRIPT, PAGE, listOf(challenge, challenge.copy(input = "def")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlayerScriptChallenge("n-18", PlayerScriptChallengeKind.RATE_PARAM, " ")
        }
    }

    @Test
    fun `challenge inputs and resolved values never print`() {
        val challenge = PlayerScriptChallenge(
            key = "sig-18",
            kind = PlayerScriptChallengeKind.SIGNATURE,
            input = "secret-signature-input",
        )
        val request = PlayerScriptRequest(SCRIPT, PAGE, listOf(challenge))
        val result = PlayerScriptResult.Success(mapOf("sig-18" to "secret-output"))

        assertFalse(challenge.toString().contains("secret"))
        assertTrue(challenge.toString().contains("<22 chars>"))
        assertFalse(request.toString().contains("secret"))
        assertFalse(result.toString().contains("secret"))
        assertTrue(result.toString().contains("sig-18"))
    }

    @Test
    fun `the default runner reports that no host is wired`() = runTest {
        val request = PlayerScriptRequest(
            SCRIPT,
            PAGE,
            listOf(PlayerScriptChallenge("n-18", PlayerScriptChallengeKind.RATE_PARAM, "abc")),
        )

        assertFalse(NoPlayerScriptRunner.isAvailable)
        assertEquals(PlayerScriptResult.Unavailable, NoPlayerScriptRunner.resolve(request))
    }

    private companion object {
        const val SCRIPT = "https://player.example.test/s/player/fixture/base.js"
        const val PAGE = "https://page.example.test/watch?v=fixture"
    }
}
