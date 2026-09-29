package partyos.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HotTypeViewsTest {
    private val json = Json { classDiscriminator = "t"; encodeDefaults = true; explicitNulls = false }

    @Test fun huntScreenKeepsItsDiscriminatorAndRoundTrips() {
        val screen: Screen = Screen.Hunt(
            phase = "reveal", round = 2, totalRounds = 3, size = 4,
            tiles = "STRPLONAHECIDWKU".map { it.toString() },
            found = listOf(HuntFound("stone", 800, finders = 1, bonus = 800), HuntFound("ton", 100, finders = 2)),
            score = 1700, note = null,
        )
        val text = json.encodeToString(Screen.serializer(), screen)
        assertTrue(text.contains("\"t\":\"hunt\""), text)
        assertEquals(screen, json.decodeFromString(Screen.serializer(), text))
    }

    @Test fun hotTypeTvKeepsItsDiscriminatorAndRoundTrips() {
        val al = PlayerId("p-al")
        val tv: TvGame = HotTypeTv(
            phase = "scores", round = 2, totalRounds = 3, finalRound = false, size = 4,
            tiles = "STRPLONAHECIDWKU".map { it.toString() },
            rail = listOf(HuntRail(al, "Al", 2, 900, listOf(5, 3))),
            wordsFound = 4, longest = 5,
            bigFind = HuntBigFind(1, al, "Al", 7),
            page = listOf(HuntPageWord("stone", 800, 800, listOf(al), true)),
            missed = HuntMissed("clappers", 2200),
            deltas = listOf(HuntDelta(al, "Al", 900, 800, 500, 2200)),
            drinks = listOf(HuntDrink(PlayerId("p-bo"), "Bo", 2, "Last place! Drink 2 sips")),
        )
        val text = json.encodeToString(TvGame.serializer(), tv)
        assertTrue(text.contains("\"t\":\"hottype\""), text)
        assertEquals(tv, json.decodeFromString(TvGame.serializer(), text))
    }
}
