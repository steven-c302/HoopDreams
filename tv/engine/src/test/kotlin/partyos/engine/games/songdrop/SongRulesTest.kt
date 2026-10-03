package partyos.engine.games.songdrop

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongRulesTest {
    private fun song(i: Int, title: String = "Title $i", artist: String = "Artist $i", year: Int = 1990, genre: String = "pop") =
        Song("s$i", title, artist, year, genre, "vid" + i.toString().padStart(8, '0'), 30)

    @Test fun erasSplitTheYears() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5), listOf(1969, 1979, 1980, 1989, 1990, 1999, 2000, 2009, 2010, 2024).map(SongRules::eraOf))
    }

    @Test fun pointsFallByStageAndRiseWithSpeed() {
        assertEquals(1300, SongRules.points(0, 2_000, 2_000, 1))
        assertEquals(1000, SongRules.points(0, 0, 2_000, 1))
        assertEquals(1225, SongRules.points(0, 1_500, 2_000, 1))
        assertEquals(1000, SongRules.points(1, 4_000, 4_000, 1))
        assertEquals(450, SongRules.points(2, 0, 8_000, 1))
        assertEquals(750, SongRules.points(2, 8_000, 8_000, 1))
        assertEquals(250, SongRules.points(3, 0, 15_000, 1))
        assertEquals(2600, SongRules.points(0, 2_000, 2_000, 2))
    }

    @Test fun speedBonusStaysInRangeEvenWithOddClocks() {
        assertEquals(1300, SongRules.points(0, 9_999, 2_000, 1))
        assertEquals(1000, SongRules.points(0, -50, 2_000, 1))
    }

    @Test fun optionsAreTheAnswerPlusThreeDecoysAllDifferent() {
        val pool = (1..20).map { song(it) }
        val opts = SongRules.options(pool[0], pool, Random(1))
        assertEquals(4, opts.size)
        assertTrue(pool[0] in opts)
        assertEquals(4, opts.map { it.id }.toSet().size)
    }

    @Test fun decoysPreferTheSameEraAndGenre() {
        val answer = song(0, year = 1985, genre = "rock")
        val same = (1..5).map { song(it, year = 1982, genre = "rock") }
        val other = (6..30).map { song(it, year = 2015, genre = "pop") }
        repeat(20) { seed ->
            val opts = SongRules.options(answer, listOf(answer) + same + other, Random(seed))
            assertTrue(opts.filter { it != answer }.all { it in same }, "seed $seed took a decoy from another era")
        }
    }

    @Test fun neverShowsTheAnswersTitleOrArtistOnAnotherOption() {
        val answer = song(0, title = "Hurt", artist = "Nine Inch Nails")
        val coverSameTitle = song(1, title = "Hurt!", artist = "Johnny Cash")
        val sameArtist = song(2, title = "Closer", artist = "NINE INCH NAILS")
        val others = (3..12).map { song(it) }
        repeat(30) { seed ->
            val opts = SongRules.options(answer, listOf(answer, coverSameTitle, sameArtist) + others, Random(seed))
            assertTrue(coverSameTitle !in opts && sameArtist !in opts, "seed $seed showed a confusable option")
        }
    }

    @Test fun aSmallPoolStillGivesWhatItCanWithoutRepeating() {
        val pool = listOf(song(0), song(1), song(2))
        val opts = SongRules.options(pool[0], pool, Random(3))
        assertEquals(3, opts.size)
        assertEquals(3, opts.map { it.id }.toSet().size)
    }
}
