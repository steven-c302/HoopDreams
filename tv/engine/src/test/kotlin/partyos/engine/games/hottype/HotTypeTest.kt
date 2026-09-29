package partyos.engine.games.hottype

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.HotTypeTv
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.TvGame
import partyos.engine.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HotTypeTest {
    private val clock = FakeClock(0)
    private val dict = HotTypeDictionary(listOf("stone", "ton", "toe", "one", "tons", "quit", "zzz"))

    // S T R P
    // L O N A
    // H E C I
    // D W K U
    private val tiles = "STRPLONAHECIDWKU".map { it.toString() }
    private val stone = listOf(0, 1, 5, 6, 9)
    private val ton = listOf(1, 5, 6)

    // T O E X / X E X X ...: "toe" can be traced two ways, through either E.
    private val twoE = listOf("T", "O", "E", "X", "X", "E") + List(10) { "X" }
    private val quBoard = listOf("QU", "I", "T", "S") + List(12) { "X" }

    private lateinit var e: PartyEngine
    private var n = 0
    private val json = Json { classDiscriminator = "t"; encodeDefaults = true; explicitNulls = false }

    private fun engineWith(module: HotType) {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(module)))
    }

    /** Starts Hot Type with [count] players and the tutorial skipped, in the ready phase. */
    private fun start(count: Int, board: List<String> = tiles, rounds: Int = 3, settings: Map<String, Int> = emptyMap()): List<PlayerId> {
        engineWith(HotType(dict) { _, _ -> board })
        val ids = (1..count).map { e.add("P$it") }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("hottype", mapOf("rounds" to rounds) + settings)))
        e.host(HostCmd.SkipPhase) // the tutorial
        return ids
    }

    private val tv get() = e.tvState().stage!!.game as HotTypeTv
    private val seq get() = e.tvState().stage!!.phaseSeq
    private fun skip() { e.host(HostCmd.SkipPhase) }
    private fun toHunt() = skip()
    private fun screen(who: PlayerId) = assertIs<Screen.Hunt>(e.phoneState(who).screen)
    private fun word(who: PlayerId, path: List<Int>, round: Int = seq) =
        e.action(who, "a${n++}", round, buildJsonObject { put("kind", JsonPrimitive("word")); put("path", JsonArray(path.map(::JsonPrimitive))) })
    private fun score(who: PlayerId) = e.tvState().scores.first { it.id == who }.score

    @Test fun readyHasNoTilesAnywhereAndHuntShowsThem() {
        val ids = start(2)
        assertEquals("ready", tv.phase)
        assertTrue(tv.tiles.isEmpty())
        assertTrue(screen(ids[0]).tiles.isEmpty())
        toHunt()
        assertEquals("hunt", tv.phase)
        assertEquals(tiles, tv.tiles)
        assertEquals(tiles, screen(ids[0]).tiles)
        assertEquals(4, tv.size)
    }

    @Test fun aValidWordIsShownOnlyToItsFinderAndNeverOnTheTv() {
        val (a, b) = start(2)
        toHunt()
        assertEquals(ActionResult.Ack, word(a, stone))
        assertEquals(listOf("stone"), screen(a).found.map { it.word })
        assertEquals(800, screen(a).found.single().points)
        assertEquals(800, screen(a).score)
        assertTrue(screen(b).found.isEmpty())
        val row = tv.rail.first { it.id == a }
        assertEquals(1, row.count)
        assertEquals(listOf(5), row.lengths)
        assertFalse(json.encodeToString(TvGame.serializer(), tv).contains("stone", ignoreCase = true))
        assertEquals(1, tv.wordsFound)
        assertEquals(5, tv.longest)
    }

    @Test fun rejectsInTheSpecifiedOrder() {
        val (a) = start(2)
        assertEquals(ActionResult.Rejected("NOT_NOW"), word(a, stone)) // still ready
        toHunt()
        assertEquals(ActionResult.Rejected("BAD_PATH"), word(a, listOf(0, 6)))
        assertEquals(ActionResult.Rejected("TOO_SHORT"), word(a, listOf(0, 1)))
        assertEquals(ActionResult.Rejected("NOT_A_WORD"), word(a, listOf(0, 1, 5))) // "sto"
        assertEquals(ActionResult.Ack, word(a, stone))
        assertEquals(ActionResult.Rejected("ALREADY"), word(a, stone))
    }

    @Test fun theSameWordByAnotherPathIsAlreadyFound() {
        val (a) = start(2, board = twoE)
        toHunt()
        assertEquals(ActionResult.Ack, word(a, listOf(0, 1, 2)))
        assertEquals(ActionResult.Rejected("ALREADY"), word(a, listOf(0, 1, 5)))
    }

    @Test fun aQuTileSpellsTwoLetters() {
        val (a) = start(2, board = quBoard)
        toHunt()
        assertEquals(ActionResult.Ack, word(a, listOf(0, 1, 2)))
        val found = screen(a).found.single()
        assertEquals("quit", found.word)
        assertEquals(400, found.points)
    }

    @Test fun aMalformedActionIsBadPathNotACrash() {
        val (a) = start(2)
        toHunt()
        fun raw(build: JsonObjectBuilder.() -> Unit) = e.action(a, "m${n++}", seq, buildJsonObject(build))
        assertEquals(ActionResult.Rejected("BAD_PATH"), raw { put("kind", JsonPrimitive("word")) })
        assertEquals(ActionResult.Rejected("BAD_PATH"), raw { put("kind", JsonPrimitive("word")); put("path", JsonPrimitive("0,1,5")) })
        assertEquals(ActionResult.Rejected("BAD_PATH"), raw { put("kind", JsonPrimitive("word")); put("path", JsonArray(listOf(JsonPrimitive(0.5), JsonPrimitive("x")))) })
        assertEquals(ActionResult.Rejected("BAD_PATH"), raw { put("kind", JsonPrimitive("word")); put("path", JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(1_000_000)))) })
        assertEquals(ActionResult.Rejected("NOT_NOW"), raw { put("kind", JsonPrimitive("guess")) })
    }

    @Test fun nothingIsAcceptedAfterThePressAndAStaleRoundIsRefused() {
        val (a) = start(2)
        toHunt()
        val hunting = seq
        skip() // press
        assertEquals("press", tv.phase)
        assertEquals(ActionResult.Rejected("NOT_NOW"), word(a, stone))
        assertEquals(ActionResult.Rejected("STALE"), word(a, stone, round = hunting))
    }

    @Test fun aLateJoinerWaitsForTheNextRound() {
        start(2)
        toHunt()
        val late = e.add("Late")
        assertEquals(ActionResult.Rejected("NEXT_ROUND"), word(late, stone))
        assertIs<Screen.Waiting>(e.phoneState(late).screen)
    }

    @Test fun theSettleAwardsBaseUniqueAndLongestAndTheRevealShowsThePage() {
        val toe = listOf(1, 5, 9)
        val (a, b) = start(2)
        toHunt()
        word(a, stone); word(a, ton); word(b, ton); word(b, toe)
        skip() // press
        assertEquals(0, score(a)) // nothing is awarded until the press ends
        skip() // reveal
        assertEquals("reveal", tv.phase)
        assertEquals(2200, score(a))
        assertEquals(300, score(b))
        val stoneOnPage = tv.page.single { it.word == "stone" }
        assertEquals(listOf(a), stoneOnPage.finders)
        assertEquals(800, stoneOnPage.bonus)
        assertTrue(stoneOnPage.longest)
        assertEquals("stone", tv.page.last().word)
        val mine = screen(a).found.first { it.word == "ton" }
        assertEquals(2, mine.finders)
        assertEquals(0, mine.bonus)
        assertEquals(2200, screen(a).score)
    }

    @Test fun anEmptyRoundIsFineAndNobodyDrinks() {
        val (a, b) = start(2)
        toHunt()
        skip(); skip() // press, reveal
        assertEquals("reveal", tv.phase)
        assertTrue(tv.page.isEmpty())
        assertEquals("stone", tv.missed?.word) // the best word nobody found
        assertEquals(0, score(a) + score(b))
        skip() // scores
        assertEquals("scores", tv.phase)
        assertTrue(tv.drinks.isEmpty())
        assertTrue(tv.deltas.all { it.total == 0 })
    }

    @Test fun theLowestScorerDrinksWhenThereIsOneAndTheSwitchTurnsItOff() {
        val (a, b, c) = start(3)
        toHunt()
        word(a, stone); word(b, ton)
        skip(); skip(); skip() // press, reveal, scores
        val drink = tv.drinks.single()
        assertEquals(c, drink.id)
        assertTrue(drink.text.contains("Drink 2 sips"), drink.text)

        val off = start(3, settings = mapOf("drinks" to 0))
        toHunt()
        word(off[0], stone); word(off[1], ton)
        skip(); skip(); skip()
        assertTrue(tv.drinks.isEmpty())
    }

    @Test fun theFinalRoundDoublesEverything() {
        val (a) = start(2, rounds = 3)
        repeat(2) {
            toHunt(); skip(); skip(); skip(); skip() // hunt, press, reveal, scores, then the next ready
        }
        toHunt()
        assertTrue(tv.finalRound)
        word(a, ton)
        skip(); skip()
        assertEquals(1400, score(a)) // (100 + 100) * 2 + 500 * 2
    }

    @Test fun aBigFindShowsALengthNeverTheWord() {
        val big = HotTypeDictionary(listOf("planet", "plan"))
        // P L A N
        // X X T E
        val bigTiles = listOf("P", "L", "A", "N", "X", "X", "T", "E") + List(8) { "X" }
        engineWith(HotType(big) { _, _ -> bigTiles })
        val a = e.add("P1"); e.add("P2")
        e.host(HostCmd.StartGame("hottype", mapOf("rounds" to 3))); e.host(HostCmd.SkipPhase)
        toHunt()
        assertNull(tv.bigFind)
        assertEquals(ActionResult.Ack, word(a, listOf(0, 1, 2, 3, 7, 6))) // p-l-a-n-e-t
        val find = assertNotNull(tv.bigFind)
        assertEquals(6, find.letters)
        assertEquals(a, find.id)
        assertFalse(json.encodeToString(TvGame.serializer(), tv).contains("planet", ignoreCase = true))
        assertEquals(ActionResult.Ack, word(a, listOf(0, 1, 2, 3))) // "plan" is short, so the burst stays on the first find
        assertEquals(1, tv.bigFind!!.seq)
    }

    @Test fun stateSurvivesASerialisationRoundTrip() {
        val module = HotType(dict) { _, _ -> tiles }
        val state = HotTypeState(
            phase = HotType.HUNT, round = 2, totalRounds = 3, size = 4, tiles = tiles,
            participants = listOf(PlayerId("p-al")), found = mapOf("p-al" to listOf("stone")),
            bigFind = HotTypeBigFind(1, "p-al", 6), missed = "toe", deltas = mapOf("p-al" to 2200),
        )
        val text = Json.encodeToString(module.stateSerializer, state)
        assertEquals(state, Json.decodeFromString(module.stateSerializer, text))
    }
}
