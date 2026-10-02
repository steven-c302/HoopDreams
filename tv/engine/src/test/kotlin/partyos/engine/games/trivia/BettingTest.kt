package partyos.engine.games.trivia

import partyos.engine.BetOption
import partyos.engine.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BettingTest {
    private fun odds(vararg guesses: Pair<String, Double>) = Betting.line(guesses.toMap()).associate { it.team to it.odds }

    @Test fun oddsGrowWithDistanceFromTheMiddleOfThePack() {
        // Median of 100, 200, 210, 900 is 205: B and C tie as nearest, then A, then D.
        assertEquals(mapOf("A" to 2, "B" to 1, "C" to 1, "D" to 3), odds("A" to 100.0, "B" to 200.0, "C" to 210.0, "D" to 900.0))
    }

    @Test fun twoGuessesAreBothTheMiddleAndPayEvenly() {
        assertEquals(mapOf("A" to 1, "B" to 1), odds("A" to 10.0, "B" to 500.0))
    }

    @Test fun theLineIsSortedByGuess() {
        assertEquals(listOf("B", "A"), Betting.line(mapOf("A" to 50.0, "B" to 5.0)).map { it.team })
    }

    @Test fun stakesAreCappedAtTheScoreExceptHouseMoney() {
        assertEquals(listOf(250), Betting.allowedStakes(0))
        assertEquals(listOf(250), Betting.allowedStakes(499))
        assertEquals(listOf(250, 500), Betting.allowedStakes(500))
        assertEquals(listOf(250, 500, 1000), Betting.allowedStakes(5000))
    }

    @Test fun stakeIdsRoundTrip() {
        assertEquals("s500", Betting.stakeId(500))
        assertEquals(500, Betting.stakeOf("s500"))
        assertNull(Betting.stakeOf("s300"))
        assertNull(Betting.stakeOf("T1"))
    }

    private val p = listOf("p1", "p2", "p3").map(::PlayerId)

    @Test fun theTeamBacksTheGuessMostPlayersTapped() {
        val bets = mapOf(
            "p1" to TBet("T2", 500, at = 10), "p2" to TBet("T1", 250, at = 5), "p3" to TBet("T2", 250, at = 20),
        )
        // T2 has two backers; among them the stakes tie 500 v 250, so the lower stake wins.
        assertEquals("T2" to 250, Betting.teamBet(p, bets))
    }

    @Test fun aTieOnTheGuessGoesToTheFirstTap() {
        val bets = mapOf("p1" to TBet("T2", 500, at = 10), "p2" to TBet("T1", 500, at = 5))
        assertEquals("T1" to 500, Betting.teamBet(p, bets))
    }

    @Test fun skipsAndUnfinishedBetsDoNotCountAndNobodyBettingMeansNoBet() {
        assertNull(Betting.teamBet(p, mapOf("p1" to TBet(null, 0, at = 1), "p2" to TBet("T1", null, at = 2))))
        assertEquals("T1" to 250, Betting.teamBet(p, mapOf("p1" to TBet(null, 0, at = 1), "p2" to TBet("T1", 250, at = 2))))
    }

    private val line = listOf(BetOption("T1", 100.0, 2), BetOption("T2", 110.0, 1), BetOption("T3", 400.0, 3))

    @Test fun aWinningBetPaysStakeTimesOddsAndALosingBetCostsTheStake() {
        val r = Betting.settle(line, mapOf("T1" to ("T3" to 250), "T2" to ("T2" to 500)), answer = 112.0, available = mapOf("T1" to 1000, "T2" to 1000))
        assertEquals(false, r.getValue("T1").won)
        assertEquals(-250, r.getValue("T1").delta)
        assertEquals(true, r.getValue("T2").won)
        assertEquals(500, r.getValue("T2").delta) // odds 1
        assertEquals(1, r.getValue("T2").odds)
    }

    @Test fun aLongShotPaysTripleAndTheClosestGuessWinsTiesBothWin() {
        val r = Betting.settle(line, mapOf("T1" to ("T3" to 250)), answer = 400.0, available = mapOf("T1" to 0))
        assertEquals(750, r.getValue("T1").delta)
        // 105 is exactly between 100 and 110: both are closest, so a bet on either wins.
        val tie = Betting.settle(line, mapOf("T1" to ("T1" to 250), "T2" to ("T2" to 250)), answer = 105.0, available = mapOf("T1" to 0, "T2" to 0))
        assertEquals(true, tie.getValue("T1").won)
        assertEquals(true, tie.getValue("T2").won)
    }

    @Test fun aLossNeverTakesMoreThanTheTeamHas() {
        val r = Betting.settle(line, mapOf("T1" to ("T3" to 250)), answer = 100.0, available = mapOf("T1" to 100))
        assertEquals(-100, r.getValue("T1").delta)
        val broke = Betting.settle(line, mapOf("T1" to ("T3" to 250)), answer = 100.0, available = mapOf("T1" to 0))
        assertEquals(0, broke.getValue("T1").delta)
        assertEquals(false, broke.getValue("T1").won)
    }
}
