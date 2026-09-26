package partyos.engine

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@JvmInline
@Serializable
value class PlayerId(val v: String) {
    override fun toString() = v
}

/**
 * A player's face: [face] is a preset (`p:00`..`p:15`) or a doodle drawn on the phone (`d:` + strokes of
 * `M`/`L` points on a 0..99 grid, e.g. `d:M10,20L30,40`). Old snapshots stored an emoji in the same slot;
 * sanitising turns anything unrecognised into a preset.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Avatar(@JsonNames("emoji") val face: String = "p:00", val color: String)

@Serializable
enum class Role { PLAYER, SPECTATOR }

@Serializable
data class Player(
    val id: PlayerId,
    val name: String,
    val avatar: Avatar,
    val role: Role,
    val joinedAt: Long,
    val connected: Boolean = false,
    val kicked: Boolean = false,
)

interface Clock {
    fun now(): Long
}

object SystemClock : Clock {
    override fun now() = System.currentTimeMillis()
}

class FakeClock(var t: Long = 0) : Clock {
    override fun now() = t
    fun advance(ms: Long) {
        t += ms
    }
}
