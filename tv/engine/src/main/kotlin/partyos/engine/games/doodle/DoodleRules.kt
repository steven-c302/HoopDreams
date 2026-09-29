package partyos.engine.games.doodle

import partyos.engine.PlayerId
import partyos.engine.games.bluff.normalise
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Doodle Dash's turns, hints and scoring as pure functions, so they are tested without the game module. */
object DoodleRules {
    /** A draw is played in three stages; a hint letter is revealed when each of the last two begins. */
    const val STAGES = 3

    fun difficultyFactor(difficulty: Int) = 0.6 + 0.2 * difficulty

    /** 1st guesser 1.0, 2nd 0.85, 3rd 0.7, every later one 0.55. */
    fun orderFactor(order: Int) = when (order) {
        0 -> 1.0
        1 -> 0.85
        2 -> 0.7
        else -> 0.55
    }

    /** [order] is how many players had already got it; [fractionLeft] is the share of the draw time still on the clock. */
    fun guesserPoints(difficulty: Int, fractionLeft: Double, order: Int, multiplier: Int): Int {
        val t = fractionLeft.coerceIn(0.0, 1.0)
        return ((400 + 600 * t) * difficultyFactor(difficulty) * orderFactor(order) * multiplier).roundToInt()
    }

    fun drawerPoints(difficulty: Int, correct: Int, guessers: Int, multiplier: Int): Int {
        if (correct == 0) return 0
        val bonus = if (guessers > 0 && correct >= guessers) 500 else 0
        return ((250 * correct + bonus) * difficultyFactor(difficulty) * multiplier).roundToInt()
    }

    /** Whoever has drawn least goes next; a tie is broken at random. [candidates] must not be empty. */
    fun pickDrawer(candidates: List<PlayerId>, turns: Map<String, Int>, random: Random): PlayerId {
        val fewest = candidates.minOf { turns[it.v] ?: 0 }
        return candidates.filter { (turns[it.v] ?: 0) == fewest }.random(random)
    }

    /** Stage lengths are 40%, 30% and the rest of [total]. */
    fun stageMs(total: Long, stage: Int): Long = when (stage) {
        0 -> (total * 0.4).toLong()
        1 -> (total * 0.3).toLong()
        else -> total - (total * 0.4).toLong() - (total * 0.3).toLong()
    }

    /** Time in the stages after [stage]. */
    fun tailMs(total: Long, stage: Int): Long = (stage + 1 until STAGES).sumOf { stageMs(total, it) }

    private fun letterIndexes(word: String) = word.indices.filter { word[it].isLetter() }

    /** At most half the letters are ever shown. */
    fun hintCap(word: String) = letterIndexes(word).size / 2

    fun nextHint(word: String, revealed: List<Int>, random: Random): List<Int> {
        if (revealed.size >= hintCap(word)) return revealed
        val open = letterIndexes(word).filter { it !in revealed }
        return revealed + open.random(random)
    }

    /** "_ _ _ _ _", a wider gap between words, revealed letters in capitals; [revealAll] shows the whole word. */
    fun blanks(word: String, revealed: Collection<Int>, revealAll: Boolean = false): String =
        word.mapIndexed { i, ch ->
            when {
                ch == ' ' -> " "
                revealAll || i in revealed -> ch.uppercaseChar().toString()
                else -> "_"
            }
        }.joinToString(" ")

    /** Not right, but close: the fuzzy matcher takes small typos already, so this is a little further out than that. */
    fun nearMiss(guess: String, word: String): Boolean {
        val g = normalise(guess)
        val w = normalise(word)
        if (g == w || w.length < 4) return false
        return editDistance(g, w) <= w.length / 3 + 1
    }

    internal fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
