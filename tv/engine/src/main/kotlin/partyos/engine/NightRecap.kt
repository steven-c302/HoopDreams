package partyos.engine

import kotlinx.serialization.Serializable

/** Someone named on the night's recap card; [id] and [avatar] let the TV draw their face. */
@Serializable
data class NightPlayer(val id: PlayerId, val name: String, val avatar: Avatar)

/** One line of the night's leaderboard: [points] is how many players they out-scored, summed over the games. */
@Serializable
data class NightRow(val id: PlayerId, val name: String, val avatar: Avatar, val points: Int, val wins: Int)

/** A title handed out for how the night went. Ties share it, so [players] can hold more than one. */
@Serializable
data class NightAward(val title: String, val players: List<NightPlayer>, val note: String)

/** A line from a game's highlights, with the game it came from. */
@Serializable
data class NightMoment(val game: String, val text: String)

/** The night so far, built from the finished games only: the TV's "Night in Review" card. */
@Serializable
data class NightRecap(val games: Int, val board: List<NightRow>, val awards: List<NightAward>, val moments: List<NightMoment>)

private const val MAX_MOMENTS = 6
private const val MAX_SHARED = 3

/**
 * Everything on the card comes from [results], so it can't touch a game in progress and survives a restart with the party.
 * A game nobody scored in (ended early) is left out of the standings and awards, but its highlights still show.
 */
fun nightRecap(results: List<GameResult>): NightRecap? {
    val played = results.filter { r -> r.standings.any { it.score > 0 } }
    if (played.isEmpty()) return null

    val tally = LinkedHashMap<PlayerId, NightRow>()
    val winsByGame = ArrayList<Set<PlayerId>>()
    val lastByGame = ArrayList<PlayerId?>()
    for (g in played) {
        val top = g.standings.maxOf { it.score }
        val bottom = g.standings.minOf { it.score }
        for (row in g.standings) {
            val beat = g.standings.count { it.score < row.score }
            val won = if (row.score == top) 1 else 0
            val before = tally[row.id]
            tally[row.id] = NightRow(row.id, row.name, row.avatar, (before?.points ?: 0) + beat, (before?.wins ?: 0) + won)
        }
        winsByGame += g.standings.filter { it.score == top }.map { it.id }.toSet()
        lastByGame += g.standings.filter { it.score == bottom }.singleOrNull()?.takeIf { bottom < top }?.id
    }
    val board = tally.values.sortedWith(compareByDescending<NightRow> { it.points }.thenByDescending { it.wins }.thenBy { it.name })
    fun who(id: PlayerId) = board.first { it.id == id }.let { NightPlayer(it.id, it.name, it.avatar) }

    /** [ids] share an award only when it still means something: a few people, not the whole room (two-player rooms excepted). */
    fun shared(ids: List<PlayerId>) = ids.size in 1..MAX_SHARED && (ids.size < board.size || board.size <= 2)

    val awards = ArrayList<NightAward>()

    val topPoints = board.first().points
    if (topPoints > 0) {
        val leaders = board.filter { it.points == topPoints }.map { it.id }
        if (shared(leaders)) awards += NightAward("NIGHT CHAMP", leaders.map(::who), "$topPoints ${if (topPoints == 1) "point" else "points"}")
    }

    val mostWins = board.maxOf { it.wins }
    if (mostWins >= 2) {
        val leaders = board.filter { it.wins == mostWins }.map { it.id }
        if (shared(leaders)) awards += NightAward("MOST WINS", leaders.map(::who), "$mostWins wins")
    }

    val streaks = board.associate { b ->
        var best = 0
        var run = 0
        for (winners in winsByGame) { run = if (b.id in winners) run + 1 else 0; best = maxOf(best, run) }
        b.id to best
    }
    val longest = streaks.values.max()
    if (longest >= 2) {
        val leaders = board.filter { streaks.getValue(it.id) == longest }.map { it.id }
        if (shared(leaders)) awards += NightAward("HOT STREAK", leaders.map(::who), "$longest in a row")
    }

    for (i in 0 until played.size - 1) {
        val last = lastByGame[i] ?: continue
        if (last in winsByGame[i + 1]) {
            awards += NightAward("COMEBACK KID", listOf(who(last)), "Last in ${played[i].title}, first in ${played[i + 1].title}")
            break
        }
    }

    val lasts = lastByGame.filterNotNull().groupingBy { it }.eachCount()
    val mostLasts = lasts.values.maxOrNull() ?: 0
    if (mostLasts >= 2) {
        val leaders = board.filter { lasts[it.id] == mostLasts }.map { it.id }
        if (shared(leaders)) awards += NightAward("WOODEN SPOON", leaders.map(::who), "Last $mostLasts times")
    }

    val moments = results.asReversed().flatMap { g -> g.highlights.filter { it.isNotBlank() }.map { NightMoment(g.title, it) } }.take(MAX_MOMENTS)
    return NightRecap(played.size, board, awards, moments)
}
