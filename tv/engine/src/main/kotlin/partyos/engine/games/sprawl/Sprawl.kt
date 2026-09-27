package partyos.engine.games.sprawl

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import partyos.engine.Effect
import partyos.engine.GameContext
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.LateJoin
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.Reject
import partyos.engine.Screen
import partyos.engine.SprawlHarbourTv
import partyos.engine.SprawlHexTv
import partyos.engine.SprawlMapTv
import partyos.engine.SprawlSeatTv
import partyos.engine.SprawlTallyTv
import partyos.engine.SprawlTradeTv
import partyos.engine.SprawlTv
import partyos.engine.Step
import partyos.engine.TutorialCard
import partyos.engine.games.trivia.BrainDrain
import kotlin.random.Random

private typealias SStep = Step<SprawlState>

/**
 * Sprawl: the base island game at party speed, on hexes named after the crew's places. 3–4 players get the
 * 19-hex island, 5–6 the 30-hex one. Official rules plus turn timers, a game clock that ends in a last round, an 8 VP
 * target by default, trades on phones and drink calls. See docs/superpowers/specs/2026-09-27-sprawl-design.md.
 */
class Sprawl(private val names: SprawlNames = SprawlSetup.loadNames()) : GameModule<SprawlState> {
    override val info = GameInfo(
        id = GAME_ID,
        title = "Sprawl",
        tagline = "Build out from your friends' places. Block everyone else.",
        minPlayers = 3,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Settle the crew's places", "Put settlements on corners. When a hex's number is rolled, every settlement touching it collects."),
            TutorialCard("Build, trade, block", "Roads, settlements and cities cost cards. Trade on your phone. Roll a 7 and The Landlord robs someone."),
            TutorialCard("Race to the target", "Settlements are 1 point, cities 2, Longest Road and Most Bouncers 2 each. First to the target on the TV wins. Out of time? One last round."),
        ),
        lateJoin = LateJoin.ANYTIME,
    )
    override val stateSerializer = SprawlState.serializer()

    // ---- setup ------------------------------------------------------------------------

    override fun start(ctx: GameContext): SStep {
        val seed = ctx.random.nextLong()
        var draws = 0
        fun rnd() = random(seed, draws++)
        val players = ctx.players.shuffled(rnd()).take(MAX_SEATS)
        val size = if (players.size > 4) 1 else 0
        val seats = players.mapIndexed { i, p -> SSeat(p.id, p.name, KIT[i].first) }
        val minutes = ctx.settings["minutes"] ?: DEFAULT_MINUTES
        val s = SprawlState(
            phase = SETUP, board = SprawlSetup.deal(size, rnd(), names), seats = seats,
            bank = SprawlSetup.bank(size), deck = SprawlSetup.deck(size, rnd()),
            clockLeftMs = if (minutes > 0) minutes * 60_000L else null,
            vpTarget = (ctx.settings["vp"] ?: DEFAULT_VP).coerceIn(MIN_VP, MAX_VP),
            drinks = (ctx.settings["drinks"] ?: 1) != 0,
            rngSeed = seed, draws = draws, discards = List(seats.size) { 0 },
        )
        return go(s.log("${seats.size} players, ${s.vpTarget} points to win").beat("setup", seat = 0), SETUP, ctx)
    }

    private fun random(seed: Long, draw: Int) = Random(PartyEngine.phaseSeed(seed, draw))
    private fun SprawlState.rng(): Pair<Random, SprawlState> = random(rngSeed, draws) to copy(draws = draws + 1)

    /** The seat making setup pick [step]: in order for the first settlement, in reverse for the second. */
    private fun setupSeat(s: SprawlState, step: Int = s.setupStep): Int {
        val n = s.seats.size
        val pick = step / 2
        return if (pick < n) pick else 2 * n - 1 - pick
    }
    private fun setupPiece(s: SprawlState) = if (s.setupStep % 2 == 0) SETTLEMENT else ROAD
    private fun setupDone(s: SprawlState) = s.setupStep >= s.seats.size * 4

    // ---- the clock --------------------------------------------------------------------

    /** Charges the game clock for time spent in the current phase (pauses don't count). */
    private fun account(s: SprawlState, ctx: GameContext): SprawlState {
        val planned = s.phaseMs ?: return s
        val left = ctx.remainingMs ?: return s
        val spent = (planned - left).coerceAtLeast(0)
        val counts = s.phase != TALLY && s.phase != PODIUM
        return s.copy(phaseMs = left, clockLeftMs = if (counts) s.clockLeftMs?.let { it - spent } else s.clockLeftMs)
    }

    private fun go(s: SprawlState, phase: String, ctx: GameContext, ms: Long = dur(s, phase, ctx)) =
        SStep(s.copy(phase = phase, phaseMs = ms, peek = -1, peekKind = null), listOf<Effect>(Effect.Phase(ms)))

    private fun reset(s: SprawlState, ms: Long) = SStep(s.copy(phaseMs = ms), listOf<Effect>(Effect.Deadline(ms)))

    private fun dur(s: SprawlState, phase: String, ctx: GameContext): Long = when (phase) {
        SETUP -> if (away(s, setupSeat(s), ctx)) AUTOPILOT_MS else SETUP_MS
        ROLL -> auto(s, ctx, ROLL_MS)
        MAIN -> auto(s, ctx, MAIN_MS)
        ROBBER -> auto(s, ctx, ROBBER_MS)
        STEAL -> auto(s, ctx, STEAL_MS)
        ROAD2 -> auto(s, ctx, ROAD2_MS)
        PICK -> auto(s, ctx, PICK_MS)
        DISCARD -> DISCARD_MS
        TRADE -> TRADE_MS
        TALLY -> TALLY_MS
        else -> PODIUM_MS
    }

    private fun auto(s: SprawlState, ctx: GameContext, ms: Long) = if (away(s, s.turn, ctx)) AUTOPILOT_MS else ms
    private fun away(s: SprawlState, seat: Int, ctx: GameContext) = s.seats.getOrNull(seat)?.let { it.gone || !ctx.isConnected(it.player) } ?: true

    // ---- engine hooks -----------------------------------------------------------------

    override fun onAction(s0: SprawlState, who: PlayerId, payload: JsonObject, ctx: GameContext): SStep {
        val s = account(s0, ctx)
        val kind = payload.str("kind") ?: throw Reject("BAD_ACTION")
        val me = s.seats.indexOfFirst { it.player == who && !it.gone }
        if (me < 0) throw Reject("NOT_PLAYING")
        return when (kind) {
            "trade" -> propose(s, me, payload, ctx)
            "tradeReply" -> reply(s, me, payload, ctx)
            "tradeCancel" -> cancelTrade(s, me, payload, ctx)
            "discard" -> discard(s, me, payload.ints("cards"), ctx)
            "peek" -> peek(s, me, payload)
            else -> {
                if (s.trade != null) throw Reject("TRADE_OPEN")
                // A real move ends the eyeing: the TV stops ringing the spot.
                val moved = s.copy(peek = -1, peekKind = null)
                if (moved.phase == SETUP) {
                    if (kind != "place") throw Reject("NOT_NOW")
                    if (me != setupSeat(moved)) throw Reject("NOT_YOUR_TURN")
                    return setupPlace(moved, payload.int("target"), ctx)
                }
                if (me != moved.turn) throw Reject("NOT_YOUR_TURN")
                turnAction(moved, me, kind, payload, ctx)
            }
        }
    }

    override fun onDeadline(s0: SprawlState, ctx: GameContext): SStep {
        val s = account(s0, ctx)
        return when (s.phase) {
            SETUP -> setupPlace(s, autoSetup(s), ctx)
            ROLL -> roll(s, ctx)
            MAIN -> endTurn(s, ctx)
            DISCARD -> {
                var n = s
                n.discards.forEachIndexed { i, owed -> if (owed > 0) n = applyDiscard(n, i, SprawlRules.autoDiscard(n.seats[i].hand, owed)) }
                afterDiscards(n, ctx)
            }
            ROBBER -> moveRobber(s, SprawlRules.autoRobber(s.board, s.turn), ctx)
            STEAL -> {
                val (r, n) = s.rng()
                val vs = SprawlRules.victims(n.board, n.board.robber, n.turn, n.seats.map { it.hand })
                if (vs.isEmpty()) back(n, ctx) else steal(n, vs[r.nextInt(vs.size)], ctx)
            }
            ROAD2 -> {
                val spot = SprawlRules.roadSpots(s.board, s.turn).firstOrNull()
                if (spot == null) back(s.copy(freeRoads = 0), ctx) else freeRoad(s, spot, ctx)
            }
            PICK -> if (s.pick == PLENTY && s.bank.all { it == 0 }) back(s, ctx) else pick(s, autoPick(s), ctx)
            TRADE -> closeTrade(s, "expired", ctx)
            TALLY -> go(s, PODIUM, ctx)
            else -> SStep(s, listOf(Effect.Finish))
        }
    }

    override fun onPresence(s0: SprawlState, who: PlayerId, present: Boolean, ctx: GameContext): SStep {
        val s = account(s0, ctx)
        val seat = s.seats.indexOfFirst { it.player == who }
        if (seat < 0) return SStep(s)
        if (ctx.player(who) == null) return leave(s, seat, ctx)
        val active = if (s.phase == SETUP) setupSeat(s) else s.turn
        if (seat == active && s.phase in AUTO_PHASES) {
            val left = ctx.remainingMs ?: return SStep(s)
            if (!present && left > AUTOPILOT_MS) return reset(s, AUTOPILOT_MS)
            if (present && left < RECONNECT_MS) return reset(s, RECONNECT_MS)
        }
        return SStep(s)
    }

    override fun waitingOn(s: SprawlState): Set<PlayerId>? = null

    override fun phaseFree(payload: JsonObject) = payload.str("kind") in PHASE_FREE

    // ---- setup picks ------------------------------------------------------------------

    private fun autoSetup(s: SprawlState): Int = if (setupPiece(s) == SETTLEMENT) SprawlRules.bestOpening(s.board, setupSeat(s))
        else SprawlRules.roadSpots(s.board, setupSeat(s), from = s.setupVertex).first()

    private fun setupPlace(s0: SprawlState, target: Int, ctx: GameContext): SStep {
        val seat = setupSeat(s0)
        val round2 = s0.setupStep / 2 >= s0.seats.size
        var s = s0
        if (setupPiece(s) == SETTLEMENT) {
            if (target !in SprawlRules.settlementSpots(s.board, seat, setup = true)) throw Reject("BAD_SPOT")
            s = s.settle(target, seat, SprawlRules.SETTLEMENT_LEVEL).copy(setupVertex = target).beat("settle", seat = seat, target = target)
            if (round2) {
                val gain = MutableList(5) { 0 }
                s.board.geo.vHexes[target].filter { s.board.terrain[it] != SprawlRules.DESERT }.forEach { gain[s.board.terrain[it]]++ }
                s = s.pay(seat, gain).beat("harvest", seat = seat, gains = List(s.seats.size) { if (it == seat) gain else List(5) { 0 } })
            }
        } else {
            if (target !in SprawlRules.roadSpots(s.board, seat, from = s.setupVertex)) throw Reject("BAD_SPOT")
            s = s.road(target, seat).beat("road", seat = seat, target = target)
        }
        s = s.copy(setupStep = s.setupStep + 1)
        if (!setupDone(s)) return go(s.copy(turn = setupSeat(s)).beat("setup", seat = setupSeat(s)), SETUP, ctx)
        return beginTurn(s.copy(setupVertex = -1).log("Setup done. Roll 'em!"), 0, ctx)
    }

    // ---- turns ------------------------------------------------------------------------

    private fun beginTurn(s0: SprawlState, t: Int, ctx: GameContext): SStep {
        val s = s0.copy(
            turn = t, rolled = false, devPlayed = false, dice = emptyList(), offers = 0, turnBeat = s0.beatSeq, resume = "", resumeMs = null,
            seats = s0.seats.map { it.copy(fresh = emptyList()) },
        ).beat("turn", seat = t)
        // Points picked up on someone else's turn (an award changing hands) win at the start of your own.
        if (vp(s, t) >= s.vpTarget) return win(s, t, ctx)
        return go(s, ROLL, ctx)
    }

    private fun turnAction(s: SprawlState, me: Int, kind: String, p: JsonObject, ctx: GameContext): SStep = when (kind) {
        "roll" -> if (s.phase == ROLL) roll(s, ctx) else throw Reject("NOT_NOW")
        "build" -> build(s, me, p.str("what") ?: throw Reject("BAD_ACTION"), p.int("target"), ctx)
        "buyDev" -> buyDev(s, me, ctx)
        "play" -> play(s, me, p.str("card") ?: throw Reject("BAD_ACTION"), ctx)
        "place" -> if (s.phase == ROAD2) freeRoad(s, p.int("target"), ctx) else throw Reject("NOT_NOW")
        "robber" -> if (s.phase == ROBBER) moveRobber(s, p.int("target"), ctx) else throw Reject("NOT_NOW")
        "steal" -> {
            if (s.phase != STEAL) throw Reject("NOT_NOW")
            val v = p.int("victim")
            if (v !in SprawlRules.victims(s.board, s.board.robber, s.turn, s.seats.map { it.hand })) throw Reject("BAD_OPTION")
            steal(s, v, ctx)
        }
        "pick" -> if (s.phase == PICK) pick(s, p.int("res"), ctx) else throw Reject("NOT_NOW")
        "bank" -> bankTrade(s, me, p.int("give"), p.int("get"), ctx)
        "end" -> if (s.phase == MAIN) endTurn(s, ctx) else throw Reject("NOT_NOW")
        else -> throw Reject("BAD_ACTION")
    }

    private fun roll(s0: SprawlState, ctx: GameContext): SStep {
        val (r, s1) = s0.rng()
        val dice = listOf(r.nextInt(1, 7), r.nextInt(1, 7))
        val sum = dice.sum()
        var s = s1.copy(dice = dice, rolled = true).beat("roll", seat = s1.turn, dice = dice, amount = sum)
        if (sum == 7) {
            val owed = s.seats.map { if (it.gone) 0 else SprawlRules.discardCount(it.hand) }
            s = s.copy(discards = owed, resume = MAIN, resumeMs = null).log("${s.seats[s.turn].name} rolled a 7! ${names.landlord} is coming")
            return go(s, if (owed.any { it > 0 }) DISCARD else ROBBER, ctx)
        }
        // Seats that left keep their pieces as blockers but collect nothing, so they don't count toward a shortage either.
        val paying = s.board.copy(vOwner = s.board.vOwner.map { if (it >= 0 && s.seats[it].gone) SprawlRules.NOBODY else it })
        val gains = SprawlRules.production(paying, sum, s.bank, s.seats.size)
        gains.forEachIndexed { i, g -> if (g.any { it > 0 }) s = s.pay(i, g) }
        val hexes = s.board.numbers.indices.filter { s.board.numbers[it] == sum && it != s.board.robber }
        s = s.beat("harvest", seat = s.turn, amount = sum, targets = hexes, gains = gains)
        if (gains.all { g -> g.all { it == 0 } }) s = s.log("${s.seats[s.turn].name} rolled $sum. Nobody collects")
        return go(s, MAIN, ctx)
    }

    /** Back from a side phase (the Landlord, Road Trip, a pick) to the roll or the main phase it interrupted. */
    private fun back(s: SprawlState, ctx: GameContext): SStep {
        val to = if (s.resume == ROLL) ROLL else MAIN
        val ms = if (to == MAIN) s.resumeMs?.let { maxOf(it, RESUME_FLOOR_MS) } else null
        val next = s.copy(resume = "", resumeMs = null, pick = null, picked = -1, freeRoads = 0)
        return if (ms != null) go(next, to, ctx, if (away(s, s.turn, ctx)) AUTOPILOT_MS else ms) else go(next, to, ctx)
    }

    private fun endTurn(s0: SprawlState, ctx: GameContext): SStep {
        var s = s0.copy(trade = null)
        if (active(s).size < 2) return tally(s, ctx)
        if (!s.lastRound && (s.clockLeftMs ?: 1) <= 0) s = s.copy(lastRound = true).beat("lastround").log("Time's up! Last round.")
        val next = nextActive(s, s.turn)
        if (s.lastRound && next <= s.turn) return tally(s, ctx)
        return beginTurn(s, next, ctx)
    }

    private fun nextActive(s: SprawlState, from: Int) = (1..s.seats.size).map { (from + it) % s.seats.size }.first { !s.seats[it].gone }
    private fun active(s: SprawlState) = s.seats.indices.filter { !s.seats[it].gone }

    // ---- building ---------------------------------------------------------------------

    private fun build(s0: SprawlState, me: Int, what: String, target: Int, ctx: GameContext): SStep {
        if (s0.phase != MAIN) throw Reject("ROLL_FIRST")
        val b = s0.board
        val hand = s0.seats[me].hand
        val name = s0.seats[me].name
        val s = when (what) {
            ROAD -> {
                if (SprawlRules.roads(b, me) >= SprawlRules.MAX_ROADS) throw Reject("NO_PIECES")
                if (!SprawlRules.affords(hand, SprawlRules.ROAD)) throw Reject("CANT_AFFORD")
                if (target !in SprawlRules.roadSpots(b, me)) throw Reject("BAD_SPOT")
                awards(s0.spend(me, SprawlRules.ROAD).road(target, me).beat("road", seat = me, target = target))
            }
            SETTLEMENT -> {
                if (SprawlRules.settlements(b, me) >= SprawlRules.MAX_SETTLEMENTS) throw Reject("NO_PIECES")
                if (!SprawlRules.affords(hand, SprawlRules.SETTLEMENT)) throw Reject("CANT_AFFORD")
                if (target !in SprawlRules.settlementSpots(b, me, setup = false)) throw Reject("BAD_SPOT")
                awards(s0.spend(me, SprawlRules.SETTLEMENT).settle(target, me, SprawlRules.SETTLEMENT_LEVEL)
                    .beat("settle", seat = me, target = target).log("$name settled ${cornerName(b, target)}"))
            }
            CITY -> {
                if (SprawlRules.cities(b, me) >= SprawlRules.MAX_CITIES) throw Reject("NO_PIECES")
                if (!SprawlRules.affords(hand, SprawlRules.CITY)) throw Reject("CANT_AFFORD")
                if (target !in SprawlRules.citySpots(b, me)) throw Reject("BAD_SPOT")
                s0.spend(me, SprawlRules.CITY).settle(target, me, SprawlRules.CITY_LEVEL).beat("city", seat = me, target = target)
                    .log("$name built a city").drink(others(s0, me), CITY_SIPS, "$name built a city")
            }
            else -> throw Reject("BAD_ACTION")
        }
        return afterOwnMove(s, me, ctx)
    }

    /** A corner by the richest place it touches, for the ticker. */
    private fun cornerName(b: SBoard, v: Int) = b.geo.vHexes[v].maxByOrNull { SprawlRules.pips(b.numbers[it]) }?.let { "by ${b.names[it]}" } ?: "a corner"

    /** After the active seat gains something: win on the spot, or keep a few seconds on the clock for slow thumbs. */
    private fun afterOwnMove(s: SprawlState, me: Int, ctx: GameContext): SStep {
        if (me == s.turn && vp(s, me) >= s.vpTarget) return win(s, me, ctx)
        val left = ctx.remainingMs
        return if (s.phase == MAIN && left != null && left < MAIN_FLOOR_MS) reset(s, MAIN_FLOOR_MS) else SStep(s)
    }

    /** Re-checks Longest Road and Most Bouncers after the board or an army changed. */
    private fun awards(s0: SprawlState): SprawlState {
        var s = s0
        val lengths = s.seats.indices.map { if (s.seats[it].gone) 0 else SprawlRules.longestRoad(s.board, it) }
        val road = SprawlRules.holder(s.roadHolder, lengths, SprawlRules.ROAD_AWARD)
        if (road != s.roadHolder) s = award(s, "longest", road, s.roadHolder, names.awards["road"] ?: "Longest Road").copy(roadHolder = road)
        val knights = s.seats.map { if (it.gone) 0 else it.knights }
        val army = SprawlRules.holder(s.armyHolder, knights, SprawlRules.ARMY_AWARD)
        if (army != s.armyHolder) s = award(s, "army", army, s.armyHolder, names.awards["army"] ?: "Most Bouncers").copy(armyHolder = army)
        return s
    }

    private fun award(s: SprawlState, kind: String, to: Int, from: Int, title: String): SprawlState {
        var n = s.beat(kind, seat = to, other = from)
        n = if (to >= 0) n.log("${title.uppercase()}: ${n.seats[to].name}") else n.log("Nobody holds $title now")
        if (from >= 0 && from != to) n = n.drink(listOf(from), LOSE_AWARD_SIPS, "lost $title")
        return n
    }

    private fun buyDev(s0: SprawlState, me: Int, ctx: GameContext): SStep {
        if (s0.phase != MAIN) throw Reject("ROLL_FIRST")
        if (s0.deck.isEmpty()) throw Reject("DECK_EMPTY")
        if (!SprawlRules.affords(s0.seats[me].hand, SprawlRules.DEV)) throw Reject("CANT_AFFORD")
        val card = s0.deck.first()
        val s = s0.spend(me, SprawlRules.DEV).copy(deck = s0.deck.drop(1))
            .seat(me) { it.copy(dev = it.dev + card, fresh = it.fresh + card) }
            .beat("buyDev", seat = me).log("${s0.seats[me].name} bought a card")
        return afterOwnMove(s, me, ctx)
    }

    // ---- development cards ---------------------------------------------------------------

    /** Whether seat [me] could play [card] right now (for the phone's PLAY buttons). */
    fun canPlay(s: SprawlState, me: Int, card: String): Boolean {
        val seat = s.seats[me]
        return card in PLAYABLE && !s.devPlayed && me == s.turn && s.phase in PLAY_PHASES && s.trade == null &&
            seat.dev.count { it == card } > seat.fresh.count { it == card }
    }

    private fun play(s0: SprawlState, me: Int, card: String, ctx: GameContext): SStep {
        if (card !in PLAYABLE) throw Reject("BAD_ACTION")
        if (card == PLENTY && s0.bank.all { it == 0 }) throw Reject("BANK_EMPTY")
        if (s0.devPlayed) throw Reject("ONE_CARD")
        if (s0.phase !in PLAY_PHASES) throw Reject("NOT_NOW")
        val seat = s0.seats[me]
        if (seat.dev.count { it == card } == 0) throw Reject("NO_CARD")
        if (seat.dev.count { it == card } <= seat.fresh.count { it == card }) throw Reject("NEW_CARD")
        var s = s0.seat(me) { it.copy(dev = it.dev - card) }.copy(devPlayed = true, resume = s0.phase, resumeMs = ctx.remainingMs)
            .beat("play", seat = me, text = card).log("${seat.name} played ${names.devName(card)}")
        return when (card) {
            KNIGHT -> {
                s = awards(s.seat(me) { it.copy(knights = it.knights + 1) })
                if (vp(s, me) >= s.vpTarget) win(s, me, ctx) else go(s, ROBBER, ctx)
            }
            ROAD_TRIP -> {
                val n = minOf(2, SprawlRules.MAX_ROADS - SprawlRules.roads(s.board, me))
                if (n <= 0 || SprawlRules.roadSpots(s.board, me).isEmpty()) back(s, ctx) else go(s.copy(freeRoads = n), ROAD2, ctx)
            }
            else -> go(s.copy(pick = card, picked = -1), PICK, ctx)
        }
    }

    private fun freeRoad(s0: SprawlState, target: Int, ctx: GameContext): SStep {
        val me = s0.turn
        if (target !in SprawlRules.roadSpots(s0.board, me)) throw Reject("BAD_SPOT")
        val s = awards(s0.road(target, me).copy(freeRoads = s0.freeRoads - 1).beat("road", seat = me, target = target))
        if (vp(s, me) >= s.vpTarget) return win(s, me, ctx)
        val more = s.freeRoads > 0 && SprawlRules.roads(s.board, me) < SprawlRules.MAX_ROADS && SprawlRules.roadSpots(s.board, me).isNotEmpty()
        return if (more) go(s, ROAD2, ctx) else back(s, ctx)
    }

    /** A timed-out pick: Shakedown takes what the others hold most of; Windfall your scarcest card the bank still has. */
    private fun autoPick(s: SprawlState): Int {
        val hand = s.seats[s.turn].hand
        return if (s.pick == MONO) {
            (0 until 5).maxByOrNull { r -> s.seats.indices.filter { it != s.turn }.sumOf { s.seats[it].hand[r] } }!!
        } else (0 until 5).filter { s.bank[it] > 0 }.minByOrNull { hand[it] } ?: 0
    }

    private fun pick(s0: SprawlState, res: Int, ctx: GameContext): SStep {
        if (res !in 0 until 5) throw Reject("BAD_OPTION")
        val me = s0.turn
        val rn = names.res(res)
        if (s0.pick == MONO) {
            var s = s0
            var took = 0
            val robbed = mutableListOf<Int>()
            s.seats.indices.filter { it != me && s.seats[it].hand[res] > 0 }.forEach { o ->
                took += s.seats[o].hand[res]
                robbed += o
                s = s.seat(o) { it.copy(hand = it.hand.toMutableList().also { h -> h[res] = 0 }) }
            }
            s = s.seat(me) { it.copy(hand = it.hand.toMutableList().also { h -> h[res] += took }) }
                .beat("mono", seat = me, amount = took, target = res, seats = robbed)
                .log("${s.seats[me].name}'s ${names.devName(MONO)}: all the $rn ($took)")
                .drink(robbed, MONO_SIPS, "${names.devName(MONO)} took your $rn")
            return back(s, ctx)
        }
        if (s0.bank[res] <= 0) throw Reject("BANK_EMPTY")
        val s = s0.pay(me, List(5) { if (it == res) 1 else 0 }).beat("plenty", seat = me, target = res)
        return if (s0.picked < 0 && s.bank.any { it > 0 }) SStep(s.copy(picked = res))
        else back(s.log("${s.seats[me].name} took 2 from the bank"), ctx)
    }

    // ---- sevens: discard, move the Landlord, steal --------------------------------------

    private fun discard(s0: SprawlState, me: Int, cards: List<Int>, ctx: GameContext): SStep {
        if (s0.phase != DISCARD) throw Reject("NOT_NOW")
        val owed = s0.discards.getOrElse(me) { 0 }
        if (owed == 0) throw Reject("NOTHING_OWED")
        if (cards.size != 5 || cards.any { it < 0 } || cards.sum() != owed || !SprawlRules.affords(s0.seats[me].hand, cards)) throw Reject("BAD_DISCARD")
        val s = applyDiscard(s0, me, cards)
        return if (s.discards.all { it == 0 }) afterDiscards(s, ctx) else SStep(s)
    }

    /** Everyone has discarded: the roller moves the Landlord, unless they've left the island (then their turn ends). */
    private fun afterDiscards(s: SprawlState, ctx: GameContext): SStep = if (s.seats[s.turn].gone) endTurn(s, ctx) else go(s, ROBBER, ctx)

    private fun applyDiscard(s: SprawlState, me: Int, cards: List<Int>): SprawlState =
        s.spend(me, cards).copy(discards = s.discards.toMutableList().also { it[me] = 0 })
            .beat("discard", seat = me, amount = cards.sum()).drink(listOf(me), DISCARD_SIPS, "discarded on a 7")

    private fun moveRobber(s0: SprawlState, hex: Int, ctx: GameContext): SStep {
        if (hex !in s0.board.geo.hexes.indices || hex == s0.board.robber) throw Reject("BAD_SPOT")
        val s = s0.copy(board = s0.board.copy(robber = hex)).beat("robber", seat = s0.turn, target = hex)
            .log("${names.landlord} moves in at ${s0.board.names[hex]}")
        val vs = SprawlRules.victims(s.board, hex, s.turn, s.seats.map { it.hand })
        return when (vs.size) {
            0 -> back(s, ctx)
            1 -> steal(s, vs[0], ctx)
            else -> go(s, STEAL, ctx)
        }
    }

    private fun steal(s0: SprawlState, victim: Int, ctx: GameContext): SStep {
        val (r, s1) = s0.rng()
        val pile = s1.seats[victim].hand.flatMapIndexed { res, n -> List(n) { res } }
        if (pile.isEmpty()) return back(s1, ctx)
        val one = List(5) { if (it == pile[r.nextInt(pile.size)]) 1 else 0 }
        val thief = s1.turn
        val s = s1.seat(victim) { it.copy(hand = SprawlRules.minus(it.hand, one)) }.seat(thief) { it.copy(hand = SprawlRules.plus(it.hand, one)) }
            .beat("steal", seat = thief, other = victim).log("${s1.seats[thief].name} robbed ${s1.seats[victim].name}")
            .drink(listOf(victim), STEAL_SIPS, "got robbed")
        return back(s, ctx)
    }

    // ---- trades -----------------------------------------------------------------------

    private fun bankTrade(s0: SprawlState, me: Int, give: Int, get: Int, ctx: GameContext): SStep {
        if (s0.phase != MAIN) throw Reject("ROLL_FIRST")
        if (give !in 0 until 5 || get !in 0 until 5 || give == get) throw Reject("BAD_TRADE")
        val rate = SprawlRules.ratios(s0.board, me)[give]
        if (s0.seats[me].hand[give] < rate) throw Reject("CANT_AFFORD")
        if (s0.bank[get] <= 0) throw Reject("BANK_EMPTY")
        val s = s0.spend(me, List(5) { if (it == give) rate else 0 }).pay(me, List(5) { if (it == get) 1 else 0 })
            .beat("bank", seat = me, target = get, amount = rate).log("${s0.seats[me].name} traded $rate ${names.res(give)} for 1 ${names.res(get)}")
        return afterOwnMove(s, me, ctx)
    }

    private fun counts(p: JsonObject, k: String): List<Int> {
        val v = p.ints(k)
        if (v.isEmpty()) return List(5) { 0 }
        if (v.size != 5 || v.any { it < 0 || it > 99 }) throw Reject("BAD_TRADE")
        return v
    }

    private fun propose(s: SprawlState, me: Int, p: JsonObject, ctx: GameContext): SStep {
        val give = counts(p, "give")
        val get = counts(p, "get")
        if (give.sum() == 0 && get.sum() == 0) throw Reject("EMPTY_TRADE")
        if (give.sum() == 0 || get.sum() == 0) throw Reject("NO_GIFTS")
        if (give.indices.any { give[it] > 0 && get[it] > 0 }) throw Reject("BAD_TRADE")
        if (!SprawlRules.affords(s.seats[me].hand, give)) throw Reject("CANT_AFFORD")
        val counterId = p.str("counter")?.toIntOrNull()
        val open = s.trade
        if (counterId != null) {
            if (open == null || open.id != counterId || s.phase != TRADE || !canAccept(s, open, me)) throw Reject("TRADE_GONE")
            if (open.counters >= MAX_COUNTERS) throw Reject("NO_MORE_COUNTERS")
            val t = STrade(s.serial + 1, me, open.from, give, get, open.counters + 1, open.frozenPhase, open.frozenMs)
            return reset(s.copy(trade = t, serial = t.id).beat("counter", seat = me, other = open.from).log("${s.seats[me].name} countered"), TRADE_MS)
        }
        if (open != null) throw Reject("TRADE_OPEN")
        if (me != s.turn) throw Reject("NOT_YOUR_TURN")
        if (s.phase != MAIN) throw Reject("ROLL_FIRST")
        if (s.offers >= MAX_OFFERS) throw Reject("TOO_MANY_OFFERS")
        val to = p.int("to")
        if (to != ANYONE && (to !in s.seats.indices || to == me || s.seats[to].gone)) throw Reject("BAD_TRADE")
        val t = STrade(s.serial + 1, me, to, give, get, 0, s.phase, ctx.remainingMs)
        val who = if (to == ANYONE) "anyone" else s.seats[to].name
        return go(s.copy(trade = t, serial = t.id, offers = s.offers + 1).beat("offer", seat = me, other = to).log("${s.seats[me].name} wants a deal with $who"), TRADE, ctx)
    }

    /** [me] may answer [t]: it's to them, or to anyone and they haven't passed (never your own offer). */
    fun canAccept(s: SprawlState, t: STrade, me: Int) =
        me != t.from && !s.seats[me].gone && (t.to == me || (t.to == ANYONE && me !in t.passed))

    private fun reply(s: SprawlState, me: Int, p: JsonObject, ctx: GameContext): SStep {
        val t = s.trade ?: throw Reject("TRADE_GONE")
        if (p.int("trade") != t.id || s.phase != TRADE) throw Reject("TRADE_GONE")
        if (!canAccept(s, t, me)) throw Reject("NOT_YOUR_TRADE")
        return when (p.str("option")) {
            "accept" -> {
                if (!SprawlRules.affords(s.seats[me].hand, t.get)) throw Reject("CANT_AFFORD")
                if (!SprawlRules.affords(s.seats[t.from].hand, t.give)) throw Reject("THEY_CANT_AFFORD")
                val done = s.seat(t.from) { it.copy(hand = SprawlRules.plus(SprawlRules.minus(it.hand, t.give), t.get)) }
                    .seat(me) { it.copy(hand = SprawlRules.plus(SprawlRules.minus(it.hand, t.get), t.give)) }
                    .beat("traded", seat = t.from, other = me).log("DEAL! ${s.seats[t.from].name} and ${s.seats[me].name} traded")
                closeTrade(done, "accepted", ctx)
            }
            "reject" -> {
                if (t.to != ANYONE) return closeTrade(s, "rejected", ctx)
                val passed = t.passed + me
                val left = active(s).filter { it != t.from && it !in passed }
                if (left.isEmpty()) closeTrade(s.copy(trade = t.copy(passed = passed)), "rejected", ctx)
                else SStep(s.copy(trade = t.copy(passed = passed)).beat("pass", seat = me))
            }
            else -> throw Reject("BAD_OPTION")
        }
    }

    private fun cancelTrade(s: SprawlState, me: Int, p: JsonObject, ctx: GameContext): SStep {
        val t = s.trade ?: throw Reject("TRADE_GONE")
        if (p.int("trade") != t.id || s.phase != TRADE) throw Reject("TRADE_GONE")
        if (t.from != me) throw Reject("NOT_YOUR_TRADE")
        return closeTrade(s, "cancelled", ctx)
    }

    /** Closes the open trade and gives the frozen turn its time back (at least a few seconds). */
    private fun closeTrade(s0: SprawlState, how: String, ctx: GameContext): SStep {
        val t = s0.trade ?: return SStep(s0)
        var s = s0.copy(trade = null)
        if (how != "accepted") s = s.beat(how, seat = t.from, other = t.to)
        val ms = maxOf(t.frozenMs ?: TRADE_RESUME_MS, TRADE_RESUME_MS)
        return go(s, t.frozenPhase.ifEmpty { MAIN }, ctx, if (away(s, s.turn, ctx)) minOf(ms, AUTOPILOT_MS) else ms)
    }

    private fun peek(s: SprawlState, me: Int, p: JsonObject): SStep {
        val active = if (s.phase == SETUP) setupSeat(s) else s.turn
        if (me != active) throw Reject("NOT_YOUR_TURN")
        val what = p.str("what")?.takeIf { it in PEEK_KINDS }
        return SStep(s.copy(peek = if (what == null) -1 else p.intOr("target", -1), peekKind = what))
    }

    // ---- someone leaves -----------------------------------------------------------------

    /** A kicked player's seat is out: their cards go back to the bank and their pieces stay as blockers. */
    private fun leave(s0: SprawlState, seat: Int, ctx: GameContext): SStep {
        if (s0.seats[seat].gone || s0.phase == TALLY || s0.phase == PODIUM) return SStep(s0)
        val hand = s0.seats[seat].hand
        var s = s0.seat(seat) { it.copy(gone = true, hand = List(5) { 0 }, dev = emptyList(), fresh = emptyList()) }
            .copy(bank = SprawlRules.plus(s0.bank, hand), discards = s0.discards.toMutableList().also { if (seat in it.indices) it[seat] = 0 })
            .beat("left", seat = seat).log("${s0.seats[seat].name} left the island")
        s = awards(s)
        if (active(s).size < 2) return tally(s, ctx)
        val t = s.trade
        val nobodyLeft = t != null && t.to == ANYONE && active(s).none { canAccept(s, t, it) }
        if (t != null && (t.from == seat || t.to == seat || nobodyLeft)) {
            val closed = closeTrade(s, if (nobodyLeft && t.from != seat) "rejected" else "cancelled", ctx)
            return if (s.turn == seat) endTurn(closed.state, ctx) else closed
        }
        return when {
            s.phase == SETUP -> if (setupSeat(s) == seat) setupPlace(s, autoSetup(s), ctx) else SStep(s)
            s.phase == DISCARD && s.discards.all { it == 0 } -> afterDiscards(s, ctx)
            s.turn == seat && s.phase != DISCARD && s.phase != TRADE -> endTurn(s, ctx)
            else -> SStep(s)
        }
    }

    // ---- the end ----------------------------------------------------------------------

    /** Every point [seat] has, hidden VP cards included. */
    fun vp(s: SprawlState, seat: Int) = SprawlRules.publicVp(s.board, seat, s.roadHolder, s.armyHolder) + s.seats[seat].dev.count { it == VP }

    private fun win(s: SprawlState, seat: Int, ctx: GameContext): SStep =
        tally(s.copy(winner = seat).beat("win", seat = seat).log("${s.seats[seat].name} hits ${vp(s, seat)} points!"), ctx)

    private fun tally(s0: SprawlState, ctx: GameContext): SStep {
        if (s0.phase == TALLY || s0.phase == PODIUM) return SStep(s0)
        val points = s0.seats.indices.map { if (s0.seats[it].gone) 0 else vp(s0, it) }
        val winner = if (s0.winner >= 0) s0.winner else active(s0).maxWithOrNull(
            compareBy<Int> { points[it] }.thenBy { if (s0.roadHolder == it) 1 else 0 }.thenBy { s0.seats[it].hand.sum() },
        ) ?: -1
        var s = s0.copy(tally = points, winner = winner, trade = null).beat("tally", seat = winner)
        if (winner >= 0) s = s.drink(others(s, winner), FINISH, "${s.seats[winner].name} won")
        val effects = mutableListOf<Effect>()
        s.seats.forEachIndexed { i, seat -> if (points[i] > 0) effects += Effect.Award(seat.player, points[i], "victory points") }
        if (winner >= 0) effects += Effect.Highlight("${s.seats[winner].name} won Sprawl with ${points[winner]} points")
        val step = go(s, TALLY, ctx)
        return SStep(step.state, step.effects + effects)
    }

    // ---- views ------------------------------------------------------------------------

    fun mapView(b: SBoard): SprawlMapTv {
        val g = b.geo
        return SprawlMapTv(
            size = b.size,
            hexes = g.hexes.mapIndexed { h, p -> SprawlHexTv(p.x, p.y, b.terrain[h], b.numbers[h], b.names[h]) },
            vertices = g.vertices.map { listOf(it.x, it.y) },
            edges = g.edges,
            harbours = b.harbours.map { SprawlHarbourTv(it.edge, it.kind) },
            resources = names.resources, landlord = names.landlord, dev = names.dev, awards = names.awards,
        )
    }

    override fun tvView(s: SprawlState, ctx: GameContext): SprawlTv {
        val over = s.phase == TALLY || s.phase == PODIUM
        val ranks = s.tally.map { v -> s.tally.count { it > v } + 1 }
        return SprawlTv(
            phase = s.phase, map = mapView(s.board), robber = s.board.robber, vOwner = s.board.vOwner, vLevel = s.board.vLevel, eOwner = s.board.eOwner,
            seats = s.seats.mapIndexed { i, seat ->
                SprawlSeatTv(
                    name = seat.name, color = seat.color, player = seat.player, cards = seat.hand.sum(), dev = seat.dev.size,
                    vp = if (over || s.winner == i) vp(s, i) else SprawlRules.publicVp(s.board, i, s.roadHolder, s.armyHolder),
                    knights = seat.knights, road = SprawlRules.longestRoad(s.board, i), longest = s.roadHolder == i, army = s.armyHolder == i,
                    gone = seat.gone, discard = s.discards.getOrElse(i) { 0 },
                )
            },
            turn = s.turn,
            setupPiece = if (s.phase == SETUP) setupPiece(s) else null,
            setupRound = if (s.phase == SETUP) (if (s.setupStep / 2 < s.seats.size) 1 else 2) else 0,
            dice = s.dice,
            trade = s.trade?.let { SprawlTradeTv(it.id, it.from, it.to, it.give, it.get, it.counters, it.passed) },
            peek = s.peek, peekKind = s.peekKind, pick = s.pick, bank = s.bank, deckLeft = s.deck.size,
            clockLeftMs = s.clockLeftMs, phaseMs = s.phaseMs, lastRound = s.lastRound, timed = s.phase in TIMED,
            vpTarget = s.vpTarget, drinks = s.drinks, beats = s.beats, ticker = s.ticker, winner = s.winner,
            tally = if (s.tally.isEmpty()) emptyList() else s.seats.indices.map { i -> SprawlTallyTv(i, s.tally[i], s.seats[i].dev.count { it == VP }, ranks[i]) },
        )
    }

    override fun playerView(s: SprawlState, who: PlayerId, ctx: GameContext): Screen = SprawlPhone(names, this).view(s, who, ctx)

    internal fun setupSeatOf(s: SprawlState) = setupSeat(s)
    internal fun setupPieceOf(s: SprawlState) = setupPiece(s)

    // ---- helpers ----------------------------------------------------------------------

    private fun others(s: SprawlState, me: Int) = active(s).filter { it != me }

    private fun SprawlState.seat(i: Int, f: (SSeat) -> SSeat) = copy(seats = seats.mapIndexed { j, x -> if (j == i) f(x) else x })
    /** The bank pays [gain] to seat [i]. */
    private fun SprawlState.pay(i: Int, gain: List<Int>) = seat(i) { it.copy(hand = SprawlRules.plus(it.hand, gain)) }.copy(bank = SprawlRules.minus(bank, gain))
    /** Seat [i] pays [cost] to the bank. */
    private fun SprawlState.spend(i: Int, cost: List<Int>) = seat(i) { it.copy(hand = SprawlRules.minus(it.hand, cost)) }.copy(bank = SprawlRules.plus(bank, cost))
    private fun SprawlState.settle(v: Int, seat: Int, level: Int) = copy(board = board.copy(
        vOwner = board.vOwner.toMutableList().also { it[v] = seat }, vLevel = board.vLevel.toMutableList().also { it[v] = level },
    ))
    private fun SprawlState.road(e: Int, seat: Int) = copy(board = board.copy(eOwner = board.eOwner.toMutableList().also { it[e] = seat }))

    private fun SprawlState.beat(
        kind: String, seat: Int = -1, other: Int = -1, target: Int = -1, amount: Int = 0, dice: List<Int> = emptyList(),
        seats: List<Int> = emptyList(), targets: List<Int> = emptyList(), gains: List<List<Int>> = emptyList(), sips: Int = 0, text: String? = null,
    ) = copy(beats = (beats + SBeat(beatSeq + 1, kind, seat, other, target, amount, dice, seats, targets, gains, sips, text)).takeLast(MAX_BEATS), beatSeq = beatSeq + 1)

    private fun SprawlState.log(line: String) = copy(ticker = (ticker + line).takeLast(MAX_TICKER))

    private fun SprawlState.drink(who: List<Int>, sips: Int, why: String) =
        if (!drinks || who.isEmpty()) this else beat("drink", seats = who, sips = sips, text = why)

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(k: String) = str(k)?.toIntOrNull() ?: throw Reject("BAD_ACTION")
    private fun JsonObject.intOr(k: String, d: Int) = str(k)?.toIntOrNull() ?: d
    private fun JsonObject.ints(k: String): List<Int> = when (val v = this[k]) {
        null -> emptyList()
        is JsonArray -> v.map { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: throw Reject("BAD_ACTION") }
        else -> throw Reject("BAD_ACTION")
    }

    companion object {
        const val GAME_ID = "sprawl"

        const val SETUP = "setup"
        const val ROLL = "roll"
        const val DISCARD = "discard"
        const val ROBBER = "robber"
        const val STEAL = "steal"
        const val MAIN = "main"
        const val ROAD2 = "road2"
        const val PICK = "pick"
        const val TRADE = "trade"
        const val TALLY = "tally"
        const val PODIUM = "podium"

        const val ROAD = "road"
        const val SETTLEMENT = "settlement"
        const val CITY = "city"

        const val KNIGHT = "knight"
        const val ROAD_TRIP = "road"
        const val PLENTY = "plenty"
        const val MONO = "mono"
        const val VP = "vp"
        val PLAYABLE = setOf(KNIGHT, ROAD_TRIP, PLENTY, MONO)

        /** An offer open to whoever takes it first. */
        const val ANYONE = -2
        const val MAX_SEATS = 6
        const val DEFAULT_VP = 8
        const val MIN_VP = 8
        const val MAX_VP = 10
        const val DEFAULT_MINUTES = 45
        const val MAX_COUNTERS = 2
        const val MAX_OFFERS = 4

        const val FINISH = 99
        const val STEAL_SIPS = 1
        const val DISCARD_SIPS = 2
        const val LOSE_AWARD_SIPS = 2
        const val CITY_SIPS = 1
        const val MONO_SIPS = 1

        const val SETUP_MS = 30_000L
        const val ROLL_MS = 20_000L
        const val MAIN_MS = 60_000L
        const val MAIN_FLOOR_MS = 10_000L
        const val RESUME_FLOOR_MS = 15_000L
        const val DISCARD_MS = 20_000L
        const val ROBBER_MS = 15_000L
        const val STEAL_MS = 10_000L
        const val ROAD2_MS = 15_000L
        const val PICK_MS = 15_000L
        const val TRADE_MS = 30_000L
        const val TRADE_RESUME_MS = 5_000L
        const val AUTOPILOT_MS = 5_000L
        const val RECONNECT_MS = 10_000L
        const val TALLY_MS = 14_000L
        const val PODIUM_MS = 15_000L

        const val MAX_BEATS = 16
        const val MAX_TICKER = 5

        /** Phases that belong to the active seat: their clock shortens while they're away. */
        val AUTO_PHASES = setOf(SETUP, ROLL, MAIN, ROBBER, STEAL, ROAD2, PICK)
        /** When a development card can be played (before or after the roll). */
        val PLAY_PHASES = setOf(ROLL, MAIN)
        /** Phases whose clock is a decision timer, not an animation. */
        val TIMED = setOf(SETUP, ROLL, MAIN, DISCARD, ROBBER, STEAL, ROAD2, PICK, TRADE)
        val PHASE_FREE = setOf("trade", "tradeReply", "tradeCancel", "discard", "peek")
        val PEEK_KINDS = setOf("vertex", "edge", "hex")
        /** Seat colours (Brain Drain's kit, so everyone keeps their colour across games). */
        val KIT = BrainDrain.TEAM_KIT
    }
}
