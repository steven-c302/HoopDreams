package partyos.engine.games.doodle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.DoodleTv
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PhoneState
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.TvState
import partyos.engine.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoodleTest {
    private val clock = FakeClock(0)
    private val pack = DoodlePack(
        "test", "Test", "doodle", 1,
        listOf(
            DoodleCategory("animals", "Animal", listOf("giraffe", "penguin", "octopus"), listOf("kangaroo", "dolphin", "hedgehog"), listOf("flamingo", "chameleon", "platypus")),
            DoodleCategory("things", "Object", listOf("umbrella", "backpack", "telescope"), listOf("skateboard", "suitcase", "sunglasses"), listOf("microscope", "chandelier", "kaleidoscope")),
        ),
    )
    private val json = Json { classDiscriminator = "t"; encodeDefaults = true; explicitNulls = false }

    private lateinit var e: PartyEngine
    private var n = 0

    /** Starts Doodle Dash with [count] players, the tutorial skipped, in the pick phase of turn 1. */
    private fun start(count: Int, turns: Int = 3, settings: Map<String, Int> = emptyMap()): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Doodle(pack))))
        val ids = (1..count).map { e.add("P$it") }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("doodle", mapOf("rounds" to turns) + settings)))
        e.host(HostCmd.SkipPhase)
        return ids
    }

    private val tv get() = e.tvState().stage!!.game as DoodleTv
    private val seq get() = e.tvState().stage!!.phaseSeq
    private fun act(who: PlayerId, kind: String, vararg kv: Pair<String, String>) =
        e.action(who, "a${n++}", seq, buildJsonObject { put("kind", JsonPrimitive(kind)); kv.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
    private fun skip() { e.host(HostCmd.SkipPhase) }
    private fun score(id: PlayerId) = e.tvState().scores.first { it.id == id }.score
    private fun options(drawer: PlayerId) = assertIs<Screen.ChoiceList>(e.phoneState(drawer).screen).options

    /** The drawer picks the [level] word ("Easy", "Medium" or "Hard") and the draw begins; returns the drawer and the word. */
    private fun startDrawing(level: String = "Medium"): Pair<PlayerId, String> {
        val drawer = tv.drawer!!
        val o = options(drawer).first { it.detail == level }
        assertEquals(ActionResult.Ack, act(drawer, "pick", "option" to o.id))
        assertEquals("draw", tv.phase)
        return drawer to assertIs<Screen.Draw>(e.phoneState(drawer).screen).word
    }
    private fun guessers(ids: List<PlayerId>, drawer: PlayerId) = ids - drawer
    private fun guessScreen(who: PlayerId) = assertIs<Screen.Guess>(e.phoneState(who).screen)
    private fun toTurn(k: Int) { while (!(tv.turn == k && tv.phase == "pick")) skip() }

    // ---- pick -----------------------------------------------------------------------------------

    @Test fun startsInPickWithThreeWordsForTheDrawerAndNothingSecretOnTheTv() {
        val ids = start(4)
        val drawer = tv.drawer!!
        assertEquals("pick", tv.phase)
        assertEquals(1, tv.turn)
        assertEquals(3, tv.totalTurns)
        assertNull(tv.word)
        assertEquals("", tv.blanks)
        val opts = options(drawer)
        assertEquals(listOf("Easy", "Medium", "Hard"), opts.map { it.detail })
        ids.filter { it != drawer }.forEach { assertIs<Screen.Waiting>(e.phoneState(it).screen) }
    }

    @Test fun onlyTheDrawerPicksAndOnlyOfferedWords() {
        val ids = start(4)
        val drawer = tv.drawer!!
        val other = ids.first { it != drawer }
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(other, "pick", "option" to options(drawer).first().id))
        assertEquals(ActionResult.Rejected("BAD_OPTION"), act(drawer, "pick", "option" to "nope:word"))
        assertEquals("pick", tv.phase)
    }

    @Test fun anIdlePickerGetsTheMediumWord() {
        start(4)
        val drawer = tv.drawer!!
        val medium = options(drawer).first { it.detail == "Medium" }
        skip()
        assertEquals("draw", tv.phase)
        assertEquals(medium.text, assertIs<Screen.Draw>(e.phoneState(drawer).screen).word)
    }

    @Test fun theDefaultIsFiveTurns() {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Doodle(pack))))
        repeat(3) { e.add("P$it") }
        e.host(HostCmd.StartGame("doodle")); e.host(HostCmd.SkipPhase)
        assertEquals(5, tv.totalTurns)
    }

    // ---- secrecy --------------------------------------------------------------------------------

    @Test fun theWordIsOnlyOnTheDrawersPhoneUntilTheReveal() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        assertFalse(json.encodeToString(TvState.serializer(), e.tvState()).contains(word), "TV leaked the word")
        guessers(ids, drawer).forEach { g ->
            assertFalse(json.encodeToString(PhoneState.serializer(), e.phoneState(g)).contains(word), "a guesser's view leaked the word")
            assertEquals(word.count { it.isLetter() }, guessScreen(g).blanks.count { it == '_' })
            assertTrue(guessScreen(g).blanks.none { it.isLetter() }, "no letter before a hint")
        }
    }

    @Test fun hintLettersAppearOnGuessersAndTheTvButNeverTheWholeWord() {
        val ids = start(4)
        val (drawer, word) = startDrawing("Hard")
        val g = guessers(ids, drawer).first()
        skip() // stage 1: first hint
        assertEquals(1, guessScreen(g).blanks.count { it.isLetter() })
        assertEquals(guessScreen(g).blanks, tv.blanks)
        skip() // stage 2: second hint
        assertEquals(2, guessScreen(g).blanks.count { it.isLetter() })
        assertFalse(json.encodeToString(TvState.serializer(), e.tvState()).contains(word))
        assertEquals("draw", tv.phase)
    }

    // ---- guessing -------------------------------------------------------------------------------

    @Test fun aCorrectGuessScoresOnceAndLocksThePlayerOut() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        val g = guessers(ids, drawer).first()
        assertEquals(ActionResult.Ack, act(g, "guess", "text" to "  ${word.uppercase()} "))
        assertEquals(1000, score(g)) // medium word, first to guess, full time left
        assertTrue(guessScreen(g).solved)
        assertEquals(1000, guessScreen(g).points)
        assertEquals(ActionResult.Rejected("NOT_GUESSING"), act(g, "guess", "text" to word))
        assertEquals(1, tv.guessed)
        assertEquals(listOf(g), tv.solvers.map { it.id })
        assertNull(tv.solvers.first().points) // held back until the reveal
    }

    @Test fun laterGuessersGetLessAndTheDrawerIsPaidWhenEveryoneHasIt() {
        val ids = start(3)
        val (drawer, word) = startDrawing()
        val (a, b) = guessers(ids, drawer)
        act(a, "guess", "text" to word)
        clock.advance(15_000)
        act(b, "guess", "text" to word)
        assertEquals(1000, score(a))
        assertEquals(748, score(b)) // (400 + 600 * 0.8) * 0.85
        assertEquals("reveal", tv.phase) // everyone has it: the draw ends early
        assertEquals(1000, score(drawer)) // (250 * 2 + 500 bonus)
        assertEquals(word, tv.word)
        assertEquals(listOf(1000, 748), tv.solvers.map { it.points })
    }

    @Test fun theDrawerCannotGuessAndBadTextIsRefused() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        val g = guessers(ids, drawer).first()
        assertEquals(ActionResult.Rejected("NOT_GUESSING"), act(drawer, "guess", "text" to word))
        assertEquals(ActionResult.Rejected("BAD_TEXT"), act(g, "guess", "text" to "   "))
        assertEquals(ActionResult.Rejected("BAD_TEXT"), act(g, "guess", "text" to "x".repeat(41)))
        assertFalse(guessScreen(g).solved)
    }

    @Test fun aWrongGuessBecomesABubbleOnTheTvButANearMissIsPrivate() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        val g = guessers(ids, drawer).first()
        val close = word.first() + "qqq" + word.drop(4) // three letters wrong: not accepted, but near
        assertEquals(ActionResult.Ack, act(g, "guess", "text" to close))
        assertFalse(guessScreen(g).solved)
        assertTrue(guessScreen(g).close)
        assertEquals(close, guessScreen(g).last)
        assertEquals(listOf(close), tv.wrong.map { it.text })
        assertEquals(1, tv.missTotal)
        act(g, "guess", "text" to "zzzzzzzz")
        assertFalse(guessScreen(g).close)
        assertEquals(2, tv.missTotal)
    }

    @Test fun aGuessThatContainsTheWordCountsAndNeverLeaksAsABubble() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        val (a, b) = guessers(ids, drawer)
        assertEquals(ActionResult.Ack, act(a, "guess", "text" to "is it a $word"))
        assertTrue(guessScreen(a).solved)
        assertEquals(ActionResult.Ack, act(b, "guess", "text" to "${word}s"))
        assertTrue(guessScreen(b).solved)
        assertTrue(tv.wrong.none { it.text.contains(word) }, "a bubble held the word")
    }

    @Test fun aCorrectGuessNeverBecomesABubble() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        act(guessers(ids, drawer).first(), "guess", "text" to word)
        assertTrue(tv.wrong.isEmpty())
    }

    @Test fun onlyTheLastEightWrongGuessesAreKept() {
        val ids = start(4)
        val (drawer, _) = startDrawing()
        val g = guessers(ids, drawer).first()
        repeat(12) { act(g, "guess", "text" to "nope$it") }
        assertEquals(8, tv.wrong.size)
        assertEquals("nope11", tv.wrong.last().text)
        assertEquals(12, tv.missTotal)
    }

    // ---- ending a draw --------------------------------------------------------------------------

    @Test fun eachSkipMovesToTheNextHintStageThenTheReveal() {
        start(4)
        startDrawing()
        assertEquals(45_000, tv.tailMs)
        skip(); assertEquals(22_500, tv.tailMs); assertEquals("draw", tv.phase)
        skip(); assertEquals(0, tv.tailMs); assertEquals("draw", tv.phase)
        skip(); assertEquals("reveal", tv.phase)
    }

    @Test fun nobodyGuessingCostsTheDrawerTwoSipsAndPaysNothing() {
        val ids = start(4)
        val (drawer, _) = startDrawing()
        repeat(3) { skip() }
        assertEquals("reveal", tv.phase)
        assertEquals(0, score(drawer))
        assertEquals(listOf(drawer), tv.drinks.map { it.id })
        assertEquals(2, tv.drinks.single().sips)
        assertEquals(ids.size, e.tvState().scores.size)
    }

    @Test fun guessersWhoMissedDrinkOneSipWhenSomeoneElseGotIt() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        val (a, b, c) = guessers(ids, drawer)
        act(a, "guess", "text" to word)
        repeat(3) { skip() }
        assertEquals("reveal", tv.phase)
        assertEquals(setOf(b, c), tv.drinks.map { it.id }.toSet())
        assertTrue(tv.drinks.all { it.sips == 1 })
    }

    @Test fun drinkCallsFollowTheLobbySwitch() {
        start(3, settings = mapOf("drinks" to 0))
        startDrawing()
        repeat(3) { skip() }
        assertTrue(tv.drinks.isEmpty())

        start(3)
        startDrawing()
        repeat(3) { skip() }
        assertTrue(tv.drinks.single().text.endsWith("sips"), tv.drinks.single().text)
    }

    @Test fun theFinalTurnCountsDouble() {
        val ids = start(3, turns = 3)
        toTurn(3)
        val (drawer, word) = startDrawing()
        assertTrue(tv.finalTurn)
        act(guessers(ids, drawer).first(), "guess", "text" to word)
        assertEquals(2000, score(guessers(ids, drawer).first()))
    }

    // ---- turns ----------------------------------------------------------------------------------

    @Test fun everyoneDrawsOnceBeforeAnyoneDrawsTwice() {
        start(3, turns = 3)
        val drawers = mutableListOf<PlayerId>()
        for (t in 1..3) { toTurn(t); drawers += tv.drawer!! }
        assertEquals(3, drawers.toSet().size)
    }

    @Test fun aWordIsNeverOfferedTwiceInAGame() {
        start(3, turns = 5)
        val seen = mutableListOf<String>()
        for (t in 1..5) {
            toTurn(t)
            val (_, word) = startDrawing()
            seen += word
        }
        assertEquals(seen.size, seen.toSet().size)
    }

    @Test fun thePodiumHoldsAGalleryOfEveryTurnThenTheGameFinishes() {
        start(3, turns = 3)
        while (tv.phase != "podium") skip()
        assertEquals(3, tv.gallery.size)
        assertEquals(listOf(1, 2, 3), tv.gallery.map { it.turn })
        assertTrue(tv.gallery.all { it.word.isNotBlank() })
        skip()
        assertNull(e.tvState().stage)
        assertNotEquals(null, e.tvState().lastResult)
    }

    // ---- presence, late joiners, stale actions --------------------------------------------------

    @Test fun aLateJoinerWatchesUntilTheNextTurn() {
        start(3)
        val (_, word) = startDrawing()
        val late = e.add("Late")
        assertEquals(ActionResult.Rejected("NEXT_ROUND"), act(late, "guess", "text" to word))
        assertIs<Screen.Waiting>(e.phoneState(late).screen)
    }

    @Test fun aDrawerWhoDropsBeforePickingIsAutoPickedAndTheTurnGoesOn() {
        val ids = start(4)
        val drawer = tv.drawer!!
        e.setPresence(drawer, false)
        assertEquals("draw", tv.phase)
        val g = guessers(ids, drawer).first()
        val word = assertIs<Screen.Draw>(e.phoneState(drawer).screen).word // the drawer's phone still knows it
        act(g, "guess", "text" to word)
        assertEquals(1, tv.guessed)
    }

    @Test fun aDrawerWhoDropsMidDrawDoesNotStopTheTurnEnding() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        e.setPresence(drawer, false)
        guessers(ids, drawer).forEach { act(it, "guess", "text" to word) }
        assertEquals("reveal", tv.phase)
    }

    @Test fun aGuessFromAnEarlierPhaseIsStale() {
        val ids = start(4)
        val old = seq
        val (drawer, word) = startDrawing()
        val g = guessers(ids, drawer).first()
        assertEquals(ActionResult.Rejected("STALE"), e.action(g, "late", old, buildJsonObject { put("kind", JsonPrimitive("guess")); put("text", JsonPrimitive(word)) }))
    }

    // ---- ink permission -------------------------------------------------------------------------

    @Test fun onlyTheDrawerMayInkAndOnlyDuringTheDrawOfTheCurrentPhase() {
        val ids = start(4)
        val drawer = tv.drawer!!
        assertNull(e.inkTurn(drawer, seq)) // still picking
        startDrawing()
        assertEquals(1, e.inkTurn(drawer, seq))
        assertNull(e.inkTurn(ids.first { it != drawer }, seq))
        assertNull(e.inkTurn(drawer, seq - 1)) // an old phase number
        e.host(HostCmd.Pause)
        assertNull(e.inkTurn(drawer, seq)) // paused
        e.host(HostCmd.Resume)
        repeat(3) { skip() } // reveal
        assertNull(e.inkTurn(drawer, seq))
    }

    // ---- restore --------------------------------------------------------------------------------

    @Test fun aSavedGameRestoresMidTurn() {
        start(4)
        val (drawer, _) = startDrawing()
        val restored = PartyEngine.restore(e.snapshot(), clock, SeededEntropy(7), GameRegistry(listOf(Doodle(pack))))
        val g = restored.tvState().stage!!.game as DoodleTv
        assertEquals("draw", g.phase)
        assertEquals(drawer, g.drawer)
    }
}
