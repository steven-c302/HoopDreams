package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("blackjack")
data class BlackjackTv(
    /** bet | play | dealer | settle | podium */
    val phase: String,
    val round: Int,
    val totalRounds: Int,
    val finalRound: Boolean,
    /** House rule for this hand: id, name and one-line text. */
    val rule: String,
    val ruleName: String,
    val ruleText: String,
    /** The player dealing this hand. */
    val dealerId: PlayerId?,
    val dealerName: String,
    val dealerAvatar: Avatar?,
    /** Dealer cards; the hole card is face down (rank 0) until the dealer's turn. */
    val dealer: List<PlayingCard>,
    val dealerTotal: Int?,
    /** Sips riding on the dealer: what they drink if they bust. */
    val onTheLine: Int,
    /** Settle: sips the dealer drinks in total this hand. */
    val dealerDrinks: Int = 0,
    val seats: List<BjSeat>,
    val submitted: Int,
    val expected: Int,
) : TvGame

@Serializable
data class BjSeat(
    val id: PlayerId,
    val name: String,
    val avatar: Avatar,
    val cards: List<PlayingCard>,
    val total: Int,
    /** Sips bet (5 = a shot). */
    val bet: Int,
    val doubled: Boolean,
    /** betting | ready | playing | stood | bust | blackjack */
    val status: String,
    /** Set once settled: blackjack | win | push | lose | bust */
    val outcome: String? = null,
    /** Positive: this player drinks that many sips. Negative: the dealer drinks that many for them. */
    val drinks: Int? = null,
)
