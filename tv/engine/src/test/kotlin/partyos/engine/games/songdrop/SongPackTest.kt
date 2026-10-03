package partyos.engine.games.songdrop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SongPackTest {
    private fun song(id: String = "a-b", videoId: String = "abcdefghijk", year: Int = 1999, genre: String = "pop", start: Int = 30) =
        """{"id":"$id","title":"Title","artist":"Artist","year":$year,"genre":"$genre","videoId":"$videoId","startSec":$start}"""

    private fun pack(vararg items: String, game: String = "songdrop") =
        """{"packId":"t","title":"T","game":"$game","version":1,"items":[${items.joinToString(",")}]}"""

    private fun four() = arrayOf(song("a-1", "aaaaaaaaaaa"), song("a-2", "bbbbbbbbbbb"), song("a-3", "ccccccccccc"), song("a-4", "ddddddddddd"))

    @Test fun aWellFormedPackParses() {
        val p = SongPack.parse(pack(*four()))
        assertEquals(4, p.items.size)
        assertEquals(3, p.items.first().era)
    }

    @Test fun theBundledSeedPackLoads() {
        assertEquals(true, SongPack.core().items.size >= 4)
    }

    @Test fun aPackForAnotherGameIsRefused() {
        assertFailsWith<IllegalArgumentException> { SongPack.parse(pack(*four(), game = "doodle")) }
    }

    @Test fun tooFewSongsAreRefused() {
        assertFailsWith<IllegalArgumentException> { SongPack.parse(pack(song("a-1"), song("a-2", "bbbbbbbbbbb"))) }
    }

    @Test fun badFieldsAreRefused() {
        val ok = four().toMutableList()
        fun with(bad: String) = pack(*(ok.drop(1) + bad).toTypedArray())
        assertFailsWith<IllegalArgumentException> { SongPack.parse(with(song("a-9", videoId = "short"))) }
        assertFailsWith<IllegalArgumentException> { SongPack.parse(with(song("Bad Id", "eeeeeeeeeee"))) }
        assertFailsWith<IllegalArgumentException> { SongPack.parse(with(song("a-9", "eeeeeeeeeee", year = 1900))) }
        assertFailsWith<IllegalArgumentException> { SongPack.parse(with(song("a-9", "eeeeeeeeeee", genre = "polka"))) }
        assertFailsWith<IllegalArgumentException> { SongPack.parse(with(song("a-9", "eeeeeeeeeee", start = 5000))) }
    }

    @Test fun theBundledDeckIsBigEnoughAndGivesEveryEraRealChoices() {
        val deck = SongPack.core().items
        assertTrue(deck.size >= 100, "the real deck has at least 100 songs (the 4-song seed is a placeholder)")
        for (era in 1..5) assertTrue(deck.count { it.era == era } >= 15, "era $era has too few songs")
        val random = kotlin.random.Random(5)
        for (song in deck) {
            val options = SongRules.options(song, deck, random)
            assertEquals(4, options.size, "${song.id} needs four options")
            assertTrue(options.count { it.id == song.id } == 1)
        }
    }

    @Test fun duplicateIdsOrVideosAreRefused() {
        assertFailsWith<IllegalArgumentException> { SongPack.parse(pack(*four(), song("a-1", "eeeeeeeeeee"))) }
        assertFailsWith<IllegalArgumentException> { SongPack.parse(pack(*four(), song("a-9", "aaaaaaaaaaa"))) }
    }
}
