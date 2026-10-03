package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PageNavigationHeadersTest {
    @Test
    fun defaultsAreNavigationOnlyAndNeverSupplyIdentityOrCookies() {
        val headers = PageNavigationHeaders.withDefaults(emptyMap())

        assertEquals(PageNavigationHeaders.ACCEPT, headers["Accept"])
        assertEquals("en-US,en;q=0.9", headers["Accept-Language"])
        assertEquals("navigate", headers["Sec-Fetch-Mode"])
        assertEquals(3, headers.size)
        assertFalse(headers.containsKey("Cookie"))
        assertFalse(headers.containsKey("User-Agent"))
    }

    @Test
    fun explicitValuesAndCasingAreRetainedWithoutDuplicateNames() {
        val explicit = mapOf(
            "accept" to "text/html",
            "ACCEPT-LANGUAGE" to "fr-FR",
            "sec-fetch-mode" to "navigate",
            "User-Agent" to "browser-fixture",
        )

        assertEquals(explicit, PageNavigationHeaders.withDefaults(explicit))
    }

    @Test
    fun defaultsDoNotMutateOrRetainTheCallersMutableMap() {
        val input = mutableMapOf("Accept-Language" to "fr-FR")
        val headers = PageNavigationHeaders.withDefaults(input)
        input["Accept-Language"] = "de-DE"

        assertEquals(1, input.size)
        assertEquals("fr-FR", headers["Accept-Language"])
        assertEquals(3, headers.size)
    }
}
