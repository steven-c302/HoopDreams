package partyos.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Runtime bookkeeping for the game in progress. Mutated only by PartyEngine. */
internal class ActiveGame<S : Any>(
    val module: GameModule<S>,
    var state: S?,
    var phaseSeq: Int,
    var deadlineAt: Long?,
    var pausedRemaining: Long?,
    var paused: Boolean,
    var pauseReason: String?,
    var tutorialAcks: MutableSet<PlayerId>?,
    val scores: MutableMap<PlayerId, Int>,
    val seed: Long,
    val handled: ArrayDeque<String>,
    val settings: Map<String, Int>,
    val highlights: MutableList<String>,
) {
    var finishPending = false

    fun remember(actionId: String) {
        handled.addLast(actionId)
        while (handled.size > MAX_HANDLED) handled.removeFirst()
    }

    fun remaining(now: Long): Long? = if (paused) pausedRemaining else deadlineAt?.let { maxOf(0, it - now) }

    fun snapshot(now: Long) = GameSnapshot(
        gameId = module.info.id,
        state = state?.let { Json.encodeToJsonElement(module.stateSerializer, it) },
        phaseSeq = phaseSeq,
        remainingMs = remaining(now),
        pauseReason = pauseReason,
        tutorialAcks = tutorialAcks?.toList(),
        scores = scores.mapKeys { it.key.v },
        seed = seed,
        handled = handled.toList(),
        settings = settings,
        highlights = highlights.toList(),
    )

    companion object {
        const val MAX_HANDLED = 2048
        private val lenient = Json { ignoreUnknownKeys = true }

        fun <S : Any> restore(module: GameModule<S>, s: GameSnapshot) = ActiveGame(
            module = module,
            state = s.state?.let { lenient.decodeFromJsonElement(module.stateSerializer, it) },
            phaseSeq = s.phaseSeq,
            deadlineAt = null,
            pausedRemaining = s.remainingMs,
            paused = true,
            pauseReason = "RESTORED",
            tutorialAcks = s.tutorialAcks?.toMutableSet(),
            scores = s.scores.mapKeys { PlayerId(it.key) }.toMutableMap(),
            seed = s.seed,
            handled = ArrayDeque(s.handled),
            settings = s.settings,
            highlights = s.highlights.toMutableList(),
        )
    }
}

@Serializable
data class GameSnapshot(
    val gameId: String,
    val state: JsonElement?,
    val phaseSeq: Int,
    val remainingMs: Long?,
    val pauseReason: String?,
    val tutorialAcks: List<PlayerId>?,
    val scores: Map<String, Int>,
    val seed: Long,
    val handled: List<String>,
    val settings: Map<String, Int>,
    val highlights: List<String>,
)
