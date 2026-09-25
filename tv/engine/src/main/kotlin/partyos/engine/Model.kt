package partyos.engine

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class PlayerId(val v: String) {
    override fun toString() = v
}

@Serializable
data class Avatar(val emoji: String, val color: String)

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
