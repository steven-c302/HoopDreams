package partyos.server

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import partyos.engine.BoardCell
import partyos.engine.JeopardyCellTv
import partyos.engine.JeopardyDelta
import partyos.engine.JeopardyDrink
import partyos.engine.JeopardyFinalStep
import partyos.engine.JeopardyFinalTv
import partyos.engine.JeopardyTv
import partyos.engine.Avatar
import partyos.engine.BluffDelta
import partyos.engine.BluffReveal
import partyos.engine.BluffTv
import partyos.engine.Choice
import partyos.engine.GameResult
import partyos.engine.ImposterClue
import partyos.engine.ImposterDelta
import partyos.engine.ImposterDrink
import partyos.engine.ImposterGuess
import partyos.engine.ImposterTv
import partyos.engine.ImposterVote
import partyos.engine.SecretInput
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
import partyos.engine.TurfAuctionTv
import partyos.engine.TurfBidPad
import partyos.engine.TurfDeed
import partyos.engine.TurfDeedRef
import partyos.engine.TurfMe
import partyos.engine.TurfPartner
import partyos.engine.TurfPrompt
import partyos.engine.TurfSpaceTv
import partyos.engine.TurfTokenTv
import partyos.engine.TurfTradeView
import partyos.engine.TurfTv
import partyos.engine.TutorialCard
import partyos.engine.TutorialView
import partyos.engine.TvState
import partyos.engine.SprawlBuild
import partyos.engine.SprawlCard
import partyos.engine.SprawlHarbourTv
import partyos.engine.SprawlHexTv
import partyos.engine.SprawlMapTv
import partyos.engine.SprawlMe
import partyos.engine.SprawlPartner
import partyos.engine.SprawlPrompt
import partyos.engine.SprawlSeatTv
import partyos.engine.SprawlTradeTv
import partyos.engine.SprawlTradeView
import partyos.engine.SprawlTv
import partyos.engine.games.sprawl.SBeat
import partyos.engine.games.turf.TBeat
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

    /** A one-hex island, enough to pin the wire shape. */
    private val island = SprawlMapTv(
        size = 0, hexes = listOf(SprawlHexTv(0, 0, 3, 8, "Izzy's Rooftop")),
        vertices = listOf(listOf(0, -100), listOf(87, -50), listOf(87, 50), listOf(0, 100), listOf(-87, 50), listOf(-87, -50)),
        edges = listOf(listOf(0, 1), listOf(1, 2)), harbours = listOf(SprawlHarbourTv(0, -1)),
        resources = listOf("Brick", "Wood", "Sheep", "Wheat", "Ore"), landlord = "The Landlord",
        dev = mapOf("knight" to "Bouncer"), awards = mapOf("road" to "Longest Road", "army" to "Most Bouncers"),
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
        ServerMsg.View(
            seq = 15,
            view = PhoneState(
                me, "KXQT", "turf", "Home Turf", 21, false, null, 14_000,
                Screen.Turf(
                    me = TurfMe(0, "Sam", "#FF4B3E", "duck", 1240, sam, "Sam", true, false, 0, false, 3, "The Laundromat", 1600),
                    prompt = TurfPrompt("buy", "Buy The Laundromat?", "Or pass and everyone bids on it.",
                        listOf(Choice("buy", "BUY $60"), Choice("pass", "AUCTION IT")), timed = true, space = 3, amount = 60),
                    deeds = listOf(TurfDeed(1, "The Corner Store", "#8B5A2B", 0, 0, false, 2, mortgage = 30, tradable = true)),
                    partners = listOf(TurfPartner(1, "Al", "#2F6BFF", 900, 0, listOf(TurfDeedRef(5, "The Night Bus", "#2B2B2B", 8, false, true)))),
                    trade = TurfTradeView(4, 1, 0, "Al", "Sam", listOf(TurfDeedRef(5, "The Night Bus", "#2B2B2B", 8, false, true)), emptyList(),
                        0, 50, 0, 0, "to", true),
                    canTrade = true,
                    auction = TurfBidPad(3, 3, "The Laundromat", "#8B5A2B", 60, 20, "Al", false, 1240, true),
                    drink = "Drink 1 sip: paid rent at The Corner Store",
                ),
                rows,
            ),
        ),
        ServerMsg.Tv(
            16,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "turf", "Home Turf", 21, 1_700_000_080_000, 14_000, false, null, null,
                    game = TurfTv(
                        phase = "auction", teams = false,
                        board = listOf(TurfSpaceTv("Payday", "Payday", "payday"), TurfSpaceTv("The Corner Store", "Corner Store", "street", 0, "#8B5A2B", 60, listOf(2, 10, 30, 90, 160, 250), 50)),
                        chanceName = "Plot Twist", chestName = "Group Chat",
                        owner = listOf(-1, 0), level = listOf(0, 0), mortgaged = emptyList(),
                        tokens = listOf(TurfTokenTv("Sam", "#FF4B3E", "duck", listOf(sam), sam, 1240, 3, false, 0, false, 1600, 0)),
                        turn = 0, dice = listOf(1, 2, 3), housesLeft = 32, hotelsLeft = 12, buy = -1,
                        auction = TurfAuctionTv(3, 3, 20, 1, 2),
                        clockLeftMs = 2_400_000, phaseMs = 6_000, timed = true,
                        beats = listOf(TBeat(40, "bid", token = 1, space = 3, amount = 20)),
                        ticker = listOf("The Laundromat goes to auction!"),
                    ),
                ),
                scores = rows,
                lastResult = null,
                gamesPlayed = 2,
                settings = mapOf("game" to 4, "turfMode" to 0, "minutes" to 45, "drinks" to 1),
            ),
        ),
        ServerMsg.View(
            seq = 17,
            view = PhoneState(
                me, "KXQT", "sprawl", "Sprawl", 30, false, null, 52_000,
                Screen.Sprawl(
                    me = SprawlMe(0, "Sam", "#FF4B3E", listOf(1, 1, 0, 2, 0), 3, listOf(SprawlCard("knight", "Bouncer", 1, true)), listOf(13, 3, 4), listOf(4, 4, 3, 4, 4), true),
                    prompt = SprawlPrompt("main", "Build, trade, or end your turn", "You rolled 8.", listOf(Choice("end", "END TURN")), timed = true),
                    map = island, robber = 0, vOwner = listOf(0, -1, -1, 1, -1, -1), vLevel = listOf(1, 0, 0, 2, 0, 0), eOwner = listOf(0, -1),
                    colors = listOf("#FF4B3E", "#2F6BFF"),
                    build = SprawlBuild(roads = listOf(1), dev = false),
                    partners = listOf(SprawlPartner(1, "Al", "#2F6BFF", 4)),
                    trade = SprawlTradeView(5, 1, 0, "Al", "Sam", listOf(0, 0, 0, 1, 0), listOf(1, 0, 0, 0, 0), "to", true, true),
                    canTrade = false, bank = listOf(18, 18, 19, 17, 19),
                    drink = "Drink 1 sip: got robbed",
                ),
                rows,
            ),
        ),
        ServerMsg.Tv(
            18,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "sprawl", "Sprawl", 30, 1_700_000_090_000, 52_000, false, null, null,
                    game = SprawlTv(
                        phase = "trade", map = island, robber = 0, vOwner = listOf(0, -1, -1, 1, -1, -1), vLevel = listOf(1, 0, 0, 2, 0, 0), eOwner = listOf(0, -1),
                        seats = listOf(SprawlSeatTv("Sam", "#FF4B3E", sam, 4, 1, 3, 1, 1, false, false, false)),
                        turn = 0, dice = listOf(3, 5), trade = SprawlTradeTv(5, 1, 0, listOf(0, 0, 0, 1, 0), listOf(1, 0, 0, 0, 0), 1),
                        bank = listOf(18, 18, 19, 17, 19), deckLeft = 20, clockLeftMs = 2_000_000, phaseMs = 30_000, timed = true, vpTarget = 8,
                        beats = listOf(SBeat(12, "harvest", seat = 0, amount = 8, targets = listOf(0), gains = listOf(listOf(0, 0, 0, 1, 0)))),
                        ticker = listOf("Al countered"),
                    ),
                ),
                scores = rows,
                lastResult = null,
                gamesPlayed = 3,
                settings = mapOf("game" to 5, "vp" to 8, "minutes" to 45, "drinks" to 1),
            ),
        ),
        ServerMsg.View(
            seq = 20,
            view = PhoneState(
                me, "KXQT", "imposter", "Imposter", 2, false, null, 15_000,
                Screen.Secret("Round 1", "PIZZA", "Food", "crew", "Don't let the imposter find out the word.", "seen", false),
                rows,
            ),
        ),
        ServerMsg.View(
            seq = 21,
            view = PhoneState(
                me, "KXQT", "imposter", "Imposter", 3, false, null, 45_000,
                Screen.Secret(
                    "Round 1", "IMPOSTER", "Food", "imposter", null, null, false,
                    SecretInput("One word that fits Food", 20, "cheesy", "clue", "Locked in. You can still change it until time's up."),
                ),
                rows,
            ),
        ),
        ServerMsg.View(
            seq = 22,
            view = PhoneState(
                me, "KXQT", "imposter", "Imposter", 5, false, null, 30_000,
                Screen.ChoiceList("Who is the imposter?", listOf(Choice("p-al", "Al"), Choice("p-bo", "Bo")), "p-al", "vote", style = "faces"),
                rows,
            ),
        ),
        ServerMsg.Tv(
            23,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "imposter", "Imposter", 6, 1_700_000_100_000, 6_000, false, null, null,
                    game = ImposterTv(
                        phase = "result", round = 2, totalRounds = 5, finalRound = false, category = "Food",
                        submitted = 4, expected = 4, imposterCount = 1,
                        clues = listOf(ImposterClue(PlayerId("p-al"), "Al", "cheesy"), ImposterClue(sam, "Sam", null)),
                        imposters = listOf(PlayerId("p-al")), accused = listOf(PlayerId("p-al")),
                        votes = listOf(ImposterVote(sam, PlayerId("p-al"))),
                        drinks = listOf(ImposterDrink(PlayerId("p-al"), "Al", 2, "Caught! Drink 2 sips")),
                    ),
                ),
                scores = rows,
                lastResult = null,
                gamesPlayed = 1,
                settings = mapOf("game" to 6, "rounds" to 5, "drinks" to 1),
            ),
        ),
        ServerMsg.Tv(
            24,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "imposter", "Imposter", 8, 1_700_000_110_000, 8_000, false, null, null,
                    game = ImposterTv(
                        phase = "scores", round = 2, totalRounds = 5, finalRound = false, category = "Food",
                        submitted = 0, expected = 4, imposterCount = 1, word = "pizza",
                        guesses = listOf(ImposterGuess(PlayerId("p-al"), "Al", "pizzza", true)),
                        deltas = listOf(ImposterDelta(PlayerId("p-al"), "Al", 1000)),
                    ),
                ),
                scores = rows,
                lastResult = null,
                gamesPlayed = 1,
            ),
        ),
        ServerMsg.View(
            seq = 30,
            view = PhoneState(
                me, "KXQT", "jeopardy", "Answer & Question", 4, false, null, 20_000,
                Screen.Board(
                    "Pick a clue", listOf("Food & Drink", "Sports"),
                    listOf(BoardCell("food-200", 0, 0, 200, false), BoardCell("sports-200", 1, 0, 200, true)),
                    canPick = true,
                ),
                rows,
            ),
        ),
        ServerMsg.View(
            seq = 31,
            view = PhoneState(
                me, "KXQT", "jeopardy", "Answer & Question", 6, false, null, 10_000,
                Screen.Buzzer("locked", "Sports", 400, "Too early! Hold on...", lockedMs = 600, live = true),
                rows,
            ),
        ),
        ServerMsg.Tv(
            32,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "jeopardy", "Answer & Question", 7, 1_700_000_200_000, 5_000, false, null, null,
                    game = JeopardyTv(
                        phase = "reveal", round = 1, boards = 2,
                        categories = listOf("Food & Drink", "Sports"),
                        cells = listOf(JeopardyCellTv("food-200", 0, 0, 200, true), JeopardyCellTv("sports-200", 1, 0, 200, false)),
                        controller = sam, category = "Food & Drink", value = 200, clue = "This spread is made by mashing avocados.",
                        floor = sam, tried = listOf(PlayerId("p-al")), answer = "Guacamole", right = true,
                        deltas = listOf(JeopardyDelta(sam, "Sam", 200)),
                        drinks = listOf(JeopardyDrink(PlayerId("p-al"), "Al", 1, "Drink 1 sip")),
                    ),
                ),
                scores = rows,
                lastResult = null,
                gamesPlayed = 1,
                settings = mapOf("game" to 6, "show" to 1, "drinks" to 1),
            ),
        ),
        ServerMsg.Tv(
            33,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "jeopardy", "Answer & Question", 12, 1_700_000_260_000, 5_000, false, null, null,
                    game = JeopardyTv(
                        phase = "final_reveal", round = 3, boards = 2,
                        final = JeopardyFinalTv(
                            "World Capitals", "This capital on the Danube...", "Budapest", 2, 2,
                            listOf(JeopardyFinalStep(sam, "Sam", "Budapest", 400, true, 400, 1600)),
                        ),
                    ),
                ),
                scores = rows,
                lastResult = null,
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
        ClientMsg.Host("h-12", HostCommand.SetOption("turfMode", 2)),
        ClientMsg.Host("h-13", HostCommand.SetOption("minutes", 45)),
        ClientMsg.Action("a-6", 21, JsonObject(mapOf("kind" to JsonPrimitive("bid"), "auction" to JsonPrimitive(3), "amount" to JsonPrimitive(30)))),
        ClientMsg.Action("a-7", 21, JsonObject(mapOf("kind" to JsonPrimitive("build"), "target" to JsonPrimitive(1)))),
        ClientMsg.Action(
            "a-8", 20,
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("trade"), "to" to JsonPrimitive(1),
                    "give" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("1"))), "get" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("5"))),
                    "giveCash" to JsonPrimitive(50), "getCash" to JsonPrimitive(0),
                ),
            ),
        ),
        ClientMsg.Host("h-14", HostCommand.SetOption("vp", 10)),
        ClientMsg.Action("a-9", 30, JsonObject(mapOf("kind" to JsonPrimitive("build"), "what" to JsonPrimitive("road"), "target" to JsonPrimitive(12)))),
        ClientMsg.Action(
            "a-10", 30,
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("trade"), "to" to JsonPrimitive(-2),
                    "give" to kotlinx.serialization.json.JsonArray(listOf(1, 0, 0, 0, 0).map { JsonPrimitive(it) }),
                    "get" to kotlinx.serialization.json.JsonArray(listOf(0, 0, 0, 1, 0).map { JsonPrimitive(it) }),
                ),
            ),
        ),
        ClientMsg.Action("a-11", 2, JsonObject(mapOf("kind" to JsonPrimitive("seen")))),
        ClientMsg.Action("a-12", 3, JsonObject(mapOf("kind" to JsonPrimitive("clue"), "text" to JsonPrimitive("cheesy")))),
        ClientMsg.Action("a-13", 5, JsonObject(mapOf("kind" to JsonPrimitive("vote"), "option" to JsonPrimitive("p-al")))),
        ClientMsg.Action("a-14", 4, JsonObject(mapOf("kind" to JsonPrimitive("pick"), "cell" to JsonPrimitive("food-200")))),
        ClientMsg.Action("a-15", 6, JsonObject(mapOf("kind" to JsonPrimitive("buzz")))),
        ClientMsg.Action("a-16", 8, JsonObject(mapOf("kind" to JsonPrimitive("wager"), "value" to JsonPrimitive(300)))),
        ClientMsg.Host("h-15", HostCommand.SetOption("show", 1)),
        ClientMsg.Host("h-16", HostCommand.GameAction("pick:food-200")),
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
