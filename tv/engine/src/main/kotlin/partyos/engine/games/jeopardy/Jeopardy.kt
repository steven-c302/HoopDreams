package partyos.engine.games.jeopardy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.Effect
import partyos.engine.GameContext
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.JeopardyCellTv
import partyos.engine.JeopardyDelta
import partyos.engine.JeopardyTv
import partyos.engine.LateJoin
import partyos.engine.PlayerId
import partyos.engine.Reject
import partyos.engine.ScoreRow
import partyos.engine.Screen
import partyos.engine.Step
import partyos.engine.TutorialCard
import partyos.engine.games.trivia.AnswerMatch

@Serializable
data class JeopardyState(
    val phase: String,
    val board: List<JeopardyClue>,
    val used: Set<String> = emptySet(),
    val activeId: String? = null,
    val participants: List<PlayerId> = emptyList(),
    /** player id → typed answer */
    val answers: Map<String, String> = emptyMap(),
    val correctIds: Set<String> = emptySet(),
    val deltas: Map<String, Int> = emptyMap(),
)

/**
 * ANSWER & QUESTION: a fixed 5x5 board, one category per column. The host (TV keyboard or the captain's show
 * controls) picks a clue; everyone still in the room answers on their phone; anyone whose answer is close enough
 * wins that clue's dollar value. Individual scoring, like Bluff Battle — no teams to wrangle.
 */
class Jeopardy(private val pack: JeopardyPack = JeopardyPack.core()) : GameModule<JeopardyState> {
    override val info = GameInfo(
        id = "jeopardy",
        title = "Answer & Question",
        tagline = "Five categories, five values",
        minPlayers = 2,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Pick a clue", "The host picks a category and dollar value off the board."),
            TutorialCard("Answer fast", "Type your answer on your phone before time's up. Close spelling counts."),
            TutorialCard("Score", "Everyone who gets it right wins that clue's dollar value — no need to be first."),
        ),
        lateJoin = LateJoin.NEXT_ROUND,
    )
    override val stateSerializer = JeopardyState.serializer()

    private val byId = pack.categories.flatMap { it.clues }.associateBy { it.id }
    private val categoryOf = pack.categories.flatMap { cat -> cat.clues.map { it.id to cat.name } }.toMap()

    override fun start(ctx: GameContext): Step<JeopardyState> {
        val board = pack.categories.flatMap { it.clues }
        return Step(JeopardyState(SELECT, board), listOf(Effect.Phase(null)))
    }

    override fun onHost(s: JeopardyState, action: String, ctx: GameContext): Step<JeopardyState> {
        if (s.phase != SELECT || !action.startsWith("pick:")) throw Reject("NOT_NOW")
        val cellId = action.removePrefix("pick:")
        val clue = byId[cellId] ?: throw Reject("BAD_CELL")
        if (cellId in s.used) throw Reject("ALREADY_USED")
        val participants = ctx.players.map { it.id }
        if (participants.isEmpty()) throw Reject("NO_PLAYERS")
        return Step(
            s.copy(phase = ANSWER, activeId = cellId, participants = participants, answers = emptyMap(), correctIds = emptySet()),
            listOf(Effect.Phase(ctx.timer(ANSWER_MS))),
        )
    }

    override fun onAction(s: JeopardyState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<JeopardyState> {
        val kind = payload["kind"]?.jsonPrimitive?.content
        if (s.phase != ANSWER || kind != "answer") throw Reject("NOT_NOW")
        if (who !in s.participants) throw Reject("NEXT_ROUND")
        if (who.v in s.answers) throw Reject("LOCKED")
        val text = cleanText(payload["text"]?.jsonPrimitive?.content ?: "") ?: throw Reject("BAD_TEXT")
        return Step(s.copy(answers = s.answers + (who.v to text)))
    }

    override fun onDeadline(s: JeopardyState, ctx: GameContext): Step<JeopardyState> = when (s.phase) {
        ANSWER -> score(s, ctx)
        REVEAL -> if (s.used.size >= s.board.size) Step(s.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        else Step(s.copy(phase = SELECT, activeId = null), listOf(Effect.Phase(null)))
        else -> Step(s, listOf(Effect.Finish))
    }

    private fun score(s: JeopardyState, ctx: GameContext): Step<JeopardyState> {
        val clue = byId.getValue(requireNotNull(s.activeId))
        val correct = s.answers.filterValues { AnswerMatch.accepts(asAnswer(it), clue.answer) }.keys
        val effects = mutableListOf<Effect>()
        val deltas = LinkedHashMap<String, Int>()
        for (pid in correct) {
            deltas[pid] = clue.value
            effects += Effect.Award(PlayerId(pid), clue.value, "got it")
        }
        return Step(
            s.copy(phase = REVEAL, used = s.used + clue.id, correctIds = correct, deltas = deltas),
            effects + Effect.Phase(REVEAL_MS),
        )
    }

    override fun restorable(s: JeopardyState) = s.activeId == null || s.activeId in byId

    override fun waitingOn(s: JeopardyState): Set<PlayerId>? = when (s.phase) {
        ANSWER -> s.participants.filter { it.v !in s.answers }.toSet()
        else -> null
    }

    override fun tvView(s: JeopardyState, ctx: GameContext): JeopardyTv {
        val clue = s.activeId?.let(byId::get)
        val board = s.board.map { JeopardyCellTv(it.id, categoryOf.getValue(it.id), it.value, it.id in s.used) }
        val showReveal = s.phase == REVEAL
        return JeopardyTv(
            phase = s.phase,
            board = board,
            category = clue?.let { categoryOf[it.id] },
            value = clue?.value,
            clue = if (s.phase == ANSWER || showReveal) clue?.clue else null,
            submitted = s.answers.size,
            expected = s.participants.size,
            answer = if (showReveal) clue?.answer else null,
            correct = if (showReveal) s.correctIds.mapNotNull { ctx.player(PlayerId(it))?.name } else emptyList(),
            deltas = if (showReveal) {
                s.deltas.map { (id, pts) -> JeopardyDelta(PlayerId(id), ctx.player(PlayerId(id))?.name ?: "?", pts) }
                    .sortedByDescending { it.points }
            } else {
                emptyList()
            },
        )
    }

    override fun playerView(s: JeopardyState, who: PlayerId, ctx: GameContext): Screen {
        if (s.phase == PODIUM) return Screen.Scores("Final scores", rows(ctx))
        if (s.phase == SELECT) return Screen.Waiting("Eyes on the board", "The host is picking the next clue")
        if (who !in s.participants) return Screen.Waiting("You're in next round", "Watch the TV and get ready")
        val clue = s.activeId?.let(byId::get)
        return when (s.phase) {
            ANSWER -> Screen.TextEntry(
                prompt = clue?.clue ?: "",
                maxLen = MAX_ANSWER,
                value = s.answers[who.v],
                kind = "answer",
                hint = if (who.v in s.answers) "Locked in. Watch the TV." else "Type your answer",
            )
            REVEAL -> Screen.Waiting(
                title = if (who.v in s.correctIds) "You got it!" else "So close",
                detail = clue?.answer,
                tone = if (who.v in s.correctIds) "win" else "neutral",
            )
            else -> Screen.Scores("Scores", rows(ctx))
        }
    }

    private fun rows(ctx: GameContext) =
        ctx.players.map { ScoreRow(it.id, it.name, it.avatar, ctx.scores[it.id] ?: 0) }.sortedByDescending { it.score }

    /**
     * Players answer in the show's form ("What is guacamole?"), which the shared matcher would read as extra wrong
     * words, so the leading "what is / who's / where are..." is dropped first. Only this game does it: Write It Down
     * and Brain Drain take bare answers.
     */
    private fun asAnswer(raw: String): String = raw.trim().replace(QUESTION_LEAD, "").trimEnd('?', ' ')

    private fun cleanText(raw: String): String? {
        val t = raw.filterNot { Character.isISOControl(it) }.trim().replace(Regex("\\s+"), " ")
        return t.takeIf { it.isNotEmpty() && it.length <= MAX_ANSWER }
    }

    companion object {
        const val SELECT = "select"
        const val ANSWER = "answer"
        const val REVEAL = "reveal"
        const val PODIUM = "podium"
        private val QUESTION_LEAD = Regex("^(?:what|who|where|when)(?:['’]?s|\\s+(?:is|are|was|were))\\s+", RegexOption.IGNORE_CASE)
        const val MAX_ANSWER = 60
        const val ANSWER_MS = 30_000L
        const val REVEAL_MS = 6_000L
        const val PODIUM_MS = 15_000L
    }
}
