package partyos.engine

import kotlinx.serialization.Serializable

enum class JoinError { WRONG_ROOM, FULL, NAME_TAKEN, BAD_NAME }

sealed interface JoinResult {
    data class Joined(val player: Player, val token: String) : JoinResult
    data class Failed(val error: JoinError) : JoinResult
}

@Serializable
data class PartySnapshot(
    val roomCode: String,
    val createdAt: Long,
    val players: List<Player>,
    val tokenHashes: Map<String, PlayerId>,
    val pinSalt: String? = null,
    val pinHash: String? = null,
)

/**
 * The single authority for one party. Not thread-safe: callers serialise access (see server's PartyHost).
 */
class PartyEngine private constructor(
    private val clock: Clock,
    private val entropy: Entropy,
    val roomCode: String,
    private val createdAt: Long,
    players: List<Player>,
    tokenHashes: Map<String, PlayerId>,
    private var pinSalt: String?,
    private var pinHash: String?,
) {
    constructor(clock: Clock, entropy: Entropy) :
        this(clock, entropy, newRoomCode(entropy), clock.now(), emptyList(), emptyMap(), null, null)

    private val roster = LinkedHashMap<PlayerId, Player>().apply { players.forEach { put(it.id, it) } }
    private val tokens = HashMap(tokenHashes)

    val players: List<Player> get() = roster.values.filterNot { it.kicked }

    fun player(id: PlayerId): Player? = roster[id]?.takeUnless { it.kicked }

    fun join(room: String, rawName: String, avatar: Avatar, role: Role): JoinResult {
        if (!room.trim().equals(roomCode, ignoreCase = true)) return JoinResult.Failed(JoinError.WRONG_ROOM)
        val name = rawName.trim()
        if (name.isEmpty() || name.length > MAX_NAME || name.any { Character.isISOControl(it) }) {
            return JoinResult.Failed(JoinError.BAD_NAME)
        }
        if (players.any { it.name.equals(name, ignoreCase = true) }) return JoinResult.Failed(JoinError.NAME_TAKEN)
        if (players.count { it.role == role } >= MAX_PER_ROLE) return JoinResult.Failed(JoinError.FULL)
        val player = Player(PlayerId(entropy.token().take(12)), name, avatar.sanitized(), role, clock.now())
        val token = entropy.token()
        roster[player.id] = player
        tokens[sha256(token)] = player.id
        return JoinResult.Joined(player, token)
    }

    fun resolve(token: String): PlayerId? = tokens[sha256(token)]?.takeIf { player(it) != null }

    fun setPresence(id: PlayerId, connected: Boolean) {
        roster[id]?.let { roster[id] = it.copy(connected = connected) }
    }

    fun kick(id: PlayerId) {
        roster[id]?.let { roster[id] = it.copy(kicked = true, connected = false) }
        tokens.values.removeAll { it == id }
    }

    fun setPin(pin: String) {
        val salt = entropy.token()
        pinSalt = salt
        pinHash = sha256(salt + pin)
    }

    fun checkPin(pin: String): Boolean {
        val salt = pinSalt ?: return false
        return sha256(salt + pin) == pinHash
    }

    fun snapshot() = PartySnapshot(roomCode, createdAt, roster.values.toList(), tokens.toMap(), pinSalt, pinHash)

    companion object {
        const val MAX_NAME = 16
        const val MAX_PER_ROLE = 16
        private const val ROOM_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ"

        private fun newRoomCode(entropy: Entropy) =
            (1..4).map { ROOM_ALPHABET[entropy.nextInt(ROOM_ALPHABET.length)] }.joinToString("")

        fun restore(s: PartySnapshot, clock: Clock, entropy: Entropy) =
            PartyEngine(clock, entropy, s.roomCode, s.createdAt, s.players, s.tokenHashes, s.pinSalt, s.pinHash)
    }
}

private val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")

private fun Avatar.sanitized() = Avatar(
    emoji = emoji.take(8).filterNot { Character.isISOControl(it) }.ifEmpty { "🙂" },
    color = color.takeIf { HEX_COLOR.matches(it) } ?: "#8A5CF6",
)
