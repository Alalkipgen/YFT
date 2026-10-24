package com.alal.yft.update

/** GitHub's releases list, trimmed to the fields the check reads (plus a few it ignores). */
internal object ReleaseFixtures {
    const val DOWNLOAD = "https://github.com/Alalkipgen/YFT/releases/download"

    fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = tag.contains('-'),
        publishedAt: String? = "2026-10-25T09:00:00Z",
        body: String? = "Fixes and a faster download list.",
        assets: List<String> = listOf(apk(tag), sums(tag)),
    ): String = """
        {
          "url": "https://api.github.com/repos/Alalkipgen/YFT/releases/1",
          "id": 1,
          "tag_name": ${quote(tag)},
          "name": ${quote(tag)},
          "draft": $draft,
          "prerelease": $prerelease,
          "created_at": "2026-10-25T08:00:00Z",
          "published_at": ${publishedAt?.let(::quote) ?: "null"},
          "author": {"login": "Alalkipgen", "id": 7},
          "assets": [${assets.joinToString(",")}],
          "body": ${body?.let(::quote) ?: "null"}
        }
    """.trimIndent()

    fun apk(
        tag: String,
        name: String = "video-downloader-${tag.removePrefix("v")}.apk",
        state: String = "uploaded",
        url: String = "$DOWNLOAD/$tag/$name",
        size: Long = 6_334_176,
    ): String = asset(name, state, url, size)

    fun sums(
        tag: String,
        state: String = "uploaded",
        url: String = "$DOWNLOAD/$tag/SHA256SUMS",
    ): String = asset("SHA256SUMS", state, url, 99)

    fun asset(name: String, state: String, url: String, size: Long): String = """
        {"name": ${quote(name)}, "state": ${quote(state)}, "size": $size,
         "content_type": "application/vnd.android.package-archive",
         "browser_download_url": ${quote(url)}}
    """.trimIndent()

    fun list(vararg releases: String): String = releases.joinToString(",", "[", "]")

    fun quote(text: String): String = buildString {
        append('"')
        text.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
}
