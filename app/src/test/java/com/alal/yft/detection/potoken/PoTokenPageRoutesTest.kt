package com.alal.yft.detection.potoken

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoTokenPageRoutesTest {
    @Test
    fun `the page, its script and the challenge are served`() {
        assertEquals(
            PoTokenPageRoutes.Route.Asset("potoken.html", "text/html"),
            route("/yft-potoken/potoken.html"),
        )
        assertEquals(
            PoTokenPageRoutes.Route.Asset("potoken-page.js", "text/javascript"),
            route("/yft-potoken/potoken-page.js"),
        )
        assertEquals(PoTokenPageRoutes.Route.Challenge, route("/yft-potoken/challenge.json"))
    }

    @Test
    fun `every other address is refused, YouTube's own included`() {
        listOf(
            PoTokenPageRoutes.route("http", PoTokenPageRoutes.HOST, "/yft-potoken/potoken.html"),
            PoTokenPageRoutes.route("https", "www.youtube.com", "/yft-potoken/potoken.html"),
            PoTokenPageRoutes.route("https", "www.google.com", "/js/th/interpreter.js"),
            PoTokenPageRoutes.route("https", PoTokenPageRoutes.HOST, null),
            route("/yft-potoken/"),
            route("/yft-potoken/../potoken.html"),
            route("/yft-solver/solver.html"),
            route("/favicon.ico"),
        ).forEach { refused ->
            assertEquals(PoTokenPageRoutes.Route.Refused, refused)
        }
    }

    @Test
    fun `the policy admits no other origin`() {
        val policy = PoTokenPageRoutes.CONTENT_SECURITY_POLICY
        assertTrue(policy.startsWith("default-src 'none';"))
        assertTrue(policy.contains("connect-src 'self'"))
        assertTrue(!policy.contains("https:") && !policy.contains("*"))
        assertEquals(
            "https://appassets.androidplatform.net/yft-potoken/potoken.html",
            PoTokenPageRoutes.PAGE_URL,
        )
    }

    private fun route(path: String) =
        PoTokenPageRoutes.route("https", PoTokenPageRoutes.HOST, path)
}
