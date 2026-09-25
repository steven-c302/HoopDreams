package com.partyos.tv

import android.content.Context
import androidx.room.Room
import com.partyos.tv.data.PartyDb
import com.partyos.tv.data.PartyStore
import com.partyos.tv.net.NetworkAddressMonitor
import com.partyos.tv.net.advertisedUrl
import com.partyos.tv.settings.SettingsRepo
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import partyos.engine.GameRegistry
import partyos.engine.PartyEngine
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.TvState
import partyos.engine.games.bluff.BluffBattle
import partyos.server.PartyHost
import partyos.server.PartyServer

private const val TAG = "PartyRuntime"

/** One running party: its engine host, HTTP server and database row. */
class LiveParty(val host: PartyHost, val server: PartyServer, val partyId: Long)

/**
 * App-wide owner of the party: restores it from Room, runs the server, and exposes what the TV UI shows.
 * Started by [com.partyos.tv.service.PartyService] so it survives the activity going to the background.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PartyRuntime(private val context: Context) {
    // A failed deadline or save must never take the whole TV app down; log it and keep hosting.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e(TAG, "party task failed", e) })
    val settings = SettingsRepo(context)
    val network = NetworkAddressMonitor(context)
    val games = GameRegistry(listOf(BluffBattle()))
    private val store = PartyStore(Room.databaseBuilder(context, PartyDb::class.java, "partyos.db").build())
    private val lock = Mutex()

    private val _live = MutableStateFlow<LiveParty?>(null)
    val live: StateFlow<LiveParty?> = _live

    val tv: StateFlow<TvState?> = _live.flatMapLatest { it?.host?.tv ?: flowOf(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The URL in the QR code, or null while no reachable address is known. */
    val joinUrl: StateFlow<String?> = combine(_live, network.ip, settings.settings, tv) { live, ip, s, t ->
        if (live == null || t == null) null else advertisedUrl(s.advertisedOverride, ip, live.server.port, t.roomCode)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun start() = lock.withLock {
        if (_live.value != null) return@withLock
        runCatching { network.start() }
        val pin = settings.ensurePin()
        // Anything unreadable in the saved party falls back to a fresh party instead of a crash loop.
        val restored = runCatching { store.loadActive() }.getOrNull()
        val engine = restored?.let { runCatching { PartyEngine.restore(it.second, SystemClock, SecureEntropy(), games) }.getOrNull() }
        val fresh = engine ?: PartyEngine(SystemClock, SecureEntropy(), games)
        fresh.setPin(pin)
        val partyId = if (engine != null) restored.first else store.create(fresh.snapshot())
        _live.value = launch(fresh, partyId)
    }

    /** Ends the current party (kept in history) and opens a fresh one with a new room code. */
    suspend fun newParty() = lock.withLock {
        _live.value?.let { old ->
            old.host.close()
            withContext(Dispatchers.IO) { old.server.stop() }
            store.end(old.partyId)
        }
        val engine = PartyEngine(SystemClock, SecureEntropy(), games)
        engine.setPin(settings.ensurePin())
        _live.value = launch(engine, store.create(engine.snapshot()))
    }

    /** Sets a new host PIN and signs out every co-host phone. */
    suspend fun changePin(pin: String) {
        settings.setPin(pin)
        _live.value?.host?.changePin(pin)
    }

    /** Stops hosting: timers, persistence and the server. Safe to call from the main thread. */
    fun stop() {
        val old = _live.value ?: return
        _live.value = null
        old.host.close()
        network.stop()
        scope.launch(Dispatchers.IO) { old.server.stop() }
    }

    private suspend fun launch(engine: PartyEngine, partyId: Long): LiveParty {
        val host = PartyHost(engine, SystemClock, scope, onCommit = { store.save(partyId, it) })
        val server = withContext(Dispatchers.IO) { PartyServer.start(host, AssetStaticFiles(context.assets)) }
        settings.settings.first()
        return LiveParty(host, server, partyId)
    }
}
