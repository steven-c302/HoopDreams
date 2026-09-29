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
     * Pick one. [style]: null (plain list), `shapes` (A-D answer buttons), `sides` (two giant buttons), `teams`
     * (coloured team buttons) or `faces` (option ids are player ids; the phone draws each player's face).
     * [votes] maps option id to the teammates currently on it.
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

    /**
     * Imposter: a face-down card the phone flips only while it is held. [face] is the word, or IMPOSTER; [role] is
     * `crew` or `imposter` and never changes how the card looks face-down. [kind] names the "Got it" action (null =
     * no button); [input] is the clue field shown under a small peek chip.
     */
    @Serializable @SerialName("secret")
    data class Secret(
        val title: String,
        val face: String,
        val category: String,
        val role: String,
        val note: String? = null,
        val kind: String? = null,
        val acknowledged: Boolean = false,
        val input: SecretInput? = null,
    ) : Screen

    /**
     * Doodle Dash, the drawer: the word to draw and how the room is doing. The pad itself is the phone's canvas; its
     * strokes travel over the ink channel, not through this screen. [tailMs] is the draw time left after the current
     * hint stage, added to the phone's stage countdown.
     */
    @Serializable @SerialName("draw")
    data class Draw(
        val word: String,
        val difficulty: Int,
        val guessed: Int,
        val expected: Int,
        val tailMs: Long,
        val note: String? = null,
    ) : Screen

    /**
     * Answer & Question's board: [categories] across, [cells] by column and row. The phone picks a cell locally and
     * confirms it with `{ kind: "pick", cell }`. [canPick] is true for the player holding the board and for the
     * captain, who then sees [pickFor], the name of the player they are picking for.
     */
    @Serializable @SerialName("board")
    data class Board(
        val prompt: String,
        val categories: List<String>,
        val cells: List<BoardCell>,
        val canPick: Boolean,
        val pickFor: String? = null,
        val note: String? = null,
    ) : Screen

    /**
     * Doodle Dash, a guesser: the blanks (revealed letters in capitals), whether you have it, and your last miss.
     * [kind] names the guess action; [close] means your last miss was near.
     */
    @Serializable @SerialName("guess")
    data class Guess(
        val drawer: String,
        val blanks: String,
        val kind: String,
        val solved: Boolean,
        val points: Int? = null,
        val close: Boolean = false,
        val last: String? = null,
        val guessed: Int,
        val expected: Int,
        val tailMs: Long,
    ) : Screen

    /**
     * The BUZZ button. [state]: `reading` (clue still being read), `open`, `locked` (rang in too early, [lockedMs] left),
     * `beaten` (someone else has the floor, named in [detail]), `tried` (you already missed this clue) or `out`
     * (you joined after it started). [live] is true while the buzz window is open, so a `locked` phone can open itself
     * when the lockout runs out. A buzz sends `{ kind: "buzz" }`.
     */
    @Serializable @SerialName("buzzer")
    data class Buzzer(
        val state: String,
        val category: String,
        val value: Int,
        val detail: String? = null,
        val lockedMs: Int = 0,
        val live: Boolean = false,
    ) : Screen

    /**
     * Home Turf: your token, the one thing to do now, your places (with what you can do to each), who you could
     * trade with, and any open trade or auction. Faces are ids only; phones already have everyone's from `scores`.
     */
    @Serializable @SerialName("turf")
    data class Turf(
        val me: TurfMe?,
        val prompt: TurfPrompt,
        val deeds: List<TurfDeed> = emptyList(),
        val partners: List<TurfPartner> = emptyList(),
        val trade: TurfTradeView? = null,
        /** You hold your token's dice and trades are open right now. */
        val canTrade: Boolean = false,
        val auction: TurfBidPad? = null,
        val pieces: List<Choice> = emptyList(),
        /** Your latest drink call this turn, if any. */
        val drink: String? = null,
        val drinks: Boolean = true,
    ) : Screen

    /**
     * Sprawl: your hand and cards, the one thing to do now, the island (static [map] plus live pieces), where you're
     * placing ([spots] of [spotKind]: vertex | edge | hex), where you could build, and any open trade.
     */
    @Serializable @SerialName("sprawl")
    data class Sprawl(
        val me: SprawlMe?,
        val prompt: SprawlPrompt,
        val map: SprawlMapTv,
        val robber: Int,
        val vOwner: List<Int>,
        val vLevel: List<Int>,
        val eOwner: List<Int>,
        /** Each seat's colour, by seat index. */
        val colors: List<String>,
        val spots: List<Int> = emptyList(),
        val spotKind: String? = null,
        val build: SprawlBuild = SprawlBuild(),
        /** Who you can steal from (steal phase). */
        val victims: List<Choice> = emptyList(),
        val partners: List<SprawlPartner> = emptyList(),
        val trade: SprawlTradeView? = null,
        /** It's your main phase and no trade is open: you can offer one or trade with the bank. */
        val canTrade: Boolean = false,
        val bank: List<Int> = emptyList(),
        /** Cards you must discard now. */
        val discard: Int = 0,
        /** Your latest drink call this turn, if any. */
        val drink: String? = null,
        val drinks: Boolean = true,
    ) : Screen

    /**
     * Hot Type: the board and your own found words, newest first. [phase] is ready | hunt | press | reveal | scores.
     * [tiles] is empty until the hunt starts. [score] is your base total during the hunt and your round total after.
     * In the reveal, each [found] word carries how many players found it and your unique bonus. A swipe is sent as
     * `{ kind: "word", path: [tile index, ...] }`.
     */
    @Serializable @SerialName("hunt")
    data class Hunt(
        val phase: String,
        val round: Int,
        val totalRounds: Int,
        val size: Int,
        val tiles: List<String> = emptyList(),
        val found: List<HuntFound> = emptyList(),
        val score: Int = 0,
        val note: String? = null,
    ) : Screen
}

/** The clue field under Imposter's peek chip. */
@Serializable
data class SecretInput(val prompt: String, val maxLen: Int, val value: String?, val kind: String, val hint: String? = null)

/** One square on the phone's board. */
@Serializable
data class BoardCell(val id: String, val col: Int, val row: Int, val value: Int, val used: Boolean)

/** One playing card. rank 1 (ace) to 13 (king), suit 0..3 = spades, hearts, diamonds, clubs. rank 0 = face down. */
@Serializable
data class PlayingCard(val rank: Int, val suit: Int)

/** Game-specific TV payloads. Every game adds its variant here so the TV and fixtures stay typed. */
@Serializable
sealed interface TvGame

@Serializable @SerialName("generic")
data class GenericTv(val title: String, val lines: List<String>) : TvGame

@Serializable
data class PlayerSummary(val id: PlayerId, val name: String, val avatar: Avatar, val role: Role, val connected: Boolean, val water: Boolean = false)

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
    /** The player holding the crown, who can run the show from their phone (null when phone control is off). */
    val captain: PlayerId? = null,
    /** Lobby settings shared by the TV and the captain's phone: rounds, teams, drinks, game (index), captain (0/1). */
    val settings: Map<String, Int> = emptyMap(),
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
    /** True when this phone holds the crown. */
    val captain: Boolean = false,
    val captainName: String? = null,
    val settings: Map<String, Int> = emptyMap(),
    /** Everyone at the party, sent to the captain only (for passing the crown). */
    val crew: List<PlayerSummary> = emptyList(),
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
    /** Sets a shared lobby setting; see [PartyEngine.optionRange]. */
    data class SetOption(val key: String, val value: Int) : HostCmd
    /** Hands the crown to [player]. */
    data class MakeCaptain(val player: PlayerId) : HostCmd
    /** A show control the running game defines, e.g. Brain Drain's "shuffle" during Team Up. */
    data class GameAction(val action: String) : HostCmd
}

/** One word on a Hot Type phone. [finders] and [bonus] are 0 until the reveal. */
@Serializable
data class HuntFound(val word: String, val points: Int, val finders: Int = 0, val bonus: Int = 0)
