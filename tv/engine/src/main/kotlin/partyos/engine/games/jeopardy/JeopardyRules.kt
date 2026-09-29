package partyos.engine.games.jeopardy

import kotlin.random.Random

/** One square on a board. [id] is the id of the clue behind it. */
data class Cell(val id: String, val col: Int, val row: Int, val value: Int)

/** Answer & Question's numbers and choices as pure functions, so they are tested without the game module. */
object JeopardyRules {
    const val CATEGORIES_PER_BOARD = 5
    const val CLUES_PER_CATEGORY = 5
    const val MIN_WAGER = 5
    const val LOCKOUT_MS = 1_000L

    /** Board 1 runs $200 to $1000, board 2 ($400 to $2000) doubles it; [row] 0 is the cheapest. */
    fun valueOf(round: Int, row: Int): Int = (if (round == 1) 200 else 400) * (row + 1)

    fun topValue(round: Int): Int = valueOf(round, CLUES_PER_CATEGORY - 1)

    fun dailyDoubleCount(round: Int): Int = if (round == 1) 1 else 2

    /** How long the clue is "read" before phones can ring in: longer clues get longer, between 3 and 8 seconds. */
    fun readMs(clue: String): Long = (2_500 + 55 * clue.length).coerceIn(3_000, 8_000).toLong()

    /** Hidden Daily Double spots as (column, row): different columns, rows 1..4 (never the cheapest row). */
    fun dailyDoubles(categories: Int, count: Int, random: Random): List<Pair<Int, Int>> =
        (0 until categories).shuffled(random).take(count).map { col -> col to (1 until CLUES_PER_CATEGORY).random(random) }

    /** A Daily Double wager: at least 5, at most the player's score or the round's top value, whichever is more. */
    fun wagerRange(score: Int, round: Int): IntRange = MIN_WAGER..maxOf(score, topValue(round), MIN_WAGER)

    fun finalWagerRange(score: Int): IntRange = 0..maxOf(0, score)

    /** Who has the board at the start of [round]. [players] and the result are player ids in join order. */
    fun firstPicker(round: Int, players: List<String>, scores: Map<String, Int>, captain: String?): String =
        if (round == 1) captain?.takeIf { it in players } ?: players.first()
        else players.minByOrNull { scores[it] ?: 0 }!! // minByOrNull keeps the earliest of equal scores

    /** When the picker runs out of time: a random cell on the cheapest row that still has one. */
    fun autoPick(unused: List<Cell>, random: Random): Cell {
        val cheapest = unused.minOf { it.row }
        return unused.filter { it.row == cheapest }.random(random)
    }

    /** Final Jeopardy is revealed from the lowest score up; equal scores keep join order. */
    fun finalOrder(players: List<String>, scores: Map<String, Int>): List<String> = players.sortedBy { scores[it] ?: 0 }

    fun clueDelta(stake: Int, right: Boolean): Int = if (right) stake else -stake
}
