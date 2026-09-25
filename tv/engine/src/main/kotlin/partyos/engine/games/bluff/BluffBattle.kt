package partyos.engine.games.bluff

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.BluffDelta
import partyos.engine.BluffReveal
import partyos.engine.BluffTv
import partyos.engine.Choice
import partyos.engine.Effect
import partyos.engine.GameContext
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.LateJoin
import partyos.engine.PlayerId
import partyos.engine.Reject
import partyos.engine.ScoreRow
import partyos.engine.Screen
import partyos.engine.Step
import partyos.engine.TutorialCard

@Serializable
data class BluffOption(val id: String, val text: String, val authors: List<PlayerId>, val kind: String)

@Serializable
data class BluffState(
    val phase: String,
    val round: Int,
    val totalRounds: Int,
    val questionId: String?,
    val participants: List<PlayerId> = emptyList(),
    /** author id → fake text */
    val fakes: Map<String, String> = emptyMap(),
    val options: List<BluffOption> = emptyList(),
    /** picker id → option id */
    val picks: Map<String, String> = emptyMap(),
    val deltas: Map<String, Int> = emptyMap(),
)

class BluffBattle(private val pack: BluffPack = BluffPack.core()) : GameModule<BluffState> {
    override val info = GameInfo(
        id = "bluff",
        title = "Bluff Battle",
        tagline = "Fake it till they pick it",
        minPlayers = 3,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Write a fake", "Everyone sees a weird question. Type a believable fake answer."),
            TutorialCard("Spot the truth", "Pick the real answer from everyone's fakes. You can't pick your own."),
            TutorialCard("Score", "+1000 for finding the truth. +500 for every friend your fake fools. Last round counts double."),
        ),
        lateJoin = LateJoin.NEXT_ROUND,
    )
    override val stateSerializer = BluffState.serializer()

    private val byId = pack.items.associateBy { it.id }
    private fun question(s: BluffState) = byId.getValue(requireNotNull(s.questionId))

    override fun start(ctx: GameContext): Step<BluffState> {
        val rounds = (ctx.settings["rounds"] ?: DEFAULT_ROUNDS).coerceIn(3, 8)
        return newRound(BluffState(PODIUM, 0, rounds, null), 1, ctx)
    }

    private fun newRound(prev: BluffState, round: Int, ctx: GameContext): Step<BluffState> {
        val unused = pack.items.filter { it.id !in ctx.usedContent }
        if (unused.isEmpty()) return Step(prev.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        val q = unused.random(ctx.random)
        val s = BluffState(WRITE, round, prev.totalRounds, q.id, ctx.players.map { it.id })
        return Step(s, listOf(Effect.UseContent(q.id), Effect.Phase(WRITE_MS)))
    }

    override fun onAction(s: BluffState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<BluffState> {
        val kind = payload["kind"]?.jsonPrimitive?.content
        if (who !in s.participants) throw Reject("NEXT_ROUND")
        return when {
            s.phase == WRITE && kind == "write" -> {
                val text = cleanText(payload["text"]?.jsonPrimitive?.content ?: "", MAX_FAKE) ?: throw Reject("BAD_TEXT")
                if (normalise(text) in question(s).truths()) throw Reject("TOO_TRUE")
                Step(s.copy(fakes = s.fakes + (who.v to text)))
            }
            s.phase == PICK && kind == "pick" -> {
                val option = s.options.firstOrNull { it.id == payload["option"]?.jsonPrimitive?.content } ?: throw Reject("BAD_OPTION")
                if (who in option.authors) throw Reject("OWN_ANSWER")
                Step(s.copy(picks = s.picks + (who.v to option.id)))
            }
            else -> throw Reject("NOT_NOW")
        }
    }

    override fun onDeadline(s: BluffState, ctx: GameContext): Step<BluffState> = when (s.phase) {
        WRITE -> Step(s.copy(phase = PICK, options = buildOptions(s, ctx)), listOf(Effect.Phase(PICK_MS)))
        PICK -> score(s, ctx)
        REVEAL -> Step(s.copy(phase = SCORES), listOf(Effect.Phase(SCORES_MS)))
        SCORES -> if (s.round < s.totalRounds) newRound(s, s.round + 1, ctx) else Step(s.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        else -> Step(s, listOf(Effect.Finish))
    }

    private fun buildOptions(s: BluffState, ctx: GameContext): List<BluffOption> {
        val q = question(s)
        val order = s.participants
        // Group identical fakes (after normalisation); the earliest author's wording is shown.
        val groups = LinkedHashMap<String, Pair<String, MutableList<PlayerId>>>()
        for (author in order) {
            val text = s.fakes[author.v] ?: continue
            groups.getOrPut(normalise(text)) { text to mutableListOf() }.second += author
        }
        val raw = mutableListOf<Triple<String, List<PlayerId>, String>>()
        groups.values.forEach { (text, authors) -> raw += Triple(text, authors.toList(), FAKE) }
        raw += Triple(q.answer, emptyList(), TRUTH)
        val taken = groups.keys + normalise(q.answer)
        for (d in q.decoys.shuffled(ctx.random)) {
            if (raw.size >= MIN_OPTIONS) break
            if (normalise(d) !in taken) raw += Triple(d, emptyList(), DECOY)
        }
        return raw.shuffled(ctx.random).mapIndexed { i, (text, authors, kind) -> BluffOption("o${i + 1}", text, authors, kind) }
    }

    private fun score(s: BluffState, ctx: GameContext): Step<BluffState> {
        val mult = if (s.round == s.totalRounds) 2 else 1
        val deltas = LinkedHashMap<String, Int>()
        val effects = mutableListOf<Effect>()
        fun award(p: PlayerId, pts: Int, why: String) {
            deltas.merge(p.v, pts, Int::plus)
            effects += Effect.Award(p, pts, why)
        }
        for ((picker, optionId) in s.picks) {
            val option = s.options.first { it.id == optionId }
            if (option.kind == TRUTH) award(PlayerId(picker), TRUTH_POINTS * mult, "found the truth")
            else option.authors.forEach { award(it, FOOL_POINTS * mult, "fooled ${ctx.player(PlayerId(picker))?.name}") }
        }
        for (o in s.options.filter { it.kind == FAKE }) {
            val fooled = s.picks.count { it.value == o.id }
            if (fooled >= 3) effects += Effect.Highlight("${o.authors.names(ctx)} fooled $fooled people with “${o.text}”")
        }
        return Step(s.copy(phase = REVEAL, deltas = deltas), effects + Effect.Phase(REVEAL_STEP_MS * s.options.size + REVEAL_TAIL_MS))
    }

    override fun restorable(s: BluffState) = s.questionId == null || s.questionId in byId

    override fun waitingOn(s: BluffState): Set<PlayerId>? = when (s.phase) {
        WRITE -> s.participants.filter { it.v !in s.fakes }.toSet()
        PICK -> s.participants.filter { it.v !in s.picks }.toSet()
        else -> null
    }

    override fun tvView(s: BluffState, ctx: GameContext): BluffTv {
        val q = s.questionId?.let(byId::get)
        val showReveal = s.phase in setOf(REVEAL, SCORES)
        return BluffTv(
            phase = s.phase,
            round = s.round,
            totalRounds = s.totalRounds,
            finalRound = s.round == s.totalRounds,
            prompt = q?.prompt ?: "",
            submitted = if (s.phase == PICK) s.picks.size else s.fakes.size,
            expected = s.participants.size,
            options = if (s.phase == PICK) s.options.map { it.text } else emptyList(),
            reveal = if (showReveal) reveal(s, ctx) else emptyList(),
            deltas = s.deltas.map { (id, pts) -> BluffDelta(PlayerId(id), ctx.player(PlayerId(id))?.name ?: "?", pts) }
                .sortedByDescending { it.points },
        )
    }

    private fun reveal(s: BluffState, ctx: GameContext): List<BluffReveal> {
        fun fooledBy(o: BluffOption) = s.participants.filter { s.picks[it.v] == o.id }
        val lies = s.options.filter { it.kind != TRUTH }.sortedWith(compareBy({ fooledBy(it).size }, { it.id }))
        return (lies + s.options.filter { it.kind == TRUTH }).map { o ->
            BluffReveal(o.text, o.kind, o.authors.mapNotNull { ctx.player(it)?.name }, fooledBy(o).mapNotNull { ctx.player(it)?.name })
        }
    }

    override fun playerView(s: BluffState, who: PlayerId, ctx: GameContext): Screen {
        if (s.phase == PODIUM) return Screen.Scores("Final scores", rows(ctx))
        if (who !in s.participants) return Screen.Waiting("You're in next round", "Watch the TV and get ready to bluff")
        val q = s.questionId?.let(byId::get)
        return when (s.phase) {
            WRITE -> Screen.TextEntry(
                prompt = q?.prompt ?: "",
                maxLen = MAX_FAKE,
                value = s.fakes[who.v],
                kind = "write",
                hint = if (who.v in s.fakes) "Locked in. You can still edit until time's up." else "Write a believable fake answer",
            )
            PICK -> Screen.ChoiceList(
                prompt = "Which one is the truth?",
                options = s.options.filter { who !in it.authors }.map { Choice(it.id, it.text) },
                selected = s.picks[who.v],
                kind = "pick",
            )
            REVEAL -> Screen.Waiting("Eyes on the TV", "Who got fooled?")
            else -> Screen.Scores("Round ${s.round} scores", rows(ctx))
        }
    }

    private fun rows(ctx: GameContext) =
        ctx.players.map { ScoreRow(it.id, it.name, it.avatar, ctx.scores[it.id] ?: 0) }.sortedByDescending { it.score }

    private fun List<PlayerId>.names(ctx: GameContext) = mapNotNull { ctx.player(it)?.name }.joinToString(" & ")

    companion object {
        const val WRITE = "write"
        const val PICK = "pick"
        const val REVEAL = "reveal"
        const val SCORES = "scores"
        const val PODIUM = "podium"
        private const val FAKE = "fake"
        private const val DECOY = "decoy"
        private const val TRUTH = "truth"
        const val DEFAULT_ROUNDS = 5
        const val MAX_FAKE = 60
        const val MIN_OPTIONS = 3
        const val TRUTH_POINTS = 1000
        const val FOOL_POINTS = 500
        const val WRITE_MS = 60_000L
        const val PICK_MS = 30_000L
        const val REVEAL_STEP_MS = 2_500L
        const val REVEAL_TAIL_MS = 2_000L
        const val SCORES_MS = 8_000L
        const val PODIUM_MS = 15_000L
    }
}
