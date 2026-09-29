package partyos.engine.games.sprawl

/** A point on the board in board units (a hex is 174 wide and 200 tall, centred on the island). */
data class Pt(val x: Int, val y: Int)

/**
 * The island's shape: pointy-top hexes in horizontal rows, their corners (vertices, where buildings go) and sides
 * (edges, where roads go), with every adjacency the rules need. Ids run top to bottom, then left to right, so they
 * never change between games. After Red Blob Games' hex grid guide; a hex is 2 × 87 wide so corners land on ints.
 */
class SprawlGeometry private constructor(rows: List<Int>) {
    val hexes: List<Pt>
    val vertices: List<Pt>
    /** Each edge's two vertices. */
    val edges: List<List<Int>>
    /** Each hex's six corners, clockwise from the top. */
    val hexVertices: List<List<Int>>
    val vHexes: List<List<Int>>
    val vEdges: List<List<Int>>
    val vNeighbours: List<List<Int>>
    val edgeHexes: List<List<Int>>
    val hexNeighbours: List<List<Int>>
    /** Coast edges in order round the island. */
    val coast: List<Int>

    init {
        val mid = (rows.size - 1) / 2.0
        hexes = rows.flatMapIndexed { i, n -> (0 until n).map { j -> Pt((2 * j - (n - 1)) * HALF_W, ((i - mid) * ROW_H).toInt()) } }
        val corners = hexes.map { c -> CORNERS.map { (dx, dy) -> Pt(c.x + dx, c.y + dy) } }
        vertices = corners.flatten().distinct().sortedWith(compareBy({ it.y }, { it.x }))
        val vIndex = vertices.withIndex().associate { (i, p) -> p to i }
        hexVertices = corners.map { cs -> cs.map { vIndex.getValue(it) } }
        val sides = hexVertices.flatMap { vs -> vs.indices.map { k -> listOf(vs[k], vs[(k + 1) % 6]).sorted() } }.distinct()
        fun midpoint(e: List<Int>) = Pt(vertices[e[0]].x + vertices[e[1]].x, vertices[e[0]].y + vertices[e[1]].y)
        edges = sides.sortedWith(compareBy({ midpoint(it).y }, { midpoint(it).x }))
        val eIndex = edges.withIndex().associate { (i, e) -> e to i }
        vHexes = vertices.indices.map { v -> hexVertices.indices.filter { v in hexVertices[it] } }
        vEdges = vertices.indices.map { v -> edges.indices.filter { v in edges[it] } }
        vNeighbours = vertices.indices.map { v -> vEdges[v].map { e -> edges[e].first { it != v } } }
        val hexEdges = hexVertices.map { vs -> vs.indices.map { k -> eIndex.getValue(listOf(vs[k], vs[(k + 1) % 6]).sorted()) } }
        edgeHexes = edges.indices.map { e -> hexEdges.indices.filter { e in hexEdges[it] } }
        hexNeighbours = hexes.indices.map { h -> hexEdges[h].flatMap { e -> edgeHexes[e] }.filter { it != h }.distinct() }
        coast = walkCoast(edges.indices.filter { edgeHexes[it].size == 1 })
    }

    private fun walkCoast(open: List<Int>): List<Int> {
        val loop = mutableListOf(open.first())
        val left = open.toMutableSet().apply { remove(open.first()) }
        while (left.isNotEmpty()) {
            val tail = edges[loop.last()]
            val next = left.firstOrNull { e -> edges[e].any { it in tail } } ?: break
            loop += next
            left -= next
        }
        return loop
    }

    companion object {
        const val HALF_W = 87
        const val ROW_H = 150
        private val CORNERS = listOf(0 to -100, HALF_W to -50, HALF_W to 50, 0 to 100, -HALF_W to 50, -HALF_W to -50)
        private val ROWS = listOf(listOf(3, 4, 5, 4, 3), listOf(3, 4, 5, 6, 5, 4, 3))
        private val cache = ROWS.map { SprawlGeometry(it) }

        /** 0 = the base island (3–4 players), 1 = the big island (5–6). */
        fun of(size: Int): SprawlGeometry = cache[size]
    }
}
