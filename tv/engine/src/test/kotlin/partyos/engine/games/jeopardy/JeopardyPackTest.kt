package partyos.engine.games.jeopardy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JeopardyPackTest {
    private fun clues(cat: Int) = (0..4).map { JeopardyClue("c$cat-$it", "Clue $cat $it", "Ans${cat}x$it") }
    private fun cats(n: Int) = (0 until n).map { JeopardyCategory("c$it", "Cat $it", clues(it)) }
    private fun finals(n: Int) = (0 until n).map { JeopardyFinal("f$it", "Final $it", "Final clue $it", "Fin$it") }
    private fun pack(categories: List<JeopardyCategory> = cats(12), finals: List<JeopardyFinal> = finals(4), game: String = "jeopardy", version: Int = 2) =
        JeopardyPack("t", "T", game, version, categories, finals)

    @Test fun theShippedPackLoadsAndIsBigEnough() {
        val p = JeopardyPack.core()
        assertTrue(p.categories.size >= 14, "categories: ${p.categories.size}")
        assertTrue(p.categories.all { it.clues.size == 5 })
        assertTrue(p.finals.size >= 8, "finals: ${p.finals.size}")
        val ids = p.categories.flatMap { c -> c.clues.map { it.id } } + p.categories.map { it.id } + p.finals.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun aValidSmallPackPasses() {
        JeopardyPack.validate(pack())
    }

    @Test fun rejectsAPackForAnotherGameOrVersion() {
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(game = "bluff")) }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(version = 1)) }
    }

    @Test fun needsEnoughCategoriesAndFinals() {
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(categories = cats(11))) }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(finals = finals(3))) }
    }

    @Test fun everyCategoryHasExactlyFiveClues() {
        val short = cats(12).toMutableList().also { it[3] = it[3].copy(clues = it[3].clues.take(4)) }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(categories = short)) }
    }

    @Test fun rejectsDuplicateIdsBlankTextAndDuplicateAnswers() {
        val dup = cats(12).toMutableList().also { it[1] = it[1].copy(id = "c0") }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(categories = dup)) }
        val blank = cats(12).toMutableList().also { c -> c[2] = c[2].copy(clues = c[2].clues.mapIndexed { i, x -> if (i == 0) x.copy(clue = " ") else x }) }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(categories = blank)) }
        val sameAnswer = cats(12).toMutableList().also { c -> c[4] = c[4].copy(clues = c[4].clues.mapIndexed { i, x -> if (i == 1) x.copy(answer = "Ans4x0!") else x }) }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(categories = sameAnswer)) }
        val blankFinal = finals(4).toMutableList().also { it[0] = it[0].copy(answer = "") }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(finals = blankFinal)) }
    }
}
