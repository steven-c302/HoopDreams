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
import partyos.engine.TeamAnswer
import partyos.engine.TeamGuess
import partyos.engine.TeamTag
import partyos.engine.TriviaReveal
import partyos.engine.TriviaTeam
import partyos.engine.TriviaTv
import partyos.engine.DrinkCall
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
    private val avatar = Avatar("p:01", "#FF7A00")
    private val me = PlayerSummary(sam, "Sam", avatar, Role.PLAYER, true)
    private val rows = listOf(ScoreRow(sam, "Sam", avatar, 1500), ScoreRow(PlayerId("p-al"), "Al", Avatar("p:02", "#22AA55"), 500))

    private val team = TeamTag("T1", "Quizzards", "#FF4B3E")

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
        view(
            Screen.ChoiceList(
                "Which planet has the most known moons?", listOf(Choice("a", "Saturn"), Choice("b", "Jupiter")), "a", "answer",
                style = "shapes", votes = mapOf("a" to listOf(sam)), team = team,
            ),
        ),
        view(Screen.ChoiceList("Pick a team", listOf(Choice("T1", "Quizzards", "#FF4B3E", "2 in")), "T1", "team", style = "teams", team = team)),
        view(Screen.NumberEntry("How many bones are in the adult human body?", "bones", 206.0, "guess", listOf(TeamGuess(PlayerId("p-al"), 180.0)), team)),
        view(
            Screen.MultiSelect(
                "Which of these are Great Lakes?", listOf(Choice("a", "Huron"), Choice("b", "Erie"), Choice("c", "Champlain")),
                listOf("a"), false, "multi", eliminated = listOf("c"), votes = mapOf("a" to listOf(sam)), team = team,
            ),
        ),
        view(Screen.Waiting("Correct!", "+1250", "win", team)),
        ServerMsg.Tv(
            13,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "trivia", "Brain Drain", 9, 1_700_000_068_000, 8_000, false, null, null,
                    game = TriviaTv(
                        phase = "reveal", format = "quick", round = 1, totalRounds = 5, q = 2, qTotal = 5, durationMs = 8_000,
                        prompt = "Which planet has the most known moons?", category = "Science",
                        options = listOf(Choice("a", "Saturn"), Choice("b", "Jupiter")),
                        teams = listOf(TriviaTeam("T1", "Quizzards", "#FF4B3E", listOf(sam), 1250)),
                        reveal = TriviaReveal(listOf("a"), "Saturn", answers = listOf(TeamAnswer("T1", choice = "a", correct = true, points = 1250, seconds = 3.5))),
                        drink = DrinkCall(listOf("T2"), 1, "last place"),
                        hostLine = "Only Quizzards knew that.",
                        fact = "Saturn's count shot past 200 in 2025.",
                    ),
                ),
                scores = rows,
                lastResult = null,
                gamesPlayed = 1,
                captain = sam,
                settings = mapOf("rounds" to 5, "teams" to 0, "drinks" to 1, "game" to 0),
            ),
        ),
        ServerMsg.View(
            seq = 14,
            view = PhoneState(
                me, "KXQT", null, null, 0, false, null, null, Screen.Waiting("You're in!", "You have the crown: pick a game"), emptyList(),
                captain = true, captainName = "Sam", settings = mapOf("game" to 0), crew = listOf(me),
            ),
        ),
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
        ClientMsg.Host("h-8", HostCommand.Start("trivia", 5, mapOf("teams" to 4, "drinks" to 1))),
        ClientMsg.Host("h-9", HostCommand.SetOption("game", 1)),
        ClientMsg.Host("h-10", HostCommand.MakeCaptain(PlayerId("p-al"))),
        ClientMsg.Host("h-11", HostCommand.GameAction("shuffle")),
        ClientMsg.Action("a-4", 9, JsonObject(mapOf("kind" to JsonPrimitive("guess"), "value" to JsonPrimitive(206)))),
        ClientMsg.Action(
            "a-5", 10,
            JsonObject(mapOf("kind" to JsonPrimitive("multi"), "picks" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("a"))), "lock" to JsonPrimitive(true))),
        ),
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
