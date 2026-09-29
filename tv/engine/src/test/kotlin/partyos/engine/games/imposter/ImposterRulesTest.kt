package partyos.engine.games.imposter

import partyos.engine.PlayerId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImposterRulesTest {
    private fun ids(n: Int) = (1..n).map { PlayerId("p$it") }
    private fun votes(vararg v: Pair<String, String>) = mapOf(*v)

    @Test fun oneImposterUnderNineAndTwoFromNine() {
        for (n in 4..8) assertEquals(1, ImposterRules.imposterCount(n), "n=$n")
        for (n in 9..16) assertEquals(2, ImposterRules.imposterCount(n), "n=$n")
    }

    @Test fun dealPicksDistinctPlayersDeterministically() {
        val a = ImposterRules.deal(ids(12), Random(5))
        assertEquals(a, ImposterRules.deal(ids(12), Random(5)))
        assertEquals(2, a.toSet().size)
        assertTrue(ids(12).containsAll(a))
        assertEquals(1, ImposterRules.deal(ids(6), Random(1)).size)
    }

    @Test fun aloneOnTopIsAccused() {
        assertEquals(setOf("x"), ImposterRules.accused(votes("a" to "x", "b" to "x", "c" to "y"), 1))
    }

    @Test fun tieForFirstAccusesNobodyWithOneImposter() {
        assertEquals(emptySet(), ImposterRules.accused(votes("a" to "x", "b" to "y"), 1))
    }

    @Test fun noVotesAccuseNobody() {
        assertEquals(emptySet(), ImposterRules.accused(emptyMap(), 1))
        assertEquals(emptySet(), ImposterRules.accused(emptyMap(), 2))
    }

    @Test fun twoImposters_topTwoAreAccusedWhenClearOfTheThird() {
        val v = votes("a" to "x", "b" to "x", "c" to "x", "d" to "y", "e" to "y", "f" to "y", "g" to "z")
        assertEquals(setOf("x", "y"), ImposterRules.accused(v, 2))
    }

    @Test fun twoImposters_aTieBetweenSecondAndThirdSparesBoth() {
        val v = votes("a" to "x", "b" to "x", "c" to "x", "d" to "y", "e" to "y", "f" to "z", "g" to "z")
        assertEquals(setOf("x"), ImposterRules.accused(v, 2))
    }

    @Test fun crewPointsForNamingAnImposterAndNothingForTheCaughtImposter() {
        val v = votes("a" to "i", "b" to "i", "c" to "a", "d" to "i", "i" to "a")
        val deltas = ImposterRules.roundDeltas(setOf("i"), v, setOf("i"), 1)
        assertEquals(mapOf("a" to 1000, "b" to 1000, "d" to 1000), deltas)
    }

    @Test fun aSurvivingImposterScoresAndTheFinalRoundDoubles() {
        val v = votes("a" to "b", "b" to "a", "c" to "a", "i" to "a")
        val deltas = ImposterRules.roundDeltas(setOf("i"), v, emptySet(), 2)
        assertEquals(mapOf("i" to 3000), deltas)
    }

    @Test fun anImposterVoteNeverScoresForThem() {
        val v = votes("i" to "j", "j" to "i")
        val deltas = ImposterRules.roundDeltas(setOf("i", "j"), v, emptySet(), 1)
        assertEquals(mapOf("i" to 1500, "j" to 1500), deltas)
    }
}
