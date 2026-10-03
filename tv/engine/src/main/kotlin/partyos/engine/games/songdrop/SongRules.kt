package partyos.engine.games.songdrop

import partyos.engine.games.bluff.normalise
import kotlin.random.Random

object SongRules {
    /** How long the clip plays at each stage. A song that nobody names after the last stage is revealed. */
    val CLIP_MS = listOf(2_000L, 4_000L, 8_000L, 15_000L)
    val STAGE_POINTS = listOf(1000, 700, 450, 250)
    const val SPEED_BONUS = 300
    private const val DECOYS = 3

    /** 1 = 60s and 70s, 2 = 80s, 3 = 90s, 4 = 00s, 5 = 10s and 20s. */
    fun eraOf(year: Int): Int = when {
        year <= 1979 -> 1
        year <= 1989 -> 2
        year <= 1999 -> 3
        year <= 2009 -> 4
        else -> 5
    }

    /** A correct answer: the stage's base points plus up to [SPEED_BONUS] for how much of the clip was left. */
    fun points(stage: Int, leftMs: Long, clipMs: Long, multiplier: Int): Int {
        val fraction = (leftMs.toDouble() / clipMs).coerceIn(0.0, 1.0)
        return (STAGE_POINTS[stage.coerceIn(0, STAGE_POINTS.lastIndex)] + (SPEED_BONUS * fraction).toInt()) * multiplier
    }

    /**
     * The answer plus up to three decoys, shuffled. Decoys come from the same era and genre when they can, then the same era,
     * then anywhere, and never share a title or an artist with the answer (or with each other while the pool allows), so the
     * options are always visibly different songs.
     */
    fun options(answer: Song, pool: List<Song>, random: Random): List<Song> {
        fun clash(a: Song, b: Song) = normalise(a.title) == normalise(b.title) || normalise(a.artist) == normalise(b.artist)
        val candidates = pool.filter { it.id != answer.id && !clash(it, answer) }
        val tiers = listOf(
            candidates.filter { it.era == answer.era && it.genre == answer.genre },
            candidates.filter { it.era == answer.era },
            candidates,
        )
        val picked = mutableListOf<Song>()
        for (tier in tiers) for (c in tier.shuffled(random)) {
            if (picked.size >= DECOYS) break
            if (picked.none { clash(it, c) }) picked += c
        }
        // A pool too small to keep decoys apart from each other: fill up with any other song that isn't the answer's twin.
        for (c in candidates.shuffled(random)) {
            if (picked.size >= DECOYS) break
            if (picked.none { it.id == c.id }) picked += c
        }
        return (picked + answer).shuffled(random)
    }
}
