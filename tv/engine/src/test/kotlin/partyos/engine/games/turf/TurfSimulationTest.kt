package partyos.engine.games.turf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PhoneState
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.TvState
import partyos.engine.add
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Bots play whole games of Home Turf through the real engine, with human-ish pacing (a decision every few seconds),
 * checking the board's invariants every second of game time. Also estimates how long a party game runs.
 * `TURF_SIMS=500 ./gradlew :engine:test --tests '*TurfSimulationTest*'` plays more games.
 */
class TurfSimulationTest {
    private val registry = GameRegistry(listOf(HomeTurf()))
    private val sizes = Json { encodeDefaults = true }

    data class Result(val minutes: Double, val turns: Int, val bankrupt: Int, val tokens: Int, val trades: Int, val auctions: Int, val hotels: Int)

    private fun state(e: PartyEngine) = e.snapshot().game?.state?.let { Json.decodeFromJsonElement(TurfState.serializer(), it) }

    private fun play(seed: Long, players: Int, opts: Map<String, Int>, deep: Boolean): Result {
        val clock = FakeClock(0)
        val e = PartyEngine(clock, SeededEntropy(seed), registry)
        val ids = (1..players).map { e.add("P$it") }
        opts.forEach { (k, v) -> e.host(HostCmd.SetOption(k, v)) }
        e.host(HostCmd.StartGame("turf"))
        e.host(HostCmd.SkipPhase)
        val rnd = Random(seed)
        var n = 0
        var ticks = 0
        var seen = 0
        var turns = 0
        var trades = 0
        var auctions = 0
        var hotels = 0
        var firstTurnAt = -1L
        var last: TurfState? = null
        while (e.tvState().stage != null) {
            check(++ticks < 4 * 3600) { "game $seed never ended (phase ${last?.phase})" }
            clock.advance(1_000)
            e.tick()
            val s = state(e) ?: break
            last = s
            invariants(s, seed)
            for (b in s.beats.filter { it.seq > seen }) when (b.kind) {
                "turn" -> { turns++; if (firstTurnAt < 0) firstTurnAt = clock.now() }
                "traded" -> trades++
                "auction" -> auctions++
                "build" -> if (b.amount == TurfRules.HOTEL) hotels++
            }
            seen = s.beatSeq
            if (deep && ticks % 37 == 0) {
                val round = Json.decodeFromString(TurfState.serializer(), Json.encodeToString(TurfState.serializer(), s))
                check(round == s) { "snapshot round trip changed the state in game $seed" }
                val tv = sizes.encodeToString(TvState.serializer(), e.tvState()).length
                val phone = ids.maxOf { sizes.encodeToString(PhoneState.serializer(), e.phoneState(it)).length }
                check(tv < 24_000 && phone < 12_000) { "payloads too big: tv $tv, phone $phone" }
            }
            for (id in ids.shuffled(rnd)) {
                if (rnd.nextDouble() > 0.4) continue // people take a few seconds to decide
                val ps = e.phoneState(id)
                val scr = ps.screen as? Screen.Turf ?: continue
                val payload = decide(scr, s, rnd) ?: continue
                e.action(id, "b${n++}", ps.round, payload) // refusals (races, too-low bids) are fine
            }
        }
        val end = last ?: error("no game")
        return Result((clock.now() - firstTurnAt) / 60_000.0, turns, end.tokens.count { it.bankrupt }, end.tokens.size, trades, auctions, hotels)
    }

    private fun invariants(s: TurfState, seed: Long) {
        val e = s.estate
        fun fail(what: String): Nothing = error("game $seed, phase ${s.phase}: $what")
        if (e.level.sumOf { if (it in 1..3) it else 0 } + e.housesLeft != TurfRules.HOUSES) fail("houses don't add up")
        if (e.level.count { it == TurfRules.HOTEL } + e.hotelsLeft != TurfRules.HOTELS) fail("hotels don't add up")
        s.tokens.forEachIndexed { i, t ->
            if (t.cash < 0) fail("${t.name} has negative cash ${t.cash}")
            if (t.bankrupt && e.owner.any { it == i }) fail("${t.name} is bankrupt but still owns places")
        }
        e.owner.forEachIndexed { sp, o ->
            if (o != TurfRules.NOBODY && (o !in s.tokens.indices || !TurfBoard.spaces[sp].buyable)) fail("bad owner $o on $sp")
            if (o == TurfRules.NOBODY && (e.level[sp] != 0 || e.mortgaged[sp])) fail("unowned $sp has buildings or a mortgage")
            if (e.level[sp] > 0 && !TurfRules.ownsGroup(e, o, sp)) fail("buildings on $sp without the set")
            if (e.mortgaged[sp] && TurfBoard.groupOf(sp).any { e.level[it] > 0 }) fail("mortgaged $sp in a built-up set")
        }
        if (s.chance.size + s.tokens.sumOf { t -> t.jailCards.count { it == TurfDecks.CHANCE } } != TurfDecks.chance.size) fail("chance cards lost")
        if (s.chest.size + s.tokens.sumOf { t -> t.jailCards.count { it == TurfDecks.CHEST } } != TurfDecks.chest.size) fail("chest cards lost")
        if (s.phase == HomeTurf.DEBT && s.debts.isEmpty()) fail("debt phase with no debt")
        if (s.phase == HomeTurf.TRADE && s.trade == null) fail("trade phase with no trade")
    }

    // ---- the bots (heuristics after intrepidcoder/monopoly and arXiv 2103.00683) ----------------------

    private fun payload(kind: String, vararg kv: Pair<String, Any>): JsonObject = buildJsonObject {
        put("kind", JsonPrimitive(kind))
        kv.forEach { (k, v) ->
            when (v) {
                is Int -> put(k, JsonPrimitive(v))
                is List<*> -> put(k, JsonArray(v.map { JsonPrimitive(it.toString()) }))
                else -> put(k, JsonPrimitive(v.toString()))
            }
        }
    }

    private fun value(e: Estate, spaces: List<Int>) = spaces.sumOf { if (e.mortgaged[it]) TurfBoard.spaces[it].mortgage else TurfBoard.spaces[it].price }

    private fun decide(scr: Screen.Turf, s: TurfState, rnd: Random): JsonObject? {
        val me = scr.me ?: return null
        val p = scr.prompt
        val e = s.estate
        val ids = p.actions.map { it.id }
        return when (p.kind) {
            "pieces" -> scr.pieces.firstOrNull()?.let { payload("piece", "option" to it.id) }
            "roll" -> payload("roll")
            "jail" -> payload("jail", "option" to if ("pay" in ids && me.cash > 300 && e.owner.count { it == TurfRules.NOBODY } > 8) "pay" else "roll")
            "buy" -> {
                val completes = TurfRules.completesSet(e, me.index, p.space)
                payload("buy", "option" to if ("buy" in ids && (completes || me.cash - p.amount >= 150)) "buy" else "pass")
            }
            "bid" -> {
                val pad = scr.auction ?: return null
                if (!pad.canBid || pad.leading) return null
                val blocks = s.tokens.indices.any { it != me.index && TurfRules.completesSet(e, it, pad.space) }
                val limit = (pad.price * (if (TurfRules.completesSet(e, me.index, pad.space) || blocks) 2.0 else 1.5)).toInt()
                val next = pad.top + 10 * (1 + rnd.nextInt(5))
                if (next <= minOf(limit, pad.maxBid - 50)) payload("bid", "auction" to pad.auction, "amount" to next) else null
            }
            "bus" -> payload("choose", "option" to "total")
            "triples" -> {
                val best = TurfBoard.deeds.filter { e.owner[it] == TurfRules.NOBODY && TurfBoard.spaces[it].price <= me.cash }
                    .maxByOrNull { TurfBoard.spaces[it].price }
                payload("choose", "option" to (best ?: 24).toString())
            }
            "manage" -> manage(scr, s, rnd) ?: payload("end")
            "debt" -> when {
                "pay" in ids -> payload("pay")
                scr.deeds.any { it.sell != null } -> payload("sell", "target" to scr.deeds.first { it.sell != null }.space)
                scr.deeds.any { it.mortgage != null } -> payload("mortgage", "target" to scr.deeds.first { it.mortgage != null }.space)
                else -> payload("bankrupt")
            }
            "trade" -> {
                val t = scr.trade ?: return null
                val gain = value(e, t.give.map { it.space }) + t.giveCash - value(e, t.get.map { it.space }) - t.getCash
                val after = e.copy(owner = e.owner.toMutableList().also { o -> t.give.forEach { o[it.space] = t.to }; t.get.forEach { o[it.space] = t.from } })
                val sets = { tok: Int -> TurfBoard.groups.count { g -> g.all { after.owner[it] == tok } } - TurfBoard.groups.count { g -> g.all { e.owner[it] == tok } } }
                // People at a party take a set-completing deal if the sweetener is big enough.
                val good = (sets(t.to) > 0 && sets(t.from) == 0) || (sets(t.from) == 0 && gain > 0) || (sets(t.to) > 0 && gain >= 0) ||
                    (sets(t.from) > 0 && gain >= SWEETENER)
                payload("tradeReply", "trade" to t.id, "option" to if (good) "accept" else "reject")
            }
            else -> null
        }
    }

    /** Build, lift a mortgage, or offer a set-completing swap; null = nothing to do, so end the turn. */
    private fun manage(scr: Screen.Turf, s: TurfState, rnd: Random): JsonObject? {
        val me = scr.me ?: return null
        scr.deeds.filter { it.build != null && me.cash - it.build!! >= 250 }.maxByOrNull { it.group }?.let { return payload("build", "target" to it.space) }
        scr.deeds.firstOrNull { it.unmortgage != null && me.cash - it.unmortgage!! >= 400 }?.let { return payload("unmortgage", "target" to it.space) }
        if (scr.canTrade && rnd.nextDouble() < 0.3) {
            val e = s.estate
            for (partner in scr.partners) {
                val want = partner.deeds.firstOrNull { d -> d.tradable && TurfBoard.spaces[d.space].group >= 0 && TurfRules.completesSet(e, me.index, d.space) } ?: continue
                val give = scr.deeds.firstOrNull { d -> d.tradable && !d.set && !TurfRules.completesSet(e, partner.index, d.space) && TurfBoard.groupOf(d.space) != TurfBoard.groupOf(want.space) }
                    ?: continue
                val diff = TurfBoard.spaces[want.space].price - TurfBoard.spaces[give.space].price
                val cash = (diff + SWEETENER).coerceIn(0, me.cash / 2)
                return payload("trade", "to" to partner.index, "give" to listOf(give.space), "get" to listOf(want.space), "giveCash" to cash)
            }
        }
        return null
    }

    private val SWEETENER = 150
    private val sims = System.getenv("TURF_SIMS")?.toIntOrNull() ?: 40

    @Test fun botsPlayWholeGamesWithoutBreakingTheRules() {
        val results = (0 until sims).map { g ->
            val teams = g % 4 == 3
            val opts = mutableMapOf("minutes" to 20)
            if (teams) { opts["turfMode"] = 2; opts["teams"] = 2 + g % 3 }
            play(1_000L + g, if (teams) 7 else 2 + g % 5, opts, deep = g < 6)
        }
        println("Home Turf sims ($sims games, 20-minute clock): median ${median(results.map { it.minutes })} min, " +
            "${results.sumOf { it.bankrupt }} bankruptcies, ${results.sumOf { it.trades }} trades, ${results.sumOf { it.auctions }} auctions, " +
            "${results.sumOf { it.hotels }} hotels")
        assertTrue(results.all { it.minutes <= 20 + 15 }, "a 20-minute game plus its last lap: ${results.map { it.minutes }}")
    }

    @Test fun aFullGameFitsThePartyWindow() {
        val results = (0 until 6).map { play(9_000L + it, 4, mapOf("minutes" to 45), deep = false) }
        results.forEach { r ->
            println("45-minute game, ${r.tokens} tokens: ${"%.1f".format(r.minutes)} min, ${r.turns} turns, ${r.bankrupt} bankrupt, ${r.hotels} hotels, ${r.trades} trades")
        }
        assertTrue(results.all { it.minutes in 10.0..60.0 }, "games run 45 minutes plus a last lap, or end early on bankruptcies: ${results.map { it.minutes }}")
    }

    private fun median(xs: List<Double>) = xs.sorted().let { "%.1f".format(it[it.size / 2]) }
}
