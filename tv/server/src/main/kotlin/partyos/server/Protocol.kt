package partyos.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import partyos.engine.Avatar
import partyos.engine.HostCmd
import partyos.engine.PhoneState
import partyos.engine.PlayerId
import partyos.engine.Role
import partyos.engine.TvState

const val PROTOCOL_VERSION = 1

/** Game settings a host may pass when starting a game. */
private val START_OPTIONS = setOf("teams", "drinks")
private const val MAX_GAME_ACTION = 32

/** One JSON configuration for every wire message; sealed types carry their tag in "t". */
val PartyJson = Json {
    classDiscriminator = "t"
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

@Serializable
sealed interface ClientMsg {
    @Serializable @SerialName("hello") data class Hello(val protocol: Int) : ClientMsg
    @Serializable @SerialName("action") data class Action(val id: String, val round: Int, val payload: JsonObject) : ClientMsg
    @Serializable @SerialName("host") data class Host(val id: String, val cmd: HostCommand) : ClientMsg
    @Serializable @SerialName("ping") data object Ping : ClientMsg
}

@Serializable
sealed interface ServerMsg {
    @Serializable @SerialName("welcome")
    data class Welcome(val playerId: PlayerId?, val role: Role?, val host: Boolean, val protocol: Int = PROTOCOL_VERSION) : ServerMsg
    @Serializable @SerialName("view") data class View(val seq: Long, val view: PhoneState) : ServerMsg
    @Serializable @SerialName("tv") data class Tv(val seq: Long, val tv: TvState) : ServerMsg
    @Serializable @SerialName("ack") data class Ack(val id: String) : ServerMsg
    @Serializable @SerialName("reject") data class Reject(val id: String, val code: String) : ServerMsg
    @Serializable @SerialName("pong") data object Pong : ServerMsg
    @Serializable @SerialName("bye") data class Bye(val reason: String) : ServerMsg
}

@Serializable
sealed interface HostCommand {
    /** [options] are game settings, e.g. `teams` (0 = auto) and `drinks` (0/1) for Brain Drain. */
    @Serializable @SerialName("start")
    data class Start(val gameId: String, val rounds: Int? = null, val options: Map<String, Int> = emptyMap()) : HostCommand
    @Serializable @SerialName("pause") data object Pause : HostCommand
    @Serializable @SerialName("resume") data object Resume : HostCommand
    @Serializable @SerialName("skip") data object Skip : HostCommand
    @Serializable @SerialName("end") data object End : HostCommand
    @Serializable @SerialName("kick") data class Kick(val playerId: PlayerId) : HostCommand
    @Serializable @SerialName("setRounds") data class SetRounds(val rounds: Int) : HostCommand
    /** A shared lobby setting: rounds, teams, drinks, game (index into /api/games), captain (phones allowed, 0/1). */
    @Serializable @SerialName("setOption") data class SetOption(val key: String, val value: Int) : HostCommand
    @Serializable @SerialName("makeCaptain") data class MakeCaptain(val playerId: PlayerId) : HostCommand
    /** A game's own show control, e.g. `shuffle` during Brain Drain's Team Up. */
    @Serializable @SerialName("gameAction") data class GameAction(val action: String) : HostCommand

    fun toCmd(): HostCmd = when (this) {
        is Start -> HostCmd.StartGame(
            gameId,
            options.filterKeys { it in START_OPTIONS }.mapValues { (_, v) -> v.coerceIn(0, 16) } + (rounds?.let { mapOf("rounds" to it) } ?: emptyMap()),
        )
        Pause -> HostCmd.Pause
        Resume -> HostCmd.Resume
        Skip -> HostCmd.SkipPhase
        End -> HostCmd.EndGame
        is Kick -> HostCmd.Kick(playerId)
        is SetRounds -> HostCmd.SetRounds(rounds)
        is SetOption -> HostCmd.SetOption(key, value)
        is MakeCaptain -> HostCmd.MakeCaptain(playerId)
        is GameAction -> HostCmd.GameAction(action.take(MAX_GAME_ACTION))
    }
}

@Serializable
data class JoinRequest(val room: String, val name: String, val avatar: Avatar, val spectator: Boolean = false)

@Serializable
data class JoinResponse(val playerId: PlayerId, val token: String)

@Serializable
data class PinRequest(val pin: String)

@Serializable
data class RoleRequest(val token: String, val role: Role)

@Serializable
data class HostLoginResponse(val hostToken: String)

/** For a browser TV on the host machine itself: host rights plus the address phones should scan. */
@Serializable
data class TvSessionResponse(val hostToken: String, val room: String, val joinUrl: String?)

@Serializable
data class ErrorResponse(val error: String, val retryAfterSec: Int? = null)

@Serializable
data class GameListing(val id: String, val title: String, val tagline: String, val minPlayers: Int, val maxPlayers: Int)
