package com.alal.yft.core.model.logging

import java.net.URI

/**
 * Extra protection for text the owner may copy/share. No response bodies or session data belong
 * in diagnostics. URLs become origins only; credential-bearing lines are omitted altogether.
 */
object DiagnosticTextSanitizer {
    private val url = Regex("""(?i)\b[a-z][a-z0-9+.-]*://[^\s<>"']+""")
    private val encodedUrl = Regex("""(?i)\b[a-z][a-z0-9+.-]*%3a%2f%2f[^\s<>"']+""")
    private val sensitiveLine = Regex(
        """(?i)\b(cookie|set-cookie|authorization|proxy-authorization|x-api-key|""" +
            """password|passcode|(?:[a-z0-9]+_)*token|api_key|signature|sig|pot|""" +
            """sessionid|session_id|ttwid|visitor_data|visitorData)\b""",
    )
    private val query = Regex("""\?[^\s<>"']*""")
    private const val OMITTED = "[sensitive data omitted]"
    const val MAX_STEPS = 32
    const val MAX_STEP_CHARS = 240

    fun redact(text: String): String =
        SensitiveValueRedactor.redact(text.replace("\\/", "/"))
            .replace(encodedUrl, "[URL omitted]")
            .lineSequence().joinToString("\n") { line ->
            // Omit the whole line even for quoted JSON keys, incomplete values or unknown
            // credential formats, rather than guessing where a secret ends.
            if (sensitiveLine.containsMatchIn(line)) {
                OMITTED
            } else {
                line.replace(url) { match ->
                    runCatching {
                        val address = URI(match.value)
                        val host = address.host ?: return@runCatching "[URL omitted]"
                        val port = address.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
                        "${address.scheme.lowercase()}://$host$port"
                    }.getOrDefault("[URL omitted]")
                }.replace(query, "[query omitted]")
            }
        }

    /** Bounded immutable steps; no raw adapter values survive in the UI or its copy action. */
    fun details(steps: List<String>): List<String> = steps.asSequence()
        .take(MAX_STEPS)
        .flatMap { step -> redact(step.take(4_096)).lineSequence() }
        .map { it.trim().take(MAX_STEP_CHARS) }
        .filter { it.isNotEmpty() && it != OMITTED }
        .take(MAX_STEPS)
        .toList()
}
