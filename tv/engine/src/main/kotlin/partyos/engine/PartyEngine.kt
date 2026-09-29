package partyos.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

enum class JoinError { WRONG_ROOM, FULL, NAME_TAKEN, BAD_NAME }

sealed interface JoinResult {
    data class Joined(val player: Player, val token: String) : JoinResult
    data class Failed(val error: JoinError) : JoinResult
}

@Serializable
data class PartySnapshot(
    val roomCode: String,
    val createdAt: Long,
    val players: List<Player>,
    val tokenHashes: Map<String, PlayerId>,
    val pinSalt: String? = null,
    val pinHash: String? = null,
    val settings: Map<String, Int> = emptyMap(),
    val usedContent: List<String> = emptyList(),
    val results: List<GameResult> = emptyList(),
    val game: GameSnapshot? = null,
    val memory: Map<String, String> = emptyMap(),
    val captain: PlayerId? = null,
)

/**
 * The single authority for one party: roster, identities, and the game runtime.
 * Not thread-safe: callers serialise access (see server's PartyHost).
 */
class PartyEngine private constructor(
    private val clock: Clock,
    private val entropy: Entropy,
    private val games: GameRegistry,
    val roomCode: String,
    private val createdAt: Long,
    players: List<Player>,
    tokenHashes: Map<String, PlayerId>,
    private var pinSalt: String?,
    private var pinHash: String?,
    settings: Map<String, Int>,
    usedContent: Collection<String>,
    results: List<GameResult>,
    memory: Map<String, String>,
    private var captainPick: PlayerId?,
) {
    constructor(clock: Clock, entropy: Entropy, games: GameRegistry = GameRegistry(emptyList())) : this(
        clock, entropy, games, newRoomCode(entropy), clock.now(), emptyList(), emptyMap(),
        null, null, emptyMap(), emptyList(), emptyList(), emptyMap(), null,
    )

    private val roster = LinkedHashMap<PlayerId, Player>().apply { players.forEach { put(it.id, it) } }
    private val tokens = HashMap(tokenHashes)
    private val settings = HashMap(settings)
    private val usedContent = LinkedHashSet(usedContent)
    /** What's been played at this party, as opposed to remembered from earlier nights (a restored party counts it all). */
    private val partyPlayed = LinkedHashSet(usedContent)
    private val results = ArrayList(results)
    private val memory = LinkedHashMap(memory)
    private var active: ActiveGame<*>? = null

    /** Games this party can start, in registry order. */
    val gameInfos: List<GameInfo> get() = games.all.map { it.info }

    val players: List<Player> get() = roster.values.filterNot { it.kicked }
    private val gamePlayers get() = players.filter { it.role == Role.PLAYER }
    private val connectedPlayers get() = gamePlayers.count { it.connected }

    fun player(id: PlayerId): Player? = roster[id]?.takeUnless { it.kicked }

    // ---- identity -------------------------------------------------------------------------

    fun join(room: String, rawName: String, avatar: Avatar, role: Role, water: Boolean = false): JoinResult {
        if (!room.trim().equals(roomCode, ignoreCase = true)) return JoinResult.Failed(JoinError.WRONG_ROOM)
        val name = rawName.trim()
        if (name.isEmpty() || name.length > MAX_NAME || name.any { Character.isISOControl(it) }) {
            return JoinResult.Failed(JoinError.BAD_NAME)
        }
        if (players.any { it.name.equals(name, ignoreCase = true) }) return JoinResult.Failed(JoinError.NAME_TAKEN)
        if (players.count { it.role == role } >= MAX_PER_ROLE) return JoinResult.Failed(JoinError.FULL)
        val player = Player(PlayerId(entropy.token().take(12)), name, avatar.sanitized(), role, clock.now(), water = water)
        val token = entropy.token()
        roster[player.id] = player
        tokens[sha256(token)] = player.id
        // The first to join holds the crown (Jackbox's VIP); it follows them back if their phone drops and returns.
        if (captainPick == null && role == Role.PLAYER) captainPick = player.id
        return JoinResult.Joined(player, token)
    }

    fun resolve(token: String): PlayerId? = tokens[sha256(token)]?.takeIf { player(it) != null }

    fun setPresence(id: PlayerId, connected: Boolean) {
        val p = roster[id] ?: return
        if (p.connected == connected) return
        roster[id] = p.copy(connected = connected)
        active?.let { if (p.role == Role.PLAYER) presenceChanged(it, id, connected) }
        settle()
    }

    fun kick(id: PlayerId) {
        val p = roster[id] ?: return
        if (captainPick == id) captainPick = null
        roster[id] = p.copy(kicked = true, connected = false)
        tokens.values.removeAll { it == id }
        active?.let { if (p.role == Role.PLAYER) presenceChanged(it, id, false) }
        settle()
    }

    /** Switches a member between player and spectator; only between games and only into a free seat. Null = done. */
    fun setRole(id: PlayerId, role: Role): String? {
        val p = player(id) ?: return "UNKNOWN_PLAYER"
        if (p.role == role) return null
        if (active != null) return "GAME_RUNNING"
        if (players.count { it.role == role } >= MAX_PER_ROLE) return "FULL"
        roster[id] = p.copy(role = role)
        return null
    }

    /** Water tonight (or not): any time, games included. Drink calls stay the same; screens word them as water. */
    fun setWater(id: PlayerId, water: Boolean): Boolean {
        val p = player(id) ?: return false
        roster[id] = p.copy(water = water)
        return true
    }

    /**
     * Questions and prompts already played on earlier nights (oldest first), so games skip them. Call before the first
     * game; see [GameContext.fresh] for what happens once a pack is used up.
     */
    fun rememberPlayed(ids: Collection<String>) = ids.forEach(::markPlayed)

    /** Played content, oldest first: playing something again moves it to the end, and only the newest [MAX_PLAYED] are kept. */
    private fun markPlayed(id: String) {
        usedContent.remove(id)
        usedContent += id
        while (usedContent.size > MAX_PLAYED) usedContent.first().let { usedContent.remove(it); partyPlayed.remove(it) }
    }

    fun setPin(pin: String) {
        val salt = entropy.token()
        pinSalt = salt
        pinHash = sha256(salt + pin)
    }

    fun checkPin(pin: String): Boolean {
        val salt = pinSalt ?: return false
        return sha256(salt + pin) == pinHash
    }

    // ---- the captain ----------------------------------------------------------------------

    /**
     * Who holds the crown right now: the chosen captain if their phone is connected, otherwise the earliest-joined
     * connected player (then spectator). Null when the host has turned phone control off.
     */
    fun captain(): PlayerId? {
        if (settings["captain"] == 0) return null
        captainPick?.let { id -> if (roster[id]?.let { it.connected && !it.kicked } == true) return id }
        val here = players.filter { it.connected }.sortedBy { it.joinedAt }
        return (here.firstOrNull { it.role == Role.PLAYER } ?: here.firstOrNull())?.id
    }

    /** A host command sent from the captain's phone. The captain runs the show but can't remove people. */
    fun captainCommand(id: PlayerId, cmd: HostCmd): ActionResult {
        if (captain() != id) return ActionResult.Rejected("NOT_CAPTAIN")
        return when {
            cmd is HostCmd.Kick -> ActionResult.Rejected("HOST_ONLY")
            cmd is HostCmd.SetOption && cmd.key == "captain" -> ActionResult.Rejected("HOST_ONLY")
            else -> host(cmd)
        }
    }

    /** Allowed values for each shared setting. */
    fun optionRange(key: String): IntRange? = when (key) {
        "rounds" -> 3..8
        "teams" -> 0..6
        "drinks", "captain" -> 0..1
        // Decision timers: 0 normal, 1 relaxed (1.5×), 2 no rush (2×). See GameContext.timer.
        "timers" -> 0..2
        // Home Turf: 0 auto (solo up to 6 players, teams beyond), 1 solo, 2 teams; game clock in minutes (0 = no limit).
        "turfMode" -> 0..2
        "minutes" -> 0..120
        // Sprawl: points to win (8 or 10).
        "vp" -> 8..10
        "game" -> 0..(games.all.size - 1).coerceAtLeast(0)
        else -> null
    }

    // ---- game runtime ---------------------------------------------------------------------

    fun action(id: PlayerId, actionId: String, round: Int, payload: JsonObject): ActionResult {
        val p = player(id) ?: return ActionResult.Rejected("UNKNOWN_PLAYER")
        if (p.role != Role.PLAYER) return ActionResult.Rejected("SPECTATOR")
        val g = active ?: return ActionResult.Rejected("NO_GAME")
        if (actionId in g.handled) return ActionResult.Ack
        if (g.paused) return ActionResult.Rejected("PAUSED")
        if (round != g.phaseSeq && !(g.tutorialAcks == null && g.state != null && g.module.phaseFree(payload))) return ActionResult.Rejected("STALE")
        val result = if (g.tutorialAcks != null) {
            if (payload["kind"]?.jsonPrimitive?.content != "ack") return ActionResult.Rejected("TUTORIAL")
            g.tutorialAcks!!.add(id)
            ActionResult.Ack
        } else {
            try {
                gameAction(g, id, payload)
                ActionResult.Ack
            } catch (r: Reject) {
                return ActionResult.Rejected(r.code)
            }
        }
        g.remember(actionId)
        settle()
        return result
    }

    fun host(cmd: HostCmd): ActionResult {
        when (cmd) {
            is HostCmd.StartGame -> {
                if (active != null) return ActionResult.Rejected("GAME_RUNNING")
                val module = games[cmd.gameId] ?: return ActionResult.Rejected("UNKNOWN_GAME")
                if (connectedPlayers < module.info.minPlayers) return ActionResult.Rejected("NOT_ENOUGH_PLAYERS")
                active = newActive(module, settings + cmd.settings)
                if (module.info.tutorial.isEmpty()) beginGame(active!!)
            }
            HostCmd.Pause -> active?.let { pause(it, "HOST") } ?: return ActionResult.Rejected("NO_GAME")
            HostCmd.Resume -> {
                val g = active ?: return ActionResult.Rejected("NO_GAME")
                if (!g.paused) return ActionResult.Ack
                if (connectedPlayers < 2) return ActionResult.Rejected("NOT_ENOUGH_PLAYERS")
                g.paused = false
                g.pauseReason = null
                g.deadlineAt = g.pausedRemaining?.let { clock.now() + it }
                g.pausedRemaining = null
            }
            HostCmd.SkipPhase -> {
                val g = active ?: return ActionResult.Rejected("NO_GAME")
                if (g.tutorialAcks != null) beginGame(g) else deadline(g)
            }
            HostCmd.EndGame -> active?.let { finish(it) } ?: return ActionResult.Rejected("NO_GAME")
            is HostCmd.Kick -> kick(cmd.player)
            is HostCmd.SetRounds -> settings["rounds"] = cmd.rounds.coerceIn(3, 8)
            is HostCmd.SetOption -> {
                // A running game keeps the timers it started with (the TV's clock faces assume them).
                if (cmd.key == "timers" && active != null) return ActionResult.Rejected("GAME_RUNNING")
                settings[cmd.key] = cmd.value.coerceIn(optionRange(cmd.key) ?: return ActionResult.Rejected("BAD_OPTION"))
            }
            is HostCmd.MakeCaptain -> {
                if (player(cmd.player) == null) return ActionResult.Rejected("UNKNOWN_PLAYER")
                captainPick = cmd.player
            }
            is HostCmd.GameAction -> {
                val g = active ?: return ActionResult.Rejected("NO_GAME")
                if (g.tutorialAcks != null || g.state == null) return ActionResult.Rejected("NOT_NOW")
                if (g.paused) return ActionResult.Rejected("PAUSED")
                try {
                    hostAction(g, cmd.action)
                } catch (r: Reject) {
                    return ActionResult.Rejected(r.code)
                }
            }
        }
        settle()
        return ActionResult.Ack
    }

    /**
     * The drawing turn [id] may ink on in the phase numbered [round], or null: not a player, not the drawer, wrong or
     * old phase, paused, in the tutorial, or the game doesn't draw.
     */
    fun inkTurn(id: PlayerId, round: Int): Int? {
        if (player(id)?.role != Role.PLAYER) return null
        val g = active ?: return null
        if (g.paused || g.tutorialAcks != null || round != g.phaseSeq) return null
        return inkTurnOf(g, id)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <S : Any> inkTurnOf(g: ActiveGame<S>, id: PlayerId): Int? =
        (g.module as? InkAware<S>)?.let { m -> g.state?.let { m.inkTurn(it, id) } }

    /** Fires the current deadline if it has passed. Call at [nextDeadline]. */
    fun tick() {
        val g = active ?: return
        val due = g.deadlineAt ?: return
        if (g.paused || clock.now() < due) return
        if (g.tutorialAcks != null) beginGame(g) else deadline(g)
        settle()
    }

    fun nextDeadline(): Long? = active?.takeUnless { it.paused }?.deadlineAt

    // ---- views ----------------------------------------------------------------------------

    fun tvState(): TvState {
        val g = active
        return TvState(
            roomCode = roomCode,
            players = players.map { it.summary() },
            stage = g?.let { stageInfo(it) },
            scores = g?.let { scoreRows(it.scores) } ?: emptyList(),
            lastResult = results.lastOrNull(),
            gamesPlayed = results.size,
            captain = captain(),
            settings = settings.toMap(),
        )
    }

    fun phoneState(id: PlayerId): PhoneState {
        val p = requireNotNull(player(id)) { "unknown player $id" }
        val g = active
        val captain = captain()
        val captainName = captain?.let { player(it)?.name }
        val screen = when {
            g == null -> Screen.Waiting("You're in!", captainName?.let { if (captain == id) "You have the crown: pick a game" else "$it has the crown and picks the game" } ?: "Waiting for the host to pick a game")
            p.role == Role.SPECTATOR -> Screen.Waiting("Watching", g.module.info.title)
            g.tutorialAcks != null -> Screen.Tutorial(g.module.info.tutorial, id in g.tutorialAcks!!)
            else -> playerView(g, id)
        }
        return PhoneState(
            me = p.summary(),
            roomCode = roomCode,
            gameId = g?.module?.info?.id,
            gameTitle = g?.module?.info?.title,
            round = g?.phaseSeq ?: 0,
            paused = g?.paused ?: false,
            pauseReason = g?.pauseReason,
            remainingMs = g?.remaining(clock.now()),
            screen = screen,
            scores = g?.let { scoreRows(it.scores) } ?: results.lastOrNull()?.standings ?: emptyList(),
            captain = captain == id,
            captainName = captainName,
            settings = settings.toMap(),
            crew = if (captain == id) players.map { it.summary() } else emptyList(),
        )
    }

    fun snapshot() = PartySnapshot(
        roomCode, createdAt, roster.values.toList(), tokens.toMap(), pinSalt, pinHash,
        settings.toMap(), usedContent.toList(), results.toList(), active?.snapshot(clock.now()), memory.toMap(), captainPick,
    )

    // ---- internals ------------------------------------------------------------------------

    private fun <S : Any> newActive(module: GameModule<S>, settings: Map<String, Int>) = ActiveGame(
        module = module, state = null, phaseSeq = 1,
        deadlineAt = clock.now() + TUTORIAL_MS, pausedRemaining = null, paused = false, pauseReason = null,
        tutorialAcks = mutableSetOf(), scores = mutableMapOf(), seed = entropy.nextLong(),
        handled = ArrayDeque(), settings = settings, highlights = mutableListOf(),
    )

    private fun <S : Any> ctx(g: ActiveGame<S>) = GameContext(
        now = clock.now(),
        random = Random(phaseSeed(g.seed, g.phaseSeq)),
        players = gamePlayers,
        scores = g.scores.toMap(),
        settings = g.settings,
        usedContent = usedContent.toSet(),
        playedThisParty = partyPlayed.toSet(),
        memory = memory.toMap(),
        remainingMs = g.remaining(clock.now()),
    )

    private fun <S : Any> beginGame(g: ActiveGame<S>) {
        g.tutorialAcks = null
        g.deadlineAt = null
        apply(g, g.module.start(ctx(g)))
    }

    private fun <S : Any> deadline(g: ActiveGame<S>) = g.state?.let { apply(g, g.module.onDeadline(it, ctx(g))) }

    private fun <S : Any> gameAction(g: ActiveGame<S>, id: PlayerId, payload: JsonObject) =
        apply(g, g.module.onAction(g.state!!, id, payload, ctx(g)))

    private fun <S : Any> hostAction(g: ActiveGame<S>, action: String) = apply(g, g.module.onHost(g.state!!, action, ctx(g)))

    private fun <S : Any> presenceChanged(g: ActiveGame<S>, id: PlayerId, present: Boolean) {
        g.state?.let { apply(g, g.module.onPresence(it, id, present, ctx(g))) }
    }

    private fun <S : Any> playerView(g: ActiveGame<S>, id: PlayerId) = g.module.playerView(g.state!!, id, ctx(g))

    private fun <S : Any> stageInfo(g: ActiveGame<S>) = StageInfo(
        gameId = g.module.info.id,
        title = g.module.info.title,
        phaseSeq = g.phaseSeq,
        deadlineAt = g.deadlineAt,
        remainingMs = g.remaining(clock.now()),
        paused = g.paused,
        pauseReason = g.pauseReason,
        tutorial = g.tutorialAcks?.let { TutorialView(g.module.info.tutorial, it.toList()) },
        game = g.state?.let { g.module.tvView(it, ctx(g)) },
    )

    private fun <S : Any> apply(g: ActiveGame<S>, step: Step<S>) {
        g.state = step.state
        for (e in step.effects) when (e) {
            is Effect.Phase -> {
                g.phaseSeq++
                setDeadline(g, e.durationMs)
            }
            is Effect.Deadline -> setDeadline(g, e.durationMs)
            is Effect.Award -> if (roster.containsKey(e.player)) g.scores.merge(e.player, e.points, Int::plus)
            is Effect.Highlight -> g.highlights += e.text
            is Effect.UseContent -> {
                markPlayed(e.id)
                partyPlayed += e.id
            }
            is Effect.Remember -> if (e.key.length <= MAX_MEMORY_KEY && e.value.length <= MAX_MEMORY_VALUE) memory[e.key] = e.value
            Effect.Finish -> g.finishPending = true
        }
    }

    private fun setDeadline(g: ActiveGame<*>, durationMs: Long?) {
        if (g.paused) {
            g.pausedRemaining = durationMs
            g.deadlineAt = null
        } else {
            g.deadlineAt = durationMs?.let { clock.now() + it }
        }
    }

    private fun pause(g: ActiveGame<*>, reason: String) {
        if (g.paused) return
        g.pausedRemaining = g.deadlineAt?.let { maxOf(0, it - clock.now()) }
        g.deadlineAt = null
        g.paused = true
        g.pauseReason = reason
    }

    /** Applies runtime rules after any change: finish, tutorial completion, early phase end, auto-pause. */
    private fun settle() {
        repeat(MAX_SETTLE) {
            val g = active ?: return
            if (g.finishPending) {
                finish(g); return
            }
            if (g.paused) return
            val connected = gamePlayers.filter { it.connected }.map { it.id }.toSet()
            val acks = g.tutorialAcks
            val advanced = when {
                acks != null -> if (connected.isNotEmpty() && acks.containsAll(connected)) {
                    beginGame(g); true
                } else false
                else -> earlyEnd(g, connected)
            }
            if (!advanced) {
                if (connected.size < 2) pause(g, "WAITING_FOR_PLAYERS")
                return
            }
        }
    }

    private fun <S : Any> earlyEnd(g: ActiveGame<S>, connected: Set<PlayerId>): Boolean {
        val waiting = g.state?.let { g.module.waitingOn(it) } ?: return false
        if (waiting.any { it in connected }) return false
        deadline(g)
        return true
    }

    private fun finish(g: ActiveGame<*>) {
        results += GameResult(g.module.info.id, g.module.info.title, clock.now(), scoreRows(g.scores), g.highlights.toList())
        active = null
    }

    private fun scoreRows(scores: Map<PlayerId, Int>) = gamePlayers
        .map { ScoreRow(it.id, it.name, it.avatar, scores[it.id] ?: 0) }
        .sortedByDescending { it.score }

    private fun Player.summary() = PlayerSummary(id, name, avatar, role, connected, water)

    companion object {
        const val MAX_NAME = 16
        const val MAX_PER_ROLE = 16
        const val TUTORIAL_MS = 30_000L
        private const val MAX_MEMORY_KEY = 64
        private const val MAX_MEMORY_VALUE = 8_192
        private const val MAX_SETTLE = 16
        /** Played-content ids kept: far more than every pack together, so only very old live questions ever drop off. */
        const val MAX_PLAYED = 5_000
        private const val ROOM_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ"

        /**
         * Each phase's own seed. Kotlin's Random only lightly mixes its seed, so nearby seeds give related first draws
         * (answer shuffles that fall into patterns from one question to the next); SplitMix64's finaliser fully
         * scrambles the game seed + phase number so every phase draws independently.
         */
        internal fun phaseSeed(seed: Long, phase: Int): Long {
            var z = seed + phase.toLong() * -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        private fun newRoomCode(entropy: Entropy) =
            (1..4).map { ROOM_ALPHABET[entropy.nextInt(ROOM_ALPHABET.length)] }.joinToString("")

        /** A saved game that can't be decoded or played any more is dropped; the party and roster survive. */
        @Suppress("UNCHECKED_CAST")
        private fun restoreGame(module: GameModule<*>, gs: GameSnapshot): ActiveGame<*>? {
            val m = module as GameModule<Any>
            val g = runCatching { ActiveGame.restore(m, gs) }.getOrNull() ?: return null
            return g.takeIf { a -> a.state?.let { runCatching { m.restorable(it) }.getOrDefault(false) } ?: true }
        }

        fun restore(
            s: PartySnapshot,
            clock: Clock,
            entropy: Entropy,
            games: GameRegistry = GameRegistry(emptyList()),
        ): PartyEngine {
            val e = PartyEngine(
                clock, entropy, games, s.roomCode, s.createdAt, s.players.map { it.copy(connected = false) },
                s.tokenHashes, s.pinSalt, s.pinHash, s.settings, s.usedContent, s.results, s.memory, s.captain,
            )
            s.game?.let { gs -> games[gs.gameId]?.let { e.active = restoreGame(it, gs) } }
            return e
        }
    }
}

private val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")
private val PRESET_FACE = Regex("p:(0[0-9]|1[0-5])")
private val DRAWN_FACE = Regex("d:(?:[ML]\\d{1,2},\\d{1,2})+")
/** A selfie or photo the phone uploaded to the server (PhotoStore); the id is the first 8 bytes of its SHA-256, in hex. */
private val PHOTO_FACE = Regex("i:[0-9a-f]{16}")
const val MAX_FACE = 1_600
const val PRESET_FACES = 16

/** Presets and photo ids pass through; doodles must be well-formed and small; anything else becomes a stable preset. */
internal fun Avatar.sanitized() = Avatar(
    face = when {
        PRESET_FACE.matches(face) || PHOTO_FACE.matches(face) -> face
        face.length <= MAX_FACE && DRAWN_FACE.matches(face) -> face
        else -> "p:%02d".format(Math.floorMod(face.hashCode(), PRESET_FACES))
    },
    color = color.takeIf { HEX_COLOR.matches(it) } ?: "#8B4DFF",
)
