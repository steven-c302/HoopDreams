package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("songdrop")
data class SongDropTv(
    /** load | stage | reveal | podium | dead */
    val phase: String,
    val song: Int,
    val totalSongs: Int,
    val finalSong: Boolean,
    /** Counts up for every clip the TV has to start; the TV echoes it back in `ready:`, `bad:` and `replay:`. */
    val clipSeq: Int,
    /** Load, stage and reveal only: what to play and where the hook starts. */
    val videoId: String = "",
    val startSec: Int = 0,
    val stage: Int = 0,
    val stages: Int = 4,
    val clipMs: Long = 0,
    val answered: Int = 0,
    val expected: Int = 0,
    /** Who has it, in order. Points are held back until the reveal. */
    val solvers: List<SongSolver> = emptyList(),
    val lockedOut: List<PlayerId> = emptyList(),
    /** Reveal only. */
    val card: SongCard? = null,
    val drinks: List<SongDrink> = emptyList(),
    /** Podium only: every song played, in order. */
    val gallery: List<SongCard> = emptyList(),
) : TvGame

@Serializable
data class SongSolver(val id: PlayerId, val name: String, val points: Int? = null)

@Serializable
data class SongCard(val title: String, val artist: String, val year: Int, val videoId: String)

/** [text] is the whole drink line. */
@Serializable
data class SongDrink(val id: PlayerId, val name: String, val sips: Int, val text: String)
