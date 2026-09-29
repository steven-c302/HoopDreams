package partyos.engine.games.doodle

import partyos.engine.games.bluff.normalise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DoodlePackTest {
    private val ok = DoodleCategory("a", "A", listOf("giraffe"), listOf("kangaroo"), listOf("platypus"))
    private fun pack(vararg items: DoodleCategory, game: String = "doodle") = DoodlePack("t", "T", game, 1, items.toList())

    @Test fun theCorePackParsesAndIsBigEnough() {
        val words = DoodlePack.core().words()
        assertTrue(words.count { it.difficulty == 1 } >= 60, "easy")
        assertTrue(words.count { it.difficulty == 2 } >= 60, "medium")
        assertTrue(words.count { it.difficulty == 3 } >= 40, "hard")
        DoodlePack.core().items.forEach { c ->
            assertTrue(c.easy.size + c.medium.size + c.hard.size >= 40, "${c.id} has too few words")
        }
        assertEquals(words.size, words.map { it.id }.toSet().size)
        assertEquals(words.size, words.map { normalise(it.word) }.toSet().size)
    }

    @Test fun wordIdsNameTheirCategory() {
        val w = pack(ok).words()
        assertEquals(listOf("a:giraffe", "a:kangaroo", "a:platypus"), w.map { it.id })
        assertEquals(listOf(1, 2, 3), w.map { it.difficulty })
    }

    @Test fun validationRejectsBadPacks() {
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok, game = "trivia")) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack()) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok, ok)) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok.copy(easy = listOf("  ")))) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok.copy(easy = listOf("R2D2")))) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok.copy(easy = listOf("a")))) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok.copy(easy = listOf("one two three four")))) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok.copy(easy = listOf("Giraffe"), medium = listOf("giraffe")))) }
        DoodlePack.validate(pack(ok, ok.copy(id = "b", easy = listOf("ice cream"), medium = listOf("rainbow"), hard = listOf("sloth"))))
    }
}
