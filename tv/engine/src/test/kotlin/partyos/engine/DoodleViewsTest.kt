package partyos.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoodleViewsTest {
    private val json = Json { classDiscriminator = "t"; encodeDefaults = true; explicitNulls = false }

    @Test fun drawScreenKeepsItsDiscriminatorAndRoundTrips() {
        val screen: Screen = Screen.Draw("pizza", 2, 1, 4, 45_000, "Draw it!")
        val text = json.encodeToString(Screen.serializer(), screen)
        assertTrue(text.contains("\"t\":\"draw\""), text)
        assertEquals(screen, json.decodeFromString(Screen.serializer(), text))
    }

    @Test fun guessScreenKeepsItsDiscriminatorAndRoundTrips() {
        val screen: Screen = Screen.Guess("Al", "_ _ _ _ _", "guess", solved = false, close = true, last = "pizzq", guessed = 1, expected = 4, tailMs = 22_500)
        val text = json.encodeToString(Screen.serializer(), screen)
        assertTrue(text.contains("\"t\":\"guess\""), text)
        assertEquals(screen, json.decodeFromString(Screen.serializer(), text))
    }

    @Test fun doodleTvKeepsItsDiscriminatorAndRoundTrips() {
        val al = PlayerId("p-al")
        val tv: TvGame = DoodleTv(
            phase = "reveal", turn = 2, totalTurns = 5, finalTurn = false, drawer = al, drawerName = "Al", difficulty = 2,
            blanks = "", guessed = 2, expected = 3, drawMs = 75_000, tailMs = 0,
            solvers = listOf(DoodleSolver(PlayerId("p-bo"), "Bo", 1000)),
            wrong = listOf(DoodleMissTv(PlayerId("p-cy"), "Cy", "pizzq")), missTotal = 9,
            word = "pizza",
            drinks = listOf(DoodleDrink(PlayerId("p-cy"), "Cy", 1, "Missed it. Drink 1 sip")),
            deltas = listOf(DoodleDelta(al, "Al", 1000)),
            gallery = listOf(DoodleShot(1, "pizza", al, "Al", PlayerId("p-bo"), "Bo")),
        )
        val text = json.encodeToString(TvGame.serializer(), tv)
        assertTrue(text.contains("\"t\":\"doodle\""), text)
        assertEquals(tv, json.decodeFromString(TvGame.serializer(), text))
    }
}
