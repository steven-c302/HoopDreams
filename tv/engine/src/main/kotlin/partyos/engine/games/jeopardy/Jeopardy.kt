package partyos.engine.games.jeopardy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.BoardCell
import partyos.engine.Effect
import partyos.engine.GameContext
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.JeopardyCellTv
import partyos.engine.JeopardyDelta
import partyos.engine.JeopardyDrink
import partyos.engine.JeopardyFinalStep
import partyos.engine.JeopardyFinalTv
import partyos.engine.JeopardyTv
import partyos.engine.LateJoin
import partyos.engine.PlayerId
import partyos.engine.Reject
import partyos.engine.ScoreRow
import partyos.engine.Screen
import partyos.engine.Step
import partyos.engine.TutorialCard
import partyos.engine.games.trivia.AnswerMatch
import partyos.engine.ofWater
import kotlin.random.Random

@Serializable
data class JeopardyState(
    val phase: String,
    /** 1 or 2 for a board, 3 for Final Jeopardy. */
    val round: Int,
    /** 1 = Short, 2 = Full. */
    val boards: Int,
    /** Category ids of the board on screen, in column order. */
    val categories: List<String>,
    /** Board 2's category ids, until it is played. */
    val nextCategories: List<String>,
    val finalId: String,
    /** Clue ids hiding a Daily Double on the current board. */
    val dailyDoubles: Set<String> = emptySet(),
    val used: Set<String> = emptySet(),
    /** Player id holding the board. */
    val controller: String? = null,
    /** Clue id in play. */
    val active: String? = null,
    val dailyDouble: Boolean = false,
    val wager: Int = 0,
    /** Who may ring in on the clue in play: everyone in the room when it was opened. */
    val eligible: List<PlayerId> = emptyList(),
    /** player id → engine time until which they are locked out for ringing in early. */
    val lockedUntil: Map<String, Long> = emptyMap(),
    /** Player id who has the floor (rang in first, or the Daily Double picker). */
    val floor: String? = null,
    val tried: Set<String> = emptySet(),
    /** How the clue in play ended, for the reveal. */
    val right: Boolean? = null,
    /** player id → points won or lost on the clue in play. */
    val deltas: Map<String, Int> = emptyMap(),
    /** player id → sips to drink for the clue in play (or Final so far). */
    val sips: Map<String, Int> = emptyMap(),
    val finalPlayers: List<PlayerId> = emptyList(),
    /** Players with a score above zero when Final started: the only ones who bet. */
    val finalWagerers: List<PlayerId> = emptyList(),
    val finalWagers: Map<String, Int> = emptyMap(),
    val finalAnswers: Map<String, String> = emptyMap(),
    /** Player ids from the lowest score up. */
    val finalOrder: List<String> = emptyList(),
    val finalStep: Int = 0,
    val finalRight: Set<String> = emptySet(),
    /** player id → points won or lost in Final so far. */
    val finalResults: Map<String, Int> = emptyMap(),
)

class Jeopardy(private val pack: JeopardyPack = JeopardyPack.core()) : GameModule<JeopardyState> {
    override val info = GameInfo(
        id = "jeopardy",
        title = "Answer & Question",
        tagline = "Ring in. Risk it. Answer as a question.",
        minPlayers = 2,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Ring in", "When your phone lights up, hit BUZZ first. Too early and you're locked out for a second."),
            TutorialCard("Answer as a question", "\"What is guacamole?\" or just \"guacamole\": both count. Right wins the clue's value; wrong loses it."),
            TutorialCard("Wager big", "Daily Doubles hide on the board: pick one and bet your own score. Final Jeopardy lets everyone bet it all."),
        ),
        lateJoin = LateJoin.ANYTIME,
    )
    override val stateSerializer = JeopardyState.serializer()

    private val categoryById = pack.categories.associateBy { it.id }
    private val finalById = pack.finals.associateBy { it.id }
    private val clueById = pack.categories.flatMap { it.clues }.associateBy { it.id }
    private val categoryNameOfClue = pack.categories.flatMap { c -> c.clues.map { it.id to c.name } }.toMap()

    // ---- setup ----------------------------------------------------------------------------

    override fun start(ctx: GameContext): Step<JeopardyState> {
        val boards = if ((ctx.settings["show"] ?: 0) == 1) 2 else 1
        val need = JeopardyRules.CATEGORIES_PER_BOARD * boards
        val fresh = ctx.fresh(pack.categories) { it.id }
        val ordered = fresh.shuffled(ctx.random) + (pack.categories - fresh.toSet()).shuffled(ctx.random)
        val picked = ordered.take(need).map { it.id }
        val final = ctx.fresh(pack.finals) { it.id }.ifEmpty { pack.finals }.random(ctx.random)
        val first = picked.take(JeopardyRules.CATEGORIES_PER_BOARD)
        val players = ctx.players.map { it.id.v }
        val s = JeopardyState(
            phase = INTRO, round = 1, boards = boards,
            categories = first, nextCategories = picked.drop(JeopardyRules.CATEGORIES_PER_BOARD), finalId = final.id,
            dailyDoubles = dealDoubles(1, first, ctx.random),
            controller = JeopardyRules.firstPicker(1, players, emptyMap(), ctx.captain?.v),
        )
        val used: List<Effect> = picked.map { Effect.UseContent(it) } + Effect.UseContent(final.id)
        return Step(s, used + Effect.Phase(INTRO_MS))
    }

    private fun dealDoubles(round: Int, categoryIds: List<String>, random: Random): Set<String> =
        JeopardyRules.dailyDoubles(categoryIds.size, JeopardyRules.dailyDoubleCount(round), random)
            .map { (col, row) -> categoryById.getValue(categoryIds[col]).clues[row].id }.toSet()

    private fun cells(s: JeopardyState): List<Cell> = s.categories.flatMapIndexed { col, id ->
        categoryById.getValue(id).clues.mapIndexed { row, clue -> Cell(clue.id, col, row, JeopardyRules.valueOf(s.round, row)) }
    }

    private fun cellOf(s: JeopardyState, id: String): Cell = cells(s).first { it.id == id }

    // ---- the board ------------------------------------------------------------------------

    private fun toPick(s: JeopardyState, ctx: GameContext): Step<JeopardyState> =
        Step(s.copy(phase = PICK, active = null, floor = null, dailyDouble = false), listOf(Effect.Phase(ctx.timer(PICK_MS))))

    private fun pick(s: JeopardyState, cellId: String, ctx: GameContext): Step<JeopardyState> {
        val cell = cells(s).firstOrNull { it.id == cellId } ?: throw Reject("BAD_CELL")
        if (cell.id in s.used) throw Reject("ALREADY_USED")
        return openClue(s, cell, ctx)
    }

    private fun openClue(s: JeopardyState, cell: Cell, ctx: GameContext): Step<JeopardyState> {
        val dd = cell.id in s.dailyDoubles
        val base = s.copy(
            active = cell.id, dailyDouble = dd, wager = 0, eligible = ctx.players.map { it.id },
            lockedUntil = emptyMap(), floor = null, tried = emptySet(), right = null, deltas = emptyMap(), sips = emptyMap(),
        )
        return if (dd) Step(base.copy(phase = WAGER), listOf(Effect.Phase(ctx.timer(DD_WAGER_MS))))
        else Step(base.copy(phase = CLUE), listOf(Effect.Phase(JeopardyRules.readMs(clueById.getValue(cell.id).clue))))
    }

    // ---- actions and deadlines ------------------------------------------------------------

    override fun onHost(s: JeopardyState, action: String, ctx: GameContext): Step<JeopardyState> {
        if (s.phase != PICK || !action.startsWith("pick:")) throw Reject("NOT_NOW")
        return pick(s, action.removePrefix("pick:"), ctx)
    }

    override fun onAction(s: JeopardyState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<JeopardyState> {
        val kind = payload["kind"]?.jsonPrimitive?.content
        return when {
            s.phase == PICK && kind == "pick" -> {
                if (who.v != s.controller && who != ctx.captain) throw Reject("NOT_YOUR_PICK")
                pick(s, payload["cell"]?.jsonPrimitive?.content ?: "", ctx)
            }
            s.phase == CLUE && kind == "buzz" -> {
                // Ringing in before the clue has been read locks you out for a second; a Daily Double has no ringing in.
                if (s.dailyDouble || who !in s.eligible) throw Reject("NOT_NOW")
                Step(s.copy(lockedUntil = s.lockedUntil + (who.v to ctx.now + JeopardyRules.LOCKOUT_MS)))
            }
            s.phase == BUZZ && kind == "buzz" -> {
                if (who !in s.eligible || who.v in s.tried) throw Reject("NOT_NOW")
                if ((s.lockedUntil[who.v] ?: 0L) > ctx.now) throw Reject("LOCKED_OUT")
                // The engine handles one action at a time, so the first buzz to arrive is the winner.
                Step(s.copy(phase = ANSWER, floor = who.v), listOf(Effect.Phase(ctx.timer(ANSWER_MS))))
            }
            s.phase == ANSWER && kind == "answer" -> {
                if (who.v != s.floor) throw Reject("NOT_NOW")
                val text = cleanText(payload["text"]?.jsonPrimitive?.content ?: "") ?: throw Reject("BAD_TEXT")
                judge(s, text, ctx)
            }
            s.phase == WAGER && kind == "wager" -> {
                if (who.v != s.controller) throw Reject("NOT_NOW")
                val value = payload["value"]?.jsonPrimitive?.intOrNull ?: throw Reject("BAD_WAGER")
                if (value !in JeopardyRules.wagerRange(ctx.scores[who] ?: 0, s.round)) throw Reject("BAD_WAGER")
                placeWager(s, value)
            }
            s.phase == FINAL_WAGER && kind == "wager" -> {
                if (who !in s.finalWagerers) throw Reject("NOT_NOW")
                val value = payload["value"]?.jsonPrimitive?.intOrNull ?: throw Reject("BAD_WAGER")
                if (value !in JeopardyRules.finalWagerRange(ctx.scores[who] ?: 0)) throw Reject("BAD_WAGER")
                Step(s.copy(finalWagers = s.finalWagers + (who.v to value)))
            }
            s.phase == FINAL_ANSWER && kind == "answer" -> {
                if (who !in s.finalPlayers) throw Reject("NOT_NOW")
                val text = cleanText(payload["text"]?.jsonPrimitive?.content ?: "") ?: throw Reject("BAD_TEXT")
                Step(s.copy(finalAnswers = s.finalAnswers + (who.v to text)))
            }
            else -> throw Reject("NOT_NOW")
        }
    }

    override fun onDeadline(s: JeopardyState, ctx: GameContext): Step<JeopardyState> = when (s.phase) {
        INTRO -> toPick(s, ctx)
        PICK -> openClue(s, JeopardyRules.autoPick(cells(s).filter { it.id !in s.used }, ctx.random), ctx)
        WAGER -> placeWager(s, JeopardyRules.MIN_WAGER) // no wager in time counts as the minimum
        CLUE ->
            if (s.dailyDouble) Step(s.copy(phase = ANSWER, floor = s.controller), listOf(Effect.Phase(ctx.timer(DD_ANSWER_MS))))
            else Step(s.copy(phase = BUZZ), listOf(Effect.Phase(ctx.timer(BUZZ_MS))))
        BUZZ -> reveal(s.copy(right = false), emptyList()) // nobody rang in
        ANSWER -> judge(s, null, ctx) // time ran out with nothing typed
        REVEAL -> afterReveal(s, ctx)
        BREAK -> startSecondBoard(s, ctx)
        FINAL_CATEGORY -> Step(s.copy(phase = FINAL_WAGER), listOf(Effect.Phase(ctx.timer(FINAL_WAGER_MS))))
        FINAL_WAGER -> Step(s.copy(phase = FINAL_ANSWER), listOf(Effect.Phase(ctx.timer(FINAL_ANSWER_MS))))
        FINAL_ANSWER -> beginFinalReveal(s, ctx)
        FINAL_REVEAL -> nextFinalStep(s, ctx)
        else -> Step(s, listOf(Effect.Finish))
    }

    override fun waitingOn(s: JeopardyState): Set<PlayerId>? = when (s.phase) {
        PICK -> s.controller?.let { setOf(PlayerId(it)) }
        WAGER -> s.controller?.let { setOf(PlayerId(it)) }
        BUZZ -> s.eligible.filter { it.v !in s.tried }.toSet()
        ANSWER -> s.floor?.let { setOf(PlayerId(it)) }
        FINAL_WAGER -> s.finalWagerers.filter { it.v !in s.finalWagers }.toSet()
        FINAL_ANSWER -> s.finalPlayers.filter { it.v !in s.finalAnswers }.toSet()
        else -> null
    }

    override fun restorable(s: JeopardyState) = s.categories.all { it in categoryById } && s.finalId in finalById

    private fun placeWager(s: JeopardyState, value: Int): Step<JeopardyState> =
        Step(s.copy(phase = CLUE, wager = value), listOf(Effect.Phase(JeopardyRules.readMs(clueById.getValue(requireNotNull(s.active)).clue))))

    /** Marks the floor holder's answer (null = they ran out of time). Right takes the board; wrong loses the stake. */
    private fun judge(s: JeopardyState, text: String?, ctx: GameContext): Step<JeopardyState> {
        val active = requireNotNull(s.active)
        val floor = requireNotNull(s.floor)
        val right = matches(text, clueById.getValue(active).answer)
        val stake = if (s.dailyDouble) s.wager else cellOf(s, active).value
        val delta = JeopardyRules.clueDelta(stake, right)
        val deltas = s.deltas + (floor to ((s.deltas[floor] ?: 0) + delta))
        val sips = if (right) s.sips else s.sips + (floor to (if (s.dailyDouble) 2 else 1))
        val award = Effect.Award(PlayerId(floor), delta, if (right) "got it" else "missed")
        if (right) return reveal(s.copy(controller = floor, right = true, deltas = deltas, sips = sips), listOf(award))
        val tried = s.tried + floor
        val next = s.copy(tried = tried, deltas = deltas, sips = sips, floor = null)
        val others = !s.dailyDouble && s.eligible.any { it.v !in tried && ctx.isConnected(it) }
        return if (others) Step(next.copy(phase = BUZZ), listOf(award, Effect.Phase(ctx.timer(REBUZZ_MS))))
        else reveal(next.copy(right = false), listOf(award))
    }

    private fun reveal(s: JeopardyState, effects: List<Effect>): Step<JeopardyState> =
        Step(s.copy(phase = REVEAL, used = s.used + requireNotNull(s.active), lockedUntil = emptyMap()), effects + Effect.Phase(REVEAL_MS))

    /** After the reveal: the next pick, or, once the board is empty, the break, the next board or Final Jeopardy. */
    private fun afterReveal(s: JeopardyState, ctx: GameContext): Step<JeopardyState> {
        val boardDone = cells(s).all { it.id in s.used }
        return when {
            !boardDone -> toPick(s, ctx)
            s.round < s.boards -> Step(s.copy(phase = BREAK, active = null, floor = null, dailyDouble = false), listOf(Effect.Phase(BREAK_MS)))
            else -> enterFinal(s, ctx)
        }
    }

    /** Double Jeopardy: a fresh board, doubled values, two Daily Doubles, and the lowest score picks first. */
    private fun startSecondBoard(s: JeopardyState, ctx: GameContext): Step<JeopardyState> {
        val players = ctx.players.map { it.id.v }
        return Step(
            s.copy(
                phase = INTRO, round = 2, categories = s.nextCategories, nextCategories = emptyList(), used = emptySet(),
                dailyDoubles = dealDoubles(2, s.nextCategories, ctx.random),
                controller = JeopardyRules.firstPicker(2, players, ctx.scores.mapKeys { it.key.v }, ctx.captain?.v),
            ),
            listOf(Effect.Phase(INTRO_MS)),
        )
    }

    /** Final Jeopardy, unless nobody has a score above zero to bet. */
    private fun enterFinal(s: JeopardyState, ctx: GameContext): Step<JeopardyState> {
        val bettors = ctx.players.filter { (ctx.scores[it.id] ?: 0) > 0 }.map { it.id }
        if (bettors.isEmpty()) return podium(s)
        return Step(
            s.copy(phase = FINAL_CATEGORY, round = 3, active = null, floor = null, dailyDouble = false, sips = emptyMap(), finalPlayers = ctx.players.map { it.id }, finalWagerers = bettors),
            listOf(Effect.Phase(FINAL_CAT_MS)),
        )
    }

    private fun podium(s: JeopardyState): Step<JeopardyState> =
        Step(s.copy(phase = PODIUM, active = null, floor = null, dailyDouble = false), listOf(Effect.Phase(PODIUM_MS)))

    private fun beginFinalReveal(s: JeopardyState, ctx: GameContext): Step<JeopardyState> {
        val order = JeopardyRules.finalOrder(s.finalPlayers.map { it.v }, ctx.scores.mapKeys { it.key.v })
        return revealFinalStep(s.copy(phase = FINAL_REVEAL, finalOrder = order, finalStep = 0), ctx)
    }

    private fun nextFinalStep(s: JeopardyState, ctx: GameContext): Step<JeopardyState> =
        if (s.finalStep + 1 < s.finalOrder.size) revealFinalStep(s.copy(finalStep = s.finalStep + 1), ctx) else podium(s)

    /** Shows one player's Final answer: it is marked and their wager is won or lost. */
    private fun revealFinalStep(s: JeopardyState, ctx: GameContext): Step<JeopardyState> {
        val id = s.finalOrder[s.finalStep]
        val wager = s.finalWagers[id] ?: 0
        val right = matches(s.finalAnswers[id], finalById.getValue(s.finalId).answer)
        val delta = if (wager == 0) 0 else JeopardyRules.clueDelta(wager, right)
        val effects = mutableListOf<Effect>()
        if (delta != 0) effects += Effect.Award(PlayerId(id), delta, if (right) "won Final Jeopardy" else "lost Final Jeopardy")
        return Step(
            s.copy(
                finalResults = s.finalResults + (id to delta),
                finalRight = if (right) s.finalRight + id else s.finalRight,
                sips = if (!right && wager > 0) s.sips + (id to 2) else s.sips,
            ),
            effects + Effect.Phase(FINAL_STEP_MS),
        )
    }

    // ---- views ----------------------------------------------------------------------------

    private fun nameOf(ctx: GameContext, id: String?): String = id?.let { ctx.player(PlayerId(it))?.name } ?: "Someone"

    private fun rows(ctx: GameContext) =
        ctx.players.map { ScoreRow(it.id, it.name, it.avatar, ctx.scores[it.id] ?: 0) }.sortedByDescending { it.score }

    private fun drinks(s: JeopardyState, ctx: GameContext): List<JeopardyDrink> {
        if ((ctx.settings["drinks"] ?: 1) == 0) return emptyList()
        return s.sips.mapNotNull { (id, sips) ->
            val p = ctx.player(PlayerId(id)) ?: return@mapNotNull null
            JeopardyDrink(p.id, p.name, sips, "Drink $sips ${if (sips == 1) "sip" else "sips"}${ofWater(p.water)}")
        }
    }

    override fun tvView(s: JeopardyState, ctx: GameContext): JeopardyTv {
        val onBoard = s.phase !in FINAL_PHASES && s.phase != PODIUM
        val clue = s.active?.let(clueById::get)
        val reveal = s.phase == REVEAL
        val cell = s.active?.let { cellOf(s, it) }
        return JeopardyTv(
            phase = s.phase,
            round = s.round,
            boards = s.boards,
            categories = if (onBoard) s.categories.map { categoryById.getValue(it).name } else emptyList(),
            cells = if (onBoard) cells(s).map { JeopardyCellTv(it.id, it.col, it.row, it.value, it.id in s.used) } else emptyList(),
            controller = s.controller?.let(::PlayerId),
            category = s.active?.let { categoryNameOfClue[it] },
            value = cell?.value,
            clue = if (s.phase in CLUE_VISIBLE) clue?.clue else null,
            dailyDouble = s.dailyDouble && s.active != null,
            wager = if (s.dailyDouble && s.phase in COMMITTED) s.wager else null,
            buzzOpen = s.phase == BUZZ,
            floor = s.floor?.let(::PlayerId),
            locked = s.lockedUntil.filterValues { it > ctx.now }.keys.map(::PlayerId),
            tried = s.tried.map(::PlayerId),
            answer = if (reveal) clue?.answer else null,
            right = if (reveal) s.right else null,
            deltas = if (reveal) s.deltas.map { (id, pts) -> JeopardyDelta(PlayerId(id), nameOf(ctx, id), pts) }.sortedByDescending { it.points } else emptyList(),
            drinks = if (reveal || s.phase == FINAL_REVEAL) drinks(s, ctx) else emptyList(),
            final = if (s.phase in FINAL_PHASES) finalTv(s, ctx) else null,
        )
    }

    private fun finalTv(s: JeopardyState, ctx: GameContext): JeopardyFinalTv {
        val f = finalById.getValue(s.finalId)
        val clueShown = s.phase == FINAL_ANSWER || s.phase == FINAL_REVEAL
        val positive = s.finalWagerers.size
        return JeopardyFinalTv(
            category = f.category,
            clue = if (clueShown) f.clue else null,
            answer = if (s.phase == FINAL_REVEAL) f.answer else null,
            wagers = if (s.phase == FINAL_ANSWER || s.phase == FINAL_WAGER) s.finalWagers.size else 0,
            expected = if (s.phase == FINAL_ANSWER) s.finalPlayers.size else positive,
            steps = if (s.phase == FINAL_REVEAL) {
                s.finalOrder.take(s.finalStep + 1).map { id ->
                    JeopardyFinalStep(
                        PlayerId(id), nameOf(ctx, id), s.finalAnswers[id], s.finalWagers[id] ?: 0,
                        id in s.finalRight, s.finalResults[id] ?: 0, ctx.scores[PlayerId(id)] ?: 0,
                    )
                }
            } else emptyList(),
        )
    }

    override fun playerView(s: JeopardyState, who: PlayerId, ctx: GameContext): Screen {
        val me = who.v
        val controllerName = nameOf(ctx, s.controller)
        return when (s.phase) {
            PODIUM -> Screen.Scores("Final scores", rows(ctx))
            INTRO -> Screen.Waiting("Here we go", "Watch the categories come up on the TV")
            BREAK -> Screen.Waiting("Double Jeopardy!", "Every clue is worth double")
            PICK -> boardScreen(s, who, ctx)
            WAGER ->
                if (me == s.controller) Screen.NumberEntry("Daily Double! How much do you wager?", "$", null, "wager")
                else Screen.Waiting("Daily Double!", "$controllerName is placing a wager")
            CLUE, BUZZ, ANSWER -> clueScreen(s, who, ctx)
            REVEAL -> revealScreen(s, who, ctx)
            FINAL_CATEGORY -> Screen.Waiting("Final Jeopardy", finalById.getValue(s.finalId).category)
            FINAL_WAGER ->
                if (who in s.finalWagerers) Screen.NumberEntry("Final Jeopardy: how much do you wager?", "$", s.finalWagers[me]?.toDouble(), "wager")
                else Screen.Waiting("Sit this bet out", "You need a positive score to wager. You can still answer.")
            FINAL_ANSWER ->
                if (who in s.finalPlayers) Screen.TextEntry(finalById.getValue(s.finalId).clue, MAX_ANSWER, s.finalAnswers[me], "answer", "Answer as a question, or just say it")
                else Screen.Waiting("Final Jeopardy", "You'll play the next show")
            FINAL_REVEAL -> Screen.Waiting("Eyes on the TV", "Final answers are coming up, lowest score first")
            else -> Screen.Waiting("Eyes on the TV", null)
        }
    }

    private fun boardScreen(s: JeopardyState, who: PlayerId, ctx: GameContext): Screen {
        val isController = who.v == s.controller
        val isCaptain = ctx.captain == who
        val controllerName = nameOf(ctx, s.controller)
        return Screen.Board(
            prompt = when {
                isController -> "Pick a clue"
                isCaptain -> "Pick for $controllerName"
                else -> "$controllerName is picking"
            },
            categories = s.categories.map { categoryById.getValue(it).name },
            cells = cells(s).map { BoardCell(it.id, it.col, it.row, it.value, it.id in s.used) },
            canPick = isController || isCaptain,
            pickFor = if (!isController && isCaptain) controllerName else null,
            note = if (isController || isCaptain) null else "Watch the TV",
        )
    }

    private fun clueScreen(s: JeopardyState, who: PlayerId, ctx: GameContext): Screen {
        val me = who.v
        val active = requireNotNull(s.active)
        val clue = clueById.getValue(active)
        val category = categoryNameOfClue.getValue(active)
        val value = cellOf(s, active).value
        if (s.dailyDouble) {
            return when {
                s.phase == ANSWER && me == s.floor -> Screen.TextEntry(clue.clue, MAX_ANSWER, null, "answer", "Daily Double! Answer as a question, or just say it")
                me == s.controller -> Screen.Waiting("Daily Double!", "It's all you. Wager: ${s.wager}")
                else -> Screen.Waiting("Daily Double!", "${nameOf(ctx, s.controller)} is on the clue")
            }
        }
        if (s.phase == ANSWER && me == s.floor) return Screen.TextEntry(clue.clue, MAX_ANSWER, null, "answer", "Answer as a question, or just say it")
        fun buzzer(state: String, detail: String? = null, lockedMs: Int = 0) = Screen.Buzzer(state, category, value, detail, lockedMs, s.phase == BUZZ)
        if (who !in s.eligible) return buzzer("out", "You're in on the next clue")
        val locked = ((s.lockedUntil[me] ?: 0L) - ctx.now).coerceAtLeast(0).toInt()
        return when {
            s.phase == ANSWER -> buzzer("beaten", "${nameOf(ctx, s.floor)} rang in first")
            me in s.tried -> buzzer("tried", "You missed this one")
            locked > 0 -> buzzer("locked", "Too early! Hold on...", locked)
            s.phase == BUZZ -> buzzer("open", "Ring in!")
            else -> buzzer("reading", "Wait for the clue to finish")
        }
    }

    private fun revealScreen(s: JeopardyState, who: PlayerId, ctx: GameContext): Screen {
        val me = who.v
        val delta = s.deltas[me] ?: 0
        val drink = drinks(s, ctx).firstOrNull { it.id == who }?.text
        val answer = clueById.getValue(requireNotNull(s.active)).answer
        return when {
            delta > 0 -> Screen.Waiting("You got it! +$delta", answer, tone = "win")
            delta < 0 -> Screen.Waiting("Ouch! $delta", listOfNotNull(answer, drink).joinToString(" · "), tone = "lose")
            else -> Screen.Waiting("Eyes on the TV", answer)
        }
    }

    private fun cleanText(raw: String): String? {
        val t = raw.filterNot { Character.isISOControl(it) }.trim().replace(Regex("\\s+"), " ")
        return t.takeIf { it.isNotEmpty() && it.length <= MAX_ANSWER }
    }

    /**
     * Players answer in the show's form ("What is guacamole?"), which the shared matcher would read as extra wrong
     * words, so the leading "what is / who's / where are..." is dropped first. Only this game does it: Write It Down
     * and Brain Drain take bare answers.
     */
    private fun asAnswer(raw: String): String = raw.trim().replace(QUESTION_LEAD, "").trimEnd('?', ' ')

    private fun matches(text: String?, answer: String) = text != null && AnswerMatch.accepts(asAnswer(text), answer)

    companion object {
        const val INTRO = "intro"
        const val PICK = "pick"
        const val WAGER = "wager"
        const val CLUE = "clue"
        const val BUZZ = "buzz"
        const val ANSWER = "answer"
        const val REVEAL = "reveal"
        const val BREAK = "break"
        const val FINAL_CATEGORY = "final_category"
        const val FINAL_WAGER = "final_wager"
        const val FINAL_ANSWER = "final_answer"
        const val FINAL_REVEAL = "final_reveal"
        const val PODIUM = "podium"
        private val FINAL_PHASES = setOf(FINAL_CATEGORY, FINAL_WAGER, FINAL_ANSWER, FINAL_REVEAL)
        /** The clue text is on screen from the read onward. */
        private val CLUE_VISIBLE = setOf(CLUE, BUZZ, ANSWER, REVEAL)
        /** A Daily Double's wager is shown once it has been placed. */
        private val COMMITTED = setOf(CLUE, ANSWER, REVEAL)
        private val QUESTION_LEAD = Regex("^(?:what|who|where|when)(?:['’]?s|\\s+(?:is|are|was|were))\\s+", RegexOption.IGNORE_CASE)
        const val MAX_ANSWER = 60
        const val PICK_MS = 20_000L
        const val BUZZ_MS = 10_000L
        const val REBUZZ_MS = 8_000L
        const val ANSWER_MS = 15_000L
        const val DD_WAGER_MS = 20_000L
        const val DD_ANSWER_MS = 30_000L
        const val REVEAL_MS = 5_000L
        const val INTRO_MS = 8_000L
        const val BREAK_MS = 8_000L
        const val FINAL_CAT_MS = 6_000L
        const val FINAL_WAGER_MS = 30_000L
        const val FINAL_ANSWER_MS = 30_000L
        const val FINAL_STEP_MS = 5_000L
        const val PODIUM_MS = 15_000L
    }
}
