package partyos.engine.games.sprawl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PhoneState
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.TvState
import partyos.engine.add
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bots play whole games of Sprawl through the real engine at human-ish pace, checking the island's invariants every
 * second of game time; also estimates how long a party game runs. Heuristics after Catanatron's rule-based bot.
 * `SPRAWL_SIMS=500 ./gradlew :engine:test --tests '*SprawlSimulationTest*' --rerun-tasks` plays more games.
 */
class SprawlSimulationTest {
    private val registry = GameRegistry(listOf(Sprawl()))
    private val sizes = Json { encodeDefaults = true }

    data class Result(val minutes: Double, val turns: Int, val winnerVp: Int, val lastRound: Boolean, val trades: Int, val sevens: Int, val cards: Int)

    private fun state(e: PartyEngine) = e.snapshot().game?.state?.let { Json.decodeFromJsonElement(SprawlState.serializer(), it) }

    private fun play(seed: Long, players: Int, opts: Map<String, Int>, deep: Boolean, chaos: Boolean = false): Result {
        val clock = FakeClock(0)
        val e = PartyEngine(clock, SeededEntropy(seed), registry)
        val ids = (1..players).map { e.add("P$it") }.toMutableList()
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
            check(++ticks < 4 * 3600) {
                val st = e.tvState()
                "game $seed never ended (phase ${last?.phase}, paused ${st.stage?.paused} ${st.stage?.pauseReason}, remaining ${st.stage?.remainingMs}, " +
                    "connected ${st.players.count { it.connected }}/${st.players.size}, seats gone ${last?.seats?.count { it.gone }})"
            }
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
            if (chaos) {
                chaos(e, ids, rnd, n++)
                // Reconnecting does not dismiss an engine pause: the host presses Resume when the party is back.
                val tv = e.tvState()
                if (tv.stage?.paused == true && tv.players.count { it.connected } >= 2) {
                    assertEquals(ActionResult.Ack, e.host(HostCmd.Resume), "host resume in game $seed")
                }
                state(e)?.let { invariants(it, seed) }
            }
            for (id in ids.shuffled(rnd)) {
                if (rnd.nextDouble() > (if (chaos) 0.15 else 0.45)) continue // people take a few seconds to decide
                val ps = e.phoneState(id)
                val scr = ps.screen as? Screen.Sprawl ?: continue
                val payload = decide(scr, s, rnd) ?: continue
                e.action(id, "b${n++}", ps.round, payload) // refusals (races) are fine
            }
        }
        val end = last ?: error("no game")
        return Result(clock.now() / 60_000.0, turns, end.tally.maxOrNull() ?: 0, sawLast, trades, sevens, cards)
    }

    /**
     * Party chaos: phones drop and come back, someone gets kicked now and then, and people mash buttons with nonsense
     * (bad targets, wrong turns, junk trades). Actions may be accepted or refused, but must preserve the invariants.
     */
    private fun chaos(e: PartyEngine, ids: MutableList<PlayerId>, rnd: Random, n: Int) {
        val id = ids[rnd.nextInt(ids.size)]
        when {
            rnd.nextDouble() < 0.02 -> e.setPresence(id, rnd.nextBoolean())
            rnd.nextDouble() < 0.0015 && ids.size > 2 -> { e.kick(id); ids.remove(id); return }
        }
        if (rnd.nextDouble() < 0.3) {
            val ps = e.phoneState(id)
            val r = { rnd.nextInt(-3, 130) }
            val cards = { List(rnd.nextInt(0, 7)) { rnd.nextInt(-1, 4) } }
            val junk = when (rnd.nextInt(12)) {
                0 -> payload("build", "what" to listOf("road", "settlement", "city", "castle")[rnd.nextInt(4)], "target" to r())
                1 -> payload("place", "target" to r())
                2 -> payload("robber", "target" to r())
                3 -> payload("steal", "victim" to rnd.nextInt(-2, 8))
                4 -> payload("pick", "res" to rnd.nextInt(-1, 7))
                5 -> payload("bank", "give" to rnd.nextInt(-1, 6), "get" to rnd.nextInt(-1, 6))
                6 -> payload("trade", "to" to rnd.nextInt(-3, 8), "give" to cards(), "get" to cards())
                7 -> payload("tradeReply", "trade" to rnd.nextInt(0, 60), "option" to listOf("accept", "reject", "maybe")[rnd.nextInt(3)])
                8 -> payload("discard", "cards" to cards())
                9 -> payload("play", "card" to listOf("knight", "road", "plenty", "mono", "vp", "joker")[rnd.nextInt(6)])
                10 -> payload("peek", "what" to listOf("vertex", "edge", "hex", "moon")[rnd.nextInt(4)], "target" to r())
                else -> payload(listOf("roll", "end", "buyDev", "tradeCancel", "")[rnd.nextInt(5)], "trade" to rnd.nextInt(0, 60))
            }
            e.action(id, "c$n", if (rnd.nextBoolean()) ps.round else rnd.nextInt(0, 400), junk)
        }
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
        s.seats.filter { it.gone }.forEach { if (it.hand.sum() > 0 || it.dev.isNotEmpty()) fail("${it.name} left but still holds cards") }
        if (s.phase in setOf(Sprawl.ROLL, Sprawl.MAIN, Sprawl.ROBBER, Sprawl.STEAL, Sprawl.ROAD2, Sprawl.PICK) && s.seats[s.turn].gone) fail("a seat that left is taking a turn")
        s.trade?.let { t -> if (s.seats[t.from].gone || (t.to >= 0 && s.seats[t.to].gone)) fail("a trade with a seat that left") }
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

    @Test fun chaosNeverBreaksTheGame() {
        val results = (0 until sims).map { g ->
            val opts = mutableMapOf("minutes" to 3 + g % 8, "vp" to if (g % 3 == 0) 10 else 8, "drinks" to g % 2)
            play(5_000L + g, 3 + g % 4, opts, deep = g < 6, chaos = true)
        }
        println("Sprawl chaos ($sims games): ${results.count { it.lastRound }} last rounds, median ${median(results.map { it.minutes })} min")
        assertTrue(results.any { it.lastRound }, "short clocks reach a last round")
    }

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
