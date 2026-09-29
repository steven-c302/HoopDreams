package partyos.engine.games.hottype

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HotTypeBoardTest {
    private val tiles = "STRPLONAHECIDWKU".map { it.toString() }

    @Test fun theDiceSetsHaveTheRightShape() {
        assertEquals(16, HotTypeBoard.DICE_4.size)
        assertEquals(25, HotTypeBoard.DICE_5.size)
        assertTrue((HotTypeBoard.DICE_4 + HotTypeBoard.DICE_5).all { it.length == 6 })
    }

    @Test fun dealingGivesOneTilePerSquareAndIsRepeatableWithASeed() {
        val four = HotTypeBoard.deal(4, Random(1))
        val five = HotTypeBoard.deal(5, Random(1))
        assertEquals(16, four.size)
        assertEquals(25, five.size)
        assertEquals(four, HotTypeBoard.deal(4, Random(1)))
        assertTrue(four.all { it == "QU" || (it.length == 1 && it[0] in 'A'..'Z') })
    }

    @Test fun aQFaceBecomesTheQuTile() {
        val boards = (1..300).map { HotTypeBoard.deal(4, Random(it)) }
        assertTrue(boards.any { "QU" in it })
        assertTrue(boards.none { "Q" in it })
    }

    @Test fun adjacencyIncludesCornersAndExcludesJumpsAndSelf() {
        assertTrue(HotTypeBoard.adjacent(0, 1, 4))
        assertTrue(HotTypeBoard.adjacent(0, 5, 4)) // the corner
        assertFalse(HotTypeBoard.adjacent(0, 2, 4))
        assertFalse(HotTypeBoard.adjacent(3, 4, 4)) // end of one row, start of the next
        assertFalse(HotTypeBoard.adjacent(5, 5, 4))
    }

    @Test fun aMiddleTileHasEightNeighboursAndACornerThree() {
        assertEquals(8, HotTypeBoard.neighbours(5, 4).size)
        assertEquals(3, HotTypeBoard.neighbours(0, 4).size)
    }

    @Test fun aLegalPathSpellsALowerCaseWord() {
        assertEquals("stone", HotTypeBoard.wordOf(tiles, 4, listOf(0, 1, 5, 6, 9)))
    }

    @Test fun anIllegalPathIsNull() {
        assertNull(HotTypeBoard.wordOf(tiles, 4, emptyList()))
        assertNull(HotTypeBoard.wordOf(tiles, 4, listOf(0, 2))) // a jump
        assertNull(HotTypeBoard.wordOf(tiles, 4, listOf(0, 1, 0))) // repeats a tile
        assertNull(HotTypeBoard.wordOf(tiles, 4, listOf(0, 1, 99))) // out of range
        assertNull(HotTypeBoard.wordOf(tiles, 4, listOf(0, -1)))
        assertNull(HotTypeBoard.wordOf(tiles, 4, (0..20).toList())) // longer than the board
    }

    @Test fun theQuTileCountsAsTwoLetters() {
        val board = listOf("QU", "I", "T", "S") + List(12) { "X" }
        assertEquals("quit", HotTypeBoard.wordOf(board, 4, listOf(0, 1, 2)))
    }
}
