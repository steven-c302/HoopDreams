package partyos.engine.games.hottype

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HotTypeSolverTest {
    // S T R P
    // L O N A
    // H E C I
    // D W K U
    private val tiles = "STRPLONAHECIDWKU".map { it.toString() }
    private val small = HotTypeDictionary(listOf("stone", "ton", "toe", "one", "tons", "zzz"))
    private val solver = HotTypeSolver(small)

    @Test fun findsEveryTraceableWordAndNoOthers() {
        assertEquals(setOf("stone", "ton", "toe", "one"), solver.solve(tiles, 4).keys)
    }

    @Test fun eachWordComesWithAPathThatSpellsIt() {
        for ((word, path) in solver.solve(tiles, 4)) assertEquals(word, HotTypeBoard.wordOf(tiles, 4, path))
    }

    @Test fun theBestMissedWordIsTheLongestNobodyFound() {
        assertEquals("stone", solver.bestMissed(tiles, 4, emptySet(), emptySet()))
        // with "stone" found, the rest are 3 letters and ties break alphabetically
        assertEquals("one", solver.bestMissed(tiles, 4, setOf("stone"), emptySet()))
    }

    @Test fun aBlockedWordIsNeverTheBestMissed() {
        assertEquals("ton", solver.bestMissed(tiles, 4, emptySet(), setOf("stone", "toe", "one")))
    }

    @Test fun nothingLeftMeansNoBestMissed() {
        assertNull(solver.bestMissed(tiles, 4, setOf("stone", "ton", "toe", "one"), emptySet()))
    }

    @Test fun theDealerReturnsABoardThatPassesTheGateWithTheRealDictionary() {
        val real = HotTypeSolver(HotTypeDictionary.core)
        for (seed in 1..3) {
            val four = HotTypeDealer(real).deal(4, Random(seed))
            val words4 = real.solve(four, 4).keys
            assertTrue(words4.size >= 60, "4x4 seed $seed: ${words4.size} words")
            assertTrue(words4.any { it.length >= 6 })
            val five = HotTypeDealer(real).deal(5, Random(seed))
            val words5 = real.solve(five, 5).keys
            assertTrue(words5.size >= 120, "5x5 seed $seed: ${words5.size} words")
            assertTrue(words5.any { it.length >= 7 })
        }
    }

    @Test fun theDealerStillReturnsABoardWhenNoneCanPass() {
        val dealt = HotTypeDealer(solver, attempts = 5).deal(4, Random(1))
        assertEquals(16, dealt.size)
    }
}
