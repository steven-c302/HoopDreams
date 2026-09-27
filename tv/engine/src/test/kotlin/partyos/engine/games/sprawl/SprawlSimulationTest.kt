package partyos.engine.games.sprawl

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
 * Bots play whole games of Sprawl through the real engine at human-ish pace, checking the island's invariants every
 * second of game time; also estimates how long a party game runs. Heuristics after Catanatron's rule-based bot.
 * `SPRAWL_SIMS=200 ./gradlew :engine:test --tests '*SprawlSimulationTest*'` plays more games.
 */
class SprawlSimulationTest {
    private val registry = GameRegistry(listOf(Sprawl()))
    private val sizes = Json { encodeDefaults = true }

    data class Result(val minutes: Double, val turns: Int, val winnerVp: Int, val lastRound: Boolean, val trades: Int, val sevens: Int, val cards: Int)

    private fun state(e: PartyEngine) = e.snapshot().game?.state?.let { Json.decodeFromJsonElement(SprawlState.serializer(), it) }

    private fun play(seed: Long, players: Int, opts: Map<String, Int>, deep: Boolean): Result {
        val clock = FakeClock(0)
        val e = PartyEngine(clock, SeededEntropy(seed), registry)
        val ids = (1..players).map { e.add("P$it") }
        opts.forEach { (k, v) -> e.host(HostCmd.SetOption(k, v)) }
        e.host(HostCmd.StartGame("sprawl"))
        e.host(HostCmd.SkipPhase)
        val rnd = Random(seed)
        var n = 0
        var ticks = 0
        var seen = 0
        var turns = 0
        var trades = 0
        var sevens = 0
        var cards = 0
        var sawLast = false
        var last: SprawlState? = null
        while (e.tvState().stage != null) {
            check(++ticks < 4 * 3600) { "game $seed never ended (phase ${last?.phase})" }
            clock.advance(1_000)
            e.tick()
            val s = state(e) ?: break
            last = s
            invariants(s, seed)
            sawLast = sawLast || s.lastRound
            for (b in s.beats.filter { it.seq > seen }) when (b.kind) {
                "turn" -> turns++
                "traded" -> trades++
                "roll" -> if (b.amount == 7) sevens++
                "play" -> cards++
            }
            seen = s.beatSeq
            if (deep && ticks % 29 == 0) {
                val round = Json.decodeFromString(SprawlState.serializer(), Json.encodeToString(SprawlState.serializer(), s))
                check(round == s) { "snapshot round trip changed the state in game $seed" }
                val tv = sizes.encodeToString(TvState.serializer(), e.tvState()).length
                val phone = ids.maxOf { sizes.encodeToString(PhoneState.serializer(), e.phoneState(it)).length }
                check(tv < 24_000 && phone < 12_000) { "payloads too big: tv $tv, phone $phone" }
            }
            for (id in ids.shuffled(rnd)) {
                if (rnd.nextDouble() > 0.45) continue // people take a few seconds to decide
                val ps = e.phoneState(id)
                val scr = ps.screen as? Screen.Sprawl ?: continue
                val payload = decide(scr, s, rnd) ?: continue
                e.action(id, "b${n++}", ps.round, payload) // refusals (races) are fine
            }
        }
        val end = last ?: error("no game")
        return Result(clock.now() / 60_000.0, turns, end.tally.maxOrNull() ?: 0, sawLast, trades, sevens, cards)
    }

    private fun invariants(s: SprawlState, seed: Long) {
        val b = s.board
        val g = b.geo
        fun fail(what: String): Nothing = error("game $seed, phase ${s.phase}: $what")
        val each = if (b.size == 0) 19 else 24
        (0 until 5).forEach { r -> if (s.bank[r] + s.seats.sumOf { it.hand[r] } != each) fail("resource $r doesn't add up") }
        s.seats.forEach { seat -> if (seat.hand.any { it < 0 }) fail("${seat.name} has a negative hand") }
        if (s.bank.any { it < 0 }) fail("the bank is negative")
        s.seats.indices.forEach { i ->
            if (SprawlRules.settlements(b, i) > SprawlRules.MAX_SETTLEMENTS) fail("too many settlements")
            if (SprawlRules.cities(b, i) > SprawlRules.MAX_CITIES) fail("too many cities")
            if (SprawlRules.roads(b, i) > SprawlRules.MAX_ROADS) fail("too many roads")
        }
        g.vertices.indices.filter { b.vOwner[it] >= 0 }.forEach { v ->
            if (g.vNeighbours[v].any { b.vOwner[it] >= 0 }) fail("buildings next door at $v")
        }
        b.eOwner.indices.filter { b.eOwner[it] >= 0 }.forEach { e ->
            val seat = b.eOwner[e]
            // A rival may settle mid-road later and cut it, so just check it touches the owner's network.
            val linked = g.edges[e].any { v -> b.vOwner[v] == seat || g.vEdges[v].any { it != e && b.eOwner[it] == seat } }
            if (!linked) fail("road $e floats")
        }
        if (s.phase == Sprawl.TRADE && s.trade == null) fail("trade phase with no trade")
        if (b.robber !in g.hexes.indices) fail("the Landlord is off the island")
        val devTotal = if (b.size == 0) 25 else 34
        if (s.deck.size + s.seats.sumOf { it.dev.size + it.knights } > devTotal) fail("dev cards appeared from nowhere")
    }

    // ---- the bots -----------------------------------------------------------------------------

    private fun payload(kind: String, vararg kv: Pair<String, Any>): JsonObject = buildJsonObject {
        put("kind", JsonPrimitive(kind))
        kv.forEach { (k, v) ->
            when (v) {
                is Int -> put(k, JsonPrimitive(v))
                is List<*> -> put(k, JsonArray(v.map { JsonPrimitive(it as Int) }))
                else -> put(k, JsonPrimitive(v.toString()))
            }
        }
    }

    private fun decide(scr: Screen.Sprawl, s: SprawlState, rnd: Random): JsonObject? {
        val me = scr.me ?: return null
        val p = scr.prompt
        val b = s.board
        return when (p.kind) {
            "setup" -> if (scr.spotKind == "vertex") payload("place", "target" to scr.spots.maxBy { SprawlRules.cornerScore(b, it) })
                else payload("place", "target" to scr.spots[rnd.nextInt(scr.spots.size)])
            "roll" -> {
                val knight = me.dev.firstOrNull { it.kind == "knight" && it.playable }
                val hurt = b.geo.hexVertices[b.robber].any { b.vOwner[it] == me.index }
                if (knight != null && (hurt || rnd.nextDouble() < 0.3)) payload("play", "card" to "knight") else payload("roll")
            }
            "main" -> main(scr, s, rnd) ?: payload("end")
            "discard" -> payload("discard", "cards" to SprawlRules.autoDiscard(me.hand, scr.discard))
            "robber" -> payload("robber", "target" to SprawlRules.autoRobber(b, me.index))
            "steal" -> scr.victims.firstOrNull()?.let { payload("steal", "victim" to it.id.toInt()) }
            "road2" -> scr.spots.firstOrNull()?.let { payload("place", "target" to it) }
            "pick" -> payload("pick", "res" to (0 until 5).minBy { me.hand[it] + rnd.nextDouble() })
            "trade" -> {
                val t = scr.trade ?: return null
                val need = t.get.indices.all { me.hand[it] >= t.get[it] }
                when {
                    need && rnd.nextDouble() < 0.5 -> payload("tradeReply", "trade" to t.id, "option" to "accept")
                    t.canCounter && rnd.nextDouble() < 0.2 && me.hand.sum() > 0 -> {
                        val big = me.hand.indices.maxBy { me.hand[it] }
                        val give = List(5) { if (it == big) 1 else 0 }
                        val get = List(5) { if (it != big && t.give[it] > 0) 1 else 0 }
                        if (get.sum() == 0) payload("tradeReply", "trade" to t.id, "option" to "reject")
                        else payload("trade", "counter" to t.id.toString(), "give" to give, "get" to get)
                    }
                    else -> payload("tradeReply", "trade" to t.id, "option" to "reject")
                }
            }
            else -> null
        }
    }

    /** City > settlement > dev card > road; then a card, a bank trade that completes a build, or an offer; else end. */
    private fun main(scr: Screen.Sprawl, s: SprawlState, rnd: Random): JsonObject? {
        val me = scr.me ?: return null
        val b = s.board
        val build = scr.build
        build.cities.firstOrNull()?.let { return payload("build", "what" to "city", "target" to it) }
        build.settlements.maxByOrNull { SprawlRules.cornerScore(b, it) }?.let { return payload("build", "what" to "settlement", "target" to it) }
        if (build.dev && rnd.nextDouble() < 0.6) return payload("buyDev")
        if (build.roads.isNotEmpty() && (SprawlRules.settlementSpots(b, me.index, setup = false).isEmpty() || rnd.nextDouble() < 0.3)) {
            return payload("build", "what" to "road", "target" to build.roads[rnd.nextInt(build.roads.size)])
        }
        me.dev.firstOrNull { it.playable && it.kind != "knight" }?.let { return payload("play", "card" to it.kind) }
        if (!scr.canTrade) return null
        // Trade the biggest pile with the bank for what's missing from a city, settlement or road.
        val wants = listOf(SprawlRules.CITY, SprawlRules.SETTLEMENT, SprawlRules.ROAD).firstNotNullOfOrNull { cost ->
            val missing = cost.indices.filter { me.hand[it] < cost[it] }
            if (missing.size == 1) missing[0] else null
        } ?: return null
        val give = (0 until 5).filter { it != wants && me.hand[it] >= me.ratios[it] }.maxByOrNull { me.hand[it] }
        if (give != null && scr.bank[wants] > 0) return payload("bank", "give" to give, "get" to wants)
        val spare = (0 until 5).filter { it != wants && me.hand[it] >= 2 }.maxByOrNull { me.hand[it] } ?: return null
        if (rnd.nextDouble() < 0.25) {
            return payload("trade", "to" to Sprawl.ANYONE, "give" to List(5) { if (it == spare) 1 else 0 }, "get" to List(5) { if (it == wants) 1 else 0 })
        }
        return null
    }

    private val sims = System.getenv("SPRAWL_SIMS")?.toIntOrNull() ?: 24

    @Test fun botsPlayWholeGamesWithoutBreakingTheRules() {
        val results = (0 until sims).map { g -> play(2_000L + g, 3 + g % 4, mapOf("minutes" to 30), deep = g < 8) }
        println("Sprawl sims ($sims games, 30-minute clock): median ${median(results.map { it.minutes })} min, " +
            "${results.count { it.lastRound }} went to a last round, ${results.sumOf { it.trades }} trades, " +
            "${results.sumOf { it.sevens }} sevens, ${results.sumOf { it.cards }} cards played")
        assertTrue(results.all { it.minutes <= 30 + 12 }, "a 30-minute game plus its last round: ${results.map { it.minutes }}")
        assertTrue(results.any { !it.lastRound }, "somebody reaches 8 points before time runs out")
    }

    @Test fun aFullGameFitsThePartyWindow() {
        val results = (0 until 6).map { play(9_000L + it, 4, mapOf("minutes" to 45), deep = false) }
        results.forEach { r ->
            println("45-minute game, 4 players: ${"%.1f".format(r.minutes)} min, ${r.turns} turns, winner ${r.winnerVp} VP, last round ${r.lastRound}, ${r.trades} trades")
        }
        assertTrue(results.all { it.minutes in 5.0..60.0 }, "games end by 45 minutes plus a last round: ${results.map { it.minutes }}")
    }

    private fun median(xs: List<Double>) = xs.sorted().let { "%.1f".format(it[it.size / 2]) }
}
