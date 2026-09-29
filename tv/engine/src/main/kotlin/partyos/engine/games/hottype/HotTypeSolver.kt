package partyos.engine.games.hottype

import kotlin.random.Random

/** Finds every word a board holds, by depth-first search that stops as soon as no dictionary word starts with the letters so far. */
class HotTypeSolver(private val dict: HotTypeDictionary) {
    /** Each traceable word of 3 or more letters, with one path that spells it. */
    fun solve(tiles: List<String>, size: Int): Map<String, List<Int>> {
        val found = LinkedHashMap<String, List<Int>>()
        val used = BooleanArray(tiles.size)
        val path = ArrayList<Int>()
        fun walk(at: Int, prefix: String) {
            val word = prefix + tiles[at].lowercase()
            if (!dict.hasPrefix(word)) return
            used[at] = true
            path += at
            if (word.length >= HotTypeDictionary.MIN && dict.contains(word) && word !in found) found[word] = path.toList()
            for (n in HotTypeBoard.neighbours(at, size)) if (!used[n]) walk(n, word)
            path.removeAt(path.lastIndex)
            used[at] = false
        }
        for (i in tiles.indices) walk(i, "")
        return found
    }

    /** The longest word on the board that nobody [found] and that is not [blocked] (ties break alphabetically), or null. */
    fun bestMissed(tiles: List<String>, size: Int, found: Set<String>, blocked: Set<String>): String? =
        solve(tiles, size).keys.filter { it !in found && it !in blocked }
            .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
            .firstOrNull()
}

/** Deals boards until one is worth playing: enough words, and at least one long one. Falls back to the best of [attempts]. */
class HotTypeDealer(private val solver: HotTypeSolver, private val attempts: Int = 200) {
    fun deal(size: Int, random: Random): List<String> {
        var best: List<String>? = null
        var bestScore = -1
        repeat(attempts) {
            val tiles = HotTypeBoard.deal(size, random)
            val words = solver.solve(tiles, size).keys
            val longest = words.maxOfOrNull { it.length } ?: 0
            if (words.size >= minWords(size) && longest >= minLongest(size)) return tiles
            val score = words.size + longest * 10
            if (score > bestScore) {
                bestScore = score
                best = tiles
            }
        }
        return best ?: HotTypeBoard.deal(size, random)
    }

    companion object {
        fun minWords(size: Int) = if (size == 5) 120 else 60
        fun minLongest(size: Int) = if (size == 5) 7 else 6
    }
}
