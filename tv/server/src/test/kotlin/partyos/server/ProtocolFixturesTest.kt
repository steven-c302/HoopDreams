package partyos.server

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import partyos.engine.Avatar
import partyos.engine.BluffDelta
import partyos.engine.BluffReveal
import partyos.engine.BluffTv
import partyos.engine.Choice
import partyos.engine.GameResult
import partyos.engine.PhoneState
import partyos.engine.PlayerId
import partyos.engine.PlayerSummary
import partyos.engine.Role
import partyos.engine.ScoreRow
import partyos.engine.Screen
import partyos.engine.StageInfo
import partyos.engine.TutorialCard
import partyos.engine.TutorialView
import partyos.engine.TvState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden wire samples shared with the phone controller (controller/src/protocol/fixtures).
 * The controller's Vitest suite parses every server sample and must produce every client sample byte-for-byte,
 * so a protocol change on either side fails CI. Regenerate with: ./gradlew :server:test -PupdateFixtures
 */
class ProtocolFixturesTest {
    private val pretty = Json(PartyJson) { prettyPrint = true }
    private val sam = PlayerId("p-sam")
    private val avatar = Avatar("🦊", "#FF7A00")
    private val me = PlayerSummary(sam, "Sam", avatar, Role.PLAYER, true)
    private val rows = listOf(ScoreRow(sam, "Sam", avatar, 1500), ScoreRow(PlayerId("p-al"), "Al", Avatar("🐸", "#22AA55"), 500))

    private fun view(screen: Screen, round: Int = 3) = ServerMsg.View(
        seq = 12, view = PhoneState(me, "KXQT", "bluff", "Bluff Battle", round, false, null, 42_000, screen, rows),
    )

    private val serverMessages: List<ServerMsg> = listOf(
        ServerMsg.Welcome(sam, Role.PLAYER, host = false),
        ServerMsg.Welcome(null, null, host = true),
        view(Screen.Waiting("You're in!", "Waiting for the host to pick a game"), round = 0),
        view(Screen.Tutorial(listOf(TutorialCard("Write a fake", "Type a believable fake answer.")), acknowledged = false)),
        view(Screen.TextEntry("Wombat poop is shaped like ____.", 60, null, "write", "Write a believable fake answer")),
        view(Screen.ChoiceList("Which one is the truth?", listOf(Choice("o1", "cubes"), Choice("o2", "stars")), "o2", "pick")),
        view(Screen.Scores("Final scores", rows)),
        ServerMsg.Tv(
            12,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "bluff", "Bluff Battle", 5, 1_700_000_060_000, 30_000, false, null,
                    tutorial = TutorialView(listOf(TutorialCard("Score", "+1000 for the truth")), listOf(sam)),
                    game = BluffTv(
                        "reveal", 2, 5, false, "Wombat poop is shaped like ____.", 3, 3,
                        reveal = listOf(BluffReveal("stars", "fake", listOf("Al"), listOf("Sam")), BluffReveal("cubes", "truth", emptyList(), emptyList())),
                        deltas = listOf(BluffDelta(PlayerId("p-al"), "Al", 500)),
                    ),
                ),
                scores = rows,
                lastResult = GameResult("bluff", "Bluff Battle", 1_700_000_000_000, rows, listOf("Al fooled 3 people with “stars”")),
                gamesPlayed = 1,
            ),
        ),
        ServerMsg.Ack("a-1"),
        ServerMsg.Reject("a-2", "TOO_TRUE"),
        ServerMsg.Pong,
        ServerMsg.Bye("KICKED"),
    )

    private val clientMessages: List<ClientMsg> = listOf(
        ClientMsg.Hello(PROTOCOL_VERSION),
        ClientMsg.Action("a-1", 3, JsonObject(mapOf("kind" to JsonPrimitive("write"), "text" to JsonPrimitive("stars")))),
        ClientMsg.Action("a-2", 4, JsonObject(mapOf("kind" to JsonPrimitive("pick"), "option" to JsonPrimitive("o2")))),
        ClientMsg.Action("a-3", 1, JsonObject(mapOf("kind" to JsonPrimitive("ack")))),
        ClientMsg.Host("h-1", HostCommand.Start("bluff", 5)),
        ClientMsg.Host("h-2", HostCommand.Pause),
        ClientMsg.Host("h-3", HostCommand.Resume),
        ClientMsg.Host("h-4", HostCommand.Skip),
        ClientMsg.Host("h-5", HostCommand.End),
        ClientMsg.Host("h-6", HostCommand.Kick(PlayerId("p-al"))),
        ClientMsg.Host("h-7", HostCommand.SetRounds(6)),
        ClientMsg.Ping,
    )

    @Test fun serverFixturesMatch() = check("server-messages.json", pretty.encodeToString(ListSerializer(ServerMsg.serializer()), serverMessages))

    @Test fun clientFixturesMatch() = check("client-messages.json", pretty.encodeToString(ListSerializer(ClientMsg.serializer()), clientMessages))

    @Test fun fixturesRoundTrip() {
        val text = PartyJson.encodeToString(ListSerializer(ServerMsg.serializer()), serverMessages)
        assertEquals(serverMessages, PartyJson.decodeFromString(ListSerializer(ServerMsg.serializer()), text))
    }

    private fun check(name: String, actual: String) {
        val dir = File(requireNotNull(System.getProperty("fixturesDir")))
        val file = File(dir, name)
        if (!file.exists() || System.getProperty("updateFixtures") == "true") {
            dir.mkdirs()
            file.writeText(actual + "\n")
        }
        assertEquals(file.readText().trimEnd(), actual, "$name is stale; rerun with -PupdateFixtures and update the controller")
    }
}
