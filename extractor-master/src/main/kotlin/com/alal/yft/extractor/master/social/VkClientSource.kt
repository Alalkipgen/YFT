/*
 * Provenance (agent D's wiring, VK-Master plan §8; copied, not moved; rows in
 * toolkit-provenance.tsv):
 *   extractor-sites/.../tiktok/TikTokAgents.kt  desktop, CHROME_MAJOR, FALLBACK_CHROME_MAJOR
 *                                             (desktop Chrome on Windows with the phone's Chrome
 *                                             major: vkvideo.ru's page lists its scripts only to a
 *                                             desktop browser)
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.RouteBLookup
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * VK Master plan §8, part 2 (the owner's go, 2026-10-25, on agent A's recon,
 * `docs/VK_MASTER_CENSUS.md` §5 and §3.2a; agent D's live check, 2026-10-26): V2's live self-heal
 * source — vkvideo.ru's public web-player script — as a [ValueReader] for [ValueSource]. It heals
 * two values only, each one strict pattern match: [CLIENT_ID] (the web client's app ID that VK's
 * script exports as `VK_WEB_VKVIDEO_APP_ID`, written as hex or digits) and [API_VERSION] (the
 * version after `API_VERSION:`). Never the client secret: VK's token endpoint does not check it
 * today (A §3.2a), so the bundled one stays; any other kind, a look-alike under the same name too,
 * is refused without a request.
 *
 * One read is [REQUESTS_PER_READ] GETs, each with desktop Chrome's User-Agent ([desktop]: the
 * user's own Chrome version; vkvideo.ru lists its scripts only to a desktop browser) and nothing
 * else (no cookie, no token, no Referer; the app's client sends back only the cookies vkvideo.ru's
 * own redirects set, within the one ask): vkvideo.ru's own page ([PAGE], at most
 * [PAGE_MAX_BYTES]; one lookup ask, however many redirects it takes) for today's script address
 * — VK's script names are content-hashed and its static host shards (`st.vkvideo.ru`,
 * `st{N}-{N}.vkvideo.ru`), so no script address is bundled — then that one script,
 * `web/chunks/common` (~1.8 MB today, at most [COMMON_MAX_BYTES]), which carries both values. A
 * redirect away from the page, a script off those hosts or not over HTTPS, a page that lists no
 * such script or two different ones, or an answer that is not HTML or a script gives no value (a
 * short note says which; the day's read is spent, [SiteHealth.claimRead]). The script is read
 * whole: the app's client cannot stream an answer, and refuses one over its bound
 * (`RESPONSE_TOO_LARGE`) instead of cutting it, so a read cannot stop at the first match.
 *
 * Inside V2's [Pass] (the ladder's, one lookup): each GET goes by the lookup's rules
 * ([RouteBLookup]: listed in the record as Master's, nothing new after a 429), the pass counts its
 * asks and bytes for V2's line, and the script is read once for both values (the second value of
 * the pass is matched in the same text, no new request). [ValueSource] matches the value, reads at
 * most once a day per value, keeps a value only after an OK answer and a V pass, and stops the
 * site after a 429 or a bot check. Nothing here logs.
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
        if (KINDS.none { it === kind }) return refused(SiteExtractionFailure.UNSUPPORTED_URL, "not a value this source reads")
        val pass = coroutineContext[Pass]
        pass?.script?.let { return it }
        val headers = mapOf(USER_AGENT to desktop(userAgent))
        val html = when (val got = get(pass, page, headers, PAGE_MAX_BYTES)) {
            is ExtractorHttpResult.Failure -> return refused(got.reason, "the page", got.statusCode)
            is ExtractorHttpResult.Success -> when {
                got.finalUrl != page -> return refused(SiteExtractionFailure.HTTP_STATUS, "the page redirected")
                !typed(got.contentType, HTML_TYPES) -> return refused(SiteExtractionFailure.MALFORMED_RESPONSE, "the page is not html")
                else -> got.body
            }
        }
        val script = when (val found = scriptOf(html)) {
            is Found.Script -> found.address
            is Found.None -> return refused(SiteExtractionFailure.RESPONSE_CHANGED, found.note)
        }
        return when (val got = get(pass, script, headers, minOf(COMMON_MAX_BYTES, maxBytes))) {
            is ExtractorHttpResult.Failure -> refused(got.reason, "the common script", got.statusCode)
            is ExtractorHttpResult.Success -> when {
                got.finalUrl != script -> refused(SiteExtractionFailure.HTTP_STATUS, "the script redirected")
                !typed(got.contentType, SCRIPT_TYPES) -> refused(SiteExtractionFailure.MALFORMED_RESPONSE, "not a script")
                else -> ExtractorHttpResult.Success(got.statusCode, got.body, got.finalUrl, got.contentType)
                    .also { pass?.script = it }
            }
        }
    }

    /** One GET of the read: by the lookup's rules inside a [pass] (counted there), else straight. */
    private suspend fun get(pass: Pass?, url: String, headers: Map<String, String>, maxBytes: Long): ExtractorHttpResult {
        if (pass == null) return http.get(url, headers, maxBytes)
        return pass.route.exchange(LookupAsk(LookupAsk.GET, url, null, headers), maxBytes) {
            pass.asks += 1
            http.get(url, headers, maxBytes).also { got ->
                if (got is ExtractorHttpResult.Success) pass.bytes += got.body.toByteArray(Charsets.UTF_8).size
            }
        }
    }

    override fun toString(): String = "VkClientSource(vkvideo.ru)"

    /**
     * One V2 pass of VK's ladder (one lookup): its GETs go by [route] (the lookup's record), [asks]
     * and [bytes] count what went out and came back (for V2's line: numbers only), and [script]
     * keeps the common script read in it, so the pass's second value is matched in the same text
     * without a new request. Memory only, dropped with the pass.
     */
    class Pass(val route: RouteBLookup) : AbstractCoroutineContextElement(Pass) {
        var asks: Int = 0
            internal set
        var bytes: Long = 0
            internal set
        internal var script: ExtractorHttpResult.Success? = null

        /** Whether this pass read the script already (its second value needs no request). */
        val read: Boolean get() = script != null

        override fun toString(): String = "VkClientSource.Pass(asks=$asks, bytes=$bytes)"

        companion object Key : CoroutineContext.Key<Pass>
    }

    private sealed interface Found {
        class Script(val address: String) : Found

        class None(val note: String) : Found
    }

    companion object {
        /** The source's ID in notes and the health ([SiteHealth.WORD]: never an address). */
        const val ID = "vkvideo-script"

        /** vkvideo.ru's own page: it lists today's scripts (A §5). */
        const val PAGE = "https://vkvideo.ru/"

        /** One read: the page, then the common script (VK's budget counts both: [VkSite.BUDGET]). */
        const val REQUESTS_PER_READ = 2

        /** The most read of the page (~310 KB today, the live check). */
        const val PAGE_MAX_BYTES = 1L * 1024 * 1024

        /** The most read of `web/chunks/common` (~1.8 MB today, the live check). */
        const val COMMON_MAX_BYTES = ValueKind.MAX_BYTES

        /**
         * The client id: the web client's app ID (A §3.2a: the value the token endpoint checks),
         * which VK's common script exports by name and assigns nearby — `VK_WEB_VKVIDEO_APP_ID:()=>n`,
         * then `n=0x…` (hex, read as decimal) or digits.
         */
        val CLIENT_ID = ValueKind(
            site = SiteHealth.VK,
            name = "clientId",
            pattern = Regex("[0-9]{6,10}"),
            anchor = Regex(
                "(?<![A-Za-z0-9_\$.])VK_WEB_VKVIDEO_APP_ID:\\(\\)=>([A-Za-z_\$][A-Za-z0-9_\$]{0,7})[,}]" +
                    "[\\s\\S]{0,600}?[ ,]\\1=(0x[0-9a-fA-F]{1,8}|[1-9][0-9]{5,9})(?![A-Za-z0-9_\$.])",
            ),
            maxBytes = ValueKind.MAX_BYTES,
            valueOf = { match -> match.groups[2]?.value?.let(::decimal) },
        )

        /** VK's API version (`video.get`'s `v`): a public version, kept by the health after OK + V. */
        val API_VERSION = ValueKind(
            site = SiteHealth.VK,
            name = "apiVersion",
            pattern = Regex("5\\.[0-9]{1,4}"),
            anchor = Regex(
                "(?<![A-Za-z0-9_\$.])API_VERSION:(?:\\(\\)=>)?" +
                    "(?:([A-Za-z_\$][A-Za-z0-9_\$]{0,7})\\}\\);?(?:let|var|const) \\1=)?\"(5\\.[0-9]{1,4})\"",
            ),
            publicVersion = true,
            maxBytes = ValueKind.MAX_BYTES,
            valueOf = { match -> match.groups[2]?.value },
        )

        /** The two values this source heals; nothing else (never the secret). */
        val KINDS: List<ValueKind> = listOf(CLIENT_ID, API_VERSION)

        /** Desktop Chrome on Windows with [phoneAgent]'s Chrome major version (main's TikTok agents'). */
        fun desktop(phoneAgent: String?): String {
            val major = phoneAgent?.let { CHROME_MAJOR.find(it)?.groupValues?.get(1) }
                ?: FALLBACK_CHROME_MAJOR
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
        }

        private val CHROME_MAJOR = Regex("Chrome/(\\d{2,4})\\.")
        private const val FALLBACK_CHROME_MAJOR = "131"

        private const val USER_AGENT = "User-Agent"
        private const val HASH = "[A-Za-z0-9_-]{4,64}"
        private val PAGE_ADDRESS = Regex("^https://vkvideo\\.ru/[A-Za-z0-9_.?=&-]{0,128}$")
        private val HTML_TYPES = setOf("text/html")
        private val SCRIPT_TYPES = setOf(
            "application/javascript", "text/javascript", "application/x-javascript", "application/ecmascript", "text/ecmascript",
        )
        private const val NAME = "common"

        /** The common chunk's address on VK's static host over HTTPS (`<path>/web/chunks/common.<hash>.js`). */
        private val COMMON = Regex(
            "(?<![A-Za-z0-9_.:/-])(?:https:)?//(st(?:[0-9]{1,3}-[0-9]{1,3})?\\.vkvideo\\.ru)" +
                "((?:/[A-Za-z0-9_~-][A-Za-z0-9_.~-]{0,63}){0,8}/web/chunks/common\\.$HASH\\.js)(?=[\"'?#\\s>),;\\\\])",
        )

        /** The common script the page lists, on VK's static host over HTTPS; two different files of it: none. */
        private fun scriptOf(html: String): Found {
            val text = html.replace("\\/", "/")
            val found = COMMON.findAll(text)
            val first = found.firstOrNull() ?: return Found.None("no script on the page")
            if (found.map { it.groupValues[2] }.distinct().take(2).count() > 1) {
                return Found.None("two $NAME scripts on the page")
            }
            return Found.Script("https://${first.groupValues[1]}${first.groupValues[2]}")
        }

        /** A matched client id as decimal digits: hex (`0x…`) converted, digits as they are; else null. */
        private fun decimal(value: String): String? = when {
            value.startsWith("0x") -> value.substring(2).toLongOrNull(16)?.toString()
            else -> value
        }

        private fun typed(contentType: String?, types: Set<String>): Boolean =
            contentType?.substringBefore(';')?.trim()?.lowercase() in types

        /** No value: [reason], a passed-on [statusCode] only, and a short [note] for the read's reason. */
        private fun refused(reason: SiteExtractionFailure, note: String, statusCode: Int? = null): ExtractorHttpResult =
            ExtractorHttpResult.Failure(reason, statusCode?.takeIf { it in 100..599 }, listOf(note))
    }
}
