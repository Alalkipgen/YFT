package com.alal.yft.download

import android.webkit.CookieManager
import com.alal.yft.core.download.DownloadLinkRenewal
import com.alal.yft.core.download.LinkRenewal
import com.alal.yft.core.download.LinkRenewalRequest
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectProbeResult
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.media.AudioFromVideo
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.FreshLinks
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.Mp3Variants
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.detection.BrowserPageReader
import com.alal.yft.detection.HeadlessIdentity
import com.alal.yft.detection.InstagramHomeSession
import com.alal.yft.detection.PageReread
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.tiktok.TikTokHomeSession
import com.alal.yft.detection.tiktok.TikTokPageSettings
import com.alal.yft.feature.detectedmedia.LookupOwner
import com.alal.yft.feature.downloads.DownloadPlanFactory
import com.alal.yft.feature.downloads.DownloadPlanResult
import com.alal.yft.feature.downloads.DownloadRequest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

/**
 * Track E: the sign-in a renewal's lookup sends, by who looked the video up (E0 §4.5 Q4): the
 * rule of that owner's own lookups, read when it is asked.
 */
interface LookupSessions {
    /** The cookie [owner]'s lookup of [pageUrl] sends now, or null when it sends none. */
    fun cookieFor(owner: LookupOwner, pageUrl: String): String?

    object None : LookupSessions {
        override fun cookieFor(owner: LookupOwner, pageUrl: String): String? = null

        override fun toString(): String = "LookupSessions.None"
    }
}

/**
 * The app's rules: Home sends only TikTok's sign-in (P40 G3, when the owner allows it) or
 * Instagram's (P49); the browser the cookies its cookie store holds for the page, as its tab
 * sends them. Read from YFT's own browser only; never logged or kept.
 */
class AndroidLookupSessions(private val tikTok: TikTokPageSettings) : LookupSessions {
    override fun cookieFor(owner: LookupOwner, pageUrl: String): String? = when (owner) {
        LookupOwner.HOME -> TikTokHomeSession.cookieFor(pageUrl, tikTok)
            ?: InstagramHomeSession.cookieFor(pageUrl)
        LookupOwner.BROWSER ->
            runCatching { CookieManager.getInstance().getCookie(pageUrl) }.getOrNull()
    }?.takeIf(String::isNotBlank)

    override fun toString(): String = "AndroidLookupSessions"
}

/**
 * Track E (E3, E-D8): the quiet page re-reads (P37) of a page no adapter reads, by who looked the
 * video up: the browser's sends the tab's agent and its cookies for the page, as the sheet's
 * re-read does; Home's sends Home's agent and no cookie, as Home's lookup of the page did.
 */
class PageReaders(
    private val browser: BrowserPageReader,
    private val home: BrowserPageReader,
) {
    fun of(owner: LookupOwner): BrowserPageReader = when (owner) {
        LookupOwner.BROWSER -> browser
        LookupOwner.HOME -> home
    }

    override fun toString(): String = "PageReaders"

    companion object {
        /** No re-read (`REREAD=0`): a page no adapter reads gets no fresh link, as in E2. */
        val None = PageReaders(BrowserPageReader.None, BrowserPageReader.None)
    }
}

/**
 * Track E (E2): a fresh link for a download from a site an adapter reads. It looks the page up
 * again as its owner did ([LookupSessions], Home's agent or the browser's; `fresh`, so no
 * remembered answer and no tab), finds the same video (its name and its length within 2 s) and
 * the same quality ([DownloadQualityKey]), makes the same conversion, and plans it under the same
 * task id and file name; a whole file is probed first. E3 (E-D8): a page no adapter reads is read
 * again once, quietly, as its owner read it ([PageReaders]), for the same file (its address
 * under a fresh signature, else its length within 2 s). It declines what is no link failure for
 * that site: a 400 off VK, and "YouTube refused" without a tap unless the phone's network
 * changed since the link was found (E-D5').
 */
class SiteLinkRenewal(
    private val sources: DownloadSources,
    private val coordinator: () -> SiteAdapterCoordinator,
    private val resolver: () -> VariantResolver,
    private val probe: DirectMetadataProbe,
    private val sessions: LookupSessions = LookupSessions.None,
    private val networkChanges: () -> Long = { 0L },
    private val clock: () -> Long = System::currentTimeMillis,
    private val pageReaders: PageReaders = PageReaders.None,
) : DownloadLinkRenewal {
    /** Each fresh link's first words ("Fresh link (403 at 41.0 MB)") until its run goes on. */
    private val pending = ConcurrentHashMap<String, String>()

    override suspend fun renew(request: LinkRenewalRequest): LinkRenewal {
        val source = sources[request.taskId]?.takeIf(DownloadSource::renewable)
            ?: return LinkRenewal.Declined
        if (request.reason == DownloadFailureReason.YOUTUBE_REFUSED && !request.byUser &&
            networkChanges() <= source.networkChanges
        ) {
            return LinkRenewal.Declined
        }
        val answer = if (source.rereadable) {
            // E-D1: a 400 is a link that died on VK only, and VK's pages have an adapter.
            if (request.reason == DownloadFailureReason.HTTP_STATUS) return LinkRenewal.Declined
            val reader = pageReaders.of(source.owner).takeIf { it.rereads > 0 }
                ?: return LinkRenewal.Declined
            reread(request, source, reader)
        } else {
            val lookups = coordinator()
            val identity = lookups.identity(source.pageUrl) ?: return LinkRenewal.Declined
            if (request.reason == DownloadFailureReason.HTTP_STATUS &&
                identity.siteId != VK_SITE_ID
            ) {
                return LinkRenewal.Declined
            }
            lookUp(request, source, lookups)
        }
        if (answer is LinkRenewal.Fresh) {
            sources.renewed(request.taskId)
            pending[request.taskId] = headOf(request)
        }
        return answer
    }

    override fun wentOn(taskId: String, bytesKept: Long, firstBytes: Long) {
        val head = pending.remove(taskId) ?: return
        val how = when {
            bytesKept <= 0 -> "started"
            firstBytes >= bytesKept -> "resumed at ${DownloadEnqueuer.formatSize(bytesKept)}"
            else -> "started over (the file changed)"
        }
        sources.note(taskId, "$head: $how")
    }

    private suspend fun lookUp(
        request: LinkRenewalRequest,
        source: DownloadSource,
        lookups: SiteAdapterCoordinator,
    ): LinkRenewal {
        val pageUrl = source.pageUrl
        val context = BrowserRequestContext(
            pageUrl = pageUrl,
            userAgent = when (source.owner) {
                LookupOwner.HOME -> HeadlessIdentity.USER_AGENT
                LookupOwner.BROWSER -> source.userAgent
            },
            cookie = if (source.session) sessions.cookieFor(source.owner, pageUrl) else null,
        )
        val candidates = when (
            val outcome = lookups.inspect(pageUrl, context, clock(), fresh = true)
        ) {
            is SiteAdapterOutcome.Detected -> outcome.candidates
            is SiteAdapterOutcome.Failed -> return LinkRenewal.Unavailable(
                "the site did not give the video (${words(outcome.reason.name)})",
            )
            SiteAdapterOutcome.NotHandled ->
                return LinkRenewal.Unavailable("the site did not give the video")
        }
        val savable = candidates.filter { it.drmHint != true }
        val video = MediaGroups.pageVideos(savable, adapterSite = true)
            .firstOrNull { group -> group.candidates.any { it.videoId == source.videoId } }
            ?.takeIf { group -> sameLength(source.durationMillis, group.durationMillis) }
            ?: return LinkRenewal.Unavailable(VIDEO_GONE)
        return planned(request, source, video.candidates)
    }

    /**
     * E3 (E-D8): the page read again once, as its owner read it (the browser: the tab's agent
     * and its cookies for the page; Home: Home's agent, no cookie). A page without player data
     * (a notice, a check) or one that does not answer gives no fresh link.
     */
    private suspend fun reread(
        request: LinkRenewalRequest,
        source: DownloadSource,
        reader: BrowserPageReader,
    ): LinkRenewal {
        val agent = when (source.owner) {
            LookupOwner.HOME -> HeadlessIdentity.USER_AGENT
            LookupOwner.BROWSER -> source.userAgent
        }
        val read = try {
            reader.read(source.pageUrl, agent)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            PageReread.Failed
        }
        val candidates = when (read) {
            is PageReread.Found -> read.candidates
            PageReread.NoPlayer -> return LinkRenewal.Unavailable(NO_PLAYER)
            PageReread.Failed -> return LinkRenewal.Unavailable(NOT_READ)
        }
        val videos = MediaGroups.pageVideos(candidates.filter { it.drmHint != true })
        val video = sameVideo(source, videos)
            ?.takeIf { group -> sameLength(source.durationMillis, group.durationMillis) }
            ?: return LinkRenewal.Unavailable(VIDEO_GONE)
        return planned(request, source, video.candidates)
    }

    /**
     * P29's rule on the page read again: the video with the download's file under any signature
     * (the same address without its query), else the one of its length, the closest within 2 s.
     */
    private fun sameVideo(source: DownloadSource, videos: List<MediaGroup>): MediaGroup? {
        val path = source.mediaPath ?: return null
        videos.firstOrNull { video -> video.candidates.any { it.unsignedPath == path } }
            ?.let { return it }
        val length = source.durationMillis ?: return null
        return videos
            .mapNotNull { video -> video.durationMillis?.let { video to abs(it - length) } }
            .filter { (_, gap) -> gap <= SAME_LENGTH_MILLIS }
            .minByOrNull { (_, gap) -> gap }
            ?.first
    }

    /**
     * The fresh [candidates]' file of the download's quality, converted as before and planned
     * under the same task id and file name; a whole file is probed first.
     */
    private suspend fun planned(
        request: LinkRenewalRequest,
        source: DownloadSource,
        candidates: List<MediaCandidate>,
    ): LinkRenewal {
        val match = sameQuality(source, candidates)
            ?: return LinkRenewal.Unavailable(QUALITY_GONE)
        val variant = converted(source, match.variant, match.asset.durationMillis)
            ?.let { converted -> converted.copy(label = source.label ?: converted.label) }
            ?: return LinkRenewal.Unavailable(QUALITY_GONE)
        val asset = match.asset.copy(title = source.title ?: match.asset.title)
        val plan = when (
            val planned = DownloadPlanFactory.create(asset, variant, request.taskId, clock())
        ) {
            is DownloadPlanResult.Rejected -> return LinkRenewal.Unavailable(
                "the fresh file cannot be downloaded (${words(planned.reason.name)})",
            )
            is DownloadPlanResult.Ready -> planned.request.planNamed(source.fileName)
        }
        if (plan !is DirectDownloadPlan) return LinkRenewal.Fresh(plan)
        return when (val probed = probeSafely(plan)) {
            is DirectProbeResult.Success -> LinkRenewal.Fresh(plan, probed.metadata)
            is DirectProbeResult.Failure -> LinkRenewal.Unavailable(
                "the fresh file did not answer (${failureWords(probed.failure)})",
            )
        }
    }

    /**
     * The fresh lookup's file of the download's quality: its candidates that may hold it, the
     * closest first (on a page read again, the download's own file first), are resolved (at
     * most [MAX_RESOLVES]) until one has a variant no stated fact of which differs; of those the
     * one sharing the most facts, then the one of the video's length, then the nearest bitrate.
     */
    private suspend fun sameQuality(
        source: DownloadSource,
        candidates: List<MediaCandidate>,
    ): Match? {
        val key = source.quality
        val ranked = candidates
            .filter { key.admits(it, source.candidateKind) }
            .sortedWith(
                compareByDescending<MediaCandidate> { candidate ->
                    source.mediaPath != null && candidate.unsignedPath == source.mediaPath
                }
                    .thenByDescending { key.sharedWith(it) }
                    .thenBy { key.bitrateGap(it.bitrateBitsPerSecond) },
            )
        for (candidate in ranked.take(MAX_RESOLVES)) {
            val asset = (resolveSafely(candidate) as? VariantResolutionResult.Success)?.asset
                ?: continue
            val label = DownloadQualityKey.labelOf(candidate)
            val best = asset.variants
                .filter { it.isPreviewable && it.mp3 == null && !it.audioFromVideo }
                .map { it.withFactsOf(candidate) }
                .map { variant -> variant to DownloadQualityKey.of(variant, label) }
                .filter { (_, fresh) -> key.accepts(fresh) }
                .sortedWith(
                    compareByDescending<Pair<MediaVariant, DownloadQualityKey>> { (_, fresh) ->
                        key.shared(fresh)
                    }
                        .thenByDescending { (variant, _) ->
                            val length = variant.durationMillis ?: asset.durationMillis
                            length != null && source.durationMillis != null &&
                                sameLength(source.durationMillis, length)
                        }
                        .thenBy { (_, fresh) -> key.bitrateGap(fresh.bitrateBitsPerSecond) },
                )
                .firstOrNull()
                ?.first
                ?: continue
            return Match(asset, best)
        }
        return null
    }

    private suspend fun resolveSafely(candidate: MediaCandidate): VariantResolutionResult? = try {
        resolver().resolve(candidate)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private suspend fun probeSafely(plan: DirectDownloadPlan): DirectProbeResult = try {
        probe.probe(plan)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DirectProbeResult.Failure(DownloadFailure(DownloadFailureReason.NETWORK))
    }

    private data class Match(val asset: MediaAsset, val variant: MediaVariant) {
        override fun toString(): String = "Match(variant=$variant)"
    }

    companion object {
        internal const val VIDEO_GONE = "the video is no longer on the page"
        internal const val QUALITY_GONE = "this quality is no longer offered"
        internal const val NO_PLAYER =
            "the page read again has no player data (a notice or a check)"
        internal const val NOT_READ = "the page could not be read again"

        /** A site adapter's own name for VK (`VkUrls.SITE_ID`): its 400 is a link that died. */
        private const val VK_SITE_ID = "vk"

        /** A fresh lookup tries the closest few files; more would only cost requests. */
        private const val MAX_RESOLVES = 3

        /** Two lengths of one video, as two lookups state them (P24's rule). */
        private const val SAME_LENGTH_MILLIS = 2_000L

        private fun sameLength(first: Long?, second: Long?): Boolean =
            first == null || second == null || abs(first - second) <= SAME_LENGTH_MILLIS

        /** The file's address without its signature (its query and fragment). */
        private val MediaCandidate.unsignedPath: String
            get() = FreshLinks.unsigned(mediaUrl)

        /** The download's conversion on the fresh file, as the sheet makes it. */
        internal fun converted(
            source: DownloadSource,
            file: MediaVariant,
            assetDurationMillis: Long?,
        ): MediaVariant? {
            val sound = if (source.conversion.audioFromVideo) {
                AudioFromVideo.of(file, assetDurationMillis) ?: return null
            } else {
                file
            }
            val kbps = source.conversion.mp3Kbps ?: return sound
            return Mp3Variants.of(sound, kbps, assetDurationMillis)
        }

        /** The facts the candidate states and its file does not, as the sheet adds them. */
        private fun MediaVariant.withFactsOf(candidate: MediaCandidate): MediaVariant = copy(
            width = width ?: candidate.width?.takeIf { it > 0 },
            height = height ?: candidate.height?.takeIf { it > 0 },
            framesPerSecond = framesPerSecond ?: candidate.framesPerSecond?.takeIf { it > 0 },
            bitrateBitsPerSecond = bitrateBitsPerSecond
                ?: candidate.bitrateBitsPerSecond?.takeIf { it > 0 },
        )

        /** The plan under the name the download is saved as, as the enqueue gave it. */
        private fun DownloadRequest.planNamed(fileName: String): DownloadPlan = when (this) {
            is DownloadRequest.Direct -> plan.copy(suggestedFileName = fileName)
            is DownloadRequest.Hls -> plan.copy(suggestedFileName = fileName)
            is DownloadRequest.Dash -> plan.copy(suggestedFileName = fileName)
            is DownloadRequest.Mux -> plan.copy(suggestedFileName = fileName)
        }

        /** "Fresh link (403 at 41.0 MB)": what ended the link and where; a tap's says so. */
        internal fun headOf(request: LinkRenewalRequest): String {
            val what = request.httpStatusCode?.toString() ?: when (request.reason) {
                DownloadFailureReason.EXPIRED_URL -> "link expired"
                DownloadFailureReason.YOUTUBE_REFUSED -> "YouTube refused"
                else -> words(request.reason.name)
            }
            val where = request.bytesKept.takeIf { it > 0 }
                ?.let { " at ${DownloadEnqueuer.formatSize(it)}" }
                .orEmpty()
            val name = if (request.byUser) "Fresh link on Retry" else "Fresh link"
            return "$name ($what$where)"
        }

        private fun failureWords(failure: DownloadFailure): String =
            failure.httpStatusCode?.let { "HTTP $it" } ?: words(failure.reason.name)

        /** NO_MEDIA_FOUND → "no media found". */
        private fun words(name: String): String = name.lowercase(Locale.US).replace('_', ' ')
    }
}
