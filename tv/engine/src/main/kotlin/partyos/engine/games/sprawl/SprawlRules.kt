package partyos.engine.games.sprawl

import kotlin.math.abs

/** The base game's rules as pure functions over the board. Seats are indexes; hands are 5 resource counts. */
object SprawlRules {
    const val BRICK = 0
    const val WOOD = 1
    const val SHEEP = 2
    const val WHEAT = 3
    const val ORE = 4
    const val DESERT = 5
    const val NOBODY = -1
    const val SETTLEMENT_LEVEL = 1
    const val CITY_LEVEL = 2

    val ROAD = listOf(1, 1, 0, 0, 0)
    val SETTLEMENT = listOf(1, 1, 1, 1, 0)
    val CITY = listOf(0, 0, 0, 2, 3)
    val DEV = listOf(0, 0, 1, 1, 1)

    const val MAX_SETTLEMENTS = 5
    const val MAX_CITIES = 4
    const val MAX_ROADS = 15
    const val ROAD_AWARD = 5
    const val ARMY_AWARD = 3

    fun affords(hand: List<Int>, cost: List<Int>) = hand.indices.all { hand[it] >= cost[it] }
    fun minus(a: List<Int>, b: List<Int>) = a.indices.map { a[it] - b[it] }
    fun plus(a: List<Int>, b: List<Int>) = a.indices.map { a[it] + b[it] }

    /** Pips on a number token: how many of the 36 rolls hit it. */
    fun pips(n: Int) = if (n == 0) 0 else 6 - abs(7 - n)

    fun settlements(b: SBoard, seat: Int) = b.vOwner.indices.count { b.vOwner[it] == seat && b.vLevel[it] == SETTLEMENT_LEVEL }
    fun cities(b: SBoard, seat: Int) = b.vOwner.indices.count { b.vOwner[it] == seat && b.vLevel[it] == CITY_LEVEL }
    fun roads(b: SBoard, seat: Int) = b.eOwner.count { it == seat }

    /** Empty corners with no building next door; after setup they must also touch one of [seat]'s roads. */
    fun settlementSpots(b: SBoard, seat: Int, setup: Boolean): List<Int> {
        val g = b.geo
        return g.vertices.indices.filter { v ->
            b.vOwner[v] == NOBODY && g.vNeighbours[v].all { b.vOwner[it] == NOBODY } &&
                (setup || g.vEdges[v].any { b.eOwner[it] == seat })
        }
    }

    fun citySpots(b: SBoard, seat: Int) = b.vOwner.indices.filter { b.vOwner[it] == seat && b.vLevel[it] == SETTLEMENT_LEVEL }

    /**
     * Empty edges [seat] can build on: in setup ([from]) the ones touching that settlement, otherwise any edge with an
     * end at their building, or at an empty corner one of their roads reaches (a rival's building blocks the way).
     */
    fun roadSpots(b: SBoard, seat: Int, from: Int? = null): List<Int> {
        val g = b.geo
        if (from != null) return g.vEdges[from].filter { b.eOwner[it] == NOBODY }
        fun reaches(v: Int) = b.vOwner[v] == seat || (b.vOwner[v] == NOBODY && g.vEdges[v].any { b.eOwner[it] == seat })
        return g.edges.indices.filter { e -> b.eOwner[e] == NOBODY && g.edges[e].any { reaches(it) } }
    }

    /**
     * What each seat collects on [roll]: 1 per settlement and 2 per city on every matching hex but the Landlord's.
     * When the bank can't pay a resource to everyone owed it, nobody gets it, unless only one seat is owed it (they
     * get what's left).
     */
    fun production(b: SBoard, roll: Int, bank: List<Int>, seats: Int): List<List<Int>> {
        val g = b.geo
        val owed = List(5) { IntArray(seats) }
        b.numbers.indices.filter { b.numbers[it] == roll && it != b.robber && b.terrain[it] != DESERT }.forEach { h ->
            g.hexVertices[h].forEach { v -> if (b.vOwner[v] in 0 until seats) owed[b.terrain[h]][b.vOwner[v]] += b.vLevel[v] }
        }
        for (r in 0 until 5) {
            val total = owed[r].sum()
            if (total <= bank[r]) continue
            val takers = owed[r].indices.filter { owed[r][it] > 0 }
            if (takers.size == 1) owed[r][takers[0]] = bank[r] else owed[r].fill(0)
        }
        return (0 until seats).map { s -> (0 until 5).map { owed[it][s] } }
    }

    /** The longest trail of [seat]'s roads, never using a road twice; a rival's building ends a trail. */
    fun longestRoad(b: SBoard, seat: Int): Int {
        val g = b.geo
        val mine = b.eOwner.indices.filter { b.eOwner[it] == seat }
        if (mine.isEmpty()) return 0
        val used = BooleanArray(g.edges.size)
        fun walk(v: Int): Int {
            var best = 0
            for (e in g.vEdges[v]) {
                if (b.eOwner[e] != seat || used[e]) continue
                val w = g.edges[e].first { it != v }
                used[e] = true
                val blocked = b.vOwner[w] != NOBODY && b.vOwner[w] != seat
                best = maxOf(best, 1 + if (blocked) 0 else walk(w))
                used[e] = false
            }
            return best
        }
        return mine.flatMap { g.edges[it] }.distinct().maxOf { walk(it) }
    }

    /**
     * Who holds an award after a change: nobody below [min]; the holder keeps it on a tie; a sole leader takes it;
     * a tie with the holder out of it sets it aside.
     */
    fun holder(current: Int, values: List<Int>, min: Int): Int {
        val max = values.maxOrNull() ?: return NOBODY
        if (max < min) return NOBODY
        val leaders = values.indices.filter { values[it] == max }
        return when {
            current in leaders -> current
            leaders.size == 1 -> leaders[0]
            else -> NOBODY
        }
    }

    /** The bank rate for each resource: 4, 3 with any generic harbour, 2 at that resource's harbour. */
    fun ratios(b: SBoard, seat: Int): List<Int> {
        val g = b.geo
        val r = MutableList(5) { 4 }
        b.harbours.filter { h -> g.edges[h.edge].any { b.vOwner[it] == seat } }.forEach { h ->
            if (h.kind < 0) r.indices.forEach { r[it] = minOf(r[it], 3) } else r[h.kind] = 2
        }
        return r
    }

    fun discardCount(hand: List<Int>) = hand.sum().let { if (it > 7) it / 2 else 0 }

    /** [n] cards to discard, always from the biggest pile (the phone's fallback when time runs out). */
    fun autoDiscard(hand: List<Int>, n: Int): List<Int> {
        val left = hand.toMutableList()
        val out = MutableList(5) { 0 }
        repeat(n) {
            val r = left.indices.maxByOrNull { left[it] }!!
            if (left[r] == 0) return out
            left[r]--
            out[r]++
        }
        return out
    }

    /** How good a corner is to settle: its hexes' pips, plus a bonus for each different resource. */
    fun cornerScore(b: SBoard, v: Int): Int {
        val hs = b.geo.vHexes[v].filter { b.terrain[it] != DESERT }
        return hs.sumOf { pips(b.numbers[it]) } + 2 * hs.map { b.terrain[it] }.distinct().size
    }

    /** The best legal setup corner (the phone's fallback), or -1 if the island is full. */
    fun bestOpening(b: SBoard, seat: Int): Int =
        settlementSpots(b, seat, setup = true).maxWithOrNull(compareBy<Int> { cornerScore(b, it) }.thenByDescending { it }) ?: NOBODY

    /** Where the Landlord hurts others most without touching [seat]: the most rival pips (cities count double). */
    fun autoRobber(b: SBoard, seat: Int): Int {
        val g = b.geo
        fun score(h: Int): Int {
            val vs = g.hexVertices[h]
            if (vs.any { b.vOwner[it] == seat }) return -1
            return pips(b.numbers[h]) * vs.sumOf { if (b.vOwner[it] != NOBODY) b.vLevel[it] else 0 }
        }
        return g.hexes.indices.filter { it != b.robber }.maxWithOrNull(compareBy<Int> { score(it) }.thenByDescending { it })!!
    }

    /** Seats with a building on [hex] (not [seat]) who hold at least one card. */
    fun victims(b: SBoard, hex: Int, seat: Int, hands: List<List<Int>>): List<Int> =
        b.geo.hexVertices[hex].map { b.vOwner[it] }.filter { it != NOBODY && it != seat && hands.getOrNull(it)?.sum()?.let { n -> n > 0 } == true }.distinct().sorted()

    /** Victory points everyone can see: buildings and awards (not VP cards). */
    fun publicVp(b: SBoard, seat: Int, roadHolder: Int, armyHolder: Int) =
        settlements(b, seat) + 2 * cities(b, seat) + (if (roadHolder == seat) 2 else 0) + (if (armyHolder == seat) 2 else 0)
}
