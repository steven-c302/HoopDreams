package partyos.engine.games.jeopardy

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
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
    private fun start(count: Int, show: Int = 0): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Jeopardy(pack()))))
        val ids = (1..count).map { e.add("P$it") }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("jeopardy", mapOf("show" to show))))
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
}
