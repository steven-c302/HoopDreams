package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One square on the TV board. A hidden Daily Double looks like any other square. */
@Serializable
data class JeopardyCellTv(val id: String, val col: Int, val row: Int, val value: Int, val used: Boolean)

@Serializable
data class JeopardyDelta(val id: PlayerId, val name: String, val points: Int)

/** [text] is the whole drink line, already worded for water. */
@Serializable
data class JeopardyDrink(val id: PlayerId, val name: String, val sips: Int, val text: String)

/** One player's Final Jeopardy result, revealed lowest score first. [total] is their score after this step. */
@Serializable
data class JeopardyFinalStep(val id: PlayerId, val name: String, val answer: String?, val wager: Int, val right: Boolean, val delta: Int, val total: Int)

@Serializable
data class JeopardyFinalTv(
    val category: String,
    /** Shown once the wagers are in. */
    val clue: String? = null,
    /** Shown from the first reveal step. */
    val answer: String? = null,
    val wagers: Int = 0,
    val expected: Int = 0,
    val steps: List<JeopardyFinalStep> = emptyList(),
)

@Serializable @SerialName("jeopardy")
data class JeopardyTv(
    /** intro | pick | wager | clue | buzz | answer | reveal | break | final_category | final_wager | final_answer | final_reveal | podium */
    val phase: String,
    /** 1 or 2 for a board, 3 for Final Jeopardy. */
    val round: Int,
    /** 1 (Short) or 2 (Full). */
    val boards: Int,
    val categories: List<String> = emptyList(),
    val cells: List<JeopardyCellTv> = emptyList(),
    val controller: PlayerId? = null,
    val category: String? = null,
    val value: Int? = null,
    val clue: String? = null,
    val dailyDouble: Boolean = false,
    /** A Daily Double's wager, shown once the picker has committed to it. */
    val wager: Int? = null,
    val buzzOpen: Boolean = false,
    val floor: PlayerId? = null,
    val locked: List<PlayerId> = emptyList(),
    val tried: List<PlayerId> = emptyList(),
    val answer: String? = null,
    val right: Boolean? = null,
    val deltas: List<JeopardyDelta> = emptyList(),
    val drinks: List<JeopardyDrink> = emptyList(),
    val final: JeopardyFinalTv? = null,
) : TvGame
