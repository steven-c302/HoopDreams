package partyos.engine.games.sprawl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.SprawlTv
import partyos.engine.add
import partyos.engine.games.sprawl.Sprawl.Companion.ANYONE
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SprawlTest {
    private val clock = FakeClock(0)
    private val registry = GameRegistry(listOf(Sprawl()))
    private lateinit var e: PartyEngine
    private var ids: List<PlayerId> = emptyList()
    private var n = 0

    private fun start(count: Int = 3, vararg opts: Pair<String, Int>): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(7), registry)
        ids = (1..count).map { e.add("P$it") }
        opts.forEach { (k, v) -> e.host(HostCmd.SetOption(k, v)) }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("sprawl")))
        e.host(HostCmd.SkipPhase) // tutorial
        return ids
    }

    private val tv get() = e.tvState().stage!!.game as SprawlTv
    private val seq get() = e.tvState().stage!!.phaseSeq
    private val state: SprawlState get() = Json.decodeFromJsonElement(SprawlState.serializer(), e.snapshot().game!!.state!!)
    private fun phone(who: PlayerId) = assertIs<Screen.Sprawl>(e.phoneState(who).screen)
    private fun seat(i: Int) = state.seats[i].player
    private fun passTime(ms: Long) { clock.advance(ms); e.tick() }

    private fun act(who: PlayerId, kind: String, vararg kv: Pair<String, Any>): ActionResult =
        e.action(who, "a${n++}", seq, buildJsonObject {
            put("kind", JsonPrimitive(kind))
            kv.forEach { (k, v) ->
                when (v) {
                    is Int -> put(k, JsonPrimitive(v))
                    is List<*> -> put(k, JsonArray(v.map { JsonPrimitive(it as Int) }))
                    else -> put(k, JsonPrimitive(v.toString()))
                }
            }
        })

    /** Rewrites the game state through a snapshot and restore, then brings everyone back and resumes. */
    private fun rig(f: (SprawlState) -> SprawlState) {
        val snap = e.snapshot()
        val changed = snap.game!!.copy(state = Json.encodeToJsonElement(SprawlState.serializer(), f(state)))
        e = PartyEngine.restore(snap.copy(game = changed), clock, SeededEntropy(1), registry)
        ids.forEach { e.setPresence(it, true) }
        assertEquals(ActionResult.Ack, e.host(HostCmd.Resume))
    }

    /** Every setup pick taken from the phone's first glowing spot. */
    private fun finishSetup() {
        while (tv.phase == "setup") {
            val who = seat(tv.turn)
            assertEquals(ActionResult.Ack, act(who, "place", "target" to phone(who).spots.first()))
        }
        assertEquals("roll", tv.phase)
    }

    /** Points the dice at the next roll that sums to [sum]. */
    private fun rigDice(sum: Int) = rig { s ->
        s.copy(draws = (s.draws until s.draws + 10_000).first { d ->
            val r = Random(PartyEngine.phaseSeed(s.rngSeed, d))
            r.nextInt(1, 7) + r.nextInt(1, 7) == sum
        })
    }

    /** Lets the clock play turns until it's [seat]'s roll. */
    private fun untilRollOf(seat: Int) {
        var guard = 0
        while (!(tv.turn == seat && tv.phase == "roll")) { check(guard++ < 400) { "never got back to seat $seat" }; passTime(5_000) }
    }

    /** A simple path of [n] free edges out from one of [seat]'s settlements that no rival building interrupts. */
    private fun trailFrom(b: SBoard, seat: Int, n: Int): List<Int> {
        val g = b.geo
        fun dfs(path: List<Int>, edges: List<Int>): List<Int>? {
            if (edges.size == n) return edges
            val v = path.last()
            for (e in g.vEdges[v]) {
                val w = g.edges[e].first { it != v }
                if (b.eOwner[e] >= 0 || w in path || (b.vOwner[w] >= 0 && b.vOwner[w] != seat)) continue
                dfs(path + w, edges + e)?.let { return it }
            }
            return null
        }
        return b.vOwner.indices.filter { b.vOwner[it] == seat }.firstNotNullOf { dfs(listOf(it), emptyList()) }
    }

    private fun SprawlState.hand(i: Int, vararg cards: Int) = copy(seats = seats.mapIndexed { j, x -> if (j == i) x.copy(hand = cards.toList()) else x })
    private fun SprawlState.dev(i: Int, vararg cards: String) = copy(seats = seats.mapIndexed { j, x -> if (j == i) x.copy(dev = cards.toList()) else x })
    private fun total(s: SprawlState) = (0 until 5).map { r -> s.bank[r] + s.seats.sumOf { it.hand[r] } }

    // ---- setup ----------------------------------------------------------------------------

    @Test fun setupSnakesAndTheSecondSettlementPays() {
        start(3)
        assertEquals("setup", tv.phase)
        assertEquals(19, tv.map.hexes.size)
        val order = mutableListOf<Int>()
        while (tv.phase == "setup") {
            val who = seat(tv.turn)
            val scr = phone(who)
            if (tv.setupPiece == "settlement") {
                val seatIdx = tv.turn
                order += seatIdx
                assertEquals("vertex", scr.spotKind)
                val before = state.seats[seatIdx].hand
                val v = scr.spots.first()
                act(who, "place", "target" to v)
                val expect = MutableList(5) { 0 }
                if (order.size > 3) state.board.geo.vHexes[v].filter { state.board.terrain[it] != 5 }.forEach { expect[state.board.terrain[it]]++ }
                assertEquals(expect, SprawlRules.minus(state.seats[seatIdx].hand, before))
            } else {
                assertEquals("edge", scr.spotKind)
                act(who, "place", "target" to scr.spots.first())
            }
        }
        assertEquals(listOf(0, 1, 2, 2, 1, 0), order)
        assertEquals(List(5) { 19 }, total(state))
        assertEquals("roll", tv.phase)
        assertEquals(0, tv.turn)
    }

    @Test fun onlyThePickerPlacesAndBadSpotsBounce() {
        start(3)
        val first = seat(0)
        val other = seat(1)
        assertEquals(ActionResult.Rejected("NOT_YOUR_TURN"), act(other, "place", "target" to phone(first).spots.first()))
        val v = phone(first).spots.first()
        act(first, "place", "target" to v)
        // The road has to touch the new settlement.
        val far = state.board.geo.edges.indices.first { v !in state.board.geo.edges[it] }
        assertEquals(ActionResult.Rejected("BAD_SPOT"), act(first, "place", "target" to far))
    }

    @Test fun setupTimeoutsPlaceTheBestSpotForYou() {
        start(3)
        repeat(12) { passTime(Sprawl.SETUP_MS) }
        assertEquals("roll", tv.phase)
        assertEquals(6, state.board.vOwner.count { it >= 0 })
        assertEquals(6, state.board.eOwner.count { it >= 0 })
    }

    @Test fun bigPartiesGetTheBigIsland() {
        start(5)
        assertEquals(30, tv.map.hexes.size)
        assertEquals(List(5) { 24 }, tv.bank)
        assertEquals(34, tv.deckLeft)
    }

    // ---- rolling ----------------------------------------------------------------------------

    @Test fun aRollPaysEveryoneOnTheNumber() {
        start(3)
        finishSetup()
        rigDice(8)
        val before = state
        act(seat(0), "roll")
        val s = state
        assertEquals("main", s.phase)
        assertEquals(8, s.dice.sum())
        val expect = SprawlRules.production(before.board, 8, before.bank, 3)
        s.seats.indices.forEach { assertEquals(SprawlRules.plus(before.seats[it].hand, expect[it]), s.seats[it].hand) }
        assertEquals(total(before), total(s))
    }

    @Test fun aSevenMeansDiscardThenTheLandlordThenARobbery() {
        start(3)
        finishSetup()
        rigDice(7)
        rig { it.hand(0, 0, 0, 0, 0, 0).hand(1, 3, 3, 3, 0, 0).hand(2, 1, 1, 0, 0, 0) }
        act(seat(0), "roll")
        assertEquals("discard", tv.phase)
        assertEquals(4, tv.seats[1].discard)
        assertEquals(4, phone(seat(1)).discard)
        assertEquals(ActionResult.Rejected("BAD_DISCARD"), act(seat(1), "discard", "cards" to listOf(1, 1, 1, 0, 0)))
        assertEquals(ActionResult.Ack, act(seat(1), "discard", "cards" to listOf(2, 2, 0, 0, 0)))
        assertEquals("robber", tv.phase)
        assertEquals(listOf(1, 1, 3, 0, 0), state.seats[1].hand)
        // Move the Landlord onto a hex only seat 2 touches: the robbery is automatic.
        val b = state.board
        val hex = b.geo.hexes.indices.first { h -> h != b.robber && b.geo.hexVertices[h].any { b.vOwner[it] == 2 } && b.geo.hexVertices[h].none { b.vOwner[it] == 0 || b.vOwner[it] == 1 } }
        assertEquals("hex", phone(seat(0)).spotKind)
        act(seat(0), "robber", "target" to hex)
        assertEquals("main", tv.phase)
        assertEquals(1, state.seats[2].hand.sum())
        assertEquals(1, state.seats[0].hand.sum())
        assertTrue(tv.beats.any { it.kind == "steal" && it.other == 2 })
        assertTrue(tv.beats.any { it.kind == "drink" && 2 in it.seats })
    }

    @Test fun timeoutsKeepTheGameMoving() {
        start(3)
        finishSetup()
        rigDice(12)
        passTime(Sprawl.ROLL_MS)
        assertEquals("main", tv.phase)
        passTime(Sprawl.MAIN_MS)
        assertEquals("roll", tv.phase)
        assertEquals(1, tv.turn)
    }

    // ---- building ---------------------------------------------------------------------------

    @Test fun buildingCostsCardsAndNeedsALegalSpot() {
        start(3)
        finishSetup()
        rigDice(12)
        rig { it.hand(0, 2, 2, 1, 1, 0) }
        act(seat(0), "roll")
        val scr = phone(seat(0))
        assertTrue(scr.build.roads.isNotEmpty())
        val bankBefore = state.bank
        val handBefore = state.seats[0].hand
        assertEquals(ActionResult.Rejected("BAD_SPOT"), act(seat(0), "build", "what" to "road", "target" to state.board.eOwner.indexOfFirst { it == 1 }))
        assertEquals(ActionResult.Ack, act(seat(0), "build", "what" to "road", "target" to scr.build.roads.first()))
        assertEquals(SprawlRules.minus(handBefore, SprawlRules.ROAD), state.seats[0].hand)
        assertEquals(SprawlRules.plus(bankBefore, SprawlRules.ROAD), state.bank)
        assertEquals(ActionResult.Rejected("NOT_YOUR_TURN"), act(seat(1), "build", "what" to "road", "target" to 0))
    }

    @Test fun youCantBuildBeforeRolling() {
        start(3)
        finishSetup()
        rig { it.hand(0, 1, 1, 0, 0, 0) }
        assertEquals(ActionResult.Rejected("ROLL_FIRST"), act(seat(0), "build", "what" to "road", "target" to 0))
    }

    @Test fun roadsBuildTowardLongestRoadAndACityCanWin() {
        start(3, "vp" to 8)
        finishSetup()
        rigDice(12)
        rig { it.hand(0, 6, 6, 0, 0, 0) }
        act(seat(0), "roll")
        // Build a planned trail of 5 roads out from one of seat 0's settlements.
        val trail = trailFrom(state.board, 0, 5)
        trail.forEach { assertEquals(ActionResult.Ack, act(seat(0), "build", "what" to "road", "target" to it)) }
        assertTrue(tv.seats[0].longest, "road ${tv.seats[0].road}")
        assertTrue(tv.beats.any { it.kind == "longest" && it.seat == 0 })
        // Longest Road (2) + 2 settlements (2) + 3 VP cards + a city upgrade (+1) = 8: the game ends on the spot.
        rig { it.hand(0, 0, 0, 0, 2, 3).dev(0, "vp", "vp", "vp") }
        act(seat(0), "build", "what" to "city", "target" to phone(seat(0)).build.cities.first())
        assertEquals("tally", tv.phase)
        assertEquals(0, tv.winner)
        assertEquals(8, tv.tally.first { it.seat == 0 }.vp)
        assertEquals(3, tv.tally.first { it.seat == 0 }.vpCards)
    }

    // ---- development cards ------------------------------------------------------------------

    @Test fun aBouncerWaitsADayThenMovesTheLandlord() {
        start(3)
        finishSetup()
        rigDice(12)
        rig { s -> s.hand(0, 0, 0, 1, 1, 1).copy(deck = listOf("knight") + s.deck.filter { it != "knight" }) }
        act(seat(0), "roll")
        assertEquals(ActionResult.Ack, act(seat(0), "buyDev"))
        assertEquals(ActionResult.Rejected("NEW_CARD"), act(seat(0), "play", "card" to "knight"))
        assertFalse(phone(seat(0)).me!!.dev.first { it.kind == "knight" }.playable)
        act(seat(0), "end")
        untilRollOf(0)
        assertTrue(phone(seat(0)).me!!.dev.first { it.kind == "knight" }.playable)
        assertEquals(ActionResult.Ack, act(seat(0), "play", "card" to "knight"))
        assertEquals("robber", tv.phase)
        assertEquals(1, state.seats[0].knights)
    }

    @Test fun oneCardPerTurnAndAWindfallBeforeTheRoll() {
        start(3)
        finishSetup()
        rig { it.dev(0, "plenty", "plenty") }
        val ore = state.seats[0].hand[4]
        assertEquals(ActionResult.Ack, act(seat(0), "play", "card" to "plenty"))
        assertEquals("pick", tv.phase)
        act(seat(0), "pick", "res" to 4)
        act(seat(0), "pick", "res" to 4)
        assertEquals("roll", tv.phase, "Windfall before the roll goes back to the roll")
        assertEquals(ore + 2, state.seats[0].hand[4])
        assertEquals(ActionResult.Rejected("ONE_CARD"), act(seat(0), "play", "card" to "plenty"))
    }

    @Test fun aShakedownTakesEveryonesCardsOfOneKind() {
        start(3)
        finishSetup()
        rig { it.hand(0, 0, 0, 0, 0, 0).hand(1, 0, 0, 3, 0, 0).hand(2, 0, 0, 2, 1, 0).dev(0, "mono") }
        act(seat(0), "play", "card" to "mono")
        act(seat(0), "pick", "res" to 2)
        assertEquals(5, state.seats[0].hand[2])
        assertEquals(0, state.seats[1].hand[2])
        assertEquals(listOf(0, 0, 0, 1, 0), state.seats[2].hand)
    }

    // ---- trades -----------------------------------------------------------------------------

    @Test fun anOfferCanBeCounteredThenAccepted() {
        start(3)
        finishSetup()
        rigDice(12)
        act(seat(0), "roll")
        rig { it.hand(0, 2, 0, 0, 0, 0).hand(1, 0, 0, 0, 2, 0).hand(2, 0, 0, 0, 0, 0) }
        assertEquals(ActionResult.Ack, act(seat(0), "trade", "to" to 1, "give" to listOf(1, 0, 0, 0, 0), "get" to listOf(0, 0, 0, 1, 0)))
        assertEquals("trade", tv.phase)
        assertEquals("trade", phone(seat(1)).prompt.kind)
        assertEquals("wait", phone(seat(2)).prompt.kind)
        val t = state.trade!!.id
        assertEquals(ActionResult.Rejected("NOT_YOUR_TRADE"), act(seat(2), "tradeReply", "trade" to t, "option" to "accept"))
        // Seat 1 wants 2 brick for their wheat.
        assertEquals(ActionResult.Ack, act(seat(1), "trade", "counter" to t.toString(), "give" to listOf(0, 0, 0, 1, 0), "get" to listOf(2, 0, 0, 0, 0)))
        val c = state.trade!!
        assertEquals(1, c.from)
        assertEquals(0, c.to)
        assertEquals(ActionResult.Ack, act(seat(0), "tradeReply", "trade" to c.id, "option" to "accept"))
        assertEquals("main", tv.phase)
        assertEquals(listOf(0, 0, 0, 1, 0), state.seats[0].hand)
        assertEquals(listOf(2, 0, 0, 1, 0), state.seats[1].hand)
    }

    @Test fun anOfferToAnyoneGoesToTheFirstTaker() {
        start(3)
        finishSetup()
        rigDice(12)
        act(seat(0), "roll")
        rig { it.hand(0, 1, 0, 0, 0, 0).hand(1, 0, 1, 0, 0, 0).hand(2, 0, 1, 0, 0, 0) }
        act(seat(0), "trade", "to" to ANYONE, "give" to listOf(1, 0, 0, 0, 0), "get" to listOf(0, 1, 0, 0, 0))
        val t = state.trade!!.id
        act(seat(1), "tradeReply", "trade" to t, "option" to "reject")
        assertEquals("trade", tv.phase, "one pass doesn't end an open offer")
        assertEquals(ActionResult.Ack, act(seat(2), "tradeReply", "trade" to t, "option" to "accept"))
        assertEquals(listOf(0, 1, 0, 0, 0), state.seats[0].hand)
        assertEquals(listOf(1, 0, 0, 0, 0), state.seats[2].hand)
    }

    @Test fun theBankTakesFourOfAKind() {
        start(3)
        finishSetup()
        rigDice(12)
        rig { s -> s.hand(0, 4, 0, 0, 0, 0).copy(board = s.board.copy(harbours = emptyList())) }
        act(seat(0), "roll")
        assertEquals(ActionResult.Ack, act(seat(0), "bank", "give" to 0, "get" to 4))
        assertEquals(listOf(0, 0, 0, 0, 1), state.seats[0].hand)
        assertEquals(ActionResult.Rejected("CANT_AFFORD"), act(seat(0), "bank", "give" to 0, "get" to 4))
    }

    // ---- the end ----------------------------------------------------------------------------

    @Test fun whenTheClockRunsOutEveryoneGetsOneLastTurn() {
        start(3, "minutes" to 30)
        finishSetup()
        rig { it.copy(clockLeftMs = 0) }
        var guard = 0
        var sawLast = false
        while (tv.phase != "tally") {
            check(guard++ < 500) { "never tallied" }
            sawLast = sawLast || tv.lastRound
            passTime(5_000)
        }
        assertTrue(sawLast)
        assertTrue(tv.winner >= 0)
    }

    @Test fun aKickedPlayersTurnIsSkipped() {
        start(3)
        finishSetup()
        e.kick(seat(0))
        assertEquals(1, tv.turn)
        assertTrue(tv.seats[0].gone)
        assertEquals(List(5) { 19 }, total(state))
        e.kick(seat(1))
        assertEquals("tally", tv.phase)
    }

    @Test fun thePhoneShowsYourHandAndTheIsland() {
        start(4)
        val scr = phone(seat(0))
        assertEquals(19, scr.map.hexes.size)
        assertEquals(54, scr.map.vertices.size)
        assertEquals(4, scr.colors.size)
        assertEquals("setup", scr.prompt.kind)
        assertTrue(scr.prompt.timed)
        assertEquals("wait", phone(seat(1)).prompt.kind)
        act(seat(0), "peek", "what" to "vertex", "target" to scr.spots.first())
        assertEquals(scr.spots.first(), tv.peek)
    }

    // ---- fixes from review ------------------------------------------------------------------

    @Test fun aWindfallNeedsSomethingInTheBankAndItsTimeoutNeverJams() {
        start(3)
        finishSetup()
        rig { it.dev(0, "plenty").copy(bank = List(5) { 0 }) }
        assertEquals(ActionResult.Rejected("BANK_EMPTY"), act(seat(0), "play", "card" to "plenty"))
        // The bank runs dry after the first pick: the timeout goes back to the roll instead of throwing.
        rig { it.copy(bank = listOf(1, 0, 0, 0, 0)) }
        act(seat(0), "play", "card" to "plenty")
        act(seat(0), "pick", "res" to 0)
        assertEquals("roll", tv.phase)
        rig { it.copy(bank = List(5) { 0 }, phase = "pick", pick = "plenty", picked = -1, resume = "roll") }
        passTime(Sprawl.ROLL_MS)
        assertEquals("roll", tv.phase)
    }

    @Test fun buildingClearsTheSpotTheTvIsRinging() {
        start(3)
        finishSetup()
        rigDice(12)
        rig { it.hand(0, 1, 1, 0, 0, 0) }
        act(seat(0), "roll")
        val spot = phone(seat(0)).build.roads.first()
        act(seat(0), "peek", "what" to "edge", "target" to spot)
        assertEquals(spot, tv.peek)
        act(seat(0), "build", "what" to "road", "target" to spot)
        assertEquals(-1, tv.peek)
        // And a phone backing out of build mode clears it too.
        act(seat(0), "peek", "what" to "edge", "target" to 0)
        act(seat(0), "peek")
        assertEquals(-1, tv.peek)
    }

    @Test fun seatsThatLeftDontCountTowardAShortage() {
        start(3)
        finishSetup()
        e.kick(seat(1))
        // Seat 0 and the departed seat 1 share the only ore hex; the bank has one ore left.
        rig { s ->
            val b = s.board
            val g = b.geo
            val h = b.terrain.indexOfFirst { it == 4 }
            val (v0, v1) = g.hexVertices[h][0] to g.hexVertices[h][3]
            val vOwner = List(g.vertices.size) { when (it) { v0 -> 0; v1 -> 1; else -> -1 } }
            s.copy(
                board = b.copy(numbers = b.numbers.mapIndexed { i, n -> if (i == h) 5 else if (n == 5) 0 else n }, robber = b.terrain.indexOf(5),
                    vOwner = vOwner, vLevel = vOwner.map { if (it >= 0) 1 else 0 }),
                bank = listOf(19, 19, 19, 19, 1), seats = s.seats.map { it.copy(hand = List(5) { 0 }) },
            )
        }
        rigDice(5)
        act(seat(0), "roll")
        assertEquals(1, state.seats[0].hand[4])
    }

    @Test fun aKickLeavingNobodyToAnswerAnOfferClosesIt() {
        start(3)
        finishSetup()
        rigDice(12)
        act(seat(0), "roll")
        rig { it.hand(0, 1, 0, 0, 0, 0).hand(1, 0, 1, 0, 0, 0).hand(2, 0, 1, 0, 0, 0) }
        act(seat(0), "trade", "to" to ANYONE, "give" to listOf(1, 0, 0, 0, 0), "get" to listOf(0, 1, 0, 0, 0))
        act(seat(1), "tradeReply", "trade" to state.trade!!.id, "option" to "reject")
        e.kick(seat(2))
        assertEquals("main", tv.phase)
        assertEquals(null, state.trade)
    }

    @Test fun aRollerWhoLeavesDuringTheDiscardDoesntRobAnyone() {
        start(4)
        finishSetup()
        rigDice(7)
        rig { it.hand(1, 4, 4, 0, 0, 0) }
        act(seat(0), "roll")
        assertEquals("discard", tv.phase)
        e.kick(seat(0))
        act(seat(1), "discard", "cards" to listOf(2, 2, 0, 0, 0))
        assertEquals("roll", tv.phase)
        assertEquals(1, tv.turn)
        assertFalse(tv.beats.any { it.kind == "steal" })
    }
}
