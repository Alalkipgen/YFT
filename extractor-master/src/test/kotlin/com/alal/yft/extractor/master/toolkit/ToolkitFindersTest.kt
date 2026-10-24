package com.alal.yft.extractor.master.toolkit

import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3 T1/T2: PageScripts and BalancedJson. */
class ToolkitFindersTest {
    @Test
    fun `balanced slices respect strings, escapes, arrays and the size cap`() {
        val text = """x = {"a":"}{\"","b":[1,{"c":"]"}]}; tail"""
        assertEquals("""{"a":"}{\"","b":[1,{"c":"]"}]}""", BalancedJson.firstObject(text))
        assertEquals("""[1,{"c":"]"}]""", BalancedJson.objectAt(text, text.indexOf('[')))
        assertNull(BalancedJson.objectAt("""{"open":""", 0))
        assertNull(BalancedJson.objectAt("{" + "1,".repeat(50) + "}", 0, maxChars = 20))
        assertEquals("""{"k":1}""", BalancedJson.objectAfter("""var cfg =  {"k":1};""", "var cfg ="))
        val inString = """{"contextJSON":"{\"id\":\"7\"}"}"""
        assertEquals("""{"id":"7"}""", BalancedJson.stringAt(inString, inString.indexOf("\"{")))
    }

    @Test
    fun `lenient parse retries entity-encoded JSON and cuts trailing text once`() {
        val strict = BalancedJson.lenientParse("""{"a":"b"}""")
        assertEquals("b", strict["a"].asStringOrNull)
        val encoded = BalancedJson.lenientParse("{&quot;a&quot;:&quot;x&amp;y&quot;} trailing")
        assertEquals("x&y", encoded["a"].asStringOrNull)
        assertNull(BalancedJson.lenientParse("not json"))
    }

    @Test
    fun `scripts are found by their own id and type, never by a mention`() {
        val html = """<link rel="preload" data-x="SIGI_STATE"><script>var t = "SIGI_STATE";</script>""" +
            """<script id="SIGI_STATE" type="application/json">{"ok":true}</script>""" +
            """<script type='application/ld+json'>{"@type":"VideoObject"}</script><script></script>"""
        val scripts = PageScripts.scripts(html)
        assertEquals(3, scripts.size)
        val data = PageScripts.dataScripts(html, setOf("application/ld+json"), setOf("SIGI_STATE"))
        assertEquals(listOf("SIGI_STATE", null), data.map { it.id })
        assertTrue(data.first().body.startsWith("{\"ok\""))
    }

    @Test
    fun `assigned objects and JSON inside strings are found`() {
        val script = PageScript(
            null, null,
            """window.__STATE__ = {"a":1}; foo.bar["x"] = [2]; if (a == {}) {} ytCfg = {"b":"}"};""",
        )
        assertEquals(listOf("""{"a":1}""", "[2]", """{"b":"}"}"""), PageScripts.assigned(script))
        assertTrue(PageScripts.assigned(PageScript(null, "application/json", "x = {\"a\":1}")).isEmpty())
        val html = """s.handle({"contextJSON":"{\"video_id\":\"1\"}"}); __additionalDataLoaded('/p/',{"x":2});"""
        val documents = PageScripts.embeddedDocuments(html)
        assertEquals(listOf("""{"video_id":"1"}""", """{"x":2}"""), documents)
        assertTrue(documents.all { BalancedJson.lenientParse(it) is JsonValue.Object })
    }
}
