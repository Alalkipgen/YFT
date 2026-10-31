package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.detection.DashSoundPairing
import com.alal.yft.feature.downloads.DownloadRequest
import com.alal.yft.feature.quickdownload.DashManifestSheet.Companion.text
import com.alal.yft.feature.quickdownload.DashRowDownloadTest.Companion.A
import com.alal.yft.feature.quickdownload.DashRowDownloadTest.Companion.A_URL
import com.alal.yft.feature.quickdownload.DashRowDownloadTest.Companion.B
import com.alal.yft.feature.quickdownload.DashRowDownloadTest.Companion.B_URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.0.2 fix R2: [DashRowFile] finds a row's own Representation in the manifest read for its
 * download — a merged row, a video-only row and an audio row of both of the owner's manifests,
 * each planned with its exact Representations — and nothing else when it is gone.
 */
class DashRowFileTest {
    private val a = DashManifestSheet(A_URL, text(A))
    private val b = DashManifestSheet(B_URL, text(B))

    @Test
    fun `each kind of Repro A's rows plans its own Representations`() {
        // The phone merges AVC with HE-AAC; a phone that merged neither keeps the video silent.
        val rows = a.read()
        val merged = DashSoundPairing.pair(rows.variants) { _, _ -> true }
        val silent = DashSoundPairing.pair(rows.variants) { _, _ -> false }
        val fresh = a.read().variants
        val freshMerged = DashSoundPairing.pair(fresh) { _, _ -> true }
        val freshSilent = DashSoundPairing.pair(fresh) { _, _ -> false }

        val withSound = plan(rows, freshMerged, merged.of("5")) as DownloadRequest.Mux
        assertEquals("5", withSound.plan.video.representationId)
        assertEquals("1", withSound.plan.audio.representationId)
        val videoOnly = plan(rows, freshSilent, silent.of("5")) as DownloadRequest.Dash
        assertEquals("5", videoOnly.plan.representationId)
        assertEquals(MediaTrackType.VIDEO, videoOnly.plan.trackType)
        val sound = plan(rows, freshMerged, merged.of("1")) as DownloadRequest.Dash
        assertEquals("1", sound.plan.representationId)
        assertEquals(MediaTrackType.AUDIO, sound.plan.trackType)

        // Every row is found as itself, never as the manifest's first Representation.
        for ((list, read) in listOf(merged to freshMerged, silent to freshSilent)) {
            list.forEach { row -> assertEquals(row, DashRowFile.find(read, row)) }
        }
    }

    @Test
    fun `each kind of Repro B's rows plans its own Representations`() {
        val rows = b.read()
        val fresh = b.read().variants
        // AV1 merges on (item 26's debug switch): the video with the 128 kbps sound.
        val merged = rows.variants.of("video_eng=208000")
            .withSound(rows.variants.of("audio_eng=128002"))
        val freshMerged = fresh.map {
            if (it.manifestVariantId == "video_eng=208000") {
                it.withSound(fresh.of("audio_eng=128002"))
            } else {
                it
            }
        }
        val withSound = plan(rows, freshMerged, merged, av1 = true) as DownloadRequest.Mux
        assertEquals("video_eng=208000", withSound.plan.video.representationId)
        assertEquals("audio_eng=128002", withSound.plan.audio.representationId)

        // AV1 merges off: the 640 × 284 video alone, and the 128 kbps sound, not the first one.
        val videoOnly = plan(rows, fresh, rows.variants.of("video_eng=208000"))
            as DownloadRequest.Dash
        assertEquals("video_eng=208000", videoOnly.plan.representationId)
        assertEquals(MediaTrackType.VIDEO, videoOnly.plan.trackType)
        val sound = plan(rows, fresh, rows.variants.of("audio_eng=128002")) as DownloadRequest.Dash
        assertEquals("audio_eng=128002", sound.plan.representationId)
        assertEquals(MediaTrackType.AUDIO, sound.plan.trackType)
        rows.variants.forEach { row -> assertEquals(row, DashRowFile.find(fresh, row)) }
    }

    @Test
    fun `a row is never another Representation or another track`() {
        val fresh = a.read().variants
        val merged = DashSoundPairing.pair(fresh) { _, _ -> true }
        val silent = DashSoundPairing.pair(fresh) { _, _ -> false }
        // A merged row is not its video alone, a video-only row not the video with a sound.
        assertNull(DashRowFile.find(silent, merged.of("5")))
        assertNull(DashRowFile.find(merged, silent.of("5")))
        // Its sound or its video gone: nothing.
        assertNull(DashRowFile.find(merged.filter { it.manifestVariantId != "5" }, merged.of("5")))
        assertNull(DashRowFile.find(merged.filter { it.manifestVariantId != "1" }, merged.of("1")))
        // Another sound's merge is not this row.
        val otherSound = merged.of("5").let {
            it.copy(audioCompanion = it.audioCompanion?.copy(representationId = "9"))
        }
        assertNull(DashRowFile.find(merged, otherSound))
        // B's 128 kbps sound gone: not the first sound audio_eng=64008.
        val bFresh = b.read().variants
        val high = bFresh.of("audio_eng=128002")
        assertNull(DashRowFile.find(bFresh - high, high))
        // Only a DASH row is found this way.
        val file = high.copy(kind = MediaKind.DIRECT, dashOneFile = false)
        assertNull(DashRowFile.find(bFresh + file, file))
    }

    @Test
    fun `the Details line names the Representation that is gone`() {
        val merged = DashSoundPairing.pair(a.read().variants) { _, _ -> true }
        val bRows = b.read().variants
        assertEquals(
            "Not in the manifest now: Representation 5 (video) with sound 1",
            DashRowFile.missingLine(merged.of("5")),
        )
        assertEquals(
            "Not in the manifest now: Representation video_eng=208000 (video only)",
            DashRowFile.missingLine(bRows.of("video_eng=208000")),
        )
        assertEquals(
            "Not in the manifest now: Representation audio_eng=128002 (audio)",
            DashRowFile.missingLine(bRows.of("audio_eng=128002")),
        )
    }

    /** The download of [row] as the sheet prepares it: its file in [fresh], then the plan. */
    private fun plan(
        asset: MediaAsset,
        fresh: List<MediaVariant>,
        row: MediaVariant,
        av1: Boolean = false,
    ): DownloadRequest {
        val file = requireNotNull(DashRowFile.find(fresh, row)) { "${row.manifestVariantId}" }
        return DashManifestSheet.plan(asset.copy(variants = fresh), file, av1)
    }

    private fun List<MediaVariant>.of(representation: String): MediaVariant =
        single { it.manifestVariantId == representation }

    /** The merge [DashSoundPairing] makes of a video and its manifest's sound. */
    private fun MediaVariant.withSound(sound: MediaVariant) = copy(
        trackType = MediaTrackType.AUDIO_VIDEO,
        audioCompanion = CompanionAudio(
            mediaUrl = sound.playbackUrl,
            mimeType = requireNotNull(sound.mimeType),
            codecs = sound.codecs,
            requestContext = sound.requestContext,
            bitrateBitsPerSecond = sound.bitrateBitsPerSecond,
            representationId = sound.manifestVariantId,
        ),
        sizeBytes = null,
        sizeAccuracy = null,
    )
}
