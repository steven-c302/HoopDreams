package partyos.engine.games.jeopardy

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JeopardyRulesTest {
    @Test fun valuesDoubleOnTheSecondBoard() {
        assertEquals(listOf(200, 400, 600, 800, 1000), (0..4).map { JeopardyRules.valueOf(1, it) })
        assertEquals(listOf(400, 800, 1200, 1600, 2000), (0..4).map { JeopardyRules.valueOf(2, it) })
        assertEquals(1000, JeopardyRules.topValue(1))
        assertEquals(2000, JeopardyRules.topValue(2))
    }

    @Test fun oneDailyDoubleOnTheFirstBoardAndTwoOnTheSecond() {
        assertEquals(1, JeopardyRules.dailyDoubleCount(1))
        assertEquals(2, JeopardyRules.dailyDoubleCount(2))
    }

    @Test fun readTimeGrowsWithTheClueButStaysBetweenThreeAndEightSeconds() {
        assertEquals(3_000L, JeopardyRules.readMs(""))
        assertEquals(3_050L, JeopardyRules.readMs("x".repeat(10)))
        assertEquals(2_500L + 55L * 80, JeopardyRules.readMs("x".repeat(80)))
        assertEquals(8_000L, JeopardyRules.readMs("x".repeat(500)))
    }

    @Test fun dailyDoublesAreInDifferentColumnsAndNeverOnTheCheapRow() {
        repeat(50) { seed ->
            val spots = JeopardyRules.dailyDoubles(5, 2, Random(seed))
            assertEquals(2, spots.size)
            assertEquals(2, spots.map { it.first }.toSet().size, "columns for seed $seed")
            assertTrue(spots.all { it.first in 0..4 && it.second in 1..4 }, "spots $spots for seed $seed")
        }
        assertEquals(JeopardyRules.dailyDoubles(5, 1, Random(3)), JeopardyRules.dailyDoubles(5, 1, Random(3)))
    }

    @Test fun aDailyDoubleWagerHasAFloorOfFiveAndAnUpperBoundOfScoreOrTheTopValue() {
        assertEquals(5..1200, JeopardyRules.wagerRange(1200, 1))
        assertEquals(5..1000, JeopardyRules.wagerRange(300, 1))
        assertEquals(5..1000, JeopardyRules.wagerRange(-400, 1))
        assertEquals(5..2000, JeopardyRules.wagerRange(900, 2))
    }

    @Test fun aFinalWagerIsBetweenZeroAndTheScore() {
        assertEquals(0..750, JeopardyRules.finalWagerRange(750))
        assertEquals(0..0, JeopardyRules.finalWagerRange(0))
        assertEquals(0..0, JeopardyRules.finalWagerRange(-300))
    }

    @Test fun theCaptainPicksFirstOnBoardOneAndTheLowestScorePicksFirstOnBoardTwo() {
        val players = listOf("a", "b", "c")
        assertEquals("b", JeopardyRules.firstPicker(1, players, emptyMap(), "b"))
        assertEquals("a", JeopardyRules.firstPicker(1, players, emptyMap(), null))
        assertEquals("a", JeopardyRules.firstPicker(1, players, emptyMap(), "gone"))
        assertEquals("c", JeopardyRules.firstPicker(2, players, mapOf("a" to 400, "b" to 200, "c" to -200), "a"))
        assertEquals("b", JeopardyRules.firstPicker(2, players, mapOf("a" to 400, "b" to 0, "c" to 0), "a")) // tie: earliest joined
    }

    @Test fun anAutoPickTakesARandomCellFromTheCheapestRowLeft() {
        val cells = listOf(Cell("a", 0, 0, 200), Cell("b", 1, 0, 200), Cell("c", 0, 1, 400), Cell("d", 1, 2, 600))
        repeat(20) { seed -> assertTrue(JeopardyRules.autoPick(cells, Random(seed)).id in setOf("a", "b")) }
        assertEquals("c", JeopardyRules.autoPick(cells.drop(2).take(1) + cells.drop(3), Random(1)).id)
        assertEquals(JeopardyRules.autoPick(cells, Random(9)), JeopardyRules.autoPick(cells, Random(9)))
    }

    @Test fun theFinalIsRevealedFromTheLowestScoreUpAndTiesKeepJoinOrder() {
        val players = listOf("a", "b", "c", "d")
        val scores = mapOf("a" to 500, "b" to 100, "c" to 500, "d" to -50)
        assertEquals(listOf("d", "b", "a", "c"), JeopardyRules.finalOrder(players, scores))
    }

    @Test fun rightWinsTheStakeAndWrongLosesIt() {
        assertEquals(600, JeopardyRules.clueDelta(600, true))
        assertEquals(-600, JeopardyRules.clueDelta(600, false))
    }
}
