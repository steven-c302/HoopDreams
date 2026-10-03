package partyos.engine.games.sprawl

import partyos.engine.Choice
import partyos.engine.GameContext
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SprawlBuild
import partyos.engine.SprawlCard
import partyos.engine.SprawlMe
import partyos.engine.SprawlPartner
import partyos.engine.SprawlPrompt
import partyos.engine.SprawlTradeView
import partyos.engine.games.sprawl.Sprawl.Companion.ANYONE
import partyos.engine.games.sprawl.Sprawl.Companion.DISCARD
import partyos.engine.games.sprawl.Sprawl.Companion.FINISH
import partyos.engine.games.sprawl.Sprawl.Companion.MAIN
import partyos.engine.games.sprawl.Sprawl.Companion.MAX_COUNTERS
import partyos.engine.games.sprawl.Sprawl.Companion.MONO
import partyos.engine.games.sprawl.Sprawl.Companion.PICK
import partyos.engine.games.sprawl.Sprawl.Companion.PODIUM
import partyos.engine.games.sprawl.Sprawl.Companion.ROAD2
import partyos.engine.games.sprawl.Sprawl.Companion.ROBBER
import partyos.engine.games.sprawl.Sprawl.Companion.ROLL
import partyos.engine.games.sprawl.Sprawl.Companion.SETTLEMENT
import partyos.engine.games.sprawl.Sprawl.Companion.SETUP
import partyos.engine.games.sprawl.Sprawl.Companion.STEAL
import partyos.engine.games.sprawl.Sprawl.Companion.TALLY
import partyos.engine.games.sprawl.Sprawl.Companion.TRADE
import partyos.engine.games.sprawl.Sprawl.Companion.VP

/** What each phone shows in Sprawl: your hand, one decision at a time, the island to place on, and trades. */
internal class SprawlPhone(private val names: SprawlNames, private val game: Sprawl) {
    fun view(s: SprawlState, who: PlayerId, ctx: GameContext): Screen.Sprawl {
        val me = s.seats.indexOfFirst { it.player == who }
        val seat = s.seats.getOrNull(me)?.takeIf { !it.gone }
        val b = s.board
        val (spots, spotKind) = if (seat == null) emptyList<Int>() to null else placing(s, me)
        val mainNow = seat != null && s.phase == MAIN && s.turn == me && s.trade == null
        return Screen.Sprawl(
            me = seat?.let { me(s, me, it) },
            prompt = prompt(s, me),
            map = game.mapView(b), robber = b.robber, vOwner = b.vOwner, vLevel = b.vLevel, eOwner = b.eOwner,
            colors = s.seats.map { it.color },
            spots = spots, spotKind = spotKind,
            build = if (mainNow) build(s, me) else SprawlBuild(),
            victims = if (seat != null && s.phase == STEAL && s.turn == me) {
                SprawlRules.victims(b, b.robber, me, s.seats.map { it.hand }).map { v -> Choice(v.toString(), s.seats[v].name, s.seats[v].color, "${s.seats[v].hand.sum()} cards") }
            } else emptyList(),
            partners = if (seat == null) emptyList() else s.seats.indices.filter { it != me && !s.seats[it].gone }.map { SprawlPartner(it, s.seats[it].name, s.seats[it].color, s.seats[it].hand.sum()) },
            trade = s.trade?.let { t -> tradeView(s, t, me) },
            canTrade = mainNow,
            bank = s.bank,
            discard = if (seat != null && s.phase == DISCARD) s.discards.getOrElse(me) { 0 } else 0,
            drink = drinkCall(s, me),
            drinks = s.drinks,
        )
    }

    private fun me(s: SprawlState, i: Int, seat: SSeat): SprawlMe {
        val b = s.board
        val cards = SprawlSetup.DEV_KINDS.mapNotNull { k ->
            val n = seat.dev.count { it == k }
            if (n == 0) null else SprawlCard(k, names.devName(k), n, game.canPlay(s, i, k), seat.fresh.count { it == k })
        }
        return SprawlMe(
            index = i, name = seat.name, color = seat.color, hand = seat.hand, vp = game.vp(s, i), dev = cards,
            pieces = listOf(
                SprawlRules.MAX_ROADS - SprawlRules.roads(b, i),
                SprawlRules.MAX_SETTLEMENTS - SprawlRules.settlements(b, i),
                SprawlRules.MAX_CITIES - SprawlRules.cities(b, i),
            ),
            ratios = SprawlRules.ratios(b, i), mine = s.turn == i && s.phase !in setOf(SETUP, TALLY, PODIUM),
        )
    }

    /** The spots this phone must pick from right now, if it's placing something. */
    private fun placing(s: SprawlState, me: Int): Pair<List<Int>, String?> = when {
        s.trade != null -> emptyList<Int>() to null
        s.phase == SETUP && game.setupSeatOf(s) == me -> if (game.setupPieceOf(s) == SETTLEMENT) SprawlRules.settlementSpots(s.board, me, setup = true) to "vertex"
            else SprawlRules.roadSpots(s.board, me, from = s.setupVertex) to "edge"
        s.phase == ROAD2 && s.turn == me -> SprawlRules.roadSpots(s.board, me) to "edge"
        s.phase == ROBBER && s.turn == me -> s.board.geo.hexes.indices.filter { it != s.board.robber } to "hex"
        else -> emptyList<Int>() to null
    }

    private fun build(s: SprawlState, me: Int): SprawlBuild {
        val b = s.board
        val hand = s.seats[me].hand
        return SprawlBuild(
            roads = if (SprawlRules.affords(hand, SprawlRules.ROAD) && SprawlRules.roads(b, me) < SprawlRules.MAX_ROADS) SprawlRules.roadSpots(b, me) else emptyList(),
            settlements = if (SprawlRules.affords(hand, SprawlRules.SETTLEMENT) && SprawlRules.settlements(b, me) < SprawlRules.MAX_SETTLEMENTS) SprawlRules.settlementSpots(b, me, setup = false) else emptyList(),
            cities = if (SprawlRules.affords(hand, SprawlRules.CITY) && SprawlRules.cities(b, me) < SprawlRules.MAX_CITIES) SprawlRules.citySpots(b, me) else emptyList(),
            dev = SprawlRules.affords(hand, SprawlRules.DEV) && s.deck.isNotEmpty(),
        )
    }

    private fun resourceChoices(s: SprawlState, needBank: Boolean) = (0 until 5).map { r ->
        Choice(r.toString(), names.res(r), detail = if (needBank && s.bank[r] == 0) "Bank's out" else null)
    }

    private fun prompt(s: SprawlState, me: Int): SprawlPrompt {
        if (s.phase == TALLY || s.phase == PODIUM) return over(s, me)
        val seat = s.seats.getOrNull(me) ?: return SprawlPrompt("watch", "You're watching this one", "Grab a drink and heckle. You're in the next game.")
        if (seat.gone) return SprawlPrompt("out", "You left the island", "Cheer from the couch.", tone = "lose")
        val cur = s.seats[s.turn].name
        val mine = s.turn == me
        s.trade?.let { t ->
            return when {
                game.canAccept(s, t, me) -> SprawlPrompt("trade", "${s.seats[t.from].name} wants to trade", "Accept, reject, or counter.",
                    listOf(Choice("accept", "ACCEPT"), Choice("reject", "REJECT")), timed = true)
                t.from == me -> SprawlPrompt("wait", "Waiting on ${if (t.to == ANYONE) "anyone" else s.seats[t.to].name}", "Your offer is on the TV.", listOf(Choice("cancel", "CANCEL OFFER")))
                else -> SprawlPrompt("wait", "${s.seats[t.from].name} is making a deal", "Eyes on the TV.")
            }
        }
        return when (s.phase) {
            SETUP -> {
                val who = game.setupSeatOf(s)
                val piece = game.setupPieceOf(s)
                if (who == me) SprawlPrompt("setup", if (piece == SETTLEMENT) "Place a settlement" else "Place a road",
                    if (piece == SETTLEMENT) "Tap a glowing corner. Big numbers roll more." else "Tap a glowing side next to your new settlement.", timed = true)
                else SprawlPrompt("wait", "${s.seats[who].name} is picking a spot", "You're up soon: eye the good corners.")
            }
            ROLL -> if (mine) SprawlPrompt("roll", "Your roll!", playNote(s, me), listOf(Choice("roll", "ROLL")), timed = true)
                else SprawlPrompt("wait", "$cur is rolling")
            MAIN -> if (mine) SprawlPrompt("main", "Build, trade, or end your turn", "You rolled ${s.dice.sum()}. ${game.vp(s, me)} of ${s.vpTarget} points.",
                    listOf(Choice("end", "END TURN")), timed = true)
                else SprawlPrompt("wait", "$cur is building", "They may offer you a deal.")
            DISCARD -> {
                val owed = s.discards.getOrElse(me) { 0 }
                if (owed > 0) SprawlPrompt("discard", "Discard $owed", "A 7 with more than 7 cards: drop half.", timed = true, tone = "lose")
                else SprawlPrompt("wait", "Everyone's discarding", "Then ${names.landlord} moves in.")
            }
            ROBBER -> if (mine) SprawlPrompt("robber", "Move ${names.landlord}", "Tap a hex. It stops paying, and you rob someone there.", timed = true)
                else SprawlPrompt("wait", "$cur is moving ${names.landlord}", "Please not your place…")
            STEAL -> if (mine) SprawlPrompt("steal", "Rob someone", "You take one random card.", timed = true)
                else SprawlPrompt("wait", "$cur is picking who to rob")
            ROAD2 -> if (mine) SprawlPrompt("road2", "${names.devName(Sprawl.ROAD_TRIP)}: place a free road", "${s.freeRoads} to go.", timed = true)
                else SprawlPrompt("wait", "$cur is on a ${names.devName(Sprawl.ROAD_TRIP)}")
            PICK -> if (mine) {
                if (s.pick == MONO) SprawlPrompt("pick", "${names.devName(MONO)}: name a resource", "Everyone hands you all of theirs.", resourceChoices(s, false), timed = true)
                else SprawlPrompt("pick", "${names.devName(Sprawl.PLENTY)}: take ${if (s.picked < 0) "2" else "1 more"}", "Straight from the bank.", resourceChoices(s, true), timed = true)
            } else SprawlPrompt("wait", "$cur played ${names.devName(s.pick ?: "")}")
            TRADE -> SprawlPrompt("wait", "A deal is on the table")
            else -> SprawlPrompt("wait", "Hang on")
        }
    }

    private fun playNote(s: SprawlState, me: Int) =
        s.seats[me].dev.firstOrNull { game.canPlay(s, me, it) }?.let { "You can play ${names.devName(it)} first (Cards tab)." }

    private fun over(s: SprawlState, me: Int): SprawlPrompt {
        if (me < 0 || s.tally.isEmpty()) return SprawlPrompt("over", "Game over", "Check the TV.")
        val won = s.winner == me
        val rank = s.tally.count { it > s.tally[me] } + 1
        val vpCards = s.seats[me].dev.count { it == VP }
        val detail = "${s.tally[me]} points" + if (vpCards > 0) " (incl. $vpCards ${names.devName(VP)})" else ""
        return SprawlPrompt("over", if (won) "YOU WON!" else "You finished #$rank", detail,
            tone = if (won) "win" else if (rank == s.seats.count { !it.gone }) "lose" else "neutral")
    }

    private fun tradeView(s: SprawlState, t: STrade, me: Int) = SprawlTradeView(
        id = t.id, from = t.from, to = t.to, fromName = s.seats[t.from].name, toName = if (t.to == ANYONE) "Anyone" else s.seats[t.to].name,
        give = t.give, get = t.get,
        role = when {
            me == t.from -> "from"
            me >= 0 && game.canAccept(s, t, me) -> "to"
            else -> "watch"
        },
        canAccept = me >= 0 && game.canAccept(s, t, me) && SprawlRules.affords(s.seats[me].hand, t.get),
        canCounter = me >= 0 && game.canAccept(s, t, me) && t.counters < MAX_COUNTERS && s.phase == TRADE,
    )

    /** Your latest drink call this turn, as a line for the phone. */
    private fun drinkCall(s: SprawlState, me: Int): String? {
        if (!s.drinks || me < 0) return null
        val b = s.beats.lastOrNull { it.kind == "drink" && me in it.seats && it.seq > s.turnBeat } ?: return null
        val what = when (b.sips) {
            FINISH -> "Finish your drink"
            1 -> "Drink 1 sip"
            else -> "Drink ${b.sips} sips"
        }
        return "$what: ${b.text ?: ""}".trimEnd(' ', ':')
    }
}
