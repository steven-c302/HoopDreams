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
import partyos.engine.SeededEntropy
import partyos.engine.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JeopardyTest {
    private val clock = FakeClock(0)
    private fun clue(id: String, value: Int, answer: String) = JeopardyClue(id, value, "Clue for $id", answer)
    private fun pack() = JeopardyPack(
        "test", "Test", "jeopardy", 1,
        listOf(
            JeopardyCategory("Food", listOf(clue("f200", 200, "Guacamole"))),
            JeopardyCategory("Sports", listOf(clue("s200", 200, "Kobe Bryant"))),
        ),
    )

    private lateinit var e: PartyEngine
    private var n = 0

    private fun start(names: List<String>): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Jeopardy(pack()))))
        val ids = names.map { e.add(it) }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("jeopardy")))
        e.host(HostCmd.SkipPhase)
        return ids
    }

    private val tv get() = e.tvState().stage!!.game as JeopardyTv
    private fun answer(who: PlayerId, text: String) =
        e.action(who, "a${n++}", e.tvState().stage!!.phaseSeq, buildJsonObject { put("kind", JsonPrimitive("answer")); put("text", JsonPrimitive(text)) })
    private fun score(id: PlayerId) = e.tvState().scores.single { it.id == id }.score

    @Test fun picksAClueOffTheBoard() {
        start(listOf("A", "B"))
        assertEquals("select", tv.phase)
        assertTrue(tv.board.all { !it.used })
        assertEquals(ActionResult.Ack, e.host(HostCmd.GameAction("pick:f200")))
        assertEquals("answer", tv.phase)
        assertEquals("Food", tv.category)
        assertEquals(200, tv.value)
    }

    @Test fun cannotPickTheSameClueTwice() {
        start(listOf("A", "B"))
        e.host(HostCmd.GameAction("pick:f200"))
        e.host(HostCmd.SkipPhase) // -> reveal
        e.host(HostCmd.SkipPhase) // -> select
        assertEquals("select", tv.phase)
        assertEquals(ActionResult.Rejected("ALREADY_USED"), e.host(HostCmd.GameAction("pick:f200")))
    }

    @Test fun correctAnswersScoreTheCluesValueEachIndependently() {
        val (a, b) = start(listOf("A", "B"))
        e.host(HostCmd.GameAction("pick:f200"))
        assertEquals(ActionResult.Ack, answer(a, "guacamole"))
        assertEquals(ActionResult.Ack, answer(b, "hummus"))
        // Everyone has answered, so the engine advances to reveal on its own — no SkipPhase needed.
        assertEquals("reveal", tv.phase)
        assertEquals(listOf("A"), tv.correct)
        assertEquals(200, score(a))
        assertEquals(0, score(b))
        assertTrue(tv.board.first { it.id == "f200" }.used)
    }

    @Test fun lockedAnswerCannotBeChanged() {
        val (a) = start(listOf("A", "B"))
        e.host(HostCmd.GameAction("pick:f200"))
        answer(a, "guacamole")
        assertEquals(ActionResult.Rejected("LOCKED"), answer(a, "something else"))
    }

    @Test fun endsAtPodiumOnceEveryClueIsUsed() {
        start(listOf("A", "B"))
        e.host(HostCmd.GameAction("pick:f200"))
        e.host(HostCmd.SkipPhase) // -> reveal
        e.host(HostCmd.SkipPhase) // -> select (one clue left)
        assertEquals("select", tv.phase)
        e.host(HostCmd.GameAction("pick:s200"))
        e.host(HostCmd.SkipPhase) // -> reveal
        e.host(HostCmd.SkipPhase) // -> podium (board exhausted)
        assertEquals("podium", tv.phase)
    }
}
