package partyos.engine.games.trivia

import kotlinx.serialization.Serializable
import partyos.engine.BetOption
import partyos.engine.BetResult
import partyos.engine.PlayerId
import kotlin.math.abs

/**
 * One player's bet in the Ballpark bet phase. [on] is the team whose guess they back; [stake] is null until they pick
 * one. A player who skipped has `on = null, stake = 0`; one who is still choosing has `stake = null`.
 */
@Serializable
data class TBet(val on: String? = null, val stake: Int? = null, val at: Long = 0)

/** The rules of Ballpark betting. Pure: no state, no engine. */
object Betting {
    const val BET_MS = 15_000L
    val STAKES = listOf(250, 500, 1000)
    /** Always available, so a team with nothing can still take a swing. */
    const val HOUSE_STAKE = 250
    /** Phone option ids besides team ids and stake ids. */
    const val SKIP = "skip"
    const val BACK = "back"

    fun stakeId(amount: Int) = "s$amount"
    fun stakeOf(id: String): Int? = if (id.startsWith("s")) id.drop(1).toIntOrNull()?.takeIf { it in STAKES } else null

    /**
     * Every team's guess with its odds: ranked by distance from the median of all the guesses, nearest 1x, next 2x,
     * the rest 3x; equal distances share a tier. Sorted by guess so the TV can lay them out left to right.
     */
    fun line(guesses: Map<String, Double>): List<BetOption> {
        if (guesses.isEmpty()) return emptyList()
        val middle = BrainDrain.median(guesses.values.toList())!!
        val distance = guesses.mapValues { abs(it.value - middle) }
        val tiers = distance.values.distinct().sorted()
        return guesses.map { (team, g) -> BetOption(team, g, minOf(3, tiers.indexOf(distance.getValue(team)) + 1)) }.sortedBy { it.number }
    }

    /** The stakes a team with [score] may offer. */
    fun allowedStakes(score: Int): List<Int> = STAKES.filter { it <= score || it == HOUSE_STAKE }

    /**
     * The team's bet: the guess most of its locked-in players back (ties to the first tap) and, among those backers,
     * the stake most of them chose (ties to the lower). Skips and unfinished bets don't count; null if nobody bet.
     */
    fun teamBet(members: List<PlayerId>, bets: Map<String, TBet>): Pair<String, Int>? {
        val locked = members.mapNotNull { bets[it.v] }.filter { it.on != null && (it.stake ?: 0) > 0 }
        if (locked.isEmpty()) return null
        val backed = locked.groupBy { it.on!! }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<TBet>>> { it.value.size }.thenBy { e -> e.value.minOf { it.at } })
            .first()
        val stake = backed.value.groupingBy { it.stake!! }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .first().key
        return backed.key to stake
    }

    /**
     * How each team's bet came out. A bet wins when the guess it backs is among the closest to [answer] (a tie for
     * closest wins for all of them). A win pays `stake x odds`; a loss costs the stake, but never more than the team
     * has ([available] = its score plus this round's points), so scores never go below zero.
     */
    fun settle(line: List<BetOption>, bets: Map<String, Pair<String, Int>>, answer: Double, available: Map<String, Int>): Map<String, BetResult> {
        val best = line.minOfOrNull { abs(it.number - answer) } ?: return emptyMap()
        val winners = line.filter { abs(it.number - answer) == best }.map { it.team }.toSet()
        return bets.mapNotNull { (team, bet) ->
            val (on, stake) = bet
            val option = line.firstOrNull { it.team == on } ?: return@mapNotNull null
            val won = on in winners
            val delta = if (won) stake * option.odds else -minOf(stake, (available[team] ?: 0).coerceAtLeast(0))
            team to BetResult(on, stake, option.odds, won, delta)
        }.toMap()
    }
}
