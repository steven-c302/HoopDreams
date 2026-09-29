package partyos.engine.games.imposter

import partyos.engine.PlayerId
import kotlin.random.Random

/** Imposter's voting and scoring rules as pure functions, so they are tested without the game module. */
object ImposterRules {
    const val CREW_POINTS = 1000
    const val SURVIVE_POINTS = 1500
    const val GUESS_POINTS = 1000

    fun imposterCount(players: Int) = if (players >= 9) 2 else 1

    fun deal(players: List<PlayerId>, random: Random): List<PlayerId> =
        players.shuffled(random).take(imposterCount(players.size))

    /** Votes are voter id → suspect id. Returns suspect id → number of votes. */
    fun tally(votes: Map<String, String>): Map<String, Int> = votes.values.groupingBy { it }.eachCount()

    /**
     * Who the room accuses. With [imposterCount] = k, the cut-off is the (k+1)-th highest tally (0 if there is none);
     * a player is accused when their tally is strictly above it, so a tie across the cut-off spares everyone in it.
     */
    fun accused(votes: Map<String, String>, imposterCount: Int): Set<String> {
        val tally = tally(votes)
        val cutoff = tally.values.sortedDescending().getOrElse(imposterCount) { 0 }
        return tally.filterValues { it > cutoff }.keys
    }

    /** Points for the vote itself: crew who named an imposter, and imposters nobody accused. Guess points come later. */
    fun roundDeltas(imposters: Set<String>, votes: Map<String, String>, accused: Set<String>, multiplier: Int): Map<String, Int> {
        val deltas = LinkedHashMap<String, Int>()
        for ((voter, suspect) in votes) {
            if (voter !in imposters && suspect in imposters) deltas.merge(voter, CREW_POINTS * multiplier, Int::plus)
        }
        for (i in imposters) if (i !in accused) deltas.merge(i, SURVIVE_POINTS * multiplier, Int::plus)
        return deltas
    }
}
