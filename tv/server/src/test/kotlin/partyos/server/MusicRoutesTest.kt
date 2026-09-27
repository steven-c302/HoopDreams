package partyos.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MusicRoutesTest {
    private class FakePlayer : MusicPlayer {
        val sent = mutableListOf<String>()
        var playing = false
        override fun status() = MusicStatus(available = true, running = true, playing = playing, track = "Mr. Brightside", artist = "The Killers")
        override fun command(cmd: String): Boolean {
            sent += cmd
            playing = cmd == "play" || (cmd == "playpause" && !playing)
            return true
        }
    }

    @Test fun theTvReadsWhatsPlayingAndSteersIt() = testApplication {
        val player = FakePlayer()
        serve(newHost(), ServerConfig(music = player))
        val before = PartyJson.decodeFromString<MusicStatus>(client.get("/api/music").bodyAsText())
        assertEquals("Mr. Brightside", before.track)
        assertFalse(before.playing)

        val after = client.post("/api/music/play")
        assertEquals(200, after.status.value)
        assertTrue(PartyJson.decodeFromString<MusicStatus>(after.bodyAsText()).playing)
        assertEquals(200, client.post("/api/music/next").status.value)
        assertEquals(listOf("play", "next"), player.sent)
    }

    @Test fun unknownCommandsNeverReachThePlayer() = testApplication {
        val player = FakePlayer()
        serve(newHost(), ServerConfig(music = player))
        assertEquals(400, client.post("/api/music/quit").status.value)
        assertEquals(400, client.post("/api/music/do%20shell%20script").status.value)
        assertTrue(player.sent.isEmpty())
    }

    @Test fun withNoPlayerTheTvSeesItsUnavailable() = testApplication {
        serve(newHost())
        assertFalse(PartyJson.decodeFromString<MusicStatus>(client.get("/api/music").bodyAsText()).available)
        assertEquals(404, client.post("/api/music/play").status.value)
    }
}
