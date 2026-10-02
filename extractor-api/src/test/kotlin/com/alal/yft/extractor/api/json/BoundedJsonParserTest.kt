package com.alal.yft.extractor.api.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedJsonParserTest {
    @Test
    fun `nested site payloads are read with typed accessors`() {
        val json = """
            {
              "itemInfo": {
                "itemStruct": {
                  "id": "7311234567890123456",
                  "desc": "Caption with \"quotes\" and a newline\nhere",
                  "video": {
                    "duration": 15,
                    "ratio": 720.5,
                    "bitrateInfo": [
                      { "Bitrate": 1200000, "GearName": "normal_720" },
                      { "Bitrate": 650000, "GearName": "normal_540" }
                    ]
                  },
                  "isAd": false,
                  "missing": null
                }
              }
            }
        """.trimIndent()

        val root = BoundedJsonParser.parse(json)

        val item = root.path("itemInfo", "itemStruct")
        assertEquals("7311234567890123456", item["id"].asStringOrNull)
        assertEquals(7311234567890123456L, item["id"].asLongOrNull)
        assertTrue(item["desc"].asStringOrNull!!.contains("\"quotes\""))
        assertTrue(item["desc"].asStringOrNull!!.contains('\n'))
        assertEquals(15L, item.path("video", "duration").asLongOrNull)
        assertEquals(720.5, item.path("video", "ratio").asDoubleOrNull!!, 0.0001)
        assertEquals(false, item["isAd"].asBooleanOrNull)
        assertNull(item["missing"].asStringOrNull)
        assertNull(item["absent"].asStringOrNull)

        val bitrates = item.path("video", "bitrateInfo").asArrayOrEmpty
        assertEquals(2, bitrates.size)
        assertEquals("normal_720", bitrates[0]["GearName"].asStringOrNull)
        assertEquals(650000L, bitrates[1]["Bitrate"].asLongOrNull)
    }

    @Test
    fun `mistyped nodes return null instead of throwing`() {
        val root = BoundedJsonParser.parse("""{"a": [1, 2], "b": "text"}""")

        assertNull(root.path("a", "nope"))
        assertNull(root["b"][0])
        assertNull(root["a"].asStringOrNull)
        assertNull(root["b"].asLongOrNull)
        assertEquals(emptyList<JsonValue>(), root["b"].asArrayOrEmpty)
    }

    @Test
    fun `unicode escapes and empty containers are handled`() {
        val root = BoundedJsonParser.parse("""{"t":"a\u0062c\/d","o":{},"a":[]}""")

        assertEquals("abc/d", root["t"].asStringOrNull)
        assertEquals(emptyMap<String, JsonValue>(), (root["o"] as JsonValue.Object).entries)
        assertEquals(emptyList<JsonValue>(), root["a"].asArrayOrEmpty)
    }

    @Test
    fun `large integer ids keep full precision`() {
        val root = BoundedJsonParser.parse("""{"id": 9007199254740993}""")

        assertEquals(9007199254740993L, root["id"].asLongOrNull)
        assertEquals("9007199254740993", (root["id"] as JsonValue.Number).text)
    }

    @Test
    fun `malformed documents are rejected rather than partially accepted`() {
        val malformed = listOf(
            "",
            "   ",
            "{",
            "}",
            """{"a":}""",
            """{"a" 1}""",
            """{a:1}""",
            """{"a":1,}""",
            """[1,]""",
            """{"a":01}""",
            """{"a":1}trailing""",
            """{"a":"unterminated""",
            """{"a":'single'}""",
            """{"a":tru}""",
            """{"a":1.}""",
            """{"a":1e}""",
            """{"a":"\q"}""",
            """{"a":"\uZZZZ"}""",
            "{\"a\":\"raw\u0001control\"}",
            """nullx""",
        )

        malformed.forEach { text ->
            assertNull("accepted: $text", BoundedJsonParser.parse(text))
        }
    }

    @Test
    fun `a leading plus or bare dot is not a number`() {
        assertNull(BoundedJsonParser.parse("""{"a":+1}"""))
        assertNull(BoundedJsonParser.parse("""{"a":.5}"""))
        assertEquals("-0.5", (BoundedJsonParser.parse("""{"a":-0.5}""")["a"] as JsonValue.Number).text)
    }

    @Test
    fun `excessive nesting is refused instead of overflowing the stack`() {
        val deep = "[".repeat(500) + "]".repeat(500)

        assertNull(BoundedJsonParser.parse(deep))
        assertNull(BoundedJsonParser.parse("[[[1]]]", maxDepth = 2))
    }

    @Test
    fun `an oversized node count is refused`() {
        val wide = (1..200).joinToString(prefix = "[", postfix = "]") { "$it" }

        assertNull(BoundedJsonParser.parse(wide, maxNodes = 50))
        assertEquals(200, BoundedJsonParser.parse(wide).asArrayOrEmpty.size)
    }

    @Test
    fun `scalar roots parse`() {
        assertEquals(JsonValue.Null, BoundedJsonParser.parse("null"))
        assertEquals(JsonValue.Bool(true), BoundedJsonParser.parse(" true "))
        assertEquals("x", BoundedJsonParser.parse("\"x\"").asStringOrNull)
        assertEquals(5L, BoundedJsonParser.parse("5").asLongOrNull)
    }
}
