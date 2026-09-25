package partyos.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import partyos.engine.Clock
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

    /** Latest TV state; updated after every committed change. */
    val tv: StateFlow<TvState> = _tv.asStateFlow()

    /** Increments on every commit; sessions re-render their views when it changes. */
    val version: StateFlow<Long> = _version.asStateFlow()

    val games: List<GameListing> =
        engine.gameInfos.map { GameListing(it.id, it.title, it.tagline, it.minPlayers, it.maxPlayers) }

    init {
        scope.launch {
            for (s in pending) {
                runCatching { onCommit(s) }
                delay(persistEveryMs)
            }
        }
    }

    suspend fun <T> mutate(block: PartyEngine.() -> T): T = mutex.withLock {
        val r = engine.block()
        commit()
        r
    }

    suspend fun <T> read(block: PartyEngine.() -> T): T = mutex.withLock { engine.block() }

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
}
