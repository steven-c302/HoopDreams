package partyos.engine.games.turf

/** What a card does. The wording lives in `turf/board.json`, index-aligned with [TurfDecks.chance] / [TurfDecks.chest]. */
enum class CardKind {
    /** Move forward to [Card.space], collecting Payday on the way. */
    ADVANCE,
    /** Move forward to the nearest railroad; pay its owner double rent (or buy it). */
    NEAREST_RAIL,
    /** Move forward to the nearest utility; pay 10x a fresh roll (or buy it). */
    NEAREST_UTIL,
    /** Move back [Card.amount] spaces (no Payday). */
    BACK,
    JAIL,
    /** Keep it: a free way out of Timeout. */
    JAIL_FREE,
    COLLECT,
    PAY,
    /** Pay [Card.amount] to every other token still in. */
    PAY_EACH,
    /** Collect [Card.amount] from every other token still in. */
    COLLECT_EACH,
    /** Pay [Card.perHouse] per house and [Card.perHotel] per hotel you own. */
    REPAIRS,
}

/**
 * One card. [sips] is what the drawer drinks and [othersSips] what everyone else drinks, only when drink calls are on.
 * The effects are the standard 2008+ US decks; the text is our own.
 */
data class Card(
    val kind: CardKind,
    val space: Int = -1,
    val amount: Int = 0,
    val perHouse: Int = 0,
    val perHotel: Int = 0,
    val sips: Int = 0,
    val othersSips: Int = 0,
)

object TurfDecks {
    const val CHANCE = "chance"
    const val CHEST = "chest"

    /** Plot Twist (the Chance deck). */
    val chance: List<Card> = listOf(
        Card(CardKind.ADVANCE, space = 39),
        Card(CardKind.ADVANCE, space = 0),
        Card(CardKind.ADVANCE, space = 24),
        Card(CardKind.ADVANCE, space = 11),
        Card(CardKind.NEAREST_RAIL),
        Card(CardKind.NEAREST_RAIL),
        Card(CardKind.NEAREST_UTIL),
        Card(CardKind.COLLECT, amount = 50),
        Card(CardKind.JAIL_FREE),
        Card(CardKind.BACK, amount = 3),
        Card(CardKind.JAIL),
        Card(CardKind.REPAIRS, perHouse = 25, perHotel = 100),
        Card(CardKind.PAY, amount = 15, sips = 1),
        Card(CardKind.ADVANCE, space = 5),
        Card(CardKind.PAY_EACH, amount = 50),
        Card(CardKind.COLLECT, amount = 150),
    )

    /** Group Chat (the Community Chest deck). */
    val chest: List<Card> = listOf(
        Card(CardKind.ADVANCE, space = 0),
        Card(CardKind.COLLECT, amount = 200),
        Card(CardKind.PAY, amount = 50, sips = 1),
        Card(CardKind.COLLECT, amount = 50),
        Card(CardKind.JAIL_FREE),
        Card(CardKind.JAIL),
        Card(CardKind.COLLECT, amount = 100),
        Card(CardKind.COLLECT, amount = 20),
        Card(CardKind.COLLECT_EACH, amount = 10, othersSips = 1),
        Card(CardKind.COLLECT, amount = 100),
        Card(CardKind.PAY, amount = 100),
        Card(CardKind.PAY, amount = 50),
        Card(CardKind.COLLECT, amount = 25),
        Card(CardKind.REPAIRS, perHouse = 40, perHotel = 115),
        Card(CardKind.COLLECT, amount = 10),
        Card(CardKind.COLLECT, amount = 100),
    )

    fun deck(id: String) = if (id == CHANCE) chance else chest
}
