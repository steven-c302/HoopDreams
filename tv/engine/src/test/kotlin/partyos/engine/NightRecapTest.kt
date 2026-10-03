package partyos.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NightRecapTest {
    private val ids = listOf("a", "b", "c", "d").associateWith { PlayerId(it) }
    private fun row(who: String, score: Int) = ScoreRow(ids.getValue(who), who.uppercase(), Avatar("p:01", "#FF7A00"), score)
    private fun game(title: String, vararg scores: Pair<String, Int>, highlights: List<String> = emptyList()) =
        GameResult(title.lowercase(), title, 0, scores.map { row(it.first, it.second) }.sortedByDescending { it.score }, highlights)

    private fun NightRecap?.award(title: String) = this?.awards?.singleOrNull { it.title == title }

    @Test fun noGamesMeansNoRecap() {
        assertNull(nightRecap(emptyList()))
    }

    @Test fun aGameNobodyScoredInDoesNotCount() {
        assertNull(nightRecap(listOf(game("Dud", "a" to 0, "b" to 0))))
    }

    @Test fun pointsAreHowManyPlayersYouBeatAndTiesShareTheWin() {
        val r = assertNotNull(nightRecap(listOf(game("One", "a" to 30, "b" to 30, "c" to 10, "d" to 0))))
        val byName = r.board.associateBy { it.name }
        assertEquals(2, byName.getValue("A").points)
        assertEquals(2, byName.getValue("B").points)
        assertEquals(1, byName.getValue("C").points)
        assertEquals(0, byName.getValue("D").points)
        assertEquals(1, byName.getValue("A").wins)
        assertEquals(1, byName.getValue("B").wins)
        assertEquals(1, r.games)
    }

    @Test fun theBoardRanksByPointsThenWinsThenName() {
        val r = assertNotNull(nightRecap(listOf(
            game("One", "a" to 9, "b" to 5, "c" to 1),
            game("Two", "c" to 9, "b" to 5, "a" to 1),
        )))
        assertEquals(listOf("A", "C", "B"), r.board.map { it.name })
        assertEquals(listOf(2, 2, 2), r.board.map { it.points })
        assertEquals(listOf(1, 1, 0), r.board.map { it.wins })
    }

    @Test fun nightChampIsTheTopOfTheBoardAndTiesShareIt() {
        val champ = assertNotNull(nightRecap(listOf(game("One", "a" to 9, "b" to 5, "c" to 1))).award("NIGHT CHAMP"))
        assertEquals(listOf("A"), champ.players.map { it.name })
        val shared = assertNotNull(nightRecap(listOf(game("One", "a" to 9, "b" to 5), game("Two", "b" to 9, "a" to 5))).award("NIGHT CHAMP"))
        assertEquals(setOf("A", "B"), shared.players.map { it.name }.toSet())
    }

    @Test fun nobodyIsChampWhenEveryoneIsLevel() {
        assertNull(nightRecap(listOf(game("One", "a" to 5, "b" to 5))).award("NIGHT CHAMP"))
    }

    @Test fun mostWinsNeedsTwoWinsAndAClearLead() {
        val level = nightRecap(listOf(game("One", "a" to 9, "b" to 5), game("Two", "b" to 9, "a" to 5)))
        assertNull(level.award("MOST WINS"))
        val lead = assertNotNull(nightRecap(listOf(
            game("One", "a" to 9, "b" to 5), game("Two", "a" to 9, "b" to 5), game("Three", "b" to 9, "a" to 5),
        )).award("MOST WINS"))
        assertEquals(listOf("A"), lead.players.map { it.name })
        assertEquals("2 wins", lead.note)
    }

    @Test fun hotStreakIsTwoOrMoreWinsInARow() {
        val streak = assertNotNull(nightRecap(listOf(
            game("One", "a" to 9, "b" to 5), game("Two", "a" to 9, "b" to 5), game("Three", "a" to 9, "b" to 5),
        )).award("HOT STREAK"))
        assertEquals("3 in a row", streak.note)
        assertNull(nightRecap(listOf(
            game("One", "a" to 9, "b" to 5), game("Two", "b" to 9, "a" to 5), game("Three", "a" to 9, "b" to 5),
        )).award("HOT STREAK"))
    }

    @Test fun woodenSpoonIsTwoLastPlacesNotCountingTies() {
        val spoon = assertNotNull(nightRecap(listOf(
            game("One", "a" to 9, "b" to 5, "c" to 1), game("Two", "b" to 9, "a" to 5, "c" to 1),
        )).award("WOODEN SPOON"))
        assertEquals(listOf("C"), spoon.players.map { it.name })
        assertNull(nightRecap(listOf(
            game("One", "a" to 9, "b" to 1, "c" to 1), game("Two", "a" to 9, "b" to 1, "c" to 1),
        )).award("WOODEN SPOON"))
    }

    @Test fun comebackKidWentFromLastToFirstInTheNextGame() {
        val kid = assertNotNull(nightRecap(listOf(
            game("One", "a" to 9, "b" to 5, "c" to 1), game("Two", "c" to 9, "a" to 5, "b" to 2),
        )).award("COMEBACK KID"))
        assertEquals(listOf("C"), kid.players.map { it.name })
        assertEquals("Last in One, first in Two", kid.note)
    }

    @Test fun momentsListEachGamesHighlightsWithTheGameNameNewestFirstAndCapped() {
        val games = (1..5).map { game("G$it", "a" to 9, "b" to 1, highlights = listOf("h$it-1", "h$it-2")) }
        val r = assertNotNull(nightRecap(games))
        assertEquals(6, r.moments.size)
        assertEquals("G5", r.moments.first().game)
        assertEquals("h5-1", r.moments.first().text)
        assertTrue(r.moments.all { it.text.isNotBlank() })
    }
}
