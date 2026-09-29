package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Hot Type on the TV. Nothing here holds a found word or a path before `reveal`: the rail carries counts and word
 * lengths only, and the big-find burst carries a length only.
 */
@Serializable @SerialName("hottype")
data class HotTypeTv(
    /** ready | hunt | press | reveal | scores | podium */
    val phase: String,
    val round: Int,
    val totalRounds: Int,
    val finalRound: Boolean,
    val size: Int,
    /** From `hunt` onward (empty in `ready`). */
    val tiles: List<String> = emptyList(),
    /** Hunt and press: one row per player, in join order. */
    val rail: List<HuntRail> = emptyList(),
    /** Hunt and press: distinct words found so far and the longest length. */
    val wordsFound: Int = 0,
    val longest: Int = 0,
    val bigFind: HuntBigFind? = null,
    /** Reveal onward: the front page, in stamping order (rising points, longest last). */
    val page: List<HuntPageWord> = emptyList(),
    val missed: HuntMissed? = null,
    /** Scores only. */
    val deltas: List<HuntDelta> = emptyList(),
    val drinks: List<HuntDrink> = emptyList(),
) : TvGame

@Serializable
data class HuntRail(val id: PlayerId, val name: String, val count: Int, val score: Int, val lengths: List<Int>)

/** The latest word of 6 or more letters. [seq] rises with each one so the TV animates each once. Length only. */
@Serializable
data class HuntBigFind(val seq: Int, val id: PlayerId, val name: String, val letters: Int)

@Serializable
data class HuntPageWord(val word: String, val points: Int, val bonus: Int, val finders: List<PlayerId>, val longest: Boolean)

@Serializable
data class HuntMissed(val word: String, val points: Int)

@Serializable
data class HuntDelta(val id: PlayerId, val name: String, val base: Int, val unique: Int, val longest: Int, val total: Int)

/** [text] is the whole drink line, already worded for water. */
@Serializable
data class HuntDrink(val id: PlayerId, val name: String, val sips: Int, val text: String)
