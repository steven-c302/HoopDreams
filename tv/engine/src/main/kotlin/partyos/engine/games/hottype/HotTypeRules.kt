package partyos.engine.games.hottype

/** Hot Type's scoring as pure functions, so it is tested without the game module. */
object HotTypeRules {
    const val MIN_LETTERS = 3
    const val LONGEST_BONUS = 500
    /** A word this long (or longer) fires the burst on the TV. */
    const val BIG_FIND = 6

    fun points(letters: Int): Int = when {
        letters < MIN_LETTERS -> 0
        letters == 3 -> 100
        letters == 4 -> 400
        letters == 5 -> 800
        letters == 6 -> 1400
        letters == 7 -> 1800
        else -> 2200
    }

    data class Parts(val base: Int, val unique: Int, val longest: Int) {
        val total: Int get() = base + unique + longest
    }

    /** [bonus] is the unique bonus (equal to [base] when one player found the word, else 0). Both already include the multiplier. */
    data class WordResult(val word: String, val finders: List<String>, val base: Int, val bonus: Int, val longest: Boolean)

    data class RoundResult(val words: List<WordResult>, val parts: Map<String, Parts>)

    /** [found] is player id to the words they found. Every part is multiplied by [multiplier] (2 in the final round). */
    fun score(found: Map<String, List<String>>, multiplier: Int): RoundResult {
        val finders = LinkedHashMap<String, MutableList<String>>()
        for ((player, words) in found) for (w in words.distinct()) finders.getOrPut(w) { mutableListOf() } += player
        val longestLen = finders.keys.maxOfOrNull { it.length } ?: 0
        val words = finders.map { (w, who) ->
            val base = points(w.length) * multiplier
            WordResult(w, who.toList(), base, if (who.size == 1) base else 0, longestLen >= MIN_LETTERS && w.length == longestLen)
        }
        val parts = found.keys.associateWith { p ->
            val mine = words.filter { p in it.finders }
            Parts(
                base = mine.sumOf { it.base },
                unique = mine.sumOf { it.bonus },
                longest = if (mine.any { it.longest }) LONGEST_BONUS * multiplier else 0,
            )
        }
        return RoundResult(words, parts)
    }

    /**
     * Up to [limit] words for the reveal's front page. Picked in this order: the longest word, then unique words, then
     * shared ones (each best first, ties alphabetical). Returned in stamping order: rising points, longest word last.
     */
    fun frontPage(result: RoundResult, limit: Int = 8): List<WordResult> {
        val best = compareByDescending<WordResult> { it.base }.thenBy { it.word }
        val longest = result.words.filter { it.longest }.sortedWith(best).take(1)
        val rest = result.words - longest.toSet()
        val filler = (rest.filter { it.bonus > 0 }.sortedWith(best) + rest.filter { it.bonus == 0 }.sortedWith(best))
            .take((limit - longest.size).coerceAtLeast(0))
        return filler.sortedWith(compareBy<WordResult>({ it.base }, { it.word.length }, { it.word })) + longest
    }
}
