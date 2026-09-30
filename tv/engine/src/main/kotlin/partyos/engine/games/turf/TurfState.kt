package partyos.engine.games.turf

import kotlinx.serialization.Serializable
import partyos.engine.PlayerId

/**
 * One piece on the board: a player (solo) or a team sharing it. [seat] is the member holding the dice right now; in
 * teams it rotates after each of the team's turns. [jailCards] holds the deck id of each Get Out card kept.
 */
@Serializable
data class TToken(
    val id: String,
    val name: String,
    val color: String,
    val members: List<PlayerId>,
    val seat: PlayerId? = null,
    val piece: String? = null,
    val cash: Int = 0,
    val pos: Int = 0,
    val jailed: Boolean = false,
    val jailCards: List<String> = emptyList(),
    val bankrupt: Boolean = false,
    val passedPayday: Boolean = false,
)

@Serializable
data class TBid(val token: Int, val amount: Int)

/** A live auction for [space]. The last bid is the top bid. */
@Serializable
data class TAuction(val id: Int, val space: Int, val bids: List<TBid> = emptyList()) {
    val top get() = bids.lastOrNull()?.amount ?: 0
    val leader get() = bids.lastOrNull()?.token ?: TurfRules.NOBODY
}

/**
 * An open trade: [from] offers [give] + [giveCash] + [giveCards] jail cards for [to]'s [get] + [getCash] + [getCards].
 * While it's open the turn clock is frozen: [frozenPhase] resumes with [frozenMs] once it closes.
 */
@Serializable
data class TTrade(
    val id: Int,
    val from: Int,
    val to: Int,
    val give: List<Int> = emptyList(),
    val get: List<Int> = emptyList(),
    val giveCash: Int = 0,
    val getCash: Int = 0,
    val giveCards: Int = 0,
    val getCards: Int = 0,
    val counters: Int = 0,
    val frozenPhase: String = "",
    val frozenMs: Long? = null,
)

/** [token] owes [amount] to token [to], or to the bank ([TurfRules.NOBODY]) or everyone else ([HomeTurf.EVERYONE]). */
@Serializable
data class TDebt(val token: Int, val amount: Int, val to: Int, val why: String)

/**
 * Something that just happened, for the TV to animate and phones to react to. [seq] only goes up. Unused fields keep
 * their defaults (and stay out of the JSON).
 */
@Serializable
data class TBeat(
    val seq: Int,
    val kind: String,
    val token: Int = -1,
    val other: Int = -1,
    val space: Int = -1,
    val amount: Int = 0,
    val dice: List<Int> = emptyList(),
    val path: List<Int> = emptyList(),
    val tokens: List<Int> = emptyList(),
    val sips: Int = 0,
    val text: String? = null,
)

@Serializable
data class TCard(val deck: String, val index: Int)

@Serializable
data class TurfState(
    val phase: String,
    val teams: Boolean,
    val tokens: List<TToken>,
    val estate: Estate = Estate(),
    /** Index into [tokens] of whose turn it is. */
    val turn: Int = 0,
    val roll: Roll? = null,
    val doubles: Int = 0,
    /** Card draw order (indexes into the deck); held Get Out cards are out of the pile. */
    val chance: List<Int> = emptyList(),
    val chest: List<Int> = emptyList(),
    val card: TCard? = null,
    /** The space on offer in the buy phase. */
    val buy: Int = -1,
    /** bus | triples: what the choose phase is picking. */
    val choose: String? = null,
    val auction: TAuction? = null,
    val trade: TTrade? = null,
    val debts: List<TDebt> = emptyList(),
    /** What happens once the current debts are settled: [HomeTurf.AFTER_LAND] or [HomeTurf.JAIL_MOVE]. */
    val next: String = HomeTurf.AFTER_LAND,
    /** The speed die showed the scout: after this landing, jump ahead. */
    val scout: Boolean = false,
    /** The next landing pays card rent: rail2 (double railroad) or util10 (10x a fresh roll). */
    val rentMode: String? = null,
    /** This move came from a Timeout roll, so doubles don't roll again. */
    val jailRoll: Boolean = false,
    /** Game time left (null = no limit), as of the last change; [phaseMs] is the current phase's planned length. */
    val clockLeftMs: Long? = null,
    val phaseMs: Long? = null,
    val lastLap: Boolean = false,
    val drinks: Boolean = true,
    /** Quick pace: the old short MOVE dwell and no dice theatre on the TV. Theatre (false) is the default. */
    val quick: Boolean = false,
    val rngSeed: Long = 0,
    val draws: Int = 0,
    val beats: List<TBeat> = emptyList(),
    val beatSeq: Int = 0,
    val ticker: List<String> = emptyList(),
    /** Ids for auctions and trades. */
    val serial: Int = 0,
    /** "from>to" trade offers already made this turn (one per partner per turn). */
    val offered: List<String> = emptyList(),
    /** [beatSeq] when the current turn began: drink calls since then show on phones. */
    val turnBeat: Int = 0,
    /** Final net worth per token, set at the tally. */
    val tally: List<Int> = emptyList(),
    /** A one-off note for the TV (e.g. "Too many for solo: teams it is"). */
    val notice: String? = null,
)
