package com.alal.yft.core.download

import java.util.Base64

/**
 * D1: small HLS pieces made with FFmpeg 7.0.2, kept as Base64 text in `src/test/resources/hls`:
 *
 * - `av-0.ts`, `av-1.ts`: 2 s of H.264 High 160×90 at 10 fps (B frames, a keyframe a second)
 *   with AAC LC mono at 22.05 kHz, the picture starting 46 ms after the sound: `ffmpeg -f lavfi
 *   -i testsrc=size=160x90:rate=10 -f lavfi -i sine=frequency=440:sample_rate=22050 -t 2
 *   -c:v libx264 -profile:v high -pix_fmt yuv420p -bf 2 -g 10 -sc_threshold 0 -b:v 30k -c:a aac
 *   -ac 1 -b:a 24k -f hls -hls_time 1 -hls_list_size 0 -hls_segment_filename av-%d.ts av.m3u8`;
 * - `audio-0.ts`, `audio-1.ts`: the same sound alone (`-vn`), the first two of its pieces;
 * - `mpeg2.ts`: 1 s of MPEG-2 video at 32×32 (`-c:v mpeg2video -an -f mpegts`), which the
 *   remux does not support.
 */
internal object HlsFixtures {
    fun bytes(name: String): ByteArray {
        val stream = HlsFixtures::class.java.classLoader?.getResourceAsStream("hls/$name.b64")
        val text = requireNotNull(stream) { "Missing fixture $name" }
            .use { it.readBytes().decodeToString() }
        return Base64.getMimeDecoder().decode(text)
    }
}
