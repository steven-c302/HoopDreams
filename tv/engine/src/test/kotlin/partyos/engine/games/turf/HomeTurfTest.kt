package partyos.engine.games.turf

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
import partyos.engine.TurfTv
import partyos.engine.add
import partyos.engine.games.turf.HomeTurf.Companion.DEAL_MS
import partyos.engine.games.turf.HomeTurf.Companion.HOP_MS
import partyos.engine.games.turf.HomeTurf.Companion.MOVE_PAD_MS
import partyos.engine.games.turf.HomeTurf.Companion.PIECES_MS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ROLL_MS_FOR_TEST = HomeTurf.ROLL_MS + 10_000

class HomeTurfTest {
    private val clock = FakeClock(0)
    private val registry = GameRegistry(listOf(HomeTurf()))
    private lateinit var e: PartyEngine
    private var ids: List<PlayerId> = emptyList()
    private var n = 0

    private fun start(names: List<String>, vararg opts: Pair<String, Int>): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(3), registry)
        ids = names.map { e.add(it) }
        e.host(HostCmd.SetOption("pace", 1)) // Quick: the old MOVE dwell, so the existing timings hold; tests that need Theatre pass "pace" to 0
        opts.forEach { (k, v) -> e.host(HostCmd.SetOption(k, v)) }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("turf")))
        e.host(HostCmd.SkipPhase) // tutorial
        return ids
    }

    private val tv get() = e.tvState().stage!!.game as TurfTv
    private val seq get() = e.tvState().stage!!.phaseSeq
    private val remaining get() = e.tvState().stage!!.remainingMs
    private val state: TurfState get() = Json.decodeFromJsonElement(TurfState.serializer(), e.snapshot().game!!.state!!)
    private fun phone(who: PlayerId) = assertIs<Screen.Turf>(e.phoneState(who).screen)
    private fun seatOf(t: Int) = state.tokens[t].seat!!
    private fun passTime(ms: Long) { clock.advance(ms); e.tick() }
    private fun moveMs(hops: Int) = hops * HOP_MS + MOVE_PAD_MS

    private fun act(who: PlayerId, kind: String, vararg kv: Pair<String, Any>, round: Int = seq): ActionResult =
        e.action(who, "a${n++}", round, buildJsonObject {
            put("kind", JsonPrimitive(kind))
            kv.forEach { (k, v) ->
                when (v) {
                    is Int -> put(k, JsonPrimitive(v))
                    is List<*> -> put(k, JsonArray(v.map { JsonPrimitive(it.toString()) }))
                    else -> put(k, JsonPrimitive(v.toString()))
                }
            }
        })

    private fun skipSetup() {
        if (tv.phase == "teamup") e.host(HostCmd.SkipPhase)
        passTime(PIECES_MS)
        assertEquals("deal", tv.phase)
        passTime(DEAL_MS)
        assertEquals("roll", tv.phase)
    }

    /** Rewrites the game state through a snapshot and restore, then brings everyone back and resumes. */
    private fun rig(f: (TurfState) -> TurfState) {
        val snap = e.snapshot()
        val gs = snap.game!!
        val changed = gs.copy(state = Json.encodeToJsonElement(TurfState.serializer(), f(state)))
        e = PartyEngine.restore(snap.copy(game = changed), clock, SeededEntropy(1), registry)
        ids.forEach { e.setPresence(it, true) }
        assertEquals(ActionResult.Ack, e.host(HostCmd.Resume))
    }

    /** Points the dice at the next roll that satisfies [want] (speed die as the current token would roll it). */
    private fun rigRoll(want: (Roll) -> Boolean) = rig { s ->
        val speed = s.tokens[s.turn].passedPayday && !s.tokens[s.turn].jailed && s.phase != "jail"
        s.copy(draws = (s.draws until s.draws + 200_000).first { want(TurfDice.roll(s.rngSeed, it, speed)) })
    }

    private fun TurfState.clean() = copy(
        estate = Estate(), doubles = 0, debts = emptyList(),
        tokens = tokens.map { it.copy(cash = 1500, pos = 0, passedPayday = false, jailed = false, jailCards = emptyList()) },
    )
    private fun TurfState.owning(vararg spaces: Pair<Int, Int>) =
        copy(estate = estate.copy(owner = estate.owner.toMutableList().also { o -> spaces.forEach { (sp, t) -> o[sp] = t } }))
    private fun TurfState.at(t: Int, pos: Int) = copy(tokens = tokens.mapIndexed { i, k -> if (i == t) k.copy(pos = pos) else k })
    private fun TurfState.cashOf(t: Int, cash: Int) = copy(tokens = tokens.mapIndexed { i, k -> if (i == t) k.copy(cash = cash) else k })

    // ---- setup ----------------------------------------------------------------------------

    @Test fun soloSetupPiecesThenStarterPlaces() {
        val (a, b, c) = start(listOf("Ava", "Ben", "Cleo"))
        assertEquals("pieces", tv.phase)
        assertFalse(tv.teams)
        assertEquals(3, tv.tokens.size)
        assertEquals(6, phone(a).pieces.size)
        assertEquals(ActionResult.Ack, act(a, "piece", "option" to "duck"))
        assertEquals(ActionResult.Rejected("PIECE_TAKEN"), act(b, "piece", "option" to "duck"))
        assertEquals(ActionResult.Rejected("ALREADY_PICKED"), act(a, "piece", "option" to "cup"))
        act(b, "piece", "option" to "cup")
        act(c, "piece", "option" to "pizza")
        assertEquals("deal", tv.phase, "everyone picked, so the deal starts early")
        tv.tokens.forEachIndexed { i, t ->
            val mine = tv.owner.indices.filter { tv.owner[it] == i }
            assertEquals(2, mine.size)
            assertEquals(1500 - mine.sumOf { TurfBoard.spaces[it].price }, t.cash)
        }
        passTime(DEAL_MS)
        assertEquals("roll", tv.phase)
        val first = seatOf(state.turn)
        assertEquals("roll", phone(first).prompt.kind)
        assertTrue(phone(first).prompt.timed)
        assertEquals(45 * 60_000L, state.clockLeftMs, "setup doesn't use up the game clock")
    }

    @Test fun piecesNobodyPicksAreHandedOut() {
        start(listOf("Ava", "Ben"))
        passTime(PIECES_MS)
        assertTrue(tv.tokens.all { it.piece != null })
        assertEquals(2, tv.tokens.map { it.piece }.toSet().size)
    }

    // ---- turns ----------------------------------------------------------------------------

    @Test fun rollMoveBuyEndTurn() {
        start(listOf("Ava", "Ben")); skipSetup()
        rig { it.clean() }
        rigRoll { !it.doubles && it.move == 3 }
        val t = state.turn
        val who = seatOf(t)
        val other = 1 - t
        assertEquals(ActionResult.Rejected("NOT_YOUR_TURN"), act(seatOf(other), "roll"))
        assertEquals(ActionResult.Ack, act(who, "roll"))
        assertEquals("move", tv.phase)
        assertEquals(3, tv.tokens[t].pos)
        assertEquals(listOf(1, 2, 3), state.beats.last { it.kind == "move" }.path)
        assertFalse(tv.timed, "moving is an animation, not a decision")
        passTime(moveMs(3))
        assertEquals("buy", tv.phase)
        assertEquals(3, tv.buy)
        val p = phone(who).prompt
        assertEquals("buy", p.kind)
        assertEquals(listOf("buy", "pass"), p.actions.map { it.id })
        assertEquals("wait", phone(seatOf(other)).prompt.kind)
        act(who, "buy", "option" to "buy")
        assertEquals(t, tv.owner[3])
        assertEquals(1440, tv.tokens[t].cash)
        assertEquals("manage", tv.phase)
        assertEquals(ActionResult.Ack, act(who, "end"))
        assertEquals("roll", tv.phase)
        assertEquals(other, tv.turn)
    }

    // ---- pacing (the TypeScript timeline test carries the same table) ---------------------------

    @Test fun moveDwellTableMatchesTheTvTimeline() {
        assertEquals(2180L, HomeTurf.moveDwellMs(3, diced = true, quick = true))
        assertEquals(3220L, HomeTurf.moveDwellMs(7, diced = false, quick = true))
        assertEquals(5100L, HomeTurf.moveDwellMs(3, diced = true, quick = false))
        assertEquals(6020L, HomeTurf.moveDwellMs(7, diced = true, quick = false))
        assertEquals(3420L, HomeTurf.moveDwellMs(7, diced = false, quick = false))
        assertEquals(1720L, HomeTurf.moveDwellMs(1, diced = false, quick = false))
        assertEquals(listOf(230L, 230L, 230L, 230L, 320L, 460L, 820L), (0 until 7).map { HomeTurf.hopMs(it, 7, false) })
        assertEquals(listOf(260L, 260L, 260L), (0 until 3).map { HomeTurf.hopMs(it, 3, true) })
    }

    @Test fun paceOptionIsAcceptedAndShownToTheTv() {
        start(listOf("Ava", "Ben"), "pace" to 0); skipSetup()
        assertFalse(tv.quick)
        assertEquals(ActionResult.Ack, e.host(HostCmd.SetOption("pace", 1)))
    }

    @Test fun quickPaceKeepsTheOldMoveDwell() {
        start(listOf("Ava", "Ben")); skipSetup()
        rig { it.clean() }
        rigRoll { !it.doubles && it.move == 3 }
        act(seatOf(state.turn), "roll")
        assertTrue(tv.quick)
        assertEquals("move", tv.phase)
        assertEquals(moveMs(3), remaining)
    }

    @Test fun theatrePaceHoldsTheMoveForTheDiceAndTheSlowedWalk() {
        start(listOf("Ava", "Ben"), "pace" to 0); skipSetup()
        rig { it.clean() }
        rigRoll { !it.doubles && it.move == 3 }
        act(seatOf(state.turn), "roll")
        assertFalse(tv.quick)
        assertEquals("move", tv.phase)
        assertEquals(5_100L, remaining)
        passTime(5_099)
        assertEquals("move", tv.phase)
        passTime(1)
        assertNotEquals("move", tv.phase)
    }

    @Test fun doublesRollAgainAndThreeInARowIsTimeout() {
        start(listOf("Ava", "Ben")); skipSetup()
        rig { it.clean() }
        rigRoll { it.doubles && it.a == 3 } // 3+3 lands on space 6, which nobody owns
        val t = state.turn
        act(seatOf(t), "roll")
        passTime(moveMs(6))
        act(seatOf(t), "buy", "option" to "pass")
        passTime(10_000) // nobody bids
        assertEquals("roll", tv.phase, "doubles: roll again")
        assertEquals(t, tv.turn)
        assertEquals(1, tv.doubles)
        rig { it.copy(doubles = 2) }
        rigRoll { it.doubles }
        act(seatOf(t), "roll")
        assertTrue(tv.tokens[t].jailed)
        assertEquals(TurfBoard.JAIL, tv.tokens[t].pos)
        assertEquals("manage", tv.phase, "the turn is over, but they can still build and trade")
        val drink = state.beats.last { it.kind == "drink" }
        assertEquals(listOf(t), drink.tokens)
        assertEquals(HomeTurf.JAIL_SIPS, drink.sips)
    }

    @Test fun passingPaydayPaysAndTurnsOnTheSpeedDie() {
        start(listOf("Ava", "Ben")); skipSetup()
        rig { s -> s.clean().at(s.turn, 36) }
        rigRoll { !it.doubles && it.move == 5 }
        val t = state.turn
        act(seatOf(t), "roll")
        assertEquals(1, tv.tokens[t].pos)
        assertEquals(1700, tv.tokens[t].cash)
        assertTrue(state.tokens[t].passedPayday)
        passTime(moveMs(5))
        rig { s -> s.copy(phase = "roll", doubles = 0) }
        rigRoll { it.speed in 1..3 && !it.doubles }
        act(seatOf(t), "roll")
        assertEquals(3, tv.dice.size, "the speed die joins once you've passed Payday")
    }

    @Test fun speedDieBusScoutAndTriples() {
        start(listOf("Ava", "Ben")); skipSetup()
        rig { s -> s.clean().copy(tokens = s.tokens.map { it.copy(cash = 1500, pos = 0, passedPayday = true) }) }
        rigRoll { it.bus && !it.doubles && it.a == 1 }
        val t = state.turn
        act(seatOf(t), "roll")
        assertEquals("choose", tv.phase)
        assertEquals("bus", phone(seatOf(t)).prompt.kind)
        act(seatOf(t), "choose", "option" to "a")
        assertEquals(1, tv.tokens[t].pos, "bus: moved by one die")

        rig { s -> s.clean().copy(phase = "roll", turn = t, tokens = s.tokens.map { it.copy(cash = 1500, pos = 0, passedPayday = true) }) }
        rigRoll { it.triples }
        act(seatOf(t), "roll")
        assertEquals("triples", phone(seatOf(t)).prompt.kind)
        assertEquals(40, phone(seatOf(t)).prompt.actions.size)
        act(seatOf(t), "choose", "option" to "39")
        assertEquals(39, tv.tokens[t].pos)
        passTime(moveMs(39))
        assertEquals("buy", tv.phase)

        // Scout: land, then jump to the next place nobody owns.
        rig { s -> s.clean().copy(phase = "roll", turn = t, tokens = s.tokens.map { it.copy(cash = 1500, pos = 0, passedPayday = true) })
            .owning(1 to t, 3 to t, 5 to t, 6 to t, 8 to t, 9 to t) }
        rigRoll { it.scout && !it.doubles && it.move == 3 }
        act(seatOf(t), "roll")
        passTime(moveMs(3))
        assertEquals("move", tv.phase, "the scout moves them on")
        assertEquals(11, tv.tokens[t].pos, "past their own places to the next one nobody owns")
    }

    // ---- auctions and rent ----------------------------------------------------------------

    @Test fun passingStartsALiveAuction() {
        start(listOf("Ava", "Ben", "Cleo")); skipSetup()
        rig { it.clean() }
        rigRoll { !it.doubles && it.move == 3 }
        val t = state.turn
        val who = seatOf(t)
        act(who, "roll"); passTime(moveMs(3))
        act(who, "buy", "option" to "pass")
        assertEquals("auction", tv.phase)
        val auction = tv.auction!!.id
        val others = (0 until 3).filter { it != t }
        val b1 = seatOf(others[0])
        val b2 = seatOf(others[1])
        val round = seq
        assertEquals(ActionResult.Ack, act(b1, "bid", "auction" to auction, "amount" to 20))
        assertEquals(round, seq, "a bid resets the clock without a new round")
        assertEquals(HomeTurf.BID_MS, remaining)
        assertEquals(ActionResult.Rejected("BID_TOO_LOW"), act(b2, "bid", "auction" to auction, "amount" to 20))
        assertEquals(ActionResult.Ack, act(b2, "bid", "auction" to auction, "amount" to 70))
        assertEquals(ActionResult.Rejected("CANT_AFFORD"), act(who, "bid", "auction" to auction, "amount" to 5000))
        assertEquals("bid", phone(b1).prompt.kind)
        assertFalse(phone(b1).auction!!.leading)
        assertTrue(phone(b2).auction!!.leading)
        passTime(HomeTurf.BID_MS)
        assertEquals(others[1], tv.owner[3])
        assertEquals(1430, tv.tokens[others[1]].cash)
        assertEquals("manage", tv.phase)
        assertEquals(ActionResult.Rejected("STALE"), act(b1, "bid", "auction" to auction, "amount" to 90, round = round), "the hammer fell")
    }

    @Test fun rentDoublesOnASetAndTheRenterDrinks() {
        start(listOf("Ava", "Ben")); skipSetup()
        rig { s -> s.clean().owning(1 to 1 - s.turn, 3 to 1 - s.turn) }
        rigRoll { !it.doubles && it.move == 3 }
        val t = state.turn
        val o = 1 - t
        act(seatOf(t), "roll"); passTime(moveMs(3))
        assertEquals(1500 - 8, tv.tokens[t].cash)
        assertEquals(1500 + 8, tv.tokens[o].cash)
        val drink = state.beats.last { it.kind == "drink" }
        assertEquals(listOf(t), drink.tokens)
        assertEquals(1, drink.sips)
        assertEquals("Drink 1 sip: paid rent at The Laundromat", phone(seatOf(t)).drink)
        assertNull(phone(seatOf(o)).drink)
        assertEquals("manage", tv.phase)
    }

    @Test fun drinkCallsCanBeOff() {
        start(listOf("Ava", "Ben"), "drinks" to 0); skipSetup()
        rig { s -> s.clean().owning(1 to 1 - s.turn, 3 to 1 - s.turn) }
        rigRoll { !it.doubles && it.move == 3 }
        act(seatOf(state.turn), "roll"); passTime(moveMs(3))
        assertTrue(state.beats.none { it.kind == "drink" })
        assertFalse(tv.drinks)
    }

    // ---- Timeout --------------------------------------------------------------------------

    private fun jailed(t: Int, f: (TurfState) -> TurfState = { it }) = rig { s ->
        f(s.clean().copy(phase = "jail", turn = t).let { c -> c.copy(tokens = c.tokens.mapIndexed { i, k -> if (i == t) k.copy(pos = 10, jailed = true) else k }) })
    }

    @Test fun timeoutPayOrCardThenRoll() {
        start(listOf("Ava", "Ben")); skipSetup()
        val t = state.turn
        jailed(t)
        val jail = phone(seatOf(t)).prompt
        assertEquals("jail", jail.kind)
        assertEquals(listOf("pay", "roll"), jail.actions.map { it.id })
        act(seatOf(t), "jail", "option" to "pay")
        assertEquals("roll", tv.phase)
        assertEquals(1450, tv.tokens[t].cash)
        assertFalse(tv.tokens[t].jailed)

        jailed(t) { s -> s.copy(chance = s.chance - 8, tokens = s.tokens.mapIndexed { i, k -> if (i == t) k.copy(jailCards = listOf("chance")) else k }) }
        assertEquals(listOf("pay", "card", "roll"), phone(seatOf(t)).prompt.actions.map { it.id })
        act(seatOf(t), "jail", "option" to "card")
        assertFalse(tv.tokens[t].jailed)
        assertEquals(0, tv.tokens[t].jailCards)
        assertEquals(8, state.chance.last(), "the card goes back under the pile")
    }

    @Test fun timeoutDoublesWalkOutWithoutAnotherRoll() {
        start(listOf("Ava", "Ben")); skipSetup()
        val t = state.turn
        jailed(t) { it.owning(14 to t) }
        rigRoll { it.doubles && it.a == 2 }
        act(seatOf(t), "jail", "option" to "roll")
        assertFalse(tv.tokens[t].jailed)
        assertEquals(14, tv.tokens[t].pos)
        passTime(moveMs(4))
        assertEquals("manage", tv.phase, "doubles out of Timeout don't roll again")
        assertEquals(1500, tv.tokens[t].cash)
    }

    @Test fun timeoutMissPaysAndMovesAnyway() {
        start(listOf("Ava", "Ben")); skipSetup()
        val t = state.turn
        jailed(t) { it.owning(15 to t) }
        rigRoll { !it.doubles && it.move == 5 }
        act(seatOf(t), "jail", "option" to "roll")
        assertEquals(1450, tv.tokens[t].cash)
        assertEquals(15, tv.tokens[t].pos)
        assertFalse(tv.tokens[t].jailed)
    }

    // ---- cards ----------------------------------------------------------------------------

    @Test fun cardsShowThenApply() {
        start(listOf("Ava", "Ben")); skipSetup()
        // Plot Twist "go back 3" from space 7 lands on Split the Bill ($200).
        rig { s -> s.clean().at(s.turn, 4).copy(chance = listOf(9) + (s.chance - 9)) }
        rigRoll { !it.doubles && it.move == 3 }
        val t = state.turn
        act(seatOf(t), "roll"); passTime(moveMs(3))
        assertEquals("card", tv.phase)
        assertEquals("Plot Twist", tv.card!!.deckName)
        assertEquals("Wrong ride. Go back 3 spaces.", tv.card!!.text)
        assertEquals("card", phone(seatOf(1 - t)).prompt.kind)
        passTime(HomeTurf.CARD_MS)
        assertEquals(4, tv.tokens[t].pos)
        passTime(moveMs(3))
        assertEquals(1300, tv.tokens[t].cash)
        assertEquals(9, state.chance.last(), "the card goes to the bottom")
    }

    @Test fun birthdayCollectsFromEveryone() {
        start(listOf("Ava", "Ben", "Cleo")); skipSetup()
        rig { s -> s.clean().at(s.turn, 14).copy(chest = listOf(8) + (s.chest - 8)) }
        rigRoll { !it.doubles && it.move == 3 }
        val t = state.turn
        act(seatOf(t), "roll"); passTime(moveMs(3))
        assertEquals("It's your birthday! Collect \$10 from everyone. Everyone else drinks 1 sip.", tv.card!!.text)
        passTime(HomeTurf.CARD_MS)
        assertEquals(1520, tv.tokens[t].cash)
        (0 until 3).filter { it != t }.forEach { assertEquals(1490, tv.tokens[it].cash) }
    }

    // ---- debt and bankruptcy ----------------------------------------------------------------

    @Test fun debtCanBeCoveredByMortgaging() {
        start(listOf("Ava", "Ben", "Cleo")); skipSetup()
        rig { s -> s.clean().cashOf(s.turn, 10).owning(5 to (s.turn + 1) % 3, 3 to s.turn) }
        rigRoll { !it.doubles && it.move == 5 }
        val t = state.turn
        val o = (t + 1) % 3
        act(seatOf(t), "roll"); passTime(moveMs(5))
        assertEquals("debt", tv.phase)
        val debt = phone(seatOf(t)).prompt
        assertEquals("debt", debt.kind)
        assertEquals(25, debt.amount)
        assertEquals(listOf("bankrupt"), debt.actions.map { it.id }, "can't pay yet")
        assertEquals(30, phone(seatOf(t)).deeds.single().mortgage)
        assertEquals(ActionResult.Rejected("CANT_AFFORD"), act(seatOf(t), "pay"))
        act(seatOf(t), "mortgage", "target" to 3)
        assertEquals(40, tv.tokens[t].cash)
        act(seatOf(t), "pay")
        assertEquals(15, tv.tokens[t].cash)
        assertEquals(1525, tv.tokens[o].cash)
        assertEquals("manage", tv.phase)
    }

    @Test fun bankruptcyToAPlayerHandsOverEverythingAndTheLastOneStandingWins() {
        start(listOf("Ava", "Ben")); skipSetup()
        rig { s -> s.clean().cashOf(s.turn, 10).owning(5 to 1 - s.turn, 9 to s.turn).let { c -> c.copy(estate = c.estate.copy(mortgaged = c.estate.mortgaged.toMutableList().also { it[9] = true })) } }
        rigRoll { !it.doubles && it.move == 5 }
        val t = state.turn
        val o = 1 - t
        act(seatOf(t), "roll"); passTime(moveMs(5))
        passTime(HomeTurf.DEBT_MS) // time's up: sell off, still short, bankrupt
        assertTrue(tv.tokens[t].bankrupt)
        assertEquals(o, tv.owner[9], "the creditor takes their places")
        assertTrue(9 in tv.mortgaged, "a mortgaged place stays mortgaged")
        assertEquals(1500 + 10, tv.tokens[o].cash)
        assertEquals("tally", tv.phase)
        assertEquals("Bankrupt", phone(seatOf(t)).prompt.title)
        passTime(HomeTurf.TALLY_MS)
        assertEquals("podium", tv.phase)
        passTime(HomeTurf.PODIUM_MS)
        assertNull(e.tvState().stage)
        assertEquals("turf", e.tvState().lastResult!!.gameId)
        assertTrue(e.tvState().lastResult!!.standings.first().score > 1500)
    }

    // ---- building ---------------------------------------------------------------------------

    @Test fun buildingOnYourTurnWithASet() {
        start(listOf("Ava", "Ben")); skipSetup()
        rig { s -> s.clean().owning(1 to s.turn, 3 to s.turn).copy(phase = "manage") }
        val t = state.turn
        val deeds = phone(seatOf(t)).deeds
        assertEquals(listOf(50, 50), deeds.map { it.build })
        assertTrue(deeds.all { it.set })
        act(seatOf(t), "build", "target" to 1)
        assertEquals(ActionResult.Rejected("BUILD_EVENLY"), act(seatOf(t), "build", "target" to 1))
        act(seatOf(t), "build", "target" to 3)
        assertEquals(listOf(1, 1), listOf(tv.level[1], tv.level[3]))
        assertEquals(1400, tv.tokens[t].cash)
        assertEquals(30, tv.housesLeft)
        assertEquals(ActionResult.Rejected("NOT_YOUR_TURN"), act(seatOf(1 - t), "build", "target" to 1))
    }

    // ---- trades -----------------------------------------------------------------------------

    @Test fun tradesFreezeTheTurnAndSwapEverything() {
        start(listOf("Ava", "Ben", "Cleo")); skipSetup()
        rig { s -> s.clean().owning(1 to s.turn, 3 to (s.turn + 1) % 3) }
        val t = state.turn
        val o = (t + 1) % 3
        val oldRound = seq
        // The partner drafts an offer on their phone while the turn moves on; it isn't refused as stale.
        rigRoll { !it.doubles && it.move == 8 }
        act(seatOf(t), "roll")
        assertNotEquals(oldRound, seq)
        val r = act(seatOf(o), "trade", "to" to t, "give" to listOf(3), "get" to listOf(1), "giveCash" to 50, round = oldRound)
        assertEquals(ActionResult.Rejected("TRADE_LATER"), r, "not while the dice are moving")
        passTime(moveMs(8))
        act(seatOf(t), "buy", "option" to "pass"); passTime(10_000)
        assertEquals("manage", tv.phase)
        assertEquals(ActionResult.Ack, act(seatOf(o), "trade", "to" to t, "give" to listOf(3), "get" to listOf(1), "giveCash" to 50, round = oldRound))
        assertEquals("trade", tv.phase)
        assertEquals("trade", phone(seatOf(t)).prompt.kind)
        assertEquals("wait", phone(seatOf(o)).prompt.kind)
        assertEquals(ActionResult.Rejected("TRADE_OPEN"), act(seatOf(t), "end"))
        act(seatOf(t), "tradeReply", "trade" to tv.trade!!.id, "option" to "accept")
        assertEquals(t, tv.owner[3])
        assertEquals(o, tv.owner[1])
        assertEquals(1550, tv.tokens[t].cash)
        assertEquals(1450, tv.tokens[o].cash)
        assertEquals("manage", tv.phase, "the turn picks up where it left off")
        assertTrue(remaining!! >= HomeTurf.TRADE_RESUME_MS)
        assertEquals(ActionResult.Rejected("ONE_OFFER"), act(seatOf(o), "trade", "to" to t, "giveCash" to 5))
    }

    @Test fun counterOffersAndRejections() {
        start(listOf("Ava", "Ben")); skipSetup()
        rig { s -> s.clean().owning(1 to s.turn, 3 to 1 - s.turn).copy(phase = "manage") }
        val t = state.turn
        val o = 1 - t
        act(seatOf(t), "trade", "to" to o, "give" to listOf(1), "get" to listOf(3))
        val first = tv.trade!!.id
        val round = seq
        assertTrue(phone(seatOf(o)).trade!!.canCounter)
        act(seatOf(o), "trade", "to" to t, "give" to listOf(3), "get" to listOf(1), "getCash" to 100, "counter" to first)
        assertEquals(round, seq, "a counter keeps the round")
        assertEquals(1, tv.trade!!.counters)
        assertEquals(o, tv.trade!!.from)
        act(seatOf(t), "tradeReply", "trade" to tv.trade!!.id, "option" to "reject")
        assertNull(tv.trade)
        assertEquals(t, tv.owner[1])
        assertEquals("manage", tv.phase)
    }

    @Test fun anUnansweredTradeExpires() {
        start(listOf("Ava", "Ben")); skipSetup()
        rig { s -> s.clean().owning(1 to s.turn).copy(phase = "manage") }
        val t = state.turn
        act(seatOf(t), "trade", "to" to 1 - t, "give" to listOf(1), "getCash" to 10)
        passTime(HomeTurf.TRADE_MS)
        assertNull(tv.trade)
        assertEquals("manage", tv.phase)
        assertEquals(t, tv.owner[1])
    }

    // ---- teams ------------------------------------------------------------------------------

    @Test fun teamsShareATokenAndTheDicePassAround() {
        start(listOf("Ava", "Ben", "Cleo", "Dev", "Eli", "Fay"), "turfMode" to 2, "teams" to 2)
        assertEquals("teamup", tv.phase)
        assertTrue(tv.teams)
        assertEquals(listOf(3, 3), tv.tokens.map { it.members.size })
        assertEquals(ActionResult.Ack, e.host(HostCmd.GameAction("shuffle")))
        assertEquals(listOf(3, 3), tv.tokens.map { it.members.size })
        e.host(HostCmd.SkipPhase)
        assertEquals("pieces", tv.phase)
        val team0 = state.tokens[0].members
        assertEquals(ActionResult.Ack, act(team0[2], "piece", "option" to "boombox"), "any teammate can grab the piece")
        passTime(PIECES_MS); passTime(DEAL_MS)
        val t = state.turn
        val seat = seatOf(t)
        val mate = state.tokens[t].members.first { it != seat }
        assertEquals(ActionResult.Rejected("NOT_YOUR_SEAT"), act(mate, "roll"))
        assertEquals("watch", phone(mate).prompt.kind)
        rig { it.copy(phase = "manage") }
        val holder = seatOf(t) // a restore reconnects everyone, which can hand the dice to a teammate
        act(holder, "end")
        val members = state.tokens[t].members
        assertEquals(members[(members.indexOf(holder) + 1) % members.size], state.tokens[t].seat, "the dice pass to the next teammate")
    }

    @Test fun tooManyForSoloBecomesTeams() {
        start((1..8).map { "P$it" }, "turfMode" to 1)
        assertTrue(tv.teams)
        assertEquals("Too many for solo, so it's teams", tv.notice)
        assertEquals(3, tv.tokens.size)
    }

    // ---- presence, kicks and the clock --------------------------------------------------------

    @Test fun anAbsentPlayerIsPutOnAutopilot() {
        start(listOf("Ava", "Ben", "Cleo")); skipSetup()
        val seat = seatOf(state.turn)
        e.setPresence(seat, false)
        assertEquals(HomeTurf.AUTOPILOT_MS, remaining)
        e.setPresence(seat, true)
        assertEquals(HomeTurf.RECONNECT_MS, remaining)
        e.setPresence(seat, false)
        passTime(HomeTurf.AUTOPILOT_MS)
        assertNotEquals("roll", tv.phase, "the dice rolled themselves")
    }

    @Test fun aKickedSoloPlayerRetiresAndTheirPlacesGoBack() {
        start(listOf("Ava", "Ben", "Cleo")); skipSetup()
        rig { s -> s.clean().owning(1 to s.turn, 3 to s.turn).let { c -> c.copy(estate = c.estate.copy(level = c.estate.level.toMutableList().also { it[1] = 2; it[3] = 2 }, housesLeft = 28)) } }
        val t = state.turn
        e.kick(seatOf(t))
        assertTrue(tv.tokens[t].bankrupt)
        assertEquals(-1, tv.owner[1])
        assertEquals(0, tv.level[1])
        assertEquals(32, tv.housesLeft)
        assertNotEquals(t, tv.turn, "their turn ended")
        assertEquals("roll", tv.phase)
    }

    @Test fun aKickDuringSomeoneElsesTradeEndsTheRetiredTokensTurn() {
        start(listOf("Ava", "Ben", "Cleo", "Dev")); skipSetup()
        rig { s -> s.clean().owning(1 to (s.turn + 1) % 4, 3 to (s.turn + 2) % 4) }
        val t = state.turn
        val (b, c) = (t + 1) % 4 to (t + 2) % 4
        assertEquals("roll", tv.phase)
        // Two other tokens trade while it's t's roll; then t's only player is kicked.
        assertEquals(ActionResult.Ack, act(seatOf(b), "trade", "to" to c, "give" to listOf(1), "get" to listOf(3)))
        assertEquals("trade", tv.phase)
        e.kick(seatOf(t))
        assertTrue(tv.tokens[t].bankrupt)
        assertEquals("trade", tv.phase, "the other two can still finish their deal")
        act(seatOf(c), "tradeReply", "trade" to tv.trade!!.id, "option" to "reject")
        assertNotEquals(t, tv.turn, "the retired token doesn't get its roll back")
        assertEquals("roll", tv.phase)
        val before = tv.tokens[t].pos
        passTime(ROLL_MS_FOR_TEST)
        assertEquals(before, tv.tokens[t].pos, "and never moves again")
        assertEquals(1, tv.tokens.count { it.bankrupt })
    }

    @Test fun whenTheClockRunsOutEveryoneFinishesTheLap() {
        start(listOf("Ava", "Ben", "Cleo")); skipSetup()
        rig { s -> s.clean().cashOf(0, 1600).cashOf(2, 1400).copy(turn = 1, phase = "manage", clockLeftMs = 0) }
        act(seatOf(1), "end")
        assertTrue(tv.lastLap)
        assertEquals(2, tv.turn, "the last token in the order still gets a turn")
        rig { it.copy(phase = "manage") }
        act(seatOf(2), "end")
        assertEquals("tally", tv.phase)
        assertEquals(3, tv.tally.size)
        assertEquals(1, tv.tally.count { it.rank == 1 })
    }

    @Test fun theGameClockSkipsPauses() {
        start(listOf("Ava", "Ben", "Cleo")); skipSetup()
        val before = state.clockLeftMs!!
        passTime(4_000)
        e.host(HostCmd.Pause)
        passTime(600_000)
        e.host(HostCmd.Resume)
        rigRoll { !it.doubles && it.move == 7 } // restore also pauses and resumes
        act(seatOf(state.turn), "roll")
        assertTrue(before - state.clockLeftMs!! in 4_000..20_000, "only unpaused turn time counts (${before - state.clockLeftMs!!} ms)")
    }

    // ---- snapshots and payloads -----------------------------------------------------------------

    @Test fun aGameSurvivesASnapshotRoundTrip() {
        start(listOf("Ava", "Ben", "Cleo")); skipSetup()
        rigRoll { !it.doubles }
        act(seatOf(state.turn), "roll")
        val before = state
        rig { it }
        assertEquals(before.copy(phaseMs = state.phaseMs, clockLeftMs = state.clockLeftMs), state)
    }

    @Test fun payloadsStaySmall() {
        start((1..6).map { "Player$it" }); skipSetup()
        rig { s -> s.owning(*TurfBoard.deeds.mapIndexed { i, d -> d to i % 6 }.toTypedArray()) }
        val json = Json { encodeDefaults = true }
        val tvBytes = json.encodeToString(partyos.engine.TvState.serializer(), e.tvState()).length
        val phoneBytes = json.encodeToString(partyos.engine.PhoneState.serializer(), e.phoneState(ids[0])).length
        assertTrue(tvBytes < 24_000, "TV payload $tvBytes bytes")
        assertTrue(phoneBytes < 12_000, "phone payload $phoneBytes bytes")
    }
}
