package partyos.engine.games.trivia

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.BetInfo
import partyos.engine.BetOption
import partyos.engine.BetResult
import partyos.engine.Choice
import partyos.engine.DrinkCall
import partyos.engine.Effect
import partyos.engine.FinaleInfo
import partyos.engine.FinaleResult
import partyos.engine.GameContext
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.HeistInfo
import partyos.engine.LateJoin
import partyos.engine.PlayerId
import partyos.engine.Reject
import partyos.engine.Screen
import partyos.engine.SidesCall
import partyos.engine.SidesInfo
import partyos.engine.Step
import partyos.engine.TeamAnswer
import partyos.engine.TeamGuess
import partyos.engine.TeamTag
import partyos.engine.TriviaAward
import partyos.engine.TriviaReveal
import partyos.engine.TriviaTeam
import partyos.engine.TriviaTv
import partyos.engine.TutorialCard
import partyos.engine.games.bluff.cleanText
import partyos.engine.games.bluff.normalise
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable
data class TTeam(
    val id: String,
    val name: String,
    val color: String,
    val members: List<PlayerId> = emptyList(),
    val score: Int = 0,
    val named: Boolean = false,
    val position: Int = 0,
    val headStart: Int = 0,
)

/** A player's current vote. [at] is when they last changed it. */
@Serializable
data class TVote(
    val choice: String? = null,
    val number: Double? = null,
    val picks: List<String> = emptyList(),
    val locked: Boolean = false,
    val at: Long = 0,
    /** Write It Down: what they typed. */
    val text: String? = null,
)

@Serializable
data class TOption(val id: String, val text: String, val fit: Boolean = false)

@Serializable
data class RememberedTeam(val name: String, val color: String, val named: Boolean, val members: List<PlayerId>)

@Serializable
data class TriviaState(
    val phase: String,
    val format: String = BrainDrain.TEAMUP,
    val round: Int = 0,
    val q: Int = 0,
    val qTotal: Int = 0,
    val perRound: Int = BrainDrain.DEFAULT_N,
    val drinks: Boolean = true,
    /** This show's rounds, in play order (shuffled at the start). Older saved shows default to the fixed order. */
    val order: List<String> = BrainDrain.LEGACY_ORDER,
    val teams: List<TTeam> = emptyList(),
    /** Players present when the show started; Team Up waits for each of them to tap a team. */
    val roster: List<PlayerId> = emptyList(),
    /** Players who tapped their team this Team Up (remembered teams start filled in but unconfirmed). */
    val confirmed: List<PlayerId> = emptyList(),
    /** Content item in play (a set id for Pick a Side). */
    val itemId: String? = null,
    /** The question in play when it came from the live feed, kept here so a restored show doesn't need the feed. */
    val live: McItem? = null,
    val options: List<TOption> = emptyList(),
    val correct: List<String> = emptyList(),
    /** player id → vote, for the current question (or the Heist victim vote). */
    val votes: Map<String, TVote> = emptyMap(),
    val startedAt: Long = 0,
    val durationMs: Long? = null,
    val reveal: TriviaReveal? = null,
    val drink: DrinkCall? = null,
    val hostLine: String? = null,
    val heist: HeistInfo? = null,
    /** Gauntlet catch-up: team id → option shown crossed out. */
    val eliminated: Map<String, String> = emptyMap(),
    val sidesHistory: List<SidesCall> = emptyList(),
    /** Pick a Side: the set's items (indices) in this show's shuffled play order; empty in older saves = pack order. */
    val sidesOrder: List<Int> = emptyList(),
    val podium: List<String> = emptyList(),
    /** player id → running tally for the end-of-show awards. */
    val stats: Map<String, PStats> = emptyMap(),
    val awards: List<TriviaAward> = emptyList(),
    /** Ballpark betting: each team's guess with its odds, frozen when the bet phase opens. */
    val line: List<BetOption> = emptyList(),
    /** player id → their bet this question (the guess they back and their stake). */
    val bets: Map<String, TBet> = emptyMap(),
    /** The Final Wager after the last round; null in shows saved before it existed and in Write It Down. */
    val finale: FinaleState? = null,
)

/** One player's show so far, for the awards. "Right" is their own pick, whatever their team went with. */
@Serializable
data class PStats(
    /** Questions they were on a team for. */
    val asked: Int = 0,
    val answered: Int = 0,
    val right: Int = 0,
    /** Times they were first on their team to answer (teams of two or more). */
    val first: Int = 0,
    /** Picks that went against their team's answer, and how many of those were right while the team was wrong. */
    val rebel: Int = 0,
    val rebelRight: Int = 0,
    /** Their closest Ballpark guess, as a fraction of the answer (0.02 = within 2%). */
    val bestMiss: Double? = null,
)

/**
 * BRAIN DRAIN: a team trivia show in five formats, played in a random order. Teams vote on their phones and the
 * team's answer is the plurality (numbers: the median; typed answers: the most-written one, typos of the right
 * answer counting together). The most points wins. (The Gauntlet race is retired: old saved shows can still finish it.)
 */
class BrainDrain(
    private val pack: TriviaPack = TriviaPack.core(),
    /** Live multiple-choice questions for when the bundled ones run out; null keeps the show fully offline. */
    private val feed: TriviaFeed? = null,
    /** Deal the rounds in a random order each show (tests turn this off to play them in [FORMATS] order). */
    private val shuffleRounds: Boolean = true,
    /** [Mode.SHOW] is Brain Drain; [Mode.WRITE] is Write It Down on its own, a pub quiz of typed answers. */
    private val mode: Mode = Mode.SHOW,
) : GameModule<TriviaState> {
    enum class Mode { SHOW, WRITE }

    override val info = when (mode) {
        Mode.SHOW -> GameInfo(
            id = GAME_ID,
            title = "Brain Drain",
            tagline = "Team trivia in five rounds. Most points wins the night.",
            minPlayers = 2,
            maxPlayers = 16,
            tutorial = listOf(
                TutorialCard("Team up", "Tap a team colour on your phone. The first teammate to type a name names the team."),
                TutorialCard("Argue, then vote", "Everyone votes. Your team's answer is whatever most of you pick."),
                TutorialCard("Five rounds, any order", "Quick Draw, Ballpark, Pick a Side, The Heist and Write It Down. Most points wins."),
            ),
            lateJoin = LateJoin.NEXT_ROUND,
        )
        Mode.WRITE -> GameInfo(
            id = WRITE_GAME_ID,
            title = "Write It Down",
            tagline = "Pub quiz rules: no options, type the answer.",
            minPlayers = 2,
            maxPlayers = 16,
            tutorial = listOf(
                TutorialCard("Team up", "Tap a team colour on your phone. The first teammate to type a name names the team."),
                TutorialCard("No options", "Type the answer on your phone. Your team's most-written answer counts."),
                TutorialCard("Close enough counts", "Typos are fine; numbers must be exact. $WRITE_SHOW_ROUNDS rounds, most points wins."),
            ),
            lateJoin = LateJoin.NEXT_ROUND,
        )
    }
    override val stateSerializer = TriviaState.serializer()

    private val mcById = pack.mc.associateBy { it.id }
    private val ballparkById = pack.ballpark.associateBy { it.id }
    private val sidesById = pack.sides.associateBy { it.id }
    private val gauntletById = pack.gauntlet.associateBy { it.id }

    /** The multiple-choice question in play, bundled or live. */
    private fun mcOf(s: TriviaState): McItem? = mcById[s.itemId] ?: s.live?.takeIf { it.id == s.itemId }

    // ---- flow -----------------------------------------------------------------------------------

    override fun start(ctx: GameContext): Step<TriviaState> {
        val n = (ctx.settings["rounds"] ?: DEFAULT_N).coerceIn(3, 8)
        val requested = ctx.settings["teams"] ?: 0
        val count = if (requested in 2..TEAM_KIT.size) requested else autoTeams(ctx.players.size)
        val s = TriviaState(
            phase = TEAMUP,
            perRound = n,
            drinks = (ctx.settings["drinks"] ?: 1) != 0,
            order = when {
                mode == Mode.WRITE -> List(WRITE_SHOW_ROUNDS) { WRITE }
                shuffleRounds -> dealRounds(ctx.random)
                else -> FORMATS
            },
            teams = seedTeams(count, ctx),
            roster = ctx.players.map { it.id },
            startedAt = ctx.now,
            durationMs = ctx.timer(TEAMUP_MS),
            hostLine = pick(ctx, "Grab a team. Argue about the name later.", "Pick your people wisely.", "Choose your allies."),
        )
        return Step(s, listOf(Effect.Phase(s.durationMs!!)))
    }

    override fun onAction(s: TriviaState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<TriviaState> {
        val kind = payload["kind"]?.jsonPrimitive?.content
        fun str(key: String) = payload[key]?.jsonPrimitive?.content
        return when (s.phase) {
            TEAMUP -> when (kind) {
                "team" -> {
                    val target = s.teams.firstOrNull { it.id == str("option") } ?: throw Reject("BAD_OPTION")
                    val teams = s.teams.map { t ->
                        t.copy(members = if (t.id == target.id) t.members.takeIf { who in it } ?: (t.members + who) else t.members - who)
                    }
                    Step(s.copy(teams = teams, confirmed = (s.confirmed - who) + who))
                }
                "teamName" -> {
                    val team = teamOf(s, who) ?: throw Reject("NO_TEAM")
                    if (team.named) throw Reject("TAKEN")
                    val name = cleanText(str("text") ?: "", MAX_TEAM_NAME) ?: throw Reject("BAD_TEXT")
                    if (s.teams.any { it.id != team.id && normalise(it.name) == normalise(name) }) throw Reject("NAME_TAKEN")
                    Step(s.copy(teams = s.teams.map { if (it.id == team.id) it.copy(name = name, named = true) else it }))
                }
                else -> throw Reject("NOT_NOW")
            }
            QUESTION -> {
                teamOf(s, who) ?: throw Reject("NEXT_ROUND")
                val prev = s.votes[who.v]
                val vote = when (s.format) {
                    QUICK, HEIST, SIDES -> {
                        if (kind != "answer") throw Reject("NOT_NOW")
                        val opt = str("option")?.takeIf { o -> s.options.any { it.id == o } } ?: throw Reject("BAD_OPTION")
                        if (prev?.choice == opt) prev else TVote(choice = opt, at = ctx.now)
                    }
                    BALLPARK -> {
                        if (kind != "guess") throw Reject("NOT_NOW")
                        val v = payload["value"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && abs(it) <= MAX_GUESS } ?: throw Reject("BAD_NUMBER")
                        TVote(number = v, at = ctx.now)
                    }
                    WRITE -> {
                        if (kind != "write") throw Reject("NOT_NOW")
                        val text = cleanText(str("text") ?: "", MAX_WRITE) ?: throw Reject("BAD_TEXT")
                        if (prev?.text == text) prev else TVote(text = text, at = ctx.now)
                    }
                    GAUNTLET -> {
                        if (kind != "multi") throw Reject("NOT_NOW")
                        val ids = s.options.map { it.id }.toSet()
                        val picks = runCatching { payload["picks"]!!.jsonArray.map { it.jsonPrimitive.content } }.getOrNull()
                            ?.distinct()?.takeIf { p -> p.all { it in ids } } ?: throw Reject("BAD_OPTION")
                        TVote(picks = picks, locked = payload["lock"]?.jsonPrimitive?.booleanOrNull ?: false, at = ctx.now)
                    }
                    else -> throw Reject("NOT_NOW")
                }
                Step(s.copy(votes = s.votes + (who.v to vote)))
            }
            BET -> {
                val team = teamOf(s, who) ?: throw Reject("NEXT_ROUND")
                if (kind != "bet") throw Reject("NOT_NOW")
                val option = str("option") ?: throw Reject("BAD_OPTION")
                val bet = when {
                    option == Betting.SKIP -> TBet(null, 0, ctx.now)
                    option == Betting.BACK -> TBet(null, null, ctx.now)
                    option.startsWith("s") -> {
                        val prev = s.bets[who.v]
                        val on = prev?.on ?: throw Reject("NOT_NOW")
                        val amount = Betting.stakeOf(option)?.takeIf { it in Betting.allowedStakes(team.score) } ?: throw Reject("BAD_STAKE")
                        TBet(on, amount, prev.at) // keep when they backed the guess, so "tapped first" isn't reset by locking a stake
                    }
                    else -> {
                        if (s.line.none { it.team == option }) throw Reject("BAD_OPTION")
                        TBet(option, null, ctx.now)
                    }
                }
                Step(s.copy(bets = s.bets + (who.v to bet)))
            }
            FINAL_WAGER -> {
                val team = teamOf(s, who) ?: throw Reject("NEXT_ROUND")
                val fin = s.finale ?: throw Reject("NOT_NOW")
                if (kind != "finalWager") throw Reject("NOT_NOW")
                val option = str("option")?.takeIf { it in finaleOffered(s, team) } ?: throw Reject("BAD_OPTION")
                Step(s.copy(finale = fin.copy(wagers = fin.wagers + (who.v to FWager(option, ctx.now)))))
            }
            FINAL_QUESTION -> {
                teamOf(s, who) ?: throw Reject("NEXT_ROUND")
                if (kind != "finalAnswer") throw Reject("NOT_NOW")
                val text = cleanText(str("text") ?: "", MAX_WRITE) ?: throw Reject("BAD_TEXT")
                val prev = s.votes[who.v]
                Step(s.copy(votes = s.votes + (who.v to if (prev?.text == text) prev else TVote(text = text, at = ctx.now))))
            }
            VICTIM -> {
                val heist = s.heist ?: throw Reject("NOT_NOW")
                if (kind != "victim") throw Reject("NOT_NOW")
                if (teamOf(s, who)?.id != heist.thief) throw Reject("NOT_YOUR_HEIST")
                val target = str("option")?.takeIf { o -> o != heist.thief && s.teams.any { it.id == o && it.members.isNotEmpty() } } ?: throw Reject("BAD_OPTION")
                Step(s.copy(votes = s.votes + (who.v to TVote(choice = target, at = ctx.now))))
            }
            else -> throw Reject("NOT_NOW")
        }
    }

    override fun onDeadline(s: TriviaState, ctx: GameContext): Step<TriviaState> = when (s.phase) {
        TEAMUP -> finishTeamUp(s, ctx)
        INTRO -> nextQuestion(s, ctx)
        QUESTION -> if (s.format == BALLPARK && teamGuesses(s).values.count { it != null } >= 2) enterBet(s, ctx) else score(s, ctx)
        BET -> score(s, ctx)
        FINAL_CATEGORY -> enterFinalWager(s, ctx)
        FINAL_WAGER -> enterFinalQuestion(s, ctx)
        FINAL_QUESTION -> finalReveal(s, ctx)
        FINAL_REVEAL -> finishFinale(s, ctx)
        REVEAL -> afterReveal(s, ctx)
        VICTIM -> steal(s, ctx)
        STEAL -> nextOrStandings(s, ctx)
        STANDINGS -> startRound(s, s.round + 1, ctx)
        PODIUM -> if (s.awards.isNotEmpty()) {
            Step(s.copy(phase = AWARDS, drink = null, hostLine = "And now, the awards.", startedAt = ctx.now, durationMs = AWARDS_MS), listOf(Effect.Phase(AWARDS_MS)))
        } else {
            Step(s, listOf(Effect.Finish))
        }
        else -> Step(s, listOf(Effect.Finish))
    }

    override fun onHost(s: TriviaState, action: String, ctx: GameContext): Step<TriviaState> = when {
        action == SHUFFLE -> shuffle(s, ctx)
        action.startsWith(UNNAME) -> unname(s, action.removePrefix(UNNAME))
        else -> throw Reject("UNSUPPORTED")
    }

    /**
     * The host's veto on a typed team name: it goes back to the team's kit name, and in Team Up the team can name
     * itself again. Once the show is on, the kit name is remembered for the next game instead.
     */
    private fun unname(s: TriviaState, teamId: String): Step<TriviaState> {
        val kit = TEAM_KIT.getOrNull((teamId.removePrefix("T").toIntOrNull() ?: 0) - 1) ?: throw Reject("UNKNOWN_TEAM")
        if (s.teams.none { it.id == teamId }) throw Reject("UNKNOWN_TEAM")
        val teams = s.teams.map { if (it.id == teamId) it.copy(name = kit.second, named = false) else it }
        val next = s.copy(teams = teams)
        if (s.phase == TEAMUP) return Step(next)
        val remembered = teams.map { RememberedTeam(it.name, it.color, it.named, it.members) }
        return Step(next, listOf(Effect.Remember(MEMORY_KEY, json.encodeToString(ListSerializer(RememberedTeam.serializer()), remembered))))
    }

    /** Team Up: deal everyone evenly across the teams at random. Names stay; everyone checks their new team. */
    private fun shuffle(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        if (s.phase != TEAMUP) throw Reject("NOT_NOW")
        val people = ctx.players.map { it.id }.shuffled(ctx.random)
        if (people.size < 2) throw Reject("NOT_ENOUGH_PLAYERS")
        val slots = s.teams.size
        val teams = s.teams.mapIndexed { i, t -> t.copy(members = people.filterIndexed { j, _ -> j % slots == i }) }
        val left = (s.startedAt + (s.durationMs ?: TEAMUP_MS) - ctx.now).coerceAtLeast(0)
        val duration = maxOf(left, SHUFFLE_GRACE_MS)
        return Step(
            s.copy(
                teams = teams, confirmed = emptyList(), roster = (s.roster + people).distinct(), startedAt = ctx.now, durationMs = duration,
                hostLine = pick(ctx, "Shuffled! Check your phone for your new team.", "New teams. No complaining."),
            ),
            listOf(Effect.Phase(duration)),
        )
    }

    private fun finishTeamUp(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        var teams = sync(s.teams, ctx)
        // Everyone piled onto one team: split it so there is a match to play.
        if (teams.count { it.members.isNotEmpty() } < 2 && ctx.players.size >= 2) {
            val big = teams.maxBy { it.members.size }
            val movers = big.members.filterIndexed { i, _ -> i % 2 == 1 }.toSet()
            val empty = teams.first { it.id != big.id }
            teams = teams.map {
                when (it.id) {
                    big.id -> it.copy(members = it.members - movers)
                    empty.id -> it.copy(members = it.members + movers)
                    else -> it
                }
            }
        }
        teams = teams.filter { it.members.isNotEmpty() }
        val remembered = teams.map { RememberedTeam(it.name, it.color, it.named, it.members) }
        val next = startRound(s.copy(teams = teams), 1, ctx)
        val remember = Effect.Remember(MEMORY_KEY, json.encodeToString(ListSerializer(RememberedTeam.serializer()), remembered))
        return next.copy(effects = listOf(remember) + next.effects)
    }

    private fun startRound(s0: TriviaState, round: Int, ctx: GameContext): Step<TriviaState> {
        if (round > s0.order.size) return if (hasFinale(s0)) enterFinale(s0, ctx) else podium(s0, ctx)
        val format = s0.order[round - 1]
        var s = s0.copy(
            phase = INTRO, format = format, round = round, q = 0, votes = emptyMap(), reveal = null, drink = null, line = emptyList(), bets = emptyMap(),
            heist = null, eliminated = emptyMap(), sidesHistory = emptyList(), options = emptyList(), correct = emptyList(),
            itemId = null, teams = sync(s0.teams, ctx), hostLine = null,
        )
        val effects = mutableListOf<Effect>()
        when (format) {
            QUICK, WRITE -> s = s.copy(qTotal = s.perRound)
            BALLPARK, HEIST -> s = s.copy(qTotal = (s.perRound + 1) / 2)
            SIDES -> {
                val set = ctx.fresh(pack.sides) { it.id }.randomOrNull(ctx.random)
                    ?: return startRound(s0, round + 1, ctx)
                effects += Effect.UseContent(set.id)
                // A fresh order every show: the packs are written left, right, left… and shouldn't play that way.
                val order = set.items.indices.shuffled(ctx.random).take(SIDES_ITEMS)
                s = s.copy(itemId = set.id, qTotal = order.size, sidesOrder = order)
            }
            GAUNTLET -> {
                if (pack.gauntlet.none { it.id !in ctx.usedContent }) return podium(s0, ctx)
                val ranks = denseRanks(s.teams.filter { it.members.isNotEmpty() })
                s = s.copy(
                    qTotal = GAUNTLET_PROMPTS,
                    teams = s.teams.map { t -> ((HEAD_START - (ranks[t.id] ?: HEAD_START)).coerceAtLeast(0)).let { h -> t.copy(position = h, headStart = h) } },
                    hostLine = pick(ctx, "Your points just became a head start. Anyone can still win.", "Last round. First team to the finish wins the night."),
                )
            }
        }
        val duration = if (format == GAUNTLET) GAUNTLET_INTRO_MS else INTRO_MS
        return Step(s.copy(startedAt = ctx.now, durationMs = duration), effects + Effect.Phase(duration))
    }

    private fun nextQuestion(s0: TriviaState, ctx: GameContext): Step<TriviaState> {
        val q = s0.q + 1
        if (q > s0.qTotal) return endRound(s0, ctx)
        val base = s0.copy(q = q, votes = emptyMap(), reveal = null, drink = null, line = emptyList(), bets = emptyMap(), heist = null, hostLine = null, live = null, teams = sync(s0.teams, ctx), startedAt = ctx.now)
        return when (s0.format) {
            QUICK, HEIST -> {
                val item = pickMc(s0, ctx) { true } ?: return endRound(s0, ctx)
                val options = (item.wrong + item.answer).shuffled(ctx.random).mapIndexed { i, t -> TOption(LETTERS[i], t, t == item.answer) }
                val live = item.takeIf { it.id !in mcById }
                question(base.copy(itemId = item.id, live = live, options = options, correct = options.filter { it.fit }.map { it.id }), ctx.timer(QUICK_MS), item.id)
            }
            WRITE -> {
                // No options on screen: only questions that still make sense without them.
                val item = pickMc(s0, ctx, ::writable) ?: return endRound(s0, ctx)
                val live = item.takeIf { it.id !in mcById }
                question(base.copy(itemId = item.id, live = live, options = emptyList(), correct = emptyList()), ctx.timer(WRITE_MS), item.id)
            }
            BALLPARK -> {
                val lastCategory = ballparkById[s0.itemId]?.category
                val unused = ctx.fresh(pack.ballpark) { it.id }
                val item = unused.filter { it.category != lastCategory }.ifEmpty { unused }.randomOrNull(ctx.random) ?: return endRound(s0, ctx)
                question(base.copy(itemId = item.id, options = emptyList(), correct = emptyList()), ctx.timer(BALLPARK_MS), item.id)
            }
            SIDES -> {
                val set = sidesById.getValue(requireNotNull(s0.itemId))
                val item = requireNotNull(sideItem(s0, set, q))
                val options = listOf(
                    TOption(TriviaPack.LEFT, set.left, item.side == TriviaPack.LEFT),
                    TOption(TriviaPack.RIGHT, set.right, item.side == TriviaPack.RIGHT),
                )
                question(base.copy(options = options, correct = listOf(item.side)), ctx.timer(SIDES_MS), null)
            }
            GAUNTLET -> {
                val item = pack.gauntlet.filter { it.id !in ctx.usedContent }.randomOrNull(ctx.random) ?: return podium(s0, ctx)
                val options = item.options.shuffled(ctx.random).mapIndexed { i, o -> TOption(LETTERS[i], o.text, o.fit) }
                // Catch-up help: the team(s) in last place see one wrong option crossed out.
                val misfits = options.filter { !it.fit }
                val eliminated = if (misfits.isEmpty()) emptyMap() else trailingByPosition(base.teams).associate { it.id to misfits.random(ctx.random).id }
                question(base.copy(itemId = item.id, options = options, correct = options.filter { it.fit }.map { it.id }, eliminated = eliminated), ctx.timer(GAUNTLET_MS), item.id)
            }
            else -> endRound(s0, ctx)
        }
    }

    /**
     * An unused multiple-choice question that passes [fits], changing the subject from the last one when the pack
     * allows. The bundled pack comes first; once it's used up, a live question if the feed has one ready, and failing
     * that, the bundled questions played longest ago.
     */
    private fun pickMc(s0: TriviaState, ctx: GameContext, fits: (McItem) -> Boolean): McItem? {
        val lastCategory = mcOf(s0)?.category
        val unused = pack.mc.filter { it.id !in ctx.usedContent }
        if (unused.size <= WARM_FEED_AT) feed?.warm()
        val pool = unused.filter(fits)
        fun pick(from: List<McItem>) = from.filter { it.category != lastCategory }.ifEmpty { from }.randomOrNull(ctx.random)
        return pick(pool)
            ?: feed?.take(ctx.usedContent, lastCategory)?.takeIf { it.id !in mcById && TriviaPack.mcProblem(it) == null && fits(it) }
            // Every bundled question played and nothing live: the ones played longest ago come back.
            ?: pick(ctx.fresh(pack.mc.filter(fits)) { it.id })
    }

    private fun question(s: TriviaState, duration: Long, contentId: String?): Step<TriviaState> {
        val effects = listOfNotNull(contentId?.let { Effect.UseContent(it) }, Effect.Phase(duration))
        return Step(s.copy(phase = QUESTION, durationMs = duration), effects)
    }

    private fun endRound(s: TriviaState, ctx: GameContext): Step<TriviaState> =
        if (s.format == GAUNTLET) podium(s, ctx) else standings(s, ctx)

    // ---- scoring --------------------------------------------------------------------------------

    /** Each active team's Ballpark guess (the median of its members' numbers), or null if nobody typed one. */
    private fun teamGuesses(s: TriviaState): Map<String, Double?> =
        s.teams.filter { it.members.isNotEmpty() }.associate { t -> t.id to median(t.members.mapNotNull { s.votes[it.v]?.number }) }

    /** Ballpark guesses are in: show them with their odds and let teams bet before the answer. */
    private fun enterBet(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val line = Betting.line(teamGuesses(s).mapNotNull { (id, g) -> g?.let { id to it } }.toMap())
        val duration = ctx.timer(Betting.BET_MS)
        val next = s.copy(
            phase = BET, line = line, bets = emptyMap(), startedAt = ctx.now, durationMs = duration,
            hostLine = pick(ctx, "Back a guess. Bigger odds, bigger risk.", "Who's closest? Put your points where your mouth is."),
        )
        return Step(next, listOf(Effect.Phase(duration)))
    }

    /** What Brainy says about the bets, if anything is worth saying. */
    private fun betTalk(s: TriviaState, settled: Map<String, BetResult>): String? {
        if (s.line.isNotEmpty() && settled.isEmpty()) return "Nobody dared to bet."
        val big = settled.entries.filter { it.value.won && (it.value.odds == 3 || it.value.stake == Betting.STAKES.last()) }.maxByOrNull { it.value.delta }
        if (big != null) return "${nameOf(s, big.key)} bet big and it paid: +${formatNumber(big.value.delta.toDouble())}."
        val bust = settled.entries.filter { !it.value.won && it.value.stake >= 500 }.maxByOrNull { it.value.stake }
        if (bust != null) return "${nameOf(s, bust.key)} lost a ${formatNumber(bust.value.stake.toDouble())} bet. Ouch."
        return null
    }

    /** Brain Drain only (not Write It Down on its own, nor a show saved with the retired Gauntlet), with a match to settle. */
    private fun hasFinale(s: TriviaState) = mode == Mode.SHOW && GAUNTLET !in s.order && s.teams.count { it.members.isNotEmpty() } >= 2

    /** The wagers this team may pick, from where it stands among the active teams. */
    private fun finaleOffered(s: TriviaState, team: TTeam): List<String> =
        Finale.options(team.score, s.teams.filter { it.members.isNotEmpty() }.map { it.score })

    /** After the last round: pick a short typed question and slam its category. The question itself comes after the wagers. */
    private fun enterFinale(s0: TriviaState, ctx: GameContext): Step<TriviaState> {
        val item = pickMc(s0, ctx) { writable(it) && it.answer.length <= Finale.MAX_ANSWER } ?: return podium(s0, ctx)
        val s = s0.copy(
            phase = FINAL_CATEGORY, format = FINAL, teams = sync(s0.teams, ctx), itemId = item.id, live = item.takeIf { it.id !in mcById },
            options = emptyList(), correct = emptyList(), votes = emptyMap(), reveal = null, drink = null, heist = null, line = emptyList(), bets = emptyMap(),
            finale = FinaleState(category = item.category, answerText = item.answer), startedAt = ctx.now, durationMs = Finale.CATEGORY_MS,
            hostLine = pick(ctx, "Final wager. The category is ${item.category}. Choose wisely.", "One last bet. ${item.category}. Go big or go home."),
        )
        return Step(s, listOf(Effect.UseContent(item.id), Effect.Phase(Finale.CATEGORY_MS)))
    }

    private fun enterFinalWager(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val duration = ctx.timer(Finale.WAGER_MS)
        val next = s.copy(
            phase = FINAL_WAGER, startedAt = ctx.now, durationMs = duration, hostLine = "Pick your wager. Nobody sees it until the reveal.",
            finale = s.finale?.copy(wagers = emptyMap()),
        )
        return Step(next, listOf(Effect.Phase(duration)))
    }

    private fun enterFinalQuestion(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val duration = ctx.timer(Finale.QUESTION_MS)
        return Step(s.copy(phase = FINAL_QUESTION, votes = emptyMap(), startedAt = ctx.now, durationMs = duration, hostLine = null), listOf(Effect.Phase(duration)))
    }

    /** The question is closed: work out every team's result, last place first, and start the reveal. Scores wait until it ends. */
    private fun finalReveal(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val fin = s.finale ?: return podium(s, ctx)
        val active = s.teams.filter { it.members.isNotEmpty() }
        val results = active.sortedBy { it.score }.map { t ->
            val option = Finale.teamWager(t.members, fin.wagers, finaleOffered(s, t))
            val wager = Finale.amount(option, t.score)
            val written = teamWrite(t, s.votes, fin.answerText)
            val right = written?.right == true
            val delta = Finale.settle(t.score, wager, right)
            FinaleResult(t.id, written?.text, right, option, wager, delta, t.score, (t.score + delta).coerceAtLeast(0))
        }
        val leader = active.maxByOrNull { it.score }
        val winner = results.maxByOrNull { it.after }
        val line = when {
            winner == null -> null
            leader != null && winner.team != leader.id -> "${nameOf(s, winner.team)} steal the win!"
            else -> "${nameOf(s, winner.team)} hold on to win!"
        }
        val wrong = results.filter { !it.right }
        val sips = if (wrong.any { it.option == "w75" || it.option == Finale.ALL_IN }) 2 else 1
        val drink = if (s.drinks && wrong.isNotEmpty()) DrinkCall(wrong.map { it.team }, sips, "wrong final") else null
        val duration = Finale.revealMs(results.size)
        return Step(
            s.copy(phase = FINAL_REVEAL, finale = fin.copy(results = results), drink = drink, hostLine = line, startedAt = ctx.now, durationMs = duration),
            listOf(Effect.Phase(duration)),
        )
    }

    /** The reveal is over: the wagers land on the scores (once), then the podium. */
    private fun finishFinale(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val results = s.finale?.results.orEmpty().associateBy { it.team }
        val teams = s.teams.map { t -> results[t.id]?.let { t.copy(score = it.after) } ?: t }
        val effects = results.values.filter { it.delta != 0 }.flatMap { awardTeam(s.teams, it.team, it.delta, "final wager") }
        val next = podium(s.copy(teams = teams, finale = null), ctx)
        return next.copy(effects = effects + next.effects)
    }

    private fun score(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val points = LinkedHashMap<String, Int>()
        val answers: List<TeamAnswer>
        var heist: HeistInfo? = null
        var history = s.sidesHistory
        var teams = s.teams
        val answerText: String
        var number: Double? = null
        val line: String?
        val active = teams.filter { it.members.isNotEmpty() }
        var betDeltas = emptyMap<String, Int>()

        when (s.format) {
            QUICK, HEIST, SIDES -> {
                val base = when (s.format) { QUICK -> QUICK_POINTS; HEIST -> HEIST_POINTS; else -> SIDES_POINTS }
                answers = active.map { t ->
                    val p = plurality(t, s.votes)
                    val right = p != null && p.first in s.correct
                    val ms = p?.let { it.second - s.startedAt }
                    val bonus = if (right && s.format == QUICK) speedBonus(ms ?: 0, s.durationMs ?: QUICK_MS) else 0
                    val pts = if (right) base + bonus else 0
                    if (pts > 0) points[t.id] = pts
                    TeamAnswer(t.id, choice = p?.first, correct = right, points = pts, seconds = ms?.let { it / 1000.0 })
                }
                answerText = s.options.first { it.id in s.correct }.text
                val rightTeams = answers.filter { it.correct }
                if (s.format == HEIST) rightTeams.minByOrNull { it.seconds ?: Double.MAX_VALUE }?.let { heist = HeistInfo(thief = it.team) }
                if (s.format == SIDES) {
                    val set = sidesById.getValue(requireNotNull(s.itemId))
                    history = history + SidesCall(requireNotNull(sideItem(s, set)).text, s.correct.first(), rightTeams.map { it.team })
                    line = null
                } else {
                    line = verdictLine(ctx, rightTeams.map { nameOf(s, it.team) }, active.size)
                }
            }
            WRITE -> {
                val item = requireNotNull(mcOf(s))
                answerText = item.answer
                answers = active.map { t ->
                    val w = teamWrite(t, s.votes, item.answer)
                    val pts = if (w?.right == true) WRITE_POINTS else 0
                    if (pts > 0) points[t.id] = pts
                    TeamAnswer(t.id, text = w?.text, correct = w?.right == true, points = pts, seconds = w?.let { (it.at - s.startedAt) / 1000.0 })
                }
                line = verdictLine(ctx, answers.filter { it.correct }.map { nameOf(s, it.team) }, active.size)
            }
            BALLPARK -> {
                val item = ballparkById.getValue(requireNotNull(s.itemId))
                number = item.answer
                answerText = (if (item.year) item.answer.toLong().toString() else formatNumber(item.answer)) + (item.unit?.let { " $it" } ?: "")
                val guesses = teamGuesses(s)
                val diffs = guesses.mapNotNull { (id, g) -> g?.let { id to abs(it - item.answer) } }.toMap()
                val distinct = diffs.values.distinct().sorted()
                val roundPoints = active.associate { t ->
                    val rank = diffs[t.id]?.let { distinct.indexOf(it) + 1 }
                    val bull = diffs[t.id]?.let { it <= abs(item.answer) * BULLSEYE_TOLERANCE } == true
                    t.id to ((when { rank == 1 -> CLOSEST_POINTS; rank == 2 && active.size >= 3 -> SECOND_POINTS; else -> 0 }) + if (bull) BULLSEYE_POINTS else 0)
                }
                val teamBets = if (s.line.isEmpty()) emptyMap() else active.mapNotNull { t -> Betting.teamBet(t.members, s.bets)?.let { t.id to it } }.toMap()
                val settled = Betting.settle(s.line, teamBets, item.answer, active.associate { it.id to it.score + (roundPoints[it.id] ?: 0) })
                betDeltas = settled.mapValues { it.value.delta }
                answers = active.map { t ->
                    val d = diffs[t.id]
                    val rank = d?.let { distinct.indexOf(it) + 1 }
                    val bull = d != null && d <= abs(item.answer) * BULLSEYE_TOLERANCE
                    val pts = when {
                        rank == 1 -> CLOSEST_POINTS
                        rank == 2 && active.size >= 3 -> SECOND_POINTS
                        else -> 0
                    } + if (bull) BULLSEYE_POINTS else 0
                    if (pts > 0) points[t.id] = pts
                    TeamAnswer(t.id, number = guesses[t.id], correct = rank == 1, points = pts, rank = rank, bullseye = bull, bet = settled[t.id])
                }
                val winners = answers.filter { it.rank == 1 }
                val closestLine = when {
                    winners.isEmpty() -> "Nobody guessed. Bold strategy."
                    winners.any { it.bullseye } -> pick(ctx, "{t} nailed it. Who's googling?", "Bullseye from {t}.")
                        .replace("{t}", nameOf(s, winners.first { it.bullseye }.team))
                    else -> pick(ctx, "{t} was closest, off by {d}.", "{t} takes it, {d} away.")
                        .replace("{t}", winners.joinToString(" and ") { nameOf(s, it.team) })
                        .replace("{d}", formatNumber(diffs.getValue(winners.first().team)))
                }
                line = listOfNotNull(closestLine, betTalk(s, settled)).joinToString(" ")
            }
            GAUNTLET -> {
                answerText = s.options.filter { it.fit }.joinToString(", ") { it.text }
                answers = active.map { t ->
                    val chosen = teamPicks(t, s.votes) - setOfNotNull(s.eliminated[t.id])
                    val moved = chosen.count { it in s.correct } - chosen.count { it !in s.correct }
                    TeamAnswer(t.id, picks = chosen.sorted(), correct = moved > 0, moved = moved)
                }
                val moves = answers.associate { it.team to (it.moved ?: 0) }
                teams = teams.map { t -> t.copy(position = (t.position + (moves[t.id] ?: 0)).coerceIn(0, FINISH)) }
                val lead = teams.filter { it.members.isNotEmpty() }.maxBy { it.position }
                line = if (lead.position >= FINISH) "${lead.name} cross the finish line!"
                else pick(ctx, "{t} lead the race.", "{t} are out in front.", "{t} can smell the finish.").replace("{t}", lead.name)
            }
            else -> return Step(s)
        }

        teams = teams.map { t ->
            val add = (points[t.id] ?: 0) + (betDeltas[t.id] ?: 0)
            if (add == 0) t else t.copy(score = (t.score + add).coerceAtLeast(0))
        }
        // Every teammate gets what the team got, bets included, so the party standings keep up with the team scores.
        val net = points.toMutableMap().also { n -> betDeltas.forEach { (team, d) -> n[team] = (n[team] ?: 0) + d } }.filterValues { it != 0 }
        val effects = net.flatMap { (teamId, pts) -> awardTeam(s.teams, teamId, pts, "${s.format} answer") }
        val revealMs = when (s.format) { SIDES -> SIDES_REVEAL_MS; BALLPARK -> BALLPARK_REVEAL_MS; else -> REVEAL_MS }
        val next = s.copy(
            phase = REVEAL, teams = teams, reveal = TriviaReveal(s.correct, answerText, number, answers), heist = heist,
            sidesHistory = history, hostLine = line, drink = null, startedAt = ctx.now, durationMs = revealMs, stats = tally(s, number),
        )
        return Step(next, effects + Effect.Phase(revealMs))
    }

    /** Adds this question to every teammate's running tally (see [PStats]). [answer] is the Ballpark number. */
    private fun tally(s: TriviaState, answer: Double?): Map<String, PStats> {
        val stats = s.stats.toMutableMap()
        for (t in s.teams.filter { it.members.isNotEmpty() }) {
            val written = if (s.format == WRITE) mcOf(s)?.answer else null
            val teamWritten = written?.let { teamWrite(t, s.votes, it) }
            val teamPick = if (s.format in setOf(QUICK, HEIST, SIDES)) plurality(t, s.votes)?.first else null
            val teamRight = teamPick != null && teamPick in s.correct
            val voters = t.members.filter { s.votes[it.v] != null }
            val first = voters.minByOrNull { s.votes.getValue(it.v).at }?.takeIf { t.members.size >= 2 }
            for (m in t.members) {
                val v = s.votes[m.v]
                val was = stats[m.v] ?: PStats()
                stats[m.v] = if (v == null) was.copy(asked = was.asked + 1) else when (s.format) {
                    QUICK, HEIST, SIDES -> {
                        val right = v.choice in s.correct
                        val rebel = v.choice != teamPick
                        was.copy(
                            asked = was.asked + 1, answered = was.answered + 1, right = was.right + if (right) 1 else 0,
                            first = was.first + if (m == first) 1 else 0, rebel = was.rebel + if (rebel) 1 else 0,
                            rebelRight = was.rebelRight + if (rebel && right && !teamRight) 1 else 0,
                        )
                    }
                    WRITE -> {
                        val key = if (v.text != null && written != null) writeKey(v.text, written) else null
                        val right = key == RIGHT_KEY
                        val rebel = key != null && key != teamWritten?.key
                        was.copy(
                            asked = was.asked + 1, answered = was.answered + 1, right = was.right + if (right) 1 else 0,
                            first = was.first + if (m == first) 1 else 0, rebel = was.rebel + if (rebel) 1 else 0,
                            rebelRight = was.rebelRight + if (rebel && right && teamWritten?.right != true) 1 else 0,
                        )
                    }
                    BALLPARK -> {
                        val miss = if (v.number != null && answer != null) abs(v.number - answer) / maxOf(abs(answer), 1.0) else null
                        was.copy(
                            asked = was.asked + 1, answered = was.answered + 1, right = was.right + if (miss != null && miss <= SHARP_GUESS) 1 else 0,
                            bestMiss = listOfNotNull(was.bestMiss, miss).minOrNull(),
                        )
                    }
                    GAUNTLET -> {
                        val net = v.picks.count { it in s.correct } - v.picks.count { it !in s.correct }
                        was.copy(asked = was.asked + 1, answered = was.answered + 1, right = was.right + if (net > 0) 1 else 0)
                    }
                    else -> was
                }
            }
        }
        return stats
    }

    /**
     * Up to four shout-outs, each to a different player who's still here, brags first, then a roast. Every award has
     * a floor so a short show never hands out something silly; ties go to whoever joined the show first.
     */
    private fun awardsFor(s: TriviaState, ctx: GameContext): List<TriviaAward> {
        val here = ctx.players.map { it.id }.toSet()
        val people = (s.roster + s.teams.flatMap { it.members }).distinct().filter { it in here && it.v in s.stats }
        val out = mutableListOf<TriviaAward>()
        fun give(title: String, roast: Boolean, score: (PStats) -> Double?, line: (PStats) -> String): Boolean {
            if (out.size >= MAX_AWARDS) return false
            val taken = out.map { it.player }.toSet()
            val best = people.filter { it !in taken }.mapNotNull { p -> score(s.stats.getValue(p.v))?.let { p to it } }.maxByOrNull { it.second } ?: return false
            out += TriviaAward(title, best.first, line(s.stats.getValue(best.first.v)), roast)
            return true
        }
        fun times(n: Int) = if (n == 1) "once" else "$n times"
        give("Big Brain", false, { p -> p.right.takeIf { it >= 3 }?.let { it + p.right / maxOf(1.0, p.answered.toDouble()) } }) { "${it.right} of ${it.answered} right" }
        give("Fastest Thumb", false, { p -> p.first.takeIf { it >= 3 }?.toDouble() }) { "First on their team to answer ${times(it.first)}" }
        give("Lone Wolf", false, { p -> p.rebelRight.takeIf { it >= 1 }?.toDouble() }) { "Went against their team and was right ${times(it.rebelRight)}" } ||
            give("Contrarian", true, { p -> p.rebel.takeIf { it >= 3 && p.rebelRight == 0 }?.toDouble() }) { "Went against their team ${times(it.rebel)}. Wrong every time." }
        give("Dead Weight", true, { p -> p.takeIf { it.answered >= 4 && it.right * 3 < it.answered }?.let { 1.0 - it.right / it.answered.toDouble() } }) {
            "${it.right} of ${it.answered} right. The team carried them."
        } || give("Ghost", true, { p -> (p.asked - p.answered).takeIf { it >= 3 }?.toDouble() }) { "Sat out ${it.asked - it.answered} questions" }
        give("Human Calculator", false, { p -> p.bestMiss?.takeIf { it <= 0.05 }?.let { 1.0 - it } }) {
            val m = it.bestMiss ?: 0.0
            if (m < 0.0005) "Nailed a Ballpark number dead on" else "Ballpark guess within ${formatNumber((m * 1000).roundToInt() / 10.0)}%"
        }
        return out
    }

    private fun afterReveal(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val heist = s.heist
        if (s.format == HEIST && heist != null) {
            val others = s.teams.filter { it.id != heist.thief && it.members.isNotEmpty() }
            if (others.isEmpty()) return nextOrStandings(s, ctx)
            if (others.size == 1) return steal(s.copy(votes = emptyMap()), ctx)
            return Step(
                s.copy(
                    phase = VICTIM, votes = emptyMap(), startedAt = ctx.now, durationMs = ctx.timer(VICTIM_MS),
                    hostLine = "${nameOf(s, heist.thief)} were fastest. Who are they robbing?",
                ),
                listOf(Effect.Phase(ctx.timer(VICTIM_MS))),
            )
        }
        if (s.format == GAUNTLET && (s.teams.any { it.position >= FINISH } || s.q >= s.qTotal)) return podium(s, ctx)
        return nextOrStandings(s, ctx)
    }

    private fun nextOrStandings(s: TriviaState, ctx: GameContext) = if (s.q < s.qTotal) nextQuestion(s, ctx) else endRound(s, ctx)

    private fun steal(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val heist = s.heist ?: return nextOrStandings(s, ctx)
        val thief = s.teams.first { it.id == heist.thief }
        val others = s.teams.filter { it.id != thief.id && it.members.isNotEmpty() }
        val voted = plurality(thief, s.votes)?.first
        // Nobody voted: rob the richest team (earlier team on a tie).
        val victim = others.firstOrNull { it.id == voted } ?: others.sortedWith(compareByDescending<TTeam> { it.score }).first()
        val amount = minOf(STEAL_POINTS, victim.score).coerceAtLeast(0)
        val teams = s.teams.map {
            when (it.id) {
                thief.id -> it.copy(score = it.score + amount)
                victim.id -> it.copy(score = it.score - amount)
                else -> it
            }
        }
        val effects = mutableListOf<Effect>()
        if (amount > 0) {
            effects += awardTeam(s.teams, thief.id, amount, "stole from ${victim.name}")
            effects += awardTeam(s.teams, victim.id, -amount, "robbed by ${thief.name}")
        }
        val line = if (amount > 0) {
            pick(ctx, "{t} robbed {v} for {a}.", "{v} just got robbed by {t}.")
                .replace("{t}", thief.name).replace("{v}", victim.name).replace("{a}", formatNumber(amount.toDouble()))
        } else {
            "${victim.name} had nothing to steal. Awkward."
        }
        val drink = if (s.drinks) DrinkCall(listOf(victim.id), 1, "robbed") else null
        return Step(
            s.copy(
                phase = STEAL, teams = teams, heist = heist.copy(victim = victim.id, amount = amount), drink = drink,
                hostLine = line, startedAt = ctx.now, durationMs = STEAL_MS,
            ),
            effects + Effect.Phase(STEAL_MS),
        )
    }

    private fun standings(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val active = s.teams.filter { it.members.isNotEmpty() }
        val low = active.minOfOrNull { it.score }
        val last = if (low == null || active.all { it.score == low }) emptyList() else active.filter { it.score == low }
        val leader = active.maxByOrNull { it.score }
        val drink = if (s.drinks && last.isNotEmpty()) DrinkCall(last.map { it.id }, 1, "last place") else null
        val line = leader?.let {
            pick(ctx, "{t} lead after round {r}.", "{t} on top. For now.", "{t} in front. Everyone else, regroup.")
                .replace("{t}", it.name).replace("{r}", s.round.toString())
        }
        return Step(
            s.copy(phase = STANDINGS, drink = drink, hostLine = line, reveal = null, heist = null, startedAt = ctx.now, durationMs = STANDINGS_MS),
            listOf(Effect.Phase(STANDINGS_MS)),
        )
    }

    private fun podium(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val active = s.teams.filter { it.members.isNotEmpty() }
        val raced = s.format == GAUNTLET
        val order = active.sortedWith(compareByDescending<TTeam> { if (raced) it.position else 0 }.thenByDescending { it.score })
        val effects = mutableListOf<Effect>()
        var teams = s.teams
        if (raced) {
            order.take(PODIUM_BONUS.size).forEachIndexed { i, t ->
                effects += awardTeam(s.teams, t.id, PODIUM_BONUS[i], "finished ${ordinal(i + 1)}")
                teams = teams.map { if (it.id == t.id) it.copy(score = it.score + PODIUM_BONUS[i]) else it }
            }
        }
        val winner = order.firstOrNull()
        winner?.let { effects += Effect.Highlight("${it.name} won ${info.title}") }
        val finished = order.filter { it.position >= FINISH }
        val low = active.minOfOrNull { it.score }
        val drink = when {
            !s.drinks -> null
            // A points show: last place drinks two (nobody, if everyone tied).
            !raced -> active.filter { it.score == low }.takeIf { it.size < active.size }?.let { DrinkCall(it.map { t -> t.id }, 2, "last place") }
            else -> (if (finished.isNotEmpty()) order - finished.toSet() else order.drop(1))
                .takeIf { it.isNotEmpty() }?.let { DrinkCall(it.map { t -> t.id }, 2, "didn't escape") }
        }
        return Step(
            s.copy(
                phase = PODIUM, teams = teams, podium = order.map { it.id }, drink = drink, reveal = null, heist = null,
                hostLine = winner?.let { "${it.name} win ${info.title}!" }, startedAt = ctx.now, durationMs = PODIUM_MS, awards = awardsFor(s, ctx),
            ),
            effects + Effect.Phase(PODIUM_MS),
        )
    }

    // ---- views ----------------------------------------------------------------------------------

    override fun waitingOn(s: TriviaState): Set<PlayerId>? = when (s.phase) {
        // Team Up ends early once everyone has tapped a team and every team with players has a name.
        TEAMUP -> (s.roster.filter { it !in s.confirmed } + s.teams.filter { !it.named }.flatMap { it.members }).toSet()
        QUESTION -> s.teams.flatMap { it.members }.filter { id ->
            val v = s.votes[id.v]
            if (s.format == GAUNTLET) v?.locked != true else v == null
        }.toSet()
        // Every team needs one pick; the question waits for every player's answer.
        FINAL_WAGER -> s.teams.filter { t -> t.members.none { s.finale?.wagers?.containsKey(it.v) == true } }.flatMap { it.members }.toSet()
        FINAL_QUESTION -> s.teams.flatMap { it.members }.filter { s.votes[it.v] == null }.toSet()
        BET -> s.teams.flatMap { it.members }.filter { s.bets[it.v]?.stake == null }.toSet()
        VICTIM -> s.teams.firstOrNull { it.id == s.heist?.thief }?.members?.filter { it.v !in s.votes }?.toSet()
        else -> null
    }

    override fun restorable(s: TriviaState) = s.itemId == null ||
        mcOf(s) != null || s.itemId in ballparkById || s.itemId in sidesById || s.itemId in gauntletById

    override fun tvView(s: TriviaState, ctx: GameContext): TriviaTv {
        val showAnswer = s.phase in setOf(REVEAL, VICTIM, STEAL)
        val live = s.phase == QUESTION || s.phase == BET || s.phase == FINAL_QUESTION || s.phase == FINAL_REVEAL || showAnswer
        val sides = s.itemId?.let(sidesById::get)?.takeIf { s.format == SIDES }
        val prompt = when {
            s.phase == TEAMUP -> "Team up!"
            live -> when (s.format) {
                QUICK, HEIST, WRITE, FINAL -> mcOf(s)?.prompt
                BALLPARK -> ballparkById[s.itemId]?.prompt
                SIDES -> sides?.let { sideItem(s, it) }?.text
                GAUNTLET -> gauntletById[s.itemId]?.prompt
                else -> null
            }
            else -> null
        } ?: ""
        return TriviaTv(
            phase = s.phase,
            format = s.format,
            round = s.round,
            totalRounds = s.order.size,
            q = s.q,
            qTotal = s.qTotal,
            durationMs = s.durationMs,
            prompt = prompt,
            category = when (s.format) {
                QUICK, HEIST, WRITE -> mcOf(s)?.category
                BALLPARK -> ballparkById[s.itemId]?.category
                SIDES -> sides?.prompt
                else -> null
            }.takeIf { live || s.format == SIDES },
            options = if (live) s.options.map { Choice(it.id, it.text) } else emptyList(),
            unit = if (s.format == BALLPARK) ballparkById[s.itemId]?.unit else null,
            teams = s.teams.filter { it.members.isNotEmpty() || s.phase == TEAMUP }.map { t ->
                TriviaTeam(
                    t.id, t.name, t.color, t.members, t.score,
                    answered = if (s.phase == QUESTION || s.phase == FINAL_QUESTION) t.members.count { voted(s, it) } else 0,
                    position = t.position, headStart = t.headStart,
                )
            },
            answered = if (s.phase == QUESTION || s.phase == FINAL_QUESTION) s.teams.sumOf { t -> t.members.count { voted(s, it) } } else 0,
            expected = s.teams.sumOf { it.members.size },
            reveal = if (showAnswer) s.reveal else null,
            sides = sides?.let { SidesInfo(it.left, it.right, s.q, s.qTotal, s.sidesHistory) },
            heist = s.heist.takeIf { showAnswer },
            drink = s.drink,
            bet = if (s.phase == BET) BetInfo(s.line, s.teams.filter { Betting.teamBet(it.members, s.bets) != null }.map { it.id }) else null,
            finale = s.finale?.takeIf { s.phase in FINALE_PHASES }?.let { f ->
                FinaleInfo(
                    category = f.category,
                    locked = if (s.phase == FINAL_WAGER) s.teams.filter { t -> t.members.any { f.wagers.containsKey(it.v) } }.map { it.id } else emptyList(),
                    results = if (s.phase == FINAL_REVEAL) f.results else emptyList(),
                    answerText = f.answerText.takeIf { s.phase == FINAL_REVEAL },
                )
            },
            hostLine = s.hostLine,
            fact = if (showAnswer) factFor(s) else null,
            credit = if (live && s.format in setOf(QUICK, HEIST, WRITE, FINAL)) mcOf(s)?.source else null,
            finishLine = FINISH,
            podium = s.podium,
            awards = if (s.phase == AWARDS) s.awards else emptyList(),
        )
    }

    private fun factFor(s: TriviaState): String? = when (s.format) {
        QUICK, HEIST, WRITE -> mcOf(s)?.fact
        BALLPARK -> ballparkById[s.itemId]?.fact
        else -> null
    }

    private fun voted(s: TriviaState, id: PlayerId): Boolean {
        val v = s.votes[id.v] ?: return false
        return if (s.format == GAUNTLET) v.locked || v.picks.isNotEmpty() else true
    }

    override fun playerView(s: TriviaState, who: PlayerId, ctx: GameContext): Screen {
        val team = teamOf(s, who)
        val tag = team?.let { TeamTag(it.id, it.name, it.color) }
        val mine = s.votes[who.v]
        return when (s.phase) {
            TEAMUP -> if (team != null && !team.named) {
                Screen.TextEntry("Name your team", MAX_TEAM_NAME, null, "teamName", hint = "First name in wins", team = tag)
            } else {
                Screen.ChoiceList(
                    prompt = when {
                        team == null -> "Pick a team"
                        who !in s.confirmed -> "Still ${team.name}? Tap to confirm"
                        else -> "You're on ${team.name}"
                    },
                    options = s.teams.map { Choice(it.id, it.name, it.color, "${it.members.size} in") },
                    selected = team?.id,
                    kind = "team",
                    style = "teams",
                    team = tag,
                )
            }
            INTRO -> Screen.Waiting(ROUND_TITLES.getValue(s.format), ROUND_RULES.getValue(s.format), team = tag)
            QUESTION -> {
                if (team == null) return Screen.Waiting("You're in next question", "We'll put you on the smallest team")
                val votes = teamVotes(team, s)
                when (s.format) {
                    QUICK, HEIST -> Screen.ChoiceList(
                        mcOf(s)?.prompt ?: "", s.options.map { Choice(it.id, it.text) }, mine?.choice, "answer",
                        style = "shapes", votes = votes, team = tag,
                    )
                    SIDES -> {
                        val set = sidesById.getValue(requireNotNull(s.itemId))
                        Screen.ChoiceList(requireNotNull(sideItem(s, set)).text, s.options.map { Choice(it.id, it.text) }, mine?.choice, "answer", style = "sides", votes = votes, team = tag)
                    }
                    BALLPARK -> {
                        val item = ballparkById.getValue(requireNotNull(s.itemId))
                        Screen.NumberEntry(
                            item.prompt, item.unit, mine?.number, "guess",
                            guesses = team.members.filter { it != who }.mapNotNull { id -> s.votes[id.v]?.number?.let { TeamGuess(id, it) } },
                            team = tag,
                        )
                    }
                    WRITE -> {
                        val names = ctx.players.associate { it.id to it.name }
                        val mates = team.members.filter { it != who }.mapNotNull { id -> s.votes[id.v]?.text?.let { "${names[id] ?: "?"}: $it" } }
                        Screen.TextEntry(
                            mcOf(s)?.prompt ?: "", MAX_WRITE, mine?.text, "write",
                            hint = if (mates.isEmpty()) "Spelling doesn't need to be perfect" else "Team: " + mates.joinToString(" · "),
                            team = tag,
                        )
                    }
                    else -> Screen.MultiSelect(
                        gauntletById[s.itemId]?.prompt ?: "", s.options.map { Choice(it.id, it.text) }, mine?.picks ?: emptyList(),
                        mine?.locked ?: false, "multi", eliminated = listOfNotNull(s.eliminated[team.id]), votes = votes, team = tag,
                    )
                }
            }
            BET -> {
                if (team == null) return Screen.Waiting("You're in next question", "We'll put you on the smallest team")
                betScreen(s, team, tag, s.bets[who.v])
            }
            REVEAL -> revealScreen(s, team, tag)
            VICTIM -> {
                val heist = s.heist ?: return Screen.Waiting("Eyes on the TV", team = tag)
                if (team?.id == heist.thief) {
                    Screen.ChoiceList(
                        "Who are you robbing?",
                        s.teams.filter { it.id != team.id && it.members.isNotEmpty() }
                            .map { Choice(it.id, it.name, it.color, "${formatNumber(it.score.toDouble())} pts") },
                        mine?.choice, "victim", style = "teams", votes = teamVotes(team, s), team = tag,
                    )
                } else {
                    Screen.Waiting("The Heist", "${nameOf(s, heist.thief)} are picking who to rob", team = tag)
                }
            }
            STEAL -> {
                val h = s.heist ?: return Screen.Waiting("Eyes on the TV", team = tag)
                when (team?.id) {
                    h.thief -> Screen.Waiting("You stole ${h.amount}!", "From ${nameOf(s, h.victim ?: "")}", "win", tag)
                    h.victim -> Screen.Waiting("You got robbed", "${nameOf(s, h.thief)} took ${h.amount}", "lose", tag)
                    else -> Screen.Waiting("The Heist", s.hostLine, team = tag)
                }
            }
            STANDINGS, PODIUM -> {
                if (team == null) return Screen.Waiting("Eyes on the TV")
                val order = if (s.phase == PODIUM) s.podium else s.teams.filter { it.members.isNotEmpty() }.sortedByDescending { it.score }.map { it.id }
                val place = order.indexOf(team.id) + 1
                Screen.Waiting(
                    title = if (s.phase == PODIUM && place == 1) "You won!" else "${ordinal(place)} place",
                    detail = "${team.name} · ${formatNumber(team.score.toDouble())} pts",
                    tone = if (place == 1) "win" else if (s.drink?.teams?.contains(team.id) == true) "lose" else "neutral",
                    team = tag,
                )
            }
            AWARDS -> s.awards.firstOrNull { it.player == who }
                ?.let { Screen.Waiting("You got ${it.title}", it.line, if (it.roast) "lose" else "win", tag) }
                ?: Screen.Waiting("The awards", "Eyes on the TV", team = tag)
            else -> Screen.Waiting("Eyes on the TV", team = tag)
        }
    }

    /** Two taps on the phone: back a guess, then pick a stake. A locked bet shows its guess and can be changed. */
    private fun betScreen(s: TriviaState, team: TTeam, tag: TeamTag?, mine: TBet?): Screen {
        val backers = LinkedHashMap<String, MutableList<PlayerId>>()
        for (m in team.members) s.bets[m.v]?.on?.let { backers.getOrPut(it) { mutableListOf() } += m }
        val on = mine?.on
        if (on != null && mine.stake == null) {
            val odds = s.line.firstOrNull { it.team == on }?.odds ?: 1
            val stakes = Betting.allowedStakes(team.score).map { Choice(Betting.stakeId(it), "$it pts", detail = "wins ${it * odds}") }
            return Screen.ChoiceList(
                "How much on ${nameOf(s, on)}? Pays $odds×", stakes + Choice(Betting.BACK, "Change guess"), null, "bet",
                style = "teams", votes = backers, team = tag,
            )
        }
        val guesses = s.line.map { o ->
            Choice(o.team, nameOf(s, o.team), s.teams.firstOrNull { it.id == o.team }?.color, "guess ${formatNumber(o.number)} · pays ${o.odds}×")
        }
        val skipped = mine != null && mine.on == null && mine.stake == 0
        return Screen.ChoiceList(
            if (on != null) "Backing ${nameOf(s, on)} for ${mine.stake}. Tap to change" else "Who's closest? Back a guess",
            guesses + Choice(Betting.SKIP, "Skip betting"), on ?: if (skipped) Betting.SKIP else null, "bet",
            style = "teams", votes = backers, team = tag,
        )
    }

    /** Adds "bet +250" or "bet -250" to a Ballpark result screen when the team had a bet. */
    private fun withBet(a: TeamAnswer, screen: Screen): Screen {
        val bet = a.bet?.takeIf { it.on != null }
        if (bet == null || screen !is Screen.Waiting) return screen
        val note = "bet " + if (bet.won) "+${bet.delta}" else if (bet.delta == 0) "lost" else "${bet.delta}"
        return screen.copy(detail = listOfNotNull(screen.detail, note).joinToString(" · "))
    }

    private fun revealScreen(s: TriviaState, team: TTeam?, tag: TeamTag?): Screen {
        val r = s.reveal ?: return Screen.Waiting("Eyes on the TV", team = tag)
        val a = team?.let { t -> r.answers.firstOrNull { it.team == t.id } } ?: return Screen.Waiting("Eyes on the TV", r.answerText, team = tag)
        return when (s.format) {
            BALLPARK -> withBet(a, when {
                a.number == null -> Screen.Waiting("No guess", "It was ${r.answerText}", "lose", tag)
                a.bullseye -> Screen.Waiting("Bullseye!", "+${a.points}", "win", tag)
                a.rank == 1 -> Screen.Waiting("Closest!", "+${a.points} · it was ${r.answerText}", "win", tag)
                else -> Screen.Waiting(
                    "Off by ${formatNumber(abs(a.number - (r.number ?: 0.0)))}", "It was ${r.answerText}",
                    if (a.points > 0) "win" else "lose", tag,
                )
            })
            WRITE -> when {
                a.text == null -> Screen.Waiting("No answer", "It was ${r.answerText}", "lose", tag)
                a.correct -> Screen.Waiting("Correct!", "“${a.text}” counts · +${a.points}", "win", tag)
                else -> Screen.Waiting("Nope", "Your team wrote “${a.text}”. It was ${r.answerText}", "lose", tag)
            }
            GAUNTLET -> {
                val m = a.moved ?: 0
                val title = if (m > 0) "Forward $m!" else if (m < 0) "Back ${-m}" else "Standing still"
                Screen.Waiting(title, "Fits: ${r.answerText}", if (m > 0) "win" else if (m < 0) "lose" else "neutral", tag)
            }
            else -> when {
                a.choice == null -> Screen.Waiting("Too slow", "It was ${r.answerText}", "lose", tag)
                a.correct -> Screen.Waiting("Correct!", "+${a.points}", "win", tag)
                else -> Screen.Waiting("Nope", "It was ${r.answerText}", "lose", tag)
            }
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** Pick a Side's [q]th call (1-based) in this show's order. */
    private fun sideItem(s: TriviaState, set: SidesSet, q: Int = s.q): SidesItem? =
        set.items.getOrNull(s.sidesOrder.getOrNull(q - 1) ?: (q - 1))

    private fun teamOf(s: TriviaState, who: PlayerId) = s.teams.firstOrNull { who in it.members }

    private fun nameOf(s: TriviaState, teamId: String) = s.teams.firstOrNull { it.id == teamId }?.name ?: "?"

    private fun teamVotes(team: TTeam, s: TriviaState): Map<String, List<PlayerId>> {
        val out = LinkedHashMap<String, MutableList<PlayerId>>()
        for (m in team.members) {
            val v = s.votes[m.v] ?: continue
            if (s.format == GAUNTLET && s.phase == QUESTION) v.picks.forEach { out.getOrPut(it) { mutableListOf() } += m }
            else v.choice?.let { out.getOrPut(it) { mutableListOf() } += m }
        }
        return out
    }

    /** The team's most-voted option and when it was first chosen; ties go to the option chosen first. */
    private fun plurality(team: TTeam, votes: Map<String, TVote>): Pair<String, Long>? {
        val vs = team.members.mapNotNull { votes[it.v]?.takeIf { v -> v.choice != null } }
        if (vs.isEmpty()) return null
        val best = vs.groupBy { it.choice!! }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<TVote>>> { it.value.size }.thenBy { e -> e.value.minOf { it.at } })
            .first()
        return best.key to best.value.minOf { it.at }
    }

    private data class WriteAnswer(val key: String, val text: String, val right: Boolean, val at: Long)

    /** How one typed answer groups: every accepted spelling of the right answer is one group. */
    private fun writeKey(text: String, answer: String) = if (AnswerMatch.accepts(text, answer)) RIGHT_KEY else AnswerMatch.key(text)

    /**
     * The team's written answer: the most-written one (typos of the right answer count together), ties to the one
     * written first, shown as its first spelling. Null if nobody on the team typed anything.
     */
    private fun teamWrite(team: TTeam, votes: Map<String, TVote>, answer: String): WriteAnswer? {
        val typed = team.members.mapNotNull { votes[it.v] }.filter { it.text != null }
        if (typed.isEmpty()) return null
        val best = typed.groupBy { writeKey(it.text!!, answer) }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<TVote>>> { it.value.size }.thenBy { e -> e.value.minOf { it.at } })
            .first()
        val first = best.value.minBy { it.at }
        return WriteAnswer(best.key, first.text!!, best.key == RIGHT_KEY, first.at)
    }

    /** Options picked by at least half of the teammates who answered. */
    private fun teamPicks(team: TTeam, votes: Map<String, TVote>): Set<String> {
        val voters = team.members.mapNotNull { votes[it.v] }.filter { it.locked || it.picks.isNotEmpty() }
        if (voters.isEmpty()) return emptySet()
        return voters.flatMap { it.picks }.groupingBy { it }.eachCount().filter { it.value * 2 >= voters.size }.keys
    }

    private fun awardTeam(teams: List<TTeam>, teamId: String, pts: Int, why: String): List<Effect> =
        teams.firstOrNull { it.id == teamId }?.members?.map { Effect.Award(it, pts, why) } ?: emptyList()

    /** Drops players who left and puts newcomers on the smallest team. */
    private fun sync(teams0: List<TTeam>, ctx: GameContext): List<TTeam> {
        val present = ctx.players.map { it.id }.toSet()
        var teams = teams0.map { t -> t.copy(members = t.members.filter { it in present }) }
        if (teams.isEmpty()) return teams
        val placed = teams.flatMap { it.members }.toSet()
        for (p in ctx.players.filter { it.id !in placed }) {
            val smallest = teams.withIndex().minWith(compareBy<IndexedValue<TTeam>> { it.value.members.size }.thenBy { it.index }).value
            teams = teams.map { if (it.id == smallest.id) it.copy(members = it.members + p.id) else it }
        }
        return teams
    }

    private fun seedTeams(count: Int, ctx: GameContext): List<TTeam> {
        val present = ctx.players.map { it.id }.toSet()
        val remembered = ctx.memory[MEMORY_KEY]
            ?.let { runCatching { json.decodeFromString(ListSerializer(RememberedTeam.serializer()), it) }.getOrNull() }
            ?.takeIf { it.size == count }
        return TEAM_KIT.take(count).mapIndexed { i, (color, name) ->
            val r = remembered?.getOrNull(i)
            TTeam(
                id = "T${i + 1}",
                name = r?.name ?: name,
                color = r?.color ?: color,
                members = r?.members?.filter { it in present } ?: emptyList(),
                named = r?.named ?: false,
            )
        }
    }

    private fun trailingByPosition(teams: List<TTeam>): List<TTeam> {
        val active = teams.filter { it.members.isNotEmpty() }
        if (active.size < 2) return emptyList()
        val low = active.minOf { it.position }
        if (active.all { it.position == low }) return emptyList()
        return active.filter { it.position == low }
    }

    private fun denseRanks(teams: List<TTeam>): Map<String, Int> {
        val scores = teams.map { it.score }.distinct().sortedDescending()
        return teams.associate { it.id to scores.indexOf(it.score) }
    }

    private fun verdictLine(ctx: GameContext, right: List<String>, total: Int): String = when {
        right.isEmpty() -> pick(ctx, "Not one team. Not one.", "Zero for everybody. Impressive, honestly.", "The whole room got it wrong. Together.")
        right.size == total && total > 1 -> pick(ctx, "Everybody got it. Suspicious.", "Clean sweep. Too easy?", "The whole room knew that one.")
        right.size == 1 -> pick(ctx, "Only {t} knew that.", "{t} carried that one.", "{t}, alone. Everyone else, look at {t}.").replace("{t}", right.first())
        else -> pick(ctx, "${right.size} of $total teams got it.", "Split room: ${right.size} teams got it.")
    }

    private fun pick(ctx: GameContext, vararg lines: String) = lines[ctx.random.nextInt(lines.size)]

    companion object {
        const val TEAMUP = "teamup"
        const val INTRO = "intro"
        const val QUESTION = "question"
        const val REVEAL = "reveal"
        const val VICTIM = "victim"
        const val STEAL = "steal"
        const val STANDINGS = "standings"
        const val BET = "bet"
        const val FINAL = "final"
        const val FINAL_CATEGORY = "final_category"
        const val FINAL_WAGER = "final_wager"
        const val FINAL_QUESTION = "final_question"
        const val FINAL_REVEAL = "final_reveal"
        private val FINALE_PHASES = setOf(FINAL_CATEGORY, FINAL_WAGER, FINAL_QUESTION, FINAL_REVEAL)
        const val PODIUM = "podium"
        const val AWARDS = "awards"

        /** [HostCmd.GameAction] during Team Up: deal everyone evenly across the teams. */
        const val SHUFFLE = "shuffle"
        /** Host action "unname:<team id>": a typed team name goes back to the kit name. */
        const val UNNAME = "unname:"
        /** After a shuffle, Team Up lasts at least this long so people can find their new team. */
        const val SHUFFLE_GRACE_MS = 20_000L
        const val AWARDS_MS = 14_000L
        const val MAX_AWARDS = 4
        /** A Ballpark guess within 10% counts as right for the awards. */
        const val SHARP_GUESS = 0.10

        const val QUICK = "quick"
        const val BALLPARK = "ballpark"
        const val SIDES = "sides"
        const val HEIST = "heist"
        /** Retired from the show: only a saved show from before the change can still reach it. */
        const val GAUNTLET = "gauntlet"
        const val WRITE = "write"
        /** Every show plays these five, in the order [dealRounds] deals them. */
        val FORMATS = listOf(QUICK, BALLPARK, SIDES, HEIST, WRITE)
        /** The fixed order shows were saved with before rounds were shuffled. */
        val LEGACY_ORDER = listOf(QUICK, BALLPARK, SIDES, HEIST, GAUNTLET)
        private val LETTERS = listOf("a", "b", "c", "d")
        /** Groups every accepted spelling of the right answer (typed keys are letters, digits and spaces only). */
        private const val RIGHT_KEY = "✓"

        val ROUND_TITLES = mapOf(
            FINAL to "The Final Wager", QUICK to "Quick Draw", BALLPARK to "Ballpark", SIDES to "Pick a Side", HEIST to "The Heist", WRITE to "Write It Down", GAUNTLET to "The Gauntlet",
        )
        val ROUND_RULES = mapOf(
            QUICK to "Four answers. Your team's top pick counts. Faster is worth more.",
            FINAL to "Bet your points before you see the question. Last place reveals first.",
            BALLPARK to "Guess the number. Your team's guess is the middle of everyone's. Closest wins. Then bet on whose guess is closest.",
            SIDES to "Quick calls, five seconds each. Which side does it belong on?",
            HEIST to "Right answers win 500. The fastest team robs somebody.",
            WRITE to "No options this time. Type the answer; your team's most-written one counts. Close spelling is fine.",
            GAUNTLET to "Pick every answer that fits. Right picks move you forward, wrong ones back. First to the finish wins.",
        )

        /** The five rounds in a random order, never opening on The Heist (nobody has points to steal yet). */
        fun dealRounds(random: kotlin.random.Random): List<String> {
            val order = FORMATS.shuffled(random).toMutableList()
            if (order.first() == HEIST) {
                val swap = 1 + random.nextInt(order.size - 1)
                order[0] = order[swap].also { order[swap] = HEIST }
            }
            return order
        }

        /** A multiple-choice question that still works with no options shown ("Which of these…" doesn't). */
        fun writable(q: McItem) = q.answer.length <= MAX_WRITE_ANSWER && !OPTION_BOUND.containsMatchIn(q.prompt)
        private val OPTION_BOUND = Regex("(?i)\\b(of these|the following|which one|not a|isn't a|can't|cannot)\\b|\\(not ")

        /** Team colours and default names, in order. */
        val TEAM_KIT = listOf(
            "#FF4B3E" to "Team Tomato",
            "#2F6BFF" to "Team Blueberry",
            "#2FBF55" to "Team Lime",
            "#8B4DFF" to "Team Grape",
            "#FF8A2B" to "Team Tangerine",
            "#FF6FB5" to "Team Bubblegum",
        )

        const val MEMORY_KEY = "trivia.teams"
        /** Game ids: Brain Drain, and Write It Down played on its own (both share teams and the question pool). */
        const val GAME_ID = "trivia"
        const val WRITE_GAME_ID = "writeitdown"
        /** Write It Down on its own: this many rounds of typed answers, with standings between them. */
        const val WRITE_SHOW_ROUNDS = 3
        const val DEFAULT_N = 5
        const val MAX_TEAM_NAME = 20
        const val MAX_GUESS = 1e12

        const val QUICK_POINTS = 1000
        const val SPEED_BONUS = 500
        const val CLOSEST_POINTS = 1000
        const val SECOND_POINTS = 500
        const val BULLSEYE_POINTS = 500
        const val BULLSEYE_TOLERANCE = 0.01
        const val SIDES_POINTS = 200
        const val WRITE_POINTS = 1000
        /** Longest typed answer a phone can send, and longest pack answer Write It Down will ask for. */
        const val MAX_WRITE = 40
        const val MAX_WRITE_ANSWER = 30
        const val HEIST_POINTS = 500
        const val STEAL_POINTS = 500
        const val FINISH = 10
        const val HEAD_START = 3
        val PODIUM_BONUS = listOf(3000, 1500, 500)
        const val SIDES_ITEMS = 7
        const val GAUNTLET_PROMPTS = 8
        /** Unused bundled multiple-choice questions left (about two long shows) when the live feed starts filling. */
        const val WARM_FEED_AT = 24

        const val TEAMUP_MS = 45_000L
        const val INTRO_MS = 7_000L
        const val GAUNTLET_INTRO_MS = 10_000L
        const val QUICK_MS = 25_000L
        const val BALLPARK_MS = 35_000L
        const val SIDES_MS = 6_000L
        const val GAUNTLET_MS = 30_000L
        const val WRITE_MS = 40_000L
        const val REVEAL_MS = 8_000L
        const val BALLPARK_REVEAL_MS = 9_000L
        const val SIDES_REVEAL_MS = 2_500L
        const val VICTIM_MS = 12_000L
        const val STEAL_MS = 6_000L
        const val STANDINGS_MS = 9_000L
        const val PODIUM_MS = 16_000L

        private val json = Json { ignoreUnknownKeys = true }

        fun autoTeams(players: Int) = ((players + 2) / 4).coerceIn(2, TEAM_KIT.size)

        fun speedBonus(elapsedMs: Long, durationMs: Long): Int =
            (SPEED_BONUS * (1.0 - elapsedMs.toDouble() / durationMs)).roundToInt().coerceIn(0, SPEED_BONUS)

        fun median(xs: List<Double>): Double? {
            if (xs.isEmpty()) return null
            val s = xs.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }

        fun formatNumber(x: Double): String {
            val whole = x == Math.rint(x) && abs(x) < 1e15
            return if (whole) "%,d".format(x.toLong()) else "%,.2f".format(x).trimEnd('0').trimEnd('.')
        }

        fun ordinal(n: Int) = n.toString() + when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
    }
}
