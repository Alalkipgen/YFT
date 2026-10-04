package com.alal.yft.core.model.settings

/** A site shortcut on Home ("Your sites"): a short name shown under its initial and an address. */
data class HomeSite(
    val name: String,
    val url: String,
) {
    /** The letter drawn in the site's circle. */
    val initial: String
        get() = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
}

/** Limits, first-run shortcuts and the stored form of [HomeSite] lists. */
object HomeSites {
    const val MAX_SITES = 12
    const val MAX_NAME_LENGTH = 24
    const val MAX_URL_LENGTH = 2_048

    /** Shown until the user changes the list. */
    val DEFAULTS: List<HomeSite> = listOf(
        HomeSite("YouTube", "https://m.youtube.com"),
        HomeSite("Facebook", "https://m.facebook.com"),
        HomeSite("TikTok", "https://www.tiktok.com"),
    )

    /** Version of [DEFAULTS]; lists stored by an older version go through [migrateDefaults]. */
    const val DEFAULTS_VERSION = 2

    /** The first-run sites of 1.0.0-beta.1 and beta.2 (version 1). */
    val LEGACY_DEFAULTS: List<HomeSite> = listOf(
        HomeSite("Archive", "https://archive.org"),
        HomeSite("Wikimedia", "https://commons.wikimedia.org"),
        HomeSite("NASA", "https://images.nasa.gov"),
    )

    /**
     * Moves a stored list from the version 1 defaults to [DEFAULTS]. A list that still holds a
     * version 1 default loses those entries and gets the new defaults first, in their order; a
     * site of the same brand the user added takes its default's place, so nothing is doubled.
     * Every other site the user added is kept, and defaults are only added while there is room
     * under [MAX_SITES]. A list without any version 1 default, including an empty one, is the
     * user's own and stays as it is.
     */
    fun migrateDefaults(stored: List<HomeSite>): List<HomeSite> {
        if (stored.none { it in LEGACY_DEFAULTS }) return stored
        val kept = stored.filterNot { it in LEGACY_DEFAULTS }
        var room = MAX_SITES - kept.size
        val front = DEFAULTS.mapNotNull { default ->
            val brand = SiteBrand.of(default.url)
            kept.firstOrNull { brand != null && SiteBrand.of(it.url) == brand }
                ?: default.takeIf { room > 0 }?.also { room-- }
        }
        return (front + kept).distinctBy { it.url }.take(MAX_SITES)
    }

    /** Collapses whitespace and drops control characters so a name stays on one short line. */
    fun cleanName(name: String): String = name
        .replace(WHITESPACE, " ")
        .filterNot { it.isISOControl() }
        .replace(WHITESPACE, " ")
        .trim()
        .take(MAX_NAME_LENGTH)
        .trim()

    /**
     * One site per line as `name<TAB>url`. Names never contain tabs or line breaks after
     * [cleanName], and addresses are checked URLs without whitespace, so no escaping is needed.
     */
    fun encode(sites: List<HomeSite>): String =
        sites.take(MAX_SITES).joinToString(separator = "\n") { "${it.name}\t${it.url}" }

    /** Reads [encode]'s form, skipping lines that are damaged or would not pass [cleanName]. */
    fun decode(value: String): List<HomeSite> = value
        .lineSequence()
        .mapNotNull { line ->
            val name = line.substringBefore('\t', missingDelimiterValue = "")
            val url = line.substringAfter('\t', missingDelimiterValue = "")
            val cleaned = cleanName(name)
            if (cleaned.isEmpty() || cleaned != name) return@mapNotNull null
            if (!url.startsWith("https://") || url.length > MAX_URL_LENGTH) return@mapNotNull null
            if (url.any { it.isWhitespace() }) return@mapNotNull null
            HomeSite(name = cleaned, url = url)
        }
        .distinctBy { it.url }
        .take(MAX_SITES)
        .toList()

    private val WHITESPACE = Regex("""\s+""")
}
