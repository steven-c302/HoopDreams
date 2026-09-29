package partyos.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InkBoardTest {
    private fun start(s: Int, c: Int = 0, w: Int = 0, x: Int = 10, y: Int = 10, p: Int = 50) = InkOp.Start(s, c, w, x, y, p)
    private fun InkBoard.strokes(turn: Int = 1) = sync().turns.firstOrNull { it.turn == turn }?.strokes ?: emptyList()

    @Test fun aStrokeGrowsAndCloses() {
        val b = InkBoard()
        b.apply(1, listOf(start(1, c = 2, w = 1, x = 100, y = 100)))
        b.apply(1, listOf(InkOp.Pts(1, listOf(110, 105, 50, 120, 110, 60))))
        assertEquals(listOf(InkStroke(1, 2, 1, listOf(100, 100, 50, 110, 105, 50, 120, 110, 60), open = true)), b.strokes())
        b.apply(1, listOf(InkOp.End(1)))
        assertEquals(false, b.strokes().single().open)
    }

    @Test fun eachAcceptedBatchGetsTheNextSequenceNumberAndOnlyTheValidOpsComeBack() {
        val b = InkBoard()
        val (n1, ops1) = b.apply(1, listOf(start(1), InkOp.Pts(9, listOf(1, 1, 1))))!!
        assertEquals(1, n1)
        assertEquals(listOf(start(1)), ops1) // the pts for a stroke that doesn't exist were dropped
        assertNull(b.apply(1, listOf(InkOp.Pts(9, listOf(1, 1, 1))))) // nothing valid: no event, no number used
        assertEquals(2, b.apply(1, listOf(InkOp.End(1)))!!.first)
        assertEquals(2, b.seq)
    }

    @Test fun invalidOpsAreDropped() {
        val b = InkBoard()
        assertNull(b.apply(1, listOf(start(1, x = 1001))))
        assertNull(b.apply(1, listOf(start(1, y = 751))))
        assertNull(b.apply(1, listOf(start(1, x = -1))))
        assertNull(b.apply(1, listOf(start(1, p = 101))))
        assertNull(b.apply(1, listOf(start(1, c = 8))))
        assertNull(b.apply(1, listOf(start(1, w = 3))))
        b.apply(1, listOf(start(1)))
        assertNull(b.apply(1, listOf(start(1)))) // a stroke id is used once
        assertNull(b.apply(1, listOf(InkOp.Pts(1, listOf(1, 2)))))          // not whole points
        assertNull(b.apply(1, listOf(InkOp.Pts(1, emptyList()))))
        assertNull(b.apply(1, listOf(InkOp.Pts(1, listOf(2000, 1, 1)))))    // off the grid
        assertNull(b.apply(1, listOf(InkOp.Pts(1, List(402) { 5 }))))       // too many numbers at once
        assertNull(b.apply(1, emptyList()))
        assertNull(b.apply(1, List(65) { InkOp.Undo }))                     // too many ops in one message
        assertEquals(1, b.strokes().size)
    }

    @Test fun aClosedStrokeCannotGrowOrCloseAgain() {
        val b = InkBoard()
        b.apply(1, listOf(start(1), InkOp.End(1)))
        assertNull(b.apply(1, listOf(InkOp.Pts(1, listOf(5, 5, 5)))))
        assertNull(b.apply(1, listOf(InkOp.End(1))))
    }

    @Test fun undoRemovesTheLastStrokeAndClearRemovesAll() {
        val b = InkBoard()
        b.apply(1, listOf(start(1), InkOp.End(1), start(2), InkOp.End(2)))
        b.apply(1, listOf(InkOp.Undo))
        assertEquals(listOf(1), b.strokes().map { it.s })
        b.apply(1, listOf(start(3), InkOp.Clear))
        assertTrue(b.strokes().isEmpty())
        assertNull(b.apply(1, listOf(InkOp.Undo)))  // nothing to undo
        assertNull(b.apply(1, listOf(InkOp.Clear))) // nothing to clear
    }

    @Test fun aStrokeIdCanComeBackOnceItsStrokeWasUndone() {
        val b = InkBoard()
        b.apply(1, listOf(start(1), InkOp.Undo))
        assertNotNull(b.apply(1, listOf(start(1))))
    }

    /** The same sequence and result are pinned in the controller's board.test.ts, so both reducers agree. */
    @Test fun theGoldenSequenceEndsWhereTheControllerDoes() {
        val b = InkBoard()
        val ops = listOf(
            start(1, 2, 1, 100, 100, 50), InkOp.Pts(1, listOf(110, 105, 50, 120, 110, 60)), InkOp.End(1),
            start(2, 0, 0, 500, 500, 50), InkOp.Pts(2, listOf(510, 500, 50)), InkOp.Undo,
            start(3, 7, 2, 10, 20, 30), InkOp.End(3), InkOp.End(3),
            InkOp.Undo, InkOp.Undo, InkOp.Undo,
            start(1, 1, 1, 1, 2, 3), InkOp.Clear, start(4, 3, 0, 900, 700, 100),
        )
        ops.forEach { b.apply(1, listOf(it)) }
        assertEquals(listOf(InkStroke(4, 3, 0, listOf(900, 700, 100), open = true)), b.strokes())
    }

    @Test fun aTurnHasAtMost200Strokes() {
        val b = InkBoard()
        repeat(200) { assertNotNull(b.apply(1, listOf(start(it)))) }
        assertNull(b.apply(1, listOf(start(200))))
    }

    @Test fun aTurnHasAtMost40000Points() {
        val b = InkBoard()
        b.apply(1, listOf(start(1)))
        val chunk = InkOp.Pts(1, List(399) { 5 }) // 133 points
        var points = 1
        while (b.apply(1, listOf(chunk)) != null) points += 133
        assertTrue(points <= InkBoard.MAX_POINTS, "$points points got in")
        assertTrue(points > InkBoard.MAX_POINTS - 133)
    }

    @Test fun turnsAreKeptSeparatelyAndOlderTurnsAreIgnored() {
        val b = InkBoard()
        b.apply(1, listOf(start(1)))
        b.apply(2, listOf(start(1)))
        assertEquals(false, b.strokes(1).single().open) // moving on closes the old turn's open stroke
        assertEquals(listOf(1, 2), b.sync().turns.map { it.turn })
        assertNull(b.apply(1, listOf(start(2)))) // a late batch for turn 1
    }

    @Test fun resetForgetsTheDrawingsButNotTheSequence() {
        val b = InkBoard()
        b.apply(1, listOf(start(1)))
        b.reset()
        assertTrue(b.sync().turns.isEmpty())
        assertEquals(1, b.sync().upTo)
        assertEquals(2, b.apply(1, listOf(start(1)))!!.first)
    }
}
