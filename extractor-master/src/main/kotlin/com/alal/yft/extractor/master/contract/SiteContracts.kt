/*
 * Provenance (Master R8, copied, not moved; main 34a41890):
 *   extractor-sites/.../vimeo/VimeoUrls.kt  unlistedHashOf, UNLISTED_HASH, configUrl (the
 *                                           player configuration address keeps the hash)
 */
package com.alal.yft.extractor.master.contract

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.ContractAnswer
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.recipes.ContractRecipe
import com.alal.yft.extractor.master.recipes.ContractRecipes
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * R8: asks one site's own public player endpoint for the identified video, once, through the
 * caller's bounded client (HTTPS, redirect limit, timeouts, body cap). The answer is handed to
 * L2 ([com.alal.yft.extractor.master.layers.ContractLayer]) as a snapshot only the engine can
 * build; no layer fetches. Nothing is retried, signed beyond the widget's own token, or logged.
 */
class SiteContracts internal constructor(
    private val http: ExtractorHttpClient,
    private val recipes: List<ContractRecipe>,
) {
    constructor(http: ExtractorHttpClient) : this(http, ContractRecipes.ALL)

    internal sealed interface Asked {
        data class Answer(val snapshot: PageSnapshot, val detail: String) : Asked
        data class Unanswered(val detail: String?) : Asked
    }

    /** Whether a recipe reads [identity]'s site; a short-link code is never asked about. */
    internal fun reads(identity: SitePageIdentity?): Boolean =
        identity != null && !identity.requiresCanonicalResolution &&
            recipes.any { it.site == identity.siteId }

    internal suspend fun ask(request: MasterRequest): Asked {
        val identity = request.identity?.takeIf { reads(it) } ?: return Asked.Unanswered(null)
        val recipe = recipes.first { it.site == identity.siteId }
        val label = "master: ${recipe.site} contract"
        val url = endpoint(recipe, identity)?.takeIf { hostIn(it, recipe.answerHosts) }
            ?: return Asked.Unanswered("$label: no endpoint for this video")
        return when (val result = http.get(url, headers(recipe, identity, request), recipe.maxBytes)) {
            is ExtractorHttpResult.Failure -> Asked.Unanswered(
                "$label: ${result.reason.name}" + (result.statusCode?.let { " $it" } ?: ""),
            )
            is ExtractorHttpResult.Success -> if (!hostIn(result.finalUrl, recipe.answerHosts)) {
                Asked.Unanswered("$label: answer left the site's endpoint host")
            } else {
                Asked.Answer(
                    PageSnapshot(
                        request.pageUrl, request.generation,
                        contract = ContractAnswer(recipe.site, result.body, result.finalUrl),
                    ),
                    "$label: answered ${result.statusCode} (${result.body.length} characters)",
                )
            }
        }
    }

    private fun endpoint(recipe: ContractRecipe, identity: SitePageIdentity): String? {
        var url = recipe.endpoint.replace("{id}", encode(identity.contentId))
        if ("{hashQuery}" in url) {
            url = url.replace("{hashQuery}", unlistedHashOf(identity.canonicalPageUrl)?.let { "?h=$it" }.orEmpty())
        }
        if ("{href}" in url) url = url.replace("{href}", encode(identity.canonicalPageUrl))
        if ("{token}" in url) {
            val token = runCatching { WidgetToken.token(identity.contentId) }.getOrNull() ?: return null
            url = url.replace("{token}", token)
        }
        return url.takeUnless { '{' in it }
    }

    private fun headers(
        recipe: ContractRecipe,
        identity: SitePageIdentity,
        request: MasterRequest,
    ): Map<String, String> = buildMap {
        (recipe.agent ?: request.requestContext.userAgent)?.takeIf(String::isNotBlank)
            ?.let { put("User-Agent", it) }
        recipe.headers.forEach { (name, value) ->
            put(name, value.replace("{page}", identity.canonicalPageUrl))
        }
        val domain = recipe.cookieDomain
        val cookie = request.requestContext.cookie?.takeIf(String::isNotBlank)
        // The browser's cookie belongs to the tab's page; it stays inside the site's own domain.
        if (domain != null && cookie != null && hostIn(request.pageUrl, setOf(domain), true)) {
            put("Cookie", cookie)
        }
    }

    internal companion object {
        /** Main's VimeoUrls.UNLISTED_HASH: a short lowercase hex privacy hash. */
        private val UNLISTED_HASH = Regex("^[0-9a-f]{6,20}$")

        /** Main's VimeoUrls.unlistedHashOf: the hash a canonical clip address carries. */
        fun unlistedHashOf(canonicalPageUrl: String): String? {
            val uri = runCatching { URI(canonicalPageUrl.trim()) }.getOrNull() ?: return null
            val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
            return segments.getOrNull(1)?.takeIf(UNLISTED_HASH::matches)
        }

        fun hostIn(url: String, hosts: Set<String>, subdomains: Boolean = false): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            if (!uri.scheme.equals("https", true) || uri.userInfo != null) return false
            val host = uri.host?.lowercase() ?: return false
            return hosts.any { host == it || subdomains && host.endsWith(".$it") }
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }
}
