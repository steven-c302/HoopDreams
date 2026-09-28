package partyos.engine

import partyos.engine.games.bluff.BluffBattle
import partyos.engine.games.turf.HomeTurf
import kotlin.test.Test
import kotlin.test.assertEquals

/** The lobby's timer length stretches the time players get to act, and only that. */
class TimerSettingTest {
    private val clock = FakeClock(0)

    private fun bluff(timers: Int?): PartyEngine {
        val e = PartyEngine(clock, SeededEntropy(1), GameRegistry(listOf(BluffBattle())))
        repeat(3) { e.add("P$it") }
        timers?.let { assertEquals(ActionResult.Ack, e.host(HostCmd.SetOption("timers", it))) }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("bluff")))
        e.host(HostCmd.SkipPhase) // tutorial
        return e
    }

    private val PartyEngine.remaining get() = tvState().stage!!.remainingMs

    @Test fun normalRelaxedAndNoRushStretchTheWriteTimer() {
        assertEquals(BluffBattle.WRITE_MS, bluff(null).remaining)
        assertEquals(BluffBattle.WRITE_MS * 3 / 2, bluff(1).remaining)
        assertEquals(BluffBattle.WRITE_MS * 2, bluff(2).remaining)
    }

    @Test fun revealsKeepTheirPace() {
        val e = bluff(2)
        e.host(HostCmd.SkipPhase) // write -> pick
        assertEquals(BluffBattle.PICK_MS * 2, e.remaining)
        e.host(HostCmd.SkipPhase) // pick -> reveal
        val normal = bluff(null).apply { host(HostCmd.SkipPhase); host(HostCmd.SkipPhase) }
        assertEquals(normal.remaining, e.remaining, "the reveal is an animation, not a decision")
    }

    @Test fun aRunningGameKeepsItsTimers() {
        val e = bluff(1)
        assertEquals(ActionResult.Rejected("GAME_RUNNING"), e.host(HostCmd.SetOption("timers", 2)))
        assertEquals(BluffBattle.WRITE_MS * 3 / 2, e.remaining)
    }

    @Test fun anAbsentPlayersTurnStillRunsOnTheShortAutopilotClock() {
        val e = PartyEngine(clock, SeededEntropy(3), GameRegistry(listOf(HomeTurf())))
        listOf("Ava", "Ben", "Cleo").forEach { e.add(it) }
        e.host(HostCmd.SetOption("timers", 2))
        e.host(HostCmd.StartGame("turf"))
        e.host(HostCmd.SkipPhase) // tutorial
        assertEquals(HomeTurf.PIECES_MS * 2, e.remaining, "picking a piece is a decision")
        clock.advance(HomeTurf.PIECES_MS * 2); e.tick()
        clock.advance(HomeTurf.DEAL_MS); e.tick()
        assertEquals(HomeTurf.ROLL_MS * 2, e.remaining, "a present player's roll gets the long clock")
        val tv = e.tvState().stage!!.game as TurfTv
        e.setPresence(tv.tokens[tv.turn].seat!!, false)
        assertEquals(HomeTurf.AUTOPILOT_MS, e.remaining, "an absent player's turn isn't stretched")
    }
}
