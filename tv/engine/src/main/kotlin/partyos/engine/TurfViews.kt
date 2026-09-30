package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import partyos.engine.games.turf.TBeat

/**
 * Home Turf on the TV. The static board ([board]) rides along so the TV needs nothing else; per-space state is
 * three arrays by space index. Faces come from [TvState.players] by id, so no avatars here.
 */
@Serializable @SerialName("turf")
data class TurfTv(
    /** teamup | pieces | deal | roll | jail | move | buy | auction | card | choose | manage | debt | trade | tally | podium */
    val phase: String,
    val teams: Boolean,
    val board: List<TurfSpaceTv>,
    val chanceName: String,
    val chestName: String,
    /** Owner token index per space (-1 = nobody). */
    val owner: List<Int>,
    /** 0..3 houses, 4 = hotel, per space. */
    val level: List<Int>,
    /** Mortgaged space indexes. */
    val mortgaged: List<Int>,
    val tokens: List<TurfTokenTv>,
    val turn: Int,
    val dice: List<Int> = emptyList(),
    val doubles: Int = 0,
    val housesLeft: Int,
    val hotelsLeft: Int,
    /** The space on offer (buy phase), or -1. */
    val buy: Int = -1,
    /** bus | triples while choosing. */
    val choose: String? = null,
    val card: TurfCardTv? = null,
    val auction: TurfAuctionTv? = null,
    val trade: TurfTradeTv? = null,
    val debt: TurfDebtTv? = null,
    /** Game time left as of this phase's start; the TV counts it down with the stage clock. Null = no limit. */
    val clockLeftMs: Long? = null,
    val phaseMs: Long? = null,
    val lastLap: Boolean = false,
    /** The stage deadline is a decision timer (tick and hurry), not an animation pause. */
    val timed: Boolean = false,
    val drinks: Boolean = true,
    val quick: Boolean = false,
    val beats: List<TBeat> = emptyList(),
    val ticker: List<String> = emptyList(),
    /** Pieces still free (pieces phase). */
    val pieces: List<String> = emptyList(),
    val tally: List<TurfTallyTv> = emptyList(),
    val notice: String? = null,
) : TvGame

/** [kind]: payday | street | railroad | utility | chance | chest | tax | jail | couch | gotojail. */
@Serializable
data class TurfSpaceTv(
    val name: String,
    val label: String,
    val kind: String,
    val group: Int = -1,
    val color: String? = null,
    val price: Int = 0,
    val rent: List<Int> = emptyList(),
    val houseCost: Int = 0,
    val tax: Int = 0,
)

@Serializable
data class TurfTokenTv(
    val name: String,
    val color: String,
    val piece: String?,
    val members: List<PlayerId>,
    val seat: PlayerId?,
    val cash: Int,
    val pos: Int,
    val jailed: Boolean,
    val jailCards: Int,
    val bankrupt: Boolean,
    val worth: Int,
    /** Full colour sets owned. */
    val sets: Int,
)

@Serializable
data class TurfCardTv(val deck: String, val deckName: String, val text: String, val sips: Int = 0)

@Serializable
data class TurfAuctionTv(val id: Int, val space: Int, val top: Int, val leader: Int, val bids: Int)

@Serializable
data class TurfTradeTv(
    val id: Int,
    val from: Int,
    val to: Int,
    val give: List<Int>,
    val get: List<Int>,
    val giveCash: Int,
    val getCash: Int,
    val giveCards: Int,
    val getCards: Int,
    val counters: Int,
)

@Serializable
data class TurfDebtTv(val token: Int, val amount: Int, val to: Int, val why: String)

@Serializable
data class TurfTallyTv(val token: Int, val worth: Int, val cash: Int, val places: Int, val buildings: Int, val rank: Int)

// ---- the phone --------------------------------------------------------------------------

/** Your token (or team) as your phone shows it. [mine] = you hold the dice right now. */
@Serializable
data class TurfMe(
    val index: Int,
    val name: String,
    val color: String,
    val piece: String?,
    val cash: Int,
    val seat: PlayerId?,
    val seatName: String?,
    val mine: Boolean,
    val jailed: Boolean,
    val jailCards: Int,
    val bankrupt: Boolean,
    val pos: Int,
    val spaceName: String,
    val worth: Int,
)

/**
 * The one thing to do now. [kind]: wait | teamup | pieces | deal | roll | jail | buy | bid | bus | triples | manage |
 * debt | trade | tally | over | watch. [actions] are sent as `{kind, option}`; [timed] lights the phone's countdown.
 */
@Serializable
data class TurfPrompt(
    val kind: String,
    val title: String,
    val detail: String? = null,
    val actions: List<Choice> = emptyList(),
    val timed: Boolean = false,
    /** win | lose | neutral */
    val tone: String? = null,
    val space: Int = -1,
    /** Debt: what's owed; buy: the price. */
    val amount: Int = 0,
)

/** One of your places, with what you can do to it right now (null = can't, else the cost or refund). */
@Serializable
data class TurfDeed(
    val space: Int,
    val name: String,
    val color: String,
    /** 0..7 streets, 8 railroads, 9 utilities: for grouping. */
    val group: Int,
    val level: Int,
    val mortgaged: Boolean,
    val rent: Int,
    val build: Int? = null,
    val sell: Int? = null,
    val mortgage: Int? = null,
    val unmortgage: Int? = null,
    /** Can go in a trade (no buildings in its colour group). */
    val tradable: Boolean,
    /** You own the whole colour group. */
    val set: Boolean = false,
)

@Serializable
data class TurfDeedRef(val space: Int, val name: String, val color: String, val group: Int, val mortgaged: Boolean, val tradable: Boolean)

/** Another token you could trade with. */
@Serializable
data class TurfPartner(val index: Int, val name: String, val color: String, val cash: Int, val jailCards: Int, val deeds: List<TurfDeedRef>)

/** The open trade as your phone sees it. [role]: from (you offered) | to (you decide) | watch. */
@Serializable
data class TurfTradeView(
    val id: Int,
    val from: Int,
    val to: Int,
    val fromName: String,
    val toName: String,
    val give: List<TurfDeedRef>,
    val get: List<TurfDeedRef>,
    val giveCash: Int,
    val getCash: Int,
    val giveCards: Int,
    val getCards: Int,
    val role: String,
    val canCounter: Boolean,
)

/** The auction bid pad. [maxBid] is your cash; [leading] = your token holds the top bid. */
@Serializable
data class TurfBidPad(
    val auction: Int,
    val space: Int,
    val name: String,
    val color: String,
    val price: Int,
    val top: Int,
    val leaderName: String?,
    val leading: Boolean,
    val maxBid: Int,
    val canBid: Boolean,
)
