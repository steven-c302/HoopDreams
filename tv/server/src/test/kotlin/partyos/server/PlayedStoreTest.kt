package partyos.server

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayedStoreTest {
    private val dir = Files.createTempDirectory("played").toFile()

    @Test fun savesAndLoadsInOrder() {
        val file = File(dir, "sub/played.json")
        val store = PlayedStore(file)
        assertEquals(emptyList(), store.load()) // nothing played yet
        store.save(listOf("tmc003", "tbp001", "tmc001"))
        assertEquals(listOf("tmc003", "tbp001", "tmc001"), PlayedStore(file).load())
        assertFalse(File(file.path + ".tmp").exists())
        assertTrue(file.readText().contains("\"version\":1")) // so a future format can tell old files apart
    }

    @Test fun anUnreadableFileIsSetAsideNotLost() {
        val file = File(dir, "played.json").apply { writeText("{ not json") }
        val errors = mutableListOf<String>()
        assertEquals(emptyList(), PlayedStore(file) { errors += it }.load())
        assertTrue(File(file.path + ".bad").readText().startsWith("{ not json"))
        assertEquals(1, errors.size)
    }
}
