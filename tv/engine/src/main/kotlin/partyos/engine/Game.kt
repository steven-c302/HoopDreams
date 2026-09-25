package partyos.engine

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

/** Thrown by a game to refuse an action; the code is sent to the phone. */
class Reject(val code: String) : Exception(code)

enum class LateJoin { NEXT_ROUND, NEXT_GAME, ANYTIME }

@Serializable
data class TutorialCard(val title: String, val body: String)

data class GameInfo(
    val id: String,
    val title: String,
    val tagline: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val tutorial: List<TutorialCard>,
    val lateJoin: LateJoin,
)

sealed interface Effect {
    /** A new phase begins: the runtime bumps the round number and sets the deadline (null = no deadline). */
    data class Phase(val durationMs: Long?) : Effect
    data class Award(val player: PlayerId, val points: Int, val reason: String) : Effect
    data class Highlight(val text: String) : Effect
    /** Marks a content item (e.g. a question id) as used for the rest of the party. */
    data class UseContent(val id: String) : Effect
    data object Finish : Effect
}

data class Step<S>(val state: S, val effects: List<Effect> = emptyList())

class GameContext(
    val now: Long,
    val random: Random,
    /** Non-kicked PLAYER-role members, in join order. */
    val players: List<Player>,
    val scores: Map<PlayerId, Int>,
    val settings: Map<String, Int>,
    val usedContent: Set<String>,
) {
    fun player(id: PlayerId) = players.firstOrNull { it.id == id }
    fun isConnected(id: PlayerId) = player(id)?.connected == true
}

interface GameModule<S : Any> {
    val info: GameInfo
    val stateSerializer: KSerializer<S>
    fun start(ctx: GameContext): Step<S>
    fun onAction(s: S, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<S>
    fun onDeadline(s: S, ctx: GameContext): Step<S>
    fun onPresence(s: S, who: PlayerId, present: Boolean, ctx: GameContext): Step<S> = Step(s)
    /** Players still expected to act in the current phase, or null when the phase takes no input. */
    fun waitingOn(s: S): Set<PlayerId>?
    fun tvView(s: S, ctx: GameContext): TvGame
    fun playerView(s: S, who: PlayerId, ctx: GameContext): Screen
}

class GameRegistry(modules: List<GameModule<*>>) {
    private val byId = modules.associateBy { it.info.id }
    val all: List<GameModule<*>> = modules
    operator fun get(id: String): GameModule<*>? = byId[id]
}
