package partyos.engine.games.blackjack

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.BjSeat
import partyos.engine.BlackjackTv
import partyos.engine.Choice
import partyos.engine.Effect
import partyos.engine.GameContext
import partyos.engine.ofWater
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.LateJoin
import partyos.engine.PlayerId
import partyos.engine.PlayingCard
import partyos.engine.Reject
import partyos.engine.ScoreRow
import partyos.engine.Screen
import partyos.engine.Step
import partyos.engine.TutorialCard
import kotlin.random.Random

@Serializable
data class BjHand(val cards: List<PlayingCard>, val bet: Int, val doubled: Boolean = false, val done: Boolean = false)

@Serializable
data class BjResult(val outcome: String, val drinks: Int)

@Serializable
data class BjState(
    val phase: String,
    val round: Int,
    val totalRounds: Int,
    /** Who deals which hand, in order. */
    val dealerOrder: List<PlayerId> = emptyList(),
    val dealerId: PlayerId? = null,
    /** Players betting against the dealer this hand. */
    val participants: List<PlayerId> = emptyList(),
    val rule: String = DrunkBlackjack.CLASSIC,
    val bets: Map<String, Int> = emptyMap(),
    val hands: Map<String, BjHand> = emptyMap(),
    val dealer: List<PlayingCard> = emptyList(),
    val dealerDone: Boolean = false,
    val shoe: List<PlayingCard> = emptyList(),
    val results: Map<String, BjResult> = emptyMap(),
)

/**
 * Drunk Blackjack: the dealer rotates round the room. Everyone else bets sips (or a shot) against this hand's
 * dealer, and all play their hands at once. Then the dealer plays their own hand from their phone (they must hit
 * under 17). A dealer who busts drinks every bet on the table; otherwise each player either makes the dealer
 * drink their bet or drinks it themselves. Score = sips you made other people drink.
 */
class DrunkBlackjack : GameModule<BjState> {
    override val info = GameInfo(
        id = "blackjack",
        title = "Drunk Blackjack",
        tagline = "Everyone deals. Everyone drinks.",
        minPlayers = 2,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Be the House", "Each hand a different player deals. Everyone else bets sips, or a shot, against them."),
            TutorialCard("Beat the dealer", "Get closer to 21 than the dealer without busting. Win: the dealer drinks your bet. Lose or bust: you drink it."),
            TutorialCard("Dealer busts?", "The dealer plays last from their phone and must hit under 17. Bust, and they drink EVERY bet on the table."),
        ),
        lateJoin = LateJoin.NEXT_ROUND,
    )
    override val stateSerializer = BjState.serializer()

    override fun start(ctx: GameContext): Step<BjState> {
        val order = ctx.players.map { it.id }.shuffled(ctx.random)
        val hands = order.size.coerceIn(2, MAX_HANDS)
        return newRound(BjState(PODIUM, 0, hands, order, shoe = freshShoe(ctx.random)), 1, ctx)
    }

    private fun newRound(prev: BjState, round: Int, ctx: GameContext): Step<BjState> {
        val shoe = if (prev.shoe.size < RESHUFFLE_AT) freshShoe(ctx.random) else prev.shoe
        // Late joiners are added to the end of the dealing order; anyone gone is skipped.
        val order = prev.dealerOrder + ctx.players.map { it.id }.filter { it !in prev.dealerOrder }
        val present = ctx.players.map { it.id }.toSet()
        val dealer = (0 until order.size).map { order[(round - 1 + it) % order.size] }.firstOrNull { it in present && ctx.isConnected(it) }
            ?: order.first { it in present }
        val rule = if (round == 1) CLASSIC else RULES.keys.toList()[ctx.random.nextInt(RULES.size)]
        val players = ctx.players.map { it.id }.filter { it != dealer }
        val s = BjState(BET, round, prev.totalRounds, order, dealer, players, rule, shoe = shoe)
        return Step(s, listOf(Effect.Phase(ctx.timer(BET_MS))))
    }

    override fun onAction(s: BjState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<BjState> {
        val kind = payload["kind"]?.jsonPrimitive?.content
        val option = payload["option"]?.jsonPrimitive?.content
        if (who == s.dealerId) {
            if (s.phase != DEALER || kind != "move" || s.dealerDone) throw Reject("NOT_NOW")
            val t = total(s.dealer)
            return when (option) {
                "hit" -> {
                    val cards = s.dealer + s.shoe.first()
                    val next = s.copy(dealer = cards, shoe = s.shoe.drop(1), dealerDone = total(cards) >= 21)
                    if (next.dealerDone) settle(next, ctx) else Step(next)
                }
                "stand" -> if (t < DEALER_STANDS) throw Reject("MUST_HIT") else settle(s.copy(dealerDone = true), ctx)
                else -> throw Reject("BAD_OPTION")
            }
        }
        if (who !in s.participants) throw Reject("NEXT_ROUND")
        return when {
            s.phase == BET && kind == "bet" -> {
                val sips = option?.removePrefix("b")?.toIntOrNull() ?: throw Reject("BAD_OPTION")
                if (sips !in BETS) throw Reject("BAD_OPTION")
                Step(s.copy(bets = s.bets + (who.v to sips)))
            }
            s.phase == PLAY && kind == "move" -> {
                val hand = s.hands[who.v] ?: throw Reject("NOT_NOW")
                if (hand.done) throw Reject("NOT_NOW")
                val next = when (option) {
                    "hit" -> hand.copy(cards = hand.cards + s.shoe.first()).let { h -> h.copy(done = total(h.cards) >= 21) }
                    "stand" -> hand.copy(done = true)
                    "double" -> {
                        if (hand.cards.size != 2) throw Reject("NOT_NOW")
                        hand.copy(cards = hand.cards + s.shoe.first(), bet = hand.bet * 2, doubled = true, done = true)
                    }
                    else -> throw Reject("BAD_OPTION")
                }
                val drew = next.cards.size - hand.cards.size
                Step(s.copy(hands = s.hands + (who.v to next), shoe = s.shoe.drop(drew)))
            }
            else -> throw Reject("NOT_NOW")
        }
    }

    override fun onDeadline(s: BjState, ctx: GameContext): Step<BjState> = when (s.phase) {
        BET -> deal(s, ctx)
        PLAY -> dealerTurn(s.copy(hands = s.hands.mapValues { it.value.copy(done = true) }), ctx)
        // Time's up for the dealer: they play by the book (hit to 17).
        DEALER -> {
            var dealer = s.dealer
            var shoe = s.shoe
            while (total(dealer) < DEALER_STANDS) { dealer = dealer + shoe.first(); shoe = shoe.drop(1) }
            settle(s.copy(dealer = dealer, shoe = shoe, dealerDone = true), ctx)
        }
        SETTLE -> if (s.round < s.totalRounds) newRound(s, s.round + 1, ctx) else Step(s.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        else -> Step(s, listOf(Effect.Finish))
    }

    private fun deal(s: BjState, ctx: GameContext): Step<BjState> {
        var shoe = s.shoe
        fun draw(): PlayingCard { val c = shoe.first(); shoe = shoe.drop(1); return c }
        // One card round the table, one to the dealer, then the second round.
        val first = s.participants.associate { it.v to draw() }
        val dealer1 = draw()
        val second = s.participants.associate { it.v to draw() }
        val dealer2 = draw()
        val hands = s.participants.associate { p ->
            val cards = listOf(first.getValue(p.v), second.getValue(p.v))
            p.v to BjHand(cards, s.bets[p.v] ?: BETS.first(), done = total(cards) == 21)
        }
        return Step(s.copy(phase = PLAY, hands = hands, dealer = listOf(dealer1, dealer2), shoe = shoe), listOf(Effect.Phase(ctx.timer(PLAY_MS))))
    }

    private fun dealerTurn(s: BjState, ctx: GameContext): Step<BjState> {
        // Nothing to play for if every player busted or has blackjack, or if the dealer has 21 already.
        val alive = s.hands.values.any { total(it.cards) <= 21 && !isBlackjack(it.cards) }
        if (!alive || total(s.dealer) == 21) return settle(s.copy(dealerDone = true), ctx)
        return Step(s.copy(phase = DEALER), listOf(Effect.Phase(ctx.timer(DEALER_MS))))
    }

    private fun settle(s: BjState, ctx: GameContext): Step<BjState> {
        val dTotal = total(s.dealer)
        val dealerBj = isBlackjack(s.dealer)
        val dealerBust = dTotal > 21
        val results = LinkedHashMap<String, BjResult>()
        val effects = mutableListOf<Effect>()
        var dealerDrinks = 0
        for ((id, h) in s.hands) {
            val t = total(h.cards)
            val bj = isBlackjack(h.cards)
            val outcome = when {
                t > 21 -> BUST
                bj && !dealerBj -> BLACKJACK
                bj && dealerBj -> PUSH
                dealerBj -> LOSE
                dealerBust || t > dTotal -> WIN
                t == dTotal -> PUSH
                else -> LOSE
            }
            var sips = h.bet * (if (s.rule == DOUBLE_TROUBLE) 2 else 1)
            if (outcome == BLACKJACK) sips *= 2
            // Positive: the player drinks. Negative: the dealer drinks for this player.
            var drinks = when (outcome) {
                BLACKJACK, WIN -> -sips
                LOSE, BUST -> sips
                else -> 0
            }
            if (s.rule == LUCKY_SEVENS && outcome != BUST) drinks -= h.cards.count { it.rank == 7 }
            results[id] = BjResult(outcome, drinks)
            if (drinks < 0) {
                dealerDrinks += -drinks
                effects += Effect.Award(PlayerId(id), -drinks, "made the dealer drink")
            } else if (drinks > 0) {
                s.dealerId?.let { effects += Effect.Award(it, drinks, "made ${ctx.player(PlayerId(id))?.name} drink") }
            }
        }
        if (dealerBust && dealerDrinks > 0) effects += Effect.Highlight("${ctx.player(s.dealerId ?: PlayerId(""))?.name} busted as dealer and drank $dealerDrinks sips")
        return Step(s.copy(phase = SETTLE, results = results, dealerDone = true), effects + Effect.Phase(SETTLE_MS))
    }

    override fun waitingOn(s: BjState): Set<PlayerId>? = when (s.phase) {
        BET -> s.participants.filter { it.v !in s.bets }.toSet()
        PLAY -> s.participants.filter { s.hands[it.v]?.done == false }.toSet()
        DEALER -> null // the dealer's own actions end this phase
        else -> null
    }

    private fun dealerView(s: BjState): List<PlayingCard> =
        if (s.phase == PLAY && s.dealer.size == 2) listOf(s.dealer[0], HIDDEN) else s.dealer

    /** Sips riding on the dealer right now: what they drink if they bust. */
    private fun onTheLine(s: BjState): Int = s.participants.sumOf { p ->
        val h = s.hands[p.v]
        when {
            h == null -> s.bets[p.v] ?: 0
            total(h.cards) > 21 -> 0
            else -> h.bet * (if (isBlackjack(h.cards)) 2 else 1)
        }
    } * (if (s.rule == DOUBLE_TROUBLE) 2 else 1)

    override fun tvView(s: BjState, ctx: GameContext): BlackjackTv {
        val seats = s.participants.mapNotNull { id ->
            val p = ctx.player(id) ?: return@mapNotNull null
            val h = s.hands[id.v]
            val r = s.results[id.v]
            val status = when {
                s.phase == BET -> if (id.v in s.bets) "ready" else "betting"
                h == null -> "betting"
                total(h.cards) > 21 -> "bust"
                isBlackjack(h.cards) -> "blackjack"
                h.done -> "stood"
                else -> "playing"
            }
            BjSeat(id, p.name, p.avatar, h?.cards.orEmpty(), h?.let { total(it.cards) } ?: 0,
                h?.bet ?: s.bets[id.v] ?: 0, h?.doubled == true, status, r?.outcome, r?.drinks)
        }
        val rule = RULES.getValue(s.rule)
        val dealer = s.dealerId?.let { ctx.player(it) }
        val shown = if (s.phase == BET) emptyList() else dealerView(s)
        return BlackjackTv(
            phase = s.phase, round = s.round, totalRounds = s.totalRounds, finalRound = s.round == s.totalRounds,
            rule = s.rule, ruleName = rule.first, ruleText = rule.second,
            dealerId = s.dealerId, dealerName = dealer?.name ?: "The House", dealerAvatar = dealer?.avatar,
            dealer = shown,
            dealerTotal = if (shown.isEmpty()) null else total(shown.filter { it.rank > 0 }),
            onTheLine = onTheLine(s),
            dealerDrinks = s.results.values.sumOf { if (it.drinks < 0) -it.drinks else 0 },
            seats = seats,
            submitted = when (s.phase) {
                BET -> s.bets.size
                PLAY -> s.hands.values.count { it.done }
                else -> s.participants.size
            },
            expected = s.participants.size,
        )
    }

    override fun playerView(s: BjState, who: PlayerId, ctx: GameContext): Screen {
        if (s.phase == PODIUM) return Screen.Scores("Sips handed out", rows(ctx))
        val rule = RULES.getValue(s.rule)
        val water = ofWater(ctx.player(who)?.water == true)
        if (who == s.dealerId) return dealerScreen(s, rule, water)
        if (who !in s.participants) return Screen.Waiting("You're in next hand", "Grab a drink and watch the table")
        val hand = s.hands[who.v]
        val dealerName = s.dealerId?.let { ctx.player(it)?.name } ?: "the dealer"
        return when (s.phase) {
            BET -> Screen.Cards(
                title = "Bet against $dealerName", hand = emptyList(), total = null, dealer = emptyList(),
                actions = if (who.v in s.bets) emptyList() else BETS.map { Choice("b$it", sipLabel(it)) },
                kind = "bet",
                note = s.bets[who.v]?.let { "Locked in: ${sipLabel(it)} · ${rule.first}" } ?: "${rule.first}: ${rule.second}",
                tone = "neutral",
            )
            PLAY -> {
                val h = hand ?: return Screen.Waiting("Dealing…")
                val t = total(h.cards)
                val actions = if (h.done) emptyList() else buildList {
                    add(Choice("hit", "HIT"))
                    add(Choice("stand", "STAND"))
                    if (h.cards.size == 2) add(Choice("double", "DOUBLE"))
                }
                val note = when {
                    t > 21 -> "BUST! Drink ${sipLabel(h.bet)}$water."
                    isBlackjack(h.cards) -> "BLACKJACK! $dealerName drinks double."
                    h.done -> "Standing on $t. Now $dealerName plays…"
                    else -> "${sipLabel(h.bet)} riding · double to double the drinks"
                }
                Screen.Cards("Your hand", h.cards, t, dealerView(s), actions, "move", note,
                    if (t > 21) "lose" else if (isBlackjack(h.cards)) "win" else "neutral")
            }
            DEALER -> Screen.Cards("$dealerName is playing", hand?.cards.orEmpty(), hand?.let { total(it.cards) }, s.dealer, emptyList(), "move",
                "Eyes on the TV. Pray they bust.", "neutral")
            else -> {
                val r = s.results[who.v]
                val headline = when (r?.outcome) {
                    BLACKJACK -> "BLACKJACK!"
                    WIN -> "YOU WIN"
                    PUSH -> "PUSH"
                    BUST -> "BUST"
                    else -> "DEALER WINS"
                }
                val d = r?.drinks ?: 0
                val call = when {
                    d > 0 -> "DRINK ${sipLabel(d)}${water.uppercase()}"
                    d < 0 -> "$dealerName drinks ${sipLabel(-d)}"
                    else -> "Nobody drinks"
                }
                Screen.Cards(headline, hand?.cards.orEmpty(), hand?.let { total(it.cards) }, s.dealer, emptyList(), "move", call,
                    when { d < 0 -> "win"; d > 0 -> "lose"; else -> "push" })
            }
        }
    }

    private fun dealerScreen(s: BjState, rule: Pair<String, String>, water: String): Screen {
        val t = total(s.dealer)
        val line = onTheLine(s)
        return when (s.phase) {
            BET -> Screen.Cards("You're the House", emptyList(), null, emptyList(), emptyList(), "move",
                "Everyone is betting against you. ${rule.first}: ${rule.second}", "neutral")
            PLAY -> Screen.Cards("You're the House", s.dealer, t, emptyList(), emptyList(), "move",
                "Players are playing. $line sips riding on you.", "neutral")
            DEALER -> Screen.Cards(
                "Your turn to deal", s.dealer, t, emptyList(),
                if (s.dealerDone) emptyList() else buildList { add(Choice("hit", "HIT")); if (t >= DEALER_STANDS) add(Choice("stand", "STAND")) },
                "move",
                if (t < DEALER_STANDS) "Under 17: you must hit. Bust and you drink $line sips$water." else "$line sips riding. Stand, or push your luck?",
                if (t > 21) "lose" else "neutral",
            )
            else -> {
                val drank = s.results.values.sumOf { if (it.drinks < 0) -it.drinks else 0 }
                val gave = s.results.values.sumOf { if (it.drinks > 0) it.drinks else 0 }
                Screen.Cards(if (t > 21) "YOU BUSTED" else "House has $t", s.dealer, t, emptyList(), emptyList(), "move",
                    if (drank > 0) "DRINK ${sipLabel(drank)}${water.uppercase()} · you made the table drink $gave" else "You drink nothing · the table drinks $gave",
                    if (drank > gave) "lose" else "win")
            }
        }
    }

    private fun rows(ctx: GameContext) =
        ctx.players.map { ScoreRow(it.id, it.name, it.avatar, ctx.scores[it.id] ?: 0) }.sortedByDescending { it.score }

    companion object {
        const val BET = "bet"
        const val PLAY = "play"
        const val DEALER = "dealer"
        const val SETTLE = "settle"
        const val PODIUM = "podium"
        const val BLACKJACK = "blackjack"
        const val WIN = "win"
        const val PUSH = "push"
        const val LOSE = "lose"
        const val BUST = "bust"
        const val CLASSIC = "classic"
        const val DOUBLE_TROUBLE = "double_trouble"
        const val LUCKY_SEVENS = "lucky_sevens"
        val RULES = linkedMapOf(
            CLASSIC to ("Classic Rules" to "No tricks. Just you, the dealer and your liver."),
            DOUBLE_TROUBLE to ("Double Trouble" to "Every drink this hand is doubled."),
            LUCKY_SEVENS to ("Lucky Sevens" to "Every 7 in your final hand makes the dealer drink 1 more."),
        )
        /** Bet sizes in sips; 5 is a shot. */
        val BETS = listOf(1, 2, 3, 5)
        const val SHOT = 5
        const val DEALER_STANDS = 17
        const val MAX_HANDS = 10
        const val RESHUFFLE_AT = 60
        const val BET_MS = 15_000L
        const val PLAY_MS = 25_000L
        const val DEALER_MS = 30_000L
        const val SETTLE_MS = 10_000L
        const val PODIUM_MS = 15_000L
        val HIDDEN = PlayingCard(0, 0)

        fun sipLabel(n: Int): String {
            val shots = n / SHOT
            val sips = n % SHOT
            return listOfNotNull(
                if (shots > 0) "$shots shot${if (shots > 1) "s" else ""}" else null,
                if (sips > 0) "$sips sip${if (sips > 1) "s" else ""}" else null,
            ).joinToString(" + ").ifEmpty { "0 sips" }
        }

        fun value(c: PlayingCard) = when {
            c.rank == 1 -> 11
            c.rank >= 10 -> 10
            else -> c.rank
        }

        /** Best blackjack total: aces count 11 until that would bust. */
        fun total(cards: List<PlayingCard>): Int {
            var t = cards.sumOf { value(it) }
            var aces = cards.count { it.rank == 1 }
            while (t > 21 && aces > 0) { t -= 10; aces-- }
            return t
        }

        fun isBlackjack(cards: List<PlayingCard>) = cards.size == 2 && total(cards) == 21

        fun freshShoe(random: Random): List<PlayingCard> =
            (0 until 4).flatMap { (0..3).flatMap { suit -> (1..13).map { PlayingCard(it, suit) } } }.shuffled(random)
    }
}
