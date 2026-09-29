package partyos.engine

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class PhaseSeedTest {
    /** Where the right answer lands when four options are shuffled, as Quick Draw does. */
    private fun slot(seed: Long, phase: Int) = listOf(0, 1, 2, 3).shuffled(Random(PartyEngine.phaseSeed(seed, phase))).indexOf(3)

    @Test fun theRightAnswerLandsAnywhereAndEachQuestionIsIndependent() {
        val shows = 4_000
        val counts = IntArray(4)
        var sameAsLast = 0
        var sameAsTwoBack = 0
        for (seed in 1L..shows) {
            // Questions sit two phases apart (question, reveal), like a real round.
            val slots = (0 until 6).map { slot(seed * 7_919, 4 + it * 2) }
            slots.forEach { counts[it]++ }
            sameAsLast += (1 until slots.size).count { slots[it] == slots[it - 1] }
            sameAsTwoBack += (2 until slots.size).count { slots[it] == slots[it - 2] }
        }
        val total = shows * 6.0
        counts.forEach { assertTrue(abs(it / total - 0.25) < 0.02, "slot share ${it / total}") }
        assertTrue(abs(sameAsLast / (shows * 5.0) - 0.25) < 0.02, "repeat rate ${sameAsLast / (shows * 5.0)}")
        assertTrue(abs(sameAsTwoBack / (shows * 4.0) - 0.25) < 0.02, "alternation rate ${sameAsTwoBack / (shows * 4.0)}")
    }
}
