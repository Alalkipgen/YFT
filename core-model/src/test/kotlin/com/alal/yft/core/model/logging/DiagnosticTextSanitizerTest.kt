package com.alal.yft.core.model.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTextSanitizerTest {
    @Test
    fun safeStepsRemainUseful() {
        val steps = listOf(
            "page GET 200 (612 KB)",
            """client WEB_EMBEDDED_PLAYER: ERROR "This video is unavailable"""",
            "adapter youtube: RATE_LIMITED",
        )
        assertEquals(steps, DiagnosticTextSanitizer.details(steps))
    }

    @Test
    fun credentialsAndQueriesNeverSurviveDetails() {
        val steps = listOf(
            "Cookie: redaction-fixture",
            """{"signature":"redaction-fixture"}""",
            "pot=redaction-fixture",
            """{"tt_chain_token":"redaction-fixture"}""",
            "Authorization: Bearer redaction-fixture",
            "page https://user:redaction-fixture@a.test/private?opaque=redaction-fixture",
            "unlabelled ?opaque=redaction-fixture",
        )
        val safe = DiagnosticTextSanitizer.details(steps).joinToString("\n")
        listOf("?", "Cookie", "signature=", "pot=", "redaction-fixture", "/private").forEach {
            assertFalse(safe.contains(it, ignoreCase = true))
        }
        assertTrue(safe.contains("https://a.test"))
    }

    @Test
    fun escapedEncodedAndLocalUrlsDoNotExposeValuesOrPaths() {
        val safe = DiagnosticTextSanitizer.redact(
            """https:\/\/a.test\/private?opaque=redaction-fixture""" + "\n" +
                "https%3A%2F%2Fa.test%2Fprivate%3Fopaque%3Dredaction-fixture\n" +
                "file:///private/redaction-fixture.txt",
        )
        assertFalse(safe.contains("redaction-fixture"))
        assertFalse(safe.contains("private"))
        assertFalse(safe.contains("?"))
        assertTrue(safe.contains("https://a.test"))
    }

    @Test
    fun snapshotsAreBoundedAndIndependentOfMutableInput() {
        val source = MutableList(100) { "a".repeat(500) }
        val safe = DiagnosticTextSanitizer.details(source)
        source.clear()
        assertEquals(DiagnosticTextSanitizer.MAX_STEPS, safe.size)
        assertTrue(safe.all { it.length == DiagnosticTextSanitizer.MAX_STEP_CHARS })
    }

    @Test
    fun multilineInputCannotBypassTheStepLimit() {
        val source = List(100) { "page GET 200" }.joinToString("\n")
        val safe = DiagnosticTextSanitizer.details(listOf(source))
        assertEquals(DiagnosticTextSanitizer.MAX_STEPS, safe.size)
    }
}
