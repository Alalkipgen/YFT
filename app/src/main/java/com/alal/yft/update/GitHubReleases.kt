package com.alal.yft.update

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.get
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** An update found on GitHub: what the Settings row, the Updates screen and the link need. */
data class FoundUpdate(
    val version: ReleaseVersion,
    /** The rule's versionCode of a stable tag; null for a prerelease (test mode only). */
    val code: Long?,
    val prerelease: Boolean,
    /** The APK's `browser_download_url`, always under [GitHubReleases.DOWNLOAD_PREFIX]. */
    val apkUrl: String,
    val apkSize: Long,
    /** The release's `SHA256SUMS` asset; null when it has none (the in-app install refuses). */
    val sumsUrl: String?,
    /**
     * The release's body as written (Markdown), at most [GitHubReleases.MAX_NOTES] characters; the
     * Updates screen renders its Markdown ([ReleaseNotes], [ReleaseNotesView]).
     */
    val notes: String,
    /** GitHub's `published_at`, such as "2026-10-25T09:00:00Z"; null when missing. */
    val publishedAt: String?,
)

/** One release of GitHub's list, reduced to what the check reads; null fields were missing. */
internal data class GitHubRelease(
    val tag: String?,
    val draft: Boolean?,
    val prerelease: Boolean?,
    val publishedAt: String?,
    val body: String?,
    val assets: List<GitHubAsset>,
)

internal data class GitHubAsset(
    val name: String?,
    val state: String?,
    val url: String?,
    val size: Long?,
)

/**
 * Reads `GET /repos/Alalkipgen/YFT/releases` and picks the update to offer: never a draft; a
 * prerelease only in test mode; a stable tag's rule code above the installed versionCode (in test
 * mode the installed code counts as 0, so every release that reads qualifies); the APK asset
 * named exactly `video-downloader-<version>.apk`, uploaded, under the repository's download
 * address. The highest code wins (the newest `published_at` in test mode).
 */
internal object GitHubReleases {
    const val MAX_BYTES = 512L * 1024
    const val MAX_NOTES = 20_000
    const val DOWNLOAD_PREFIX = "https://github.com/Alalkipgen/YFT/releases/download/"
    const val SUMS_NAME = "SHA256SUMS"
    private const val UPLOADED = "uploaded"
    private val PUBLISHED_AT = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")

    /** The releases of a list answer, or null when it is not a JSON array of objects. */
    fun parse(json: String): List<GitHubRelease>? {
        val root = BoundedJsonParser.parse(json) as? JsonValue.Array ?: return null
        return root.items.map { item ->
            if (item !is JsonValue.Object) return null
            GitHubRelease(
                tag = item["tag_name"].text(),
                draft = item["draft"].asBooleanOrNull,
                prerelease = item["prerelease"].asBooleanOrNull,
                publishedAt = item["published_at"].text()?.takeIf(PUBLISHED_AT::matches),
                body = item["body"].text(),
                assets = item["assets"].asArrayOrEmpty.mapNotNull { asset ->
                    (asset as? JsonValue.Object)?.let {
                        GitHubAsset(
                            name = it["name"].text(),
                            state = it["state"].text(),
                            url = it["browser_download_url"].text(),
                            size = it["size"].asLongOrNull,
                        )
                    }
                },
            )
        }
    }

    /** The update to offer, or null when no release qualifies. */
    fun candidate(
        releases: List<GitHubRelease>,
        installedCode: Long,
        testMode: Boolean,
    ): FoundUpdate? {
        val offers = releases.mapNotNull { offer(it, installedCode, testMode) }
        return if (testMode) {
            offers.maxWithOrNull(
                compareBy<FoundUpdate> { it.publishedAt.orEmpty() }.thenBy { it.version },
            )
        } else {
            offers.maxWithOrNull(compareBy<FoundUpdate> { it.code ?: 0 }.thenBy { it.version })
        }
    }

    /** The body with `\n` line ends: at most [MAX_NOTES] characters, cut at a line end. */
    fun notes(body: String?): String {
        val text = body.orEmpty().replace("\r\n", "\n").replace('\r', '\n').trimEnd()
        if (text.length <= MAX_NOTES) return text
        val lineEnd = text.lastIndexOf('\n', MAX_NOTES)
        var end = if (lineEnd > 0) lineEnd else MAX_NOTES
        if (text[end - 1].isHighSurrogate()) end -= 1
        return text.substring(0, end).trimEnd()
    }

    /** A download address YFT may open: HTTPS, on github.com, under this repository's releases. */
    fun isReleaseDownload(url: String?): Boolean {
        if (url == null || !url.startsWith(DOWNLOAD_PREFIX)) return false
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.isHttps && parsed.host == "github.com" && url.none { it.isWhitespace() }
    }

    private fun offer(
        release: GitHubRelease,
        installedCode: Long,
        testMode: Boolean,
    ): FoundUpdate? {
        if (release.draft != false) return null
        val prerelease = release.prerelease ?: return null
        if (prerelease && !testMode) return null
        val version = release.tag?.let(ReleaseVersion::parse) ?: return null
        val code = version.ruleCode
        if (!testMode && (code == null || code <= installedCode)) return null
        val apk = release.assets.firstOrNull { it.name == version.apkName } ?: return null
        val apkSize = apk.size ?: return null
        if (apk.state != UPLOADED || apkSize <= 0 || !isReleaseDownload(apk.url)) return null
        val sums = release.assets.firstOrNull { it.name == SUMS_NAME }
            ?.takeIf { it.state == UPLOADED && isReleaseDownload(it.url) }
        return FoundUpdate(
            version = version,
            code = code,
            prerelease = prerelease || !version.isStable,
            apkUrl = checkNotNull(apk.url),
            apkSize = apkSize,
            sumsUrl = sums?.url,
            notes = notes(release.body),
            publishedAt = release.publishedAt,
        )
    }

    private fun JsonValue?.text(): String? = (this as? JsonValue.Text)?.value
}
