package com.alal.yft.core.model.media

import kotlin.math.abs

/**
 * P43 (FIX_ADD_PLAN G8): how strictly a file on a page without an adapter must prove that it is
 * the page's own video before the download sheet offers it as the main one.
 */
enum class AdRule {
    /**
     * The owner's choice: a file of unknown length is measured first; when the page states a
     * length only a file of that length is offered; a file still of unknown length on a page with
     * ad signs is skipped; when nothing passes no ad is offered ("Reload page and try again").
     */
    STRICT,

    /** Today's rule (the first choice stays the browser's) plus the measured length checks. */
    LENIENT,
}

/** P43: why a file is the page's own video. */
enum class PageVideoReason {
    /** A site adapter named it: its own choice stands. */
    ADAPTER,

    /** The page's player setup or its markup names it ([PageMediaRole.MAIN], P28's keys). */
    NAMED,

    /** Its length matches the length the page states. */
    PAGE_LENGTH,

    /** Its length matches the length of the video whose link failed. */
    FAILED_LENGTH,
}

/** P43: why a file is an ad, or at least not the page's video. */
enum class AdReason(val label: String) {
    AD_HOST("ad host"),
    AD_ADDRESS("ad address"),
    AD_BREAK("ad break"),
    MARKED("marked as an ad"),
    SHORT("short"),

    /** Its length is not the one the page states; [AdRule.STRICT] offers only that one. */
    LENGTH("not the page's length"),
}

/** P43: what [PageVideoProof] says about one video. */
sealed interface VideoProof {
    /** The length the verdict used; null when it is unknown. */
    val lengthMillis: Long?

    data class PageVideo(
        val reasons: List<PageVideoReason>,
        override val lengthMillis: Long?,
    ) : VideoProof

    data class Ad(val reason: AdReason, override val lengthMillis: Long?) : VideoProof

    data class NotProven(override val lengthMillis: Long?) : VideoProof
}

/**
 * P43 (R34): the one rule for "is this the page's video?" on a site without an adapter, used by
 * the sheet's first choice and by every step of its fresh-link chain (the page's newest link,
 * the player's link, the page read again, the next video). Pure, so the rules are unit-tested;
 * its Details lines name lengths and reasons, never addresses.
 *
 * A video is the page's own when the page's player setup or markup names it, when its length
 * matches the length the page states (within 5 s or 5 %), or the length of the video whose link
 * failed — never when its known length contradicts the length the page states. It is an ad when
 * an ad network serves it, the page's player fetched it inside an ad break, it is marked as an
 * ad, or it is at most 60 s long while the page states (or its named video has) a length more
 * than twice as long. Otherwise nothing proves it ([VideoProof.NotProven]).
 */
data class PageVideoProof(
    /** What the page states about its video: its length above all. */
    val facts: PageVideoFacts? = null,
    /** The length of the video whose link failed; null when unknown or when it was an ad. */
    val failedLengthMillis: Long? = null,
    /** The longest known length of a video the page's player setup names. */
    val namedLengthMillis: Long? = null,
    /** Whether the page shows any sign of ads ([hasAdSigns]). */
    val adSigns: Boolean = false,
    val rule: AdRule = DEFAULT_RULE,
) {
    /** The verdict on [video] when its length is [lengthMillis] (measured, or as stated). */
    fun of(video: MediaGroup, lengthMillis: Long? = video.durationMillis): VideoProof {
        val files = video.candidates
        val length = lengthMillis?.takeIf { it > 0 }
        if (files.any { it.videoId != null }) {
            return VideoProof.PageVideo(listOf(PageVideoReason.ADAPTER), length)
        }
        // An ad network's file is an ad, whatever else the page says about it.
        files.firstNotNullOfOrNull { file -> file.adSign?.takeIf { it != AdSign.AD_BREAK } }
            ?.let { sign -> return VideoProof.Ad(reasonOf(sign), length) }
        val stated = facts?.durationMillis
        val contradicted = stated != null && length != null && !sameLength(stated, length)
        val reasons = buildList {
            if (files.any(::isNamed)) add(PageVideoReason.NAMED)
            if (stated != null && length != null && !contradicted) {
                add(PageVideoReason.PAGE_LENGTH)
            }
            val failed = failedLengthMillis
            if (stated == null && failed != null && length != null && sameLength(failed, length)) {
                add(PageVideoReason.FAILED_LENGTH)
            }
        }
        if (!contradicted && reasons.isNotEmpty()) return VideoProof.PageVideo(reasons, length)
        if (files.any { it.adSign == AdSign.AD_BREAK }) {
            return VideoProof.Ad(AdReason.AD_BREAK, length)
        }
        if (files.all { it.pageRole == PageMediaRole.PREVIEW }) {
            return VideoProof.Ad(AdReason.MARKED, length)
        }
        val longer = stated ?: namedLengthMillis ?: failedLengthMillis
        if (length != null && length <= SHORT_AD_MILLIS && longer != null && longer > length * 2) {
            return VideoProof.Ad(AdReason.SHORT, length)
        }
        if (contradicted) return VideoProof.Ad(AdReason.LENGTH, length)
        return VideoProof.NotProven(length)
    }

    /**
     * Whether the sheet may offer a video of this [proof] as the main one. [AdRule.LENIENT]
     * still offers a video whose length only differs from the page's, and any video nothing
     * proves; [AdRule.STRICT] skips a video still of unknown length on a page with ad signs.
     */
    fun offers(proof: VideoProof): Boolean = when (proof) {
        is VideoProof.PageVideo -> true
        is VideoProof.Ad -> rule == AdRule.LENIENT && proof.reason == AdReason.LENGTH
        is VideoProof.NotProven -> rule == AdRule.LENIENT || !adSigns || proof.lengthMillis != null
    }

    /** The Details lines of an offered video: why it is taken for the page's. */
    fun chosenLines(proof: VideoProof): List<String> {
        val length = proof.lengthMillis
        return when (proof) {
            is VideoProof.PageVideo -> proof.reasons.mapNotNull { reason ->
                when (reason) {
                    PageVideoReason.ADAPTER -> null
                    PageVideoReason.NAMED -> "chosen: named by the page's player"
                    PageVideoReason.PAGE_LENGTH ->
                        "length ${clock(length)} matches the page (${clock(facts?.durationMillis)})"
                    PageVideoReason.FAILED_LENGTH ->
                        "length ${clock(length)} matches the first video " +
                            "(${clock(failedLengthMillis)})"
                }
            }
            is VideoProof.Ad -> listOf(
                "chosen: ${clock(length)}, the page states ${clock(facts?.durationMillis)}",
            )
            is VideoProof.NotProven -> listOf(
                if (length == null) "chosen: length unknown, no ad sign" else
                    "chosen: ${clock(length)}, no ad sign",
            )
        }
    }

    /** The Details line of a video the sheet skipped. */
    fun skippedLine(proof: VideoProof): String {
        val length = proof.lengthMillis
        return when {
            proof is VideoProof.Ad && proof.reason == AdReason.LENGTH ->
                "skipped: ${clock(length)}, not the page's length (${clock(facts?.durationMillis)})"
            proof is VideoProof.Ad ->
                "skipped: ${length?.let { "${clock(it)} ad" } ?: "ad"} (${proof.reason.label})"
            length == null -> "skipped: length unknown"
            else -> "skipped: ${clock(length)}, not proven"
        }
    }

    companion object {
        /** The owner's answer to G8: none, so the plan's default. */
        val DEFAULT_RULE: AdRule = AdRule.STRICT

        /** An ad is at most a minute long. */
        const val SHORT_AD_MILLIS = 60_000L
        private const val SLACK_MILLIS = 5_000L
        private const val SLACK_PERCENT = 5L
        private const val PERCENT = 100L
        private const val SECONDS_PER_MINUTE = 60L
        private const val SECONDS_PER_HOUR = 3_600L
        private const val MILLIS_PER_SECOND = 1_000L

        /** The ad signs that only ads get: the page's previews of its videos are not ads. */
        private val AD_ONLY = setOf(AdReason.AD_HOST, AdReason.AD_ADDRESS, AdReason.AD_BREAK)

        /**
         * The rule for a page of [candidates] that states [facts], after [failed]'s link failed
         * (null when none did, or when it was itself an ad). [sawAd] adds what the sheet itself
         * measured: a file it skipped as an ad is an ad sign of the page.
         */
        fun forPage(
            candidates: List<MediaCandidate>,
            facts: PageVideoFacts?,
            failed: MediaGroup? = null,
            rule: AdRule = DEFAULT_RULE,
            sawAd: Boolean = false,
        ): PageVideoProof {
            val named = MediaGroups.of(candidates).filter { it.candidates.any(::isNamed) }
            return PageVideoProof(
                facts = facts,
                failedLengthMillis = failed?.durationMillis,
                namedLengthMillis = named.mapNotNull { it.durationMillis }.maxOrNull(),
                adSigns = sawAd || hasAdSigns(candidates, facts),
                rule = rule,
            )
        }

        /**
         * Whether a page of [candidates] shows a sign of ads: a file marked as an ad or a preview
         * ([PageMediaRole.PREVIEW]), a file an [AdSign] marks, or a file whose known length misses
         * the length the page states. A site adapter's files are never one.
         */
        fun hasAdSigns(candidates: List<MediaCandidate>, facts: PageVideoFacts?): Boolean {
            val stated = facts?.durationMillis
            return candidates.any { file ->
                file.videoId == null && (
                    file.pageRole == PageMediaRole.PREVIEW || file.adSign != null ||
                        stated != null && file.durationMillis != null &&
                        !sameLength(stated, file.durationMillis)
                    )
            }
        }

        /**
         * Whether [video] is proven an ad by a sign only ads get (an ad network, an ad address,
         * an ad break), so no list of the page's videos shows it.
         */
        fun isProvenAd(video: MediaGroup, facts: PageVideoFacts? = null): Boolean {
            val proof = PageVideoProof(facts = facts).of(video)
            return proof is VideoProof.Ad && proof.reason in AD_ONLY
        }

        /** Whether [length] is the length [expected]: within 5 s or 5 % of it. */
        fun sameLength(expected: Long, length: Long?): Boolean {
            val actual = length?.takeIf { it > 0 } ?: return false
            val slack = maxOf(SLACK_MILLIS, expected * SLACK_PERCENT / PERCENT)
            return abs(expected - actual) <= slack
        }

        /** A length as a clock shows it: `0:30`, `10:05`, `1:02:03`; `?` when unknown. */
        fun clock(millis: Long?): String {
            val seconds = millis?.takeIf { it >= 0 }?.let { it / MILLIS_PER_SECOND } ?: return "?"
            val hours = seconds / SECONDS_PER_HOUR
            val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
            val rest = seconds % SECONDS_PER_MINUTE
            val tail = twoDigits(rest)
            return if (hours > 0) "$hours:${twoDigits(minutes)}:$tail" else "$minutes:$tail"
        }

        private fun twoDigits(value: Long): String = value.toString().padStart(2, '0')

        /** P28: the page's player setup or its markup names this file as its video. */
        private fun isNamed(file: MediaCandidate): Boolean =
            file.pageVideoKey != null || file.pageRole == PageMediaRole.MAIN

        private fun reasonOf(sign: AdSign): AdReason = when (sign) {
            AdSign.AD_HOST -> AdReason.AD_HOST
            AdSign.AD_ADDRESS -> AdReason.AD_ADDRESS
            AdSign.AD_BREAK -> AdReason.AD_BREAK
        }
    }
}
