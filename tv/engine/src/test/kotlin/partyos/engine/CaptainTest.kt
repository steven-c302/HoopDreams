package partyos.engine

import partyos.engine.games.trivia.BrainDrain
import partyos.engine.games.trivia.BallparkItem
import partyos.engine.games.trivia.GauntletItem
import partyos.engine.games.trivia.GauntletOption
import partyos.engine.games.trivia.McItem
import partyos.engine.games.trivia.SidesItem
import partyos.engine.games.trivia.SidesSet
import partyos.engine.games.trivia.TriviaPack
import partyos.engine.games.trivia.TriviaState
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptainTest {
    private val clock = FakeClock(0)
    private val pack = TriviaPack(
        mc = (1..4).map { McItem("tm$it", "Test", "Q$it?", "R$it", listOf("A$it", "B$it", "C$it")) },
        ballpark = listOf(BallparkItem("tb1", "Test", "N?", 1.0)),
        sides = listOf(SidesSet("ts1", "L or R?", "L", "R", (1..5).map { SidesItem("I$it", if (it % 2 == 0) "left" else "right") })),
        gauntlet = listOf(GauntletItem("tg1", "Fits?", listOf(GauntletOption("F", true), GauntletOption("M", false), GauntletOption("G", true)))),
    )
    private fun engine() = PartyEngine(clock, SeededEntropy(11), GameRegistry(listOf(BrainDrain(pack), CountdownGame)))

    @Test fun theFirstPlayerToJoinHoldsTheCrown() {
        val e = engine()
        assertNull(e.captain())
        val a = e.add("Ava")
        clock.advance(10)
        val b = e.add("Ben")
        assertEquals(a, e.captain())
        assertEquals(a, e.tvState().captain)
        assertTrue(e.phoneState(a).captain)
        assertFalse(e.phoneState(b).captain)
        assertEquals("Ava", e.phoneState(b).captainName)
        assertEquals(listOf("Ava", "Ben"), e.phoneState(a).crew.map { it.name })
        assertTrue(e.phoneState(b).crew.isEmpty())
    }

    @Test fun theCrownMovesWhenTheCaptainsPhoneDropsAndComesBackWithThem() {
        val e = engine()
        val a = e.add("Ava"); clock.advance(10)
        val b = e.add("Ben"); clock.advance(10)
        e.add("Cy")
        e.setPresence(a, false)
        assertEquals(b, e.captain())
        e.setPresence(a, true)
        assertEquals(a, e.captain())
    }

    @Test fun theCaptainCanPassTheCrownAndTheHostCanReassignIt() {
        val e = engine()
        val a = e.add("Ava")
        val b = e.add("Ben")
        val c = e.add("Cy")
        assertEquals(ActionResult.Rejected("NOT_CAPTAIN"), e.captainCommand(b, HostCmd.MakeCaptain(b)))
        assertEquals(ActionResult.Ack, e.captainCommand(a, HostCmd.MakeCaptain(b)))
        assertEquals(b, e.captain())
        assertEquals(ActionResult.Ack, e.host(HostCmd.MakeCaptain(c)))
        assertEquals(c, e.captain())
    }

    @Test fun theCaptainRunsTheShowButCannotRemovePeopleOrDisablePhones() {
        val e = engine()
        val a = e.add("Ava")
        val b = e.add("Ben")
        assertEquals(ActionResult.Rejected("HOST_ONLY"), e.captainCommand(a, HostCmd.Kick(b)))
        assertEquals(ActionResult.Rejected("HOST_ONLY"), e.captainCommand(a, HostCmd.SetOption("captain", 0)))
        assertEquals(ActionResult.Ack, e.captainCommand(a, HostCmd.StartGame("countdown")))
        assertNotNull(e.tvState().stage)
        assertEquals(ActionResult.Ack, e.captainCommand(a, HostCmd.Pause))
        assertTrue(e.tvState().stage!!.paused)
        assertEquals(ActionResult.Ack, e.captainCommand(a, HostCmd.EndGame))
        assertNull(e.tvState().stage)
    }

    @Test fun sharedSettingsAreClampedAndFlowIntoTheNextGame() {
        val e = engine()
        val a = e.add("Ava")
        (1..7).forEach { e.add("P$it") }
        assertEquals(ActionResult.Ack, e.captainCommand(a, HostCmd.SetOption("teams", 3)))
        assertEquals(ActionResult.Ack, e.captainCommand(a, HostCmd.SetOption("drinks", 0)))
        assertEquals(ActionResult.Ack, e.captainCommand(a, HostCmd.SetOption("rounds", 99)))
        assertEquals(ActionResult.Ack, e.captainCommand(a, HostCmd.SetOption("game", 9)))
        assertEquals(ActionResult.Rejected("BAD_OPTION"), e.captainCommand(a, HostCmd.SetOption("volume", 3)))
        assertEquals(mapOf("teams" to 3, "drinks" to 0, "rounds" to 8, "game" to 1), e.tvState().settings)
        e.captainCommand(a, HostCmd.StartGame("trivia"))
        e.host(HostCmd.SkipPhase) // tutorial
        val state = Json.decodeFromJsonElement(TriviaState.serializer(), e.snapshot().game!!.state!!)
        assertEquals(3, state.teams.size)
        assertEquals(8, state.perRound)
        assertFalse(state.drinks)
    }

    @Test fun turningPhoneControlOffTakesTheCrownAway() {
        val e = engine()
        val a = e.add("Ava")
        e.host(HostCmd.SetOption("captain", 0))
        assertNull(e.captain())
        assertEquals(ActionResult.Rejected("NOT_CAPTAIN"), e.captainCommand(a, HostCmd.Pause))
        e.host(HostCmd.SetOption("captain", 1))
        assertEquals(a, e.captain())
    }

    @Test fun aRemovedCaptainHandsTheCrownOnAndARestoreKeepsIt() {
        val e = engine()
        val a = e.add("Ava"); clock.advance(10)
        val b = e.add("Ben"); clock.advance(10)
        val c = e.add("Cy")
        e.kick(a)
        assertEquals(b, e.captain())
        e.host(HostCmd.MakeCaptain(c))
        val restored = PartyEngine.restore(e.snapshot(), clock, SeededEntropy(3), GameRegistry(listOf(BrainDrain(pack))))
        restored.setPresence(b, true)
        restored.setPresence(c, true)
        assertEquals(c, restored.captain())
    }
}
