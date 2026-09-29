package partyos.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImposterViewsTest {
    private val json = Json { classDiscriminator = "t"; encodeDefaults = true; explicitNulls = false }

    @Test fun secretScreenKeepsItsDiscriminatorAndRoundTrips() {
        val screen: Screen = Screen.Secret(
            "Round 1", "IMPOSTER", "Food", "imposter", null, null, false,
            SecretInput("One word that fits Food", 20, "cheesy", "clue", "One word only"),
        )
        val text = json.encodeToString(Screen.serializer(), screen)
        assertTrue(text.contains("\"t\":\"secret\""), text)
        assertEquals(screen, json.decodeFromString(Screen.serializer(), text))
    }

    @Test fun imposterTvKeepsItsDiscriminatorAndRoundTrips() {
        val al = PlayerId("p-al")
        val tv: TvGame = ImposterTv(
            phase = "result", round = 2, totalRounds = 5, finalRound = false, category = "Food",
            submitted = 4, expected = 4, imposterCount = 1,
            clues = listOf(ImposterClue(al, "Al", "cheesy"), ImposterClue(PlayerId("p-bo"), "Bo", null)),
            word = "pizza", imposters = listOf(al), accused = listOf(al),
            votes = listOf(ImposterVote(PlayerId("p-bo"), al)),
            guesses = listOf(ImposterGuess(al, "Al", "pizzza", true)),
            drinks = listOf(ImposterDrink(al, "Al", 2, "Caught! Drink 2 sips")),
            deltas = listOf(ImposterDelta(al, "Al", 1000)),
        )
        val text = json.encodeToString(TvGame.serializer(), tv)
        assertTrue(text.contains("\"t\":\"imposter\""), text)
        assertEquals(tv, json.decodeFromString(TvGame.serializer(), text))
    }
}
