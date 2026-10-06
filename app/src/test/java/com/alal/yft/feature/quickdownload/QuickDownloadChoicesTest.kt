package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.LENGTH
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.MIB
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.audio
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.choices
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.group
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.resolvedAsset
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.sources
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.video
import com.alal.yft.ui.format.YftFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickDownloadChoicesTest {
    private val mp3Titles = listOf("MP3 · 320 kbps", "MP3 · 192 kbps", "MP3 · 128 kbps")

    @Test
    fun exactlyTwoSectionsAudioThenVideoWithOneRowPerResolution() {
        // P3-FIX (owner's phone): "Music" quick rows, "Fast"/"High" and More formats listed the
        // same formats twice. Now Audio (M4A, MP3 bitrates) and Video (one row per resolution).
        val choices = choices(QuickDownloadFixtures.youtube())!!

        assertEquals(
            listOf("1080p · Full HD", "720p · HD", "480p", "360p"),
            choices.video.map(SheetOption::title),
        )
        assertEquals(
            listOf("~80 MB", "~42 MB", "~18 MB", "11 MB"),
            choices.video.map(SheetOption::size),
        )
        assertEquals("1920 × 1080 · 30 fps · MP4", choices.video.first().detail)
        // P25: the original sound is "M4A", its bitrate on the line below.
        assertEquals(listOf("M4A") + mp3Titles, choices.audio.map(SheetOption::title))
        assertEquals("128 kbps", choices.audio.first().detail)
        assertTrue(choices.audio.all { it.section == OptionSection.AUDIO })
        assertTrue(choices.video.all { it.section == OptionSection.VIDEO && it.chips.isEmpty() })
        assertTrue(choices.options.none { "Fast" in it.title || "High" in it.title })
        assertEquals(choices.audio + choices.video, choices.options)
        // The merged rows keep the sound they are merged with.
        assertNotNull(choices.video.first().variant.audioCompanion)
        // Every row is found by its own id, and only by it.
        choices.options.forEach { assertSame(it, choices.option(it.id)) }
        assertEquals(choices.options.size, choices.options.map(SheetOption::id).distinct().size)
        assertNull(choices.option("missing"))
        assertNull(choices.option(null))
        assertEquals("Ocean waves", choices.title)
        assertEquals("youtube.com", choices.source)
        assertEquals(LENGTH, choices.durationMillis)
    }

    @Test
    fun compactDefaultsToM4aMp3At128And720pWith480p() {
        val full = choices(QuickDownloadFixtures.youtube())!!
        val short = QuickDownloadChoices.compact(full, QualityPreference.UP_TO_720P)

        assertEquals(listOf("M4A", "MP3 · 128 kbps"), short.audio.map { it.title })
        assertEquals(listOf("720p · HD", "480p"), short.video.map { it.title })
        assertEquals(8, full.options.size)
        assertEquals(4, short.options.size)
        short.options.forEach { assertSame(it, full.option(it.id)) }
        assertEquals(full.options.size, full.options.map { it.id }.distinct().size)
    }

    @Test
    fun compactFallsBackToTheNearestLowerThenHigherAndDoesNotDuplicateRows() {
        val cases = listOf(
            listOf(1080, 720, 360) to listOf(720, 360),
            listOf(1080, 480, 360) to listOf(480, 360),
            listOf(2160, 1080) to listOf(1080, 2160),
            listOf(360) to listOf(360),
        )
        cases.forEach { (available, wanted) ->
            val full = choices(available.map { video(it, MIB) })!!
            val short = QuickDownloadChoices.compact(full, QualityPreference.UP_TO_720P)
            assertEquals(wanted, short.video.map { it.rankHeight })
            assertEquals(short.options.size, short.options.map { it.id }.distinct().size)
        }
    }

    @Test
    fun compactHonorsSavedPreferencesAndWorksWithAudioOnlyAndUnknownHeights() {
        val full = choices(QuickDownloadFixtures.youtube())!!
        assertEquals(
            listOf(1080, 720),
            QuickDownloadChoices.compact(full, QualityPreference.HIGHEST).video.map {
                it.rankHeight
            },
        )
        assertEquals(
            listOf(480, 360),
            QuickDownloadChoices.compact(full, QualityPreference.UP_TO_480P).video.map {
                it.rankHeight
            },
        )
        val audioOnly = choices(listOf(audio(128, MIB)))!!
        val shortAudio = QuickDownloadChoices.compact(audioOnly, QualityPreference.UP_TO_720P)
        assertTrue(shortAudio.video.isEmpty())
        assertEquals(2, shortAudio.audio.size)
        val unknown = choices(listOf(video(null, MIB, label = null)))!!
        assertEquals(
            unknown.video,
            QuickDownloadChoices.compact(unknown, QualityPreference.UP_TO_720P).video,
        )
    }

    @Test
    fun rowsAreNamedAfterTheNearestStandardHeightAndKeepTheRealPicture() {
        // The owner's Facebook reel: 848 × 478 and 636 × 358 were "478p" and "358p".
        val choices = choices(
            listOf(
                video(478, 2 * MIB, width = 848, index = 1),
                video(358, MIB, width = 636, index = 2),
            ),
        )!!

        assertEquals(listOf("480p", "360p"), choices.video.map(SheetOption::title))
        assertEquals(
            listOf("848 × 478 · 30 fps · MP4", "636 × 358 · 30 fps · MP4"),
            choices.video.map(SheetOption::detail),
        )
        assertEquals(listOf(480, 360), choices.video.map(SheetOption::rankHeight))
    }

    @Test
    fun standardHeightsUseThePicturesShortSideAndNeverRoundUpOnATie() {
        fun name(width: Int?, height: Int) = QuickDownloadChoices.standardHeight(width, height)

        assertEquals(144, name(256, 144))
        assertEquals(240, name(426, 240))
        assertEquals(720, name(1108, 720))
        assertEquals(1080, name(1660, 1078))
        // A portrait reel is named after its width, like the sites do.
        assertEquals(720, name(720, 1280))
        assertEquals(1440, name(2560, 1440))
        assertEquals(2160, name(3840, 2160))
        assertEquals(4320, name(7680, 4320))
        assertEquals(1080, name(null, 1080))
        assertEquals(240, name(null, 300))
        assertEquals(480, name(null, 600))
    }

    @Test
    fun oneRowPerResolutionPrefersSoundThenAWholeFileAndKeepsASilentOnlyResolution() {
        val silent720 = video(720, 30 * MIB, index = 1)
        val sound720 = video(720, 35 * MIB, index = 2)
        val silent1080 = video(1080, 60 * MIB, index = 3)
        val stream720 = video(720, index = 4, kind = MediaKind.HLS)
        val sources = listOf(
            SheetSource(silent720, resolvedAsset(silent720, silent = true), true),
            SheetSource(stream720, resolvedAsset(stream720), true),
            SheetSource(sound720, resolvedAsset(sound720), true),
            SheetSource(silent1080, resolvedAsset(silent1080, silent = true), true),
        )

        val choices = QuickDownloadChoices.of(group(sources.map { it.candidate }), sources)!!

        assertEquals(listOf("1080p · Full HD", "720p · HD"), choices.video.map(SheetOption::title))
        assertSame(sound720, choices.video[1].source.candidate)
        assertEquals(listOf(QuickDownloadChoices.NO_SOUND), choices.video[0].chips)
        assertTrue(choices.video[1].chips.isEmpty())
    }

    @Test
    fun aCompleteFileBeatsAMergeAtTheSameResolution() {
        // Facebook (P4): the HD file and the 720p video track merged with the audio track.
        val hdFile = video(720, 30 * MIB, index = 1, label = "HD")
        val merged720 = video(720, 40 * MIB, merged = true, index = 2)
            .copy(bitrateBitsPerSecond = 2_749_477)
        val merged1080 = video(1080, 60 * MIB, merged = true, index = 3)
            .copy(codecs = listOf("av01.0.08M.08"), bitrateBitsPerSecond = 1_340_000)
        val candidates = listOf(merged1080, merged720, hdFile, audio(57, 2 * MIB))

        val choices = choices(candidates)!!

        assertEquals(listOf("1080p · Full HD", "720p · HD"), choices.video.map(SheetOption::title))
        assertSame(merged1080, choices.video[0].source.candidate)
        assertSame(hdFile, choices.video[1].source.candidate)
        assertEquals("M4A", choices.audio.first().title)
        assertEquals("57 kbps", choices.audio.first().detail)
    }

    @Test
    fun twoKAndFourKRowsThePhoneCannotDecodeAreStillOfferedWithAWarning() {
        // P6: YouTube's 2K and 4K are VP9 WebM merged with Opus; some phones cannot play them.
        fun webm(height: Int) = video(height, 300 * MIB, merged = true).copy(
            mediaUrl = "https://media.example.test/video-$height.webm",
            mimeType = "video/webm",
            codecs = listOf("vp9"),
        )
        val candidates = listOf(webm(2160), webm(1440)) + QuickDownloadFixtures.youtube()
        val asked = mutableListOf<Int?>()
        val noVp9 = VideoPlaybackSupport { variant ->
            asked += variant.height
            variant.codecs.none { it == "vp9" }
        }

        val warned = QuickDownloadChoices.of(group(candidates), sources(candidates), noVp9)!!
        val plain = QuickDownloadChoices.of(group(candidates), sources(candidates))!!

        assertEquals(
            listOf("2160p · 4K", "1440p · 2K", "1080p · Full HD", "720p · HD", "480p", "360p"),
            warned.video.map(SheetOption::title),
        )
        assertEquals("3840 × 2160 · 30 fps · WebM", warned.video.first().detail)
        assertEquals(
            listOf(QuickDownloadChoices.MAY_NOT_PLAY),
            warned.video.first().chips,
        )
        assertEquals(listOf(QuickDownloadChoices.MAY_NOT_PLAY), warned.video[1].chips)
        assertTrue(warned.video.drop(2).all { it.chips.isEmpty() })
        // Full HD and below are never asked about: every phone YFT runs on plays them.
        assertEquals(listOf(2160, 1440), asked)
        assertTrue(plain.video.all { it.chips.isEmpty() })
    }

    @Test
    fun aVideoWithoutAnAudioFileOffersItsOwnSoundAsM4aAndMp3() {
        // Facebook-style: one MP4 with AAC sound and no audio-only file.
        val file = video(360, 11 * MIB)
        val source = SheetSource(file, resolvedAsset(file, audioBitrate = 96_000), true)

        val choices = QuickDownloadChoices.of(group(listOf(file)), listOf(source))!!

        assertEquals(listOf("M4A") + mp3Titles, choices.audio.map(SheetOption::title))
        val m4a = choices.audio.first()
        assertTrue(m4a.variant.audioFromVideo)
        assertEquals("The video's own sound · 96 kbps", m4a.detail)
        // 252 s at 96 kbps.
        assertEquals("~${YftFormat.bytes(3_024_000)}", m4a.size)
        // P25: the sound is copied, not re-encoded: the fastest audio, so not "Slow".
        assertTrue(m4a.chips.isEmpty())
        assertTrue(
            choices.audio.drop(1).all {
                it.variant.mp3 != null && it.variant.audioFromVideo &&
                    it.chips == listOf(QuickDownloadChoices.SLOW)
            },
        )
        assertEquals("360p", choices.video.single().title)
        assertEquals("640 × 360 · 30 fps · MP4", choices.video.single().detail)
        assertEquals("11 MB", choices.video.single().size)
    }

    @Test
    fun audioOnlyGivesTheM4aAndMp3Only() {
        val choices = choices(listOf(audio(128, 4 * MIB), audio(48, 2 * MIB)))!!

        assertTrue(choices.video.isEmpty())
        assertTrue(choices.isAudioOnly)
        assertEquals(listOf("M4A") + mp3Titles, choices.audio.map(SheetOption::title))
        assertEquals("4 MB", choices.audio[0].size)
        // 252 s at 192 kbps.
        assertEquals("~${YftFormat.bytes(6_048_000)}", choices.audio[2].size)
        assertFalse(choices.audio[0].variant.audioFromVideo)
        assertTrue(choices.audio.drop(1).all { it.chips == listOf(QuickDownloadChoices.SLOW) })
    }

    @Test
    fun aQualityLabelNeverComesFromThePageTitle() {
        // A file found on a page, not by an adapter: no height and the page's title.
        val file = video(
            height = null,
            bytes = 11 * MIB,
            label = null,
            title = "Morning swim — Lake",
            videoId = null,
        )

        val choices = choices(listOf(file))!!

        val row = choices.video.single()
        assertEquals("Quality unknown", row.title)
        assertEquals("MP4", row.detail)
        assertEquals("11 MB", row.size)
        assertTrue(choices.options.none { "Morning" in it.title || "Lake" in it.title })
        assertEquals("Morning swim — Lake", choices.title)
    }

    @Test
    fun facebooksUnmeasuredHdAndSdRankByTheSitesWord() {
        val id = "facebook:1603698891196107"
        val hd = video(height = null, bytes = 25 * MIB, label = "HD", videoId = id, index = 1)
        val sd = video(height = null, bytes = 9 * MIB, label = "SD", videoId = id, index = 2)
        val dash = video(
            height = null,
            label = "DASH",
            kind = MediaKind.DASH,
            videoId = id,
            index = 3,
        )
        val group = group(listOf(hd, sd, dash))
        // A source whose lookup failed is left out.
        val sources = listOf(hd, sd).map { SheetSource(it, resolvedAsset(it), true) } +
            SheetSource(dash, null, resolved = false)

        val choices = QuickDownloadChoices.of(group, sources)!!

        assertEquals(3, group.candidates.size)
        assertEquals("Ocean waves", choices.title)
        assertEquals(listOf("HD", "SD"), choices.video.map(SheetOption::title))
        assertEquals(listOf("25 MB", "9 MB"), choices.video.map(SheetOption::size))
        assertEquals(listOf(720, 480), choices.video.map(SheetOption::rankHeight))
    }

    @Test
    fun audioThatIsNotAacHasNoMp3Row() {
        val webm = choices(listOf(audio(160, mimeType = "audio/webm")))!!

        assertEquals(listOf("WebM · 160 kbps"), webm.audio.map(SheetOption::title))
        assertTrue(webm.options.none { it.variant.mp3 != null })
    }

    @Test
    fun aQualityPlaylistFoundBesideItsMasterIsNoRowOfItsOwn() {
        // P24: the page's player fetched its HLS master and the 720p playlist the master names;
        // both are one video, and the playlist is the master's 720p row, not a third row.
        val master = video(null, kind = MediaKind.HLS, videoId = null, label = null, index = 1)
        val playlist = video(null, kind = MediaKind.HLS, videoId = null, label = null, index = 2)
        val stated = resolvedAsset(master).variants.single()
        val masterAsset = resolvedAsset(master).copy(
            variants = listOf(
                stated.copy(id = "hls-720", playbackUrl = playlist.mediaUrl, height = 720),
                stated.copy(id = "hls-1080", playbackUrl = "${master.mediaUrl}?q=1", height = 1080),
            ),
        )
        val sources = listOf(
            SheetSource(master, masterAsset, resolved = true),
            SheetSource(playlist, resolvedAsset(playlist), resolved = true),
        )

        val choices = QuickDownloadChoices.of(group(listOf(master, playlist)), sources)!!

        assertEquals(listOf("1080p · Full HD", "720p · HD"), choices.video.map(SheetOption::title))
        assertEquals(playlist.mediaUrl, choices.video.last().variant.playbackUrl)
    }

    @Test
    fun nothingReadableGivesNoChoices() {
        val file = video(720).copy(drmHint = true)

        assertNull(
            QuickDownloadChoices.of(
                group(listOf(file)),
                listOf(SheetSource(file, null, resolved = false)),
            ),
        )
    }

    @Test
    fun unresolvedSourcesStillGiveEveryStatedQualityWithoutASizeCheck() {
        val candidates = listOf(1080, 720, 480, 360).map { video(it, videoId = "facebook:fixture") }
        val sources = candidates.map { SheetSource(it, null, resolved = false) }
        val choices = QuickDownloadChoices.of(group(candidates), sources)!!
        assertEquals(listOf(1080, 720, 480, 360), choices.video.map { it.rankHeight })
        assertTrue(choices.video.all { it.size == null })
        assertEquals(4, choices.audio.size)
    }

    @Test
    fun statedSizesAreEstimatedFromTheBitrateAndLengthAndCannotOverflow() {
        val candidate = video(720).copy(bitrateBitsPerSecond = 1_000_000, durationMillis = 8_000)
        val source = SheetSource(candidate, null, resolved = false)
        val choices = QuickDownloadChoices.of(group(listOf(candidate)), listOf(source))!!
        assertEquals("~${YftFormat.bytes(1_000_000)}", choices.video.single().size)
        val huge = candidate.copy(bitrateBitsPerSecond = Long.MAX_VALUE,
            durationMillis = Long.MAX_VALUE)
        assertNull(QuickDownloadMetadata.asset(huge)!!.variants.single().sizeBytes)
    }

    @Test
    fun knownDashHeightsStayInTheShortViewAndUnmeasuredHdSdStayUnderMore() {
        val id = "facebook:fixture"
        val candidates = listOf(
            video(null, label = "HD", videoId = id, index = 1),
            video(null, label = "SD", videoId = id, index = 2),
            video(720, merged = true, videoId = id, index = 3),
            video(480, merged = true, videoId = id, index = 4),
        )
        val full = QuickDownloadChoices.of(group(candidates), candidates.map {
            SheetSource(it, null, resolved = false)
        })!!
        assertEquals(listOf("720p · HD", "HD", "480p", "SD"), full.video.map { it.title })
        assertEquals(listOf("720p · HD", "480p"),
            QuickDownloadChoices.compact(full, QualityPreference.UP_TO_720P).video.map { it.title })
        assertEquals(full.options.size, full.options.map { it.id }.distinct().size)
    }

    @Test
    fun aSizeUpdateNeverChangesRowIdsTitlesOrderOrTheChosenFormats() {
        val candidate = video(720, videoId = "facebook:fixture")
        val initial = SheetSource(candidate, QuickDownloadMetadata.asset(candidate), false)
        val full = QuickDownloadChoices.of(group(listOf(candidate)), listOf(initial))!!
        val sized = initial.copy(
            asset = resolvedAsset(candidate.copy(contentLengthBytes = 5 * MIB)),
        )
        val updated = QuickDownloadChoices.updateSizes(full, sized)
        assertEquals(full.options.map { it.id }, updated.options.map { it.id })
        assertEquals(full.options.map { it.title }, updated.options.map { it.title })
        assertEquals("5 MB", updated.video.single().size)
    }

    @Test
    fun everySiteGivesTheSameSectionsNamesAndOrder() {
        // P25: YouTube, Facebook (DASH + HD/SD, or HD/SD alone) and another site's HLS + MP4
        // give one sheet: Audio "M4A" then MP3, Video by height, the same short view.
        val youtube = QuickDownloadFixtures.youtubeLadder()
        val facebookDash = QuickDownloadFixtures.facebookDash()
        val facebookFiles = QuickDownloadFixtures.facebookFiles()
        val other = QuickDownloadFixtures.otherSite()
        val sites = listOf(
            "YouTube" to choices(youtube)!!,
            "Facebook DASH + HD/SD" to choices(facebookDash)!!,
            "Facebook HD/SD" to choices(facebookFiles)!!,
            "Other site HLS + MP4" to QuickDownloadChoices.of(
                group(other.map(SheetSource::candidate)),
                other,
            )!!,
        )
        val videos = mapOf(
            "YouTube" to listOf(
                "2160p · 4K", "1440p · 2K", "1080p · Full HD", "720p · HD", "480p", "360p",
                "240p", "144p",
            ),
            "Facebook DASH + HD/SD" to listOf(
                "1080p · Full HD", "720p · HD", "HD", "480p", "SD", "360p",
            ),
            "Facebook HD/SD" to listOf("HD", "SD"),
            "Other site HLS + MP4" to listOf("1080p · Full HD", "720p · HD", "480p"),
        )
        val shortVideos = mapOf(
            "YouTube" to listOf("720p · HD", "480p"),
            "Facebook DASH + HD/SD" to listOf("720p · HD", "480p"),
            "Facebook HD/SD" to listOf("HD", "SD"),
            "Other site HLS + MP4" to listOf("720p · HD", "480p"),
        )

        sites.forEach { (site, full) ->
            val short = QuickDownloadChoices.compact(full, QualityPreference.UP_TO_720P)
            assertEquals(site, listOf("M4A") + mp3Titles, full.audio.map(SheetOption::title))
            assertEquals(site, videos.getValue(site), full.video.map(SheetOption::title))
            assertEquals(site, full.audio + full.video, full.options)
            assertEquals(site, listOf("M4A", "MP3 · 128 kbps"), short.audio.map { it.title })
            assertEquals(site, shortVideos.getValue(site), short.video.map { it.title })
            assertEquals(
                site,
                short.video.first().id,
                QuickDownloadChoices.preselect(full, QualityPreference.UP_TO_720P)?.id,
            )
        }
        // The sound of a file whose codecs nobody stated is offered too (P25).
        val otherSound = sites.last().second.audio.first()
        assertTrue(otherSound.variant.audioFromVideo)
        assertEquals(other.last().candidate, otherSound.source.candidate)
        assertTrue(sites[2].second.audio.first().variant.audioFromVideo)
        // The other site's 720p is its whole MP4, not the stream's 720p.
        assertEquals(MediaKind.DIRECT, sites.last().second.video[1].variant.kind)
    }

    @Test
    fun everyRowSaysWhatItIsForInOneLine() {
        // P25: the line under each title (`quick-row-description`).
        val youtube = choices(QuickDownloadFixtures.youtubeLadder())!!
        val facebook = choices(QuickDownloadFixtures.facebookFiles())!!
        val unknown = choices(listOf(video(null, MIB, label = null, videoId = null)))!!

        assertEquals(
            listOf(
                "High details for big screen play",
                "High details for big screen play",
                "High details for full screen play",
                "Clear view and quick play",
                "Normal quality for quick play",
                "Normal quality for quick play",
                "Low quality for quick play",
                "Low quality, smallest file",
            ),
            youtube.video.map(SheetOption::description),
        )
        assertEquals(
            listOf("Original sound, fastest") + List(3) { "Plays everywhere" },
            youtube.audio.map(SheetOption::description),
        )
        // HD reads as 720p and SD as 480p until their pictures are measured.
        assertEquals(
            listOf("Clear view and quick play", "Normal quality for quick play"),
            facebook.video.map(SheetOption::description),
        )
        assertEquals("Quality unknown", unknown.video.single().title)
        assertEquals("As the page plays it", unknown.video.single().description)
        // A site's own MP3 file plays everywhere too.
        val mp3File = choices(listOf(audio(128, MIB, mimeType = "audio/mpeg")))!!
        assertEquals("MP3 · 128 kbps", mp3File.audio.single().title)
        assertEquals("Plays everywhere", mp3File.audio.single().description)
        assertTrue(mp3File.audio.single().chips.isEmpty())
    }

    @Test
    fun anHdOrSdRowIsRenamedInPlaceOnceItsPictureIsMeasured() {
        // P25 (P11): "HD"/"SD" until the header check reads the file's picture, then the
        // height's name without moving, changing its rank or the short view.
        val files = QuickDownloadFixtures.facebookFiles()
        val stated = files.map { SheetSource(it, QuickDownloadMetadata.asset(it), false) }
        val full = QuickDownloadChoices.of(group(files), stated)!!
        val short = QuickDownloadChoices.compact(full, QualityPreference.UP_TO_720P)
        assertEquals(listOf("HD", "SD"), full.video.map(SheetOption::title))

        fun measured(source: SheetSource, width: Int, height: Int, bytes: Long): SheetSource {
            val asset = requireNotNull(source.asset)
            return source.copy(
                asset = asset.copy(
                    variants = asset.variants.map {
                        it.copy(
                            width = width,
                            height = height,
                            framesPerSecond = 30.0,
                            sizeBytes = bytes,
                            sizeAccuracy = MediaSizeAccuracy.EXACT,
                            audioBitrateBitsPerSecond = 96_000,
                        )
                    },
                ),
            )
        }
        val hd = QuickDownloadChoices.updateSizes(full, measured(stated[0], 1280, 720, 25 * MIB))
        val both = QuickDownloadChoices.updateSizes(hd, measured(stated[1], 640, 360, 9 * MIB))

        assertEquals(listOf("720p · HD", "SD"), hd.video.map(SheetOption::title))
        assertEquals(listOf("720p · HD", "360p"), both.video.map(SheetOption::title))
        assertEquals(full.options.map(SheetOption::id), both.options.map(SheetOption::id))
        assertEquals(listOf(720, 480), both.video.map(SheetOption::rankHeight))
        assertEquals(listOf("720p", "360p"), both.video.map(SheetOption::quality))
        assertEquals(
            listOf("1280 × 720 · 30 fps · MP4", "640 × 360 · 30 fps · MP4"),
            both.video.map(SheetOption::detail),
        )
        assertEquals(listOf("25 MB", "9 MB"), both.video.map(SheetOption::size))
        assertEquals(
            short.options.map(SheetOption::id),
            QuickDownloadChoices.compact(both, QualityPreference.UP_TO_720P).options.map { it.id },
        )
        assertEquals(listOf("M4A") + mp3Titles, both.audio.map(SheetOption::title))
        // The measured sound's bitrate sizes the M4A: 252 s at 96 kbps.
        assertEquals("~${YftFormat.bytes(3_024_000)}", both.audio.first().size)
        assertEquals("Normal quality for quick play", both.video.last().description)
    }

    @Test
    fun sizesAreStatedElseEstimatedFromTheBitrateAndLengthElseUnknownAndNoRowIsDropped() {
        // P25: a looked-up file without a size whose bitrate is known reads "~"; a merge adds
        // its sound; nothing known keeps the row, which the sheet labels "Size unknown".
        val stream = video(720, index = 1).copy(bitrateBitsPerSecond = 2_000_000)
        val merged = video(1080, merged = true, index = 2).copy(bitrateBitsPerSecond = 4_000_000)
        val bare = video(480, index = 3)
        val music = audio(160)

        val choices = choices(listOf(stream, merged, bare, music))!!

        assertEquals(listOf(1080, 720, 480), choices.video.map(SheetOption::rankHeight))
        assertEquals(
            listOf(
                // 252 s at 4 Mbit/s and the merge's 4 MiB sound.
                "~${YftFormat.bytes(126_000_000 + 4 * MIB)}",
                // 252 s at 2 Mbit/s.
                "~${YftFormat.bytes(63_000_000)}",
                null,
            ),
            choices.video.map(SheetOption::size),
        )
        // 252 s at 160 kbps.
        assertEquals("~${YftFormat.bytes(5_040_000)}", choices.audio.first().size)
    }

    @Test
    fun preselectionFollowsTheDefaultQuality() {
        val choices = choices(QuickDownloadFixtures.youtube())!!
        val tall = choices(listOf(video(1080), video(720)))!!
        val music = choices(listOf(audio(128)))!!

        fun pick(from: QuickChoices, quality: QualityPreference) =
            QuickDownloadChoices.preselect(from, quality)?.title
        assertEquals("1080p · Full HD", pick(choices, QualityPreference.HIGHEST))
        assertEquals("1080p · Full HD", pick(choices, QualityPreference.UP_TO_1080P))
        assertEquals("720p · HD", pick(choices, QualityPreference.UP_TO_720P))
        assertEquals("480p", pick(choices, QualityPreference.UP_TO_480P))
        assertEquals("360p", pick(choices, QualityPreference.LOWEST))
        // Nothing at or below the ceiling: the lowest there is.
        assertEquals("720p · HD", pick(tall, QualityPreference.UP_TO_480P))
        assertEquals("M4A", pick(music, QualityPreference.HIGHEST))
    }
}
