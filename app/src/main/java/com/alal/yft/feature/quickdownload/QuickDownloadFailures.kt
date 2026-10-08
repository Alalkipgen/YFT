package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.FreshLinks
import com.alal.yft.core.model.media.LinkOrigin
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.ResolutionStep
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import java.io.IOException
import java.net.MalformedURLException
import java.net.URI
import java.net.UnknownServiceException

/**
 * P24: what the sheet says when a video cannot be prepared, and its Details.
 *
 * The message follows what went wrong, so a list of qualities the app could not read never says
 * the media could not be reached, and a site that refused the video says so with its status.
 * Details name the step, the host and the HTTP status; never a path, a query or a cookie.
 */
internal object QuickDownloadFailures {
    /** An exception a resolver threw, by its type: only a network problem is NETWORK. */
    fun reasonOf(error: Exception): VariantResolutionFailure = when (error) {
        is MalformedURLException, is UnknownServiceException -> VariantResolutionFailure.INVALID_URL
        is IOException -> VariantResolutionFailure.NETWORK
        else -> VariantResolutionFailure.MALFORMED_MANIFEST
    }

    /** The host of [url] for Details, or null for an address without one (`blob:`, `data:`). */
    fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.takeIf(String::isNotBlank)?.lowercase()

    fun message(failure: VariantResolutionResult.Failure): String {
        val status = failure.httpStatusCode
        return when {
            status == null -> message(failure.reason)
            status == 401 -> "The site asks you to sign in for this video (HTTP 401)."
            status == 403 -> "$REFUSED (HTTP 403)."
            status == 404 || status == 410 -> "The site no longer has this video (HTTP $status)."
            status == 429 -> "The site is busy (HTTP 429). Try again in a minute."
            status >= 500 -> "The site had a problem (HTTP $status). Try again later."
            else -> "The site answered HTTP $status. Try again or pick another format."
        }
    }

    fun message(reason: VariantResolutionFailure?): String = when (reason) {
        VariantResolutionFailure.EXPIRED_URL ->
            "This link has expired. Open the page again."
        VariantResolutionFailure.DRM_PROTECTED -> "Protected media (DRM) can't be saved."
        VariantResolutionFailure.NETWORK ->
            "The media could not be reached. Check the connection and try again."
        VariantResolutionFailure.UNSUPPORTED_CODEC, null ->
            "This version can't be saved. Try another format."
        VariantResolutionFailure.MALFORMED_MANIFEST ->
            "The site's list of qualities could not be read."
        VariantResolutionFailure.MANIFEST_TOO_LARGE ->
            "The site's list of qualities is too large to read."
        VariantResolutionFailure.NO_VARIANTS ->
            "The site's list of qualities has nothing YFT can save."
        VariantResolutionFailure.INVALID_URL ->
            "This video's address can't be downloaded. Try another video on the page."
        VariantResolutionFailure.UNSAFE_REDIRECT ->
            "The site sent this video to an unsafe address, so YFT stopped."
        VariantResolutionFailure.TOO_MANY_REDIRECTS ->
            "The site redirected this video too many times."
        VariantResolutionFailure.HTTP_STATUS ->
            "This version could not be prepared. Try again or pick another format."
    }

    /** The Details lines: which request failed, where and how. */
    fun details(failure: VariantResolutionResult.Failure): List<String> = buildList {
        failure.step?.let { add("Step: ${stepName(it)}") }
        failure.host?.let { add("Host: $it") }
        add("Status: ${failure.httpStatusCode?.let { "HTTP $it" } ?: reasonName(failure.reason)}")
    }

    /**
     * P37: where a link came from, how old it is and whether its own expiry time passed, for
     * every attempt's Details (FIX_ADD_PLAN R17): "Link from: page script", "Link age: 12 min",
     * "Link expiry: passed". Never the address or a query value. [origin] overrides what the
     * candidate's sources say.
     */
    fun linkLines(
        candidate: MediaCandidate,
        nowMillis: Long,
        origin: LinkOrigin = FreshLinks.origin(candidate),
    ): List<String> = listOf(
        "Link from: ${origin.label}",
        "Link age: ${age(candidate.observedAtEpochMs, nowMillis)}",
        "Link expiry: ${FreshLinks.expiry(candidate, nowMillis).label}",
    )

    /** "under 1 min", "12 min", "2 h 5 min"; "unknown" when the time it was seen is not known. */
    fun age(seenAtMillis: Long, nowMillis: Long): String {
        if (seenAtMillis <= 0L || seenAtMillis > nowMillis + CLOCK_SLACK_MILLIS) return "unknown"
        val minutes = (nowMillis - seenAtMillis).coerceAtLeast(0L) / MILLIS_PER_MINUTE
        return when {
            minutes < 1 -> "under 1 min"
            minutes < MINUTES_PER_HOUR -> "$minutes min"
            minutes % MINUTES_PER_HOUR == 0L -> "${minutes / MINUTES_PER_HOUR} h"
            else -> "${minutes / MINUTES_PER_HOUR} h ${minutes % MINUTES_PER_HOUR} min"
        }
    }

    private fun stepName(step: ResolutionStep): String = when (step) {
        ResolutionStep.ADDRESS -> "video address"
        ResolutionStep.FILE_CHECK -> "file check"
        ResolutionStep.MANIFEST -> "list of qualities (manifest)"
        ResolutionStep.MEDIA_PLAYLIST -> "quality playlist"
        ResolutionStep.PREPARE -> "preparing the qualities"
    }

    private fun reasonName(reason: VariantResolutionFailure): String = when (reason) {
        VariantResolutionFailure.INVALID_URL -> "address not supported"
        VariantResolutionFailure.EXPIRED_URL -> "link expired"
        VariantResolutionFailure.DRM_PROTECTED -> "protected (DRM)"
        VariantResolutionFailure.UNSUPPORTED_CODEC -> "format not supported"
        VariantResolutionFailure.MALFORMED_MANIFEST -> "manifest not readable"
        VariantResolutionFailure.MANIFEST_TOO_LARGE -> "manifest too large"
        VariantResolutionFailure.UNSAFE_REDIRECT -> "unsafe redirect"
        VariantResolutionFailure.TOO_MANY_REDIRECTS -> "too many redirects"
        VariantResolutionFailure.HTTP_STATUS -> "HTTP error"
        VariantResolutionFailure.NETWORK -> "no answer (network)"
        VariantResolutionFailure.NO_VARIANTS -> "no qualities"
    }

    /** The words a 403 starts with. */
    const val REFUSED = "The site refused this video"

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L
    private const val CLOCK_SLACK_MILLIS = 60_000L
}
