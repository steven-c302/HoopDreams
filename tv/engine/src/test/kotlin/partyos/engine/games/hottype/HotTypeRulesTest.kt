package partyos.engine.games.hottype

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HotTypeRulesTest {
    @Test fun pointsByLength() {
        assertEquals(listOf(0, 0, 100, 400, 800, 1400, 1800, 2200, 2200), (1..9).map { HotTypeRules.points(it) })
    }

    @Test fun everyFinderEarnsBaseAndAWordOnlyOnePlayerFoundEarnsAnEqualBonus() {
        val r = HotTypeRules.score(mapOf("a" to listOf("stone", "ton"), "b" to listOf("ton", "toe")), 1)
        assertEquals(HotTypeRules.Parts(base = 900, unique = 800, longest = 500), r.parts.getValue("a"))
        assertEquals(HotTypeRules.Parts(base = 200, unique = 100, longest = 0), r.parts.getValue("b"))
        assertEquals(2200, r.parts.getValue("a").total)
        assertEquals(300, r.parts.getValue("b").total)
    }

    @Test fun aSharedWordEarnsNoBonusAndListsBothFinders() {
        val r = HotTypeRules.score(mapOf("a" to listOf("ton"), "b" to listOf("ton")), 1)
        val ton = r.words.single()
        assertEquals(listOf("a", "b"), ton.finders)
        assertEquals(0, ton.bonus)
    }

    @Test fun tiedLongestWordsAllEarnTheBonusButOnlyOncePerPlayer() {
        val r = HotTypeRules.score(mapOf("a" to listOf("stone", "tones"), "b" to listOf("stone"), "c" to emptyList()), 1)
        assertEquals(500, r.parts.getValue("a").longest)
        assertEquals(500, r.parts.getValue("b").longest)
        assertEquals(0, r.parts.getValue("c").longest)
    }

    @Test fun theFinalRoundDoublesEveryPart() {
        val r = HotTypeRules.score(mapOf("a" to listOf("ton"), "b" to emptyList()), 2)
        assertEquals(HotTypeRules.Parts(base = 200, unique = 200, longest = 1000), r.parts.getValue("a"))
        assertEquals(1400, r.parts.getValue("a").total)
    }

    @Test fun aWordAPlayerListsTwiceCountsOnce() {
        val r = HotTypeRules.score(mapOf("a" to listOf("ton", "ton")), 1)
        assertEquals(100, r.parts.getValue("a").base)
    }

    @Test fun aRoundWithNoWordsScoresZeroForEveryone() {
        val r = HotTypeRules.score(mapOf("a" to emptyList(), "b" to emptyList()), 1)
        assertTrue(r.words.isEmpty())
        assertEquals(0, r.parts.getValue("a").total)
        assertEquals(0, r.parts.getValue("b").total)
        assertTrue(HotTypeRules.frontPage(r).isEmpty())
    }

    @Test fun theFrontPageKeepsTheLongestAndIsStampedLongestLast() {
        val found = mapOf(
            "a" to listOf("planets", "plane", "lane", "ant", "tan", "pan", "nap", "lap", "pal", "sap"),
            "b" to listOf("plane", "lane", "ant", "tan", "pan", "nap", "lap", "pal", "sap"),
        )
        val page = HotTypeRules.frontPage(HotTypeRules.score(found, 1), limit = 4)
        assertEquals(4, page.size)
        assertEquals("planets", page.last().word) // the longest lands last
        assertTrue(page.last().longest)
        val rest = page.dropLast(1).map { it.base }
        assertEquals(rest.sorted(), rest) // the others rise in points
    }

    @Test fun uniqueWordsBeatSharedOnesWhenThePageIsFull() {
        val found = mapOf("a" to listOf("stone", "ton", "toe"), "b" to listOf("ton", "one"))
        val page = HotTypeRules.frontPage(HotTypeRules.score(found, 1), limit = 3)
        assertEquals(setOf("stone", "toe", "one"), page.map { it.word }.toSet()) // ton is shared and drops out
    }
}
