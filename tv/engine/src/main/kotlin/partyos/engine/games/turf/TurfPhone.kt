package partyos.engine.games.turf

import partyos.engine.Choice
import partyos.engine.GameContext
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.TurfBidPad
import partyos.engine.TurfDeed
import partyos.engine.TurfDeedRef
import partyos.engine.TurfMe
import partyos.engine.TurfPartner
import partyos.engine.TurfPrompt
import partyos.engine.TurfTradeView
import partyos.engine.games.turf.HomeTurf.Companion.AUCTION
import partyos.engine.games.turf.HomeTurf.Companion.BUY
import partyos.engine.games.turf.HomeTurf.Companion.BUS
import partyos.engine.games.turf.HomeTurf.Companion.CARD
import partyos.engine.games.turf.HomeTurf.Companion.CHOOSE
import partyos.engine.games.turf.HomeTurf.Companion.DEAL
import partyos.engine.games.turf.HomeTurf.Companion.DEBT
import partyos.engine.games.turf.HomeTurf.Companion.EVERYONE
import partyos.engine.games.turf.HomeTurf.Companion.JAIL
import partyos.engine.games.turf.HomeTurf.Companion.JAIL_FEE
import partyos.engine.games.turf.HomeTurf.Companion.MANAGE
import partyos.engine.games.turf.HomeTurf.Companion.MANAGE_PHASES
import partyos.engine.games.turf.HomeTurf.Companion.MAX_COUNTERS
import partyos.engine.games.turf.HomeTurf.Companion.MOVE
import partyos.engine.games.turf.HomeTurf.Companion.PIECES
import partyos.engine.games.turf.HomeTurf.Companion.PIECES_ALL
import partyos.engine.games.turf.HomeTurf.Companion.PIECE_NAMES
import partyos.engine.games.turf.HomeTurf.Companion.PODIUM
import partyos.engine.games.turf.HomeTurf.Companion.ROLL
import partyos.engine.games.turf.HomeTurf.Companion.TALLY
import partyos.engine.games.turf.HomeTurf.Companion.TEAMUP
import partyos.engine.games.turf.HomeTurf.Companion.TRADE
import partyos.engine.games.turf.HomeTurf.Companion.TRADE_PHASES
import partyos.engine.games.turf.HomeTurf.Companion.sipLabel

/** What each phone shows in Home Turf: one decision at a time, plus your places, your trade partners and the auction. */
internal class TurfPhone(private val names: BoardNames, private val game: HomeTurf) {
    fun view(s: TurfState, who: PlayerId, ctx: GameContext): Screen.Turf {
        val me = s.tokens.indexOfFirst { who in it.members }
        val tok = s.tokens.getOrNull(me)
        val hold = tok != null && tok.seat == who && !tok.bankrupt
        val meView = tok?.let {
            TurfMe(
                index = me, name = it.name, color = it.color, piece = it.piece, cash = it.cash, seat = it.seat,
                seatName = it.seat?.let { id -> ctx.player(id)?.name }, mine = hold, jailed = it.jailed, jailCards = it.jailCards.size,
                bankrupt = it.bankrupt, pos = it.pos, spaceName = names.spaces[it.pos].name,
                worth = if (it.bankrupt) 0 else TurfRules.netWorth(s.estate, me, it.cash),
            )
        }
        val live = tok != null && !tok.bankrupt
        return Screen.Turf(
            me = meView,
            prompt = prompt(s, me, hold, ctx),
            deeds = if (tok == null) emptyList() else deeds(s, me, hold),
            partners = if (!live) emptyList() else s.tokens.indices.filter { it != me && !s.tokens[it].bankrupt }.map { partner(s, it) },
            trade = s.trade?.let { tradeView(s, it, me, hold) },
            canTrade = hold && s.trade == null && s.phase in TRADE_PHASES && s.phase !in HomeTurf.SETUP,
            auction = s.auction?.takeIf { s.phase == AUCTION }?.let { bidPad(s, it, me, hold) },
            pieces = if (s.phase == PIECES && tok != null && tok.piece == null) {
                PIECES_ALL.filter { p -> s.tokens.none { it.piece == p } }.map { Choice(it, PIECE_NAMES.getValue(it)) }
            } else emptyList(),
            drink = drinkCall(s, me),
            drinks = s.drinks,
        )
    }

    private fun name(i: Int) = names.spaces[i].name

    private fun prompt(s: TurfState, me: Int, hold: Boolean, ctx: GameContext): TurfPrompt {
        val tok = s.tokens.getOrNull(me)
        if (s.phase == TALLY || s.phase == PODIUM) return overPrompt(s, me)
        if (tok == null) return TurfPrompt("watch", "You're watching this one", "Grab a drink and heckle. You're in the next game.")
        if (tok.bankrupt) return TurfPrompt("out", "You're out", "Cheer from the couch. Rent can't hurt you now.", tone = "lose")
        val cur = s.tokens[s.turn]
        val mine = s.turn == me
        val seatName = tok.seat?.let { ctx.player(it)?.name } ?: "your team"
        // A teammate has the dice: everything is visible, nothing is tappable.
        fun watch(what: String) = TurfPrompt("watch", "$seatName has the dice", "$what Yell at them.")
        return when (s.phase) {
            TEAMUP -> {
                val mates = tok.members.mapNotNull { ctx.player(it)?.name }
                TurfPrompt("teamup", "You're on ${tok.name}", if (mates.size > 1) "With ${mates.joinToString(", ")}" else null)
            }
            PIECES -> if (tok.piece == null) TurfPrompt("pieces", "Grab your piece!", "First tap wins.", timed = true)
                else TurfPrompt("wait", "You're the ${PIECE_NAMES[tok.piece]}", "Waiting for everyone to grab one")
            DEAL -> {
                val mine2 = s.estate.owned(me)
                TurfPrompt("deal", "Your starter places", mine2.joinToString(" and ") { name(it) }.ifEmpty { null })
            }
            ROLL -> when {
                mine && hold -> TurfPrompt("roll", if (s.doubles > 0) "Doubles! Roll again" else "Your roll!",
                    if (tok.passedPayday) "The speed die is in play." else null, listOf(Choice("roll", "ROLL")), timed = true)
                mine -> watch("It's your team's roll.")
                else -> TurfPrompt("wait", "${cur.name} is rolling")
            }
            JAIL -> when {
                mine && hold -> TurfPrompt(
                    "jail", "You're in ${name(TurfBoard.JAIL)}", "Pay $$JAIL_FEE or use a card and roll, or try for doubles (miss and you pay anyway).",
                    buildList {
                        add(Choice("pay", "PAY $$JAIL_FEE", detail = if (tok.cash < JAIL_FEE) "Not enough cash" else null))
                        if (tok.jailCards.isNotEmpty()) add(Choice("card", "USE CARD"))
                        add(Choice("roll", "ROLL DOUBLES"))
                    },
                    timed = true,
                )
                mine -> watch("Your team is in ${name(TurfBoard.JAIL)}.")
                else -> TurfPrompt("wait", "${cur.name} is in ${name(TurfBoard.JAIL)}")
            }
            MOVE -> TurfPrompt("wait", if (mine) "Moving…" else "${cur.name} is moving", name(cur.pos))
            BUY -> {
                val pos = s.buy
                val price = TurfBoard.spaces.getOrNull(pos)?.price ?: 0
                when {
                    mine && hold -> TurfPrompt(
                        "buy", "Buy ${name(pos)}?",
                        if (tok.cash < price) "Not enough cash: mortgage something in My Places, or let it go to auction." else "Or pass and everyone bids on it.",
                        buildList {
                            if (tok.cash >= price) add(Choice("buy", "BUY $$price"))
                            add(Choice("pass", "AUCTION IT"))
                        },
                        timed = true, space = pos, amount = price,
                    )
                    mine -> watch("Buy ${name(pos)} for $$price?")
                    else -> TurfPrompt("wait", "${cur.name} is eyeing ${name(pos)}", "$$price", space = pos)
                }
            }
            AUCTION -> {
                val a = s.auction
                if (a == null) TurfPrompt("wait", "Auction")
                else if (hold) TurfPrompt("bid", "Auction: ${name(a.space)}", "Highest bid when the clock runs out wins it.", timed = true, space = a.space)
                else watch("${name(a.space)} is up for auction.")
            }
            CARD -> {
                val c = s.card
                if (c == null) TurfPrompt("wait", "Drawing…")
                else TurfPrompt("card", if (c.deck == TurfDecks.CHANCE) names.chance else names.chest, game.cardText(c.deck, c.index, s.drinks),
                    tone = "neutral")
            }
            CHOOSE -> {
                val r = s.roll
                when {
                    mine && hold && s.choose == BUS && r != null -> TurfPrompt(
                        "bus", "Bus! Pick your move", "Move by one die or both.",
                        listOf(Choice("a", "MOVE ${r.a}"), Choice("b", "MOVE ${r.b}"), Choice("total", "MOVE ${r.a + r.b}")), timed = true,
                    )
                    mine && hold -> TurfPrompt("triples", "TRIPLES! Go anywhere", "Pick any space.", spaceChoices(s, me), timed = true)
                    mine -> watch(if (s.choose == BUS) "Bus: pick a move." else "Triples: go anywhere.")
                    else -> TurfPrompt("wait", if (s.choose == BUS) "${cur.name} caught the bus" else "${cur.name} rolled TRIPLES")
                }
            }
            MANAGE -> when {
                mine && hold -> TurfPrompt("manage", "Build, trade, or end your turn", "Build houses in My Places. Three make a hotel.",
                    listOf(Choice("end", "END TURN")), timed = true)
                mine -> watch("Your team is deciding what to build.")
                else -> TurfPrompt("wait", "${cur.name} is building", "Trade tab: make them an offer.")
            }
            DEBT -> {
                val d = s.debts.firstOrNull()
                if (d == null) TurfPrompt("wait", "Settling up")
                else if (d.token == me && hold) TurfPrompt(
                    "debt", "You owe $${d.amount}",
                    "${creditor(s, d)} for ${d.why}. Sell or mortgage in My Places, then pay. Can't? Go bankrupt.",
                    buildList {
                        if (tok.cash >= d.amount) add(Choice("pay", "PAY $${d.amount}"))
                        add(Choice("bankrupt", "GO BANKRUPT"))
                    },
                    timed = true, tone = "lose", amount = d.amount,
                )
                else if (d.token == me) watch("Your team owes $${d.amount}.")
                else TurfPrompt("wait", "${s.tokens[d.token].name} owes $${d.amount}", "They're scraping cash together.")
            }
            TRADE -> {
                val t = s.trade
                when {
                    t == null -> TurfPrompt("wait", "Trade")
                    t.to == me && hold -> TurfPrompt("trade", "${s.tokens[t.from].name} wants to trade", "Accept, reject, or counter.",
                        listOf(Choice("accept", "ACCEPT"), Choice("reject", "REJECT")), timed = true)
                    t.from == me && hold -> TurfPrompt("wait", "Waiting on ${s.tokens[t.to].name}", "Your offer is on the TV.", listOf(Choice("cancel", "CANCEL OFFER")))
                    t.to == me || t.from == me -> watch("Your team is making a deal.")
                    else -> TurfPrompt("wait", "${s.tokens[t.from].name} and ${s.tokens[t.to].name} are making a deal", "Eyes on the TV.")
                }
            }
            else -> TurfPrompt("wait", "Hang on")
        }
    }

    private fun creditor(s: TurfState, d: TDebt) = when {
        d.to >= 0 -> "To ${s.tokens[d.to].name}"
        d.to == EVERYONE -> "To everyone"
        else -> "To the bank"
    }

    private fun overPrompt(s: TurfState, me: Int): TurfPrompt {
        if (me < 0 || s.tally.isEmpty()) return TurfPrompt("over", "Game over", "Check the TV.")
        val worth = s.tally[me]
        val rank = s.tally.count { it > worth } + 1
        val won = rank == 1 && !s.tokens[me].bankrupt
        return TurfPrompt("over", if (won) "YOU WON!" else if (s.tokens[me].bankrupt) "Bankrupt" else "You finished #$rank",
            "Net worth $$worth", tone = if (won) "win" else if (rank == s.tokens.size || s.tokens[me].bankrupt) "lose" else "neutral")
    }

    private fun spaceChoices(s: TurfState, me: Int) = TurfBoard.spaces.map { sp ->
        val owner = s.estate.owner[sp.index]
        val detail = when {
            !sp.buyable -> null
            owner == TurfRules.NOBODY -> "Nobody's: $${sp.price}"
            owner == me -> "Yours"
            else -> "${s.tokens[owner].name}'s"
        }
        Choice(sp.index.toString(), name(sp.index), if (sp.buyable) TurfBoard.colorOf(sp.index) else null, detail)
    }

    private fun groupId(i: Int) = when (TurfBoard.spaces[i].kind) {
        SpaceKind.RAILROAD -> 8
        SpaceKind.UTILITY -> 9
        else -> TurfBoard.spaces[i].group
    }

    private fun tradable(e: Estate, i: Int) = TurfBoard.groupOf(i).none { e.level[it] > 0 }

    private fun deeds(s: TurfState, me: Int, hold: Boolean): List<TurfDeed> {
        val e = s.estate
        val debtor = s.phase == DEBT && s.debts.firstOrNull()?.token == me
        val canManage = hold && s.trade == null && ((me == s.turn && s.phase in MANAGE_PHASES) || debtor)
        return e.owned(me).sortedWith(compareBy({ groupId(it) }, { it })).map { i ->
            val sp = TurfBoard.spaces[i]
            TurfDeed(
                space = i, name = name(i), color = TurfBoard.colorOf(i), group = groupId(i), level = e.level[i], mortgaged = e.mortgaged[i],
                rent = if (sp.kind == SpaceKind.UTILITY) (if (TurfBoard.utilities.all { e.owner[it] == me }) 10 else 4) else TurfRules.rent(e, i, 0),
                build = if (canManage && !debtor && TurfRules.buildRefusal(e, me, i) == null) sp.houseCost else null,
                sell = if (canManage && TurfRules.sellRefusal(e, me, i) == null) sp.houseCost / 2 else null,
                mortgage = if (canManage && TurfRules.mortgageRefusal(e, me, i) == null) sp.mortgage else null,
                unmortgage = if (canManage && !debtor && TurfRules.unmortgageRefusal(e, me, i) == null) TurfRules.unmortgageCost(i) else null,
                tradable = tradable(e, i),
                set = TurfRules.ownsGroup(e, me, i),
            )
        }
    }

    private fun ref(e: Estate, i: Int) = TurfDeedRef(i, name(i), TurfBoard.colorOf(i), groupId(i), e.mortgaged[i], tradable(e, i))

    private fun partner(s: TurfState, i: Int) = s.tokens[i].let { t ->
        TurfPartner(i, t.name, t.color, t.cash, t.jailCards.size, s.estate.owned(i).sortedWith(compareBy({ groupId(it) }, { it })).map { ref(s.estate, it) })
    }

    private fun tradeView(s: TurfState, t: TTrade, me: Int, hold: Boolean) = TurfTradeView(
        id = t.id, from = t.from, to = t.to, fromName = s.tokens[t.from].name, toName = s.tokens[t.to].name,
        give = t.give.map { ref(s.estate, it) }, get = t.get.map { ref(s.estate, it) },
        giveCash = t.giveCash, getCash = t.getCash, giveCards = t.giveCards, getCards = t.getCards,
        role = when (me) { t.from -> "from"; t.to -> "to"; else -> "watch" },
        canCounter = me == t.to && hold && t.counters < MAX_COUNTERS && s.phase == TRADE,
    )

    private fun bidPad(s: TurfState, a: TAuction, me: Int, hold: Boolean): TurfBidPad {
        val sp = TurfBoard.spaces[a.space]
        val tok = s.tokens.getOrNull(me)
        return TurfBidPad(
            auction = a.id, space = a.space, name = name(a.space), color = TurfBoard.colorOf(a.space), price = sp.price, top = a.top,
            leaderName = a.leader.takeIf { it >= 0 }?.let { s.tokens[it].name },
            leading = me >= 0 && a.leader == me, maxBid = tok?.cash ?: 0,
            canBid = hold && tok != null && !tok.bankrupt && tok.cash > a.top,
        )
    }

    /** Your latest drink call this turn, as a line for the phone. */
    private fun drinkCall(s: TurfState, me: Int): String? {
        if (!s.drinks || me < 0) return null
        val b = s.beats.lastOrNull { it.kind == "drink" && me in it.tokens && it.seq > s.turnBeat } ?: return null
        val what = if (b.sips == HomeTurf.FINISH) "Finish your drink" else "Drink ${sipLabel(b.sips)}"
        return "$what: ${b.text ?: ""}".trimEnd(' ', ':')
    }
}

