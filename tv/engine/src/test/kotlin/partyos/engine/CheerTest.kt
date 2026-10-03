package partyos.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheerTest {
    private val clock = FakeClock(10_000)
    private fun engine() = PartyEngine(clock, SeededEntropy(3))

    @Test fun aCheerShowsOnTheTvWithTheCheerersNameAndFace() {
        val e = engine()
        val spec = e.add("Wes", Role.SPECTATOR)
        assertEquals(ActionResult.Ack, e.cheer(spec, "yes"))
        val c = e.tvState().cheers.single()
        assertEquals("Wes", c.name)
        assertEquals("yes", c.kind)
        assertEquals(1, c.seq)
    }

    @Test fun cheersCountUpAndOnlyTheLatestFewAreKept() {
        val e = engine()
        val people = (1..12).map { e.add("P$it", Role.SPECTATOR) }
        people.forEach { assertEquals(ActionResult.Ack, e.cheer(it, "wow")) }
        val seen = e.tvState().cheers
        assertEquals(PartyEngine.MAX_CHEERS, seen.size)
        assertEquals((13 - PartyEngine.MAX_CHEERS..12).toList(), seen.map { it.seq })
        assertEquals("P12", seen.last().name)
    }

    @Test fun onlyTheFixedCheersAreAccepted() {
        val e = engine()
        val spec = e.add("Wes", Role.SPECTATOR)
        assertEquals(ActionResult.Rejected("BAD_CHEER"), e.cheer(spec, "<script>"))
        assertEquals(ActionResult.Rejected("BAD_CHEER"), e.cheer(spec, ""))
        assertEquals(ActionResult.Rejected("NOT_PLAYER"), e.cheer(PlayerId("nobody"), "yes"))
        assertTrue(e.tvState().cheers.isEmpty())
    }

    @Test fun eachPersonIsLimitedToOneCheerAtATimeButOthersAreNot() {
        val e = engine()
        val a = e.add("A", Role.SPECTATOR)
        val b = e.add("B", Role.SPECTATOR)
        assertEquals(ActionResult.Ack, e.cheer(a, "yes"))
        assertEquals(ActionResult.Rejected("SLOW_DOWN"), e.cheer(a, "boo"))
        assertEquals(ActionResult.Ack, e.cheer(b, "boo"))
        clock.advance(PartyEngine.CHEER_GAP_MS)
        assertEquals(ActionResult.Ack, e.cheer(a, "boo"))
        assertEquals(listOf("A", "B", "A"), e.tvState().cheers.map { it.name })
    }

    @Test fun aKickedPlayerCantCheerAndCheersDontSurviveARestore() {
        val e = engine()
        val a = e.add("A", Role.SPECTATOR)
        e.cheer(a, "ooh")
        val back = PartyEngine.restore(e.snapshot(), clock, SeededEntropy(4))
        assertTrue(back.tvState().cheers.isEmpty(), "a restart starts with a quiet room")
        e.host(HostCmd.Kick(a))
        assertEquals(ActionResult.Rejected("NOT_PLAYER"), e.cheer(a, "yes"))
    }
}
