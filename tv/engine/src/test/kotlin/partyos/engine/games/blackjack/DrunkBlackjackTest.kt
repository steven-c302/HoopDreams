package partyos.engine.games.blackjack

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.BlackjackTv
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.PlayingCard
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.add
import partyos.engine.games.blackjack.DrunkBlackjack.Companion.isBlackjack
import partyos.engine.games.blackjack.DrunkBlackjack.Companion.sipLabel
import partyos.engine.games.blackjack.DrunkBlackjack.Companion.total
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DrunkBlackjackTest {
    private val clock = FakeClock(0)
    private lateinit var e: PartyEngine
    private var n = 0

    private fun start(names: List<String>, seed: Long = 7): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(seed), GameRegistry(listOf(DrunkBlackjack())))
        val ids = names.map { e.add(it) }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("blackjack")))
        e.host(HostCmd.SkipPhase) // tutorial
        return ids
    }

    private fun passTime(ms: Long) { clock.advance(ms); e.tick() }
    private fun untilPhase(phase: String) {
        repeat(120) { if (tv.phase == phase) return; passTime(1_000) }
        assertEquals(phase, tv.phase)
    }
    private val tv get() = e.tvState().stage!!.game as BlackjackTv
    private val round get() = e.tvState().stage!!.phaseSeq
    private fun act(who: PlayerId, kind: String, option: String) =
        e.action(who, "a${n++}", round, buildJsonObject { put("kind", JsonPrimitive(kind)); put("option", JsonPrimitive(option)) })

    @Test fun totalsAndSipLabels() {
        fun c(vararg r: Int) = r.map { PlayingCard(it, 0) }
        assertEquals(21, total(c(1, 13)))
        assertTrue(isBlackjack(c(1, 12)))
        assertEquals(12, total(c(1, 1)))
        assertEquals(22, total(c(10, 5, 7)))
        assertEquals("3 sips", sipLabel(3))
        assertEquals("1 shot", sipLabel(5))
        assertEquals("2 shots + 1 sip", sipLabel(11))
    }

    @Test fun onePlayerDealsAndEveryoneElseBetsSips() {
        start(listOf("Ava", "Ben", "Cleo", "Dev"))
        val dealer = tv.dealerId!!
        assertEquals(3, tv.seats.size)
        assertTrue(tv.seats.none { it.id == dealer }, "the dealer does not have a seat")
        val bet = assertIs<Screen.Cards>(e.phoneState(tv.seats[0].id).screen)
        assertEquals(listOf("b1", "b2", "b3", "b5"), bet.actions.map { it.id })
        assertIs<ActionResult.Rejected>(act(dealer, "bet", "b2"))
        tv.seats.forEach { act(it.id, "bet", "b5") }
        assertEquals("play", tv.phase)
        assertEquals(0, tv.dealer[1].rank, "the hole card stays hidden from the table")
    }

    @Test fun theDealerPlaysFromTheirPhoneAndMustHitUnder17() {
        start(listOf("Ava", "Ben", "Cleo"), seed = 11)
        val dealer = tv.dealerId!!
        tv.seats.forEach { act(it.id, "bet", "b2") }
        tv.seats.filter { it.status == "playing" }.forEach { act(it.id, "move", "stand") }
        if (tv.phase != "dealer") return // everyone had blackjack or the dealer had 21: nothing to play
        val screen = assertIs<Screen.Cards>(e.phoneState(dealer).screen)
        val t = total(tv.dealer)
        if (t < 17) {
            assertEquals(listOf("hit"), screen.actions.map { it.id })
            assertIs<ActionResult.Rejected>(act(dealer, "move", "stand"))
        }
        repeat(10) { if (tv.phase == "dealer") act(dealer, "move", if (total(tv.dealer) < 17) "hit" else "stand") }
        assertEquals("settle", tv.phase)
    }

    @Test fun aBustedDealerDrinksEveryBetStillStanding() {
        // Try seeds until the book-playing dealer busts, then check the whole table's bets land on them.
        for (seed in 1L..200L) {
            start(listOf("Ava", "Ben", "Cleo", "Dev"), seed)
            tv.seats.forEach { act(it.id, "bet", "b3") }
            tv.seats.filter { it.status == "playing" }.forEach { act(it.id, "move", "stand") }
            if (tv.phase == "dealer") passTime(DrunkBlackjack.DEALER_MS)
            if (tv.phase != "settle" || total(tv.dealer) <= 21) continue
            val standing = tv.seats.filter { it.total <= 21 }
            assertTrue(standing.all { (it.drinks ?: 0) < 0 })
            assertEquals(standing.sumOf { -(it.drinks ?: 0) }, tv.dealerDrinks)
            val givers = e.tvState().scores.filter { it.score > 0 }.map { it.id }.toSet()
            assertEquals(standing.map { it.id }.toSet(), givers)
            return
        }
        error("no seed made the dealer bust")
    }

    @Test fun theDealRotatesAndTheGameEndsWhenEveryoneHasDealt() {
        val ids = start(listOf("Ava", "Ben", "Cleo"))
        assertEquals(3, tv.totalRounds)
        val dealers = mutableListOf<PlayerId>()
        repeat(3) {
            dealers += tv.dealerId!!
            tv.seats.forEach { act(it.id, "bet", "b1") }
            passTime(DrunkBlackjack.PLAY_MS)
            if (tv.phase == "dealer") passTime(DrunkBlackjack.DEALER_MS)
            untilPhase("settle")
            passTime(DrunkBlackjack.SETTLE_MS)
        }
        assertEquals(ids.toSet(), dealers.toSet(), "everyone deals once")
        assertEquals("podium", tv.phase)
        passTime(DrunkBlackjack.PODIUM_MS)
        assertEquals(null, e.tvState().stage)
        assertNotEquals(null, e.tvState().lastResult)
    }
}
