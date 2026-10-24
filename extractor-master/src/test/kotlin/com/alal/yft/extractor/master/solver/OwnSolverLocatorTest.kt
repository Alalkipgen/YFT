package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.master.solver.OwnSolverBreaker.Step
import com.alal.yft.extractor.master.solver.OwnSolverLadderRule.Kind
import com.alal.yft.extractor.master.solver.OwnSolverLocator.appendLocator
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.2 N5b A.4/A.5: the locator cache's data ([OwnSolverLocator]), its store
 * ([OwnSolverLocators]), the V ledger ([OwnSolverVLedger]) and their file ([AtomicTextFile]).
 * Data only and strict: anything not exactly the core's form is refused; a store whose file is
 * corrupt, of another format or core version, or unreadable starts empty and never throws.
 */
class OwnSolverLocatorTest {
    @Test
    fun `parts go out in the core's form and come back the same, empty parts are locate only`() {
        val parts = OwnSolverLocator.Parts(rate = P_PART, signature = B_PART)
        val text = buildString { appendLocator(parts) }
        assertEquals(parts, OwnSolverLocator.decode(BoundedJsonParser.parse(text)))
        assertEquals("{}", buildString { appendLocator(OwnSolverLocator.Parts()) })
        assertEquals(OwnSolverLocator.Parts(rate = P_PART), OwnSolverLocator.decode(BoundedJsonParser.parse("{\"v\":1,\"n\":$P_JSON}")))
        assertEquals(OwnSolverLocator.Parts(), OwnSolverLocator.decode(BoundedJsonParser.parse("{\"v\":1}")))
    }

    @Test
    fun `a part not exactly of the core's form is refused`() {
        listOf(
            // P: keys, candidates (1 to 16, whole 0 to 15, distinct), hash.
            "{\"s\":\"P\",\"c\":[0],\"o\":\"0a1b2c3d\",\"x\":0}",
            "{\"s\":\"P\",\"c\":[],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"P\",\"c\":[${(0..16).joinToString(",")}],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"P\",\"c\":[16],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"P\",\"c\":[1,1],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"P\",\"c\":[-1],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"P\",\"c\":[1.0],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"P\",\"c\":[1e0],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"P\",\"c\":[01],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"P\",\"c\":[\"1\"],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"P\",\"c\":[1],\"o\":\"0A1B2C3D\"}",
            "{\"s\":\"P\",\"c\":[1],\"o\":\"0a1b2c3\"}",
            "{\"s\":\"P\",\"c\":[1]}",
            "{\"s\":\"Q\",\"c\":[1],\"o\":\"0a1b2c3d\"}",
            // B: keys, members (1 to 8 of 4), id ≤ 1e6, shape ≤ 1e5, source hash, size 1 to 1e8, each
            // id and shape once.
            "{\"s\":\"B\",\"c\":[1],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"B\",\"m\":[],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"B\",\"m\":[${(0..8).joinToString(",") { "[$it,0,\"0a1b2c3d\",9]" }}],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"B\",\"m\":[[1,0,\"0a1b2c3d\"]],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"B\",\"m\":[[1000001,0,\"0a1b2c3d\",9]],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"B\",\"m\":[[1,100001,\"0a1b2c3d\",9]],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"B\",\"m\":[[1,0,\"0a1b2c3x\",9]],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"B\",\"m\":[[1,0,\"0a1b2c3d\",0]],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"B\",\"m\":[[1,0,\"0a1b2c3d\",100000001]],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"B\",\"m\":[[1,1,\"0a1b2c3d\",9],[1,1,\"0a1b2c3d\",9]],\"o\":\"0a1b2c3d\"}",
            "{\"s\":\"B\",\"m\":[[1,0,\"0a1b2c3d\",9]],\"o\":\"0a1b2c3d\",\"c\":[1]}",
            "[1]",
        ).forEach { text -> assertNull(text, OwnSolverLocator.decodePart(BoundedJsonParser.parse(text))) }
        listOf(
            "{\"v\":2,\"n\":$P_JSON}",
            "{\"n\":$P_JSON}",
            "{\"v\":1,\"n\":$P_JSON,\"page\":1}",
            "{\"v\":1,\"n\":{\"s\":\"P\",\"c\":[1]}}",
            "{\"v\":1.0}",
        ).forEach { text -> assertNull(text, OwnSolverLocator.decode(BoundedJsonParser.parse(text))) }
        // The bounds themselves are the core's.
        assertEquals(OwnSolverLocator.PPart((0..15).toList(), "0a1b2c3d"), OwnSolverLocator.decodePart(BoundedJsonParser.parse("{\"s\":\"P\",\"c\":[${(0..15).joinToString(",")}],\"o\":\"0a1b2c3d\"}")))
        // One function that won in two shapes is two members (B's groups on the corpus have them).
        val twoShapes = OwnSolverLocator.decodePart(BoundedJsonParser.parse("{\"s\":\"B\",\"m\":[[1,0,\"0a1b2c3d\",9],[1,1,\"0a1b2c3d\",9]],\"o\":\"0a1b2c3d\"}"))
        assertEquals(listOf(0, 1), (twoShapes as OwnSolverLocator.BPart).members.map(OwnSolverLocator.Member::shape))
        val wide = OwnSolverLocator.decodePart(BoundedJsonParser.parse("{\"s\":\"B\",\"m\":[[1000000,100000,\"0a1b2c3d\",100000000]],\"o\":\"0a1b2c3d\"}"))
        assertEquals(OwnSolverLocator.Member(1_000_000, 100_000, "0a1b2c3d", 100_000_000), (wide as OwnSolverLocator.BPart).members.single())
    }

    @Test
    fun `a part's V name is short and its own, and the script hash is SHA-256`() {
        val hash = OwnSolverLocator.hashOf(P_PART)
        assertTrue(Regex("[0-9a-f]{16}").matches(hash))
        assertEquals(hash, OwnSolverLocator.hashOf(P_PART.copy()))
        assertNotEquals(hash, OwnSolverLocator.hashOf(P_PART.copy(candidates = listOf(0, 4))))
        assertNotEquals(hash, OwnSolverLocator.hashOf(B_PART))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", OwnSolverLocator.sha256Hex("abc"))
    }

    @Test
    fun `an entry learned from other player text is dropped, and a part is its step's strategy`() {
        val store = OwnSolverLocators()
        store.put(PLAYER, SCRIPT, Kind.RATE, OwnSolverLocators.Located(Step.WORKER_P, P_PART))
        assertEquals(P_PART, store.get(PLAYER, SCRIPT)?.rate?.part)
        assertNull(store.get(PLAYER, OTHER_SCRIPT))
        assertNull(store.get(PLAYER, SCRIPT))
        assertEquals(0, store.size)
        listOf(Step.WORKER_P to B_PART, Step.WORKER_B to P_PART, Step.PAGE_P to P_PART, Step.PAGE_B to B_PART).forEach { (step, part) ->
            assertTrue(runCatching { OwnSolverLocators.Located(step, part) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `at most max entries, the least recently used go first, and a put of other text replaces the entry`() {
        val store = OwnSolverLocators(maxEntries = 2)
        val located = OwnSolverLocators.Located(Step.WORKER_P, P_PART)
        store.put("aaaa", SCRIPT, Kind.RATE, located)
        store.put("bbbb", SCRIPT, Kind.RATE, located)
        assertTrue(store.get("aaaa", SCRIPT) != null)
        store.put("cccc", SCRIPT, Kind.RATE, located)
        assertEquals(2, store.size)
        assertNull(store.peek("bbbb"))
        assertTrue(store.peek("aaaa") != null && store.peek("cccc") != null)

        store.put("aaaa", SCRIPT, Kind.SIGNATURE, OwnSolverLocators.Located(Step.WORKER_B, B_PART))
        assertEquals(P_PART, store.peek("aaaa")?.rate?.part)
        store.put("aaaa", OTHER_SCRIPT, Kind.SIGNATURE, OwnSolverLocators.Located(Step.WORKER_B, B_PART))
        assertEquals(OTHER_SCRIPT, store.peek("aaaa")?.scriptHash)
        assertNull(store.peek("aaaa")?.rate)
    }

    @Test
    fun `drop takes one kind's part, only its step's when named, and an empty entry goes`() {
        val file = MemoryTextFile()
        val store = OwnSolverLocators(file)
        store.put(PLAYER, SCRIPT, Kind.RATE, OwnSolverLocators.Located(Step.WORKER_P, P_PART))
        store.put(PLAYER, SCRIPT, Kind.SIGNATURE, OwnSolverLocators.Located(Step.WORKER_B, B_PART))
        val writes = file.writes
        // The same part again is no change: nothing is written.
        store.put(PLAYER, SCRIPT, Kind.RATE, OwnSolverLocators.Located(Step.WORKER_P, P_PART))
        assertEquals(writes, file.writes)

        store.drop(PLAYER, Kind.SIGNATURE, Step.WORKER_P)
        assertEquals(B_PART, store.peek(PLAYER)?.signature?.part)
        store.drop(PLAYER, Kind.SIGNATURE, Step.WORKER_B)
        assertNull(store.peek(PLAYER)?.signature)
        store.drop(PLAYER, Kind.RATE)
        assertNull(store.peek(PLAYER))
        assertEquals(0, store.size)
    }

    @Test
    fun `entries survive a restart through the file, read only on first use`() {
        val file = MemoryTextFile()
        val first = OwnSolverLocators(file)
        first.put(PLAYER, SCRIPT, Kind.RATE, OwnSolverLocators.Located(Step.WORKER_P, P_PART))
        first.put(PLAYER, SCRIPT, Kind.SIGNATURE, OwnSolverLocators.Located(Step.WORKER_B, B_PART))
        first.put("zzzz", OTHER_SCRIPT, Kind.RATE, OwnSolverLocators.Located(Step.WORKER_P, P_PART))

        val reads = file.reads
        val restarted = OwnSolverLocators(file)
        assertEquals(reads, file.reads)
        val entry = restarted.get(PLAYER, SCRIPT)
        assertEquals(reads + 1, file.reads)
        assertEquals(OwnSolverLocators.Located(Step.WORKER_P, P_PART), entry?.rate)
        assertEquals(OwnSolverLocators.Located(Step.WORKER_B, B_PART), entry?.signature)
        assertEquals(2, restarted.size)
        // Data only: no player text, no input, no value; ids, hashes and numbers.
        val text = requireNotNull(file.text)
        assertTrue(text.startsWith("{\"format\":1,\"core\":1,\"entries\":["))
        restarted.get(PLAYER, SCRIPT)
        assertEquals(reads + 1, file.reads)
    }

    @Test
    fun `a corrupt file, another format or core version gives an empty cache, never a crash`() {
        val good = OwnSolverLocators.encodeFile(
            mapOf(PLAYER to OwnSolverLocators.Entry(SCRIPT, OwnSolverLocators.Located(Step.WORKER_P, P_PART), null)),
        )
        assertEquals(1, OwnSolverLocators.decodeFile(good, 32)?.size)
        val entry = "{\"player\":\"$PLAYER\",\"script\":\"$SCRIPT\",\"n\":{\"step\":\"P\",\"part\":$P_JSON}}"
        listOf(
            "",
            "{",
            "[]",
            good.replace("\"format\":1", "\"format\":2"),
            good.replace("\"core\":1", "\"core\":2"),
            good.replace("\"core\":1,", ""),
            good.replace("{\"format\"", "{\"extra\":0,\"format\""),
            "{\"format\":1,\"core\":1,\"entries\":[$entry,$entry]}",
            "{\"format\":1,\"core\":1,\"entries\":[${entry.replace(PLAYER, "a b")}]}",
            "{\"format\":1,\"core\":1,\"entries\":[${entry.replace(SCRIPT, SCRIPT.uppercase())}]}",
            "{\"format\":1,\"core\":1,\"entries\":[${entry.replace("\"step\":\"P\"", "\"step\":\"B\"")}]}",
            "{\"format\":1,\"core\":1,\"entries\":[${entry.replace("\"step\":\"P\"", "\"step\":\"PAGE_P\"")}]}",
            "{\"format\":1,\"core\":1,\"entries\":[${entry.replace(",\"n\":{\"step\":\"P\",\"part\":$P_JSON}", "")}]}",
            "{\"format\":1,\"core\":1,\"entries\":[${entry.replace("\"c\":[0,3]", "\"c\":[0,99]")}]}",
            "{\"format\":1,\"core\":1,\"entries\":[${entry.replace("\"script\"", "\"text\":\"x\",\"script\"")}]}",
        ).forEach { text ->
            assertNull(text, OwnSolverLocators.decodeFile(text, 32))
            val store = OwnSolverLocators(MemoryTextFile(text))
            assertNull(text, store.get(PLAYER, SCRIPT))
            assertEquals(text, 0, store.size)
        }
        // More entries than the store holds: not this store's file.
        assertNull(OwnSolverLocators.decodeFile(good, 0))
    }

    @Test
    fun `a file that throws is an empty cache that still works in memory`() {
        val store = OwnSolverLocators(ThrowingTextFile())
        assertNull(store.get(PLAYER, SCRIPT))
        store.put(PLAYER, SCRIPT, Kind.RATE, OwnSolverLocators.Located(Step.WORKER_P, P_PART))
        assertEquals(P_PART, store.get(PLAYER, SCRIPT)?.rate?.part)
        val ledger = OwnSolverVLedger(ThrowingTextFile())
        ledger.pass(V_KEY)
        assertTrue(ledger.passed(V_KEY))
    }

    @Test
    fun `V passes survive a restart through the file, other keys are not passed`() {
        val file = MemoryTextFile()
        OwnSolverVLedger(file).pass(V_KEY)
        val restarted = OwnSolverVLedger(file)
        assertEquals(1, file.writes)
        assertTrue(restarted.passed(V_KEY))
        assertFalse(restarted.passed(V_KEY.replace("|-", "|0123456789abcdef")))
        assertFalse(restarted.passed(V_KEY.replace("WORKER_P", "WORKER_B")))
        // A pass already kept is not written again.
        restarted.pass(V_KEY)
        assertEquals(1, file.writes)
    }

    @Test
    fun `the ledger keeps at most max keys, the oldest go first, and only keys of the form`() {
        val ledger = OwnSolverVLedger(maxKeys = 2)
        val keys = listOf("aaaa", "bbbb", "cccc").map { V_KEY.replace(PLAYER, it) }
        keys.forEach(ledger::pass)
        assertFalse(ledger.passed(keys[0]))
        assertTrue(ledger.passed(keys[1]) && ledger.passed(keys[2]))
        listOf("x|WORKER_P||-", "$PLAYER|WORKER_P||", "$PLAYER|STEP||-", "$PLAYER|WORKER_P|abc|-", "$PLAYER|WORKER_P||0123").forEach { key ->
            ledger.pass(key)
            assertFalse(key, ledger.passed(key))
        }
        // An unknown script hash is an empty field (still its own key).
        ledger.pass("$PLAYER|PAGE_B||-")
        assertTrue(ledger.passed("$PLAYER|PAGE_B||-"))
    }

    @Test
    fun `a corrupt ledger file or another format is an empty ledger`() {
        val good = OwnSolverVLedger.encode(listOf(V_KEY))
        assertEquals(listOf(V_KEY), OwnSolverVLedger.decode(good, 64))
        listOf(
            "",
            "{\"format\":2,\"passed\":[\"$V_KEY\"]}",
            "{\"format\":1,\"passed\":[\"$V_KEY\",\"$V_KEY\"]}",
            "{\"format\":1,\"passed\":[\"not a key\"]}",
            "{\"format\":1,\"passed\":[1]}",
            "{\"format\":1,\"passed\":[\"$V_KEY\"],\"x\":0}",
            "{\"format\":1}",
        ).forEach { text ->
            assertNull(text, OwnSolverVLedger.decode(text, 64))
            assertFalse(text, OwnSolverVLedger(MemoryTextFile(text)).passed(V_KEY))
        }
        assertNull(OwnSolverVLedger.decode(good, 0))
    }

    @Test
    fun `the atomic file writes whole text through a temporary file and reads it back`() {
        val dir = Files.createTempDirectory("yft-locators").toFile()
        try {
            val target = File(dir, "own-solver/locators.json")
            val file = AtomicTextFile(target)
            assertNull(file.read())
            assertTrue(file.write("{\"a\":1}"))
            assertEquals("{\"a\":1}", file.read())
            assertTrue(file.write("{}"))
            assertEquals("{}", AtomicTextFile(target).read())
            assertFalse(File(target.parentFile, "locators.json.tmp").exists())

            val small = AtomicTextFile(target, maxChars = 4)
            assertFalse(small.write("12345"))
            assertTrue(file.write("12345"))
            assertNull(small.read())

            // A write that fails before the rename leaves the old text whole.
            val temp = File(target.parentFile, "locators.json.tmp")
            temp.mkdirs()
            assertFalse(file.write("{\"b\":2}"))
            assertEquals("12345", file.read())
            temp.delete()

            // A directory in the way, or a location that throws: false and null, never a crash.
            val blocked = File(dir, "blocked")
            File(blocked, "inside").mkdirs()
            assertFalse(AtomicTextFile(blocked).write("{}"))
            assertNull(AtomicTextFile(blocked).read())
            val nowhere = AtomicTextFile { error("no files dir") }
            assertNull(nowhere.read())
            assertFalse(nowhere.write("{}"))
            // A file gone between the check and the read (the read throws): null, never a crash.
            val gone = object : File(dir, "gone.json") {
                override fun isFile(): Boolean = true
            }
            assertNull(AtomicTextFile(gone).read())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the stores use the atomic file across a restart`() {
        val dir = Files.createTempDirectory("yft-locators").toFile()
        try {
            val locators = File(dir, "own-solver/locators.json")
            val ledger = File(dir, "own-solver/v-ledger.json")
            OwnSolverLocators(AtomicTextFile(locators)).put(PLAYER, SCRIPT, Kind.RATE, OwnSolverLocators.Located(Step.WORKER_P, P_PART))
            OwnSolverVLedger(AtomicTextFile(ledger)).pass(V_KEY)
            assertEquals(P_PART, OwnSolverLocators(AtomicTextFile(locators)).get(PLAYER, SCRIPT)?.rate?.part)
            assertTrue(OwnSolverVLedger(AtomicTextFile(ledger)).passed(V_KEY))
            locators.writeText("{\"format\":1,\"core\":1,\"entries\":[")
            assertNull(OwnSolverLocators(AtomicTextFile(locators)).get(PLAYER, SCRIPT))
        } finally {
            dir.deleteRecursively()
        }
    }

    private class MemoryTextFile(var text: String? = null) : OwnSolverTextFile {
        var reads = 0
        var writes = 0

        override fun read(): String? {
            reads += 1
            return text
        }

        override fun write(text: String): Boolean {
            writes += 1
            this.text = text
            return true
        }
    }

    private class ThrowingTextFile : OwnSolverTextFile {
        override fun read(): String? = throw IllegalStateException("disk")

        override fun write(text: String): Boolean = throw IllegalStateException("disk")
    }

    private companion object {
        const val PLAYER = "f1x7ure0"
        val SCRIPT = "0123456789abcdef".repeat(4)
        val OTHER_SCRIPT = "fedcba9876543210".repeat(4)
        val P_PART = OwnSolverLocator.PPart(listOf(0, 3), "0a1b2c3d")
        val B_PART = OwnSolverLocator.BPart(
            listOf(OwnSolverLocator.Member(41, 2, "00ff00ff", 1234), OwnSolverLocator.Member(7, 0, "12345678", 99)),
            "deadbeef",
        )
        const val P_JSON = "{\"s\":\"P\",\"c\":[0,3],\"o\":\"0a1b2c3d\"}"
        val V_KEY = "$PLAYER|WORKER_P|$SCRIPT|-"
    }
}
