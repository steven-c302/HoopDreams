package partyos.engine.games.doodle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.Choice
import partyos.engine.DoodleDelta
import partyos.engine.DoodleDrink
import partyos.engine.DoodleMissTv
import partyos.engine.DoodleShot
import partyos.engine.DoodleSolver
import partyos.engine.DoodleTv
import partyos.engine.Effect
import partyos.engine.GameContext
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.InkAware
import partyos.engine.LateJoin
import partyos.engine.PlayerId
import partyos.engine.Reject
import partyos.engine.ScoreRow
import partyos.engine.Screen
import partyos.engine.Step
import partyos.engine.TutorialCard
import partyos.engine.games.bluff.cleanText
import partyos.engine.games.trivia.AnswerMatch
import partyos.engine.ofWater
import kotlin.random.Random

@Serializable
data class DoodleOption(val id: String, val word: String, val difficulty: Int)

/** A wrong guess: [id] is the guesser. */
@Serializable
data class DoodleMiss(val id: String, val text: String)

/** A finished turn, kept for the podium gallery. */
@Serializable
data class DoodleDone(val turn: Int, val word: String, val drawer: String, val first: String? = null)

@Serializable
data class DoodleState(
    val phase: String,
    val turn: Int,
    val totalTurns: Int,
    /** drawer id → turns drawn so far, this game. */
    val turns: Map<String, Int> = emptyMap(),
    /** Who is in this turn (connected when it began); the drawer is one of them. Later joiners watch. */
    val participants: List<PlayerId> = emptyList(),
    val drawer: PlayerId? = null,
    val options: List<DoodleOption> = emptyList(),
    val wordId: String? = null,
    val word: String = "",
    val difficulty: Int = 0,
    /** The whole draw time, fixed when the draw starts (the lobby timer setting scales it). */
    val drawMs: Long = 0,
    val stage: Int = 0,
    /** Character positions of the letters shown as hints. */
    val revealed: List<Int> = emptyList(),
    /** Guesser ids in the order they got it. */
    val correct: List<String> = emptyList(),
    val points: Map<String, Int> = emptyMap(),
    /** guesser id → their latest wrong guess. */
    val last: Map<String, String> = emptyMap(),
    /** Guessers whose latest wrong guess was near. */
    val close: Set<String> = emptySet(),
    val misses: List<DoodleMiss> = emptyList(),
    val missTotal: Int = 0,
    /** player id → points this turn so far (guesses, then the drawer's pay at the reveal). */
    val deltas: Map<String, Int> = emptyMap(),
    val done: List<DoodleDone> = emptyList(),
)

class Doodle(pack: DoodlePack = DoodlePack.core()) : GameModule<DoodleState>, InkAware<DoodleState> {
    override val info = GameInfo(
        id = "doodle",
        title = "Doodle Dash",
        tagline = "Pictionary-style: draw it, guess it",
        minPlayers = 3,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Draw it", "One player at a time gets a word and draws it on their phone. The TV shows every stroke. No letters, no numbers, no talking."),
            TutorialCard("Guess fast", "Everyone else types guesses on their phones. Wrong guesses float across the TV. The quicker you get it, the more you score."),
            TutorialCard("Everyone draws", "Everybody takes a turn drawing. The drawer scores when others guess. Hints show up as the clock runs down."),
        ),
        lateJoin = LateJoin.NEXT_ROUND,
    )
    override val stateSerializer = DoodleState.serializer()

    private val words = pack.words()
    private val byId = words.associateBy { it.id }

    override fun start(ctx: GameContext): Step<DoodleState> {
        val turns = (ctx.settings["rounds"] ?: DEFAULT_TURNS).coerceIn(3, 8)
        return newTurn(DoodleState(PODIUM, 0, turns), 1, ctx)
    }

    private fun newTurn(prev: DoodleState, turn: Int, ctx: GameContext): Step<DoodleState> {
        val fresh = ctx.fresh(words) { it.id }
        val here = ctx.players.filter { it.connected }.ifEmpty { ctx.players }.map { it.id }
        if (fresh.isEmpty() || here.isEmpty()) return Step(prev.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        val drawer = DoodleRules.pickDrawer(here, prev.turns, ctx.random)
        val s = DoodleState(
            phase = PICK, turn = turn, totalTurns = prev.totalTurns,
            turns = prev.turns + (drawer.v to ((prev.turns[drawer.v] ?: 0) + 1)),
            participants = here, drawer = drawer, options = offer(fresh, ctx.random),
            missTotal = prev.missTotal, done = prev.done,
        )
        return Step(s, listOf(Effect.Phase(ctx.timer(PICK_MS))))
    }

    /** One word per difficulty; a pack that ran short of one still offers three words when it can. */
    private fun offer(fresh: List<DoodleWord>, random: Random): List<DoodleOption> {
        val chosen = (1..3).mapNotNull { d -> fresh.filter { it.difficulty == d }.randomOrNull(random) }.toMutableList()
        for (w in fresh.filter { w -> chosen.none { it.id == w.id } }.shuffled(random)) {
            if (chosen.size >= 3) break
            chosen += w
        }
        return chosen.sortedBy { it.difficulty }.map { DoodleOption(it.id, it.word, it.difficulty) }
    }

    override fun onAction(s: DoodleState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<DoodleState> {
        val kind = payload["kind"]?.jsonPrimitive?.content
        if (who !in s.participants) throw Reject("NEXT_ROUND")
        return when {
            s.phase == PICK && kind == "pick" -> {
                if (who != s.drawer) throw Reject("NOT_NOW")
                val id = payload["option"]?.jsonPrimitive?.content
                startDraw(s, s.options.firstOrNull { it.id == id } ?: throw Reject("BAD_OPTION"), ctx)
            }
            s.phase == DRAW && kind == "guess" -> guess(s, who, payload, ctx)
            else -> throw Reject("NOT_NOW")
        }
    }

    private fun startDraw(s: DoodleState, o: DoodleOption, ctx: GameContext): Step<DoodleState> {
        val total = ctx.timer(DRAW_MS)
        return Step(
            s.copy(phase = DRAW, wordId = o.id, word = o.word, difficulty = o.difficulty, drawMs = total, stage = 0),
            listOf(Effect.UseContent(o.id), Effect.Phase(DoodleRules.stageMs(total, 0))),
        )
    }

    private fun guess(s: DoodleState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<DoodleState> {
        if (who == s.drawer || who.v in s.correct) throw Reject("NOT_GUESSING")
        val text = cleanText(payload["text"]?.jsonPrimitive?.content ?: "", MAX_GUESS) ?: throw Reject("BAD_TEXT")
        if (!AnswerMatch.accepts(text, s.word)) {
            return Step(
                s.copy(
                    last = s.last + (who.v to text),
                    close = if (DoodleRules.nearMiss(text, s.word)) s.close + who.v else s.close - who.v,
                    misses = (s.misses + DoodleMiss(who.v, text)).takeLast(MAX_MISSES),
                    missTotal = s.missTotal + 1,
                ),
            )
        }
        val left = ((ctx.remainingMs ?: 0L) + DoodleRules.tailMs(s.drawMs, s.stage)).toDouble() / s.drawMs
        val pts = DoodleRules.guesserPoints(s.difficulty, left, s.correct.size, multiplier(s))
        return Step(
            s.copy(
                correct = s.correct + who.v,
                points = s.points + (who.v to pts),
                close = s.close - who.v,
                deltas = s.deltas + (who.v to ((s.deltas[who.v] ?: 0) + pts)),
            ),
            listOf(Effect.Award(who, pts, "got “${s.word}”")),
        )
    }

    override fun onDeadline(s: DoodleState, ctx: GameContext): Step<DoodleState> = when (s.phase) {
        PICK -> startDraw(s, s.options.firstOrNull { it.difficulty == 2 } ?: s.options[s.options.size / 2], ctx)
        DRAW -> {
            val pending = guessers(s).any { it.v !in s.correct && ctx.isConnected(it) }
            if (!pending || s.stage >= DoodleRules.STAGES - 1) endDraw(s, ctx)
            else {
                val next = s.stage + 1
                Step(
                    s.copy(stage = next, revealed = DoodleRules.nextHint(s.word, s.revealed, ctx.random)),
                    listOf(Effect.Deadline(DoodleRules.stageMs(s.drawMs, next))),
                )
            }
        }
        REVEAL -> Step(s.copy(phase = SCORES), listOf(Effect.Phase(SCORES_MS)))
        SCORES ->
            if (s.turn < s.totalTurns) newTurn(s, s.turn + 1, ctx)
            else Step(s.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        else -> Step(s, listOf(Effect.Finish))
    }

    /** The draw ends (everyone has it, or the clock ran out): the drawer is paid and the word is revealed. */
    private fun endDraw(s: DoodleState, ctx: GameContext): Step<DoodleState> {
        val drawer = requireNotNull(s.drawer)
        val guessers = guessers(s)
        val pts = DoodleRules.drawerPoints(s.difficulty, s.correct.size, guessers.size, multiplier(s))
        val effects = mutableListOf<Effect>()
        var deltas = s.deltas
        if (pts > 0) {
            deltas = deltas + (drawer.v to pts)
            effects += Effect.Award(drawer, pts, "drew “${s.word}”")
        }
        if (guessers.isNotEmpty() && s.correct.size == guessers.size) {
            ctx.player(drawer)?.let { effects += Effect.Highlight("${it.name} drew “${s.word}” and everyone got it") }
        }
        val done = s.done + DoodleDone(s.turn, s.word, drawer.v, s.correct.firstOrNull())
        return Step(s.copy(phase = REVEAL, deltas = deltas, done = done), effects + Effect.Phase(REVEAL_MS))
    }

    override fun waitingOn(s: DoodleState): Set<PlayerId>? = when (s.phase) {
        PICK -> s.drawer?.let { setOf(it) } ?: emptySet()
        DRAW -> guessers(s).filter { it.v !in s.correct }.toSet()
        else -> null
    }

    override fun inkTurn(s: DoodleState, who: PlayerId): Int? = if (s.phase == DRAW && who == s.drawer) s.turn else null

    override fun restorable(s: DoodleState) = s.wordId == null || s.wordId in byId

    private fun guessers(s: DoodleState) = s.participants.filter { it != s.drawer }
    private fun multiplier(s: DoodleState) = if (s.turn == s.totalTurns) 2 else 1
    private fun tail(s: DoodleState) = if (s.phase == DRAW) DoodleRules.tailMs(s.drawMs, s.stage) else 0L

    // ---- views ----------------------------------------------------------------------------------

    override fun tvView(s: DoodleState, ctx: GameContext): DoodleTv {
        val name = { id: PlayerId? -> id?.let { ctx.player(it)?.name } ?: "?" }
        val shown = s.phase == REVEAL || s.phase == SCORES
        val inTurn = s.phase in TURN_PHASES
        return DoodleTv(
            phase = s.phase,
            turn = s.turn,
            totalTurns = s.totalTurns,
            finalTurn = s.turn == s.totalTurns,
            drawer = if (inTurn) s.drawer else null,
            drawerName = if (inTurn) name(s.drawer) else "",
            difficulty = if (s.phase == PICK || !inTurn) 0 else s.difficulty,
            blanks = if (s.phase == DRAW) DoodleRules.blanks(s.word, s.revealed) else "",
            guessed = if (inTurn && s.phase != PICK) s.correct.size else 0,
            expected = if (inTurn) guessers(s).size else 0,
            drawMs = if (s.phase == DRAW) s.drawMs else 0,
            tailMs = tail(s),
            solvers = if (inTurn && s.phase != PICK) {
                s.correct.map { DoodleSolver(PlayerId(it), name(PlayerId(it)), if (shown) s.points[it] else null) }
            } else emptyList(),
            wrong = if (s.phase == DRAW) s.misses.map { DoodleMissTv(PlayerId(it.id), name(PlayerId(it.id)), it.text) } else emptyList(),
            missTotal = s.missTotal,
            word = if (shown) s.word else null,
            drinks = if (shown) drinks(s, ctx) else emptyList(),
            deltas = if (s.phase == SCORES) {
                s.deltas.map { (id, pts) -> DoodleDelta(PlayerId(id), name(PlayerId(id)), pts) }.sortedByDescending { it.points }
            } else emptyList(),
            gallery = if (s.phase == PODIUM) {
                s.done.map { d -> DoodleShot(d.turn, d.word, PlayerId(d.drawer), name(PlayerId(d.drawer)), d.first?.let(::PlayerId), d.first?.let { name(PlayerId(it)) }) }
            } else emptyList(),
        )
    }

    private fun drinks(s: DoodleState, ctx: GameContext): List<DoodleDrink> {
        if ((ctx.settings["drinks"] ?: 1) == 0) return emptyList()
        val drawer = s.drawer?.let { ctx.player(it) }
        if (s.correct.isEmpty()) {
            return listOfNotNull(drawer?.let { DoodleDrink(it.id, it.name, 2, "Nobody got it! Drink 2 sips${ofWater(it.water)}") })
        }
        return guessers(s).filter { it.v !in s.correct }.mapNotNull { id ->
            ctx.player(id)?.let { DoodleDrink(it.id, it.name, 1, "Missed it. Drink 1 sip${ofWater(it.water)}") }
        }
    }

    override fun playerView(s: DoodleState, who: PlayerId, ctx: GameContext): Screen {
        if (s.phase == PODIUM) return Screen.Scores("Final scores", rows(ctx))
        if (who !in s.participants) return Screen.Waiting("You're in next turn", "Watch the TV and get ready")
        val drawerName = s.drawer?.let { ctx.player(it)?.name } ?: "?"
        val guessed = s.correct.size
        val expected = guessers(s).size
        return when (s.phase) {
            PICK ->
                if (who == s.drawer) Screen.ChoiceList("Pick a word to draw", s.options.map { Choice(it.id, it.word, detail = LEVELS[it.difficulty]) }, null, "pick")
                else Screen.Waiting("$drawerName is picking a word", "Get your guessing fingers ready")
            DRAW ->
                if (who == s.drawer) Screen.Draw(s.word, s.difficulty, guessed, expected, tail(s), "Draw it! No letters or numbers.")
                else Screen.Guess(
                    drawer = drawerName, blanks = DoodleRules.blanks(s.word, s.revealed), kind = "guess",
                    solved = who.v in s.correct, points = s.points[who.v], close = who.v in s.close, last = s.last[who.v],
                    guessed = guessed, expected = expected, tailMs = tail(s),
                )
            REVEAL -> revealScreen(s, who, ctx)
            else -> Screen.Scores("Turn ${s.turn} scores", rows(ctx))
        }
    }

    private fun revealScreen(s: DoodleState, who: PlayerId, ctx: GameContext): Screen {
        val gained = s.deltas[who.v] ?: 0
        val title = "It was ${s.word.uppercase()}"
        val drink = drinks(s, ctx).firstOrNull { it.id == who }?.text
        val detail = when {
            drink != null -> drink
            gained > 0 -> "+$gained"
            else -> "Eyes on the TV"
        }
        return Screen.Waiting(title, detail, tone = if (gained > 0) "win" else if (drink != null) "lose" else "neutral")
    }

    private fun rows(ctx: GameContext) =
        ctx.players.map { ScoreRow(it.id, it.name, it.avatar, ctx.scores[it.id] ?: 0) }.sortedByDescending { it.score }

    companion object {
        const val PICK = "pick"
        const val DRAW = "draw"
        const val REVEAL = "reveal"
        const val SCORES = "scores"
        const val PODIUM = "podium"
        private val TURN_PHASES = setOf(PICK, DRAW, REVEAL, SCORES)
        private val LEVELS = mapOf(1 to "Easy", 2 to "Medium", 3 to "Hard")
        const val DEFAULT_TURNS = 5
        const val MAX_GUESS = 40
        const val MAX_MISSES = 8
        const val PICK_MS = 12_000L
        const val DRAW_MS = 75_000L
        const val REVEAL_MS = 7_000L
        const val SCORES_MS = 6_000L
        const val PODIUM_MS = 24_000L
    }
}
