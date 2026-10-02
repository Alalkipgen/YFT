package com.alal.yft.extractor.api

/** Per-adapter kill switch so one failing site can be turned off without shipping new code paths. */
fun interface SiteAdapterFlags {
    fun isEnabled(adapterId: String): Boolean

    companion object {
        val AllEnabled = SiteAdapterFlags { true }
    }
}

/**
 * Chooses at most one adapter for a page and otherwise defers to the generic detector.
 *
 * Selection is offline and order-independent: adapter IDs must be unique and at most one adapter
 * may claim a URL, so two adapters cannot silently fight over the same site.
 */
class SiteExtractorRegistry(
    extractors: List<SiteExtractor>,
    private val flags: SiteAdapterFlags = SiteAdapterFlags.AllEnabled,
) {
    private val extractors: List<SiteExtractor> = extractors.toList()

    init {
        val ids = this.extractors.map(SiteExtractor::id)
        require(ids.none(String::isBlank)) { "Adapter IDs must not be blank" }
        require(ids.distinct().size == ids.size) { "Adapter IDs must be unique" }
    }

    val adapterIds: List<String>
        get() = extractors.map(SiteExtractor::id)

    /** Adapters that are compiled in but currently switched off. */
    fun disabledAdapterIds(): List<String> =
        adapterIds.filterNot(flags::isEnabled)

    /**
     * Resolves the adapter for [pageUrl].
     *
     * Returns [SiteAdapterSelection.None] when no adapter claims the page, which is the normal
     * path into the generic detector rather than an error.
     */
    fun select(pageUrl: String): SiteAdapterSelection {
        val matches = extractors.mapNotNull { extractor ->
            extractor.identify(pageUrl)?.let { identity -> extractor to identity }
        }
        require(matches.size <= 1) {
            "Multiple adapters claimed the same page: ${matches.map { it.first.id }}"
        }
        val (extractor, identity) = matches.firstOrNull() ?: return SiteAdapterSelection.None
        return if (flags.isEnabled(extractor.id)) {
            SiteAdapterSelection.Matched(extractor, identity)
        } else {
            SiteAdapterSelection.Disabled(extractor.id)
        }
    }
}

sealed interface SiteAdapterSelection {
    /** No adapter handles this page; use the generic detector. */
    data object None : SiteAdapterSelection

    data class Disabled(val adapterId: String) : SiteAdapterSelection

    data class Matched(
        val extractor: SiteExtractor,
        val identity: SitePageIdentity,
    ) : SiteAdapterSelection
}
