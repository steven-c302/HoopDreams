package com.partyos.tv.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import partyos.engine.ActionResult
import partyos.engine.Avatar
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.JoinResult
import partyos.engine.PartyEngine
import partyos.engine.Role
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.bluff.BluffBattle

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PartyStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PartyDb::class.java)
        .allowMainThreadQueries().build()
    private val store = PartyStore(db)
    private val games = GameRegistry(listOf(BluffBattle()))

    @After fun close() = db.close()

    private fun engineWithGame(): PartyEngine {
        val e = PartyEngine(SystemClock, SecureEntropy(), games)
        listOf("Ava", "Ben", "Cy").forEach {
            val j = e.join(e.roomCode, it, Avatar("🙂", "#112233"), Role.PLAYER) as JoinResult.Joined
            e.setPresence(j.player.id, true)
        }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("bluff")))
        e.host(HostCmd.SkipPhase)
        return e
    }

    @Test fun noActivePartyAtFirstLaunch() = runBlocking { assertNull(store.loadActive()) }

    @Test fun snapshotRoundTrips() = runBlocking {
        val e = engineWithGame()
        val id = store.create(e.snapshot())
        val saved = e.snapshot()
        store.save(id, saved)
        val (loadedId, snap) = store.loadActive()!!
        assertEquals(id, loadedId)
        assertEquals(saved, snap)
    }

    @Test fun restoredMidRoundPartyIsPaused() = runBlocking {
        val e = engineWithGame()
        store.create(e.snapshot())
        val (_, snap) = store.loadActive()!!
        val restored = PartyEngine.restore(snap, SystemClock, SecureEntropy(), games)
        val stage = restored.tvState().stage!!
        assertTrue(stage.paused)
        assertEquals("RESTORED", stage.pauseReason)
        assertEquals(e.roomCode, restored.roomCode)
    }

    @Test fun endedPartyIsNotRestoredAndFinishedGamesAreKept() = runBlocking {
        val e = engineWithGame()
        val id = store.create(e.snapshot())
        e.host(HostCmd.EndGame)
        store.save(id, e.snapshot())
        assertEquals(1, store.results(id).size)
        store.end(id)
        assertNull(store.loadActive())
        assertEquals("bluff", store.results(id).single().gameId)
    }
}
