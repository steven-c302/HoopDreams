package partyos.engine

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.games.bluff.BluffBattle
import partyos.engine.games.bluff.BluffPack
import partyos.engine.games.bluff.BluffQuestion
import partyos.engine.games.bluff.normalise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestoreHardeningTest {
    private val clock = FakeClock(0)
    private val q = BluffQuestion("q1", "Wombat poop is shaped like ____.", "The Eiffel Tower", emptyList(), listOf("spirals", "stars"))
    private fun bluff(vararg qs: BluffQuestion) = BluffBattle(BluffPack("t", "t", "bluff", 1, qs.toList()))

    private fun running(games: GameRegistry): PartyEngine {
        val e = PartyEngine(clock, SeededEntropy(3), games)
        listOf("A", "B", "C").forEach { e.add(it) }
        e.host(HostCmd.StartGame("bluff"))
        e.host(HostCmd.SkipPhase)
        return e
    }

    @Test fun unreadableGameStateRestoresTheRosterWithoutTheGame() {
        val e = running(GameRegistry(listOf(bluff(q))))
        val snap = e.snapshot()
        val broken = snap.copy(game = snap.game!!.copy(state = buildJsonObject { put("phase", JsonPrimitive(42)) }))
        val r = PartyEngine.restore(broken, clock, SeededEntropy(4), GameRegistry(listOf(bluff(q))))
        assertNull(r.tvState().stage)
        assertEquals(listOf("A", "B", "C"), r.players.map { it.name })
    }

    @Test fun gameWhoseQuestionLeftThePackIsDroppedOnRestore() {
        val e = running(GameRegistry(listOf(bluff(q))))
        val other = q.copy(id = "q2")
        val r = PartyEngine.restore(e.snapshot(), clock, SeededEntropy(4), GameRegistry(listOf(bluff(other))))
        assertNull(r.tvState().stage)
    }

    @Test fun extraFieldsFromANewerVersionDoNotBreakRestore() {
        val e = running(GameRegistry(listOf(bluff(q))))
        val snap = e.snapshot()
        val state = snap.game!!.state as kotlinx.serialization.json.JsonObject
        val newer = snap.copy(game = snap.game!!.copy(state = kotlinx.serialization.json.JsonObject(state + ("futureField" to JsonPrimitive(1)))))
        val r = PartyEngine.restore(newer, clock, SeededEntropy(4), GameRegistry(listOf(bluff(q))))
        assertNotNull(r.tvState().stage)
    }

    @Test fun quotedTruthIsStillTooTrue() {
        assertEquals(normalise("The Eiffel Tower"), normalise("\"The Eiffel Tower\""))
        assertEquals(normalise("the eiffel tower"), normalise("«The Eiffel-Tower!»"))
    }

    @Test fun emojiOnlyFakesDoNotAllMerge() {
        assertNotEquals(normalise("🦄🦄"), normalise("🍕"))
        assertTrue(normalise("🍕").isNotEmpty())
    }

    @Test fun spectatorCanTakeAFreeSeatBetweenGames() {
        val e = PartyEngine(clock, SeededEntropy(5), GameRegistry(listOf(bluff(q))))
        val s = e.add("Watcher", Role.SPECTATOR)
        assertEquals(null, e.setRole(s, Role.PLAYER))
        assertEquals(Role.PLAYER, e.player(s)!!.role)
    }

    @Test fun roleSwitchIsRefusedMidGameOrWhenFull() {
        val e = running(GameRegistry(listOf(bluff(q))))
        val s = e.add("Watcher", Role.SPECTATOR)
        assertEquals("GAME_RUNNING", e.setRole(s, Role.PLAYER))
        e.host(HostCmd.EndGame)
        repeat(13) { e.add("P$it") }
        assertEquals("FULL", e.setRole(s, Role.PLAYER))
    }
}
