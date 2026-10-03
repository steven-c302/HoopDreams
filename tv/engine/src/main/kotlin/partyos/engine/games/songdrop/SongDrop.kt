package partyos.engine.games.songdrop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import partyos.engine.SongCard
import partyos.engine.SongDrink
import partyos.engine.SongDropTv
import partyos.engine.SongSolver
import partyos.engine.Step
import partyos.engine.TutorialCard

@Serializable
data class SongState(
    val phase: String,
    val song: Int,
    val totalSongs: Int,
    val participants: List<PlayerId> = emptyList(),
    val songId: String? = null,
    /** Song ids in answer order: a, b, c, d. */
    val options: List<String> = emptyList(),
    val answer: String = "a",
    val stage: Int = 0,
    val clipSeq: Int = 0,
    /** Songs skipped as unplayable since a clip last played. */
    val fails: Int = 0,
    /** Songs skipped as unplayable in this game. */
    val bad: List<String> = emptyList(),
    val correct: List<String> = emptyList(),
    val wrong: List<String> = emptyList(),
    val points: Map<String, Int> = emptyMap(),
    /** Song ids revealed so far, for the podium. */
    val played: List<String> = emptyList(),
)

class SongDrop(private val pack: SongPack = SongPack.core()) : GameModule<SongState> {
    override val info = GameInfo(
        id = "songdrop",
        title = "Song Drop",
        tagline = "Name that tune as the clip grows",
        minPlayers = 2,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Listen up", "A clip of a real song plays on the TV. It starts short and gets longer each time nobody has it."),
            TutorialCard("Name it", "Tap the right song on your phone. A wrong tap locks you out of that song, so think first."),
            TutorialCard("Beat the clock", "Shorter clips and quicker taps score more. The last song counts double."),
        ),
        lateJoin = LateJoin.NEXT_ROUND,
        probe = pack.items.firstOrNull()?.videoId,
    )
    override val stateSerializer = SongState.serializer()

    private val songs = pack.items
    private val byId = songs.associateBy { it.id }

    override fun start(ctx: GameContext): Step<SongState> {
        val total = ((ctx.settings["rounds"] ?: DEFAULT_ROUNDS) + EXTRA_SONGS).coerceIn(MIN_SONGS, MAX_SONGS)
        return nextSong(SongState(PODIUM, 0, total), 1, ctx)
    }

    private fun remembered(ctx: GameContext): Set<String> =
        ctx.memory[BAD_KEY]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    /** Picks song number [n] (a skipped song's replacement keeps its number) and waits for the TV to start playing it. */
    private fun nextSong(prev: SongState, n: Int, ctx: GameContext): Step<SongState> {
        val here = ctx.players.filter { it.connected }.ifEmpty { ctx.players }.map { it.id }
        val banned = prev.bad.toSet() + remembered(ctx)
        val era = ctx.settings["era"] ?: 0
        val fresh = ctx.fresh(songs) { it.id }.filter { it.id !in banned }
        val answer = fresh.filter { era == 0 || it.era == era }.ifEmpty { fresh }.randomOrNull(ctx.random)
        if (answer == null || here.isEmpty()) return Step(prev.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        val choices = SongRules.options(answer, songs.filter { it.id !in banned }, ctx.random)
        val s = prev.copy(
            phase = LOAD, song = n, participants = here, songId = answer.id, options = choices.map { it.id },
            answer = LETTERS[choices.indexOfFirst { it.id == answer.id }], stage = 0, clipSeq = prev.clipSeq + 1,
            correct = emptyList(), wrong = emptyList(), points = emptyMap(),
        )
        return Step(s, listOf(Effect.UseContent(answer.id), Effect.Phase(LOAD_MS)))
    }

    /** The song can't be played (the TV said so, or never reported playing): remember it and draw a replacement. */
    private fun skip(s: SongState, ctx: GameContext): Step<SongState> {
        val id = s.songId ?: return Step(s)
        val fails = s.fails + 1
        val remember = Effect.Remember(BAD_KEY, (remembered(ctx).toList() + id).takeLast(MAX_BAD).joinToString(","))
        if (fails >= MAX_FAILS) return Step(s.copy(phase = DEAD, fails = fails), listOf(remember, Effect.Phase(DEAD_MS)))
        val next = nextSong(s.copy(bad = s.bad + id, fails = fails), s.song, ctx)
        return next.copy(effects = listOf(remember) + next.effects)
    }

    /**
     * The TV's side of the clip handshake. Every message carries the clip number it is about; one for any clip but the
     * current one is a late message from an earlier clip and does nothing.
     */
    override fun onHost(s: SongState, action: String, ctx: GameContext): Step<SongState> {
        val verb = action.substringBefore(':')
        if (verb != "ready" && verb != "bad" && verb != "replay") throw Reject("UNSUPPORTED")
        val seq = action.substringAfter(':', "").toIntOrNull() ?: throw Reject("BAD_ACTION")
        if (seq != s.clipSeq) return Step(s)
        return when (verb) {
            "ready" ->
                if (s.phase == LOAD) Step(s.copy(phase = STAGE, fails = 0), listOf(Effect.Phase(SongRules.CLIP_MS[s.stage]))) else Step(s)
            "bad" -> if (s.phase == LOAD || s.phase == STAGE) skip(s, ctx) else Step(s)
            else ->
                if (s.phase == STAGE) Step(s.copy(phase = LOAD, clipSeq = s.clipSeq + 1), listOf(Effect.Phase(LOAD_MS))) else Step(s)
        }
    }

    override fun onAction(s: SongState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<SongState> {
        if (who !in s.participants) throw Reject("NEXT_ROUND")
        if (payload["kind"]?.jsonPrimitive?.content != "pick" || s.phase != STAGE) throw Reject("NOT_NOW")
        if (who.v in s.correct) throw Reject("NOT_NOW")
        if (who.v in s.wrong) throw Reject("LOCKED")
        val letter = payload["option"]?.jsonPrimitive?.content
        val at = LETTERS.indexOf(letter)
        if (letter == null || at < 0 || at >= s.options.size) throw Reject("BAD_OPTION")
        if (letter != s.answer) return Step(s.copy(wrong = s.wrong + who.v))
        val pts = SongRules.points(s.stage, ctx.remainingMs ?: 0L, SongRules.CLIP_MS[s.stage], multiplier(s))
        return Step(s.copy(correct = s.correct + who.v, points = s.points + (who.v to pts)), listOf(Effect.Award(who, pts, "named it")))
    }

    override fun onDeadline(s: SongState, ctx: GameContext): Step<SongState> = when (s.phase) {
        LOAD -> skip(s, ctx)
        STAGE -> endOfStage(s, ctx)
        REVEAL ->
            if (s.song < s.totalSongs) nextSong(s, s.song + 1, ctx)
            else Step(s.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        else -> Step(s, listOf(Effect.Finish))
    }

    /** Somebody named it, nobody who could still try is left, or it was the longest clip: reveal. Otherwise the clip grows. */
    private fun endOfStage(s: SongState, ctx: GameContext): Step<SongState> {
        if (s.correct.isNotEmpty() || pending(s).none { ctx.isConnected(it) } || s.stage >= SongRules.CLIP_MS.lastIndex) return reveal(s, ctx)
        return Step(s.copy(phase = LOAD, stage = s.stage + 1, clipSeq = s.clipSeq + 1), listOf(Effect.Phase(LOAD_MS)))
    }

    private fun reveal(s: SongState, ctx: GameContext): Step<SongState> {
        val song = byId.getValue(requireNotNull(s.songId))
        val effects = mutableListOf<Effect>()
        val first = s.correct.firstOrNull()?.let { ctx.player(PlayerId(it)) }
        if (first != null && s.stage == 0) effects += Effect.Highlight("${first.name} knew “${song.title}” in 2 seconds")
        return Step(s.copy(phase = REVEAL, played = s.played + song.id), effects + Effect.Phase(REVEAL_MS))
    }

    private fun pending(s: SongState) = s.participants.filter { it.v !in s.correct && it.v !in s.wrong }
    private fun multiplier(s: SongState) = if (s.song == s.totalSongs) 2 else 1

    /** Only the guessing stage waits on players; load has no early end (the TV, not a phone, ends it). */
    override fun waitingOn(s: SongState): Set<PlayerId>? = if (s.phase == STAGE) pending(s).toSet() else null

    override fun restorable(s: SongState) = s.songId == null || (s.songId in byId && s.options.all { it in byId })

    // ---- views ----------------------------------------------------------------------------------

    private fun card(song: Song) = SongCard(song.title, song.artist, song.year, song.videoId)
    private fun label(song: Song) = "${song.title} – ${song.artist}"

    override fun tvView(s: SongState, ctx: GameContext): SongDropTv {
        val song = s.songId?.let(byId::get)
        val name = { id: String -> ctx.player(PlayerId(id))?.name ?: "?" }
        val inSong = s.phase == LOAD || s.phase == STAGE || s.phase == REVEAL
        val shown = s.phase == REVEAL
        return SongDropTv(
            phase = s.phase,
            song = s.song,
            totalSongs = s.totalSongs,
            finalSong = s.song == s.totalSongs,
            clipSeq = s.clipSeq,
            videoId = if (inSong) song?.videoId.orEmpty() else "",
            startSec = if (inSong) song?.startSec ?: 0 else 0,
            stage = s.stage,
            stages = SongRules.CLIP_MS.size,
            clipMs = SongRules.CLIP_MS[s.stage.coerceIn(0, SongRules.CLIP_MS.lastIndex)],
            answered = s.correct.size + s.wrong.size,
            expected = s.participants.size,
            solvers = if (inSong) s.correct.map { SongSolver(PlayerId(it), name(it), if (shown) s.points[it] else null) } else emptyList(),
            lockedOut = if (inSong) s.wrong.map(::PlayerId) else emptyList(),
            card = if (shown) song?.let(::card) else null,
            drinks = if (shown) drinks(s, ctx) else emptyList(),
            gallery = if (s.phase == PODIUM) s.played.mapNotNull { byId[it]?.let(::card) } else emptyList(),
        )
    }

    private fun drinks(s: SongState, ctx: GameContext): List<SongDrink> {
        if ((ctx.settings["drinks"] ?: 1) == 0) return emptyList()
        return s.participants.filter { it.v !in s.correct }
            .mapNotNull { id -> ctx.player(id)?.let { SongDrink(it.id, it.name, 1, "Missed it. Drink 1 sip") } }
    }

    override fun playerView(s: SongState, who: PlayerId, ctx: GameContext): Screen {
        if (s.phase == PODIUM) return Screen.Scores("Final scores", rows(ctx))
        if (s.phase == DEAD) return Screen.Waiting("Song Drop stopped", "YouTube isn't playing on the TV")
        if (who !in s.participants) return Screen.Waiting("You're in next song", "Watch the TV and get ready")
        return when (s.phase) {
            LOAD -> Screen.Waiting("Get ready…", "Song ${s.song} of ${s.totalSongs}")
            STAGE -> when {
                who.v in s.correct -> Screen.Waiting("Got it!", "+${s.points[who.v] ?: 0}", tone = "win")
                who.v in s.wrong -> Screen.Waiting("Locked out", "Wait for the next song", tone = "lose")
                else -> Screen.ChoiceList(
                    prompt = "Name that song (${SongRules.CLIP_MS[s.stage] / 1000} sec clip)",
                    options = s.options.mapIndexed { i, id -> Choice(LETTERS[i], label(byId.getValue(id))) },
                    selected = null,
                    kind = "pick",
                    style = "shapes",
                )
            }
            else -> revealScreen(s, who, ctx)
        }
    }

    private fun revealScreen(s: SongState, who: PlayerId, ctx: GameContext): Screen {
        val song = byId.getValue(requireNotNull(s.songId))
        val gained = s.points[who.v] ?: 0
        val drink = drinks(s, ctx).firstOrNull { it.id == who }?.text
        val detail = when {
            drink != null -> drink
            gained > 0 -> "+$gained"
            else -> "Eyes on the TV"
        }
        return Screen.Waiting(song.title, "${song.artist}. $detail", tone = if (gained > 0) "win" else if (drink != null) "lose" else "neutral")
    }

    private fun rows(ctx: GameContext) =
        ctx.players.map { ScoreRow(it.id, it.name, it.avatar, ctx.scores[it.id] ?: 0) }.sortedByDescending { it.score }

    companion object {
        const val LOAD = "load"
        const val STAGE = "stage"
        const val REVEAL = "reveal"
        const val PODIUM = "podium"
        const val DEAD = "dead"
        const val LOAD_MS = 10_000L
        const val REVEAL_MS = 9_000L
        const val PODIUM_MS = 20_000L
        const val DEAD_MS = 8_000L
        const val MAX_FAILS = 3
        const val BAD_KEY = "songdrop.bad"
        private const val MAX_BAD = 300
        private val LETTERS = listOf("a", "b", "c", "d")
        const val DEFAULT_ROUNDS = 5
        private const val EXTRA_SONGS = 3
        private const val MIN_SONGS = 6
        private const val MAX_SONGS = 11
    }
}
