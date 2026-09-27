package partyos.engine.games.sprawl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SprawlGeometryTest {
    @Test fun theBaseIslandHasTheOfficialCounts() {
        val g = SprawlGeometry.of(0)
        assertEquals(19, g.hexes.size)
        assertEquals(54, g.vertices.size)
        assertEquals(72, g.edges.size)
        assertEquals(30, g.coast.size)
    }

    @Test fun theBigIslandFitsSixPlayers() {
        val g = SprawlGeometry.of(1)
        assertEquals(30, g.hexes.size)
        assertEquals(80, g.vertices.size)
        assertEquals(109, g.edges.size)
    }

    @Test fun everyCornerAndEdgeIsWiredBothWays() {
        for (size in 0..1) {
            val g = SprawlGeometry.of(size)
            g.vertices.indices.forEach { v ->
                assertTrue(g.vEdges[v].size in 2..3, "vertex $v has ${g.vEdges[v].size} edges")
                assertTrue(g.vHexes[v].size in 1..3, "vertex $v touches ${g.vHexes[v].size} hexes")
                assertEquals(g.vEdges[v].size, g.vNeighbours[v].size)
                g.vEdges[v].forEach { e -> assertTrue(v in g.edges[e]) }
                g.vHexes[v].forEach { h -> assertTrue(v in g.hexVertices[h]) }
            }
            g.hexVertices.forEach { assertEquals(6, it.toSet().size) }
            // Ids run top to bottom, left to right, so they're stable.
            val ys = g.vertices.map { it.y }
            assertEquals(ys.sorted(), ys)
        }
    }

    @Test fun theCoastIsOneClosedLoop() {
        for (size in 0..1) {
            val g = SprawlGeometry.of(size)
            g.coast.forEach { e -> assertEquals(1, g.edgeHexes[e].size) }
            g.coast.indices.forEach { i ->
                val a = g.edges[g.coast[i]]
                val b = g.edges[g.coast[(i + 1) % g.coast.size]]
                assertTrue(a.intersect(b.toSet()).size == 1, "coast edges $i and ${i + 1} don't touch")
            }
        }
    }

    @Test fun hexNeighboursShareAnEdge() {
        val g = SprawlGeometry.of(0)
        // The middle hex of the middle row touches six others; a corner hex touches three.
        val mid = g.hexes.indexOfFirst { it.x == 0 && it.y == 0 }
        assertEquals(6, g.hexNeighbours[mid].size)
        assertEquals(3, g.hexNeighbours[0].size)
    }
}
