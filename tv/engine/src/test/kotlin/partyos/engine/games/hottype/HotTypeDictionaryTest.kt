package partyos.engine.games.hottype

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HotTypeDictionaryTest {
    private val dict = HotTypeDictionary(listOf("stone", "ton", "toe", "Tons", "at", "it's", "zzz", "ton"))

    @Test fun keepsOnlyLowerCaseLettersOfThreeToTwentyFive() {
        assertEquals(5, dict.size) // stone, ton, toe, tons, zzz. "at" is too short, "it's" has a mark, "ton" is a duplicate.
        assertTrue(dict.contains("tons"))
        assertFalse(dict.contains("at"))
        assertFalse(dict.contains("it's"))
    }

    @Test fun containsIsExact() {
        assertTrue(dict.contains("stone"))
        assertFalse(dict.contains("ston"))
        assertFalse(dict.contains("stones"))
    }

    @Test fun aPrefixMatchesAnyWordThatStartsWithIt() {
        assertTrue(dict.hasPrefix("st"))
        assertTrue(dict.hasPrefix("stone"))
        assertTrue(dict.hasPrefix("to"))
        assertFalse(dict.hasPrefix("tx"))
        assertFalse(dict.hasPrefix("stonex"))
    }

    @Test fun theCoreListIsTheRealOne() {
        val core = HotTypeDictionary.core
        assertTrue(core.size in 150_000..200_000, "size ${core.size}")
        assertTrue(core.contains("planets"))
        assertTrue(core.contains("quit"))
        assertFalse(core.contains("zzzzz"))
        assertTrue(core.hasPrefix("plane"))
    }

    @Test fun theBlockedListHasOnlySingleLowerCaseWordsInTheDictionaryFormat() {
        val blocked = HotTypeDictionary.blocked
        assertTrue(blocked.isNotEmpty())
        assertTrue(blocked.all { w -> w.all { it in 'a'..'z' } && w.length in 3..25 })
    }
}
