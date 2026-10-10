package com.alal.yft.detection.master

import android.os.Build
import com.alal.yft.core.download.AudioVideoMuxCompatibility
import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.detection.DeviceMergeSupport
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.SiteScope
import com.alal.yft.detection.platformHasDecoder
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.android.CodecSteering
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.extractor.master.verify.OkHttpMediaValidator
import com.alal.yft.extractor.master.verify.CapturedMediaMetadata
import com.alal.yft.extractor.master.present.MasterMainPresentation
import com.alal.yft.extractor.master.policy.TerminalRules
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Browser-only, outside the site registry. Home/pasted-link extraction remains unchanged. */
interface BrowserMasterFallback {
    val enabled: Boolean
    val capture: WebViewPlaybackCapture? get() = null
    fun mainAndMore(candidates: List<MediaCandidate>): MasterMainPresentation? = null

    suspend fun recover(
        lookupUrl: String,
        primary: SiteAdapterOutcome,
        nowEpochMs: Long,
        genericOnDemand: Boolean = false,
    ): SiteAdapterOutcome

    data object None : BrowserMasterFallback {
        override val enabled = false
        override suspend fun recover(
            lookupUrl: String,
            primary: SiteAdapterOutcome,
            nowEpochMs: Long,
            genericOnDemand: Boolean,
        ): SiteAdapterOutcome = primary
    }
}

class AndroidBrowserMasterFallback(
    override val capture: WebViewPlaybackCapture,
    private val sites: SiteAdapterCoordinator,
    private val engine: MasterFallbackEngine,
    private val mergeSupport: MergeSupport = DeviceMergeSupport(),
    private val requestFactory: suspend (SiteExtractionFailure, Long, String?) -> MasterRequest? =
        capture::request,
) : BrowserMasterFallback {
    override val enabled: Boolean get() = capture.enabled
    override fun mainAndMore(candidates: List<MediaCandidate>): MasterMainPresentation? =
        capture.mainAndMore(candidates)

    override suspend fun recover(
        lookupUrl: String,
        primary: SiteAdapterOutcome,
        nowEpochMs: Long,
        genericOnDemand: Boolean,
    ): SiteAdapterOutcome {
        // Successful/disabled primary paths never sample the page or launch another probe.
        if (!enabled || primary is SiteAdapterOutcome.Detected) return primary
        if (primary === SiteAdapterOutcome.NotHandled && !genericOnDemand) return primary
        if (!sites.allowsMasterFallback(lookupUrl)) return primary
        val failure = (primary as? SiteAdapterOutcome.Failed)?.reason
            ?: SiteExtractionFailure.NO_MEDIA_FOUND
        // R2: a walled site's bot check, login or player-script answer is final, before capture.
        if (TerminalRules.blocksFallback(failure, lookupUrl)) return primary
        val key = sites.videoKey(lookupUrl)
        val request = requestFactory(failure, nowEpochMs, key?.substringAfter(':'))
            ?: return primary
        if (TerminalRules.blocksFallback(failure, request.pageUrl) ||
            !sites.allowsMasterFallback(request.pageUrl) ||
            !relatedPage(lookupUrl, request.pageUrl, key)
        ) return primary
        val result = try {
            // Fresh one-shot capture avoids a cached passive snapshot masking changed playback.
            withContext(Dispatchers.Default) { engine.extract(request.copy(snapshot = null)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return failed(primary, failure, PLAY_FIRST, listOf("master: capture unavailable"))
        }
        val scope = capture.session.currentScope()
        if (scope == null || scope.generation != request.generation ||
            scope.pageUrl != request.pageUrl
        ) return primary
        return when (result) {
            is MasterResult.Success -> {
                val focused = capture.appCandidates(result.result.candidates, key)
                    ?: result.result.candidates.filter {
                    key == null || it.videoId == key
                }
                val kept = focused.filter(mergeSupport::canMerge)
                when {
                    focused.isEmpty() -> failed(
                        primary, failure, PLAY_FIRST,
                        result.result.details + "master: focused content identity required",
                    )
                    kept.isEmpty() -> failed(
                        primary, SiteExtractionFailure.NO_MEDIA_FOUND,
                        "This phone cannot merge the captured tracks.",
                        result.result.details + "master: device merge gate rejected tracks",
                    )
                    else -> SiteAdapterOutcome.Detected(
                        (primary as? SiteAdapterOutcome.Failed)?.adapterId ?: "master",
                        kept,
                    )
                }
            }
            is MasterResult.NeedsPlayback ->
                failed(primary, failure, PLAY_FIRST, result.details)
            is MasterResult.NeedsSelection -> failed(
                primary, failure,
                "Play only the video you want, then tap Try again.",
                result.details,
            )
            is MasterResult.Failure -> failed(
                primary, result.reason,
                if (result.reason == SiteExtractionFailure.DRM_PROTECTED) {
                    "Protected media cannot be captured."
                } else {
                    "No verified media was captured. Play the video and tap Try again."
                },
                result.details,
            )
            is MasterResult.Skipped -> primary
        }
    }

    private fun relatedPage(lookup: String, page: String, requestedKey: String?): Boolean {
        if (lookup == page || sites.sameContent(lookup, page)) return true
        // A feed's focused permalink can share its tab, but a different named post cannot.
        if (requestedKey == null || sites.videoKey(page) != null) return false
        val first = runCatching { URI(lookup).host }.getOrNull() ?: return false
        val second = runCatching { URI(page).host }.getOrNull() ?: return false
        return SiteScope.sameSite(first, second)
    }

    private fun failed(
        primary: SiteAdapterOutcome,
        reason: SiteExtractionFailure,
        message: String,
        details: List<String>,
    ): SiteAdapterOutcome.Failed {
        val old = primary as? SiteAdapterOutcome.Failed
        return SiteAdapterOutcome.Failed(
            adapterId = old?.adapterId ?: "master",
            reason = reason,
            message = message,
            allowsGenericFallback = old?.allowsGenericFallback ?: true,
            details = DiagnosticTextSanitizer.details(old?.details.orEmpty() + details),
        )
    }

    companion object {
        /**
         * R5: the page's player is steered to what [DeviceMergeSupport] merges — VP9/Opus WebM
         * from Android 10, AV1 only while its merges are on and a decoder plays it.
         */
        fun codecSteering(sdkInt: Int = Build.VERSION.SDK_INT): CodecSteering = CodecSteering(
            vp9 = sdkInt >= AudioVideoMuxCompatibility.WEBM_OPUS_MIN_SDK,
            av1 = AudioVideoMuxCompatibility.AV1_MP4_ENABLED &&
                sdkInt >= AudioVideoMuxCompatibility.AV1_MP4_MIN_SDK &&
                platformHasDecoder("video/av01"),
        )

        private const val PLAY_FIRST = "Play the video on this page, then tap Try again."

        fun create(
            enabled: Boolean,
            client: OkHttpClient,
            sites: SiteAdapterCoordinator,
        ): BrowserMasterFallback {
            if (!enabled) return BrowserMasterFallback.None
            val capture = WebViewPlaybackCapture(
                enabled = true,
                contentIdOf = { url -> sites.videoKey(url)?.substringAfter(':') },
                inspect = CapturedMediaMetadata(client)::inspect,
                codecs = codecSteering(),
            )
            return AndroidBrowserMasterFallback(
                capture,
                sites,
                MasterFallbackEngine(
                    OkHttpMediaValidator(client), capture, MasterPolicy(enabled = true),
                    captureSelection = capture::selectMain,
                ),
            )
        }
    }
}
