package partyos.engine.games.trivia

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.Choice
import partyos.engine.DrinkCall
import partyos.engine.Effect
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
data class TVote(val choice: String? = null, val number: Double? = null, val picks: List<String> = emptyList(), val locked: Boolean = false, val at: Long = 0)

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
    val teams: List<TTeam> = emptyList(),
    /** Players present when the show started; Team Up waits for each of them to tap a team. */
    val roster: List<PlayerId> = emptyList(),
    /** Players who tapped their team this Team Up (remembered teams start filled in but unconfirmed). */
    val confirmed: List<PlayerId> = emptyList(),
    /** Content item in play (a set id for Pick a Side). */
    val itemId: String? = null,
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
    val podium: List<String> = emptyList(),
)

/**
 * BRAIN DRAIN: a team trivia show in five formats. Teams vote on their phones and the team's answer is the
 * plurality (numbers: the median). Quick Draw, Ballpark, Pick a Side and The Heist build a lead; The Gauntlet
 * turns that lead into a head start in a race, so a trailing team can still win the night.
 */
class BrainDrain(private val pack: TriviaPack = TriviaPack.core()) : GameModule<TriviaState> {
    override val info = GameInfo(
        id = "trivia",
        title = "Brain Drain",
        tagline = "Team trivia in five rounds. Win the race, win the night.",
        minPlayers = 2,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Team up", "Tap a team colour on your phone. The first teammate to type a name names the team."),
            TutorialCard("Argue, then vote", "Everyone votes. Your team's answer is whatever most of you pick."),
            TutorialCard("Five rounds", "Quick Draw, Ballpark, Pick a Side, The Heist, then The Gauntlet: a race for the win."),
        ),
        lateJoin = LateJoin.NEXT_ROUND,
    )
    override val stateSerializer = TriviaState.serializer()

    private val mcById = pack.mc.associateBy { it.id }
    private val ballparkById = pack.ballpark.associateBy { it.id }
    private val sidesById = pack.sides.associateBy { it.id }
    private val gauntletById = pack.gauntlet.associateBy { it.id }

    // ---- flow -----------------------------------------------------------------------------------

    override fun start(ctx: GameContext): Step<TriviaState> {
        val n = (ctx.settings["rounds"] ?: DEFAULT_N).coerceIn(3, 8)
        val requested = ctx.settings["teams"] ?: 0
        val count = if (requested in 2..TEAM_KIT.size) requested else autoTeams(ctx.players.size)
        val s = TriviaState(
            phase = TEAMUP,
            perRound = n,
            drinks = (ctx.settings["drinks"] ?: 1) != 0,
            teams = seedTeams(count, ctx),
            roster = ctx.players.map { it.id },
            startedAt = ctx.now,
            durationMs = TEAMUP_MS,
            hostLine = pick(ctx, "Grab a team. Argue about the name later.", "Pick your people wisely.", "Choose your allies."),
        )
        return Step(s, listOf(Effect.Phase(TEAMUP_MS)))
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
        QUESTION -> score(s, ctx)
        REVEAL -> afterReveal(s, ctx)
        VICTIM -> steal(s, ctx)
        STEAL -> nextOrStandings(s, ctx)
        STANDINGS -> startRound(s, s.round + 1, ctx)
        else -> Step(s, listOf(Effect.Finish))
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
        if (round > FORMATS.size) return podium(s0, ctx)
        val format = FORMATS[round - 1]
        var s = s0.copy(
            phase = INTRO, format = format, round = round, q = 0, votes = emptyMap(), reveal = null, drink = null,
            heist = null, eliminated = emptyMap(), sidesHistory = emptyList(), options = emptyList(), correct = emptyList(),
            itemId = null, teams = sync(s0.teams, ctx), hostLine = null,
        )
        val effects = mutableListOf<Effect>()
        when (format) {
            QUICK -> s = s.copy(qTotal = s.perRound)
            BALLPARK, HEIST -> s = s.copy(qTotal = (s.perRound + 1) / 2)
            SIDES -> {
                val set = pack.sides.filter { it.id !in ctx.usedContent }.randomOrNull(ctx.random)
                    ?: return startRound(s0, round + 1, ctx)
                effects += Effect.UseContent(set.id)
                s = s.copy(itemId = set.id, qTotal = minOf(set.items.size, SIDES_ITEMS))
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
        val base = s0.copy(q = q, votes = emptyMap(), reveal = null, drink = null, heist = null, hostLine = null, teams = sync(s0.teams, ctx), startedAt = ctx.now)
        return when (s0.format) {
            QUICK, HEIST -> {
                // Change the subject every question when the pack allows it.
                val lastCategory = mcById[s0.itemId]?.category
                val unused = pack.mc.filter { it.id !in ctx.usedContent }
                val item = unused.filter { it.category != lastCategory }.ifEmpty { unused }.randomOrNull(ctx.random) ?: return endRound(s0, ctx)
                val options = (item.wrong + item.answer).shuffled(ctx.random).mapIndexed { i, t -> TOption(LETTERS[i], t, t == item.answer) }
                question(base.copy(itemId = item.id, options = options, correct = options.filter { it.fit }.map { it.id }), QUICK_MS, item.id)
            }
            BALLPARK -> {
                val lastCategory = ballparkById[s0.itemId]?.category
                val unused = pack.ballpark.filter { it.id !in ctx.usedContent }
                val item = unused.filter { it.category != lastCategory }.ifEmpty { unused }.randomOrNull(ctx.random) ?: return endRound(s0, ctx)
                question(base.copy(itemId = item.id, options = emptyList(), correct = emptyList()), BALLPARK_MS, item.id)
            }
            SIDES -> {
                val set = sidesById.getValue(requireNotNull(s0.itemId))
                val item = set.items[q - 1]
                val options = listOf(
                    TOption(TriviaPack.LEFT, set.left, item.side == TriviaPack.LEFT),
                    TOption(TriviaPack.RIGHT, set.right, item.side == TriviaPack.RIGHT),
                )
                question(base.copy(options = options, correct = listOf(item.side)), SIDES_MS, null)
            }
            GAUNTLET -> {
                val item = pack.gauntlet.filter { it.id !in ctx.usedContent }.randomOrNull(ctx.random) ?: return podium(s0, ctx)
                val options = item.options.shuffled(ctx.random).mapIndexed { i, o -> TOption(LETTERS[i], o.text, o.fit) }
                // Catch-up help: the team(s) in last place see one wrong option crossed out.
                val misfits = options.filter { !it.fit }
                val eliminated = if (misfits.isEmpty()) emptyMap() else trailingByPosition(base.teams).associate { it.id to misfits.random(ctx.random).id }
                question(base.copy(itemId = item.id, options = options, correct = options.filter { it.fit }.map { it.id }, eliminated = eliminated), GAUNTLET_MS, item.id)
            }
            else -> endRound(s0, ctx)
        }
    }

    private fun question(s: TriviaState, duration: Long, contentId: String?): Step<TriviaState> {
        val effects = listOfNotNull(contentId?.let { Effect.UseContent(it) }, Effect.Phase(duration))
        return Step(s.copy(phase = QUESTION, durationMs = duration), effects)
    }

    private fun endRound(s: TriviaState, ctx: GameContext): Step<TriviaState> =
        if (s.format == GAUNTLET) podium(s, ctx) else standings(s, ctx)

    // ---- scoring --------------------------------------------------------------------------------

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
                    history = history + SidesCall(set.items[s.q - 1].text, s.correct.first(), rightTeams.map { it.team })
                    line = null
                } else {
                    line = verdictLine(ctx, rightTeams.map { nameOf(s, it.team) }, active.size)
                }
            }
            BALLPARK -> {
                val item = ballparkById.getValue(requireNotNull(s.itemId))
                number = item.answer
                answerText = (if (item.year) item.answer.toLong().toString() else formatNumber(item.answer)) + (item.unit?.let { " $it" } ?: "")
                val guesses = active.associate { t -> t.id to median(t.members.mapNotNull { s.votes[it.v]?.number }) }
                val diffs = guesses.mapNotNull { (id, g) -> g?.let { id to abs(it - item.answer) } }.toMap()
                val distinct = diffs.values.distinct().sorted()
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
                    TeamAnswer(t.id, number = guesses[t.id], correct = rank == 1, points = pts, rank = rank, bullseye = bull)
                }
                val winners = answers.filter { it.rank == 1 }
                line = when {
                    winners.isEmpty() -> "Nobody guessed. Bold strategy."
                    winners.any { it.bullseye } -> pick(ctx, "{t} nailed it. Who's googling?", "Bullseye from {t}.")
                        .replace("{t}", nameOf(s, winners.first { it.bullseye }.team))
                    else -> pick(ctx, "{t} was closest, off by {d}.", "{t} takes it, {d} away.")
                        .replace("{t}", winners.joinToString(" and ") { nameOf(s, it.team) })
                        .replace("{d}", formatNumber(diffs.getValue(winners.first().team)))
                }
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

        teams = teams.map { t -> points[t.id]?.let { t.copy(score = t.score + it) } ?: t }
        val effects = points.flatMap { (teamId, pts) -> awardTeam(s.teams, teamId, pts, "${s.format} answer") }
        val revealMs = when (s.format) { SIDES -> SIDES_REVEAL_MS; BALLPARK -> BALLPARK_REVEAL_MS; else -> REVEAL_MS }
        val next = s.copy(
            phase = REVEAL, teams = teams, reveal = TriviaReveal(s.correct, answerText, number, answers), heist = heist,
            sidesHistory = history, hostLine = line, drink = null, startedAt = ctx.now, durationMs = revealMs,
        )
        return Step(next, effects + Effect.Phase(revealMs))
    }

    private fun afterReveal(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val heist = s.heist
        if (s.format == HEIST && heist != null) {
            val others = s.teams.filter { it.id != heist.thief && it.members.isNotEmpty() }
            if (others.isEmpty()) return nextOrStandings(s, ctx)
            if (others.size == 1) return steal(s.copy(votes = emptyMap()), ctx)
            return Step(
                s.copy(
                    phase = VICTIM, votes = emptyMap(), startedAt = ctx.now, durationMs = VICTIM_MS,
                    hostLine = "${nameOf(s, heist.thief)} were fastest. Who are they robbing?",
                ),
                listOf(Effect.Phase(VICTIM_MS)),
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
        winner?.let { effects += Effect.Highlight("${it.name} won Brain Drain") }
        val finished = order.filter { it.position >= FINISH }
        val losers = if (!raced) emptyList() else if (finished.isNotEmpty()) order - finished.toSet() else order.drop(1)
        val drink = if (s.drinks && losers.isNotEmpty()) DrinkCall(losers.map { it.id }, 2, "didn't escape") else null
        return Step(
            s.copy(
                phase = PODIUM, teams = teams, podium = order.map { it.id }, drink = drink, reveal = null, heist = null,
                hostLine = winner?.let { "${it.name} win Brain Drain!" }, startedAt = ctx.now, durationMs = PODIUM_MS,
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
        VICTIM -> s.teams.firstOrNull { it.id == s.heist?.thief }?.members?.filter { it.v !in s.votes }?.toSet()
        else -> null
    }

    override fun restorable(s: TriviaState) = s.itemId == null ||
        s.itemId in mcById || s.itemId in ballparkById || s.itemId in sidesById || s.itemId in gauntletById

    override fun tvView(s: TriviaState, ctx: GameContext): TriviaTv {
        val showAnswer = s.phase in setOf(REVEAL, VICTIM, STEAL)
        val live = s.phase == QUESTION || showAnswer
        val sides = s.itemId?.let(sidesById::get)?.takeIf { s.format == SIDES }
        val prompt = when {
            s.phase == TEAMUP -> "Team up!"
            live -> when (s.format) {
                QUICK, HEIST -> mcById[s.itemId]?.prompt
                BALLPARK -> ballparkById[s.itemId]?.prompt
                SIDES -> sides?.items?.getOrNull(s.q - 1)?.text
                GAUNTLET -> gauntletById[s.itemId]?.prompt
                else -> null
            }
            else -> null
        } ?: ""
        return TriviaTv(
            phase = s.phase,
            format = s.format,
            round = s.round,
            totalRounds = FORMATS.size,
            q = s.q,
            qTotal = s.qTotal,
            durationMs = s.durationMs,
            prompt = prompt,
            category = when (s.format) {
                QUICK, HEIST -> mcById[s.itemId]?.category
                BALLPARK -> ballparkById[s.itemId]?.category
                SIDES -> sides?.prompt
                else -> null
            }.takeIf { live || s.format == SIDES },
            options = if (live) s.options.map { Choice(it.id, it.text) } else emptyList(),
            unit = if (s.format == BALLPARK) ballparkById[s.itemId]?.unit else null,
            teams = s.teams.filter { it.members.isNotEmpty() || s.phase == TEAMUP }.map { t ->
                TriviaTeam(
                    t.id, t.name, t.color, t.members, t.score,
                    answered = if (s.phase == QUESTION) t.members.count { voted(s, it) } else 0,
                    position = t.position, headStart = t.headStart,
                )
            },
            answered = if (s.phase == QUESTION) s.teams.sumOf { t -> t.members.count { voted(s, it) } } else 0,
            expected = s.teams.sumOf { it.members.size },
            reveal = if (showAnswer) s.reveal else null,
            sides = sides?.let { SidesInfo(it.left, it.right, s.q, s.qTotal, s.sidesHistory) },
            heist = s.heist.takeIf { showAnswer },
            drink = s.drink,
            hostLine = s.hostLine,
            fact = if (showAnswer) factFor(s) else null,
            finishLine = FINISH,
            podium = s.podium,
        )
    }

    private fun factFor(s: TriviaState): String? = when (s.format) {
        QUICK, HEIST -> mcById[s.itemId]?.fact
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
                        mcById[s.itemId]?.prompt ?: "", s.options.map { Choice(it.id, it.text) }, mine?.choice, "answer",
                        style = "shapes", votes = votes, team = tag,
                    )
                    SIDES -> {
                        val set = sidesById.getValue(requireNotNull(s.itemId))
                        Screen.ChoiceList(set.items[s.q - 1].text, s.options.map { Choice(it.id, it.text) }, mine?.choice, "answer", style = "sides", votes = votes, team = tag)
                    }
                    BALLPARK -> {
                        val item = ballparkById.getValue(requireNotNull(s.itemId))
                        Screen.NumberEntry(
                            item.prompt, item.unit, mine?.number, "guess",
                            guesses = team.members.filter { it != who }.mapNotNull { id -> s.votes[id.v]?.number?.let { TeamGuess(id, it) } },
                            team = tag,
                        )
                    }
                    else -> Screen.MultiSelect(
                        gauntletById[s.itemId]?.prompt ?: "", s.options.map { Choice(it.id, it.text) }, mine?.picks ?: emptyList(),
                        mine?.locked ?: false, "multi", eliminated = listOfNotNull(s.eliminated[team.id]), votes = votes, team = tag,
                    )
                }
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
            else -> Screen.Waiting("Eyes on the TV", team = tag)
        }
    }

    private fun revealScreen(s: TriviaState, team: TTeam?, tag: TeamTag?): Screen {
        val r = s.reveal ?: return Screen.Waiting("Eyes on the TV", team = tag)
        val a = team?.let { t -> r.answers.firstOrNull { it.team == t.id } } ?: return Screen.Waiting("Eyes on the TV", r.answerText, team = tag)
        return when (s.format) {
            BALLPARK -> when {
                a.number == null -> Screen.Waiting("No guess", "It was ${r.answerText}", "lose", tag)
                a.bullseye -> Screen.Waiting("Bullseye!", "+${a.points}", "win", tag)
                a.rank == 1 -> Screen.Waiting("Closest!", "+${a.points} · it was ${r.answerText}", "win", tag)
                else -> Screen.Waiting(
                    "Off by ${formatNumber(abs(a.number - (r.number ?: 0.0)))}", "It was ${r.answerText}",
                    if (a.points > 0) "win" else "lose", tag,
                )
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
        const val PODIUM = "podium"

        const val QUICK = "quick"
        const val BALLPARK = "ballpark"
        const val SIDES = "sides"
        const val HEIST = "heist"
        const val GAUNTLET = "gauntlet"
        val FORMATS = listOf(QUICK, BALLPARK, SIDES, HEIST, GAUNTLET)
        private val LETTERS = listOf("a", "b", "c", "d")

        val ROUND_TITLES = mapOf(QUICK to "Quick Draw", BALLPARK to "Ballpark", SIDES to "Pick a Side", HEIST to "The Heist", GAUNTLET to "The Gauntlet")
        val ROUND_RULES = mapOf(
            QUICK to "Four answers. Your team's top pick counts. Faster is worth more.",
            BALLPARK to "Guess the number. Your team's guess is the middle of everyone's. Closest wins.",
            SIDES to "Quick calls, five seconds each. Which side does it belong on?",
            HEIST to "Right answers win 500. The fastest team robs somebody.",
            GAUNTLET to "Pick every answer that fits. Right picks move you forward, wrong ones back. First to the finish wins.",
        )

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
        const val HEIST_POINTS = 500
        const val STEAL_POINTS = 500
        const val FINISH = 10
        const val HEAD_START = 3
        val PODIUM_BONUS = listOf(3000, 1500, 500)
        const val SIDES_ITEMS = 7
        const val GAUNTLET_PROMPTS = 8

        const val TEAMUP_MS = 45_000L
        const val INTRO_MS = 7_000L
        const val GAUNTLET_INTRO_MS = 10_000L
        const val QUICK_MS = 25_000L
        const val BALLPARK_MS = 35_000L
        const val SIDES_MS = 6_000L
        const val GAUNTLET_MS = 30_000L
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
