package com.alal.yft.core.media.resolver

import com.alal.yft.core.media.resolver.PlaylistReadAhead.Quality
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Playlist read-ahead: only reads the one-at-a-time size loop is sure to make start early, so the
 * loop's requests and budget stay its own.
 */
class PlaylistReadAheadTest {
    /** The seven-quality master whose first quality is gone and has the other sound. */
    private val firstGone = PlaylistReadAhead(
        listOf(Quality("q0", "a")) + (1..6).map { Quality("q$it", "b") },
        budget = 6,
    )

    @Test
    fun aSoundIsNotReadAheadForAQualityThatMayFail() {
        // q0's sound may be asked, so a fifth quality may be over the budget.
        assertEquals(listOf("q0", "q1", "q2", "q3"), firstGone.sure(emptyMap()))
        // q0 is gone: its sound is never asked and q4 fits.
        assertEquals(
            listOf("q0", "q1", "q2", "q3", "q4"),
            firstGone.sure(mapOf("q0" to false)),
        )
        // q1 has a length: its sound comes right after it, and q5 is over the budget.
        assertEquals(
            listOf("q0", "q1", "b", "q2", "q3", "q4"),
            firstGone.sure(mapOf("q0" to false, "q1" to true)),
        )
    }

    @Test
    fun aSoundIsSureOnceAnyQualityOfItsGroupHasALength() {
        // q2 has a length but q1 is still open: b follows q1 or q2, inside the budget either way.
        val sure = firstGone.sure(mapOf("q2" to true))
        assertTrue("b" in sure)
        assertEquals(listOf("q0", "q1", "q2", "b", "q3"), sure)
    }

    @Test
    fun qualitiesWithoutSoundStopAtTheBudget() {
        val plan = PlaylistReadAhead((0..6).map { Quality("q$it", null) }, budget = 6)
        assertEquals((0..5).map { "q$it" }, plan.sure(emptyMap()))
    }

    @Test
    fun aRepeatedPlaylistIsAskedOnce() {
        val plan = PlaylistReadAhead(
            listOf(Quality("q0", "a"), Quality("q1", "a"), Quality("q0", "a"), Quality("q2", "a")),
            budget = 3,
        )
        assertEquals(listOf("q0", "q1"), plan.sure(emptyMap()))
        assertEquals(listOf("q0", "a", "q1"), plan.sure(mapOf("q0" to true)))
        assertEquals(listOf("q0", "q1", "q2"), plan.sure(mapOf("q0" to false, "q1" to false)))
    }

    /**
     * Against every master of up to eight qualities and three sounds the generator makes: a read
     * is sure only when every way the open reads can answer has the loop make it, and the loop's
     * next read is sure once all its earlier reads have answered.
     */
    @Test
    fun aSureReadIsOneTheLoopMakesWhateverTheOpenReadsAnswer() {
        val random = Random(SEED)
        repeat(CASES) {
            val sounds = (0 until random.nextInt(4)).map { "s$it" }
            val qualities = (0 until 1 + random.nextInt(8)).map { index ->
                val sound = sounds.takeIf { it.isNotEmpty() && random.nextInt(4) > 0 }
                    ?.let { it[random.nextInt(it.size)] }
                Quality("q$index", sound)
            }
            val budget = 1 + random.nextInt(7)
            val plan = PlaylistReadAhead(qualities, budget)
            val ids = qualities.map { it.id } + sounds
            val world = ids.associateWith { random.nextInt(3) > 0 }
            val made = loop(qualities, budget, world::getValue)
            val answered = made.filter { random.nextBoolean() }.associateWith(world::getValue)
            val sure = plan.sure(answered)
            val open = ids.filter { it !in answered }
            for (mask in 0 until (1 shl open.size)) {
                val other = answered + open.withIndex().associate { (bit, id) ->
                    id to (mask shr bit and 1 == 1)
                }
                val otherMade = loop(qualities, budget, other::getValue)
                assertTrue("$qualities $budget $answered", otherMade.containsAll(sure))
            }
            for (next in made.indices) {
                val earlier = made.take(next).associateWith(world::getValue)
                assertTrue("$qualities $budget $earlier", made[next] in plan.sure(earlier))
            }
        }
    }

    /** The size loop reading one playlist at a time: the reads it makes, in order. */
    private fun loop(
        qualities: List<Quality>,
        budget: Int,
        length: (String) -> Boolean,
    ): List<String> {
        val made = mutableListOf<String>()
        val seen = mutableMapOf<String, Boolean>()
        fun finite(id: String): Boolean {
            seen[id]?.let { return it }
            if (made.size >= budget) return false
            made += id
            return length(id).also { seen[id] = it }
        }
        for (quality in qualities) {
            if (!finite(quality.id)) continue
            quality.soundId?.let(::finite)
        }
        return made
    }

    private companion object {
        const val SEED = 20_261_108L
        const val CASES = 400
    }
}
