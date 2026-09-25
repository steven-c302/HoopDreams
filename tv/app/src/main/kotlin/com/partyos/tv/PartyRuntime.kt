package com.partyos.tv

import android.content.Context
import androidx.room.Room
import com.partyos.tv.data.PartyDb
import com.partyos.tv.data.PartyStore
import com.partyos.tv.net.NetworkAddressMonitor
import com.partyos.tv.net.advertisedUrl
import com.partyos.tv.settings.SettingsRepo
import kotlinx.coroutines.CoroutineScope
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

/** One running party: its engine host, HTTP server and database row. */
class LiveParty(val host: PartyHost, val server: PartyServer, val partyId: Long)

/**
 * App-wide owner of the party: restores it from Room, runs the server, and exposes what the TV UI shows.
 * Started by [com.partyos.tv.service.PartyService] so it survives the activity going to the background.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PartyRuntime(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
        network.start()
        val pin = settings.ensurePin()
        val restored = store.loadActive()
        val engine = restored?.let { PartyEngine.restore(it.second, SystemClock, SecureEntropy(), games) }
            ?: PartyEngine(SystemClock, SecureEntropy(), games)
        engine.setPin(pin)
        val partyId = restored?.first ?: store.create(engine.snapshot())
        _live.value = launch(engine, partyId)
    }

    /** Ends the current party (kept in history) and opens a fresh one with a new room code. */
    suspend fun newParty() = lock.withLock {
        _live.value?.let { old ->
            withContext(Dispatchers.IO) { old.server.stop() }
            store.end(old.partyId)
        }
        val engine = PartyEngine(SystemClock, SecureEntropy(), games)
        engine.setPin(settings.ensurePin())
        _live.value = launch(engine, store.create(engine.snapshot()))
    }

    suspend fun changePin(pin: String) {
        settings.setPin(pin)
        _live.value?.host?.mutate { setPin(pin) }
    }

    fun stop() {
        _live.value?.server?.stop()
        _live.value = null
        network.stop()
    }

    private suspend fun launch(engine: PartyEngine, partyId: Long): LiveParty {
        val host = PartyHost(engine, SystemClock, scope, onCommit = { store.save(partyId, it) })
        val server = withContext(Dispatchers.IO) { PartyServer.start(host, AssetStaticFiles(context.assets)) }
        settings.settings.first()
        return LiveParty(host, server, partyId)
    }
}
