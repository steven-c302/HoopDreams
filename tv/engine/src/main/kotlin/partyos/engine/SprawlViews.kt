package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import partyos.engine.games.sprawl.SBeat

/**
 * Sprawl's island as the TV and phones draw it, the same all game: hex centres, corners and sides in board units
 * (a hex is 174 wide, 200 tall), terrain 0..5 (hills, forest, pasture, fields, mountains, desert), number tokens,
 * the crew's place names and the harbours (kind -1 = any 3:1, 0..4 = that resource 2:1).
 */
@Serializable
data class SprawlMapTv(
    val size: Int,
    val hexes: List<SprawlHexTv>,
    /** [x, y] per vertex. */
    val vertices: List<List<Int>>,
    /** The two vertex ids of each edge. */
    val edges: List<List<Int>>,
    val harbours: List<SprawlHarbourTv>,
    val resources: List<String>,
    val landlord: String,
    /** Card kind (knight | road | plenty | mono | vp) to its name. */
    val dev: Map<String, String>,
    /** road | army to its name. */
    val awards: Map<String, String>,
)

@Serializable
data class SprawlHexTv(val x: Int, val y: Int, val terrain: Int, val number: Int, val name: String)

@Serializable
data class SprawlHarbourTv(val edge: Int, val kind: Int)

/** Sprawl on the TV. Faces come from [TvState.players] by [SprawlSeatTv.player]. */
@Serializable @SerialName("sprawl")
data class SprawlTv(
    /** setup | roll | discard | robber | steal | main | road2 | pick | trade | tally | podium */
    val phase: String,
    val map: SprawlMapTv,
    val robber: Int,
    val vOwner: List<Int>,
    val vLevel: List<Int>,
    val eOwner: List<Int>,
    val seats: List<SprawlSeatTv>,
    val turn: Int,
    /** setup: settlement | road, the pick being made. */
    val setupPiece: String? = null,
    /** 1 or 2 during setup. */
    val setupRound: Int = 0,
    val dice: List<Int> = emptyList(),
    val trade: SprawlTradeTv? = null,
    /** What the active phone is eyeing (vertex, edge or hex by [peekKind]), or -1. */
    val peek: Int = -1,
    val peekKind: String? = null,
    /** plenty | mono while a card's resource is being picked. */
    val pick: String? = null,
    val bank: List<Int>,
    val deckLeft: Int,
    val clockLeftMs: Long? = null,
    val phaseMs: Long? = null,
    val lastRound: Boolean = false,
    /** The stage deadline is a decision timer (tick and hurry). */
    val timed: Boolean = false,
    val vpTarget: Int,
    val drinks: Boolean = true,
    val beats: List<SBeat> = emptyList(),
    val ticker: List<String> = emptyList(),
    val winner: Int = -1,
    val tally: List<SprawlTallyTv> = emptyList(),
) : TvGame

/** One player's public side. [vp] leaves out hidden VP cards until the tally. [discard]: cards still owed on a 7. */
@Serializable
data class SprawlSeatTv(
    val name: String,
    val color: String,
    val player: PlayerId,
    val cards: Int,
    val dev: Int,
    val vp: Int,
    val knights: Int,
    val road: Int,
    val longest: Boolean,
    val army: Boolean,
    val gone: Boolean,
    val discard: Int = 0,
)

@Serializable
data class SprawlTradeTv(val id: Int, val from: Int, val to: Int, val give: List<Int>, val get: List<Int>, val counters: Int, val passed: List<Int> = emptyList())

@Serializable
data class SprawlTallyTv(val seat: Int, val vp: Int, val vpCards: Int, val rank: Int)

// ---- the phone --------------------------------------------------------------------------

/** You at the table. [mine]: it's your turn. [pieces]: roads, settlements, cities left. [ratios]: your bank rates. */
@Serializable
data class SprawlMe(
    val index: Int,
    val name: String,
    val color: String,
    val hand: List<Int>,
    val vp: Int,
    val dev: List<SprawlCard>,
    val pieces: List<Int>,
    val ratios: List<Int>,
    val mine: Boolean,
)

/** A development card in your hand; [playable] = you can play one right now. [fresh]: bought this turn. */
@Serializable
data class SprawlCard(val kind: String, val name: String, val count: Int, val playable: Boolean, val fresh: Int = 0)

/**
 * The one thing to do now. [kind]: watch | wait | setup | roll | main | discard | robber | steal | road2 | pick |
 * trade | over | out. [timed] lights the phone's countdown.
 */
@Serializable
data class SprawlPrompt(
    val kind: String,
    val title: String,
    val detail: String? = null,
    val actions: List<Choice> = emptyList(),
    val timed: Boolean = false,
    /** win | lose | neutral */
    val tone: String? = null,
)

/** Where you could build right now (affordable, pieces left); empty lists when you can't. */
@Serializable
data class SprawlBuild(val roads: List<Int> = emptyList(), val settlements: List<Int> = emptyList(), val cities: List<Int> = emptyList(), val dev: Boolean = false)

@Serializable
data class SprawlPartner(val index: Int, val name: String, val color: String, val cards: Int)

/** The open trade as your phone sees it. [role]: from (you offered) | to (you decide) | watch. */
@Serializable
data class SprawlTradeView(
    val id: Int,
    val from: Int,
    val to: Int,
    val fromName: String,
    val toName: String,
    val give: List<Int>,
    val get: List<Int>,
    val role: String,
    val canAccept: Boolean,
    val canCounter: Boolean,
)
