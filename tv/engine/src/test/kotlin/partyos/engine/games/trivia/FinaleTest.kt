package partyos.engine.games.trivia

import partyos.engine.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinaleTest {
    private val base = listOf("w0", "w25", "w50", "w75")

    @Test fun onlyTheBottomHalfMayGoAllIn() {
        // Two teams: only the one behind. Three: only last. Four: the bottom two. Five: the bottom two.
        assertEquals(base, Finale.options(1500, listOf(1500, 0)))
        assertEquals(base + "wall", Finale.options(0, listOf(1500, 0)))
        assertEquals(base, Finale.options(2000, listOf(2000, 1500, 0)))
        assertEquals(base, Finale.options(1500, listOf(2000, 1500, 0)))
        assertEquals(base + "wall", Finale.options(0, listOf(2000, 1500, 0)))
        assertEquals(base, Finale.options(3000, listOf(3000, 2000, 1000, 0)))
        assertEquals(base, Finale.options(2000, listOf(3000, 2000, 1000, 0)))
        assertEquals(base + "wall", Finale.options(1000, listOf(3000, 2000, 1000, 0)))
        assertEquals(base + "wall", Finale.options(0, listOf(3000, 2000, 1000, 0)))
        assertFalse("wall" in Finale.options(3000, listOf(5000, 4000, 3000, 2000, 1000)))
        assertTrue("wall" in Finale.options(2000, listOf(5000, 4000, 3000, 2000, 1000)))
        assertTrue("wall" in Finale.options(1000, listOf(5000, 4000, 3000, 2000, 1000)))
    }

    @Test fun tiesShareTheirStanding() {
        // Two teams tied for last of three both qualify; two tied for first of three never do.
        assertTrue("wall" in Finale.options(0, listOf(1500, 0, 0)))
        assertFalse("wall" in Finale.options(1500, listOf(1500, 1500, 0)))
        // Everyone level: everyone is an underdog.
        assertTrue("wall" in Finale.options(500, listOf(500, 500, 500)))
    }

    @Test fun amountsAreAPercentOfTheScoreWithAFloorRoundedToFifty() {
        assertEquals(0, Finale.amount("w0", 4000))
        assertEquals(1000, Finale.amount("w25", 4000))
        assertEquals(3000, Finale.amount("w75", 4000))
        assertEquals(4000, Finale.amount("wall", 4000))
        assertEquals(300, Finale.amount("w25", 1234)) // 308 rounds to 300
        assertEquals(250, Finale.amount("w25", 0)) // the floor: a team on nothing bets from 1000
        assertEquals(1000, Finale.amount("wall", 200))
        assertEquals(0, Finale.amount("nonsense", 4000))
    }

    private val p = listOf("p1", "p2", "p3").map(::PlayerId)
    private val offered = base + "wall"

    @Test fun theTeamWagersWhatMostPlayersPickedAndTiesGoLower() {
        val w = mapOf("p1" to FWager("w50", 1), "p2" to FWager("w50", 2), "p3" to FWager("wall", 3))
        assertEquals("w50", Finale.teamWager(p, w, offered))
        val tie = mapOf("p1" to FWager("wall", 1), "p2" to FWager("w25", 2))
        assertEquals("w25", Finale.teamWager(p, tie, offered))
    }

    @Test fun nobodyPickingOrAPickNotOfferedMeansNoWager() {
        assertEquals("w0", Finale.teamWager(p, emptyMap(), offered))
        assertEquals("w0", Finale.teamWager(p, mapOf("p1" to FWager("wall", 1)), base))
    }

    @Test fun rightAddsTheWagerAndWrongCostsAtMostTheScore() {
        assertEquals(750, Finale.settle(1500, 750, right = true))
        assertEquals(-750, Finale.settle(1500, 750, right = false))
        assertEquals(-200, Finale.settle(200, 1000, right = false)) // never more than the team has
        assertEquals(0, Finale.settle(0, 1000, right = false)) // a team on 0 loses nothing
        assertEquals(1000, Finale.settle(0, 1000, right = true))
    }

    @Test fun theRevealLastsAStepPerTeamPlusTheLeadersPause() {
        assertEquals(5_000L * 4 + 4_000L, Finale.revealMs(4))
    }
}
