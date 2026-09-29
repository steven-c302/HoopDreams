package partyos.engine.games.imposter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImposterPackTest {
    private fun cat(id: String, vararg w: String) = ImposterCategory(id, id.replaceFirstChar(Char::uppercase), w.toList())
    private val eight = arrayOf("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8")
    private fun pack(vararg c: ImposterCategory, game: String = "imposter") = ImposterPack("t", "T", game, 1, c.toList())

    @Test fun corePackLoadsAndIsBigEnough() {
        val p = ImposterPack.core()
        assertTrue(p.items.size >= 30, "categories: ${p.items.size}")
        assertTrue(p.items.all { it.words.size >= 10 })
        assertEquals(p.words().size, p.words().map { it.id }.toSet().size)
    }

    @Test fun wordIdsCombineTheCategoryAndTheWord() {
        val w = ImposterPack.validate(pack(cat("food", *eight))).words().first()
        assertEquals("food:a1", w.id)
        assertEquals("Food", w.category)
        assertEquals("a1", w.word)
    }

    @Test fun rejectsAPackForAnotherGame() {
        assertFailsWith<IllegalArgumentException> { ImposterPack.validate(pack(cat("food", *eight), game = "bluff")) }
    }

    @Test fun rejectsAnEmptyPack() {
        assertFailsWith<IllegalArgumentException> { ImposterPack.validate(pack()) }
    }

    @Test fun rejectsDuplicateCategoryIds() {
        assertFailsWith<IllegalArgumentException> { ImposterPack.validate(pack(cat("food", *eight), cat("food", *eight))) }
    }

    @Test fun rejectsACategoryWithTooFewWords() {
        assertFailsWith<IllegalArgumentException> { ImposterPack.validate(pack(cat("food", "a1", "a2", "a3", "a4", "a5", "a6", "a7"))) }
    }

    @Test fun rejectsBlankAndDuplicateWords() {
        assertFailsWith<IllegalArgumentException> { ImposterPack.validate(pack(cat("food", "", "a2", "a3", "a4", "a5", "a6", "a7", "a8"))) }
        assertFailsWith<IllegalArgumentException> { ImposterPack.validate(pack(cat("food", "a1", "A1!", "a3", "a4", "a5", "a6", "a7", "a8"))) }
    }
}
