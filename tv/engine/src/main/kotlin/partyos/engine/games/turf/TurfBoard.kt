package partyos.engine.games.turf

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What a board space does. */
enum class SpaceKind { PAYDAY, STREET, RAILROAD, UTILITY, CHANCE, CHEST, TAX, JAIL, COUCH, GO_TO_JAIL }

/**
 * One space. Prices, rents, house costs and mortgage values are the standard US board's numbers (facts, not
 * expression), keyed by position so renaming a space can never change the economy. [rent] is
 * `[site, 1 house, 2 houses, 3 houses, 4 houses, hotel]`; Home Turf's hotel comes after 3 houses, so index 4 is unused.
 */
data class Space(
    val index: Int,
    val kind: SpaceKind,
    /** Colour group 0..7 (cheapest to priciest) for streets; -1 otherwise. */
    val group: Int = -1,
    val price: Int = 0,
    val rent: List<Int> = emptyList(),
    val houseCost: Int = 0,
    /** Tax amount for [SpaceKind.TAX]. */
    val tax: Int = 0,
) {
    val mortgage get() = price / 2
    val buyable get() = kind == SpaceKind.STREET || kind == SpaceKind.RAILROAD || kind == SpaceKind.UTILITY
}

/** The names file: everything a party can rename. Numbers never live here. */
@Serializable
data class BoardNames(
    val chance: String,
    val chest: String,
    val spaces: List<SpaceName>,
    val chanceCards: List<String>,
    val chestCards: List<String>,
)

/** [label] is the TV board's two-line tag; [name] is the full name on deed cards and phones. */
@Serializable
data class SpaceName(val name: String, val label: String)

object TurfBoard {
    private fun street(i: Int, group: Int, price: Int, house: Int, vararg rent: Int) =
        Space(i, SpaceKind.STREET, group, price, rent.toList(), house)
    private fun rail(i: Int) = Space(i, SpaceKind.RAILROAD, price = 200)
    private fun util(i: Int) = Space(i, SpaceKind.UTILITY, price = 150)

    val spaces: List<Space> = listOf(
        Space(0, SpaceKind.PAYDAY),
        street(1, 0, 60, 50, 2, 10, 30, 90, 160, 250),
        Space(2, SpaceKind.CHEST),
        street(3, 0, 60, 50, 4, 20, 60, 180, 320, 450),
        Space(4, SpaceKind.TAX, tax = 200),
        rail(5),
        street(6, 1, 100, 50, 6, 30, 90, 270, 400, 550),
        Space(7, SpaceKind.CHANCE),
        street(8, 1, 100, 50, 6, 30, 90, 270, 400, 550),
        street(9, 1, 120, 50, 8, 40, 100, 300, 450, 600),
        Space(10, SpaceKind.JAIL),
        street(11, 2, 140, 100, 10, 50, 150, 450, 625, 750),
        util(12),
        street(13, 2, 140, 100, 10, 50, 150, 450, 625, 750),
        street(14, 2, 160, 100, 12, 60, 180, 500, 700, 900),
        rail(15),
        street(16, 3, 180, 100, 14, 70, 200, 550, 750, 950),
        Space(17, SpaceKind.CHEST),
        street(18, 3, 180, 100, 14, 70, 200, 550, 750, 950),
        street(19, 3, 200, 100, 16, 80, 220, 600, 800, 1000),
        Space(20, SpaceKind.COUCH),
        street(21, 4, 220, 150, 18, 90, 250, 700, 875, 1050),
        Space(22, SpaceKind.CHANCE),
        street(23, 4, 220, 150, 18, 90, 250, 700, 875, 1050),
        street(24, 4, 240, 150, 20, 100, 300, 750, 925, 1100),
        rail(25),
        street(26, 5, 260, 150, 22, 110, 330, 800, 975, 1150),
        street(27, 5, 260, 150, 22, 110, 330, 800, 975, 1150),
        util(28),
        street(29, 5, 280, 150, 24, 120, 360, 850, 1025, 1200),
        Space(30, SpaceKind.GO_TO_JAIL),
        street(31, 6, 300, 200, 26, 130, 390, 900, 1100, 1275),
        street(32, 6, 300, 200, 26, 130, 390, 900, 1100, 1275),
        Space(33, SpaceKind.CHEST),
        street(34, 6, 320, 200, 28, 150, 450, 1000, 1200, 1400),
        rail(35),
        Space(36, SpaceKind.CHANCE),
        street(37, 7, 350, 200, 35, 175, 500, 1100, 1300, 1500),
        Space(38, SpaceKind.TAX, tax = 100),
        street(39, 7, 400, 200, 50, 200, 600, 1400, 1700, 2000),
    )

    const val SIZE = 40
    const val JAIL = 10
    const val GO_TO_JAIL = 30
    const val PAYDAY_PAY = 200
    val railroads = spaces.filter { it.kind == SpaceKind.RAILROAD }.map { it.index }
    val utilities = spaces.filter { it.kind == SpaceKind.UTILITY }.map { it.index }
    /** Street indexes per colour group. */
    val groups: List<List<Int>> = (0..7).map { g -> spaces.filter { it.group == g }.map { it.index } }
    /** TV colours per group: brown, light blue, pink, orange, red, yellow, green, dark blue. */
    val groupColors = listOf("#8B5A2B", "#9ED8F5", "#E0408C", "#FF8A2B", "#E8322B", "#FFD23F", "#1FA64A", "#1F4FB8")

    /** Every buyable space: the things players own, trade and mortgage. */
    val deeds = spaces.filter { it.buyable }.map { it.index }

    fun groupOf(i: Int): List<Int> = when (spaces[i].kind) {
        SpaceKind.STREET -> groups[spaces[i].group]
        SpaceKind.RAILROAD -> railroads
        SpaceKind.UTILITY -> utilities
        else -> emptyList()
    }

    fun colorOf(i: Int): String = when (spaces[i].kind) {
        SpaceKind.STREET -> groupColors[spaces[i].group]
        SpaceKind.RAILROAD -> "#2B2B2B"
        SpaceKind.UTILITY -> "#8B4DFF"
        else -> "#FFFFFF"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Loads the names file from the classpath (`turf/board.json`) and validates it. */
    fun loadNames(): BoardNames {
        val text = TurfBoard::class.java.classLoader.getResourceAsStream(NAMES_RESOURCE)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("$NAMES_RESOURCE is missing")
        return json.decodeFromString(BoardNames.serializer(), text).also { validate(it) }
    }

    /** Problems with a names file, or empty if it's fine. */
    fun problems(n: BoardNames): List<String> = buildList {
        if (n.spaces.size != SIZE) add("need $SIZE spaces, found ${n.spaces.size}")
        if (n.chance.isBlank() || n.chance.length > MAX_NAME) add("chance deck name must be 1-$MAX_NAME characters")
        if (n.chest.isBlank() || n.chest.length > MAX_NAME) add("chest deck name must be 1-$MAX_NAME characters")
        n.spaces.forEachIndexed { i, s ->
            if (s.name.isBlank() || s.name.length > MAX_NAME) add("space $i: name must be 1-$MAX_NAME characters (\"${s.name}\")")
            if (s.label.isBlank() || s.label.length > MAX_LABEL) add("space $i: label must be 1-$MAX_LABEL characters (\"${s.label}\")")
            s.label.split(' ').filter { it.length > MAX_LABEL_WORD }.forEach { add("space $i: label word \"$it\" is over $MAX_LABEL_WORD letters") }
        }
        if (n.chanceCards.size != TurfDecks.chance.size) add("need ${TurfDecks.chance.size} chance cards, found ${n.chanceCards.size}")
        if (n.chestCards.size != TurfDecks.chest.size) add("need ${TurfDecks.chest.size} chest cards, found ${n.chestCards.size}")
        (n.chanceCards + n.chestCards).forEach { text ->
            if (text.isBlank() || text.length > MAX_CARD) add("card text must be 1-$MAX_CARD characters (\"$text\")")
            PLACEHOLDER.findAll(text).forEach { m ->
                val idx = m.groupValues[1].toInt()
                if (idx !in 0 until SIZE) add("card \"$text\" names space $idx, which doesn't exist")
            }
        }
    }

    fun validate(n: BoardNames) {
        val p = problems(n)
        require(p.isEmpty()) { "$NAMES_RESOURCE: " + p.joinToString("; ") }
    }

    /** Fills `{space:N}` placeholders with space names. */
    fun fill(text: String, n: BoardNames): String =
        PLACEHOLDER.replace(text) { m -> n.spaces.getOrNull(m.groupValues[1].toInt())?.name ?: m.value }

    const val NAMES_RESOURCE = "turf/board.json"
    const val MAX_NAME = 24
    const val MAX_LABEL = 22
    const val MAX_LABEL_WORD = 11
    const val MAX_CARD = 110
    private val PLACEHOLDER = Regex("""\{space:(\d+)}""")
}
