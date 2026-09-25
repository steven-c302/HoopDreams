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
    @Serializable @SerialName("start") data class Start(val gameId: String, val rounds: Int? = null) : HostCommand
    @Serializable @SerialName("pause") data object Pause : HostCommand
    @Serializable @SerialName("resume") data object Resume : HostCommand
    @Serializable @SerialName("skip") data object Skip : HostCommand
    @Serializable @SerialName("end") data object End : HostCommand
    @Serializable @SerialName("kick") data class Kick(val playerId: PlayerId) : HostCommand
    @Serializable @SerialName("setRounds") data class SetRounds(val rounds: Int) : HostCommand

    fun toCmd(): HostCmd = when (this) {
        is Start -> HostCmd.StartGame(gameId, rounds?.let { mapOf("rounds" to it) } ?: emptyMap())
        Pause -> HostCmd.Pause
        Resume -> HostCmd.Resume
        Skip -> HostCmd.SkipPhase
        End -> HostCmd.EndGame
        is Kick -> HostCmd.Kick(playerId)
        is SetRounds -> HostCmd.SetRounds(rounds)
    }
}

@Serializable
data class JoinRequest(val room: String, val name: String, val avatar: Avatar, val spectator: Boolean = false)

@Serializable
data class JoinResponse(val playerId: PlayerId, val token: String)

@Serializable
data class PinRequest(val pin: String)

@Serializable
data class HostLoginResponse(val hostToken: String)

@Serializable
data class ErrorResponse(val error: String, val retryAfterSec: Int? = null)

@Serializable
data class GameListing(val id: String, val title: String, val tagline: String, val minPlayers: Int, val maxPlayers: Int)
