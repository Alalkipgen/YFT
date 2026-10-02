package com.alal.yft.detection.script

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EjsSolverProtocolTest {
    private val batch = EjsSolverProtocol.Batch(
        rateInputs = listOf("rateA", "rateB"),
        signatureInputs = listOf("sig=A"),
    )

    @Test
    fun `a raw player job carries the exact script and both request kinds`() {
        val player = "var a=\"q\\\\\";\n\tb='\u00e9\u2028'+\ud83d\ude00+\ud800;"

        val input = EjsSolverProtocol.encode(EjsSolverProtocol.Player.Raw(player), batch, true)

        assertTrue(input.all { it.code in 0x20..0x7e })
        val root = BoundedJsonParser.parse(input)
        assertEquals("player", root["type"].asStringOrNull)
        assertEquals(true, root["output_preprocessed"].asBooleanOrNull)
        assertEquals(player, (root["player"] as JsonValue.Text).value)
        val requests = root["requests"].asArrayOrEmpty
        assertEquals(listOf("n", "sig"), requests.map { it["type"].asStringOrNull })
        assertEquals(
            listOf("rateA", "rateB"),
            requests[0]["challenges"].asArrayOrEmpty.map { it.asStringOrNull },
        )
        assertEquals(
            listOf("sig=A"),
            requests[1]["challenges"].asArrayOrEmpty.map { it.asStringOrNull },
        )
    }

    @Test
    fun `a preprocessed job names the reduced player and leaves out empty kinds`() {
        val onlyRate = EjsSolverProtocol.Batch(listOf("rateA"), emptyList())

        val input = EjsSolverProtocol.encode(
            EjsSolverProtocol.Player.Preprocessed("reduced"),
            onlyRate,
            keepPreprocessed = true,
        )

        val root = BoundedJsonParser.parse(input)
        assertEquals("preprocessed", root["type"].asStringOrNull)
        assertEquals("reduced", root["preprocessed_player"].asStringOrNull)
        assertNull(root["output_preprocessed"])
        assertEquals(listOf("n"), root["requests"].asArrayOrEmpty.map { it["type"].asStringOrNull })
    }

    @Test
    fun `results are read per kind and only for inputs that were asked`() {
        val output = """
            {"type":"result","preprocessed_player":"reduced",
             "responses":[
               {"type":"result","data":{"rateA":"outA","rateB":"","extra":"injected"}},
               {"type":"result","data":{"sig=A":"signed"}}
             ]}
        """.trimIndent()

        val decoded = EjsSolverProtocol.decode(output, batch) as EjsSolverProtocol.Decoded.Solved

        assertEquals(mapOf("rateA" to "outA"), decoded.rate)
        assertEquals(mapOf("sig=A" to "signed"), decoded.signature)
        assertEquals("reduced", decoded.preprocessedPlayer)
        assertFalse(decoded.toString().contains("outA"))
    }

    @Test
    fun `a kind the solver could not handle comes back empty`() {
        val output = """
            {"type":"result","responses":[
              {"type":"error","error":"Failed to extract n function"},
              {"type":"result","data":{"sig=A":"signed"}}
            ]}
        """.trimIndent()

        val decoded = EjsSolverProtocol.decode(output, batch) as EjsSolverProtocol.Decoded.Solved

        assertTrue(decoded.rate.isEmpty())
        assertEquals(mapOf("sig=A" to "signed"), decoded.signature)
        assertNull(decoded.preprocessedPlayer)
    }

    @Test
    fun `failed runs and foreign documents are told apart`() {
        assertEquals(
            EjsSolverProtocol.Decoded.Failed,
            EjsSolverProtocol.decode("{\"type\":\"error\",\"error\":\"parse\"}", batch),
        )
        listOf(
            "",
            "not json",
            "{\"type\":\"other\"}",
            "{\"type\":\"result\",\"responses\":[]}",
            "{\"type\":\"result\",\"responses\":[{},{},{}]}",
        ).forEach { output ->
            assertNull(output, EjsSolverProtocol.decode(output, batch))
        }
    }
}
