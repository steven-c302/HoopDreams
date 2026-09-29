package partyos.engine.games.jeopardy

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.Avatar
import partyos.engine.JoinResult
import partyos.engine.Role
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.JeopardyTv
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JeopardyTest {
    private val clock = FakeClock(0)
    private fun clues(cat: Int) = (0..4).map { JeopardyClue("c$cat-$it", "Clue c$cat-$it", "Ans${cat}x$it") }
    private fun pack() = JeopardyPack(
        "test", "Test", "jeopardy", 2,
        (0 until 12).map { JeopardyCategory("c$it", "Cat $it", clues(it)) },
        (0 until 4).map { JeopardyFinal("f$it", "Final $it", "Final clue $it", "Fin$it") },
    )

    private lateinit var e: PartyEngine
    private var n = 0

    /** Starts Answer & Question with [count] players, the tutorial skipped, in the intro phase. */
    private fun start(count: Int, show: Int = 0, extra: Map<String, Int> = emptyMap()): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Jeopardy(pack()))))
        val ids = (1..count).map { e.add("P$it") }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("jeopardy", mapOf("show" to show) + extra)))
        e.host(HostCmd.SkipPhase)
        return ids
    }

    private val tv get() = e.tvState().stage!!.game as JeopardyTv
    private val seq get() = e.tvState().stage!!.phaseSeq
    private fun act(who: PlayerId, kind: String, vararg kv: Pair<String, Any>) = e.action(
        who, "a${n++}", seq,
        buildJsonObject { put("kind", JsonPrimitive(kind)); kv.forEach { (k, v) -> put(k, if (v is Int) JsonPrimitive(v) else JsonPrimitive(v.toString())) } },
    )
    private fun skip() { e.host(HostCmd.SkipPhase) }
    private fun screen(who: PlayerId) = e.phoneState(who).screen
    private fun toPick() { repeat(3) { if (tv.phase != "pick") skip() } }
    private fun pickCell(id: String) = e.host(HostCmd.GameAction("pick:$id"))

    @Test fun startsInTheIntroWithTheFiveCategoriesOfTheFirstBoard() {
        start(3)
        assertEquals("intro", tv.phase)
        assertEquals(1, tv.round)
        assertEquals(5, tv.categories.size)
        assertEquals(25, tv.cells.size)
        assertEquals(listOf(200, 400, 600, 800, 1000), (0..4).map { r -> tv.cells.first { it.row == r && it.col == 0 }.value })
        assertTrue(tv.cells.none { it.used })
    }

    @Test fun theShowLengthSettingChoosesOneBoardOrTwo() {
        start(3, show = 0); assertEquals(1, tv.boards)
        start(3, show = 1); assertEquals(2, tv.boards)
    }

    @Test fun showIsAnAllowedLobbySettingWithTwoValues() {
        val lobby = PartyEngine(clock, SeededEntropy(1), GameRegistry(emptyList()))
        assertEquals(ActionResult.Ack, lobby.host(HostCmd.SetOption("show", 1)))
        // The engine clamps an out-of-range value into the option's range; only unknown keys are rejected.
        assertEquals(ActionResult.Ack, lobby.host(HostCmd.SetOption("show", 2)))
        assertEquals(ActionResult.Rejected("BAD_OPTION"), lobby.host(HostCmd.SetOption("shows", 1)))
    }

    @Test fun theIntroMovesToTheBoardWithTheCaptainHoldingIt() {
        val ids = start(3)
        skip()
        assertEquals("pick", tv.phase)
        assertEquals(ids[0], tv.controller)
    }

    @Test fun theControllerSeesABoardTheyCanPickFromAndOthersSeeWhoIsPicking() {
        val ids = start(3)
        toPick()
        val mine = assertIs<Screen.Board>(screen(ids[0]))
        assertTrue(mine.canPick)
        assertEquals(5, mine.categories.size)
        assertEquals(25, mine.cells.size)
        val theirs = assertIs<Screen.Board>(screen(ids[1]))
        assertFalse(theirs.canPick)
        assertTrue("P1" in theirs.prompt, theirs.prompt)
    }

    @Test fun theControllerPicksACellFromTheirPhone() {
        val ids = start(3)
        toPick()
        val id = tv.cells.first { it.row == 0 && it.col == 2 }.id
        assertEquals(ActionResult.Ack, act(ids[0], "pick", "cell" to id))
        assertEquals("clue", tv.phase)
        assertEquals("Clue $id", tv.clue)
        assertEquals(200, tv.value)
        assertTrue(tv.cells.first { it.id == id }.let { !it.used }) // it is only used once the clue is revealed
    }

    @Test fun onlyTheControllerOrTheCaptainMayPickAndTheCellMustExist() {
        val ids = start(3)
        toPick()
        val id = tv.cells.first { it.row == 0 }.id
        assertEquals(ActionResult.Rejected("NOT_YOUR_PICK"), act(ids[1], "pick", "cell" to id))
        assertEquals(ActionResult.Rejected("BAD_CELL"), act(ids[0], "pick", "cell" to "nope"))
        assertEquals("pick", tv.phase)
    }

    @Test fun theTvKeyboardCanPickWithoutAPhone() {
        start(3)
        toPick()
        val id = tv.cells.first { it.row == 0 && it.col == 4 }.id
        assertEquals(ActionResult.Ack, pickCell(id))
        assertEquals("clue", tv.phase)
    }

    @Test fun runningOutOfPickTimeTakesACellFromTheCheapestRow() {
        start(3)
        toPick()
        skip() // the pick timer runs out
        assertEquals("clue", tv.phase)
        assertEquals(200, tv.value)
    }

    @Test fun aPickerWhoLeavesIsSkippedAtOnce() {
        val ids = start(3)
        toPick()
        e.setPresence(ids[0], false)
        assertEquals("clue", tv.phase)
    }

    @Test fun eachNightGetsDifferentCategoriesWhileThePoolLasts() {
        start(3)
        val first = tv.categories.toSet()
        e.host(HostCmd.EndGame)
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("jeopardy", mapOf("show" to 0))))
        e.host(HostCmd.SkipPhase)
        assertTrue(first.intersect(tv.categories.toSet()).isEmpty(), "second night repeated $first vs ${tv.categories}")
    }

    private fun score(id: PlayerId) = e.tvState().scores.single { it.id == id }.score
    private fun ansOf(cellId: String): String { val (cat, row) = cellId.removePrefix("c").split("-"); return "Ans${cat}x$row" }

    /** Opens a cell on the cheapest row (which never hides a Daily Double) and reads it, ending in the buzz phase. */
    private fun openPlain(col: Int = 0): String {
        toPick()
        val id = tv.cells.first { it.row == 0 && it.col == col && !it.used }.id
        assertEquals(ActionResult.Ack, pickCell(id))
        skip() // the clue has been read
        assertEquals("buzz", tv.phase)
        return id
    }

    @Test fun theClueIsReadBeforePhonesCanRingIn() {
        val ids = start(3)
        toPick()
        pickCell(tv.cells.first { it.row == 0 }.id)
        assertEquals("clue", tv.phase)
        val reading = assertIs<Screen.Buzzer>(screen(ids[1]))
        assertEquals("reading", reading.state)
        assertFalse(reading.live)
        skip()
        assertTrue(tv.buzzOpen)
        val open = assertIs<Screen.Buzzer>(screen(ids[1]))
        assertEquals("open", open.state)
        assertTrue(open.live)
    }

    @Test fun theFirstBuzzGetsTheFloorAndTheSecondIsRefused() {
        val ids = start(3)
        openPlain()
        assertEquals(ActionResult.Ack, act(ids[1], "buzz"))
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(ids[2], "buzz"))
        assertEquals("answer", tv.phase)
        assertEquals(ids[1], tv.floor)
        assertIs<Screen.TextEntry>(screen(ids[1])).also { assertEquals("answer", it.kind) }
        val beaten = assertIs<Screen.Buzzer>(screen(ids[2]))
        assertEquals("beaten", beaten.state)
        assertTrue("P2" in beaten.detail.orEmpty(), beaten.detail)
    }

    @Test fun ringingInBeforeTheClueIsReadLocksYouOutForASecond() {
        val ids = start(3)
        toPick()
        pickCell(tv.cells.first { it.row == 0 }.id)
        assertEquals(ActionResult.Ack, act(ids[1], "buzz")) // far too early
        val locked = assertIs<Screen.Buzzer>(screen(ids[1]))
        assertEquals("locked", locked.state)
        assertEquals(1000, locked.lockedMs)
        skip() // the clue has been read; the lockout is still running
        assertEquals(ActionResult.Rejected("LOCKED_OUT"), act(ids[1], "buzz"))
        clock.advance(1001)
        assertEquals(ActionResult.Ack, act(ids[1], "buzz"))
        assertEquals(ids[1], tv.floor)
    }

    @Test fun aRightAnswerWinsTheValueAndTheBoard() {
        val ids = start(3)
        val id = openPlain(col = 1)
        act(ids[2], "buzz")
        assertEquals(ActionResult.Ack, act(ids[2], "answer", "text" to ansOf(id)))
        assertEquals("reveal", tv.phase)
        assertEquals(true, tv.right)
        assertEquals(ansOf(id), tv.answer)
        assertEquals(200, score(ids[2]))
        skip()
        assertEquals("pick", tv.phase)
        assertEquals(ids[2], tv.controller)
        assertTrue(assertIs<Screen.Board>(screen(ids[2])).canPick)
    }

    @Test fun theCaptainCanPickForWhoeverHoldsTheBoard() {
        val ids = start(3)
        val id = openPlain()
        act(ids[2], "buzz"); act(ids[2], "answer", "text" to ansOf(id))
        skip()
        val captainsView = assertIs<Screen.Board>(screen(ids[0]))
        assertTrue(captainsView.canPick)
        assertEquals("P3", captainsView.pickFor)
        assertEquals(ActionResult.Rejected("NOT_YOUR_PICK"), act(ids[1], "pick", "cell" to tv.cells.first { !it.used }.id))
        assertEquals(ActionResult.Ack, act(ids[0], "pick", "cell" to tv.cells.first { !it.used }.id))
        assertEquals("clue", tv.phase)
    }

    @Test fun answersGivenAsAQuestionCountLikeOnTheShow() {
        for (lead in listOf("What is", "whats", "Who is", "WHERE ARE", "what was")) {
            val ids = start(2)
            val id = openPlain()
            act(ids[1], "buzz")
            act(ids[1], "answer", "text" to "$lead ${ansOf(id)}?")
            assertEquals(true, tv.right, "'$lead' should count")
            assertEquals(200, score(ids[1]), lead)
        }
    }

    @Test fun aWrongAnswerCostsTheValueAndTheClueReopensForTheOthers() {
        val ids = start(3)
        val id = openPlain()
        act(ids[1], "buzz")
        assertEquals(ActionResult.Ack, act(ids[1], "answer", "text" to "nope"))
        assertEquals(-200, score(ids[1])) // scores can go negative
        assertEquals("buzz", tv.phase)
        assertEquals(listOf(ids[1]), tv.tried)
        assertEquals("tried", assertIs<Screen.Buzzer>(screen(ids[1])).state)
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(ids[1], "buzz"))
        act(ids[2], "buzz")
        act(ids[2], "answer", "text" to ansOf(id))
        assertEquals(200, score(ids[2]))
        assertEquals(-200, score(ids[1]))
        assertEquals(ids[2], tv.controller)
    }

    @Test fun ifEveryoneAnswersWrongTheClueEndsAndTheBoardStaysPut() {
        val ids = start(3)
        openPlain()
        for (p in listOf(ids[1], ids[2], ids[0])) { act(p, "buzz"); act(p, "answer", "text" to "nope") }
        assertEquals("reveal", tv.phase)
        assertEquals(false, tv.right)
        assertTrue(ids.all { score(it) == -200 })
        assertEquals(3, tv.drinks.size)
        assertTrue(tv.drinks.all { it.text == "Drink 1 sip" })
        skip()
        assertEquals(ids[0], tv.controller)
    }

    @Test fun ifNobodyRingsInTheAnswerIsRevealedAndNothingChanges() {
        val ids = start(3)
        val id = openPlain()
        skip() // the buzz window closes
        assertEquals("reveal", tv.phase)
        assertEquals(false, tv.right)
        assertEquals(ansOf(id), tv.answer)
        assertTrue(ids.all { score(it) == 0 })
        skip()
        assertEquals(ids[0], tv.controller)
    }

    @Test fun anAnswerThatTimesOutCountsAsWrong() {
        val ids = start(3)
        openPlain()
        act(ids[1], "buzz")
        skip()
        assertEquals(-200, score(ids[1]))
        assertEquals("buzz", tv.phase)
    }

    @Test fun drinkLinesFollowTheLobbySwitch() {
        val ids = start(3, extra = mapOf("drinks" to 0))
        openPlain()
        for (p in listOf(ids[1], ids[2], ids[0])) { act(p, "buzz"); act(p, "answer", "text" to "nope") }
        assertTrue(tv.drinks.isEmpty())
    }

    @Test fun drinkLinesAreWordedAsWaterForAPlayerOnWater() {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Jeopardy(pack()))))
        val a = e.add("P1")
        val b = e.add("P2")
        val wet = (e.join(e.roomCode, "Wet", Avatar("p:00", "#123456"), Role.PLAYER, water = true) as JoinResult.Joined).player.id
        e.setPresence(wet, true)
        e.host(HostCmd.StartGame("jeopardy", mapOf("show" to 0)))
        e.host(HostCmd.SkipPhase)
        openPlain()
        for (p in listOf(wet, a, b)) { act(p, "buzz"); act(p, "answer", "text" to "nope") }
        assertTrue(tv.drinks.single { it.id == wet }.text.endsWith("Drink 1 sip of water"))
    }

    @Test fun aPlayerWhoJoinsMidClueWatchesThatClueAndRingsInOnTheNext() {
        val ids = start(3)
        openPlain()
        val late = e.add("Late")
        assertEquals("out", assertIs<Screen.Buzzer>(screen(late)).state)
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(late, "buzz"))
        skip() // nobody rang in
        skip() // back to the board
        val id = tv.cells.first { it.row == 0 && !it.used }.id
        pickCell(id)
        assertEquals("reading", assertIs<Screen.Buzzer>(screen(late)).state)
    }
}
