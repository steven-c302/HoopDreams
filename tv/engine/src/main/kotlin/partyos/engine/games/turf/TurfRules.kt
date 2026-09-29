package partyos.engine.games.turf

import kotlinx.serialization.Serializable

/**
 * Who owns what on the board. Index = space. [owner] is a token index or [TurfRules.NOBODY]; [level] is 0..3 houses
 * or [TurfRules.HOTEL]. Plain lists so a snapshot round-trips through JSON unchanged.
 */
@Serializable
data class Estate(
    val owner: List<Int> = List(TurfBoard.SIZE) { TurfRules.NOBODY },
    val level: List<Int> = List(TurfBoard.SIZE) { 0 },
    val mortgaged: List<Boolean> = List(TurfBoard.SIZE) { false },
    val housesLeft: Int = TurfRules.HOUSES,
    val hotelsLeft: Int = TurfRules.HOTELS,
) {
    fun owned(token: Int) = TurfBoard.deeds.filter { owner[it] == token }
}

/** One step of raising cash: sell a building level on [space], or mortgage it. */
data class CashStep(val kind: String, val space: Int, val amount: Int)

/** Pure board rules: rent, building, mortgages, net worth, trades. No state beyond what's passed in. */
object TurfRules {
    const val NOBODY = -1
    /** A hotel replaces 3 houses (the official short-game rule). */
    const val HOTEL = 4
    const val HOUSES_PER_HOTEL = 3
    const val HOUSES = 32
    const val HOTELS = 12

    private fun sp(i: Int) = TurfBoard.spaces[i]

    /** True when [token] owns every street in [space]'s colour group. */
    fun ownsGroup(e: Estate, token: Int, space: Int): Boolean =
        token != NOBODY && sp(space).kind == SpaceKind.STREET && TurfBoard.groupOf(space).all { e.owner[it] == token }

    /** True if buying [space] would give [token] the whole colour group. */
    fun completesSet(e: Estate, token: Int, space: Int): Boolean =
        sp(space).kind == SpaceKind.STREET && TurfBoard.groupOf(space).all { it == space || e.owner[it] == token }

    /**
     * Rent owed for landing on [space]. [pips] is the dice total (utilities); [doubleRail] and [utilTen] are the
     * nearest-railroad and nearest-utility cards. Mortgaged or unowned: 0.
     */
    fun rent(e: Estate, space: Int, pips: Int, doubleRail: Boolean = false, utilTen: Boolean = false): Int {
        val owner = e.owner[space]
        if (owner == NOBODY || e.mortgaged[space]) return 0
        val s = sp(space)
        return when (s.kind) {
            SpaceKind.STREET -> when (val lvl = e.level[space]) {
                0 -> s.rent[0] * (if (ownsGroup(e, owner, space)) 2 else 1)
                HOTEL -> s.rent[5]
                else -> s.rent[lvl]
            }
            SpaceKind.RAILROAD -> {
                val n = TurfBoard.railroads.count { e.owner[it] == owner }
                (25 shl (n - 1)) * (if (doubleRail) 2 else 1)
            }
            SpaceKind.UTILITY -> {
                val n = TurfBoard.utilities.count { e.owner[it] == owner }
                pips * (if (utilTen || n == 2) 10 else 4)
            }
            else -> 0
        }
    }

    // ---- building ---------------------------------------------------------------------

    /** Why [token] can't add a building level to [space] right now, or null if they can. */
    fun buildRefusal(e: Estate, token: Int, space: Int): String? {
        val s = sp(space)
        if (s.kind != SpaceKind.STREET) return "NOT_A_STREET"
        if (e.owner[space] != token) return "NOT_YOURS"
        if (!ownsGroup(e, token, space)) return "NEED_SET"
        val group = TurfBoard.groupOf(space)
        if (group.any { e.mortgaged[it] }) return "MORTGAGED_IN_SET"
        val lvl = e.level[space]
        if (lvl >= HOTEL) return "MAX_BUILT"
        if (lvl > group.minOf { e.level[it] }) return "BUILD_EVENLY"
        if (lvl < HOUSES_PER_HOTEL && e.housesLeft < 1) return "NO_HOUSES_LEFT"
        if (lvl == HOUSES_PER_HOTEL && e.hotelsLeft < 1) return "NO_HOTELS_LEFT"
        return null
    }

    /** Adds one level (a house, or the hotel that returns 3 houses to the bank). Cost: the space's house cost. */
    fun build(e: Estate, space: Int): Estate {
        val lvl = e.level[space]
        return if (lvl == HOUSES_PER_HOTEL) {
            e.copy(level = e.level.set(space, HOTEL), housesLeft = e.housesLeft + HOUSES_PER_HOTEL, hotelsLeft = e.hotelsLeft - 1)
        } else {
            e.copy(level = e.level.set(space, lvl + 1), housesLeft = e.housesLeft - 1)
        }
    }

    fun sellRefusal(e: Estate, token: Int, space: Int): String? {
        if (e.owner[space] != token) return "NOT_YOURS"
        val lvl = e.level[space]
        if (lvl == 0) return "NOTHING_TO_SELL"
        if (lvl < TurfBoard.groupOf(space).maxOf { e.level[it] }) return "SELL_EVENLY"
        return null
    }

    /**
     * Sells one level back at half price. A hotel breaks back into 3 houses, or as many as the bank has left (the
     * rest are sold too). Returns the new estate and the refund.
     */
    fun sell(e: Estate, space: Int): Pair<Estate, Int> {
        val half = sp(space).houseCost / 2
        val lvl = e.level[space]
        return if (lvl == HOTEL) {
            val down = minOf(HOUSES_PER_HOTEL, e.housesLeft)
            e.copy(level = e.level.set(space, down), housesLeft = e.housesLeft - down, hotelsLeft = e.hotelsLeft + 1) to (HOTEL - down) * half
        } else {
            e.copy(level = e.level.set(space, lvl - 1), housesLeft = e.housesLeft + 1) to half
        }
    }

    /** Houses and hotels [token] owns (for repair cards). */
    fun buildings(e: Estate, token: Int): Pair<Int, Int> {
        val mine = e.owned(token)
        return mine.sumOf { if (e.level[it] in 1..HOUSES_PER_HOTEL) e.level[it] else 0 } to mine.count { e.level[it] == HOTEL }
    }

    // ---- mortgages --------------------------------------------------------------------

    fun interest(space: Int) = (sp(space).mortgage + 9) / 10
    fun unmortgageCost(space: Int) = sp(space).mortgage + interest(space)

    fun mortgageRefusal(e: Estate, token: Int, space: Int): String? {
        if (!sp(space).buyable) return "NOT_A_DEED"
        if (e.owner[space] != token) return "NOT_YOURS"
        if (e.mortgaged[space]) return "ALREADY_MORTGAGED"
        if (TurfBoard.groupOf(space).any { e.level[it] > 0 }) return "MUST_SELL_BUILDINGS"
        return null
    }

    fun mortgage(e: Estate, space: Int): Pair<Estate, Int> = e.copy(mortgaged = e.mortgaged.set(space, true)) to sp(space).mortgage

    fun unmortgageRefusal(e: Estate, token: Int, space: Int): String? {
        if (e.owner[space] != token) return "NOT_YOURS"
        if (!e.mortgaged[space]) return "NOT_MORTGAGED"
        return null
    }

    fun unmortgage(e: Estate, space: Int): Estate = e.copy(mortgaged = e.mortgaged.set(space, false))

    // ---- worth and cash ---------------------------------------------------------------

    /** Cash + places at printed price (half if mortgaged) + buildings at cost (a hotel = 4 house payments). */
    fun netWorth(e: Estate, token: Int, cash: Int): Int = cash + e.owned(token).sumOf { i ->
        val s = sp(i)
        (if (e.mortgaged[i]) s.mortgage else s.price) + e.level[i] * s.houseCost
    }

    /** What selling every building and mortgaging every place would raise. */
    fun liquidValue(e: Estate, token: Int): Int = e.owned(token).sumOf { i ->
        val s = sp(i)
        e.level[i] * s.houseCost / 2 + (if (e.mortgaged[i]) 0 else s.mortgage)
    }

    /**
     * How to raise [need] more cash automatically: sell buildings evenly (priciest first), then mortgage utilities,
     * railroads, lone streets and finally set members, cheapest first. Stops once [need] is covered.
     */
    fun raiseCash(e0: Estate, token: Int, need: Int): Pair<Estate, List<CashStep>> {
        var e = e0
        var raised = 0
        val steps = mutableListOf<CashStep>()
        while (raised < need) {
            val sellable = e.owned(token).filter { sellRefusal(e, token, it) == null }
            if (sellable.isNotEmpty()) {
                val pick = sellable.maxWith(compareBy<Int> { sp(it).houseCost }.thenBy { it })
                val (next, got) = sell(e, pick)
                e = next; raised += got; steps += CashStep("sell", pick, got)
                continue
            }
            val mortgageable = e.owned(token).filter { mortgageRefusal(e, token, it) == null }
            if (mortgageable.isEmpty()) break
            val pick = mortgageable.minWith(compareBy<Int>({ mortgageRank(e, token, it) }, { sp(it).price }, { it }))
            val (next, got) = mortgage(e, pick)
            e = next; raised += got; steps += CashStep("mortgage", pick, got)
        }
        return e to steps
    }

    private fun mortgageRank(e: Estate, token: Int, i: Int) = when {
        sp(i).kind == SpaceKind.UTILITY -> 0
        sp(i).kind == SpaceKind.RAILROAD -> 1
        !ownsGroup(e, token, i) -> 2
        else -> 3
    }

    // ---- movement ---------------------------------------------------------------------

    /** Spaces forward from [from] to [to]. */
    fun distance(from: Int, to: Int) = ((to - from) % TurfBoard.SIZE + TurfBoard.SIZE) % TurfBoard.SIZE

    /** The first of [targets] after [from], moving forward (a full lap if [from] is the only one). */
    fun nearest(from: Int, targets: List<Int>): Int = targets.minBy { t -> distance(from, t).let { if (it == 0) TurfBoard.SIZE else it } }

    /** The spaces a token hops through moving [steps] forward (or back when negative), ending on the landing space. */
    fun path(from: Int, steps: Int): List<Int> =
        if (steps >= 0) (1..steps).map { (from + it) % TurfBoard.SIZE }
        else (1..-steps).map { ((from - it) % TurfBoard.SIZE + TurfBoard.SIZE) % TurfBoard.SIZE }

    // ---- trades -----------------------------------------------------------------------

    /** One side of a proposed trade: what [token] holds ([cash], [cards]) and hands over ([deeds], [giveCash], [giveCards]). */
    data class Side(val token: Int, val cash: Int, val cards: Int, val deeds: List<Int>, val giveCash: Int, val giveCards: Int)

    /**
     * Why a trade can't happen, or null. Official rules: only unimproved colour groups trade, and whoever takes a
     * mortgaged place pays its 10% interest at once, so each side must be able to cover that after the swap.
     */
    fun tradeRefusal(e: Estate, a: Side, b: Side): String? {
        if (a.token == b.token) return "BAD_TRADE"
        if (a.deeds.isEmpty() && b.deeds.isEmpty() && a.giveCash == 0 && b.giveCash == 0 && a.giveCards == 0 && b.giveCards == 0) return "EMPTY_TRADE"
        val all = a.deeds + b.deeds
        if (all.toSet().size != all.size) return "BAD_TRADE"
        if (all.any { it !in 0 until TurfBoard.SIZE || !sp(it).buyable }) return "BAD_TRADE"
        if (a.deeds.any { e.owner[it] != a.token } || b.deeds.any { e.owner[it] != b.token }) return "NOT_YOURS"
        if (all.any { d -> TurfBoard.groupOf(d).any { e.level[it] > 0 } }) return "MUST_SELL_BUILDINGS"
        if (a.giveCash < 0 || b.giveCash < 0 || a.giveCards < 0 || b.giveCards < 0) return "BAD_TRADE"
        if (a.giveCash > a.cash || b.giveCash > b.cash) return "CANT_AFFORD"
        if (a.giveCards > a.cards || b.giveCards > b.cards) return "NO_CARD"
        val aInterest = b.deeds.filter { e.mortgaged[it] }.sumOf { interest(it) }
        val bInterest = a.deeds.filter { e.mortgaged[it] }.sumOf { interest(it) }
        if (a.cash - a.giveCash + b.giveCash < aInterest || b.cash - b.giveCash + a.giveCash < bInterest) return "CANT_AFFORD"
        return null
    }

    private fun <T> List<T>.set(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }
}
