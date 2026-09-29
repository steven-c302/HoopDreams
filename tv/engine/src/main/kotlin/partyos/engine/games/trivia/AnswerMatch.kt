package partyos.engine.games.trivia

import java.text.Normalizer
import kotlin.math.min

/**
 * Marks typed answers for Write It Down the way a forgiving quizmaster would: case, accents, punctuation,
 * spaces and a leading "the/a/an" never matter; about one typo per five letters is fine; and the distinctive end
 * of a longer answer counts ("da Vinci", "falcon"). Numbers must be exact, and hedges ("red or blue") never count.
 */
object AnswerMatch {
    fun accepts(guess: String, answer: String): Boolean {
        val g = tokens(guess)
        val a = tokens(answer)
        if (g.isEmpty() || a.isEmpty()) return false
        val gs = g.joinToString("")
        val aSolid = a.joinToString("")
        if (gs == aSolid) return true
        if (aSolid.all { it.isDigit() } || gs.all { it.isDigit() }) return false
        if (OR in g && OR !in a) return false
        // One- and two-letter words carry the answer ("O negative", "Vitamin D", "Plan B"): never optional.
        if (a.any { it.length <= 2 && it !in SMALL_WORDS && it !in g }) return false
        // Same words: each may have its own typo ("South" is still not "North"). Different spacing: compare solid,
        // but a longer answer's last word must be there ("vitamin" is not "Vitamin D").
        if (g.size == a.size) {
            if (g.indices.all { typoClose(g[it], a[it]) }) return true
        } else if (typoClose(gs, aSolid) && (a.size == 1 || typoClose(g.last(), a.last()))) {
            return true
        }
        return partial(g, a) || padded(g, a)
    }

    /** A stable key for grouping teammates' answers that aren't right: "The Rhine" and "rhine" are one answer. */
    fun key(text: String): String = tokens(text).joinToString(" ")

    /** Fewer words than the answer: they match answer words, include its last word and cover enough of it. */
    private fun partial(g: List<String>, a: List<String>): Boolean {
        if (a.size < 2 || g.size >= a.size) return false
        val used = BooleanArray(a.size)
        for (w in g) {
            val i = a.indices.firstOrNull { !used[it] && typoClose(w, a[it]) } ?: return false
            used[i] = true
        }
        if (!used.last()) return false
        // "Carolina" is not "North Carolina": qualifiers that change the answer can't be dropped.
        if (a.indices.any { !used[it] && a[it] in QUALIFIERS }) return false
        val covered = a.indices.filter { used[it] }.sumOf { a[it].length }
        return covered >= COVERAGE * a.sumOf { it.length }
    }

    /** More words than the answer, but only filler ("the river Seine", "Mount Everest"). */
    private fun padded(g: List<String>, a: List<String>): Boolean {
        if (g.size <= a.size) return false
        val used = BooleanArray(g.size)
        for (w in a) {
            val i = g.indices.firstOrNull { !used[it] && typoClose(g[it], w) } ?: return false
            used[i] = true
        }
        return g.indices.all { used[it] || g[it] in FILLER }
    }

    private fun typoClose(x: String, y: String): Boolean {
        if (x == y) return true
        if (x.all { it.isDigit() } || y.all { it.isDigit() }) return false
        // People rarely misspell the first letter, and it keeps look-alikes apart ("Troposphere" is not "Stratosphere").
        if (x.first() != y.first()) return false
        return distance(x, y) <= tolerance(y.length)
    }

    private fun tolerance(n: Int) = when {
        n <= 4 -> 0
        n <= 7 -> 1
        n <= 11 -> 2
        else -> 3
    }

    /** Lower-case words with accents, punctuation and a leading article gone; small number words become digits. */
    private fun tokens(text: String): List<String> {
        val plain = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase().replace("&", " and ").replace(Regex("['’`]"), "")
        val words = plain.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.map { NUMBERS[it] ?: it }
        return if (words.size > 1 && words.first() in ARTICLES) words.drop(1) else words
    }

    /** Damerau-Levenshtein (optimal string alignment) distance. */
    private fun distance(s: String, t: String): Int {
        val d = Array(s.length + 1) { IntArray(t.length + 1) }
        for (i in 0..s.length) d[i][0] = i
        for (j in 0..t.length) d[0][j] = j
        for (i in 1..s.length) for (j in 1..t.length) {
            val cost = if (s[i - 1] == t[j - 1]) 0 else 1
            var v = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && s[i - 1] == t[j - 2] && s[i - 2] == t[j - 1]) v = min(v, d[i - 2][j - 2] + 1)
            d[i][j] = v
        }
        return d[s.length][t.length]
    }

    private const val OR = "or"
    private const val COVERAGE = 0.4
    private val ARTICLES = setOf("the", "a", "an")
    /** Short words that are only glue, so leaving them out is fine ("Winter's coming"). */
    private val SMALL_WORDS = setOf("a", "an", "is", "of", "to", "in", "on", "at", "by", "as", "it", "be", "my", "or")
    private val QUALIFIERS = setOf("north", "south", "east", "west", "new", "old", "upper", "lower", "united")
    private val FILLER = setOf("the", "a", "an", "of", "river", "mount", "mt", "lake", "city", "saint", "st", "planet", "team")
    private val NUMBERS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
    ).withIndex().associate { (i, w) -> w to i.toString() }
}
