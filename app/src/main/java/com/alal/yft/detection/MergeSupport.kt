package com.alal.yft.detection

import android.media.MediaCodecList
import android.os.Build
import com.alal.yft.core.download.AudioVideoMuxCompatibility
import com.alal.yft.core.download.MuxCompatibility
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaVariant
import java.util.Locale

/**
 * Whether this phone can merge a candidate's video file and its separate audio file into one
 * file it plays (P4). A candidate without a separate audio file always qualifies.
 */
fun interface MergeSupport {
    fun canMerge(candidate: MediaCandidate): Boolean
}

/**
 * The phone's own answer, the download's own gates ([AudioVideoMuxCompatibility]): a row whose
 * video or sound names no codecs, or a sound that is no AAC, is left out, as its download would be
 * refused. AVC merges everywhere. VP9 WebM video merges with its Opus WebM sound
 * into one WebM from Android 10 ([AudioVideoMuxCompatibility.WEBM_OPUS_MIN_SDK], P6: YouTube's 2K
 * and 4K). VP9 HLS video merges with its AAC HLS sound into one MP4 from Android 10
 * ([AudioVideoMuxCompatibility.VP9_HLS_MP4_MIN_SDK], backlog item 5). AV1 would merge from
 * Android 14
 * ([AudioVideoMuxCompatibility.AV1_MP4_MIN_SDK]) and only with an AV1 decoder to play the result,
 * but is off ([AudioVideoMuxCompatibility.AV1_MP4_ENABLED]): the API 34 emulator's muxer failed.
 * v1.0.2 item 26: [av1Enabled] is read at each row — in a debug build the census screen's
 * "AV1 merges (test)" box ([Av1MergeSwitch.merges]); in the release, the constant.
 */
class DeviceMergeSupport(
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val hasDecoder: (mimeType: String) -> Boolean = ::platformHasDecoder,
    private val av1Enabled: () -> Boolean = Av1MergeSwitch::merges,
) : MergeSupport {
    private val playsAv1: Boolean by lazy { hasDecoder(AV1_MIME_TYPE) }

    override fun canMerge(candidate: MediaCandidate): Boolean {
        val audio = candidate.audioCompanion ?: return true
        return merges(
            videoMimeType = candidate.mimeType,
            videoCodecs = candidate.codecs,
            hls = candidate.kind == MediaKind.HLS,
            audio = audio,
        )
    }

    /**
     * v1.0.2 item 59: the same answer for a page's DASH video quality and its manifest's sound
     * ([DashSoundPairing]); a DASH track is a file of its own, never a playlist.
     */
    fun canMerge(video: MediaVariant, audio: CompanionAudio): Boolean = merges(
        videoMimeType = video.mimeType,
        videoCodecs = video.codecs,
        hls = video.kind == MediaKind.HLS,
        audio = audio,
    )

    private fun merges(
        videoMimeType: String?,
        videoCodecs: List<String>,
        hls: Boolean,
        audio: CompanionAudio,
    ): Boolean {
        if (AudioVideoMuxCompatibility.outputMimeTypeFor(videoMimeType) == WEBM) {
            return AudioVideoMuxCompatibility.evaluateWebm(
                videoMimeType = videoMimeType,
                videoCodecs = videoCodecs,
                audioMimeType = audio.mimeType,
                audioCodecs = audio.codecs,
                sdkInt = sdkInt,
            ) == MuxCompatibility.Compatible
        }
        // The download's own MP4 gate ([AudioVideoMuxCompatibility.evaluateMp4]), so the sheet
        // offers only the merges the download makes: the owner's retest of 7e101aa4 (VK,
        // 2026-10-26) had a 1080p row whose sound named no codecs offered, then refused. Backlog
        // item 5: VP9 HLS video and its AAC sound go into one MP4 by the stream copy.
        val compatible = AudioVideoMuxCompatibility.evaluateMp4(
            videoMimeType = videoMimeType,
            videoCodecs = videoCodecs,
            videoHlsPlaylist = hls,
            audioMimeType = audio.mimeType,
            audioCodecs = audio.codecs,
            audioHlsPlaylist = hls,
            sdkInt = sdkInt,
            av1Enabled = av1Enabled(),
        ) == MuxCompatibility.Compatible
        return compatible && videoCodecs.all { codec ->
            !codec.trim().lowercase(Locale.US).startsWith(AV1_CODEC_PREFIX) || playsAv1
        }
    }

    private companion object {
        const val AV1_MIME_TYPE = "video/av01"
        const val AV1_CODEC_PREFIX = "av01"
        const val WEBM = AudioVideoMuxCompatibility.WEBM_OUTPUT_MIME
    }
}

/** Whether a decoder for [mimeType] is installed; a failing codec list counts as none. */
internal fun platformHasDecoder(mimeType: String): Boolean = runCatching {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
        !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
    }
}.getOrDefault(false)
