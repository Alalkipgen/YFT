package com.alal.yft.extractor.sites.vk

import com.alal.yft.extractor.api.SitePageIdentity
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Pure, offline address rules for VK videos and clips (VK_PLAN §4.1, the owner's answers 2–3).
 *
 * Claimed: `/video{oid}_{id}` and `/clip{oid}_{id}` pages, a `z=video…` or `z=clip…` layer on
 * any page, and `video_ext.php?oid=…&id=…` embeds, on vk.com (www., m.), vk.ru (m.) and vkvideo.ru
 * (www., m.). Everything else — messenger posts, VK's link-out page, look-alike hosts, ids that
 * are not bounded numbers — stays with the generic detector. Matching never throws and never
 * touches the network.
 */
internal object VkUrls {
    const val SITE_ID = "vk"

    /** The embed hosts (§4.1): vkvideo.ru's own embed only ever answered the SPA shell. */
    const val COM_HOST = "vk.com"
    const val RU_HOST = "vk.ru"

    private val PAGE_HOSTS = setOf(
        "vk.com",
        "www.vk.com",
        "m.vk.com",
        "vk.ru",
        "m.vk.ru",
        "vkvideo.ru",
        "www.vkvideo.ru",
        "m.vkvideo.ru",
    )
    private const val MAX_PAGE_URL_CHARS = 4_096
    private const val MAX_MEDIA_URL_CHARS = 8_192
    private const val EMBED_PATH = "/video_ext.php"
    private const val AWAY_PATH = "/away.php"

    /** `video-50883936_456244102`, `clip30014565_456240946`: bounded digit strings only. */
    private val ITEM = Regex("^(video|clip)(-?[0-9]{1,19})_([0-9]{1,19})$")
    private val OWNER_ID = Regex("^-?[0-9]{1,19}$")
    private val VIDEO_ID = Regex("^[0-9]{1,19}$")
    private val LIST = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val HASH = Regex("^[A-Za-z0-9]{1,64}$")

    /** VK's video servers (the shape scan and the browser's media check). */
    private val PLAYER_MEDIA_HOST = Regex("^vkvd[0-9]{1,6}\\.okcdn\\.ru$")
    private const val MEDIA_DOMAIN_SUFFIX = ".okcdn.ru"

    /** okcdn.ru's image host (first frames, previews): never media. */
    private val IMAGE_HOSTS = setOf("iv.okcdn.ru")

    /** A video or clip link, as far as v1 reads it. */
    data class VkLink(
        val ownerId: Long,
        val videoId: Long,
        val isClip: Boolean = false,
        val list: String? = null,
        val hash: String? = null,
    ) {
        val contentId: String
            get() = "${ownerId}_$videoId"

        /** `https://vk.com/video{oid}_{id}` (or `clip…`), keeping the link's list and hash. */
        val canonicalUrl: String
            get() = buildString {
                append("https://").append(COM_HOST).append('/')
                append(if (isClip) "clip" else "video").append(contentId)
                val query = listOfNotNull(list?.let { "list=$it" }, hash?.let { "hash=$it" })
                if (query.isNotEmpty()) append('?').append(query.joinToString("&"))
            }
    }

    fun identify(pageUrl: String): SitePageIdentity? = linkOf(pageUrl)?.let { link ->
        SitePageIdentity(
            siteId = SITE_ID,
            contentId = link.contentId,
            canonicalPageUrl = link.canonicalUrl,
        )
    }

    /** The link [pageUrl] names, or null; never throws. */
    fun linkOf(pageUrl: String): VkLink? = try {
        parse(pageUrl)
    } catch (_: Exception) {
        null
    }

    /** `https://{host}/video_ext.php?oid=…&id=…[&hash=…][&list=…]` (values checked before). */
    fun embedUrl(host: String, link: VkLink): String = buildString {
        append("https://").append(host).append(EMBED_PATH)
        append("?oid=").append(link.ownerId).append("&id=").append(link.videoId)
        link.hash?.let { append("&hash=").append(it) }
        link.list?.let { append("&list=").append(it) }
    }

    /**
     * The VK site whose cookie a page holds: [RU_HOST] for vk.ru and its subdomains, [COM_HOST]
     * for vk.com and its, null for any other page (vkvideo.ru, Home's lookups).
     */
    fun cookieSiteOf(pageUrl: String?): String? {
        val host = pageUrl?.let(::httpsHostOf) ?: return null
        return when {
            host == RU_HOST || host.endsWith(".$RU_HOST") -> RU_HOST
            host == COM_HOST || host.endsWith(".$COM_HOST") -> COM_HOST
            else -> null
        }
    }

    /**
     * The key table's rule for an address under the video's `files`: HTTPS on VK's video CDN
     * (`*.okcdn.ru`, whatever its server prefix), never okcdn.ru's image host.
     */
    fun isMediaFileUrl(url: String): Boolean {
        val host = httpsHostOf(url) ?: return false
        return host.endsWith(MEDIA_DOMAIN_SUFFIX) && host !in IMAGE_HOSTS
    }

    /** The shape scan's and the browser's rule: VK's video servers `vkvd{N}.okcdn.ru` only. */
    fun isPlayerMediaUrl(url: String): Boolean {
        val host = httpsHostOf(url) ?: return false
        return PLAYER_MEDIA_HOST.matches(host)
    }

    /**
     * A signed address's expiry: the `expires` query value or an `/expires/{value}/` path
     * segment, in epoch milliseconds (VK's 13-digit value as is, a 10-digit one in seconds).
     */
    fun expiresAtOf(url: String): Long? = try {
        val uri = URI(url.trim())
        val fromQuery = uri.rawQuery?.split('&')
            ?.firstOrNull { it.substringBefore('=') == "expires" }
            ?.substringAfter('=', "")
        val segments = uri.rawPath.orEmpty().split('/')
        val fromPath = segments.indexOf("expires").takeIf { it >= 0 }
            ?.let { segments.getOrNull(it + 1) }
        epochMillisOf(fromQuery) ?: epochMillisOf(fromPath)
    } catch (_: Exception) {
        null
    }

    private fun epochMillisOf(value: String?): Long? {
        if (value == null || value.any { it !in '0'..'9' }) return null
        return when (value.length) {
            13 -> value.toLongOrNull()
            10 -> value.toLongOrNull()?.times(1_000)
            else -> null
        }
    }

    private fun parse(pageUrl: String): VkLink? {
        val text = pageUrl.trim()
        if (text.isEmpty() || text.length > MAX_PAGE_URL_CHARS) return null
        val uri = URI(text)
        if (uri.scheme?.lowercase(Locale.US) != "https") return null
        if (uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        if (host !in PAGE_HOSTS) return null
        val path = uri.rawPath.orEmpty()
        if (path.equals(AWAY_PATH, ignoreCase = true)) return null
        val query = queryOf(uri.rawQuery) ?: return null
        val list = query["list"]?.takeIf(LIST::matches)
        val hash = query["hash"]?.takeIf(HASH::matches)
        if (path.equals(EMBED_PATH, ignoreCase = true)) {
            val owner = query["oid"]?.takeIf(OWNER_ID::matches)?.toLongOrNull() ?: return null
            val id = query["id"]?.takeIf(VIDEO_ID::matches)?.toLongOrNull() ?: return null
            return link(owner, id, isClip = false)?.copy(list = list, hash = hash)
        }
        // A video opened over another page (feed, wall, search, messenger): the layer wins.
        query["z"]?.let { layer -> item(layer.substringBefore('/')) }?.let { found ->
            return found.copy(list = list, hash = hash)
        }
        val segments = path.split('/').filter(String::isNotEmpty)
        if (segments.size != 1) return null
        return item(segments.single())?.copy(list = list, hash = hash)
    }

    private fun item(text: String): VkLink? {
        val match = ITEM.matchEntire(text) ?: return null
        val owner = match.groupValues[2].toLongOrNull() ?: return null
        val id = match.groupValues[3].toLongOrNull() ?: return null
        return link(owner, id, isClip = match.groupValues[1] == "clip")
    }

    private fun link(owner: Long, id: Long, isClip: Boolean): VkLink? =
        if (owner == 0L || id <= 0L) null else VkLink(owner, id, isClip)

    /** The query's first value per name, decoded; null when an escape is broken. */
    private fun queryOf(rawQuery: String?): Map<String, String>? {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        val values = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val name = decode(pair.substringBefore('=')) ?: return null
            val value = decode(pair.substringAfter('=', "")) ?: return null
            if (name !in values) values[name] = value
        }
        return values
    }

    private fun decode(text: String): String? = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun httpsHostOf(url: String): String? = try {
        val text = url.trim()
        if (text.length > MAX_MEDIA_URL_CHARS) {
            null
        } else {
            val uri = URI(text)
            if (uri.scheme?.lowercase(Locale.US) != "https" || uri.rawUserInfo != null) {
                null
            } else {
                uri.host?.lowercase(Locale.US)?.takeIf(String::isNotEmpty)
            }
        }
    } catch (_: Exception) {
        null
    }
}
