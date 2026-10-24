package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.3 D0: the key-name shape of an answer and its dictionary encoding. */
class ShapeFingerprintTest {
    @Test
    fun `only key names are kept, arrays collapse and odd or redacted keys become a star`() {
        val root = parse(
            """{"playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[{"itag":1,"url":"https://x"},""" +
                """{"itag":2,"signatureCipher":"s=SigValue"}]},"videoDetails":{"videoId":"jNQXAC9IVRw","jNQXAC9IVRw":1,"bad key":2},""" +
                """"responseContext":{"visitorData":"secretVisitor"}}""",
        )

        val shape = ShapeFingerprint.of(root, redact = setOf("jNQXAC9IVRw"))

        assertEquals(
            listOf(
                "playabilityStatus", "playabilityStatus.status", "responseContext", "streamingData",
                "streamingData.adaptiveFormats", "streamingData.adaptiveFormats[].itag",
                "streamingData.adaptiveFormats[].signatureCipher", "streamingData.adaptiveFormats[].url",
                "videoDetails", "videoDetails.*", "videoDetails.videoId",
            ),
            shape.core,
        )
        assertEquals(12, shape.fullPaths)
        assertFalse(shape.truncated)
        for (shown in listOf(shape.toString(), shape.core.joinToString())) {
            for (value in listOf("secretVisitor", "jNQXAC9IVRw", "https", "OK", "SigValue")) assertFalse(value, shown.contains(value))
        }
    }

    @Test
    fun `the hash follows the key set, not the order or the values`() {
        val first = ShapeFingerprint.of(parse("""{"a":1,"b":{"c":"x"}}"""))
        val reordered = ShapeFingerprint.of(parse("""{"b":{"c":"y"},"a":2}"""))
        val renamed = ShapeFingerprint.of(parse("""{"a":1,"b":{"d":"x"}}"""))

        assertEquals(first.fullHash, reordered.fullHash)
        assertNotEquals(first.fullHash, renamed.fullHash)
        assertTrue(Regex("^[0-9a-f]{12}$").matches(first.coreHash))
        assertEquals(ShapeFingerprint.hashOf(first.core), first.coreHash)
        assertEquals(ShapeFingerprint.hashOf(listOf("b", "a")), ShapeFingerprint.hashOf(listOf("a", "b")))
    }

    @Test
    fun `the walk is bounded in depth and paths`() {
        val deep = (1..20).joinToString("") { "{\"k$it\":" } + "1" + "}".repeat(20)
        val deepShape = ShapeFingerprint.of(parse(deep))
        assertTrue(deepShape.truncated)
        assertTrue(deepShape.fullPaths <= ShapeFingerprint.MAX_DEPTH)

        val wide = (1..2_500).joinToString(",", "{", "}") { "\"k$it\":1" }
        val wideShape = ShapeFingerprint.of(parse(wide))
        assertTrue(wideShape.truncated)
        assertEquals(ShapeFingerprint.MAX_PATHS, wideShape.fullPaths)
    }

    @Test
    fun `the parser's row paths are BREAKING when removed, its negative signals are not`() {
        for (path in listOf("playabilityStatus.status", "streamingData.adaptiveFormats[].url", "streamingData.formats[].itag", "videoDetails.lengthSeconds")) {
            assertTrue(path, path in ShapeFingerprint.PARSER_READS)
        }
        for (path in listOf("streamingData.adaptiveFormats[].signatureCipher", "streamingData.serverAbrStreamingUrl", "videoDetails.isLive", "playabilityStatus.reason")) {
            assertFalse(path, path in ShapeFingerprint.PARSER_READS)
        }
        assertEquals("streamingData", ShapeFingerprint.rootOf("streamingData.formats[].url"))
        assertEquals("a", ShapeFingerprint.rootOf("a[].b"))
    }

    @Test
    fun `the dictionary lists each path once and the bits give the paths back`() {
        val first = listOf("a", "a.b", "a.b[].c", "d")
        val second = listOf("a", "a.b[].e", "f")

        val groups = ShapeDictionary.groups(listOf(first, second))
        val order = ShapeDictionary.flatten(groups)
        val bits = ShapeDictionary.bits(order, first)

        assertEquals(listOf(":a,d,f", "a:b", "a.b[]:c,e"), groups)
        assertEquals(listOf("a", "d", "f", "a.b", "a.b[].c", "a.b[].e"), order)
        assertEquals("d8", bits)
        assertEquals(first, ShapeDictionary.decode(order, bits))
        assertEquals(second.sorted(), ShapeDictionary.decode(order, ShapeDictionary.bits(order, second)))
        assertNull(ShapeDictionary.decode(order, "zz"))
        assertNull(ShapeDictionary.decode(order, bits + "0"))
    }

    @Test
    fun `a real answer's core round-trips through the dictionary`() {
        val shape = ShapeFingerprint.of(parse(Census.visionOs(Census.V1)), redact = setOf(Census.V1))
        val order = ShapeDictionary.flatten(ShapeDictionary.groups(listOf(shape.core)))

        assertEquals(shape.core, ShapeDictionary.decode(order, ShapeDictionary.bits(order, shape.core)))
        assertTrue(shape.core.contains("streamingData.hlsManifestUrl"))
        assertFalse(shape.core.any { it.startsWith("responseContext.") })
    }

    private fun parse(text: String): JsonValue = requireNotNull(BoundedJsonParser.parse(text, maxDepth = 64, maxNodes = 100_000))
}
