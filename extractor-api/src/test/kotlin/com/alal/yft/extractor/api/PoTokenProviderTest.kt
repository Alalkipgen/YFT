package com.alal.yft.extractor.api

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PoTokenProviderTest {
    @Test
    fun `a request must be bound to something and name HTTPS addresses`() {
        assertThrows(IllegalArgumentException::class.java) {
            PoTokenRequest(" ", SCRIPT, PAGE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PoTokenRequest("Yft0Fixture", "http://player.example.test/base.js", PAGE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PoTokenRequest("Yft0Fixture", SCRIPT, "http://page.example.test/watch")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PoTokenResult.Minted(" ")
        }
    }

    @Test
    fun `the binding and the token never print`() {
        val request = PoTokenRequest("secret-binding", SCRIPT, PAGE)
        val minted = PoTokenResult.Minted("secret-token-value")

        assertFalse(request.toString().contains("secret"))
        assertTrue(request.toString().contains("<14 chars>"))
        assertFalse(minted.toString().contains("secret"))
        assertTrue(minted.toString().contains("<18 chars>"))
    }

    @Test
    fun `the default provider reports that no host is wired`() = runTest {
        assertFalse(NoPoTokenProvider.isAvailable)
        assertEquals(
            PoTokenResult.Unavailable,
            NoPoTokenProvider.mint(PoTokenRequest("Yft0Fixture", SCRIPT, PAGE)),
        )
    }

    private companion object {
        const val SCRIPT = "https://player.example.test/s/player/f1x7ure0/base.js"
        const val PAGE = "https://page.example.test/watch?v=Yft0Fixture"
    }
}
