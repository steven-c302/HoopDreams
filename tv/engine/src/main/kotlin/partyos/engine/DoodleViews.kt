package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("doodle")
data class DoodleTv(
    /** pick | draw | reveal | scores | podium */
    val phase: String,
    val turn: Int,
    val totalTurns: Int,
    val finalTurn: Boolean,
    val drawer: PlayerId? = null,
    val drawerName: String = "",
    /** 1 easy, 2 medium, 3 hard (draw onward). */
    val difficulty: Int = 0,
    /** Draw only: "_ _ _ _ _" with any revealed letters. Never the word. */
    val blanks: String = "",
    /** Players who have it, and how many could. */
    val guessed: Int = 0,
    val expected: Int = 0,
    /** The whole draw time, and the part of it after the current hint stage. */
    val drawMs: Long = 0,
    val tailMs: Long = 0,
    /** Who has it, in order. Points are held back until the reveal. */
    val solvers: List<DoodleSolver> = emptyList(),
    /** The last few wrong guesses, oldest first; [missTotal] counts every wrong guess so far (stable animation keys). */
    val wrong: List<DoodleMissTv> = emptyList(),
    val missTotal: Int = 0,
    /** Reveal and scores only. */
    val word: String? = null,
    val drinks: List<DoodleDrink> = emptyList(),
    /** Scores only. */
    val deltas: List<DoodleDelta> = emptyList(),
    /** Podium only: one entry per finished turn, in order. */
    val gallery: List<DoodleShot> = emptyList(),
) : TvGame

@Serializable
data class DoodleSolver(val id: PlayerId, val name: String, val points: Int? = null)

@Serializable
data class DoodleMissTv(val id: PlayerId, val name: String, val text: String)

/** [text] is the whole drink line. */
@Serializable
data class DoodleDrink(val id: PlayerId, val name: String, val sips: Int, val text: String)

@Serializable
data class DoodleDelta(val id: PlayerId, val name: String, val points: Int)

@Serializable
data class DoodleShot(val turn: Int, val word: String, val drawer: PlayerId, val drawerName: String, val first: PlayerId? = null, val firstName: String? = null)
