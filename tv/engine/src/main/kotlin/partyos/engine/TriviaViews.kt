package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * BRAIN DRAIN on the TV. Answers stay out of this payload until [reveal] is set, because the TV state also
 * reaches co-host phones.
 */
@Serializable @SerialName("trivia")
data class TriviaTv(
    /** teamup | intro | question | bet | reveal | victim | steal | standings | podium */
    val phase: String,
    /** teamup | quick | ballpark | sides | heist | write (gauntlet only in shows saved before it was retired) */
    val format: String,
    /** 1-based round number and the number of rounds in the show. */
    val round: Int,
    val totalRounds: Int,
    /** 1-based question within the round. */
    val q: Int,
    val qTotal: Int,
    /** Length of the current phase, for the clock. */
    val durationMs: Long?,
    val prompt: String,
    val category: String? = null,
    /** Answer options in display order (A-D, left/right, or the Gauntlet's options). */
    val options: List<Choice> = emptyList(),
    val unit: String? = null,
    val teams: List<TriviaTeam>,
    val answered: Int = 0,
    val expected: Int = 0,
    val reveal: TriviaReveal? = null,
    val sides: SidesInfo? = null,
    val heist: HeistInfo? = null,
    val drink: DrinkCall? = null,
    /** The bet phase: every guess with its odds and which teams have bet. Absent in every other phase. */
    val bet: BetInfo? = null,
    val hostLine: String? = null,
    val fact: String? = null,
    /** Where a live question came from ("Open Trivia DB"), shown with it; null for the bundled packs. */
    val credit: String? = null,
    val finishLine: Int = 10,
    /** Podium order, winners first (team ids). */
    val podium: List<String> = emptyList(),
    /** End-of-show shout-outs, on the awards screen only. */
    val awards: List<TriviaAward> = emptyList(),
) : TvGame

/** One end-of-show award: brags ("Big Brain") and roasts ("Dead Weight") alike. [line] never names the player. */
@Serializable
data class TriviaAward(val title: String, val player: PlayerId, val line: String, val roast: Boolean = false)

@Serializable
data class TriviaTeam(
    val id: String,
    val name: String,
    val color: String,
    val members: List<PlayerId>,
    val score: Int,
    /** Members who have answered the current question. */
    val answered: Int = 0,
    /** Gauntlet track position. */
    val position: Int = 0,
    val headStart: Int = 0,
)

@Serializable
data class TriviaReveal(
    /** Correct option ids (one for multiple choice and sides; every fitting one in the Gauntlet). */
    val correct: List<String>,
    val answerText: String,
    val number: Double? = null,
    val answers: List<TeamAnswer>,
)

@Serializable
data class TeamAnswer(
    val team: String,
    /** The team's pick (option id), or null if nobody on the team answered. */
    val choice: String? = null,
    val number: Double? = null,
    val picks: List<String> = emptyList(),
    val correct: Boolean = false,
    val points: Int = 0,
    /** Ballpark: 1 = closest. */
    val rank: Int? = null,
    /** Gauntlet: spaces moved this prompt. */
    val moved: Int? = null,
    val bullseye: Boolean = false,
    /** Seconds the team took (speed-bonus rounds). */
    val seconds: Double? = null,
    /** Write It Down: the team's written answer, as first typed. */
    val text: String? = null,
    /** Ballpark: how this team's bet came out; null if they didn't bet. */
    val bet: BetResult? = null,
)

@Serializable
data class SidesInfo(val left: String, val right: String, val item: Int, val items: Int, val history: List<SidesCall> = emptyList())

@Serializable
data class SidesCall(val text: String, val side: String, val teamsRight: List<String>)

@Serializable
data class HeistInfo(val thief: String, val victim: String? = null, val amount: Int = 0)

@Serializable
data class DrinkCall(val teams: List<String>, val sips: Int, val reason: String)

/** Ballpark betting: one backable guess and what backing it pays (odds is the multiplier on the stake). */
@Serializable
data class BetOption(val team: String, val number: Double, val odds: Int)

/** How one team's bet came out. [on] is null when they didn't bet; [delta] is the real change to their score. */
@Serializable
data class BetResult(val on: String? = null, val stake: Int = 0, val odds: Int = 0, val won: Boolean = false, val delta: Int = 0)

/** The bet phase on the TV: every guess with its odds, and which teams have a bet in (not who backed what). */
@Serializable
data class BetInfo(val line: List<BetOption>, val locked: List<String> = emptyList())
