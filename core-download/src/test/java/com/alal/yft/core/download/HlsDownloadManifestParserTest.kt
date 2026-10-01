package com.alal.yft.core.download

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsDownloadManifestParserTest {
    @Test
    fun `parses VOD map and explicit plus implicit byte ranges in output order`() {
        val result = HlsDownloadManifestParser.parse(
            manifest = """
                #EXTM3U
                #EXT-X-VERSION:7
                #EXT-X-TARGETDURATION:4
                #EXT-X-MAP:URI="init.mp4",BYTERANGE="8@0"
                #EXTINF:4,
                #EXT-X-BYTERANGE:4@8
                media.mp4
                #EXTINF:4,
                #EXT-X-BYTERANGE:4
                media.mp4
                #EXT-X-ENDLIST
            """.trimIndent(),
            manifestUrl = "https://media.example.test/path/track.m3u8".toHttpUrl(),
            maxChunks = 10,
        ) as HlsDownloadManifestParser.Result.Parsed

        assertEquals(3, result.chunks.size)
        assertEquals(HlsChunkType.INITIALIZATION, result.chunks[0].type)
        assertEquals(ResolvedByteRange(offset = 0, length = 8), result.chunks[0].byteRange)
        assertEquals(ResolvedByteRange(offset = 8, length = 4), result.chunks[1].byteRange)
        assertEquals(ResolvedByteRange(offset = 12, length = 4), result.chunks[2].byteRange)
        assertTrue(result.chunks[2].url.toString().endsWith("/path/media.mp4"))
        assertFalse(result.chunks[0].toString().contains("media.example.test"))
    }

    @Test
    fun `fingerprint ignores signed query changes but preserves chunk identity`() {
        fun parse(token: String): HlsDownloadManifestParser.Result.Parsed =
            HlsDownloadManifestParser.parse(
                manifest = """
                    #EXTM3U
                    #EXT-X-TARGETDURATION:4
                    #EXTINF:4,
                    segment.ts?token=$token
                    #EXT-X-ENDLIST
                """.trimIndent(),
                manifestUrl = "https://media.example.test/track.m3u8".toHttpUrl(),
                maxChunks = 10,
            ) as HlsDownloadManifestParser.Result.Parsed

        assertEquals(parse("one").fingerprint, parse("two").fingerprint)
    }

    @Test
    fun `rejects encrypted playlists before exposing chunks`() {
        val result = HlsDownloadManifestParser.parse(
            manifest = """
                #EXTM3U
                #EXT-X-TARGETDURATION:4
                #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
                #EXTINF:4,
                segment.ts
                #EXT-X-ENDLIST
            """.trimIndent(),
            manifestUrl = "https://media.example.test/track.m3u8".toHttpUrl(),
            maxChunks = 10,
        )

        assertEquals(HlsDownloadManifestParser.Result.DrmProtected, result)
    }

    @Test
    fun `rejects master and live playlists for selected track export`() {
        val master = HlsDownloadManifestParser.parse(
            manifest = """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=800000
                child.m3u8
                #EXT-X-ENDLIST
            """.trimIndent(),
            manifestUrl = "https://media.example.test/master.m3u8".toHttpUrl(),
            maxChunks = 10,
        )
        val live = HlsDownloadManifestParser.parse(
            manifest = """
                #EXTM3U
                #EXT-X-TARGETDURATION:4
                #EXTINF:4,
                segment.ts
            """.trimIndent(),
            manifestUrl = "https://media.example.test/live.m3u8".toHttpUrl(),
            maxChunks = 10,
        )

        assertEquals(HlsDownloadManifestParser.Result.Unsupported, master)
        assertEquals(HlsDownloadManifestParser.Result.Unsupported, live)
    }

    @Test
    fun `enforces chunk count bound`() {
        val result = HlsDownloadManifestParser.parse(
            manifest = """
                #EXTM3U
                #EXT-X-TARGETDURATION:4
                #EXTINF:4,
                one.ts
                #EXTINF:4,
                two.ts
                #EXT-X-ENDLIST
            """.trimIndent(),
            manifestUrl = "https://media.example.test/track.m3u8".toHttpUrl(),
            maxChunks = 1,
        )

        assertEquals(HlsDownloadManifestParser.Result.TooManyChunks, result)
    }
}
