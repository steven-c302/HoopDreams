package partyos.engine.games.sprawl

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SprawlRulesTest {
    private val g = SprawlGeometry.of(0)

    /** An empty base island: every hex is fields with an 8, no harbours, the robber off to the side. */
    private fun blank(size: Int = 0): SBoard {
        val geo = SprawlGeometry.of(size)
        return SBoard(
            size = size, terrain = List(geo.hexes.size) { 3 }, numbers = List(geo.hexes.size) { 8 }, names = List(geo.hexes.size) { "H$it" },
            harbours = emptyList(), robber = -1, vOwner = List(geo.vertices.size) { -1 }, vLevel = List(geo.vertices.size) { 0 }, eOwner = List(geo.edges.size) { -1 },
        )
    }

    private fun SBoard.settle(v: Int, seat: Int, level: Int = 1) =
        copy(vOwner = vOwner.toMutableList().also { it[v] = seat }, vLevel = vLevel.toMutableList().also { it[v] = level })

    private fun SBoard.road(e: Int, seat: Int) = copy(eOwner = eOwner.toMutableList().also { it[e] = seat })

    private fun edge(a: Int, b: Int) = g.edges.indexOf(listOf(a, b).sorted())

    /** A simple path of [n] edges from [start], never revisiting a corner. */
    private fun chain(start: Int, n: Int): Pair<List<Int>, List<Int>> {
        val vs = mutableListOf(start)
        // Head for the corner with the most room, so the path never dead-ends on the coast.
        repeat(n) { vs += g.vNeighbours[vs.last()].filter { it !in vs }.maxBy { w -> g.vNeighbours[w].count { it !in vs } * 10 + g.vHexes[w].size } }
        return vs to vs.zipWithNext { a, b -> edge(a, b) }
    }

    @Test fun settlementsKeepTheirDistance() {
        val b = blank().settle(20, 0)
        val spots = SprawlRules.settlementSpots(b, 1, setup = true)
        assertFalse(20 in spots)
        g.vNeighbours[20].forEach { assertFalse(it in spots, "neighbour $it of a settlement is open") }
        assertTrue(spots.size < g.vertices.size - 1)
    }

    @Test fun laterSettlementsNeedYourRoadTwoStepsOut() {
        val (vs, es) = chain(20, 2)
        var b = blank().settle(vs[0], 0)
        assertTrue(SprawlRules.settlementSpots(b, 0, setup = false).isEmpty())
        es.forEach { b = b.road(it, 0) }
        assertEquals(listOf(vs[2]), SprawlRules.settlementSpots(b, 0, setup = false))
        assertTrue(SprawlRules.settlementSpots(b, 1, setup = false).isEmpty())
    }

    @Test fun roadsGrowFromYourNetworkButNotThroughSomeoneElsesTown() {
        val (vs, es) = chain(20, 2)
        var b = blank().settle(vs[0], 0).road(es[0], 0)
        val open = SprawlRules.roadSpots(b, 0)
        assertTrue(es[1] in open)
        g.vEdges[vs[0]].filter { it != es[0] }.forEach { assertTrue(it in open) }
        // An opponent settles at the end of the road: it can't pass through.
        b = b.settle(vs[1], 1)
        val blocked = SprawlRules.roadSpots(b, 0)
        g.vEdges[vs[1]].filter { it != es[0] }.forEach { assertFalse(it in blocked, "road through a rival town at $it") }
    }

    @Test fun setupRoadsTouchTheNewSettlement() {
        val b = blank().settle(20, 0)
        assertEquals(g.vEdges[20].sorted(), SprawlRules.roadSpots(b, 0, from = 20).sorted())
    }

    @Test fun hexesPayTheirCornersAndTheLandlordBlocks() {
        val h = 9
        val (a, c) = g.hexVertices[h][0] to g.hexVertices[h][3]
        val b = blank().copy(terrain = List(19) { if (it == h) 4 else 5 }, numbers = List(19) { if (it == h) 6 else 0 }).settle(a, 0).settle(c, 1, 2)
        val gains = SprawlRules.production(b, 6, List(5) { 19 }, 2)
        assertEquals(listOf(0, 0, 0, 0, 1), gains[0])
        assertEquals(listOf(0, 0, 0, 0, 2), gains[1])
        assertEquals(List(5) { 0 }, SprawlRules.production(b.copy(robber = h), 6, List(5) { 19 }, 2)[0])
    }

    @Test fun aShortBankPaysNobodyUnlessOnlyOneIsOwed() {
        val h = 9
        val b = blank().copy(terrain = List(19) { if (it == h) 4 else 5 }, numbers = List(19) { if (it == h) 6 else 0 })
        val two = b.settle(g.hexVertices[h][0], 0).settle(g.hexVertices[h][3], 1)
        assertEquals(List(2) { List(5) { 0 } }, SprawlRules.production(two, 6, listOf(9, 9, 9, 9, 1), 2))
        val one = b.settle(g.hexVertices[h][0], 0, 2)
        assertEquals(listOf(0, 0, 0, 0, 1), SprawlRules.production(one, 6, listOf(9, 9, 9, 9, 1), 2)[0])
    }

    @Test fun longestRoadFollowsTheLongestTrail() {
        val (vs, es) = chain(20, 6)
        var b = blank()
        es.forEach { b = b.road(it, 0) }
        assertEquals(6, SprawlRules.longestRoad(b, 0))
        // A spur off the middle doesn't add to the trail.
        val spur = g.vEdges[vs[3]].first { it !in es }
        assertEquals(6, SprawlRules.longestRoad(b.road(spur, 0), 0))
        // A rival town in the middle cuts it in two.
        assertEquals(3, SprawlRules.longestRoad(b.settle(vs[3], 1), 0))
        // Your own town doesn't.
        assertEquals(6, SprawlRules.longestRoad(b.settle(vs[3], 0), 0))
    }

    @Test fun aLoopCountsEveryRoadOnce() {
        var b = blank()
        val hex = g.hexVertices[9]
        hex.indices.forEach { k -> b = b.road(edge(hex[k], hex[(k + 1) % 6]), 0) }
        assertEquals(6, SprawlRules.longestRoad(b, 0))
    }

    @Test fun awardsNeedTheMinimumAndHoldersKeepTies() {
        assertEquals(-1, SprawlRules.holder(-1, listOf(4, 4, 0), 5))
        assertEquals(1, SprawlRules.holder(-1, listOf(4, 5, 0), 5))
        assertEquals(1, SprawlRules.holder(1, listOf(5, 5, 0), 5))
        assertEquals(0, SprawlRules.holder(1, listOf(6, 5, 0), 5))
        // The holder's road was cut and two others tie: set aside.
        assertEquals(-1, SprawlRules.holder(2, listOf(6, 6, 3), 5))
        assertEquals(-1, SprawlRules.holder(0, listOf(4, 3, 0), 5))
    }

    @Test fun harboursImproveTheBankRate() {
        val coast = g.coast[0]
        val v = g.edges[coast][0]
        var b = blank().copy(harbours = listOf(SHarbour(coast, 2), SHarbour(g.coast[10], -1)))
        assertEquals(listOf(4, 4, 4, 4, 4), SprawlRules.ratios(b, 0))
        b = b.settle(v, 0)
        assertEquals(listOf(4, 4, 2, 4, 4), SprawlRules.ratios(b, 0))
        assertEquals(listOf(4, 4, 4, 4, 4), SprawlRules.ratios(b, 1))
        b = b.settle(g.edges[g.coast[10]][0], 0)
        assertEquals(listOf(3, 3, 2, 3, 3), SprawlRules.ratios(b, 0))
    }

    @Test fun discardsHalveBigHandsFromTheBiggestPile() {
        assertEquals(0, SprawlRules.discardCount(listOf(2, 2, 2, 1, 0)))
        assertEquals(4, SprawlRules.discardCount(listOf(2, 2, 2, 2, 1)))
        assertEquals(listOf(0, 0, 4, 0, 0), SprawlRules.autoDiscard(listOf(1, 1, 5, 1, 1), 4))
        assertEquals(listOf(2, 2, 0, 0, 0), SprawlRules.autoDiscard(listOf(4, 4, 1, 0, 0), 4))
    }

    @Test fun costsAndAffordability() {
        assertTrue(SprawlRules.affords(listOf(1, 1, 0, 0, 0), SprawlRules.ROAD))
        assertFalse(SprawlRules.affords(listOf(1, 0, 1, 1, 0), SprawlRules.SETTLEMENT))
        assertTrue(SprawlRules.affords(listOf(0, 0, 0, 2, 3), SprawlRules.CITY))
        assertTrue(SprawlRules.affords(listOf(0, 0, 1, 1, 1), SprawlRules.DEV))
    }

    @Test fun theDealIsBalancedAndComplete() {
        val names = SprawlSetup.loadNames()
        repeat(200) { seed ->
            for (size in 0..1) {
                val b = SprawlSetup.deal(size, Random(seed), names)
                val geo = b.geo
                val counts = (0..5).map { t -> b.terrain.count { it == t } }
                assertEquals(if (size == 0) listOf(3, 4, 4, 4, 3, 1) else listOf(5, 6, 6, 6, 5, 2), counts)
                assertEquals(b.terrain.map { it == 5 }, b.numbers.map { it == 0 })
                b.numbers.indices.filter { b.numbers[it] == 6 || b.numbers[it] == 8 }.forEach { h ->
                    geo.hexNeighbours[h].forEach { n -> assertFalse(b.numbers[n] == 6 || b.numbers[n] == 8, "seed $seed: red numbers touch") }
                }
                assertEquals(5, b.terrain[b.robber])
                assertEquals(if (size == 0) 9 else 11, b.harbours.size)
                // No corner sits on two harbours.
                val corners = b.harbours.flatMap { geo.edges[it.edge] }
                assertEquals(corners.size, corners.toSet().size)
                assertEquals(b.names.size, b.names.toSet().size)
            }
        }
    }

    @Test fun theNamesFileIsValid() {
        assertEquals(emptyList(), SprawlSetup.problems(SprawlSetup.loadNames()))
    }

    @Test fun theOpeningPickPrefersRichCorners() {
        val b = blank().copy(numbers = List(19) { if (it == 9) 6 else 2 })
        assertTrue(SprawlRules.bestOpening(b, 0) in g.hexVertices[9])
    }

    @Test fun theLandlordGoesWhereItHurtsOthers() {
        val b = blank().copy(numbers = List(19) { when (it) { 3 -> 6; 12 -> 8; else -> 2 } })
            .settle(g.hexVertices[3][0], 0).settle(g.hexVertices[12][0], 1)
        assertEquals(12, SprawlRules.autoRobber(b, 0))
    }
}
