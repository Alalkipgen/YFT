package com.alal.yft.core.model.logging

object SensitiveValueRedactor {
    private const val REDACTED = "[REDACTED]"

    private val sensitiveHeader = Regex(
        pattern = """(?im)\b(cookie|set-cookie|authorization|proxy-authorization|x-api-key)\s*:\s*[^\r\n]+""",
    )
    private val bearerCredential = Regex(
        pattern = """(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""",
    )
    private val sensitiveQueryValue = Regex(
        pattern = """(?i)([?&](?:access_token|token|auth|authorization|api_key|key|signature|sig)=)[^&#\s]+""",
    )
    private val namedValue = Regex(
        pattern = """(?i)(\b(?:password|passcode|token|access_token|cookie|authorization|signature)\b\s*[=:]\s*)("[^"]*"|'[^']*'|[^,;&\s}]+)""",
    )

    fun redact(message: String): String = message
        .replace(sensitiveHeader) { match ->
            "${match.groupValues[1]}: $REDACTED"
        }
        .replace(bearerCredential, "Bearer $REDACTED")
        .replace(sensitiveQueryValue) { match ->
            "${match.groupValues[1]}$REDACTED"
        }
        .replace(namedValue) { match ->
            "${match.groupValues[1]}$REDACTED"
        }
}
