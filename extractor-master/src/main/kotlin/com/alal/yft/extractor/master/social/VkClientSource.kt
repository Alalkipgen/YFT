package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure

/**
 * VK Master plan §8, part 2 (the owner's go, 2026-10-25, on agent A's recon,
 * `docs/VK_MASTER_CENSUS.md` §5 and §3.2a): V2's live self-heal source — vkvideo.ru's public
 * web-player script — as a [ValueReader] for [ValueSource]. It heals two values only, each one
 * strict pattern match: [CLIENT_ID] (the digits after `clientId:` in the client block, the object
 * literal that also names `clientSecret:`) and [API_VERSION] (the version after `API_VERSION:`).
 * Never the client secret: VK's token endpoint does not check it today (A §3.2a), so the bundled
 * one stays; any other kind, a look-alike under the same name too, is refused without a request.
 *
 * One read is [REQUESTS_PER_READ] GETs, each with the lookup's own User-Agent and nothing else (no
 * cookie, no token, no Referer): vkvideo.ru's own page ([PAGE], at most [PAGE_MAX_BYTES]) for
 * today's script address — VK's script names are content-hashed and its static host shards
 * (`st.vkvideo.ru`, `st{N}-{N}.vkvideo.ru`), so no script address is bundled — then that one
 * script, the smallest that carries the value ([Chunk]): the client id from `error_monitoring`
 * (~40 KB, at most 256 KiB), the API version from `web/chunks/common` (~1.8 MB, at most 4 MiB,
 * where A found it), and `entrypoints/core_spa` (~3 MB, at most 4 MiB) only when the page lists
 * neither. A redirect, a script off those hosts or not over HTTPS, a page that lists no such
 * script or two different ones, or an answer that is not HTML or a script gives no value (a short
 * note says which; the day's read is spent, [SiteHealth.claimRead]).
 * [ValueSource] matches the value, reads at most once a day per value, keeps a value only after an
 * OK answer and a V pass, and stops the site after a 429 or a bot check. Nothing here logs.
 */
internal class VkClientSource(
    private val http: ExtractorHttpClient,
    private val page: String = PAGE,
) : ValueReader {
    override val id: String = ID

    init {
        require(PAGE_ADDRESS.matches(page)) { "not a vkvideo.ru page" }
    }

    override suspend fun read(kind: ValueKind, userAgent: String?, maxBytes: Long): ExtractorHttpResult {
        val chunks = CHUNKS[kind] ?: return refused(SiteExtractionFailure.UNSUPPORTED_URL, "not a value this source reads")
        val headers = userAgent?.takeIf(String::isNotBlank)?.let { mapOf(USER_AGENT to it) }.orEmpty()
        val html = when (val got = http.get(page, headers, PAGE_MAX_BYTES)) {
            is ExtractorHttpResult.Failure -> return refused(got.reason, "the page", got.statusCode)
            is ExtractorHttpResult.Success -> when {
                got.finalUrl != page -> return refused(SiteExtractionFailure.HTTP_STATUS, "the page redirected")
                !typed(got.contentType, HTML_TYPES) -> return refused(SiteExtractionFailure.MALFORMED_RESPONSE, "the page is not html")
                else -> got.body
            }
        }
        val script = when (val found = scriptOf(html, chunks)) {
            is Found.Script -> found
            is Found.None -> return refused(SiteExtractionFailure.RESPONSE_CHANGED, found.note)
        }
        return when (val got = http.get(script.address, headers, minOf(script.chunk.maxBytes, maxBytes))) {
            is ExtractorHttpResult.Failure -> refused(got.reason, "the ${script.chunk.name} script", got.statusCode)
            is ExtractorHttpResult.Success -> when {
                got.finalUrl != script.address -> refused(SiteExtractionFailure.HTTP_STATUS, "the script redirected")
                !typed(got.contentType, SCRIPT_TYPES) -> refused(SiteExtractionFailure.MALFORMED_RESPONSE, "not a script")
                else -> ExtractorHttpResult.Success(got.statusCode, got.body, got.finalUrl, got.contentType)
            }
        }
    }

    override fun toString(): String = "VkClientSource(vkvideo.ru)"

    /**
     * A script the page may list: [name] for notes; [file], its path's end (`<name>.<hash>.js`,
     * the hash VK's content hash); [maxBytes], the most read of it.
     */
    private class Chunk(val name: String, file: String, val maxBytes: Long) {
        val address = Regex(
            "(?<![A-Za-z0-9_.:/-])(?:https:)?//(st(?:[0-9]{1,3}-[0-9]{1,3})?\\.vkvideo\\.ru)" +
                "((?:/[A-Za-z0-9_~-][A-Za-z0-9_.~-]{0,63}){0,8}/$file)(?=[\"'?#\\s>),;\\\\])",
        )
    }

    private sealed interface Found {
        class Script(val address: String, val chunk: Chunk) : Found

        class None(val note: String) : Found
    }

    companion object {
        /** The source's ID in notes and the health ([SiteHealth.WORD]: never an address). */
        const val ID = "vkvideo-script"

        /** vkvideo.ru's own page: it lists today's scripts (A §5). */
        const val PAGE = "https://vkvideo.ru/"

        /** One read: the page, then one script (B's VK budget counts both). */
        const val REQUESTS_PER_READ = 2

        const val PAGE_MAX_BYTES = 1L * 1024 * 1024

        /** The most read of `error_monitoring` (~40 KB today, A §5). */
        const val ERROR_MONITORING_MAX_BYTES = 256L * 1024

        /** The client id: VK's web client's, digits (A §3.2a: the value the token endpoint checks). */
        val CLIENT_ID = ValueKind(
            site = SiteHealth.VK,
            name = "clientId",
            pattern = Regex("[0-9]{6,10}"),
            anchor = Regex(
                "\\{(?=[^{}]{0,512}?(?<![A-Za-z0-9_$.])clientSecret:)[^{}]{0,512}?" +
                    "(?<![A-Za-z0-9_$.])clientId:([0-9]{6,10})(?![0-9])[^{}]{0,512}?\\}",
            ),
            maxBytes = ValueKind.MAX_BYTES,
        )

        /** VK's API version (`video.get`'s `v`): a public version, kept by the health after OK + V. */
        val API_VERSION = ValueKind(
            site = SiteHealth.VK,
            name = "apiVersion",
            pattern = Regex("5\\.[0-9]{1,4}"),
            anchor = Regex("(?<![A-Za-z0-9_$.])API_VERSION:(?:\\(\\)=>)?\"(5\\.[0-9]{1,4})\""),
            publicVersion = true,
            maxBytes = ValueKind.MAX_BYTES,
        )

        /** The two values this source heals; nothing else (never the secret). */
        val KINDS: List<ValueKind> = listOf(CLIENT_ID, API_VERSION)

        private const val USER_AGENT = "User-Agent"
        private const val HASH = "[A-Za-z0-9_-]{4,64}"
        private val PAGE_ADDRESS = Regex("^https://vkvideo\\.ru/[A-Za-z0-9_.?=&-]{0,128}$")
        private val HTML_TYPES = setOf("text/html")
        private val SCRIPT_TYPES = setOf(
            "application/javascript", "text/javascript", "application/x-javascript", "application/ecmascript", "text/ecmascript",
        )

        private val ERROR_MONITORING = Chunk("error_monitoring", "error_monitoring\\.$HASH\\.js", ERROR_MONITORING_MAX_BYTES)
        private val COMMON = Chunk("common", "web/chunks/common\\.$HASH\\.js", ValueKind.MAX_BYTES)
        private val CORE_SPA = Chunk("core_spa", "entrypoints/core_spa\\.$HASH\\.js", ValueKind.MAX_BYTES)

        /**
         * Each value's scripts, smallest first (A §5; `core_spa` only when the page lists
         * neither), keyed by the kind itself: a look-alike (a looser pattern, the same name) is
         * refused.
         */
        private val CHUNKS: Map<ValueKind, List<Chunk>> = mapOf(
            CLIENT_ID to listOf(ERROR_MONITORING, CORE_SPA),
            API_VERSION to listOf(COMMON, CORE_SPA),
        )

        /** The first of [chunks] the page lists, on VK's static host over HTTPS; two different files of it: none. */
        private fun scriptOf(html: String, chunks: List<Chunk>): Found {
            val text = html.replace("\\/", "/")
            for (chunk in chunks) {
                val found = chunk.address.findAll(text)
                val first = found.firstOrNull() ?: continue
                if (found.map { it.groupValues[2] }.distinct().take(2).count() > 1) {
                    return Found.None("two ${chunk.name} scripts on the page")
                }
                return Found.Script("https://${first.groupValues[1]}${first.groupValues[2]}", chunk)
            }
            return Found.None("no script on the page")
        }

        private fun typed(contentType: String?, types: Set<String>): Boolean =
            contentType?.substringBefore(';')?.trim()?.lowercase() in types

        /** No value: [reason], a passed-on [statusCode] only, and a short [note] for the read's reason. */
        private fun refused(reason: SiteExtractionFailure, note: String, statusCode: Int? = null): ExtractorHttpResult =
            ExtractorHttpResult.Failure(reason, statusCode?.takeIf { it in 100..599 }, listOf(note))
    }
}
