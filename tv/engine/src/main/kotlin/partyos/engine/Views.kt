package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One tappable option. [color] tints it (team pickers); [detail] is a second line. */
@Serializable
data class Choice(val id: String, val text: String, val color: String? = null, val detail: String? = null)

/** The team a phone belongs to, for its colour band. */
@Serializable
data class TeamTag(val id: String, val name: String, val color: String)

/** A teammate's live number guess. */
@Serializable
data class TeamGuess(val id: PlayerId, val value: Double)

/** What a phone renders. Each variant maps to one shared controller component. */
@Serializable
sealed interface Screen {
    /** [tone] = win | lose | neutral colours the screen (e.g. your team's result on a reveal). */
    @Serializable @SerialName("waiting")
    data class Waiting(val title: String, val detail: String? = null, val tone: String? = null, val team: TeamTag? = null) : Screen

    @Serializable @SerialName("text")
    data class TextEntry(
        val prompt: String,
        val maxLen: Int,
        val value: String?,
        val kind: String,
        val hint: String? = null,
        val team: TeamTag? = null,
    ) : Screen

    /**
     * Pick one. [style]: null (plain list), `shapes` (A-D answer buttons), `sides` (two giant buttons) or `teams`
     * (coloured team buttons). [votes] maps option id to the teammates currently on it.
     */
    @Serializable @SerialName("choice")
    data class ChoiceList(
        val prompt: String,
        val options: List<Choice>,
        val selected: String?,
        val kind: String,
        val style: String? = null,
        val votes: Map<String, List<PlayerId>> = emptyMap(),
        val team: TeamTag? = null,
    ) : Screen

    /** Type a number on a keypad. [guesses] are teammates' live guesses. */
    @Serializable @SerialName("number")
    data class NumberEntry(
        val prompt: String,
        val unit: String?,
        val value: Double?,
        val kind: String,
        val guesses: List<TeamGuess> = emptyList(),
        val team: TeamTag? = null,
    ) : Screen

    /** Select every option that fits, then lock in. [eliminated] options are shown crossed out (catch-up help). */
    @Serializable @SerialName("multi")
    data class MultiSelect(
        val prompt: String,
        val options: List<Choice>,
        val selected: List<String>,
        val locked: Boolean,
        val kind: String,
        val eliminated: List<String> = emptyList(),
        val votes: Map<String, List<PlayerId>> = emptyMap(),
        val team: TeamTag? = null,
    ) : Screen

    @Serializable @SerialName("tutorial")
    data class Tutorial(val cards: List<TutorialCard>, val acknowledged: Boolean) : Screen

    @Serializable @SerialName("scores")
    data class Scores(val title: String, val rows: List<ScoreRow>) : Screen

    /** A card table: your hand, the dealer's visible cards, and the moves you can make ([kind] names the action). */
    @Serializable @SerialName("cards")
    data class Cards(
        val title: String,
        val hand: List<PlayingCard>,
        val total: Int?,
        val dealer: List<PlayingCard>,
        val actions: List<Choice>,
        val kind: String,
        val note: String? = null,
        /** win | lose | push | neutral: colours the note */
        val tone: String? = null,
        val stack: Int? = null,
    ) : Screen
}

/** One playing card. rank 1 (ace) to 13 (king), suit 0..3 = spades, hearts, diamonds, clubs. rank 0 = face down. */
@Serializable
data class PlayingCard(val rank: Int, val suit: Int)

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
