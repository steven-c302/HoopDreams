package partyos.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeTest {
    private val clock = FakeClock(0)
    private val games = GameRegistry(listOf(CountdownGame))
    private fun engine(seed: Long = 1) = PartyEngine(clock, SeededEntropy(seed), games)
    private val PartyEngine.stage get() = assertNotNull(tvState().stage)
    private val PartyEngine.round get() = stage.phaseSeq

    /** Starts countdown with the given players and skips the tutorial. */
    private fun started(vararg names: String): Pair<PartyEngine, List<PlayerId>> {
        val e = engine()
        val ids = names.map { e.add(it) }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("countdown")))
        assertEquals(ActionResult.Ack, e.host(HostCmd.SkipPhase))
        assertEquals("input", (e.stage.game as GenericTv).title)
        return e to ids
    }

    @Test fun startIsRefusedBelowMinPlayers() {
        val e = engine()
        e.add("A")
        e.add("B", connected = false)
        assertEquals(ActionResult.Rejected("NOT_ENOUGH_PLAYERS"), e.host(HostCmd.StartGame("countdown")))
        assertNull(e.tvState().stage)
    }

    @Test fun unknownGameIsRejected() {
        val e = engine(); e.add("A"); e.add("B")
        assertEquals(ActionResult.Rejected("UNKNOWN_GAME"), e.host(HostCmd.StartGame("nope")))
    }

    @Test fun tutorialEndsWhenAllConnectedPlayersAck() {
        val e = engine()
        val a = e.add("A"); val b = e.add("B"); e.add("C", connected = false); e.add("Spec", Role.SPECTATOR)
        e.host(HostCmd.StartGame("countdown"))
        assertNotNull(e.stage.tutorial)
        assertIs<Screen.Tutorial>(e.phoneState(a).screen)
        assertEquals(ActionResult.Ack, e.action(a, "a1", e.round, ack))
        assertNotNull(e.stage.tutorial)
        e.action(b, "b1", e.round, ack)
        assertNull(e.stage.tutorial)
        assertEquals("input", (e.stage.game as GenericTv).title)
    }

    @Test fun tutorialEndsAfterThirtySeconds() {
        val e = engine(); e.add("A"); e.add("B")
        e.host(HostCmd.StartGame("countdown"))
        assertEquals(30_000L, e.nextDeadline())
        clock.advance(30_000); e.tick()
        assertNull(e.stage.tutorial)
    }

    @Test fun staleRoundIsRejected() {
        val (e, ids) = started("A", "B")
        assertEquals(ActionResult.Rejected("STALE"), e.action(ids[0], "x", e.round - 1, tap))
    }

    @Test fun duplicateActionIdIsAckedButAppliedOnce() {
        val (e, ids) = started("A", "B", "C")
        val r = e.round
        assertEquals(ActionResult.Ack, e.action(ids[0], "same", r, tap))
        assertEquals(ActionResult.Ack, e.action(ids[0], "same", r, tap))
        assertEquals(10, e.tvState().scores.single { it.id == ids[0] }.score)
    }

    @Test fun gameRejectionIsReported() {
        val (e, ids) = started("A", "B")
        assertEquals(ActionResult.Rejected("BAD_ACTION"), e.action(ids[0], "x", e.round, ack))
    }

    @Test fun spectatorsCannotAct() {
        val (e, _) = started("A", "B")
        val s = e.add("S", Role.SPECTATOR)
        assertEquals(ActionResult.Rejected("SPECTATOR"), e.action(s, "x", e.round, tap))
        assertIs<Screen.Waiting>(e.phoneState(s).screen)
    }

    @Test fun pauseFreezesRemainingTimeAndResumeRestoresIt() {
        val (e, _) = started("A", "B")
        clock.advance(4_000)
        e.host(HostCmd.Pause)
        assertNull(e.nextDeadline())
        assertEquals(6_000L, e.stage.remainingMs)
        clock.advance(60_000); e.tick()
        assertEquals("input", (e.stage.game as GenericTv).title)
        e.host(HostCmd.Resume)
        assertEquals(clock.now() + 6_000, e.nextDeadline())
    }

    @Test fun actionsWhilePausedAreRejected() {
        val (e, ids) = started("A", "B")
        e.host(HostCmd.Pause)
        assertEquals(ActionResult.Rejected("PAUSED"), e.action(ids[0], "x", e.round, tap))
    }

    @Test fun phaseEndsEarlyWhenAllConnectedWaitingPlayersActed() {
        val (e, ids) = started("A", "B", "C")
        e.setPresence(ids[2], false)
        e.action(ids[0], "1", e.round, tap)
        assertEquals("input", (e.stage.game as GenericTv).title)
        e.action(ids[1], "2", e.round, tap)
        assertEquals("done", (e.stage.game as GenericTv).title)
    }

    @Test fun disconnectOfLastWaitingPlayerEndsPhase() {
        val (e, ids) = started("A", "B", "C")
        e.action(ids[0], "1", e.round, tap)
        e.action(ids[1], "2", e.round, tap)
        e.setPresence(ids[2], false)
        assertEquals("done", (e.stage.game as GenericTv).title)
    }

    @Test fun autoPausesWhenFewerThanTwoPlayersConnected() {
        val (e, ids) = started("A", "B")
        e.setPresence(ids[1], false)
        assertTrue(e.stage.paused)
        assertEquals("WAITING_FOR_PLAYERS", e.stage.pauseReason)
    }

    @Test fun deadlineAdvancesPhaseAndFinishRecordsResult() {
        val (e, ids) = started("A", "B")
        e.action(ids[0], "1", e.round, tap)
        clock.advance(10_000); e.tick()
        assertEquals("done", (e.stage.game as GenericTv).title)
        clock.advance(5_000); e.tick()
        assertNull(e.tvState().stage)
        val result = assertNotNull(e.tvState().lastResult)
        assertEquals("countdown", result.gameId)
        assertEquals(listOf("A", "B"), result.standings.map { it.name })
        assertIs<Screen.Waiting>(e.phoneState(ids[0]).screen)
    }

    @Test fun endGameRecordsResultImmediately() {
        val (e, _) = started("A", "B")
        e.host(HostCmd.EndGame)
        assertNull(e.tvState().stage)
        assertNotNull(e.tvState().lastResult)
    }

    @Test fun kickRemovesPlayerFromParty() {
        val (e, ids) = started("A", "B", "C")
        e.host(HostCmd.Kick(ids[2]))
        assertEquals(listOf("A", "B"), e.tvState().players.map { it.name })
    }

    @Test fun restoreMidPhaseIsPausedWithSameStateAndScoresAwardedOnce() {
        val (e, ids) = started("A", "B", "C")
        e.action(ids[0], "1", e.round, tap)
        clock.advance(3_000)
        val json = Json.encodeToString(PartySnapshot.serializer(), e.snapshot())
        val r = PartyEngine.restore(Json.decodeFromString(PartySnapshot.serializer(), json), clock, SeededEntropy(9), games)
        val st = assertNotNull(r.tvState().stage)
        assertTrue(st.paused)
        assertEquals("RESTORED", st.pauseReason)
        assertEquals(7_000L, st.remainingMs)
        assertEquals(e.stage.game, st.game)
        assertEquals(10, r.tvState().scores.single { it.id == ids[0] }.score)
        r.host(HostCmd.Resume)
        assertEquals(ActionResult.Ack, r.action(ids[0], "1", st.phaseSeq, tap))
        assertEquals(10, r.tvState().scores.single { it.id == ids[0] }.score)
        assertTrue(ids.all { !r.player(it)!!.connected })
    }

    @Test fun sameSeedAndActionsGiveSameResult() {
        fun run(): TvState {
            val c = FakeClock(0)
            val e = PartyEngine(c, SeededEntropy(42), games)
            val ids = (1..6).map { e.add("P$it") }
            e.host(HostCmd.StartGame("countdown")); e.host(HostCmd.SkipPhase)
            e.action(ids[3], "a", e.tvState().stage!!.phaseSeq, tap)
            return e.tvState()
        }
        assertEquals(run().stage!!.game, run().stage!!.game)
    }

    @Test fun phoneStateCarriesRoundAndRemainingTime() {
        val (e, ids) = started("A", "B")
        clock.advance(2_500)
        val p = e.phoneState(ids[0])
        assertEquals(e.round, p.round)
        assertEquals(7_500L, p.remainingMs)
        assertIs<Screen.ChoiceList>(p.screen)
    }
}
