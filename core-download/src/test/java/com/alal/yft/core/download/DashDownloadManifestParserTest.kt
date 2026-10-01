package com.alal.yft.core.download

import com.alal.yft.core.model.media.MediaTrackType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashDownloadManifestParserTest {
    @Test
    fun `fixed SegmentTemplate expands selected representation in order`() {
        val result = DashDownloadManifestParser.parse(
            manifest = fixedTemplateManifest(token = "private-one"),
            manifestUrl = MANIFEST_URL.toHttpUrl(),
            representationId = "v720",
            expectedTrackType = MediaTrackType.VIDEO,
            maxChunks = 10,
        ) as DashDownloadManifestParser.Result.Parsed

        assertEquals(4, result.chunks.size)
        assertEquals(DashChunkType.INITIALIZATION, result.chunks[0].type)
        assertTrue(result.chunks[0].url.toString().endsWith("/root/video/init-v720.mp4"))
        assertTrue(
            result.chunks[1].url.toString()
                .endsWith("/root/video/chunk-00005.m4s?token=private-one"),
        )
        assertTrue(
            result.chunks[3].url.toString()
                .endsWith("/root/video/chunk-00007.m4s?token=private-one"),
        )
        assertEquals(MediaTrackType.VIDEO, result.trackType)
        assertEquals("video/mp4", result.mimeType)
        assertEquals(listOf("avc1.4d401f"), result.codecs)
        assertFalse(result.chunks.first().toString().contains("media.example.test"))
    }

    @Test
    fun `fingerprint ignores renewed signed queries`() {
        fun parse(token: String): DashDownloadManifestParser.Result.Parsed =
            DashDownloadManifestParser.parse(
                manifest = fixedTemplateManifest(token),
                manifestUrl = MANIFEST_URL.toHttpUrl(),
                representationId = "v720",
                expectedTrackType = MediaTrackType.VIDEO,
                maxChunks = 10,
            ) as DashDownloadManifestParser.Result.Parsed

        assertEquals(parse("one").fingerprint, parse("two").fingerprint)
    }

    @Test
    fun `SegmentTimeline expands repeat and Time templates`() {
        val result = DashDownloadManifestParser.parse(
            manifest = """
                <MPD type="static" mediaPresentationDuration="PT4S"
                    xmlns="urn:mpeg:dash:schema:mpd:2011">
                  <Period>
                    <AdaptationSet contentType="audio" mimeType="audio/mp4" codecs="mp4a.40.2">
                      <SegmentTemplate timescale="1000"
                          initialization="audio/init-${'$'}RepresentationID${'$'}.mp4"
                          media="audio/segment-${'$'}Time${'$'}.m4s">
                        <SegmentTimeline>
                          <S t="100" d="1000" r="1" />
                          <S d="2000" />
                        </SegmentTimeline>
                      </SegmentTemplate>
                      <Representation id="a-en" bandwidth="128000" />
                    </AdaptationSet>
                  </Period>
                </MPD>
            """.trimIndent(),
            manifestUrl = MANIFEST_URL.toHttpUrl(),
            representationId = "a-en",
            expectedTrackType = MediaTrackType.AUDIO,
            maxChunks = 10,
        ) as DashDownloadManifestParser.Result.Parsed

        assertEquals(4, result.chunks.size)
        assertEquals(
            listOf(
                "/root/audio/init-a-en.mp4",
                "/root/audio/segment-100.m4s",
                "/root/audio/segment-1100.m4s",
                "/root/audio/segment-2100.m4s",
            ),
            result.chunks.map { it.url.encodedPath },
        )
    }

    @Test
    fun `SegmentList parses initialization and media byte ranges`() {
        val result = DashDownloadManifestParser.parse(
            manifest = """
                <MPD type="static" xmlns="urn:mpeg:dash:schema:mpd:2011">
                  <Period>
                    <AdaptationSet contentType="video" mimeType="video/mp4">
                      <Representation id="single-file" bandwidth="900000">
                        <BaseURL>track.mp4?token=private</BaseURL>
                        <SegmentList>
                          <Initialization range="0-7" />
                          <SegmentURL mediaRange="8-11" />
                          <SegmentURL mediaRange="12-15" />
                        </SegmentList>
                      </Representation>
                    </AdaptationSet>
                  </Period>
                </MPD>
            """.trimIndent(),
            manifestUrl = MANIFEST_URL.toHttpUrl(),
            representationId = "single-file",
            expectedTrackType = MediaTrackType.VIDEO,
            maxChunks = 10,
        ) as DashDownloadManifestParser.Result.Parsed

        assertEquals(3, result.chunks.size)
        assertEquals(ResolvedByteRange(offset = 0, length = 8), result.chunks[0].byteRange)
        assertEquals(ResolvedByteRange(offset = 8, length = 4), result.chunks[1].byteRange)
        assertEquals(ResolvedByteRange(offset = 12, length = 4), result.chunks[2].byteRange)
        assertTrue(result.chunks.all { it.url.encodedPath == "/root/track.mp4" })
    }

    @Test
    fun `representation BaseURL without segment metadata becomes one selected resource`() {
        val result = DashDownloadManifestParser.parse(
            manifest = """
                <MPD type="static" xmlns="urn:mpeg:dash:schema:mpd:2011">
                  <Period>
                    <AdaptationSet contentType="audio" mimeType="audio/mp4">
                      <Representation id="audio-file">
                        <BaseURL>audio.m4a</BaseURL>
                      </Representation>
                    </AdaptationSet>
                  </Period>
                </MPD>
            """.trimIndent(),
            manifestUrl = MANIFEST_URL.toHttpUrl(),
            representationId = "audio-file",
            expectedTrackType = MediaTrackType.AUDIO,
            maxChunks = 10,
        ) as DashDownloadManifestParser.Result.Parsed

        assertEquals(1, result.chunks.size)
        assertEquals(DashChunkType.MEDIA, result.chunks.single().type)
        assertTrue(result.chunks.single().url.toString().endsWith("/root/audio.m4a"))
    }

    @Test
    fun `rejects DRM dynamic and mismatched track manifests`() {
        val drm = parseSimple(
            """
            <ContentProtection schemeIdUri="urn:uuid:fixture" />
            <Representation id="v1"><BaseURL>video.mp4</BaseURL></Representation>
            """.trimIndent(),
        )
        val dynamic = DashDownloadManifestParser.parse(
            manifest = simpleManifest(
                body = "<Representation id=\"v1\"><BaseURL>video.mp4</BaseURL></Representation>",
                type = "dynamic",
            ),
            manifestUrl = MANIFEST_URL.toHttpUrl(),
            representationId = "v1",
            expectedTrackType = MediaTrackType.VIDEO,
            maxChunks = 10,
        )
        val mismatch = DashDownloadManifestParser.parse(
            manifest = simpleManifest(
                "<Representation id=\"v1\"><BaseURL>audio.m4a</BaseURL></Representation>",
                contentType = "audio",
                mimeType = "audio/mp4",
            ),
            manifestUrl = MANIFEST_URL.toHttpUrl(),
            representationId = "v1",
            expectedTrackType = MediaTrackType.VIDEO,
            maxChunks = 10,
        )

        assertEquals(DashDownloadManifestParser.Result.DrmProtected, drm)
        assertEquals(DashDownloadManifestParser.Result.Unsupported, dynamic)
        assertEquals(DashDownloadManifestParser.Result.Unsupported, mismatch)
    }

    @Test
    fun `rejects external entities and enforces chunk bound`() {
        val externalEntity = DashDownloadManifestParser.parse(
            manifest = """
                <!DOCTYPE MPD [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <MPD><Period><AdaptationSet contentType="video">
                  <Representation id="v1"><BaseURL>&xxe;</BaseURL></Representation>
                </AdaptationSet></Period></MPD>
            """.trimIndent(),
            manifestUrl = MANIFEST_URL.toHttpUrl(),
            representationId = "v1",
            expectedTrackType = MediaTrackType.VIDEO,
            maxChunks = 10,
        )
        val tooMany = DashDownloadManifestParser.parse(
            manifest = fixedTemplateManifest(token = "fixture"),
            manifestUrl = MANIFEST_URL.toHttpUrl(),
            representationId = "v720",
            expectedTrackType = MediaTrackType.VIDEO,
            maxChunks = 3,
        )

        assertEquals(DashDownloadManifestParser.Result.Malformed, externalEntity)
        assertEquals(DashDownloadManifestParser.Result.TooManyChunks, tooMany)
    }

    private fun parseSimple(body: String): DashDownloadManifestParser.Result =
        DashDownloadManifestParser.parse(
            manifest = simpleManifest(body),
            manifestUrl = MANIFEST_URL.toHttpUrl(),
            representationId = "v1",
            expectedTrackType = MediaTrackType.VIDEO,
            maxChunks = 10,
        )

    private fun simpleManifest(
        body: String,
        type: String = "static",
        contentType: String = "video",
        mimeType: String = "video/mp4",
    ): String = """
        <MPD type="$type" xmlns="urn:mpeg:dash:schema:mpd:2011">
          <Period>
            <AdaptationSet contentType="$contentType" mimeType="$mimeType">
              $body
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private fun fixedTemplateManifest(token: String): String = """
        <MPD type="static" mediaPresentationDuration="PT6S"
            xmlns="urn:mpeg:dash:schema:mpd:2011">
          <Period>
            <AdaptationSet contentType="video" mimeType="video/mp4"
                codecs="avc1.4d401f">
              <BaseURL>video/</BaseURL>
              <SegmentTemplate timescale="1000" duration="2000" startNumber="5"
                  initialization="init-${'$'}RepresentationID${'$'}.mp4"
                  media="chunk-${'$'}Number%05d${'$'}.m4s?token=$token" />
              <Representation id="v720" bandwidth="800000" width="1280" height="720" />
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private companion object {
        const val MANIFEST_URL = "https://media.example.test/root/manifest.mpd"
    }
}
