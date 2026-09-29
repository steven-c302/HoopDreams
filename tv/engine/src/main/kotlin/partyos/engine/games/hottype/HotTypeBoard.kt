package partyos.engine.games.hottype

import kotlin.math.abs
import kotlin.random.Random

/** The Hot Type board: Boggle-style dice, dealing, and reading a swipe path as a word. Tiles are upper case; the Qu tile is "QU". */
object HotTypeBoard {
    val DICE_4 = listOf(
        "AAEEGN", "ABBJOO", "ACHOPS", "AFFKPS", "AOOTTW", "CIMOTU", "DEILRX", "DELRVY",
        "DISTTY", "EEGHNW", "EEINSU", "EHRTVW", "EIOSST", "ELRTTY", "HIMNQU", "HLNNRZ",
    )
    val DICE_5 = listOf(
        "AAAFRS", "AAEEEE", "AAFIRS", "ADENNN", "AEEEEM", "AEEGMU", "AEGMNN", "AFIRSY", "BJKQXZ", "CCENST",
        "CEIILT", "CEILPT", "CEIPST", "DDHNOT", "DHHLOR", "DHLNOR", "DHLNOR", "EIIITT", "EMOTTT", "ENSSSU",
        "FIPRSY", "GORRVW", "HIPRRY", "NOOTUW", "OOOTTU",
    )

    private fun dice(size: Int) = if (size == 5) DICE_5 else DICE_4

    /** A fresh board of size² tiles: the dice are shuffled and each is rolled. A `Q` face is the single tile "QU". */
    fun deal(size: Int, random: Random): List<String> =
        dice(size).shuffled(random).take(size * size).map { die ->
            val face = die[random.nextInt(die.length)]
            if (face == 'Q') "QU" else face.toString()
        }

    fun adjacent(a: Int, b: Int, size: Int): Boolean {
        if (a == b) return false
        return abs(a / size - b / size) <= 1 && abs(a % size - b % size) <= 1
    }

    fun neighbours(i: Int, size: Int): List<Int> = (0 until size * size).filter { adjacent(i, it, size) }

    /** The lower-case word a path spells, or null when the path is illegal: empty, out of range, a repeated tile, or a jump. */
    fun wordOf(tiles: List<String>, size: Int, path: List<Int>): String? {
        if (path.isEmpty() || path.size > tiles.size) return null
        if (path.any { it !in tiles.indices } || path.toSet().size != path.size) return null
        if (path.zipWithNext().any { (a, b) -> !adjacent(a, b, size) }) return null
        return path.joinToString("") { tiles[it] }.lowercase()
    }
}
