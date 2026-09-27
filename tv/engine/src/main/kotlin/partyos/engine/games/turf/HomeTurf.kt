package partyos.engine.games.turf

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import partyos.engine.Effect
import partyos.engine.GameContext
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.LateJoin
import partyos.engine.Player
import partyos.engine.PlayerId
import partyos.engine.Reject
import partyos.engine.Screen
import partyos.engine.Step
import partyos.engine.TurfAuctionTv
import partyos.engine.TurfCardTv
import partyos.engine.TurfDebtTv
import partyos.engine.TurfSpaceTv
import partyos.engine.TurfTallyTv
import partyos.engine.TurfTokenTv
import partyos.engine.TurfTradeTv
import partyos.engine.TurfTv
import partyos.engine.TutorialCard
import partyos.engine.games.trivia.BrainDrain
import partyos.engine.games.trivia.RememberedTeam
import kotlin.random.Random

/**
 * Home Turf: Monopoly's rules at party speed, on a board of the crew's own places. Solo (up to 6 tokens) or teams
 * sharing a token, where the dice pass round the team. Official rules plus the official short-game hotel (3 houses),
 * time-limit ending, dealt starter places, the speed die and a one-turn Timeout; live auctions and trades on phones;
 * drink calls when they're on. See docs/superpowers/specs/2026-09-27-home-turf-design.md.
 */
class HomeTurf(private val names: BoardNames = TurfBoard.loadNames()) : GameModule<TurfState> {
    override val info = GameInfo(
        id = GAME_ID,
        title = "Home Turf",
        tagline = "Buy your friends' places. Charge them rent.",
        minPlayers = 2,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Buy your friends' places", "Land on a place nobody owns: buy it, or pass and everyone bids on their phones."),
            TutorialCard("Charge rent. Build. Trade.", "Own a whole colour set and its rent doubles. Three houses make a hotel. Trade anything from your phone."),
            TutorialCard("The clock is ticking", "When time runs out, everyone finishes the lap. Richest wins: cash plus places plus buildings."),
        ),
        lateJoin = LateJoin.ANYTIME,
    )
    override val stateSerializer = TurfState.serializer()

    /** The board as the TV draws it; the same every payload. */
    private val boardTv: List<TurfSpaceTv> = TurfBoard.spaces.map { s ->
        val n = names.spaces[s.index]
        TurfSpaceTv(
            name = n.name, label = n.label, kind = s.kind.name.lowercase().replace("_", ""),
            group = s.group, color = if (s.buyable) TurfBoard.colorOf(s.index) else null,
            price = s.price, rent = s.rent, houseCost = s.houseCost, tax = s.tax,
        )
    }

    // ---- setup ------------------------------------------------------------------------

    override fun start(ctx: GameContext): Step<TurfState> {
        val mode = ctx.settings["turfMode"] ?: 0
        val tooMany = ctx.players.size > MAX_TOKENS
        val teams = mode == MODE_TEAMS || tooMany
        val notice = if (mode == MODE_SOLO && tooMany) "Too many for solo, so it's teams" else null
        val seed = ctx.random.nextLong()
        var draws = 0
        fun rnd() = TurfDice.random(seed, draws++)
        val tokens = if (teams) teamTokens(ctx, rnd()) else soloTokens(ctx.players, rnd())
        val minutes = ctx.settings["minutes"] ?: DEFAULT_MINUTES
        val s = TurfState(
            phase = "", teams = teams, tokens = tokens,
            chance = TurfDecks.chance.indices.shuffled(rnd()), chest = TurfDecks.chest.indices.shuffled(rnd()),
            clockLeftMs = if (minutes > 0) minutes * 60_000L else null,
            drinks = (ctx.settings["drinks"] ?: 1) != 0,
            rngSeed = seed, draws = draws, notice = notice,
        )
        return if (teams) go(s, TEAMUP, ctx) else go(s, PIECES, ctx)
    }

    private fun soloTokens(players: List<Player>, r: Random) = players.shuffled(r).mapIndexed { i, p ->
        TToken(id = "K${i + 1}", name = p.name, color = KIT[i].first, members = listOf(p.id), seat = p.id, cash = START_CASH)
    }

    /** Teams dealt evenly (or last show's teams, if they still fit), in a random turn order. */
    private fun teamTokens(ctx: GameContext, r: Random): List<TToken> {
        val n = ctx.players.size
        val asked = ctx.settings["teams"] ?: 0
        val count = (if (asked in 2..MAX_TOKENS) asked else ((n + 2) / 3).coerceIn(2, MAX_TOKENS)).coerceAtMost(n)
        val present = ctx.players.map { it.id }.toSet()
        val remembered = ctx.memory[BrainDrain.MEMORY_KEY]
            ?.let { runCatching { json.decodeFromString(ListSerializer(RememberedTeam.serializer()), it) }.getOrNull() }
            ?.takeIf { it.size == count }
            ?.map { it.copy(members = it.members.filter { m -> m in present }) }
        val fits = remembered != null &&
            remembered.flatMap { it.members }.toSet() == present &&
            remembered.maxOf { it.members.size } - remembered.minOf { it.members.size } <= 1
        val groups = if (fits) remembered!!.map { it.members } else deal(ctx.players.map { it.id }.shuffled(r), count)
        return groups.mapIndexed { i, members ->
            val kit = KIT[i]
            TToken(
                id = "", name = if (fits) remembered!![i].name else kit.second, color = if (fits) remembered!![i].color else kit.first,
                members = members, seat = members.firstOrNull { ctx.isConnected(it) } ?: members.firstOrNull(), cash = START_CASH,
            )
        }.shuffled(r).mapIndexed { i, t -> t.copy(id = "K${i + 1}") }
    }

    private fun deal(ids: List<PlayerId>, count: Int): List<List<PlayerId>> =
        (0 until count).map { k -> ids.filterIndexed { i, _ -> i % count == k } }

    private fun rememberTeams(s: TurfState): Effect.Remember = Effect.Remember(
        BrainDrain.MEMORY_KEY,
        json.encodeToString(ListSerializer(RememberedTeam.serializer()), s.tokens.map { RememberedTeam(it.name, it.color, true, it.members) }),
    )

    /** Everyone gets two random places and pays for them (the official time-limit game's start). */
    private fun dealPlaces(s0: TurfState, ctx: GameContext): Step<TurfState> {
        var s = s0
        val deeds = TurfBoard.deeds.shuffled(TurfDice.random(s.rngSeed, s.draws))
        s = s.copy(draws = s.draws + 1)
        var owner = s.estate.owner
        s.tokens.indices.forEach { t ->
            val mine = deeds.drop(t * DEALT).take(DEALT)
            owner = owner.toMutableList().also { o -> mine.forEach { o[it] = t } }
            s = s.cash(t, -mine.sumOf { TurfBoard.spaces[it].price }).beat("deal", token = t, tokens = mine)
        }
        return go(s.copy(estate = s.estate.copy(owner = owner)).log("Starter places dealt. Game on!"), DEAL, ctx)
    }

    // ---- the clock --------------------------------------------------------------------

    /**
     * Charges the game clock for the time spent in the current phase so far. Pauses don't count (the engine's time
     * left is frozen while paused) and neither does setup. Called once at the start of every engine call.
     */
    private fun account(s: TurfState, ctx: GameContext): TurfState {
        val planned = s.phaseMs ?: return s
        val left = ctx.remainingMs ?: return s
        val spent = (planned - left).coerceAtLeast(0)
        val counts = s.phase !in SETUP && s.phase != TALLY && s.phase != PODIUM
        return s.copy(phaseMs = left, clockLeftMs = if (counts) s.clockLeftMs?.let { it - spent } else s.clockLeftMs)
    }

    private fun go(s: TurfState, phase: String, ctx: GameContext, ms: Long = dur(s, phase, ctx)) =
        Step(s.copy(phase = phase, phaseMs = ms), listOf<Effect>(Effect.Phase(ms)))

    private fun reset(s: TurfState, ms: Long) = Step(s.copy(phaseMs = ms), listOf<Effect>(Effect.Deadline(ms)))

    private fun dur(s: TurfState, phase: String, ctx: GameContext): Long = when (phase) {
        TEAMUP -> TEAMUP_MS
        PIECES -> PIECES_MS
        DEAL -> DEAL_MS
        ROLL -> auto(s, ctx, ROLL_MS)
        JAIL -> auto(s, ctx, JAIL_MS)
        BUY -> auto(s, ctx, BUY_MS)
        CHOOSE -> auto(s, ctx, CHOOSE_MS)
        MANAGE -> auto(s, ctx, MANAGE_MS)
        AUCTION -> AUCTION_MS
        CARD -> CARD_MS
        DEBT -> DEBT_MS
        TRADE -> TRADE_MS
        TALLY -> TALLY_MS
        else -> PODIUM_MS
    }

    /** A turn whose dice-holder is away plays itself quickly (autopilot). */
    private fun auto(s: TurfState, ctx: GameContext, ms: Long) = if (away(s, s.turn, ctx)) AUTOPILOT_MS else ms

    private fun away(s: TurfState, t: Int, ctx: GameContext) = s.tokens.getOrNull(t)?.seat?.let { !ctx.isConnected(it) } ?: true

    // ---- engine hooks -----------------------------------------------------------------

    override fun onAction(s0: TurfState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<TurfState> {
        val s = account(s0, ctx)
        val kind = payload.str("kind") ?: throw Reject("BAD_ACTION")
        val me = s.tokens.indexOfFirst { who in it.members }
        return when (kind) {
            "piece" -> piece(s, me, payload.str("option"), ctx)
            "trade" -> propose(s, who, me, payload, ctx)
            "tradeReply" -> reply(s, who, payload, ctx)
            "tradeCancel" -> cancelTrade(s, who, payload, ctx)
            "bid" -> bid(s, who, me, payload)
            "build", "sell", "mortgage", "unmortgage" -> manage(s, who, me, kind, payload.int("target"), ctx)
            "pay", "bankrupt" -> debtAction(s, who, me, kind, ctx)
            else -> turnAction(s, who, me, kind, payload, ctx)
        }
    }

    override fun onDeadline(s0: TurfState, ctx: GameContext): Step<TurfState> {
        val s = account(s0, ctx)
        return when (s.phase) {
            TEAMUP -> go(s, PIECES, ctx).and(rememberTeams(s))
            PIECES -> dealPlaces(autoPieces(s), ctx)
            DEAL -> beginTurn(s, 0, ctx)
            ROLL -> roll(s, ctx)
            JAIL -> jailRoll(s, ctx)
            MOVE -> land(s, ctx)
            BUY -> openAuction(s, s.buy, ctx)
            AUCTION -> closeAuction(s, ctx)
            CARD -> applyCard(s, ctx)
            CHOOSE -> choose(s, if (s.choose == BUS) "total" else autoSpace(s).toString(), ctx)
            MANAGE -> endTurn(s, ctx)
            DEBT -> autoDebt(s, ctx)
            TRADE -> closeTrade(s, "expired", ctx)
            TALLY -> go(s, PODIUM, ctx)
            else -> Step(s, listOf(Effect.Finish))
        }
    }

    override fun onPresence(s0: TurfState, who: PlayerId, present: Boolean, ctx: GameContext): Step<TurfState> {
        var s = account(s0, ctx)
        val t = s.tokens.indexOfFirst { who in it.members }
        if (ctx.player(who) == null) return if (t >= 0) removeMember(s, t, who, ctx) else Step(s)
        if (t < 0) return if (present) lateJoin(s, who) else Step(s)
        val tok = s.tokens[t]
        if (!present && tok.seat == who) s = reseat(s, t, ctx, skip = who)
        if (present && (tok.seat == null || !ctx.isConnected(tok.seat))) s = s.tok(t) { it.copy(seat = who) }
        // Autopilot: a turn whose dice-holder left speeds up; one whose holder came back slows down again.
        if (t == s.turn && s.phase in AUTO_PHASES) {
            val left = ctx.remainingMs ?: return Step(s)
            val isAway = away(s, t, ctx)
            if (isAway && left > AUTOPILOT_MS) return reset(s, AUTOPILOT_MS)
            if (!isAway && left < RECONNECT_MS) return reset(s, RECONNECT_MS)
        }
        return Step(s)
    }

    override fun onHost(s0: TurfState, action: String, ctx: GameContext): Step<TurfState> {
        val s = account(s0, ctx)
        if (action != "shuffle" || s.phase != TEAMUP) throw Reject("NOT_NOW")
        val ids = s.tokens.flatMap { it.members }.shuffled(TurfDice.random(s.rngSeed, s.draws))
        val groups = deal(ids, s.tokens.size)
        val tokens = s.tokens.mapIndexed { i, t -> t.copy(members = groups[i], seat = groups[i].firstOrNull { ctx.isConnected(it) } ?: groups[i].firstOrNull()) }
        return Step(s.copy(tokens = tokens, draws = s.draws + 1).beat("shuffle"))
    }

    override fun waitingOn(s: TurfState): Set<PlayerId>? = null

    override fun phaseFree(payload: JsonObject) = payload.str("kind") in PHASE_FREE

    // ---- pieces -----------------------------------------------------------------------

    private fun piece(s: TurfState, me: Int, option: String?, ctx: GameContext): Step<TurfState> {
        if (s.phase != PIECES) throw Reject("NOT_NOW")
        if (me < 0) throw Reject("NOT_PLAYING")
        if (s.tokens[me].piece != null) throw Reject("ALREADY_PICKED")
        if (option !in PIECES_ALL) throw Reject("BAD_OPTION")
        if (s.tokens.any { it.piece == option }) throw Reject("PIECE_TAKEN")
        val next = s.tok(me) { it.copy(piece = option) }.beat("piece", token = me, text = option)
        return if (next.tokens.all { it.piece != null }) dealPlaces(next, ctx) else Step(next)
    }

    private fun autoPieces(s: TurfState): TurfState {
        val free = PIECES_ALL.filter { p -> s.tokens.none { it.piece == p } }.toMutableList()
        return s.copy(tokens = s.tokens.map { if (it.piece == null) it.copy(piece = free.removeAt(0)) else it })
    }

    // ---- turns ------------------------------------------------------------------------

    private fun beginTurn(s0: TurfState, t: Int, ctx: GameContext): Step<TurfState> {
        val s = s0.copy(turn = t, turnBeat = s0.beatSeq).beat("turn", token = t)
        return go(s, if (s.tokens[t].jailed) JAIL else ROLL, ctx)
    }

    private fun turnAction(s: TurfState, who: PlayerId, me: Int, kind: String, payload: JsonObject, ctx: GameContext): Step<TurfState> {
        if (me != s.turn || s.tokens[me].bankrupt) throw Reject("NOT_YOUR_TURN")
        if (s.tokens[me].seat != who) throw Reject("NOT_YOUR_SEAT")
        if (s.trade != null) throw Reject("TRADE_OPEN")
        val option = payload.str("option")
        return when {
            kind == "roll" && s.phase == ROLL -> roll(s, ctx)
            kind == "jail" && s.phase == JAIL -> when (option) {
                "pay" -> {
                    if (s.tokens[me].cash < JAIL_FEE) throw Reject("CANT_AFFORD")
                    go(s.cash(me, -JAIL_FEE).tok(me) { it.copy(jailed = false) }.beat("free", token = me, amount = JAIL_FEE)
                        .log("${s.tokens[me].name} paid $$JAIL_FEE to leave ${names.spaces[TurfBoard.JAIL].name}"), ROLL, ctx)
                }
                "card" -> {
                    val card = s.tokens[me].jailCards.firstOrNull() ?: throw Reject("NO_CARD")
                    go(returnJailCard(s.tok(me) { it.copy(jailed = false, jailCards = it.jailCards.drop(1)) }, card)
                        .beat("free", token = me).log("${s.tokens[me].name} used a Get Out card"), ROLL, ctx)
                }
                "roll" -> jailRoll(s, ctx)
                else -> throw Reject("BAD_OPTION")
            }
            kind == "buy" && s.phase == BUY -> when (option) {
                "buy" -> buy(s, ctx)
                "pass" -> openAuction(s, s.buy, ctx)
                else -> throw Reject("BAD_OPTION")
            }
            kind == "choose" && s.phase == CHOOSE -> choose(s, option ?: throw Reject("BAD_OPTION"), ctx)
            kind == "end" && s.phase == MANAGE -> endTurn(s, ctx)
            else -> throw Reject("NOT_NOW")
        }
    }

    private fun roll(s0: TurfState, ctx: GameContext): Step<TurfState> {
        val t = s0.turn
        val r = TurfDice.roll(s0.rngSeed, s0.draws, s0.tokens[t].passedPayday)
        var s = s0.copy(draws = s0.draws + 1, roll = r, jailRoll = false, scout = false, rentMode = null).beat("roll", token = t, dice = r.dice)
        if (r.triples) return go(s.copy(doubles = 0, choose = TRIPLES).log("${s.tokens[t].name} rolled TRIPLES: go anywhere!"), CHOOSE, ctx)
        val doubles = if (r.doubles) s.doubles + 1 else 0
        s = s.copy(doubles = doubles)
        if (doubles >= MAX_DOUBLES) return jail(s.log("Three doubles in a row: ${s.tokens[t].name} got pulled over"), t, ctx)
        if (r.bus) return go(s.copy(choose = BUS), CHOOSE, ctx)
        return move(s.copy(scout = r.scout), t, r.move, ctx)
    }

    /** A Timeout turn: doubles walk out (no extra roll); otherwise pay $50 and move anyway (the short-game rule). */
    private fun jailRoll(s0: TurfState, ctx: GameContext): Step<TurfState> {
        val t = s0.turn
        val r = TurfDice.roll(s0.rngSeed, s0.draws, false)
        var s = s0.copy(draws = s0.draws + 1, roll = r, doubles = 0).beat("roll", token = t, dice = r.dice)
        if (r.doubles) {
            s = s.tok(t) { it.copy(jailed = false) }.copy(jailRoll = true).beat("free", token = t).log("${s.tokens[t].name} rolled doubles out of ${names.spaces[TurfBoard.JAIL].name}")
            return move(s, t, r.a + r.b, ctx)
        }
        s = s.charge(t, JAIL_FEE, TurfRules.NOBODY, "to leave ${names.spaces[TurfBoard.JAIL].name}").log("${s.tokens[t].name} paid $$JAIL_FEE to leave ${names.spaces[TurfBoard.JAIL].name}")
        if (s.debts.isNotEmpty()) return go(s.copy(next = JAIL_MOVE), DEBT, ctx)
        return jailWalk(s, ctx)
    }

    private fun jailWalk(s: TurfState, ctx: GameContext): Step<TurfState> {
        val t = s.turn
        val r = s.roll ?: return afterLand(s, ctx)
        return move(s.tok(t) { it.copy(jailed = false) }.copy(jailRoll = true).beat("free", token = t), t, r.a + r.b, ctx)
    }

    private fun jail(s0: TurfState, t: Int, ctx: GameContext): Step<TurfState> {
        val s = s0.tok(t) { it.copy(pos = TurfBoard.JAIL, jailed = true) }
            .copy(doubles = 0, scout = false, rentMode = null)
            .beat("jail", token = t, space = TurfBoard.JAIL)
            .log("${s0.tokens[t].name} went to ${names.spaces[TurfBoard.JAIL].name}")
            .drink(listOf(t), JAIL_SIPS, "sent to ${names.spaces[TurfBoard.JAIL].name}")
        return if (t == s.turn) go(s, MANAGE, ctx) else afterLand(s, ctx)
    }

    /** Moves forward (or back, for a negative count), collecting Payday when passing or landing on it. */
    private fun move(s0: TurfState, t: Int, steps: Int, ctx: GameContext): Step<TurfState> {
        val from = s0.tokens[t].pos
        val path = TurfRules.path(from, steps)
        val to = path.lastOrNull() ?: from
        val passes = steps > 0 && from + steps >= TurfBoard.SIZE
        var s = s0.tok(t) { it.copy(pos = to, passedPayday = it.passedPayday || passes) }.beat("move", token = t, space = to, path = path)
        if (passes) s = s.cash(t, TurfBoard.PAYDAY_PAY).beat("payday", token = t, amount = TurfBoard.PAYDAY_PAY)
        if (path.isEmpty()) return land(s, ctx)
        return go(s, MOVE, ctx, path.size * HOP_MS + MOVE_PAD_MS)
    }

    private fun moveTo(s: TurfState, t: Int, target: Int, ctx: GameContext) = move(s, t, TurfRules.distance(s.tokens[t].pos, target), ctx)

    private fun land(s0: TurfState, ctx: GameContext): Step<TurfState> {
        val t = s0.turn
        val pos = s0.tokens[t].pos
        val space = TurfBoard.spaces[pos]
        val who = s0.tokens[t].name
        var s = s0.copy(card = null)
        when (space.kind) {
            SpaceKind.PAYDAY, SpaceKind.JAIL -> Unit
            SpaceKind.COUCH -> s = s.beat("couch", token = t, space = pos).log("$who crashed on ${names.spaces[pos].name}")
                .drink(others(s, t), 1, "$who crashed on ${names.spaces[pos].name}")
            SpaceKind.GO_TO_JAIL -> return jail(s, t, ctx)
            SpaceKind.TAX -> s = s.beat("tax", token = t, space = pos, amount = space.tax).log("$who paid $${space.tax} for ${names.spaces[pos].name}")
                .charge(t, space.tax, TurfRules.NOBODY, names.spaces[pos].name)
            SpaceKind.CHANCE -> return draw(s, TurfDecks.CHANCE, ctx)
            SpaceKind.CHEST -> return draw(s, TurfDecks.CHEST, ctx)
            else -> {
                val owner = s.estate.owner[pos]
                when {
                    owner == TurfRules.NOBODY -> return go(s.copy(buy = pos, rentMode = null), BUY, ctx)
                    owner == t || s.estate.mortgaged[pos] || s.tokens[owner].bankrupt -> Unit
                    else -> s = payRent(s, t, owner, pos)
                }
            }
        }
        return afterLand(s.copy(rentMode = null), ctx)
    }

    private fun payRent(s0: TurfState, t: Int, owner: Int, pos: Int): TurfState {
        var s = s0
        val pips = if (s.rentMode == UTIL10) {
            val r = TurfDice.roll(s.rngSeed, s.draws, false)
            s = s.copy(draws = s.draws + 1).beat("roll", token = t, dice = r.dice)
            r.a + r.b
        } else s.roll?.pips ?: 0
        val rent = TurfRules.rent(s.estate, pos, pips, doubleRail = s.rentMode == RAIL2, utilTen = s.rentMode == UTIL10)
        val hotel = s.estate.level[pos] == TurfRules.HOTEL
        val payer = s.tokens[t].name
        return s.beat("rent", token = t, other = owner, space = pos, amount = rent)
            .log("$payer paid ${s.tokens[owner].name} $$rent at ${names.spaces[pos].name}")
            .drink(listOf(t), rentSips(rent, hotel), "paid rent at ${names.spaces[pos].name}")
            .charge(t, rent, owner, "rent at ${names.spaces[pos].name}")
    }

    /** What's next once a landing is resolved: settle debts, the scout's jump, another roll on doubles, or manage. */
    private fun afterLand(s0: TurfState, ctx: GameContext): Step<TurfState> {
        var s = s0
        if (s.debts.isNotEmpty()) return go(s.copy(next = AFTER_LAND), DEBT, ctx)
        val t = s.turn
        if (active(s).size <= 1) return tally(s, ctx)
        if (s.tokens[t].bankrupt) return endTurn(s, ctx)
        val tok = s.tokens[t]
        if (s.scout && !tok.jailed) {
            s = s.copy(scout = false)
            val target = scoutTarget(s, t)
            if (target != null) return moveTo(s.beat("scout", token = t, space = target).log("The scout sends ${tok.name} to ${names.spaces[target].name}"), t, target, ctx)
        }
        val r = s.roll
        if (!tok.jailed && !s.jailRoll && r != null && r.doubles && !r.triples && s.doubles in 1 until MAX_DOUBLES) return go(s, ROLL, ctx)
        return go(s, MANAGE, ctx)
    }

    /** The scout: the next place nobody owns, or failing that the next place where [t] owes rent. */
    private fun scoutTarget(s: TurfState, t: Int): Int? {
        val from = s.tokens[t].pos
        val ahead = (1 until TurfBoard.SIZE).map { (from + it) % TurfBoard.SIZE }.filter { TurfBoard.spaces[it].buyable }
        return ahead.firstOrNull { s.estate.owner[it] == TurfRules.NOBODY }
            ?: ahead.firstOrNull { s.estate.owner[it] != t && !s.estate.mortgaged[it] && s.estate.owner[it] != TurfRules.NOBODY }
    }

    private fun choose(s0: TurfState, option: String, ctx: GameContext): Step<TurfState> {
        val t = s0.turn
        val r = s0.roll ?: throw Reject("NOT_NOW")
        val s = s0.copy(choose = null)
        return if (s0.choose == BUS) {
            val steps = when (option) {
                "a" -> r.a
                "b" -> r.b
                "total" -> r.a + r.b
                else -> throw Reject("BAD_OPTION")
            }
            move(s.beat("bus", token = t, amount = steps), t, steps, ctx)
        } else {
            val target = option.toIntOrNull()?.takeIf { it in 0 until TurfBoard.SIZE } ?: throw Reject("BAD_OPTION")
            moveTo(s.beat("teleport", token = t, space = target), t, target, ctx)
        }
    }

    /** Triples with no pick: the next place nobody owns, or stay put. */
    private fun autoSpace(s: TurfState): Int {
        val from = s.tokens[s.turn].pos
        return (1 until TurfBoard.SIZE).map { (from + it) % TurfBoard.SIZE }
            .firstOrNull { TurfBoard.spaces[it].buyable && s.estate.owner[it] == TurfRules.NOBODY } ?: from
    }

    private fun endTurn(s0: TurfState, ctx: GameContext): Step<TurfState> {
        var s = s0.copy(roll = null, doubles = 0, jailRoll = false, scout = false, rentMode = null, card = null, buy = -1, choose = null, offered = emptyList())
        s = reseat(s, s.turn, ctx, rotate = true)
        if (active(s).size <= 1) return tally(s, ctx)
        if (!s.lastLap && (s.clockLeftMs ?: 1) <= 0) s = s.copy(lastLap = true).beat("lastlap").log("Time's up! Last lap.")
        val next = nextActive(s, s.turn)
        if (s.lastLap && next <= s.turn) return tally(s, ctx)
        return beginTurn(s, next, ctx)
    }

    private fun nextActive(s: TurfState, from: Int): Int =
        (1..s.tokens.size).map { (from + it) % s.tokens.size }.first { !s.tokens[it].bankrupt }

    // ---- buying and auctions ----------------------------------------------------------

    private fun buy(s0: TurfState, ctx: GameContext): Step<TurfState> {
        val t = s0.turn
        val pos = s0.buy
        val price = TurfBoard.spaces[pos].price
        if (s0.tokens[t].cash < price) throw Reject("CANT_AFFORD")
        val s = s0.cash(t, -price).own(pos, t).copy(buy = -1).beat("buy", token = t, space = pos, amount = price)
            .log("${s0.tokens[t].name} bought ${names.spaces[pos].name} for $$price")
        return afterLand(homeTurf(s0, s, t), ctx)
    }

    private fun openAuction(s: TurfState, space: Int, ctx: GameContext): Step<TurfState> {
        if (space < 0) return afterLand(s, ctx)
        val a = TAuction(s.serial + 1, space)
        return go(s.copy(serial = a.id, auction = a, buy = -1).beat("auction", space = space).log("${names.spaces[space].name} goes to auction!"), AUCTION, ctx)
    }

    private fun bid(s: TurfState, who: PlayerId, me: Int, payload: JsonObject): Step<TurfState> {
        val a = s.auction
        if (s.phase != AUCTION || a == null) throw Reject("NOT_NOW")
        if (payload.int("auction") != a.id) throw Reject("STALE")
        if (me < 0 || s.tokens[me].bankrupt) throw Reject("NOT_PLAYING")
        if (s.tokens[me].seat != who) throw Reject("NOT_YOUR_SEAT")
        val amount = payload.int("amount")
        if (amount <= a.top) throw Reject("BID_TOO_LOW")
        if (amount > s.tokens[me].cash) throw Reject("CANT_AFFORD")
        val next = s.copy(auction = a.copy(bids = (a.bids + TBid(me, amount)).takeLast(MAX_BIDS))).beat("bid", token = me, space = a.space, amount = amount)
        return reset(next, BID_MS)
    }

    private fun closeAuction(s0: TurfState, ctx: GameContext): Step<TurfState> {
        val a = s0.auction ?: return afterLand(s0, ctx)
        var s = s0.copy(auction = null)
        val winner = a.leader
        s = if (winner == TurfRules.NOBODY) {
            s.beat("nobid", space = a.space).log("Nobody bid on ${names.spaces[a.space].name}")
        } else {
            val won = s.cash(winner, -a.top).own(a.space, winner).beat("won", token = winner, space = a.space, amount = a.top)
                .log("${s.tokens[winner].name} won ${names.spaces[a.space].name} for $${a.top}")
            homeTurf(s, won, winner)
        }
        return afterLand(s, ctx)
    }

    /** Drinks for everyone else when [t] just completed a colour set ([before] is the board before the change). */
    private fun homeTurf(before: TurfState, after: TurfState, t: Int): TurfState {
        var s = after
        for (g in TurfBoard.groups) {
            val had = g.all { before.estate.owner[it] == t }
            val has = g.all { after.estate.owner[it] == t }
            if (has && !had) {
                s = s.beat("set", token = t, space = g.last()).log("HOME TURF! ${s.tokens[t].name} owns the whole set")
                    .drink(others(s, t), 1, "${s.tokens[t].name} completed a set")
            }
        }
        return s
    }

    // ---- cards ------------------------------------------------------------------------

    private fun draw(s0: TurfState, deck: String, ctx: GameContext): Step<TurfState> {
        val pile = if (deck == TurfDecks.CHANCE) s0.chance else s0.chest
        val idx = pile.first()
        val card = TurfDecks.deck(deck)[idx]
        val keep = card.kind == CardKind.JAIL_FREE
        val rest = pile.drop(1) + (if (keep) emptyList() else listOf(idx))
        var s = if (deck == TurfDecks.CHANCE) s0.copy(chance = rest) else s0.copy(chest = rest)
        // A Get Out card goes straight into their hand, so it can't get lost if they leave while it's on screen.
        if (keep) s = s.tok(s.turn) { it.copy(jailCards = it.jailCards + deck) }
        s = s.copy(card = TCard(deck, idx)).beat("card", token = s.turn, text = cardText(deck, idx, s.drinks))
        return go(s, CARD, ctx)
    }

    fun cardText(deck: String, idx: Int, drinks: Boolean): String {
        val text = TurfBoard.fill((if (deck == TurfDecks.CHANCE) names.chanceCards else names.chestCards)[idx], names)
        val card = TurfDecks.deck(deck)[idx]
        if (!drinks) return text
        return when {
            card.sips > 0 -> "$text Drink ${sipLabel(card.sips)}."
            card.othersSips > 0 -> "$text Everyone else drinks ${sipLabel(card.othersSips)}."
            else -> text
        }
    }

    private fun applyCard(s0: TurfState, ctx: GameContext): Step<TurfState> {
        val c = s0.card ?: return afterLand(s0, ctx)
        val card = TurfDecks.deck(c.deck)[c.index]
        val t = s0.turn
        var s = s0
        if (card.sips > 0) s = s.drink(listOf(t), card.sips, "drew a card")
        if (card.othersSips > 0) s = s.drink(others(s, t), card.othersSips, "${s.tokens[t].name}'s card")
        val pos = s.tokens[t].pos
        when (card.kind) {
            CardKind.ADVANCE -> return moveTo(s, t, card.space, ctx)
            CardKind.NEAREST_RAIL -> return moveTo(s.copy(rentMode = RAIL2), t, TurfRules.nearest(pos, TurfBoard.railroads), ctx)
            CardKind.NEAREST_UTIL -> return moveTo(s.copy(rentMode = UTIL10), t, TurfRules.nearest(pos, TurfBoard.utilities), ctx)
            CardKind.BACK -> return move(s, t, -card.amount, ctx)
            CardKind.JAIL -> return jail(s, t, ctx)
            CardKind.JAIL_FREE -> Unit // already in their hand (see draw)
            CardKind.COLLECT -> s = s.cash(t, card.amount)
            CardKind.PAY -> s = s.charge(t, card.amount, TurfRules.NOBODY, "a card")
            CardKind.PAY_EACH -> s = s.charge(t, card.amount * others(s, t).size, EVERYONE, "a card")
            CardKind.COLLECT_EACH -> others(s, t).forEach { o -> s = s.charge(o, card.amount, t, "${s.tokens[t].name}'s card") }
            CardKind.REPAIRS -> {
                val (houses, hotels) = TurfRules.buildings(s.estate, t)
                s = s.charge(t, houses * card.perHouse + hotels * card.perHotel, TurfRules.NOBODY, "repairs")
            }
        }
        return afterLand(s, ctx)
    }

    private fun returnJailCard(s: TurfState, deck: String): TurfState {
        val idx = TurfDecks.deck(deck).indexOfFirst { it.kind == CardKind.JAIL_FREE }
        return if (deck == TurfDecks.CHANCE) s.copy(chance = s.chance + idx) else s.copy(chest = s.chest + idx)
    }

    // ---- building and mortgages --------------------------------------------------------

    private fun manage(s: TurfState, who: PlayerId, me: Int, kind: String, space: Int, ctx: GameContext): Step<TurfState> {
        if (me < 0 || s.tokens[me].bankrupt) throw Reject("NOT_PLAYING")
        if (s.tokens[me].seat != who) throw Reject("NOT_YOUR_SEAT")
        if (s.trade != null) throw Reject("TRADE_OPEN")
        if (space !in 0 until TurfBoard.SIZE) throw Reject("BAD_ACTION")
        val debtor = s.phase == DEBT && s.debts.firstOrNull()?.token == me
        val myTurn = me == s.turn && s.phase in MANAGE_PHASES
        if (!debtor && !myTurn) throw Reject("NOT_YOUR_TURN")
        if (debtor && kind != "sell" && kind != "mortgage") throw Reject("PAY_FIRST")
        val e = s.estate
        val sp = TurfBoard.spaces[space]
        val name = names.spaces[space].name
        val tok = s.tokens[me]
        val next = when (kind) {
            "build" -> {
                TurfRules.buildRefusal(e, me, space)?.let { throw Reject(it) }
                if (tok.cash < sp.houseCost) throw Reject("CANT_AFFORD")
                val built = TurfRules.build(e, space)
                s.copy(estate = built).cash(me, -sp.houseCost).beat("build", token = me, space = space, amount = built.level[space])
                    .log(if (built.level[space] == TurfRules.HOTEL) "${tok.name} built a HOTEL on $name" else "${tok.name} built a house on $name")
            }
            "sell" -> {
                TurfRules.sellRefusal(e, me, space)?.let { throw Reject(it) }
                val (sold, refund) = TurfRules.sell(e, space)
                s.copy(estate = sold).cash(me, refund).beat("sell", token = me, space = space, amount = refund).log("${tok.name} sold a building on $name")
            }
            "mortgage" -> {
                TurfRules.mortgageRefusal(e, me, space)?.let { throw Reject(it) }
                val (m, got) = TurfRules.mortgage(e, space)
                s.copy(estate = m).cash(me, got).beat("mortgage", token = me, space = space, amount = got).log("${tok.name} mortgaged $name")
            }
            else -> {
                TurfRules.unmortgageRefusal(e, me, space)?.let { throw Reject(it) }
                val cost = TurfRules.unmortgageCost(space)
                if (tok.cash < cost) throw Reject("CANT_AFFORD")
                s.copy(estate = TurfRules.unmortgage(e, space)).cash(me, -cost).beat("unmortgage", token = me, space = space, amount = cost).log("${tok.name} paid off $name")
            }
        }
        // Slow thumbs keep a few seconds after every tap; a debt keeps its own clock.
        val left = ctx.remainingMs
        return if (!debtor && left != null && left < MANAGE_FLOOR_MS) reset(next, MANAGE_FLOOR_MS) else Step(next)
    }

    // ---- money, debt and bankruptcy -----------------------------------------------------

    private fun debtAction(s: TurfState, who: PlayerId, me: Int, kind: String, ctx: GameContext): Step<TurfState> {
        val debt = s.debts.firstOrNull()
        if (s.phase != DEBT || debt == null || debt.token != me) throw Reject("NOT_NOW")
        if (s.tokens[me].seat != who) throw Reject("NOT_YOUR_SEAT")
        if (s.trade != null) throw Reject("TRADE_OPEN")
        return if (kind == "pay") {
            if (s.tokens[me].cash < debt.amount) throw Reject("CANT_AFFORD")
            settleDebt(s, ctx)
        } else {
            nextDebt(bankrupt(s, me, debt.to), ctx)
        }
    }

    /** Time's up on a debt: sell and mortgage automatically, then pay, or go bankrupt if it still isn't enough. */
    private fun autoDebt(s0: TurfState, ctx: GameContext): Step<TurfState> {
        val debt = s0.debts.firstOrNull() ?: return resume(s0, ctx)
        val t = debt.token
        val need = debt.amount - s0.tokens[t].cash
        var s = s0
        if (need > 0) {
            val (e, steps) = TurfRules.raiseCash(s.estate, t, need)
            s = s.copy(estate = e).cash(t, steps.sumOf { it.amount })
            steps.forEach { st -> s = s.beat(st.kind, token = t, space = st.space, amount = st.amount) }
            if (steps.isNotEmpty()) s = s.log("${s.tokens[t].name} sold off to raise cash")
        }
        return if (s.tokens[t].cash >= debt.amount) settleDebt(s, ctx) else nextDebt(bankrupt(s, t, debt.to), ctx)
    }

    private fun settleDebt(s0: TurfState, ctx: GameContext): Step<TurfState> {
        val debt = s0.debts.first()
        val s = s0.transfer(debt.token, debt.amount, debt.to).copy(debts = s0.debts.drop(1)).beat("paid", token = debt.token, other = debt.to, amount = debt.amount)
        return nextDebt(s, ctx)
    }

    private fun nextDebt(s: TurfState, ctx: GameContext): Step<TurfState> =
        if (s.debts.isNotEmpty()) go(s, DEBT, ctx) else resume(s, ctx)

    private fun resume(s: TurfState, ctx: GameContext): Step<TurfState> {
        if (active(s).size <= 1) return tally(s, ctx)
        return if (s.next == JAIL_MOVE && !s.tokens[s.turn].bankrupt) jailWalk(s.copy(next = AFTER_LAND), ctx)
        else afterLand(s.copy(next = AFTER_LAND), ctx)
    }

    /**
     * [t] is out. To a player: they get everything ([t]'s cash, buildings sold back at half price, places as they
     * are, Get Out cards). To the bank: places go back unowned and unmortgaged (party rule: no estate auction).
     */
    private fun bankrupt(s0: TurfState, t: Int, creditor: Int, why: String = "went bankrupt"): TurfState {
        var e = s0.estate
        var proceeds = 0
        val mine = e.owned(t)
        val level = e.level.toMutableList()
        var houses = e.housesLeft
        var hotels = e.hotelsLeft
        for (i in mine) {
            val lvl = level[i]
            if (lvl == 0) continue
            proceeds += lvl * TurfBoard.spaces[i].houseCost / 2
            if (lvl == TurfRules.HOTEL) hotels++ else houses += lvl
            level[i] = 0
        }
        e = e.copy(level = level, housesLeft = houses, hotelsLeft = hotels)
        val tok = s0.tokens[t]
        val toPlayer = creditor >= 0 && !s0.tokens[creditor].bankrupt
        var s = s0.copy(estate = e)
        if (toPlayer) {
            e = e.copy(owner = e.owner.map { if (it == t) creditor else it })
            s = s.copy(estate = e).cash(creditor, tok.cash + proceeds).tok(creditor) { it.copy(jailCards = it.jailCards + tok.jailCards) }
        } else {
            e = e.copy(
                owner = e.owner.map { if (it == t) TurfRules.NOBODY else it },
                mortgaged = e.mortgaged.mapIndexed { i, m -> if (i in mine) false else m },
            )
            s = s.copy(estate = e)
            tok.jailCards.forEach { s = returnJailCard(s, it) }
        }
        s = s.tok(t) { it.copy(cash = 0, bankrupt = true, jailed = false, jailCards = emptyList()) }
            .copy(
                debts = s.debts.filter { it.token != t }.map { if (it.to == t) it.copy(to = TurfRules.NOBODY) else it },
                auction = s.auction?.let { a -> a.copy(bids = a.bids.filter { it.token != t }) },
            )
            .beat("bankrupt", token = t, other = if (toPlayer) creditor else -1)
            .log(if (toPlayer) "${tok.name} $why to ${s.tokens[creditor].name}" else "${tok.name} $why")
        return if (why == "went bankrupt") s.drink(listOf(t), FINISH, "went bankrupt") else s
    }

    // ---- trades -----------------------------------------------------------------------

    private fun propose(s: TurfState, who: PlayerId, me: Int, p: JsonObject, ctx: GameContext): Step<TurfState> {
        if (me < 0 || s.tokens[me].bankrupt) throw Reject("NOT_PLAYING")
        if (s.tokens[me].seat != who) throw Reject("NOT_YOUR_SEAT")
        val to = p.int("to")
        if (to !in s.tokens.indices || to == me || s.tokens[to].bankrupt) throw Reject("BAD_TRADE")
        val give = p.ints("give")
        val get = p.ints("get")
        val giveCash = p.intOr("giveCash", 0)
        val getCash = p.intOr("getCash", 0)
        val giveCards = p.intOr("giveCards", 0)
        val getCards = p.intOr("getCards", 0)
        val a = TurfRules.Side(me, s.tokens[me].cash, s.tokens[me].jailCards.size, give, giveCash, giveCards)
        val b = TurfRules.Side(to, s.tokens[to].cash, s.tokens[to].jailCards.size, get, getCash, getCards)
        val counterId = p.str("counter")?.toIntOrNull()
        val open = s.trade
        if (counterId != null) {
            if (open == null || open.id != counterId || open.to != me || to != open.from || s.phase != TRADE) throw Reject("TRADE_GONE")
            if (open.counters >= MAX_COUNTERS) throw Reject("NO_MORE_COUNTERS")
            TurfRules.tradeRefusal(s.estate, a, b)?.let { throw Reject(it) }
            val t = TTrade(s.serial + 1, me, to, give, get, giveCash, getCash, giveCards, getCards, open.counters + 1, open.frozenPhase, open.frozenMs)
            return reset(s.copy(trade = t, serial = t.id).beat("counter", token = me, other = to).log("${s.tokens[me].name} countered"), TRADE_MS)
        }
        if (open != null) throw Reject("TRADE_OPEN")
        if (s.phase !in TRADE_PHASES) throw Reject("TRADE_LATER")
        val key = "$me>$to"
        if (key in s.offered) throw Reject("ONE_OFFER")
        TurfRules.tradeRefusal(s.estate, a, b)?.let { throw Reject(it) }
        val t = TTrade(s.serial + 1, me, to, give, get, giveCash, getCash, giveCards, getCards, 0, s.phase, ctx.remainingMs)
        return go(s.copy(trade = t, serial = t.id, offered = s.offered + key).beat("offer", token = me, other = to)
            .log("${s.tokens[me].name} offered ${s.tokens[to].name} a deal"), TRADE, ctx)
    }

    private fun reply(s: TurfState, who: PlayerId, p: JsonObject, ctx: GameContext): Step<TurfState> {
        val t = s.trade ?: throw Reject("TRADE_GONE")
        if (p.int("trade") != t.id || s.phase != TRADE) throw Reject("TRADE_GONE")
        if (s.tokens[t.to].seat != who) throw Reject("NOT_YOUR_SEAT")
        return when (p.str("option")) {
            "accept" -> {
                val a = TurfRules.Side(t.from, s.tokens[t.from].cash, s.tokens[t.from].jailCards.size, t.give, t.giveCash, t.giveCards)
                val b = TurfRules.Side(t.to, s.tokens[t.to].cash, s.tokens[t.to].jailCards.size, t.get, t.getCash, t.getCards)
                TurfRules.tradeRefusal(s.estate, a, b)?.let { throw Reject(it) }
                closeTrade(executeTrade(s, t), "accepted", ctx)
            }
            "reject" -> closeTrade(s, "rejected", ctx)
            else -> throw Reject("BAD_OPTION")
        }
    }

    private fun cancelTrade(s: TurfState, who: PlayerId, p: JsonObject, ctx: GameContext): Step<TurfState> {
        val t = s.trade ?: throw Reject("TRADE_GONE")
        if (p.int("trade") != t.id || s.phase != TRADE) throw Reject("TRADE_GONE")
        if (s.tokens[t.from].seat != who) throw Reject("NOT_YOUR_SEAT")
        return closeTrade(s, "cancelled", ctx)
    }

    private fun executeTrade(s0: TurfState, t: TTrade): TurfState {
        var s = s0
        val e = s.estate
        val owner = e.owner.toMutableList()
        t.give.forEach { owner[it] = t.to }
        t.get.forEach { owner[it] = t.from }
        s = s.copy(estate = e.copy(owner = owner))
            .cash(t.from, t.getCash - t.giveCash).cash(t.to, t.giveCash - t.getCash)
        val fromCards = s.tokens[t.from].jailCards
        val toCards = s.tokens[t.to].jailCards
        s = s.tok(t.from) { it.copy(jailCards = fromCards.drop(t.giveCards) + toCards.take(t.getCards)) }
            .tok(t.to) { it.copy(jailCards = toCards.drop(t.getCards) + fromCards.take(t.giveCards)) }
        // Taking a mortgaged place: pay its 10% interest now (checked affordable before the swap).
        s = s.cash(t.to, -t.give.filter { e.mortgaged[it] }.sumOf { TurfRules.interest(it) })
            .cash(t.from, -t.get.filter { e.mortgaged[it] }.sumOf { TurfRules.interest(it) })
        s = s.beat("traded", token = t.from, other = t.to).log("DEAL! ${s.tokens[t.from].name} and ${s.tokens[t.to].name} traded")
        return homeTurf(s0, homeTurf(s0, s, t.from), t.to)
    }

    /**
     * Closes the open trade and gives the frozen turn its time back (at least a few seconds). If the turn's token
     * retired while two others were trading, its turn ends instead: a token that's out never rolls again.
     */
    private fun closeTrade(s0: TurfState, how: String, ctx: GameContext): Step<TurfState> {
        val t = s0.trade ?: return Step(s0)
        val s = s0.copy(trade = null).let { if (how == "accepted") it else it.beat(how, token = t.from, other = t.to) }
        if (s.tokens[s.turn].bankrupt && t.frozenPhase in TURN_PHASES) return endTurn(s, ctx)
        return go(s, t.frozenPhase, ctx, maxOf(t.frozenMs ?: TRADE_RESUME_MS, TRADE_RESUME_MS))
    }

    // ---- the crew changes mid-game ------------------------------------------------------

    private fun lateJoin(s: TurfState, who: PlayerId): Step<TurfState> {
        if (!s.teams || s.phase == TALLY || s.phase == PODIUM) return Step(s)
        val t = active(s).minByOrNull { s.tokens[it].members.size } ?: return Step(s)
        return Step(s.tok(t) { it.copy(members = it.members + who, seat = it.seat ?: who) }.beat("join", token = t))
    }

    /** A kicked player leaves their token; a token with nobody left retires (bankrupt to the bank). */
    private fun removeMember(s0: TurfState, t: Int, who: PlayerId, ctx: GameContext): Step<TurfState> {
        var s = s0.tok(t) { it.copy(members = it.members - who) }
        if (s.tokens[t].members.isNotEmpty()) {
            if (s.tokens[t].seat == who) s = reseat(s, t, ctx, skip = who)
            return Step(s)
        }
        if (s.tokens[t].bankrupt || s.phase == TALLY || s.phase == PODIUM) return Step(s.tok(t) { it.copy(seat = null) })
        // Whatever they were in the middle of ends first.
        val trade = s.trade
        val phase = if (trade != null && (trade.from == t || trade.to == t)) trade.frozenPhase else s.phase
        if (phase != s.phase) s = s.copy(trade = null, phase = phase)
        val buying = phase == BUY && s.turn == t
        s = bankrupt(s.tok(t) { it.copy(seat = null) }, t, TurfRules.NOBODY, "left the game")
        if (active(s).size <= 1) return tally(s, ctx)
        return when {
            phase in SETUP -> if (phase == s0.phase) Step(s) else go(s, phase, ctx)
            buying -> openAuction(s, s.buy, ctx)
            s.turn == t && phase in listOf(ROLL, JAIL, MOVE, CHOOSE, MANAGE, CARD) -> endTurn(s, ctx)
            phase == DEBT -> if (s.debts.isEmpty()) resume(s, ctx) else go(s, DEBT, ctx)
            phase != s0.phase -> go(s, phase, ctx, maxOf(trade?.frozenMs ?: TRADE_RESUME_MS, TRADE_RESUME_MS))
            else -> Step(s)
        }
    }

    /** Hands [t]'s dice to a connected member: the next one in line after a turn ([rotate]), else anyone but [skip]. */
    private fun reseat(s: TurfState, t: Int, ctx: GameContext, rotate: Boolean = false, skip: PlayerId? = null): TurfState {
        val tok = s.tokens[t]
        if (tok.members.isEmpty()) return s
        val here = tok.members.filter { it != skip && ctx.isConnected(it) }
        if (here.isEmpty()) return if (tok.seat == skip) s.tok(t) { it.copy(seat = tok.members.firstOrNull { m -> m != skip }) } else s
        if (!rotate && tok.seat in here) return s
        val start = tok.members.indexOf(tok.seat)
        val order = (1..tok.members.size).map { tok.members[(start + it).mod(tok.members.size)] }
        val next = order.first { it in here }
        return s.tok(t) { it.copy(seat = next) }
    }

    // ---- the end ----------------------------------------------------------------------

    private fun tally(s0: TurfState, ctx: GameContext): Step<TurfState> {
        if (s0.phase == TALLY || s0.phase == PODIUM) return Step(s0)
        val worths = s0.tokens.mapIndexed { i, t -> if (t.bankrupt) 0 else TurfRules.netWorth(s0.estate, i, t.cash) }
        var s = s0.copy(tally = worths, trade = null, auction = null, debts = emptyList()).beat("tally")
        val alive = active(s)
        val order = alive.sortedByDescending { worths[it] }
        if (alive.size > 1) s = s.drink(listOf(order.last()), LAST_PLACE_SIPS, "last place")
        val effects = mutableListOf<Effect>()
        s.tokens.forEachIndexed { i, t -> t.members.forEach { m -> if (worths[i] > 0) effects += Effect.Award(m, worths[i], "net worth") } }
        order.firstOrNull()?.let { w -> effects += Effect.Highlight("${s.tokens[w].name} won Home Turf with $${worths[w]}") }
        return go(s, TALLY, ctx).and(*effects.toTypedArray())
    }

    // ---- views ------------------------------------------------------------------------

    override fun tvView(s: TurfState, ctx: GameContext): TurfTv {
        val e = s.estate
        val worths = s.tokens.mapIndexed { i, t -> if (t.bankrupt) 0 else TurfRules.netWorth(e, i, t.cash) }
        val ranks = worths.map { w -> worths.count { it > w } + 1 }
        return TurfTv(
            phase = s.phase, teams = s.teams, board = boardTv, chanceName = names.chance, chestName = names.chest,
            owner = e.owner, level = e.level, mortgaged = e.mortgaged.indices.filter { e.mortgaged[it] },
            tokens = s.tokens.mapIndexed { i, t ->
                TurfTokenTv(t.name, t.color, t.piece, t.members, t.seat, t.cash, t.pos, t.jailed, t.jailCards.size, t.bankrupt, worths[i],
                    TurfBoard.groups.count { g -> g.all { e.owner[it] == i } })
            },
            turn = s.turn, dice = s.roll?.dice.orEmpty(), doubles = s.doubles,
            housesLeft = e.housesLeft, hotelsLeft = e.hotelsLeft, buy = s.buy, choose = s.choose,
            card = s.card?.let { TurfCardTv(it.deck, if (it.deck == TurfDecks.CHANCE) names.chance else names.chest, cardText(it.deck, it.index, s.drinks), TurfDecks.deck(it.deck)[it.index].sips) },
            auction = s.auction?.let { TurfAuctionTv(it.id, it.space, it.top, it.leader, it.bids.size) },
            trade = s.trade?.let { TurfTradeTv(it.id, it.from, it.to, it.give, it.get, it.giveCash, it.getCash, it.giveCards, it.getCards, it.counters) },
            debt = if (s.phase == DEBT) s.debts.firstOrNull()?.let { TurfDebtTv(it.token, it.amount, it.to, it.why) } else null,
            clockLeftMs = s.clockLeftMs, phaseMs = s.phaseMs, lastLap = s.lastLap,
            timed = s.phase in TIMED, drinks = s.drinks, beats = s.beats, ticker = s.ticker,
            pieces = PIECES_ALL.filter { p -> s.tokens.none { it.piece == p } },
            tally = if (s.tally.isEmpty()) emptyList() else s.tokens.indices.map { i ->
                val mine = e.owned(i)
                TurfTallyTv(i, s.tally[i], s.tokens[i].cash, mine.size, mine.sumOf { e.level[it] }, ranks[i])
            },
            notice = s.notice,
        )
    }

    override fun playerView(s: TurfState, who: PlayerId, ctx: GameContext): Screen = TurfPhone(names, this).view(s, who, ctx)

    // ---- helpers ----------------------------------------------------------------------

    private fun active(s: TurfState) = s.tokens.indices.filter { !s.tokens[it].bankrupt }
    private fun others(s: TurfState, t: Int) = active(s).filter { it != t }

    private fun TurfState.tok(i: Int, f: (TToken) -> TToken) = copy(tokens = tokens.mapIndexed { j, t -> if (j == i) f(t) else t })
    private fun TurfState.cash(i: Int, delta: Int) = if (delta == 0) this else tok(i) { it.copy(cash = it.cash + delta) }
    private fun TurfState.own(space: Int, t: Int) = copy(estate = estate.copy(owner = estate.owner.toMutableList().also { it[space] = t }))

    private fun TurfState.beat(
        kind: String, token: Int = -1, other: Int = -1, space: Int = -1, amount: Int = 0,
        dice: List<Int> = emptyList(), path: List<Int> = emptyList(), tokens: List<Int> = emptyList(), sips: Int = 0, text: String? = null,
    ) = copy(beats = (beats + TBeat(beatSeq + 1, kind, token, other, space, amount, dice, path, tokens, sips, text)).takeLast(MAX_BEATS), beatSeq = beatSeq + 1)

    private fun TurfState.log(line: String) = copy(ticker = (ticker + line).takeLast(MAX_TICKER))

    private fun TurfState.drink(who: List<Int>, sips: Int, why: String) =
        if (!drinks || who.isEmpty()) this else beat("drink", tokens = who, sips = sips, text = why)

    /** [from] pays [amount] to token [to] (or the bank, or everyone else). If they can't, a debt is queued instead. */
    private fun TurfState.charge(from: Int, amount: Int, to: Int, why: String): TurfState {
        if (amount <= 0) return this
        if (tokens[from].cash >= amount) return transfer(from, amount, to)
        return copy(debts = debts + TDebt(from, amount, to, why))
    }

    private fun TurfState.transfer(from: Int, amount: Int, to: Int): TurfState {
        var s = cash(from, -amount)
        when {
            to >= 0 && !tokens[to].bankrupt -> s = s.cash(to, amount)
            to == EVERYONE -> {
                val others = tokens.indices.filter { it != from && !tokens[it].bankrupt }
                if (others.isNotEmpty()) others.forEach { s = s.cash(it, amount / others.size) }
            }
        }
        return s
    }

    private fun Step<TurfState>.and(vararg fx: Effect) = Step(state, effects + fx)

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(k: String) = str(k)?.toIntOrNull() ?: throw Reject("BAD_ACTION")
    private fun JsonObject.intOr(k: String, d: Int) = str(k)?.toIntOrNull() ?: d
    private fun JsonObject.ints(k: String): List<Int> = when (val v = this[k]) {
        null -> emptyList()
        is JsonArray -> v.map { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: throw Reject("BAD_ACTION") }
        else -> throw Reject("BAD_ACTION")
    }

    companion object {
        const val GAME_ID = "turf"

        const val TEAMUP = "teamup"
        const val PIECES = "pieces"
        const val DEAL = "deal"
        const val ROLL = "roll"
        const val JAIL = "jail"
        const val MOVE = "move"
        const val BUY = "buy"
        const val AUCTION = "auction"
        const val CARD = "card"
        const val CHOOSE = "choose"
        const val MANAGE = "manage"
        const val DEBT = "debt"
        const val TRADE = "trade"
        const val TALLY = "tally"
        const val PODIUM = "podium"

        const val AFTER_LAND = "land"
        const val JAIL_MOVE = "jailmove"
        const val BUS = "bus"
        const val TRIPLES = "triples"
        const val RAIL2 = "rail2"
        const val UTIL10 = "util10"
        /** A debt owed to every other token still in (split evenly). */
        const val EVERYONE = -2

        const val MODE_SOLO = 1
        const val MODE_TEAMS = 2
        const val MAX_TOKENS = 6
        const val START_CASH = 1500
        const val DEALT = 2
        const val JAIL_FEE = 50
        const val MAX_DOUBLES = 3
        const val MAX_COUNTERS = 2
        const val DEFAULT_MINUTES = 45

        const val SHOT = 5
        /** "Finish your drink." */
        const val FINISH = 99
        const val JAIL_SIPS = 2
        const val LAST_PLACE_SIPS = 2

        const val TEAMUP_MS = 15_000L
        const val PIECES_MS = 15_000L
        const val DEAL_MS = 7_000L
        const val ROLL_MS = 20_000L
        const val JAIL_MS = 15_000L
        const val HOP_MS = 260L
        const val MOVE_PAD_MS = 1_400L
        const val BUY_MS = 15_000L
        const val AUCTION_MS = 10_000L
        const val BID_MS = 6_000L
        const val CARD_MS = 5_000L
        const val CHOOSE_MS = 10_000L
        const val MANAGE_MS = 20_000L
        const val MANAGE_FLOOR_MS = 10_000L
        const val DEBT_MS = 60_000L
        const val TRADE_MS = 30_000L
        const val TRADE_RESUME_MS = 5_000L
        const val AUTOPILOT_MS = 5_000L
        const val RECONNECT_MS = 10_000L
        const val TALLY_MS = 14_000L
        const val PODIUM_MS = 15_000L

        const val MAX_BEATS = 16
        const val MAX_TICKER = 5
        const val MAX_BIDS = 40

        val PIECES_ALL = listOf("cup", "pizza", "sneaker", "boombox", "cone", "duck")
        val PIECE_NAMES = mapOf("cup" to "Red Cup", "pizza" to "Pizza Slice", "sneaker" to "Sneaker", "boombox" to "Boombox", "cone" to "Traffic Cone", "duck" to "Rubber Duck")
        val SETUP = setOf(TEAMUP, PIECES, DEAL)
        /** Phases that belong to the turn's dice-holder: their clock shortens while they're away. */
        val AUTO_PHASES = setOf(ROLL, JAIL, BUY, CHOOSE, MANAGE)
        /** When the turn's owner can build, sell and mortgage. */
        val MANAGE_PHASES = setOf(ROLL, JAIL, BUY, MANAGE)
        /** When a trade can be offered (the turn freezes while it's open). */
        val TRADE_PHASES = setOf(ROLL, JAIL, MANAGE, DEBT)
        /** Trade phases that belong to the turn's token itself (DEBT resolves the debtor's side and ends it there). */
        val TURN_PHASES = setOf(ROLL, JAIL, MANAGE)
        /** Phases whose clock is a decision timer, not an animation. */
        val TIMED = setOf(ROLL, JAIL, BUY, AUCTION, CHOOSE, MANAGE, DEBT, TRADE, PIECES, TEAMUP)
        val PHASE_FREE = setOf("trade", "tradeReply", "tradeCancel")
        /** Token colours and team names (Brain Drain's kit, so teams keep their colours across games). */
        val KIT = BrainDrain.TEAM_KIT

        private val json = Json { ignoreUnknownKeys = true }

        /** Rent sips: under $100 a sip, under $400 two, and a hotel or $400+ is a shot. */
        fun rentSips(rent: Int, hotel: Boolean) = when {
            hotel || rent >= 400 -> SHOT
            rent >= 100 -> 2
            else -> 1
        }

        fun sipLabel(n: Int) = when (n) {
            FINISH -> "the rest of your drink"
            SHOT -> "a shot"
            1 -> "1 sip"
            else -> "$n sips"
        }
    }
}
