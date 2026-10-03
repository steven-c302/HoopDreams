package partyos.engine.games.hottype

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.Effect
import partyos.engine.GameContext
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.HotTypeTv
import partyos.engine.HuntBigFind
import partyos.engine.HuntDelta
import partyos.engine.HuntDrink
import partyos.engine.HuntFound
import partyos.engine.HuntMissed
import partyos.engine.HuntPageWord
import partyos.engine.HuntRail
import partyos.engine.LateJoin
import partyos.engine.PlayerId
import partyos.engine.Reject
import partyos.engine.ScoreRow
import partyos.engine.Screen
import partyos.engine.Step
import partyos.engine.TutorialCard
import kotlin.random.Random

@Serializable
data class HotTypeBigFind(val seq: Int, val id: String, val letters: Int)

@Serializable
data class HotTypeState(
    val phase: String,
    val round: Int,
    val totalRounds: Int,
    val size: Int,
    /** Stored from `ready` so a restored game keeps its board, but shown in no view until `hunt`. */
    val tiles: List<String> = emptyList(),
    val participants: List<PlayerId> = emptyList(),
    /** player id to words in the order found */
    val found: Map<String, List<String>> = emptyMap(),
    val bigFind: HotTypeBigFind? = null,
    /** The best word nobody found, worked out once when the press ends. */
    val missed: String? = null,
    val deltas: Map<String, Int> = emptyMap(),
)

class HotType(
    private val dictionary: HotTypeDictionary = HotTypeDictionary.core,
    private val dealBoard: (Int, Random) -> List<String> = { size, random -> HotTypeDealer(HotTypeSolver(dictionary)).deal(size, random) },
) : GameModule<HotTypeState> {
    override val info = GameInfo(
        id = "hottype",
        title = "Hot Type",
        tagline = "Find words. Stamp them.",
        minPlayers = 2,
        maxPlayers = 8,
        tutorial = listOf(
            TutorialCard("Swipe to spell", "Drag your finger across touching letters on your phone. Lift to stamp the word. Three letters or more."),
            TutorialCard("Only you gets double", "Every word scores. A word nobody else finds scores double. Words stay secret until the reveal."),
            TutorialCard("Long words win", "Longer words are worth much more, and the longest word of the round earns a bonus."),
        ),
        lateJoin = LateJoin.NEXT_ROUND,
    )
    override val stateSerializer = HotTypeState.serializer()

    private val solver = HotTypeSolver(dictionary)
    private val blocked = HotTypeDictionary.blocked

    override fun start(ctx: GameContext): Step<HotTypeState> {
        val rounds = (ctx.settings["rounds"] ?: DEFAULT_ROUNDS).coerceIn(3, 8)
        val size = if ((ctx.settings["grid"] ?: 0) == 1) 5 else 4
        return newRound(HotTypeState(PODIUM, 0, rounds, size), 1, ctx)
    }

    private fun newRound(prev: HotTypeState, round: Int, ctx: GameContext): Step<HotTypeState> {
        val here = ctx.players.filter { it.connected }.ifEmpty { ctx.players }.map { it.id }
        val s = prev.copy(
            phase = READY, round = round, tiles = dealBoard(prev.size, ctx.random), participants = here,
            found = here.associate { it.v to emptyList() }, bigFind = null, missed = null, deltas = emptyMap(),
        )
        return Step(s, listOf(Effect.Phase(READY_MS)))
    }

    override fun onAction(s: HotTypeState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<HotTypeState> {
        if (payload["kind"]?.jsonPrimitive?.content != "word") throw Reject("NOT_NOW")
        if (who !in s.participants) throw Reject("NEXT_ROUND")
        if (s.phase != HUNT) throw Reject("NOT_NOW")
        val path = (payload["path"] as? JsonArray)?.map { (it as? JsonPrimitive)?.intOrNull ?: throw Reject("BAD_PATH") }
            ?: throw Reject("BAD_PATH")
        val word = HotTypeBoard.wordOf(s.tiles, s.size, path) ?: throw Reject("BAD_PATH")
        if (word.length < HotTypeRules.MIN_LETTERS) throw Reject("TOO_SHORT")
        if (!dictionary.contains(word)) throw Reject("NOT_A_WORD")
        val mine = s.found[who.v].orEmpty()
        if (word in mine) throw Reject("ALREADY")
        val big = if (word.length >= HotTypeRules.BIG_FIND) HotTypeBigFind((s.bigFind?.seq ?: 0) + 1, who.v, word.length) else s.bigFind
        return Step(s.copy(found = s.found + (who.v to mine + word), bigFind = big))
    }

    override fun onDeadline(s: HotTypeState, ctx: GameContext): Step<HotTypeState> = when (s.phase) {
        READY -> Step(s.copy(phase = HUNT), listOf(Effect.Phase(ctx.timer(HUNT_MS))))
        HUNT -> Step(s.copy(phase = PRESS), listOf(Effect.Phase(PRESS_MS)))
        PRESS -> settle(s, ctx)
        REVEAL -> Step(s.copy(phase = SCORES), listOf(Effect.Phase(SCORES_MS)))
        SCORES ->
            if (s.round < s.totalRounds) newRound(s, s.round + 1, ctx)
            else Step(s.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        else -> Step(s, listOf(Effect.Finish))
    }

    /** The press ends: score the round, award the points, and find the best word nobody found. */
    private fun settle(s: HotTypeState, ctx: GameContext): Step<HotTypeState> {
        val result = HotTypeRules.score(s.found, multiplier(s))
        val deltas = result.parts.mapValues { it.value.total }
        val effects = mutableListOf<Effect>()
        for (p in s.participants) {
            val pts = deltas[p.v] ?: 0
            if (pts > 0) effects += Effect.Award(p, pts, "hunted words")
        }
        for (w in result.words.filter { it.longest && it.word.length >= 7 }) {
            for (id in w.finders) ctx.player(PlayerId(id))?.let {
                effects += Effect.Highlight("${it.name} found ${w.word.uppercase()}, the longest word of the round")
            }
        }
        val missed = solver.bestMissed(s.tiles, s.size, s.found.values.flatten().toSet(), blocked)
        return Step(s.copy(phase = REVEAL, deltas = deltas, missed = missed), effects + Effect.Phase(REVEAL_MS))
    }

    override fun waitingOn(s: HotTypeState): Set<PlayerId>? = null

    override fun restorable(s: HotTypeState) = s.tiles.isEmpty() || s.tiles.size == s.size * s.size

    private fun multiplier(s: HotTypeState) = if (s.round == s.totalRounds) 2 else 1

    override fun tvView(s: HotTypeState, ctx: GameContext): HotTypeTv {
        val name = { id: PlayerId -> ctx.player(id)?.name ?: "?" }
        val live = s.phase == HUNT || s.phase == PRESS
        val revealed = s.phase == REVEAL || s.phase == SCORES
        val result = if (revealed) HotTypeRules.score(s.found, multiplier(s)) else null
        val distinct = s.found.values.flatten().toSet()
        return HotTypeTv(
            phase = s.phase,
            round = s.round,
            totalRounds = s.totalRounds,
            finalRound = s.round == s.totalRounds,
            size = s.size,
            tiles = if (live || revealed) s.tiles else emptyList(),
            rail = if (live) s.participants.map { p ->
                val words = s.found[p.v].orEmpty()
                HuntRail(p, name(p), words.size, words.sumOf { HotTypeRules.points(it.length) }, words.map { it.length })
            } else emptyList(),
            wordsFound = if (live) distinct.size else 0,
            longest = if (live) distinct.maxOfOrNull { it.length } ?: 0 else 0,
            bigFind = if (live) s.bigFind?.let { HuntBigFind(it.seq, PlayerId(it.id), name(PlayerId(it.id)), it.letters) } else null,
            page = result?.let { r ->
                HotTypeRules.frontPage(r).map { HuntPageWord(it.word, it.base, it.bonus, it.finders.map(::PlayerId), it.longest) }
            }.orEmpty(),
            missed = if (revealed) s.missed?.let { HuntMissed(it, HotTypeRules.points(it.length)) } else null,
            deltas = if (s.phase == SCORES && result != null) {
                s.participants.map { p ->
                    val parts = result.parts.getValue(p.v)
                    HuntDelta(p, name(p), parts.base, parts.unique, parts.longest, parts.total)
                }.sortedByDescending { it.total }
            } else emptyList(),
            drinks = if (s.phase == SCORES) drinks(s, ctx) else emptyList(),
        )
    }

    private fun drinks(s: HotTypeState, ctx: GameContext): List<HuntDrink> {
        if ((ctx.settings["drinks"] ?: 1) == 0) return emptyList()
        val totals = s.participants.associateWith { s.deltas[it.v] ?: 0 }
        val low = totals.values.minOrNull() ?: return emptyList()
        val lowest = totals.filterValues { it == low }.keys
        if (lowest.size != 1) return emptyList()
        val p = lowest.single()
        val player = ctx.player(p) ?: return emptyList()
        return listOf(HuntDrink(p, player.name, 2, "Last place! Drink 2 sips"))
    }

    override fun playerView(s: HotTypeState, who: PlayerId, ctx: GameContext): Screen {
        if (s.phase == PODIUM) return Screen.Scores("Final scores", rows(ctx))
        if (who !in s.participants) return Screen.Waiting("You're in next round", "Watch the TV and get ready")
        val revealed = s.phase == REVEAL || s.phase == SCORES
        val result = if (revealed) HotTypeRules.score(s.found, multiplier(s)) else null
        val byWord = result?.words?.associateBy { it.word }
        val found = s.found[who.v].orEmpty().asReversed().map { w ->
            val r = byWord?.get(w)
            HuntFound(w, r?.base ?: HotTypeRules.points(w.length), r?.finders?.size ?: 0, r?.bonus ?: 0)
        }
        val score = result?.parts?.get(who.v)?.total ?: s.found[who.v].orEmpty().sumOf { HotTypeRules.points(it.length) }
        return Screen.Hunt(
            phase = s.phase,
            round = s.round,
            totalRounds = s.totalRounds,
            size = s.size,
            tiles = if (s.phase == READY) emptyList() else s.tiles,
            found = found,
            score = score,
            note = when (s.phase) {
                READY -> "Get ready"
                PRESS -> "Pencils down"
                else -> null
            },
        )
    }

    private fun rows(ctx: GameContext) =
        ctx.players.map { ScoreRow(it.id, it.name, it.avatar, ctx.scores[it.id] ?: 0) }.sortedByDescending { it.score }

    companion object {
        const val READY = "ready"
        const val HUNT = "hunt"
        const val PRESS = "press"
        const val REVEAL = "reveal"
        const val SCORES = "scores"
        const val PODIUM = "podium"
        /** The lobby shows 5 rounds until someone changes it, so this matches (see the spec's open questions). */
        const val DEFAULT_ROUNDS = 5
        const val READY_MS = 5_000L
        const val HUNT_MS = 90_000L
        const val PRESS_MS = 2_500L
        const val REVEAL_MS = 22_000L
        const val SCORES_MS = 8_000L
        const val PODIUM_MS = 15_000L
    }
}
