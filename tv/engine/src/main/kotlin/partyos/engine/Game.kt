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
    /**
     * Resets the current phase's deadline without starting a new phase (the round number stays, so phones keep their
     * screen and in-flight actions stay valid), e.g. an auction clock that restarts on every bid. Null = no deadline.
     */
    data class Deadline(val durationMs: Long?) : Effect
    data class Award(val player: PlayerId, val points: Int, val reason: String) : Effect
    data class Highlight(val text: String) : Effect
    /** Marks a content item (e.g. a question id) as used for the rest of the party. */
    data class UseContent(val id: String) : Effect
    /** Stores a small value for the rest of the party (e.g. the last trivia show's teams), read back via [GameContext.memory]. */
    data class Remember(val key: String, val value: String) : Effect
    data object Finish : Effect
}

data class Step<S>(val state: S, val effects: List<Effect> = emptyList())

val TIMER_SCALES = listOf(1.0, 1.5, 2.0)

/** Drink wording for a player on water tonight: "Drink 2 sips" becomes "Drink 2 sips of water". */
fun ofWater(water: Boolean) = if (water) " of water" else ""

class GameContext(
    val now: Long,
    val random: Random,
    /** Non-kicked PLAYER-role members, in join order. */
    val players: List<Player>,
    val scores: Map<PlayerId, Int>,
    val settings: Map<String, Int>,
    val usedContent: Set<String>,
    val memory: Map<String, String> = emptyMap(),
    /** The part of [usedContent] played at this party (the rest was remembered from earlier nights). */
    val playedThisParty: Set<String> = usedContent,
    /** Time left on the current phase's deadline (frozen while paused), or null when it has none. */
    val remainingMs: Long? = null,
) {
    fun player(id: PlayerId) = players.firstOrNull { it.id == id }

    /** The lobby's timer length ("timers": 0 normal, 1 relaxed, 2 no rush) as a factor on decision timers. */
    val timerScale: Double get() = TIMER_SCALES.getOrElse(settings["timers"] ?: 0) { 1.0 }

    /**
     * A decision timer ([ms] to answer, bet, roll...) stretched by [timerScale]. Games use it for the time players
     * get to act, never for animations and reveals, so a relaxed show isn't a slower one to watch.
     */
    fun timer(ms: Long): Long = (ms * timerScale).toLong()

    /**
     * The [items] nobody has played yet ([usedContent] is oldest first, and remembered across restarts). Once a pack
     * is used up, the older half of what was played on earlier nights comes back, so a pack never runs dry across
     * nights; nothing played at this party ever comes back tonight (then this is empty, as when a pack runs out).
     */
    fun <T> fresh(items: List<T>, id: (T) -> String): List<T> {
        val unused = items.filter { id(it) !in usedContent }
        if (unused.isNotEmpty()) return unused
        val earlier = items.filter { id(it) !in playedThisParty }
        if (earlier.isEmpty()) return emptyList()
        val age = usedContent.withIndex().associate { (i, v) -> v to i }
        return earlier.sortedBy { age[id(it)] ?: -1 }.take(maxOf(1, earlier.size / 2))
    }
    fun isConnected(id: PlayerId) = player(id)?.connected == true
}

interface GameModule<S : Any> {
    val info: GameInfo
    val stateSerializer: KSerializer<S>
    fun start(ctx: GameContext): Step<S>
    fun onAction(s: S, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<S>
    fun onDeadline(s: S, ctx: GameContext): Step<S>
    fun onPresence(s: S, who: PlayerId, present: Boolean, ctx: GameContext): Step<S> = Step(s)
    /** A host or captain show control ([HostCmd.GameAction]); throw [Reject] if it doesn't apply right now. */
    fun onHost(s: S, action: String, ctx: GameContext): Step<S> = throw Reject("UNSUPPORTED")
    /** Players still expected to act in the current phase, or null when the phase takes no input. */
    fun waitingOn(s: S): Set<PlayerId>?
    fun tvView(s: S, ctx: GameContext): TvGame
    fun playerView(s: S, who: PlayerId, ctx: GameContext): Screen
    /** False if a saved state can no longer be played (e.g. its content left the pack); restore then drops the game. */
    fun restorable(s: S): Boolean = true
    /**
     * True for actions that stay valid across phase changes (the game checks them itself, e.g. by an offer id), so
     * the runtime doesn't refuse them as STALE when the round moved on while the player was composing them.
     */
    fun phaseFree(payload: JsonObject): Boolean = false
}

class GameRegistry(modules: List<GameModule<*>>) {
    private val byId = modules.associateBy { it.info.id }
    val all: List<GameModule<*>> = modules
    operator fun get(id: String): GameModule<*>? = byId[id]
}
