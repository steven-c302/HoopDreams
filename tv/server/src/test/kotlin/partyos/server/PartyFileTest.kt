package partyos.server

import partyos.engine.Avatar
import partyos.engine.JoinResult
import partyos.engine.PartyEngine
import partyos.engine.Role
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartyFileTest {
    private val dir = Files.createTempDirectory("party").toFile()

    private fun partyOf(vararg names: String) = PartyEngine(SystemClock, SecureEntropy()).apply {
        names.forEach { assertTrue(join(roomCode, it, Avatar(color = "#FF0000"), Role.PLAYER) is JoinResult.Joined) }
    }

    @Test fun aRestartedServerGetsTheSamePartyBack() {
        val file = File(dir, "sub/party.json")
        val engine = partyOf("Ana", "Ben")
        PartyFile(file).save(engine.snapshot())
        val back = PartyFile(file).load(maxAgeMs = 60_000)!!
        assertEquals(engine.roomCode, back.roomCode)
        assertEquals(listOf("Ana", "Ben"), back.players.map { it.name })
        assertEquals(engine.snapshot().tokenHashes, back.tokenHashes) // phones rejoin with the token they already hold
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test fun anOldPartyIsADifferentNight() {
        val file = File(dir, "party.json")
        PartyFile(file).save(partyOf("Ana").snapshot())
        assertNull(PartyFile(file).load(maxAgeMs = 60_000, now = file.lastModified() + 60_001))
        assertTrue(file.exists()) // too old is not broken: it's left alone
    }

    @Test fun noFileMeansAFreshParty() {
        assertNull(PartyFile(File(dir, "missing.json")).load(maxAgeMs = 60_000))
    }

    @Test fun anUnreadableFileIsSetAside() {
        val file = File(dir, "bad.json").apply { writeText("{ not json") }
        val errors = mutableListOf<String>()
        assertNull(PartyFile(file) { errors += it }.load(maxAgeMs = 60_000))
        assertTrue(File(file.path + ".bad").readText().startsWith("{ not json"))
        assertEquals(1, errors.size)
    }
}
