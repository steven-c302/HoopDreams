package partyos.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import partyos.engine.ActionResult
import partyos.engine.Clock
import partyos.engine.HostCmd
import partyos.engine.SecureEntropy
import partyos.engine.sha256
import partyos.engine.PartyEngine
import partyos.engine.PartySnapshot
import partyos.engine.PlayerId
import partyos.engine.TvState

/**
 * Serialises every engine call behind one mutex, publishes the TV state, fires deadlines,
 * and hands snapshots to persistence (at most one write per [persistEveryMs]).
 */
class PartyHost(
    private val engine: PartyEngine,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val persistEveryMs: Long = 250,
    private val onCommit: suspend (PartySnapshot) -> Unit = {},
) {
    private val mutex = Mutex()
    private val _tv = MutableStateFlow(engine.tvState())
    private val _version = MutableStateFlow(0L)
    private val connections = HashMap<PlayerId, Int>()
    private val pending = Channel<PartySnapshot>(Channel.CONFLATED)
    private var deadlineJob: Job? = null
    private var persistJob: Job? = null
    private val recentHostIds = ArrayDeque<String>()
    private val hostTokenHashes = HashSet<String>()
    private val _pinGeneration = MutableStateFlow(0)
    private val entropy = SecureEntropy()
    private val inkBoard = InkBoard()
    private val _ink = MutableSharedFlow<ServerMsg.Ink>(extraBufferCapacity = 4096)

    /** Accepted ink batches, in order, for host (TV) sockets. */
    val inkEvents: SharedFlow<ServerMsg.Ink> = _ink.asSharedFlow()

    /** Bumps whenever the PIN changes; co-host sockets from an older generation are signed out. */
    val pinGeneration: StateFlow<Int> = _pinGeneration.asStateFlow()

    /** Latest TV state; updated after every committed change. */
    val tv: StateFlow<TvState> = _tv.asStateFlow()

    /** Increments on every commit; sessions re-render their views when it changes. */
    val version: StateFlow<Long> = _version.asStateFlow()

    val games: List<GameListing> =
        engine.gameInfos.map { GameListing(it.id, it.title, it.tagline, it.minPlayers, it.maxPlayers) }

    init {
        persistJob = scope.launch {
            for (s in pending) {
                runCatching { onCommit(s) }
                delay(persistEveryMs)
            }
        }
    }

    /** Runs a host command once per [id]; a resent id (e.g. after a reconnect) is acknowledged without re-running. */
    suspend fun hostCommand(id: String?, cmd: HostCmd): ActionResult = mutex.withLock {
        if (id != null && id in recentHostIds) return@withLock ActionResult.Ack
        val r = engine.host(cmd)
        if (id != null && r == ActionResult.Ack) {
            recentHostIds.addLast(id)
            while (recentHostIds.size > MAX_HOST_IDS) recentHostIds.removeFirst()
        }
        commit()
        r
    }

    /** A host command from the captain's phone, deduplicated like [hostCommand]. */
    suspend fun captainCommand(id: String, player: PlayerId, cmd: HostCmd): ActionResult = mutex.withLock {
        if (id in recentHostIds) return@withLock ActionResult.Ack
        val r = engine.captainCommand(player, cmd)
        if (r == ActionResult.Ack) {
            recentHostIds.addLast(id)
            while (recentHostIds.size > MAX_HOST_IDS) recentHostIds.removeFirst()
        }
        commit()
        r
    }

    suspend fun issueHostToken(): String = mutex.withLock {
        entropy.token().also { hostTokenHashes += sha256(it) }
    }

    suspend fun isHostToken(token: String): Boolean = mutex.withLock { sha256(token) in hostTokenHashes }

    /** Sets a new PIN and signs out every co-host phone. */
    suspend fun changePin(pin: String) {
        mutex.withLock {
            engine.setPin(pin)
            hostTokenHashes.clear()
            commit()
        }
        _pinGeneration.value += 1
    }

    /** The deadline this host will fire next, if its timer is still armed (for tests and diagnostics). */
    fun pendingDeadline(): Job? = deadlineJob?.takeIf { it.isActive }

    /** Stops this host's timers and persistence; call when the party is replaced or the server stops. */
    fun close() {
        deadlineJob?.cancel()
        deadlineJob = null
        persistJob?.cancel()
        pending.close()
    }

    suspend fun <T> mutate(block: PartyEngine.() -> T): T = mutex.withLock {
        val r = engine.block()
        commit()
        r
    }

    suspend fun <T> read(block: PartyEngine.() -> T): T = mutex.withLock { engine.block() }

    /**
     * A drawer's strokes: checked against the game, kept for TV reconnects and relayed to the TV. This never changes
     * game state and never pushes a view, so drawing costs nothing on the phones.
     */
    suspend fun ink(who: PlayerId, round: Int, ops: List<InkOp>) {
        mutex.withLock {
            val turn = engine.inkTurn(who, round) ?: return@withLock
            val (n, accepted) = inkBoard.apply(turn, ops) ?: return@withLock
            _ink.tryEmit(ServerMsg.Ink(turn, n, accepted))
        }
    }

    /** Everything drawn this game, and the batch number it is up to date with. */
    suspend fun inkSync(): ServerMsg.InkSync = mutex.withLock { inkBoard.sync() }

    suspend fun connected(id: PlayerId) = mutate {
        connections[id] = (connections[id] ?: 0) + 1
        setPresence(id, true)
    }

    suspend fun disconnected(id: PlayerId) = mutate {
        val left = (connections[id] ?: 1) - 1
        if (left <= 0) {
            connections.remove(id)
            setPresence(id, false)
        } else {
            connections[id] = left
        }
    }

    private fun commit() {
        _tv.value = engine.tvState()
        _version.value += 1
        pending.trySend(engine.snapshot())
        if (_tv.value.stage == null) inkBoard.reset() // the drawings belong to the game that just ended
        scheduleDeadline()
    }

    private fun scheduleDeadline() {
        deadlineJob?.cancel()
        val at = engine.nextDeadline() ?: return
        deadlineJob = scope.launch {
            delay((at - clock.now()).coerceAtLeast(0))
            mutate { tick() }
        }
    }

    private companion object {
        const val MAX_HOST_IDS = 256
    }
}
