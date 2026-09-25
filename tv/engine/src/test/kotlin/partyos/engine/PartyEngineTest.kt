package partyos.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartyEngineTest {
    private val clock = FakeClock(1_000)
    private val avatar = Avatar("🦊", "#FF7A00")
    private fun engine() = PartyEngine(clock, SecureEntropy())

    private fun PartyEngine.joinOk(name: String, role: Role = Role.PLAYER): JoinResult.Joined =
        assertIs<JoinResult.Joined>(join(roomCode, name, avatar, role))

    @Test fun roomCodeIsFourUnambiguousLetters() {
        repeat(50) {
            val code = engine().roomCode
            assertTrue(code.matches(Regex("[A-Z]{4}")), code)
            assertTrue(code.none { it in "ILO" }, code)
        }
    }

    @Test fun wrongRoomIsRejected() {
        val e = engine()
        val wrong = if (e.roomCode == "ABCD") "ABCE" else "ABCD"
        assertEquals(JoinResult.Failed(JoinError.WRONG_ROOM), e.join(wrong, "Sam", avatar, Role.PLAYER))
    }

    @Test fun roomCodeMatchIsCaseInsensitive() {
        val e = engine()
        assertIs<JoinResult.Joined>(e.join(e.roomCode.lowercase(), "Sam", avatar, Role.PLAYER))
    }

    @Test fun seventeenthPlayerIsRejectedButSpectatorsStillFit() {
        val e = engine()
        repeat(16) { e.joinOk("P$it") }
        assertEquals(JoinResult.Failed(JoinError.FULL), e.join(e.roomCode, "P16", avatar, Role.PLAYER))
        repeat(16) { e.joinOk("S$it", Role.SPECTATOR) }
        assertEquals(JoinResult.Failed(JoinError.FULL), e.join(e.roomCode, "S16", avatar, Role.SPECTATOR))
    }

    @Test fun namesAreTrimmedAndUniqueIgnoringCase() {
        val e = engine()
        assertEquals("Sam", e.joinOk("  Sam  ").player.name)
        assertEquals(JoinResult.Failed(JoinError.NAME_TAKEN), e.join(e.roomCode, "sAM", avatar, Role.SPECTATOR))
    }

    @Test fun blankTooLongOrControlCharNamesAreRejected() {
        val e = engine()
        for (bad in listOf("", "   ", "x".repeat(17), "a\u0000b", "new\nline")) {
            assertEquals(JoinResult.Failed(JoinError.BAD_NAME), e.join(e.roomCode, bad, avatar, Role.PLAYER), bad)
        }
        assertIs<JoinResult.Joined>(e.join(e.roomCode, "x".repeat(16), avatar, Role.PLAYER))
    }

    @Test fun tokenResolvesToPlayer() {
        val e = engine()
        val j = e.joinOk("Sam")
        assertEquals(j.player.id, e.resolve(j.token))
        assertNull(e.resolve("nope"))
    }

    @Test fun snapshotStoresOnlyTokenHashes() {
        val e = engine()
        val j = e.joinOk("Sam")
        val json = Json.encodeToString(PartySnapshot.serializer(), e.snapshot())
        assertFalse(j.token in json)
    }

    @Test fun tokenResolvesAfterRestore() {
        val e = engine()
        val j = e.joinOk("Sam")
        val restored = PartyEngine.restore(e.snapshot(), clock, SecureEntropy())
        assertEquals(j.player.id, restored.resolve(j.token))
        assertEquals(e.roomCode, restored.roomCode)
        assertEquals(listOf("Sam"), restored.players.map { it.name })
    }

    @Test fun kickedPlayerTokenNoLongerResolvesAndNameFreesUp() {
        val e = engine()
        val j = e.joinOk("Sam")
        e.kick(j.player.id)
        assertNull(e.resolve(j.token))
        assertIs<JoinResult.Joined>(e.join(e.roomCode, "Sam", avatar, Role.PLAYER))
    }

    @Test fun pinChecksAgainstStoredHash() {
        val e = engine()
        e.setPin("4821")
        assertTrue(e.checkPin("4821"))
        assertFalse(e.checkPin("0000"))
        assertFalse("4821" in Json.encodeToString(PartySnapshot.serializer(), e.snapshot()))
        assertTrue(PartyEngine.restore(e.snapshot(), clock, SecureEntropy()).checkPin("4821"))
    }

    @Test fun presenceIsTrackedPerPlayer() {
        val e = engine()
        val j = e.joinOk("Sam")
        assertFalse(e.player(j.player.id)!!.connected)
        e.setPresence(j.player.id, true)
        assertTrue(e.player(j.player.id)!!.connected)
    }
}
