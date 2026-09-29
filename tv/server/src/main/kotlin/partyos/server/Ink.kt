package partyos.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One drawing operation. Coordinates are integers on a 1000 by 750 grid; pressure is 0-100. */
@Serializable
sealed interface InkOp {
    @Serializable @SerialName("start") data class Start(val s: Int, val c: Int, val w: Int, val x: Int, val y: Int, val p: Int = 50) : InkOp
    /** Flat [x, y, pressure, x, y, pressure, ...]. */
    @Serializable @SerialName("pts") data class Pts(val s: Int, val pts: List<Int>) : InkOp
    @Serializable @SerialName("end") data class End(val s: Int) : InkOp
    @Serializable @SerialName("undo") data object Undo : InkOp
    @Serializable @SerialName("clear") data object Clear : InkOp
}

@Serializable
data class InkStroke(val s: Int, val c: Int, val w: Int, val pts: List<Int>, val open: Boolean = false)

@Serializable
data class InkTurn(val turn: Int, val strokes: List<InkStroke>)

/**
 * Every drawing of the current game, turn by turn. Ink never touches game state: the drawer's phone sends batches, the
 * game says whether that player may draw right now, and this validates and keeps them so a TV that reconnects (or the
 * time-lapse and gallery) can rebuild the picture. Not thread-safe: PartyHost calls it under its lock.
 */
class InkBoard {
    private class Stroke(val id: Int, val c: Int, val w: Int) {
        val pts = ArrayList<Int>()
        var open = true
    }

    private val turns = LinkedHashMap<Int, ArrayList<Stroke>>()
    private var current = -1

    /** Batches accepted so far this run; never goes back, not even on [reset]. */
    var seq = 0
        private set

    /**
     * Applies [ops] to [turn]'s drawing. Returns the new batch number and the ops that were valid (dropped ones are
     * simply not there), or null when nothing was valid, the message was malformed, or [turn] is older than the current one.
     */
    fun apply(turn: Int, ops: List<InkOp>): Pair<Int, List<InkOp>>? {
        if (ops.isEmpty() || ops.size > MAX_OPS || turn < current) return null
        if (turn != current) {
            turns[current]?.forEach { it.open = false }
            current = turn
        }
        val strokes = turns.getOrPut(turn) { ArrayList() }
        val accepted = ops.filter { accept(strokes, it) }
        if (accepted.isEmpty()) return null
        seq += 1
        return seq to accepted
    }

    fun sync() = ServerMsg.InkSync(
        turns.map { (turn, strokes) -> InkTurn(turn, strokes.map { InkStroke(it.id, it.c, it.w, it.pts.toList(), it.open) }) },
        seq,
    )

    fun reset() {
        turns.clear()
        current = -1
    }

    private fun total(strokes: List<Stroke>) = strokes.sumOf { it.pts.size / 3 }

    private fun onGrid(x: Int, y: Int, p: Int) = x in 0..WIDTH && y in 0..HEIGHT && p in 0..100

    private fun accept(strokes: ArrayList<Stroke>, op: InkOp): Boolean = when (op) {
        is InkOp.Start ->
            if (strokes.size >= MAX_STROKES || total(strokes) >= MAX_POINTS || op.c !in 0 until COLOURS || op.w !in 0 until BRUSHES ||
                !onGrid(op.x, op.y, op.p) || strokes.any { it.id == op.s }
            ) false
            else {
                strokes += Stroke(op.s, op.c, op.w).also { it.pts += listOf(op.x, op.y, op.p) }
                true
            }
        is InkOp.Pts -> {
            val stroke = strokes.firstOrNull { it.id == op.s && it.open }
            when {
                stroke == null || op.pts.isEmpty() || op.pts.size % 3 != 0 || op.pts.size > MAX_NUMBERS -> false
                total(strokes) + op.pts.size / 3 > MAX_POINTS -> false
                !op.pts.indices.step(3).all { onGrid(op.pts[it], op.pts[it + 1], op.pts[it + 2]) } -> false
                else -> { stroke.pts += op.pts; true }
            }
        }
        is InkOp.End -> strokes.firstOrNull { it.id == op.s && it.open }?.let { it.open = false; true } ?: false
        InkOp.Undo -> if (strokes.isEmpty()) false else { strokes.removeAt(strokes.lastIndex); true }
        InkOp.Clear -> if (strokes.isEmpty()) false else { strokes.clear(); true }
    }

    companion object {
        const val MAX_OPS = 64
        const val MAX_NUMBERS = 400
        const val MAX_STROKES = 200
        const val MAX_POINTS = 40_000
        const val COLOURS = 8
        const val BRUSHES = 3
        const val WIDTH = 1000
        const val HEIGHT = 750
    }
}
