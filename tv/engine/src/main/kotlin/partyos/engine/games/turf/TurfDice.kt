package partyos.engine.games.turf

import kotlinx.serialization.Serializable
import partyos.engine.PartyEngine
import kotlin.random.Random

/**
 * A roll: two white dice and, once a token has passed Payday, the speed die. [speed] is 1..3 (pips), [BUS] or
 * [SCOUT], or 0 when the speed die isn't in play. Only the white dice count for doubles and Timeout.
 */
@Serializable
data class Roll(val a: Int, val b: Int, val speed: Int = 0) {
    val doubles get() = a == b
    val triples get() = speed in 1..3 && a == b && b == speed
    val bus get() = speed == BUS
    val scout get() = speed == SCOUT
    val speedPips get() = if (speed in 1..3) speed else 0
    /** Spaces to move (before a bus choice). */
    val move get() = a + b + speedPips
    /** Utility rent counts every die, with bus and scout as 0. */
    val pips get() = a + b + speedPips
    val dice get() = if (speed == 0) listOf(a, b) else listOf(a, b, speed)

    companion object {
        const val BUS = 4
        const val SCOUT = 5
    }
}

/**
 * The game's own dice. The engine rebuilds its Random per phase, so two rolls in one phase would repeat; Home Turf
 * keeps a seed and a draw counter in its state instead and mixes them with the engine's SplitMix64 finaliser.
 */
object TurfDice {
    /** Speed die faces: 1, 2, 3, bus, and two scouts. */
    private val SPEED_FACES = intArrayOf(1, 2, 3, Roll.BUS, Roll.SCOUT, Roll.SCOUT)

    fun random(seed: Long, draw: Int): Random = Random(PartyEngine.phaseSeed(seed, draw))

    fun roll(seed: Long, draw: Int, speedDie: Boolean): Roll {
        val r = random(seed, draw)
        val a = r.nextInt(1, 7)
        val b = r.nextInt(1, 7)
        val speed = if (speedDie) SPEED_FACES[r.nextInt(SPEED_FACES.size)] else 0
        return Roll(a, b, speed)
    }
}
