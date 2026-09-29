package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("bluff")
data class BluffTv(
    /** write | pick | reveal | scores | podium */
    val phase: String,
    val round: Int,
    val totalRounds: Int,
    val finalRound: Boolean,
    val prompt: String,
    val submitted: Int,
    val expected: Int,
    /** Pick phase: anonymous option texts in display order. */
    val options: List<String> = emptyList(),
    /** Reveal onward: fakes/decoys ascending by players fooled, truth last. */
    val reveal: List<BluffReveal> = emptyList(),
    val deltas: List<BluffDelta> = emptyList(),
) : TvGame

@Serializable
data class BluffReveal(val text: String, val kind: String, val authors: List<String>, val fooled: List<String>)

@Serializable
data class BluffDelta(val id: PlayerId, val name: String, val points: Int)
