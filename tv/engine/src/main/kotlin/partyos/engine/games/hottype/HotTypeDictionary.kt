package partyos.engine.games.hottype

import java.util.Arrays

/** The words Hot Type accepts: lower-case a-z words of 3 to 25 letters, held as one sorted array so it stays small. */
class HotTypeDictionary(words: Collection<String>) {
    private val list: Array<String> = words.map { it.lowercase() }.filter(::ok).distinct().sorted().toTypedArray()
    val size: Int get() = list.size

    fun contains(word: String): Boolean = Arrays.binarySearch(list, word) >= 0

    /** True when some word starts with [prefix] (a word counts as its own prefix). */
    fun hasPrefix(prefix: String): Boolean {
        var lo = 0
        var hi = list.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo < list.size && list[lo].startsWith(prefix)
    }

    companion object {
        const val MIN = 3
        const val MAX = 25

        private fun ok(w: String) = w.length in MIN..MAX && w.all { it in 'a'..'z' }

        val core: HotTypeDictionary by lazy { HotTypeDictionary(lines("/hottype/words.txt")) }

        /** Words the reveal never spotlights as "the one that got away". They still count when a player finds one. */
        val blocked: Set<String> by lazy { lines("/hottype/blocked.txt").map { it.lowercase() }.filter(::ok).toSet() }

        private fun lines(resource: String): List<String> {
            val stream = requireNotNull(HotTypeDictionary::class.java.getResourceAsStream(resource)) { "$resource missing" }
            return stream.bufferedReader().useLines { seq -> seq.map { it.trim() }.filter { it.isNotEmpty() }.toList() }
        }
    }
}
