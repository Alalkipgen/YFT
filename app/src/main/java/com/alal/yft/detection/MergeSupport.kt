package com.alal.yft.detection

import android.media.MediaCodecList
import android.os.Build
import com.alal.yft.core.download.AudioVideoMuxCompatibility
import com.alal.yft.core.download.MuxCompatibility
import com.alal.yft.core.model.media.MediaCandidate
import java.util.Locale

/**
 * Whether this phone can merge a candidate's video file and its separate audio file into one
 * file it plays (P4). A candidate without a separate audio file always qualifies.
 */
fun interface MergeSupport {
    fun canMerge(candidate: MediaCandidate): Boolean
}

/**
 * The phone's own answer. AVC merges everywhere. VP9 WebM video merges with its Opus WebM sound
 * into one WebM from Android 10 ([AudioVideoMuxCompatibility.WEBM_OPUS_MIN_SDK], P6: YouTube's 2K
 * and 4K). AV1 would merge from Android 14
 * ([AudioVideoMuxCompatibility.AV1_MP4_MIN_SDK]) and only with an AV1 decoder to play the result,
 * but is off ([AudioVideoMuxCompatibility.AV1_MP4_ENABLED]): the API 34 emulator's muxer failed.
 */
class DeviceMergeSupport(
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val hasDecoder: (mimeType: String) -> Boolean = ::platformHasDecoder,
    private val av1Enabled: Boolean = AudioVideoMuxCompatibility.AV1_MP4_ENABLED,
) : MergeSupport {
    private val playsAv1: Boolean by lazy { hasDecoder(AV1_MIME_TYPE) }

    override fun canMerge(candidate: MediaCandidate): Boolean {
        val audio = candidate.audioCompanion ?: return true
        if (AudioVideoMuxCompatibility.outputMimeTypeFor(candidate.mimeType) == WEBM) {
            return AudioVideoMuxCompatibility.evaluateWebm(
                videoMimeType = candidate.mimeType,
                videoCodecs = candidate.codecs,
                audioMimeType = audio.mimeType,
                audioCodecs = audio.codecs,
                sdkInt = sdkInt,
            ) == MuxCompatibility.Compatible
        }
        return candidate.codecs.all { codec ->
            AudioVideoMuxCompatibility.canWriteVideo(codec, sdkInt, av1Enabled) &&
                (!codec.trim().lowercase(Locale.US).startsWith(AV1_CODEC_PREFIX) || playsAv1)
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
