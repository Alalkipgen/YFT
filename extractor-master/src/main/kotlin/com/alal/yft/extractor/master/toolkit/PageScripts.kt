/*
 * Provenance (Master toolkit T1, copied, not moved; main 34a41890):
 *   extractor-sites/.../tiktok/TikTokPageParser.kt      scripts (by the opening tag's own `id`)
 *   extractor-sites/.../facebook/FacebookPageParser.kt  scriptPayloads (bounded JSON scripts)
 *   extractor-sites/.../instagram/InstagramMedia.kt     InstagramPageDocuments.documents
 *                                                       (contextJSON strings, __additionalDataLoaded)
 * Adapted: one bounded scan that keeps each script's own attributes; assigned objects.
 */
package com.alal.yft.extractor.master.toolkit

/** One delivered `<script>`: its own opening-tag attributes and its body. */
internal data class PageScript(val id: String?, val type: String?, val body: String) {
    override fun toString(): String = "PageScript(id=$id, type=$type, chars=${body.length})"
}

/**
 * T1: data scripts found by their own `id`/`type`, never by the first mention of a name in the
 * page (a preload hint or another script's text cannot pose as the data script).
 */
internal object PageScripts {
    const val MAX_SCRIPTS = 200
    const val MAX_SCRIPT_CHARS = 4 * 1024 * 1024
    private const val CONTEXT_KEY = "\"contextJSON\":\""
    private const val ADDITIONAL_DATA = "__additionalDataLoaded("
    private const val MAX_STRING_DOCUMENTS = 16

    /** `var x = {`, `window.x = {`, `x.y = [` at a statement start. */
    private val ASSIGNMENT = Regex(
        """(?:^|[;{}\s(,])(?:(?:var|let|const)\s+)?(?:window\.|self\.)?[A-Za-z_$][\w$]*""" +
            """(?:\.[A-Za-z_$][\w$]*|\[\s*["'][^"']{1,80}["']\s*])*\s*=\s*(?=[{\[])""",
    )

    fun scripts(html: String): List<PageScript> {
        val found = mutableListOf<PageScript>()
        var from = 0
        while (found.size < MAX_SCRIPTS) {
            val tagStart = html.indexOf("<script", from, ignoreCase = true).takeIf { it >= 0 }
                ?: break
            val tagEnd = html.indexOf('>', tagStart).takeIf { it >= 0 } ?: break
            val bodyEnd = html.indexOf("</script", tagEnd, ignoreCase = true).takeIf { it >= 0 }
                ?: break
            from = bodyEnd + 1
            val attributes = HtmlScan.attributes(html.substring(tagStart + "<script".length, tagEnd))
            val body = html.substring(tagEnd + 1, bodyEnd).trim()
            if (body.isEmpty() || body.length > MAX_SCRIPT_CHARS) continue
            found += PageScript(attributes["id"], attributes["type"]?.lowercase(), body)
        }
        return found
    }

    /** Scripts whose own `type` is JSON, or whose own `id` is one of [ids]. */
    fun dataScripts(html: String, types: Set<String>, ids: Set<String> = emptySet()) =
        scripts(html).filter { it.type in types || it.id in ids }

    /** Objects/arrays assigned in executable scripts (`var x = {…}`), in page order. */
    fun assigned(script: PageScript, limit: Int = 8): List<String> {
        if (script.type != null && script.type !in EXECUTABLE) return emptyList()
        return ASSIGNMENT.findAll(script.body).take(limit * 4).mapNotNull { match ->
            BalancedJson.objectAt(script.body, match.range.last + 1)
        }.filter { it.length > 2 }.take(limit).toList()
    }

    /** JSON delivered inside JSON strings (`"contextJSON":"{…}"`) and `__additionalDataLoaded`. */
    fun embeddedDocuments(html: String): List<String> = buildList {
        var at = html.indexOf(CONTEXT_KEY)
        while (at >= 0 && size < MAX_STRING_DOCUMENTS) {
            BalancedJson.stringAt(html, at + CONTEXT_KEY.length - 1)?.let(::add)
            at = html.indexOf(CONTEXT_KEY, at + CONTEXT_KEY.length)
        }
        var data = html.indexOf(ADDITIONAL_DATA)
        while (data >= 0 && size < MAX_STRING_DOCUMENTS) {
            val open = html.indexOf('{', data)
            if (open >= 0 && open - data < 400) BalancedJson.objectAt(html, open)?.let(::add)
            data = html.indexOf(ADDITIONAL_DATA, data + ADDITIONAL_DATA.length)
        }
    }

    private val EXECUTABLE = setOf("text/javascript", "application/javascript", "module", "")
}
