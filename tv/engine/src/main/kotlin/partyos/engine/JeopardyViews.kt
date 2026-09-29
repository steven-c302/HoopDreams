package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JeopardyCellTv(val id: String, val category: String, val value: Int, val used: Boolean)

@Serializable @SerialName("jeopardy")
data class JeopardyTv(
    /** select | answer | reveal | podium */
    val phase: String,
    val board: List<JeopardyCellTv>,
    val category: String? = null,
    val value: Int? = null,
    val clue: String? = null,
    val submitted: Int = 0,
    val expected: Int = 0,
    val answer: String? = null,
    /** Reveal only: names of everyone who got it. */
    val correct: List<String> = emptyList(),
    val deltas: List<JeopardyDelta> = emptyList(),
) : TvGame

@Serializable
data class JeopardyDelta(val id: PlayerId, val name: String, val points: Int)
