package partyos.engine.games.sprawl

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Sprawl's names, from `sprawl/board.json`, so the crew can rename the island without touching the rules.
 * [places] are dealt onto the hexes at random each game; [deserts] name the desert hexes (the big island has two).
 */
@Serializable
data class SprawlNames(
    val places: List<String>,
    val deserts: List<String>,
    val landlord: String,
    /** brick, wood, sheep, wheat, ore */
    val resources: List<String>,
    /** knight, road, plenty, mono, vp */
    val dev: Map<String, String>,
    /** road, army */
    val awards: Map<String, String>,
) {
    fun res(i: Int) = resources[i]
    fun devName(kind: String) = dev[kind] ?: kind
}

object SprawlSetup {
    const val NAMES_RESOURCE = "sprawl/board.json"
    const val MAX_NAME = 22
    val DEV_KINDS = listOf(Sprawl.KNIGHT, Sprawl.ROAD_TRIP, Sprawl.PLENTY, Sprawl.MONO, Sprawl.VP)

    private val json = Json { ignoreUnknownKeys = true }

    fun loadNames(): SprawlNames {
        val text = SprawlSetup::class.java.classLoader.getResourceAsStream(NAMES_RESOURCE)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("$NAMES_RESOURCE is missing")
        return json.decodeFromString(SprawlNames.serializer(), text).also { n ->
            val p = problems(n)
            require(p.isEmpty()) { "$NAMES_RESOURCE: " + p.joinToString("; ") }
        }
    }

    /** Problems with a names file, or empty if it's fine. */
    fun problems(n: SprawlNames): List<String> = buildList {
        val needPlaces = SprawlGeometry.of(1).hexes.size - DESERTS[1]
        if (n.places.size < needPlaces) add("need at least $needPlaces places, found ${n.places.size}")
        if (n.deserts.size < DESERTS[1]) add("need ${DESERTS[1]} desert names")
        val all = n.places + n.deserts
        all.filter { it.isBlank() || it.length > MAX_NAME }.forEach { add("\"$it\" must be 1-$MAX_NAME characters") }
        all.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { add("\"$it\" is used twice") }
        if (n.resources.size != 5 || n.resources.any { it.isBlank() || it.length > 10 }) add("need 5 resource names of 1-10 characters")
        DEV_KINDS.filter { it !in n.dev }.forEach { add("missing a name for the $it card") }
        listOf("road", "army").filter { it !in n.awards }.forEach { add("missing a name for the $it award") }
        if (n.landlord.isBlank()) add("the landlord needs a name")
    }

    /** Hexes per terrain (hills, forest, pasture, fields, mountains, desert) for each island size. */
    private val TERRAIN = listOf(listOf(3, 4, 4, 4, 3, 1), listOf(5, 6, 6, 6, 5, 2))
    private val DESERTS = TERRAIN.map { it[SprawlRules.DESERT] }
    private val NUMBERS = listOf(
        listOf(2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12),
        listOf(2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 5, 6, 6, 6, 8, 8, 8, 9, 9, 9, 10, 10, 10, 11, 11, 11, 12, 12),
    )
    /** -1 is a generic 3:1 harbour, 0..4 a 2:1 for that resource. */
    private val HARBOURS = listOf(listOf(-1, -1, -1, -1, 0, 1, 2, 3, 4), listOf(-1, -1, -1, -1, -1, 0, 1, 2, 2, 3, 4))
    private val BANK = listOf(19, 24)
    private val DECK = listOf(
        mapOf(Sprawl.KNIGHT to 14, Sprawl.VP to 5, Sprawl.ROAD_TRIP to 2, Sprawl.PLENTY to 2, Sprawl.MONO to 2),
        mapOf(Sprawl.KNIGHT to 20, Sprawl.VP to 5, Sprawl.ROAD_TRIP to 3, Sprawl.PLENTY to 3, Sprawl.MONO to 3),
    )

    fun bank(size: Int) = List(5) { BANK[size] }
    fun deck(size: Int, r: Random): List<String> = DECK[size].flatMap { (k, n) -> List(n) { k } }.shuffled(r)

    /**
     * A random, balanced island: shuffled terrain, number tokens redealt until no 6 or 8 sit side by side (the
     * official variable setup), harbours evenly spaced round the coast, and the crew's places dealt onto the hexes.
     */
    fun deal(size: Int, r: Random, names: SprawlNames): SBoard {
        val g = SprawlGeometry.of(size)
        val terrain = TERRAIN[size].flatMapIndexed { t, n -> List(n) { t } }.shuffled(r)
        val land = terrain.indices.filter { terrain[it] != SprawlRules.DESERT }
        var numbers: List<Int>
        do {
            val tokens = NUMBERS[size].shuffled(r).iterator()
            numbers = terrain.map { if (it == SprawlRules.DESERT) 0 else tokens.next() }
        } while (land.any { h -> red(numbers[h]) && g.hexNeighbours[h].any { red(numbers[it]) } })
        val kinds = HARBOURS[size].shuffled(r)
        val offset = r.nextInt(g.coast.size)
        val harbours = kinds.mapIndexed { k, kind -> SHarbour(g.coast[(offset + k * g.coast.size / kinds.size) % g.coast.size], kind) }
        val places = names.places.shuffled(r).iterator()
        var desert = 0
        val hexNames = terrain.map { if (it == SprawlRules.DESERT) names.deserts[desert++] else places.next() }
        return SBoard(
            size = size, terrain = terrain, numbers = numbers, names = hexNames, harbours = harbours,
            robber = terrain.indexOf(SprawlRules.DESERT),
            vOwner = List(g.vertices.size) { SprawlRules.NOBODY }, vLevel = List(g.vertices.size) { 0 }, eOwner = List(g.edges.size) { SprawlRules.NOBODY },
        )
    }

    private fun red(n: Int) = n == 6 || n == 8
}
