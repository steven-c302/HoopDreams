package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("imposter")
data class ImposterTv(
    /** role | clue | discuss | vote | result | guess | scores | podium */
    val phase: String,
    val round: Int,
    val totalRounds: Int,
    val finalRound: Boolean,
    val category: String,
    /** Looked (role), clues in (clue), votes in (vote) or guesses in (guess). */
    val submitted: Int,
    val expected: Int,
    val imposterCount: Int,
    /** Discuss onward: one row per player in join order; text null = no clue in time. */
    val clues: List<ImposterClue> = emptyList(),
    /** Held back until any caught imposter has guessed (see Imposter.tvView). */
    val word: String? = null,
    /** Result onward. */
    val imposters: List<PlayerId> = emptyList(),
    val accused: List<PlayerId> = emptyList(),
    val votes: List<ImposterVote> = emptyList(),
    /** Scores only. */
    val guesses: List<ImposterGuess> = emptyList(),
    val drinks: List<ImposterDrink> = emptyList(),
    val deltas: List<ImposterDelta> = emptyList(),
) : TvGame

@Serializable
data class ImposterClue(val id: PlayerId, val name: String, val text: String?)

@Serializable
data class ImposterVote(val voter: PlayerId, val suspect: PlayerId)

@Serializable
data class ImposterGuess(val id: PlayerId, val name: String, val text: String, val right: Boolean)

/** [text] is the whole drink line. */
@Serializable
data class ImposterDrink(val id: PlayerId, val name: String, val sips: Int, val text: String)

@Serializable
data class ImposterDelta(val id: PlayerId, val name: String, val points: Int)
