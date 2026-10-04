package com.alal.yft.detection.potoken

import com.alal.yft.detection.JsonText
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.get
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotGuardProtocolTest {
    @Test
    fun `the attestation key is read from either player build`() {
        val desktop = """a.b={"X-Goog-Api-Key"]:"$TEST_KEY",c:1}"""
        val phone = """d["X-Goog-Api-Key"]="$TEST_KEY";"""
        assertEquals(TEST_KEY, BotGuardProtocol.attestationKey(desktop))
        assertEquals(TEST_KEY, BotGuardProtocol.attestationKey(phone))
        assertNull(BotGuardProtocol.attestationKey("""{"X-Goog-Api-Key":"short"}"""))
        assertNull(BotGuardProtocol.attestationKey("function player(){}"))
    }

    @Test
    fun `a scrambled challenge is descrambled like the player does`() {
        val challenge = BotGuardProtocol.parseChallenge(
            """["message",${quoted(scramble(challengeFields()))}]""",
        )!!
        assertEquals(INTERPRETER, challenge.interpreter)
        assertEquals("program-text", challenge.program)
        assertEquals("trayride", challenge.globalName)
        assertFalse(challenge.toString().contains(INTERPRETER))
    }

    @Test
    fun `a plain challenge is read as is`() {
        val challenge = BotGuardProtocol.parseChallenge("[${challengeFields()}]")!!
        assertEquals(INTERPRETER, challenge.interpreter)
        assertEquals("trayride", challenge.globalName)
    }

    @Test
    fun `challenges that would load code from elsewhere or lack a part are refused`() {
        val urlOnly = """["m",null,["//www.google.com/js/th/x.js"],"h","program","trayride"]"""
        val noProgram = """["m",["script"],null,"h",null,"trayride"]"""
        val badName = """["m",["script"],null,"h","program","window.top"]"""
        listOf(urlOnly, noProgram, badName).forEach { fields ->
            assertNull(fields, BotGuardProtocol.parseChallenge("[$fields]"))
        }
        assertNull(BotGuardProtocol.parseChallenge("""["m","%%%not base64%%%"]"""))
        assertNull(BotGuardProtocol.parseChallenge("{}"))
        assertNull(BotGuardProtocol.parseChallenge("not json"))
    }

    @Test
    fun `the integrity answer keeps token lifetime and refresh margin`() {
        val integrity = BotGuardProtocol.parseIntegrity("""["$INTEGRITY",43200,100,"fallback"]""")!!
        assertEquals(INTEGRITY, integrity.token)
        assertEquals(43_200L, integrity.lifetimeSeconds)
        assertEquals(100L, integrity.refreshSeconds)
        assertFalse(integrity.toString().contains(INTEGRITY))

        val bare = BotGuardProtocol.parseIntegrity("""["$INTEGRITY"]""")!!
        assertEquals(3_600L, bare.lifetimeSeconds)
        assertEquals(0L, bare.refreshSeconds)
        val capped = BotGuardProtocol.parseIntegrity("""["$INTEGRITY",100,900]""")!!
        assertEquals(50L, capped.refreshSeconds)
        assertNull(BotGuardProtocol.parseIntegrity("""["not a token!"]"""))
        assertNull(BotGuardProtocol.parseIntegrity("""[null,43200]"""))
    }

    @Test
    fun `request bodies and page documents are valid JSON with every value intact`() {
        val answer = "answer \"quoted\" \\ back\nline \u2028 é"
        val body = BoundedJsonParser.parse(BotGuardProtocol.generateBody(answer)) as JsonValue.Array
        assertEquals(BotGuardProtocol.REQUEST_KEY, (body[0] as JsonValue.Text).value)
        assertEquals(answer, (body[1] as JsonValue.Text).value)
        assertEquals("[\"O43z0dpjhgX20SCx4KAo\"]", BotGuardProtocol.CREATE_BODY)

        val document = BotGuardProtocol.challengeDocument(
            BotGuardProtocol.Challenge(answer, "program", "trayride"),
        )
        assertTrue(document.all { it.code in 0x20..0x7e })
        val parsed = BoundedJsonParser.parse(document)
        assertEquals(answer, (parsed["interpreter"] as JsonValue.Text).value)
        assertEquals("trayride", (parsed["globalName"] as JsonValue.Text).value)
    }

    @Test
    fun `page calls accept only plain identifiers and tokens`() {
        assertEquals("window.yftPoToken.snapshot(4)", BotGuardProtocol.snapshotCall(4))
        assertEquals(
            "window.yftPoToken.mint(5,\"dQw4w9WgXcQ\")",
            BotGuardProtocol.mintCall(5, "dQw4w9WgXcQ"),
        )
        assertEquals(
            "window.yftPoToken.createMinter(6,\"$INTEGRITY\")",
            BotGuardProtocol.createMinterCall(6, INTEGRITY),
        )
        assertEquals(
            "window.yftPoToken.mint(7,\"CgtWaXNpdG9y%3D%3D\")",
            BotGuardProtocol.mintCall(7, "CgtWaXNpdG9y%3D%3D"),
        )
        assertTrue(BotGuardProtocol.BINDING.matches("delegated0123||user0123"))
        listOf("a\");alert(1);(\"", "", "x".repeat(513), "name with space", "a\\b").forEach { bad ->
            assertTrue(runCatching { BotGuardProtocol.mintCall(1, bad) }.isFailure)
        }
        assertTrue(runCatching { BotGuardProtocol.createMinterCall(1, "\"x\"") }.isFailure)
    }

    @Test
    fun `page replies are bounded and error codes carry no page text`() {
        val reply = BotGuardProtocol.parseReply("""{"id":3,"value":"$MINTED","error":null}""")!!
        assertEquals(3, reply.id)
        assertEquals(MINTED, reply.value)
        assertNull(reply.error)
        assertFalse(reply.toString().contains(MINTED))

        val failed = BotGuardProtocol.parseReply("""{"id":2,"value":null,"error":"minter"}""")!!
        assertNull(failed.value)
        assertEquals("minter", failed.error)
        val chatty = """{"id":2,"value":null,"error":"TypeError: x at https://e.test/"}"""
        assertNull(BotGuardProtocol.parseReply(chatty)!!.error)

        assertNull(BotGuardProtocol.parseReply(null))
        assertNull(BotGuardProtocol.parseReply("""{"value":"x"}"""))
        assertNull(BotGuardProtocol.parseReply("""{"id":-1,"value":"x"}"""))
        assertNull(BotGuardProtocol.parseReply("x".repeat(BotGuardProtocol.MAX_REPLY_CHARS + 1)))
        assertTrue(BotGuardProtocol.MINTED_TOKEN.matches(MINTED))
        assertFalse(BotGuardProtocol.MINTED_TOKEN.matches("short"))
    }

    private fun challengeFields(): String =
        """["message",[null,${quoted(INTERPRETER)}],null,"hash","program-text","trayride",""" +
            """null,"blob"]"""

    /** The inverse of the player's descrambling: each byte shifted down by 97, then base64. */
    private fun scramble(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8).map { (it - 97).toByte() }.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun quoted(text: String): String = buildString {
        JsonText.appendString(this, text)
    }

    private companion object {
        /** Not a real key: the player's attestation key is read at run time, never stored. */
        const val TEST_KEY = "TestOnlyAttestationKey_0123456789"
        const val INTERPRETER = "(function(){var trayride={a:function(){}};})(); // é \"q\""
        const val INTEGRITY = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo-_0123456789"
        const val MINTED = "TWludGVkVG9rZW5Gb3JUZXN0aW5nT25seV8wMTIzNDU2Nzg5"
    }
}
