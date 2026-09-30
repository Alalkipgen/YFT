package com.alal.yft.core.model.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveValueRedactorTest {
    @Test
    fun redactsHeadersBearerCredentialsAndSignedQueryValues() {
        val input = """
            Cookie: session=fake-cookie-value
            Authorization: Bearer fake-bearer-value
            https://media.example/video.mp4?token=fake-query-value&quality=720p&signature=fake-signature
            password=fake-password
        """.trimIndent()

        val redacted = SensitiveValueRedactor.redact(input)

        listOf(
            "fake-cookie-value",
            "fake-bearer-value",
            "fake-query-value",
            "fake-signature",
            "fake-password",
        ).forEach { secret -> assertFalse(redacted.contains(secret)) }
        assertTrue(redacted.contains("quality=720p"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun leavesOrdinaryDiagnosticsReadable() {
        val message = "Request failed with status=503 retry=true"

        assertTrue(SensitiveValueRedactor.redact(message).contains(message))
    }
}
