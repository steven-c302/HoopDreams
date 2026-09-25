package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Choice(val id: String, val text: String)

/** What a phone renders. Each variant maps to one shared controller component. */
@Serializable
sealed interface Screen {
    @Serializable @SerialName("waiting")
    data class Waiting(val title: String, val detail: String? = null) : Screen

    @Serializable @SerialName("text")
    data class TextEntry(val prompt: String, val maxLen: Int, val value: String?, val kind: String, val hint: String? = null) : Screen

    @Serializable @SerialName("choice")
    data class ChoiceList(val prompt: String, val options: List<Choice>, val selected: String?, val kind: String) : Screen

    @Serializable @SerialName("tutorial")
    data class Tutorial(val cards: List<TutorialCard>, val acknowledged: Boolean) : Screen

    @Serializable @SerialName("scores")
    data class Scores(val title: String, val rows: List<ScoreRow>) : Screen
}

/** Game-specific TV payloads. Every game adds its variant here so the TV and fixtures stay typed. */
@Serializable
sealed interface TvGame

@Serializable @SerialName("generic")
data class GenericTv(val title: String, val lines: List<String>) : TvGame

@Serializable
data class PlayerSummary(val id: PlayerId, val name: String, val avatar: Avatar, val role: Role, val connected: Boolean)

@Serializable
data class ScoreRow(val id: PlayerId, val name: String, val avatar: Avatar, val score: Int)

@Serializable
data class TutorialView(val cards: List<TutorialCard>, val acked: List<PlayerId>)

@Serializable
data class StageInfo(
    val gameId: String,
    val title: String,
    val phaseSeq: Int,
    val deadlineAt: Long?,
    val remainingMs: Long?,
    val paused: Boolean,
    val pauseReason: String?,
    val tutorial: TutorialView?,
    val game: TvGame?,
)

@Serializable
data class GameResult(
    val gameId: String,
    val title: String,
    val finishedAt: Long,
    val standings: List<ScoreRow>,
    val highlights: List<String>,
)

@Serializable
data class TvState(
    val roomCode: String,
    val players: List<PlayerSummary>,
    val stage: StageInfo?,
    val scores: List<ScoreRow>,
    val lastResult: GameResult?,
    val gamesPlayed: Int,
)

@Serializable
data class PhoneState(
    val me: PlayerSummary,
    val roomCode: String,
    val gameId: String?,
    val gameTitle: String?,
    val round: Int,
    val paused: Boolean,
    val pauseReason: String?,
    val remainingMs: Long?,
    val screen: Screen,
    val scores: List<ScoreRow>,
)

sealed interface ActionResult {
    data object Ack : ActionResult
    data class Rejected(val code: String) : ActionResult
}

sealed interface HostCmd {
    data class StartGame(val gameId: String, val settings: Map<String, Int> = emptyMap()) : HostCmd
    data object Pause : HostCmd
    data object Resume : HostCmd
    data object SkipPhase : HostCmd
    data object EndGame : HostCmd
    data class Kick(val player: PlayerId) : HostCmd
    data class SetRounds(val rounds: Int) : HostCmd
}
