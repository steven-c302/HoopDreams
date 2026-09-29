package partyos.engine.games.doodle

import partyos.engine.PlayerId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoodleRulesTest {
    @Test fun guesserPointsFallWithTimeOrderAndEase() {
        assertEquals(1000, DoodleRules.guesserPoints(2, 1.0, 0, 1))
        assertEquals(800, DoodleRules.guesserPoints(1, 1.0, 0, 1))
        assertEquals(700, DoodleRules.guesserPoints(2, 0.5, 0, 1))
        assertEquals(336, DoodleRules.guesserPoints(3, 0.0, 2, 1))
        assertEquals(850, DoodleRules.guesserPoints(2, 1.0, 1, 1))
        assertEquals(550, DoodleRules.guesserPoints(2, 1.0, 5, 1)) // 4th and later share the floor factor
    }

    @Test fun theLastTurnDoubles() {
        assertEquals(2000, DoodleRules.guesserPoints(2, 1.0, 0, 2))
    }

    @Test fun timeLeftIsClamped() {
        assertEquals(DoodleRules.guesserPoints(2, 1.0, 0, 1), DoodleRules.guesserPoints(2, 1.7, 0, 1))
        assertEquals(DoodleRules.guesserPoints(2, 0.0, 0, 1), DoodleRules.guesserPoints(2, -3.0, 0, 1))
    }

    @Test fun drawerEarnsPerGuesserAndABonusWhenEveryoneGetsIt() {
        assertEquals(0, DoodleRules.drawerPoints(2, 0, 4, 1))
        assertEquals(500, DoodleRules.drawerPoints(2, 2, 4, 1))
        assertEquals(1500, DoodleRules.drawerPoints(2, 4, 4, 1))
        assertEquals(600, DoodleRules.drawerPoints(1, 1, 1, 1))
        assertEquals(3000, DoodleRules.drawerPoints(3, 3, 3, 2))
    }

    @Test fun drawerGoesToWhoeverHasDrawnLeast() {
        val ids = listOf("a", "b", "c").map(::PlayerId)
        repeat(20) { seed ->
            val d = DoodleRules.pickDrawer(ids, mapOf("a" to 1, "b" to 1), Random(seed))
            assertEquals(PlayerId("c"), d)
        }
        val seen = (0 until 60).map { DoodleRules.pickDrawer(ids, emptyMap(), Random(it)) }.toSet()
        assertEquals(ids.toSet(), seen) // a tie is broken at random
    }

    @Test fun stagesAddUpToTheDrawTime() {
        assertEquals(listOf(30_000L, 22_500L, 22_500L), (0..2).map { DoodleRules.stageMs(75_000, it) })
        assertEquals(listOf(45_000L, 22_500L, 0L), (0..2).map { DoodleRules.tailMs(75_000, it) })
        val odd = 99_999L
        assertEquals(odd, (0..2).sumOf { DoodleRules.stageMs(odd, it) })
    }

    @Test fun hintsRevealLettersUpToHalfTheWord() {
        assertEquals(2, DoodleRules.hintCap("pizza"))
        assertEquals(1, DoodleRules.hintCap("ox"))
        assertEquals(0, DoodleRules.hintCap("x"))
        val r = Random(1)
        val one = DoodleRules.nextHint("pizza", emptyList(), r)
        val two = DoodleRules.nextHint("pizza", one, r)
        val three = DoodleRules.nextHint("pizza", two, r)
        assertEquals(1, one.size)
        assertEquals(2, two.size)
        assertEquals(two, three) // capped
        assertEquals(two.size, two.toSet().size)
    }

    @Test fun hintsNeverPickASpace() {
        val word = "ice cream"
        val r = Random(3)
        var got = emptyList<Int>()
        repeat(6) { got = DoodleRules.nextHint(word, got, r) }
        assertTrue(got.none { word[it] == ' ' })
    }

    @Test fun blanksShowOneCellPerLetterAndAWideGapBetweenWords() {
        assertEquals("_ _ _ _ _", DoodleRules.blanks("pizza", emptyList()))
        assertEquals("_ _ _   _ _ _ _ _", DoodleRules.blanks("ice cream", emptyList()))
        assertEquals("I _ _   _ _ _ _ _", DoodleRules.blanks("ice cream", listOf(0)))
        assertEquals("P I Z Z A", DoodleRules.blanks("pizza", emptyList(), revealAll = true))
    }

    @Test fun nearMissIsCloseButNotRight() {
        assertTrue(DoodleRules.nearMiss("pizqq", "pizza"))
        assertFalse(DoodleRules.nearMiss("pizza", "pizza"))
        assertFalse(DoodleRules.nearMiss("banana", "flamingo"))
        assertFalse(DoodleRules.nearMiss("cot", "cat")) // too short to say
        assertTrue(DoodleRules.nearMiss("kqqqaroo", "kangaroo"))
    }
}
