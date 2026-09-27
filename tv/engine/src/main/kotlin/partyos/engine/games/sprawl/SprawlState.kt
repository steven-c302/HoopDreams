package partyos.engine.games.sprawl

import kotlinx.serialization.Serializable
import partyos.engine.PlayerId

/**
 * The island and everything built on it. Lists are indexed by hex, vertex or edge id ([SprawlGeometry]).
 * [terrain]: 0 hills (brick), 1 forest (wood), 2 pasture (sheep), 3 fields (wheat), 4 mountains (ore), 5 desert.
 * [numbers]: 0 on deserts. [vOwner]/[eOwner]: seat index or -1. [vLevel]: 0 empty, 1 settlement, 2 city.
 */
@Serializable
data class SBoard(
    val size: Int,
    val terrain: List<Int>,
    val numbers: List<Int>,
    val names: List<String>,
    val harbours: List<SHarbour>,
    val robber: Int,
    val vOwner: List<Int>,
    val vLevel: List<Int>,
    val eOwner: List<Int>,
) {
    val geo: SprawlGeometry get() = SprawlGeometry.of(size)
}

/** A harbour on a coast [edge]: [kind] -1 trades any 3:1, 0..4 trades that resource 2:1. */
@Serializable
data class SHarbour(val edge: Int, val kind: Int)

/**
 * One player. [hand] holds 5 resource counts. [dev] holds unplayed development cards (knight | road | plenty | mono
 * | vp); [fresh] the ones bought this turn (they can't be played until next turn).
 */
@Serializable
data class SSeat(
    val player: PlayerId,
    val name: String,
    val color: String,
    val hand: List<Int> = List(5) { 0 },
    val dev: List<String> = emptyList(),
    val fresh: List<String> = emptyList(),
    val knights: Int = 0,
    val gone: Boolean = false,
)

/**
 * An open trade: [from] gives [give] (5 counts) for [get]. [to] is a seat, or [Sprawl.ANYONE] for the first taker.
 * While it's open the turn clock is frozen: [frozenPhase] resumes with [frozenMs] once it closes. [passed] lists seats
 * that turned down an offer to anyone.
 */
@Serializable
data class STrade(
    val id: Int,
    val from: Int,
    val to: Int,
    val give: List<Int>,
    val get: List<Int>,
    val counters: Int = 0,
    val frozenPhase: String = "",
    val frozenMs: Long? = null,
    val passed: List<Int> = emptyList(),
)

/**
 * Something that just happened, for the TV to animate and phones to react to. [seq] only goes up. [gains] holds 5
 * resource counts per seat (production).
 */
@Serializable
data class SBeat(
    val seq: Int,
    val kind: String,
    val seat: Int = -1,
    val other: Int = -1,
    val target: Int = -1,
    val amount: Int = 0,
    val dice: List<Int> = emptyList(),
    val seats: List<Int> = emptyList(),
    val targets: List<Int> = emptyList(),
    val gains: List<List<Int>> = emptyList(),
    val sips: Int = 0,
    val text: String? = null,
)

@Serializable
data class SprawlState(
    val phase: String,
    val board: SBoard,
    val seats: List<SSeat>,
    /** Whose turn it is (seat index). Seats are already in turn order. */
    val turn: Int = 0,
    /** Setup picks made so far: each seat places a settlement then a road, first in order then in reverse. */
    val setupStep: Int = 0,
    /** The settlement just placed in setup (its road must touch it), or -1. */
    val setupVertex: Int = -1,
    val dice: List<Int> = emptyList(),
    /** The dice have been rolled this turn. */
    val rolled: Boolean = false,
    val devPlayed: Boolean = false,
    /** Where a side phase (robber from a Bouncer, Road Trip, a pick) goes back to: roll or main. */
    val resume: String = "",
    /** The main phase's time left when a side phase interrupted it (null = start it fresh). */
    val resumeMs: Long? = null,
    /** Road Trip roads still to place. */
    val freeRoads: Int = 0,
    /** plenty | mono while picking; [picked] holds Windfall's first pick. */
    val pick: String? = null,
    val picked: Int = -1,
    val bank: List<Int> = List(5) { 0 },
    /** Dev card draw order. */
    val deck: List<String> = emptyList(),
    val roadHolder: Int = -1,
    val armyHolder: Int = -1,
    /** Cards each seat still owes on a 7. */
    val discards: List<Int> = emptyList(),
    val trade: STrade? = null,
    /** Offers made this turn. */
    val offers: Int = 0,
    /** The spot a phone is eyeing right now (vertex, edge or hex, by [peekKind]), for the TV. */
    val peek: Int = -1,
    val peekKind: String? = null,
    val clockLeftMs: Long? = null,
    val phaseMs: Long? = null,
    val lastRound: Boolean = false,
    val vpTarget: Int = Sprawl.DEFAULT_VP,
    val drinks: Boolean = true,
    val rngSeed: Long = 0,
    val draws: Int = 0,
    val beats: List<SBeat> = emptyList(),
    val beatSeq: Int = 0,
    val ticker: List<String> = emptyList(),
    val serial: Int = 0,
    /** [beatSeq] when the current turn began: drink calls since then show on phones. */
    val turnBeat: Int = 0,
    val winner: Int = -1,
    /** Final VP per seat, set at the tally. */
    val tally: List<Int> = emptyList(),
)
