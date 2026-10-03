package partyos.engine.games.trivia

import kotlinx.serialization.Serializable
import partyos.engine.FinaleResult
import partyos.engine.PlayerId

/** A player's wager pick in the Final Wager: one of the option ids in [Finale] (`w0`, `w25`, `w50`, `w75`, `wall`). */
@Serializable
data class FWager(val option: String, val at: Long = 0)

/** The Final Wager as it plays: the category, the answer, everyone's wager picks, and (from the reveal on) the results. */
@Serializable
data class FinaleState(
    val category: String = "",
    val answerText: String = "",
    /** player id → their pick. */
    val wagers: Map<String, FWager> = emptyMap(),
    /** Last place first; the leader is last. Empty until the question closes. */
    val results: List<FinaleResult> = emptyList(),
)

/** The rules of the Final Wager. Pure: no state, no engine. */
object Finale {
    const val CATEGORY_MS = 7_000L
    const val WAGER_MS = 20_000L
    const val QUESTION_MS = 40_000L
    /** One team's moment in the reveal, and the extra pause before the leader's. */
    const val STEP_MS = 5_000L
    const val LEAD_PAD_MS = 4_000L
    /** Percentages are of at least this, so a team on little still has a meaningful bet. */
    const val FLOOR = 1000
    const val MAX_ANSWER = 20
    const val NONE = "w0"
    const val ALL_IN = "wall"

    private val PERCENT = linkedMapOf("w0" to 0, "w25" to 25, "w50" to 50, "w75" to 75, ALL_IN to 100)

    fun revealMs(teams: Int): Long = STEP_MS * teams + LEAD_PAD_MS

    /** In the bottom half: fewer than half the teams (rounded down) score strictly less than this one. Ties share a standing. */
    fun underdog(score: Int, scores: List<Int>): Boolean = scores.count { it < score } < scores.size / 2

    /** The wagers a team may pick, safest first; ALL IN only for underdogs. */
    fun options(score: Int, scores: List<Int>): List<String> =
        PERCENT.keys.filter { it != ALL_IN } + if (underdog(score, scores)) listOf(ALL_IN) else emptyList()

    /** What an option is worth to a team on [score]: that percent of `max(score, FLOOR)`, to the nearest 50. */
    fun amount(option: String, score: Int): Int {
        val percent = PERCENT[option] ?: return 0
        val raw = maxOf(score, FLOOR) * percent / 100
        return (raw + 25) / 50 * 50
    }

    /**
     * The team's wager: the offered option most of its players picked, ties to the smaller one; [NONE] if nobody
     * picked (or what they picked wasn't offered to this team).
     */
    fun teamWager(members: List<PlayerId>, wagers: Map<String, FWager>, offered: List<String>): String {
        val picks = members.mapNotNull { wagers[it.v]?.option }.filter { it in offered }
        if (picks.isEmpty()) return NONE
        return picks.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { PERCENT.getValue(it.key) })
            .first().key
    }

    /** The real change to a team's score: the wager if right; if wrong or missing, the wager but never more than the score. */
    fun settle(score: Int, wager: Int, right: Boolean): Int = if (right) wager else -minOf(wager, score.coerceAtLeast(0))
}
