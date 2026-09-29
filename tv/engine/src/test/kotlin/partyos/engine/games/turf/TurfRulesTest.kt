package partyos.engine.games.turf

import partyos.engine.games.turf.TurfRules.HOTEL
import partyos.engine.games.turf.TurfRules.NOBODY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurfRulesTest {
    private fun estate(vararg owned: Pair<Int, Int>) = Estate().let { e ->
        e.copy(owner = e.owner.toMutableList().also { o -> owned.forEach { (space, token) -> o[space] = token } })
    }
    private fun Estate.lv(vararg levels: Pair<Int, Int>) = copy(level = level.toMutableList().also { l -> levels.forEach { (s, v) -> l[s] = v } })
    private fun Estate.mort(vararg spaces: Int) = copy(mortgaged = mortgaged.toMutableList().also { m -> spaces.forEach { m[it] = true } })

    // ---- the board ---------------------------------------------------------------------

    /** Independent copy of the standard US numbers (Kau5hik46/monopoly-markov-chain board.us.json, MIT). */
    private val reference = mapOf(
        1 to (60 to listOf(2, 10, 30, 90, 160, 250)), 3 to (60 to listOf(4, 20, 60, 180, 320, 450)),
        6 to (100 to listOf(6, 30, 90, 270, 400, 550)), 8 to (100 to listOf(6, 30, 90, 270, 400, 550)),
        9 to (120 to listOf(8, 40, 100, 300, 450, 600)), 11 to (140 to listOf(10, 50, 150, 450, 625, 750)),
        13 to (140 to listOf(10, 50, 150, 450, 625, 750)), 14 to (160 to listOf(12, 60, 180, 500, 700, 900)),
        16 to (180 to listOf(14, 70, 200, 550, 750, 950)), 18 to (180 to listOf(14, 70, 200, 550, 750, 950)),
        19 to (200 to listOf(16, 80, 220, 600, 800, 1000)), 21 to (220 to listOf(18, 90, 250, 700, 875, 1050)),
        23 to (220 to listOf(18, 90, 250, 700, 875, 1050)), 24 to (240 to listOf(20, 100, 300, 750, 925, 1100)),
        26 to (260 to listOf(22, 110, 330, 800, 975, 1150)), 27 to (260 to listOf(22, 110, 330, 800, 975, 1150)),
        29 to (280 to listOf(24, 120, 360, 850, 1025, 1200)), 31 to (300 to listOf(26, 130, 390, 900, 1100, 1275)),
        32 to (300 to listOf(26, 130, 390, 900, 1100, 1275)), 34 to (320 to listOf(28, 150, 450, 1000, 1200, 1400)),
        37 to (350 to listOf(35, 175, 500, 1100, 1300, 1500)), 39 to (400 to listOf(50, 200, 600, 1400, 1700, 2000)),
    )
    private val houseCosts = mapOf(0 to 50, 1 to 50, 2 to 100, 3 to 100, 4 to 150, 5 to 150, 6 to 200, 7 to 200)

    @Test fun theBoardMatchesTheStandardNumbers() {
        assertEquals(40, TurfBoard.spaces.size)
        TurfBoard.spaces.forEachIndexed { i, s -> assertEquals(i, s.index) }
        val streets = TurfBoard.spaces.filter { it.kind == SpaceKind.STREET }
        assertEquals(reference.keys, streets.map { it.index }.toSet())
        for (s in streets) {
            val (price, rent) = reference.getValue(s.index)
            assertEquals(price, s.price, "price of ${s.index}")
            assertEquals(rent, s.rent, "rent of ${s.index}")
            assertEquals(houseCosts.getValue(s.group), s.houseCost, "house cost of ${s.index}")
            assertEquals(price / 2, s.mortgage)
        }
        assertEquals(listOf(5, 15, 25, 35), TurfBoard.railroads)
        assertEquals(listOf(12, 28), TurfBoard.utilities)
        assertTrue(TurfBoard.railroads.all { TurfBoard.spaces[it].price == 200 })
        assertTrue(TurfBoard.utilities.all { TurfBoard.spaces[it].price == 150 })
        assertEquals(listOf(2, 7, 17, 22, 33, 36), TurfBoard.spaces.filter { it.kind == SpaceKind.CHEST || it.kind == SpaceKind.CHANCE }.map { it.index }.sorted())
        assertEquals(200, TurfBoard.spaces[4].tax)
        assertEquals(100, TurfBoard.spaces[38].tax)
        assertEquals(listOf(2, 3, 3, 3, 3, 3, 3, 2), TurfBoard.groups.map { it.size })
        assertEquals(28, TurfBoard.deeds.size)
    }

    @Test fun theNamesFileIsValidAndFillsPlaceholders() {
        val names = TurfBoard.loadNames()
        assertEquals(emptyList(), TurfBoard.problems(names))
        assertEquals("Plot Twist", names.chance)
        assertEquals("Group Chat", names.chest)
        assertEquals("Go straight to Timeout.", TurfBoard.fill("Go straight to {space:10}.", names))
        val bad = names.copy(spaces = names.spaces.dropLast(1), chanceCards = names.chanceCards + "Advance to {space:77}")
        val problems = TurfBoard.problems(bad)
        assertTrue(problems.any { "need 40 spaces" in it })
        assertTrue(problems.any { "space 77" in it })
        assertTrue(problems.any { "chance cards" in it })
    }

    @Test fun decksHaveSixteenCardsEach() {
        assertEquals(16, TurfDecks.chance.size)
        assertEquals(16, TurfDecks.chest.size)
        assertEquals(2, TurfDecks.chance.count { it.kind == CardKind.NEAREST_RAIL })
        assertEquals(1, TurfDecks.chance.count { it.kind == CardKind.JAIL_FREE })
        assertEquals(1, TurfDecks.chest.count { it.kind == CardKind.JAIL_FREE })
        assertTrue((TurfDecks.chance + TurfDecks.chest).filter { it.kind == CardKind.ADVANCE }.all { it.space in 0 until 40 })
    }

    // ---- rent --------------------------------------------------------------------------

    @Test fun streetRentDoublesOnAFullUnimprovedSetThenFollowsTheHouseTable() {
        val lone = estate(1 to 0)
        assertEquals(2, TurfRules.rent(lone, 1, 7))
        val set = estate(1 to 0, 3 to 0)
        assertEquals(4, TurfRules.rent(set, 1, 7))
        assertEquals(8, TurfRules.rent(set, 3, 7))
        // A mortgaged set member still lets the others double; the mortgaged one charges nothing.
        assertEquals(4, TurfRules.rent(set.mort(3), 1, 7))
        assertEquals(0, TurfRules.rent(set.mort(3), 3, 7))
        assertEquals(10, TurfRules.rent(set.lv(1 to 1), 1, 7))
        assertEquals(30, TurfRules.rent(set.lv(1 to 2), 1, 7))
        assertEquals(90, TurfRules.rent(set.lv(1 to 3), 1, 7))
        assertEquals(250, TurfRules.rent(set.lv(1 to HOTEL), 1, 7), "a hotel charges the hotel rent")
        assertEquals(2000, TurfRules.rent(estate(37 to 1, 39 to 1).lv(39 to HOTEL), 39, 2))
        assertEquals(0, TurfRules.rent(Estate(), 1, 7), "unowned")
    }

    @Test fun railroadsAndUtilities() {
        assertEquals(25, TurfRules.rent(estate(5 to 0), 5, 4))
        assertEquals(50, TurfRules.rent(estate(5 to 0, 15 to 0), 5, 4))
        assertEquals(100, TurfRules.rent(estate(5 to 0, 15 to 0, 25 to 0), 5, 4))
        assertEquals(200, TurfRules.rent(estate(5 to 0, 15 to 0, 25 to 0, 35 to 0), 35, 4))
        assertEquals(50, TurfRules.rent(estate(5 to 0), 5, 4, doubleRail = true))
        assertEquals(50, TurfRules.rent(estate(5 to 0, 15 to 1), 5, 4, doubleRail = true), "another owner's railroad doesn't count")
        assertEquals(28, TurfRules.rent(estate(12 to 0), 12, 7))
        assertEquals(70, TurfRules.rent(estate(12 to 0, 28 to 0), 28, 7))
        assertEquals(70, TurfRules.rent(estate(12 to 0), 12, 7, utilTen = true))
    }

    // ---- building ----------------------------------------------------------------------

    @Test fun buildingNeedsTheSetAndGoesEvenly() {
        assertEquals("NEED_SET", TurfRules.buildRefusal(estate(6 to 0, 8 to 0), 0, 6))
        assertEquals("NOT_YOURS", TurfRules.buildRefusal(estate(6 to 0, 8 to 0, 9 to 0), 1, 6))
        assertEquals("NOT_A_STREET", TurfRules.buildRefusal(estate(5 to 0), 0, 5))
        var e = estate(6 to 0, 8 to 0, 9 to 0)
        assertEquals("MORTGAGED_IN_SET", TurfRules.buildRefusal(e.mort(9), 0, 6))
        assertNull(TurfRules.buildRefusal(e, 0, 6))
        e = TurfRules.build(e, 6)
        assertEquals("BUILD_EVENLY", TurfRules.buildRefusal(e, 0, 6))
        e = TurfRules.build(TurfRules.build(e, 8), 9)
        assertEquals(29, e.housesLeft)
        repeat(2) { for (s in listOf(6, 8, 9)) e = TurfRules.build(e, s) }
        assertEquals(3, e.level[6])
        assertEquals(23, e.housesLeft)
        e = TurfRules.build(e, 6)
        assertEquals(HOTEL, e.level[6], "the fourth level is the hotel")
        assertEquals(26, e.housesLeft, "the hotel sends its 3 houses back to the bank")
        assertEquals(11, e.hotelsLeft)
        assertEquals("MAX_BUILT", TurfRules.buildRefusal(e, 0, 6))
        assertNull(TurfRules.buildRefusal(e, 0, 8), "the other two catch up to hotels")
        e = TurfRules.build(TurfRules.build(e, 8), 9)
        assertEquals("MAX_BUILT", TurfRules.buildRefusal(e, 0, 6))
        assertEquals("MUST_SELL_BUILDINGS", TurfRules.mortgageRefusal(e, 0, 6))
    }

    @Test fun theBankRunsOutOfHousesAndHotels() {
        val e = estate(6 to 0, 8 to 0, 9 to 0)
        assertEquals("NO_HOUSES_LEFT", TurfRules.buildRefusal(e.copy(housesLeft = 0), 0, 6))
        val three = e.lv(6 to 3, 8 to 3, 9 to 3)
        assertEquals("NO_HOTELS_LEFT", TurfRules.buildRefusal(three.copy(hotelsLeft = 0), 0, 6))
        assertNull(TurfRules.buildRefusal(three.copy(housesLeft = 0), 0, 6), "a hotel needs no spare house")
    }

    @Test fun sellingGoesEvenlyAtHalfPriceAndHotelsBreakDown() {
        val e = estate(6 to 0, 8 to 0, 9 to 0).lv(6 to HOTEL, 8 to 3, 9 to 3).copy(housesLeft = 20, hotelsLeft = 11)
        assertEquals("SELL_EVENLY", TurfRules.sellRefusal(e, 0, 8))
        val (after, refund) = TurfRules.sell(e, 6)
        assertEquals(3, after.level[6])
        assertEquals(17, after.housesLeft)
        assertEquals(12, after.hotelsLeft)
        assertEquals(25, refund)
        // Only one house left in the bank: the hotel breaks down to one house and the rest is sold.
        val (short, shortRefund) = TurfRules.sell(e.copy(housesLeft = 1), 6)
        assertEquals(1, short.level[6])
        assertEquals(0, short.housesLeft)
        assertEquals(75, shortRefund)
        val (house, houseRefund) = TurfRules.sell(e.lv(6 to 3), 8)
        assertEquals(2, house.level[8])
        assertEquals(25, houseRefund)
        assertEquals("NOTHING_TO_SELL", TurfRules.sellRefusal(estate(1 to 0, 3 to 0), 0, 1))
    }

    @Test fun repairsCountHousesAndHotels() {
        val e = estate(6 to 0, 8 to 0, 9 to 0, 1 to 0, 3 to 0).lv(6 to HOTEL, 8 to 3, 9 to 3, 1 to 2, 3 to 1)
        assertEquals(9 to 1, TurfRules.buildings(e, 0))
    }

    // ---- mortgages and worth -----------------------------------------------------------

    @Test fun mortgagesPayHalfAndCostTenPercentToLift() {
        assertEquals(33, TurfRules.unmortgageCost(1))
        assertEquals(3, TurfRules.interest(1))
        assertEquals(193, TurfRules.unmortgageCost(37), "10% of 175 rounds up")
        assertEquals(220, TurfRules.unmortgageCost(39))
        val (m, got) = TurfRules.mortgage(estate(39 to 2), 39)
        assertEquals(200, got)
        assertEquals("ALREADY_MORTGAGED", TurfRules.mortgageRefusal(m, 2, 39))
        assertNull(TurfRules.unmortgageRefusal(m, 2, 39))
        assertFalse(TurfRules.unmortgage(m, 39).mortgaged[39])
        assertEquals("NOT_MORTGAGED", TurfRules.unmortgageRefusal(estate(39 to 2), 2, 39))
        assertEquals("NOT_A_DEED", TurfRules.mortgageRefusal(Estate(), 0, 0))
    }

    @Test fun netWorthCountsCashPlacesAndBuildings() {
        val e = estate(6 to 0, 8 to 0, 9 to 0, 5 to 0, 39 to 1).lv(6 to HOTEL, 8 to 3, 9 to 3).mort(5)
        // 100+100+120 printed, railroad mortgaged at 100, buildings (4+3+3) x 50.
        assertEquals(1000 + 320 + 100 + 500, TurfRules.netWorth(e, 0, 1000))
        assertEquals(400, TurfRules.netWorth(e, 1, 0))
        assertEquals(10 * 25 + 60 + 50 + 50, TurfRules.liquidValue(e, 0))
    }

    @Test fun raisingCashSellsBuildingsThenMortgagesTheLeastUseful() {
        val e = estate(6 to 0, 8 to 0, 9 to 0, 12 to 0, 21 to 0).lv(6 to 1, 8 to 1, 9 to 1)
        val (after, steps) = TurfRules.raiseCash(e, 0, 60)
        assertEquals(listOf("sell", "sell", "sell"), steps.map { it.kind }, "buildings go first")
        assertTrue(steps.sumOf { it.amount } >= 60)
        assertEquals(0, after.level.sum())
        val (_, more) = TurfRules.raiseCash(after, 0, 100)
        assertEquals(listOf(12, 21), more.map { it.space }, "the utility, then the lone street, before the set")
        val (_, everything) = TurfRules.raiseCash(e, 0, 100_000)
        assertEquals(TurfRules.liquidValue(e, 0), everything.sumOf { it.amount })
    }

    // ---- movement ----------------------------------------------------------------------

    @Test fun movementHelpers() {
        assertEquals(5, TurfRules.nearest(36, TurfBoard.railroads), "from the last Plot Twist, the nearest ride wraps to 5")
        assertEquals(15, TurfRules.nearest(7, TurfBoard.railroads))
        assertEquals(28, TurfRules.nearest(22, TurfBoard.utilities))
        assertEquals(12, TurfRules.nearest(36, TurfBoard.utilities))
        assertEquals(listOf(38, 39, 0, 1), TurfRules.path(37, 4))
        assertEquals(listOf(1, 0, 39), TurfRules.path(2, -3))
        assertEquals(4, TurfRules.distance(38, 2))
    }

    // ---- trades ------------------------------------------------------------------------

    @Test fun tradesFollowTheOfficialLimits() {
        val e = estate(1 to 0, 3 to 1, 6 to 0, 8 to 0, 9 to 0).lv(6 to 1, 8 to 1, 9 to 1).mort(1)
        fun side(t: Int, cash: Int, deeds: List<Int>, give: Int = 0, cards: Int = 0, giveCards: Int = 0) =
            TurfRules.Side(t, cash, cards, deeds, give, giveCards)
        assertNull(TurfRules.tradeRefusal(e, side(0, 500, listOf(1)), side(1, 500, listOf(3))))
        assertEquals("EMPTY_TRADE", TurfRules.tradeRefusal(e, side(0, 500, emptyList()), side(1, 500, emptyList())))
        assertEquals("NOT_YOURS", TurfRules.tradeRefusal(e, side(0, 500, listOf(3)), side(1, 500, emptyList())))
        assertEquals("MUST_SELL_BUILDINGS", TurfRules.tradeRefusal(e, side(0, 500, listOf(6)), side(1, 500, emptyList())))
        assertEquals("CANT_AFFORD", TurfRules.tradeRefusal(e, side(0, 500, emptyList(), give = 600), side(1, 500, listOf(3))))
        assertEquals("NO_CARD", TurfRules.tradeRefusal(e, side(0, 500, emptyList(), giveCards = 1), side(1, 500, listOf(3))))
        // Taking a mortgaged place means paying its $3 interest now: a broke partner can't.
        assertEquals("CANT_AFFORD", TurfRules.tradeRefusal(e, side(0, 500, listOf(1)), side(1, 2, emptyList())))
        assertNull(TurfRules.tradeRefusal(e, side(0, 500, listOf(1), give = 10), side(1, 0, emptyList())), "the cash in the deal covers it")
        assertEquals("BAD_TRADE", TurfRules.tradeRefusal(e, side(0, 500, listOf(1)), side(0, 500, emptyList())))
        assertTrue(TurfRules.completesSet(estate(1 to 0), 0, 3))
        assertFalse(TurfRules.completesSet(estate(1 to 1), 0, 3))
        assertEquals(NOBODY, Estate().owner[1])
    }

    // ---- dice --------------------------------------------------------------------------

    @Test fun diceAreDeterministicPerDrawAndFair() {
        assertEquals(TurfDice.roll(42, 3, true), TurfDice.roll(42, 3, true))
        assertNotEquals((0 until 20).map { TurfDice.roll(42, it, true) }.toSet().size, 1)
        val rolls = (0 until 60_000).map { TurfDice.roll(7, it, true) }
        val faces = rolls.groupingBy { it.a }.eachCount()
        assertEquals((1..6).toSet(), faces.keys)
        faces.values.forEach { assertTrue(it in 9_000..11_000, "white die face count $it") }
        val speed = rolls.groupingBy { it.speed }.eachCount()
        assertTrue(speed.getValue(Roll.SCOUT) in 18_000..22_000, "two scout faces")
        assertTrue(speed.getValue(Roll.BUS) in 9_000..11_000)
        assertTrue((rolls.count { it.doubles } / 60_000.0) in 0.15..0.18)
        assertTrue(TurfDice.roll(1, 1, false).speed == 0)
    }

    @Test fun rollHelpers() {
        val r = Roll(3, 3, 3)
        assertTrue(r.doubles && r.triples)
        assertEquals(9, r.move)
        assertFalse(Roll(3, 3, Roll.BUS).triples)
        assertEquals(6, Roll(2, 4, Roll.SCOUT).move)
        assertEquals(6, Roll(2, 4, Roll.BUS).pips)
        assertEquals(listOf(2, 4), Roll(2, 4).dice)
    }
}
