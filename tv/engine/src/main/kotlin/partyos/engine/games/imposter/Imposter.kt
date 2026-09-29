package partyos.engine.games.imposter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.Choice
import partyos.engine.Effect
import partyos.engine.GameContext
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.ImposterClue
import partyos.engine.ImposterDelta
import partyos.engine.ImposterDrink
import partyos.engine.ImposterGuess
import partyos.engine.ImposterTv
import partyos.engine.ImposterVote
import partyos.engine.LateJoin
import partyos.engine.PlayerId
import partyos.engine.Reject
import partyos.engine.ScoreRow
import partyos.engine.Screen
import partyos.engine.SecretInput
import partyos.engine.Step
import partyos.engine.TutorialCard
import partyos.engine.games.bluff.cleanText
import partyos.engine.games.bluff.normalise
import partyos.engine.games.trivia.AnswerMatch
import partyos.engine.ofWater

@Serializable
data class ImposterState(
    val phase: String,
    val round: Int,
    val totalRounds: Int,
    val wordId: String? = null,
    val category: String = "",
    val word: String = "",
    val participants: List<PlayerId> = emptyList(),
    val imposters: List<PlayerId> = emptyList(),
    /** Ids of players who pressed "Got it" on the role card. */
    val seen: Set<String> = emptySet(),
    /** player id → one-word clue */
    val clues: Map<String, String> = emptyMap(),
    /** voter id → suspect id */
    val votes: Map<String, String> = emptyMap(),
    val accused: Set<String> = emptySet(),
    /** accused imposter id → guessed word */
    val guesses: Map<String, String> = emptyMap(),
    val guessedRight: Set<String> = emptySet(),
    val deltas: Map<String, Int> = emptyMap(),
)

class Imposter(pack: ImposterPack = ImposterPack.core()) : GameModule<ImposterState> {
    override val info = GameInfo(
        id = "imposter",
        title = "Imposter",
        tagline = "One of you is faking it",
        minPlayers = 4,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Peek at your card", "Hold your card to peek. Everyone gets the same secret word. The imposter only gets the category."),
            TutorialCard("Give a clue", "Type ONE word that shows you know the word without giving it away. Imposter: blend in."),
            TutorialCard("Find the imposter", "Talk it out, then vote. Name the imposter for points. Imposter: stay hidden, or guess the word if you're caught."),
        ),
        lateJoin = LateJoin.NEXT_ROUND,
    )
    override val stateSerializer = ImposterState.serializer()

    private val words = pack.words()
    private val byId = words.associateBy { it.id }

    override fun start(ctx: GameContext): Step<ImposterState> {
        val rounds = (ctx.settings["rounds"] ?: DEFAULT_ROUNDS).coerceIn(3, 8)
        return newRound(ImposterState(PODIUM, 0, rounds), 1, ctx)
    }

    private fun newRound(prev: ImposterState, round: Int, ctx: GameContext): Step<ImposterState> {
        val fresh = ctx.fresh(words) { it.id }
        if (fresh.isEmpty()) return Step(prev.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        val w = fresh.random(ctx.random)
        val here = ctx.players.filter { it.connected }.ifEmpty { ctx.players }.map { it.id }
        val s = ImposterState(ROLE, round, prev.totalRounds, w.id, w.category, w.word, here, ImposterRules.deal(here, ctx.random))
        return Step(s, listOf(Effect.UseContent(w.id), Effect.Phase(ROLE_MS)))
    }

    override fun onAction(s: ImposterState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<ImposterState> {
        val kind = payload["kind"]?.jsonPrimitive?.content
        if (who !in s.participants) throw Reject("NEXT_ROUND")
        return when {
            s.phase == ROLE && kind == "seen" -> Step(s.copy(seen = s.seen + who.v))
            s.phase == CLUE && kind == "clue" -> {
                val text = cleanText(payload["text"]?.jsonPrimitive?.content ?: "", MAX_CLUE) ?: throw Reject("BAD_TEXT")
                if (who !in s.imposters && normalise(text) == normalise(s.word)) throw Reject("TOO_TRUE")
                if (!CLUE_SHAPE.matches(text)) throw Reject("BAD_TEXT")
                Step(s.copy(clues = s.clues + (who.v to text)))
            }
            s.phase == VOTE && kind == "vote" -> {
                val target = payload["option"]?.jsonPrimitive?.content?.let(::PlayerId)
                if (target == null || target !in s.participants) throw Reject("BAD_OPTION")
                if (target == who) throw Reject("OWN_VOTE")
                Step(s.copy(votes = s.votes + (who.v to target.v)))
            }
            s.phase == GUESS && kind == "guess" -> {
                if (who.v !in imposterAccused(s)) throw Reject("NOT_NOW")
                val text = cleanText(payload["text"]?.jsonPrimitive?.content ?: "", MAX_GUESS) ?: throw Reject("BAD_TEXT")
                Step(s.copy(guesses = s.guesses + (who.v to text)))
            }
            else -> throw Reject("NOT_NOW")
        }
    }

    override fun onDeadline(s: ImposterState, ctx: GameContext): Step<ImposterState> = when (s.phase) {
        ROLE -> Step(s.copy(phase = CLUE), listOf(Effect.Phase(ctx.timer(CLUE_MS))))
        CLUE -> Step(s.copy(phase = DISCUSS), listOf(Effect.Phase(DISCUSS_MS)))
        DISCUSS -> Step(s.copy(phase = VOTE), listOf(Effect.Phase(ctx.timer(VOTE_MS))))
        VOTE -> reveal(s, ctx)
        RESULT ->
            if (imposterAccused(s).isNotEmpty()) Step(s.copy(phase = GUESS), listOf(Effect.Phase(ctx.timer(GUESS_MS))))
            else Step(s.copy(phase = SCORES), listOf(Effect.Phase(SCORES_MS)))
        GUESS -> judge(s, ctx)
        SCORES ->
            if (s.round < s.totalRounds) newRound(s, s.round + 1, ctx)
            else Step(s.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        else -> Step(s, listOf(Effect.Finish))
    }

    /** The guess closes: an accused imposter who named the word (close spelling counts) earns the guess points. */
    private fun judge(s: ImposterState, ctx: GameContext): Step<ImposterState> {
        val multiplier = if (s.round == s.totalRounds) 2 else 1
        val right = imposterAccused(s).filter { id -> s.guesses[id]?.let { AnswerMatch.accepts(it, s.word) } == true }.toSet()
        val deltas = s.deltas.toMutableMap()
        val effects = mutableListOf<Effect>()
        for (id in right) {
            val pts = ImposterRules.GUESS_POINTS * multiplier
            deltas.merge(id, pts, Int::plus)
            effects += Effect.Award(PlayerId(id), pts, "guessed the word")
            ctx.player(PlayerId(id))?.let { effects += Effect.Highlight("${it.name} was caught but still guessed “${s.word}”") }
        }
        return Step(s.copy(phase = SCORES, guessedRight = right, deltas = deltas), effects + Effect.Phase(SCORES_MS))
    }

    /** The vote closes: work out who is accused and award the vote points. Guess points come later. */
    private fun reveal(s: ImposterState, ctx: GameContext): Step<ImposterState> {
        val accused = ImposterRules.accused(s.votes, s.imposters.size)
        val multiplier = if (s.round == s.totalRounds) 2 else 1
        val imposters = s.imposters.map { it.v }.toSet()
        val deltas = ImposterRules.roundDeltas(imposters, s.votes, accused, multiplier)
        val effects = mutableListOf<Effect>()
        deltas.forEach { (id, pts) ->
            effects += Effect.Award(PlayerId(id), pts, if (id in imposters) "fooled the room" else "spotted the imposter")
        }
        for (i in s.imposters) {
            if (i.v !in accused) ctx.player(i)?.let { effects += Effect.Highlight("${it.name} fooled the room as the imposter") }
        }
        return Step(s.copy(phase = RESULT, accused = accused, deltas = deltas), effects + Effect.Phase(RESULT_MS))
    }

    override fun waitingOn(s: ImposterState): Set<PlayerId>? = when (s.phase) {
        ROLE -> s.participants.filter { it.v !in s.seen }.toSet()
        CLUE -> s.participants.filter { it.v !in s.clues }.toSet()
        VOTE -> s.participants.filter { it.v !in s.votes }.toSet()
        GUESS -> s.imposters.filter { it.v in s.accused && it.v !in s.guesses }.toSet()
        else -> null
    }

    override fun restorable(s: ImposterState) = s.wordId == null || s.wordId in byId

    /** The imposters the room accused. */
    private fun imposterAccused(s: ImposterState): Set<String> = s.imposters.map { it.v }.filter { it in s.accused }.toSet()

    override fun tvView(s: ImposterState, ctx: GameContext): ImposterTv {
        val name = { id: PlayerId -> ctx.player(id)?.name ?: "?" }
        val result = s.phase in RESULTS
        // A caught imposter still has to guess, so the word waits for the scores rather than sit on the TV.
        val showWord = s.phase == SCORES || (s.phase == RESULT && imposterAccused(s).isEmpty())
        return ImposterTv(
            phase = s.phase,
            round = s.round,
            totalRounds = s.totalRounds,
            finalRound = s.round == s.totalRounds,
            category = s.category,
            submitted = when (s.phase) {
                ROLE -> s.seen.size
                CLUE -> s.clues.size
                VOTE -> s.votes.size
                GUESS -> s.guesses.size
                else -> 0
            },
            expected = if (s.phase == GUESS) imposterAccused(s).size else s.participants.size,
            imposterCount = s.imposters.size,
            clues = if (s.phase in TALKING) s.participants.map { ImposterClue(it, name(it), s.clues[it.v]) } else emptyList(),
            word = if (showWord) s.word else null,
            imposters = if (result) s.imposters else emptyList(),
            accused = if (result) s.accused.map(::PlayerId) else emptyList(),
            votes = if (result) s.participants.mapNotNull { v -> s.votes[v.v]?.let { ImposterVote(v, PlayerId(it)) } } else emptyList(),
            guesses = if (s.phase == SCORES) {
                imposterAccused(s).mapNotNull { id -> s.guesses[id]?.let { ImposterGuess(PlayerId(id), name(PlayerId(id)), it, id in s.guessedRight) } }
            } else emptyList(),
            drinks = if (result) drinks(s, ctx) else emptyList(),
            deltas = if (s.phase == SCORES) {
                s.deltas.map { (id, pts) -> ImposterDelta(PlayerId(id), name(PlayerId(id)), pts) }.sortedByDescending { it.points }
            } else emptyList(),
        )
    }

    private fun drinks(s: ImposterState, ctx: GameContext): List<ImposterDrink> {
        if ((ctx.settings["drinks"] ?: 1) == 0) return emptyList()
        val imposters = s.imposters.map { it.v }.toSet()
        return s.participants.mapNotNull { p ->
            val player = ctx.player(p) ?: return@mapNotNull null
            val water = ofWater(player.water)
            when {
                p.v in s.accused && p.v in imposters -> ImposterDrink(p, player.name, 2, "Caught! Drink 2 sips$water")
                p.v in s.accused -> ImposterDrink(p, player.name, 2, "Wrongly accused! Drink 2 sips$water")
                p.v !in imposters && s.votes[p.v]?.let { it !in imposters } == true ->
                    ImposterDrink(p, player.name, 1, "Voted for an innocent. Drink 1 sip$water")
                else -> null
            }
        }
    }

    override fun playerView(s: ImposterState, who: PlayerId, ctx: GameContext): Screen {
        if (s.phase == PODIUM) return Screen.Scores("Final scores", rows(ctx))
        if (who !in s.participants) return Screen.Waiting("You're in next round", "Watch the TV and get ready")
        val imposter = who in s.imposters
        fun card(note: String?, kind: String?, input: SecretInput?) = Screen.Secret(
            title = "Round ${s.round}",
            face = if (imposter) "IMPOSTER" else s.word,
            category = s.category,
            role = if (imposter) "imposter" else "crew",
            note = note,
            kind = kind,
            acknowledged = who.v in s.seen,
            input = input,
        )
        return when (s.phase) {
            ROLE -> card(if (imposter) "Nobody knows who you are. Blend in." else "Don't let the imposter find out the word.", "seen", null)
            CLUE -> card(
                null, null,
                SecretInput(
                    prompt = if (imposter) "One word that fits ${s.category}" else "One word about the secret word",
                    maxLen = MAX_CLUE,
                    value = s.clues[who.v],
                    kind = "clue",
                    hint = if (who.v in s.clues) "Locked in. You can still change it until time's up." else "One word only",
                ),
            )
            DISCUSS -> card("Watch the TV. Who's faking it?", null, null)
            VOTE -> Screen.ChoiceList(
                prompt = "Who is the imposter?",
                options = s.participants.filter { it != who }.map { Choice(it.v, ctx.player(it)?.name ?: "?") },
                selected = s.votes[who.v],
                kind = "vote",
                style = "faces",
            )
            RESULT -> resultScreen(s, who, ctx)
            GUESS ->
                if (who.v in imposterAccused(s)) Screen.TextEntry("You were caught! Guess the secret word.", MAX_GUESS, s.guesses[who.v], "guess", "Category: ${s.category}")
                else Screen.Waiting("Caught!", "The imposter gets one guess at the word")
            else -> Screen.Scores("Round ${s.round} scores", rows(ctx))
        }
    }

    private fun resultScreen(s: ImposterState, who: PlayerId, ctx: GameContext): Screen {
        val imposters = s.imposters.map { it.v }.toSet()
        val isImposter = who.v in imposters
        val won = if (isImposter) who.v !in s.accused else s.votes[who.v]?.let { it in imposters } == true
        val title = when {
            isImposter -> if (won) "You fooled them!" else "You were caught!"
            won -> "You found the imposter!"
            else -> "Wrong suspect"
        }
        val drink = drinks(s, ctx).firstOrNull { it.id == who }?.text
        return Screen.Waiting(title, drink ?: "Eyes on the TV", tone = if (won) "win" else "lose")
    }

    private fun rows(ctx: GameContext) =
        ctx.players.map { ScoreRow(it.id, it.name, it.avatar, ctx.scores[it.id] ?: 0) }.sortedByDescending { it.score }

    companion object {
        const val ROLE = "role"
        const val CLUE = "clue"
        const val DISCUSS = "discuss"
        const val VOTE = "vote"
        const val RESULT = "result"
        const val GUESS = "guess"
        const val SCORES = "scores"
        const val PODIUM = "podium"
        private val TALKING = setOf(DISCUSS, VOTE, RESULT, GUESS, SCORES)
        private val RESULTS = setOf(RESULT, GUESS, SCORES)
        private val CLUE_SHAPE = Regex("[\\p{L}\\p{N}-]{1,20}")
        const val DEFAULT_ROUNDS = 5
        const val MAX_CLUE = 20
        const val MAX_GUESS = 30
        const val ROLE_MS = 15_000L
        const val CLUE_MS = 45_000L
        const val DISCUSS_MS = 60_000L
        const val VOTE_MS = 30_000L
        const val RESULT_MS = 6_000L
        const val GUESS_MS = 20_000L
        const val SCORES_MS = 8_000L
        const val PODIUM_MS = 15_000L
    }
}
