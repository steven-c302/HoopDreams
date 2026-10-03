package partyos.server

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import partyos.engine.Avatar
import partyos.engine.BetInfo
import partyos.engine.BetOption
import partyos.engine.BetResult
import partyos.engine.BluffDelta
import partyos.engine.BluffReveal
import partyos.engine.BluffTv
import partyos.engine.Choice
import partyos.engine.FinaleInfo
import partyos.engine.FinaleResult
import partyos.engine.GameResult
import partyos.engine.NightAward
import partyos.engine.NightMoment
import partyos.engine.NightPlayer
import partyos.engine.NightRecap
import partyos.engine.NightRow
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
import partyos.engine.DoodleMissTv
import partyos.engine.DoodleSolver
import partyos.engine.DoodleTv
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
import partyos.engine.BoardCell
import partyos.engine.JeopardyCellTv
import partyos.engine.JeopardyDelta
import partyos.engine.JeopardyDrink
import partyos.engine.JeopardyFinalStep
import partyos.engine.JeopardyFinalTv
import partyos.engine.JeopardyTv
import partyos.engine.HotTypeTv
import partyos.engine.HuntBigFind
import partyos.engine.HuntDelta
import partyos.engine.HuntDrink
import partyos.engine.HuntFound
import partyos.engine.HuntMissed
import partyos.engine.HuntPageWord
import partyos.engine.HuntRail

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
        view(Screen.ChoiceList("Wager on Geography. Question comes after", listOf(Choice("w25", "25%", detail = "1,050"), Choice("wall", "ALL IN", detail = "4,200")), "w25", "finalWager", team = team)),
        view(Screen.ChoiceList("Who's closest? Back a guess", listOf(Choice("T1", "Quizzards", "#FF4B3E", "guess 180 · pays 2×"), Choice("skip", "Skip betting")), null, "bet", style = "teams", team = team)),
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
        ServerMsg.Tv(
            70,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "trivia", "Brain Drain", 9, 1_700_000_068_000, 8_000, false, null, null,
                    game = TriviaTv(
                        phase = "bet", format = "ballpark", round = 2, totalRounds = 5, q = 1, qTotal = 3, durationMs = 15_000,
                        prompt = "How many bones are in the adult human body?", category = "Body", unit = "bones",
                        teams = listOf(TriviaTeam("T1", "Quizzards", "#FF4B3E", listOf(sam), 1250)),
                        bet = BetInfo(listOf(BetOption("T1", 180.0, 2), BetOption("T2", 206.0, 1)), locked = listOf("T2")),
                        hostLine = "Back a guess. Bigger odds, bigger risk.",
                    ),
                ),
                scores = rows,
                lastResult = null,
                gamesPlayed = 1,
                captain = sam,
                settings = mapOf("rounds" to 5, "teams" to 0, "drinks" to 1, "game" to 0),
            ),
        ),
        ServerMsg.Tv(
            71,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "trivia", "Brain Drain", 9, 1_700_000_068_000, 8_000, false, null, null,
                    game = TriviaTv(
                        phase = "reveal", format = "ballpark", round = 2, totalRounds = 5, q = 1, qTotal = 3, durationMs = 9_000,
                        prompt = "How many bones are in the adult human body?", category = "Body", unit = "bones",
                        teams = listOf(TriviaTeam("T1", "Quizzards", "#FF4B3E", listOf(sam), 1750)),
                        reveal = TriviaReveal(
                            emptyList(), "206 bones", number = 206.0,
                            answers = listOf(TeamAnswer("T1", number = 206.0, correct = true, points = 1500, rank = 1, bullseye = true, bet = BetResult("T1", 250, 1, true, 250))),
                        ),
                        hostLine = "Quizzards nailed it. Who's googling?",
                    ),
                ),
                scores = rows,
                lastResult = null,
                gamesPlayed = 1,
                captain = sam,
                settings = mapOf("rounds" to 5, "teams" to 0, "drinks" to 1, "game" to 0),
            ),
        ),
        ServerMsg.Tv(
            72,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "trivia", "Brain Drain", 9, 1_700_000_068_000, 8_000, false, null, null,
                    game = TriviaTv(
                        phase = "final_wager", format = "final", round = 5, totalRounds = 5, q = 0, qTotal = 0, durationMs = 20_000,
                        prompt = "",
                        teams = listOf(TriviaTeam("T1", "Quizzards", "#FF4B3E", listOf(sam), 4200)),
                        finale = FinaleInfo(category = "Geography", locked = listOf("T1")),
                        hostLine = "Pick your wager. Nobody sees it until the reveal.",
                    ),
                ),
                scores = rows,
                lastResult = null,
                gamesPlayed = 1,
                captain = sam,
                settings = mapOf("rounds" to 5, "teams" to 0, "drinks" to 1, "game" to 0),
            ),
        ),
        ServerMsg.Tv(
            73,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "trivia", "Brain Drain", 9, 1_700_000_068_000, 8_000, false, null, null,
                    game = TriviaTv(
                        phase = "final_reveal", format = "final", round = 5, totalRounds = 5, q = 0, qTotal = 0, durationMs = 14_000,
                        prompt = "Which river runs through Paris?",
                        teams = listOf(TriviaTeam("T1", "Quizzards", "#FF4B3E", listOf(sam), 4200)),
                        finale = FinaleInfo(
                            category = "Geography", answerText = "The Seine",
                            results = listOf(FinaleResult("T1", "the sein", true, "w50", 2100, 2100, 4200, 6300)),
                        ),
                        hostLine = "Quizzards hold on to win!",
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
        // Between games the lobby also carries the night's recap.
        ServerMsg.Tv(
            50,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = null,
                scores = rows,
                lastResult = GameResult("bluff", "Bluff Battle", 1_700_000_000_000, rows, listOf("Al fooled 3 people with “stars”")),
                gamesPlayed = 1,
                night = NightRecap(
                    games = 1,
                    board = listOf(NightRow(sam, "Sam", avatar, 1, 1), NightRow(PlayerId("p-al"), "Al", Avatar("p:02", "#22AA55"), 0, 0)),
                    awards = listOf(NightAward("NIGHT CHAMP", listOf(NightPlayer(sam, "Sam", avatar)), "1 point")),
                    moments = listOf(NightMoment("Bluff Battle", "Al fooled 3 people with “stars”")),
                ),
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
        view(Screen.Draw("pizza", 2, 1, 4, 45_000, "Draw it! No letters or numbers."), round = 4),
        view(Screen.Guess("Al", "P _ _ _ A", "guess", solved = false, close = true, last = "pizqq", guessed = 1, expected = 4, tailMs = 22_500), round = 4),
        ServerMsg.Ink(1, 7, listOf(InkOp.Start(1, 2, 1, 100, 100, 50), InkOp.Pts(1, listOf(110, 105, 50, 120, 110, 60)), InkOp.End(1), InkOp.Undo, InkOp.Clear)),
        ServerMsg.InkSync(listOf(InkTurn(1, listOf(InkStroke(1, 2, 1, listOf(100, 100, 50, 110, 105, 50), open = false), InkStroke(2, 0, 0, listOf(5, 5, 20), open = true)))), 7),
        ServerMsg.Tv(
            25,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "doodle", "Doodle Dash", 4, 1_700_000_200_000, 30_000, false, null, null,
                    game = DoodleTv(
                        phase = "draw", turn = 1, totalTurns = 5, finalTurn = false, drawer = PlayerId("p-al"), drawerName = "Al", difficulty = 2,
                        blanks = "_ _ _ _ _", guessed = 1, expected = 3, drawMs = 75_000, tailMs = 45_000,
                        solvers = listOf(DoodleSolver(sam, "Sam")),
                        wrong = listOf(DoodleMissTv(PlayerId("p-bo"), "Bo", "pizzq")), missTotal = 3,
                    ),
                ),
                scores = rows,
                lastResult = null,
                gamesPlayed = 0,
                captain = sam,
                settings = mapOf("rounds" to 5, "drinks" to 1),
            ),
        ),
        ServerMsg.Ack("a-1"),
        ServerMsg.Reject("a-2", "TOO_TRUE"),
        ServerMsg.Pong,
        ServerMsg.Bye("KICKED"),
        ServerMsg.View(
            seq = 40,
            view = PhoneState(
                me, "KXQT", "hottype", "Hot Type", 3, false, null, 61_000,
                Screen.Hunt("hunt", 1, 3, 4, "STRPLONAHECIDWKU".map { it.toString() }, listOf(HuntFound("stone", 800), HuntFound("ton", 100)), 900),
                rows,
            ),
        ),
        ServerMsg.View(
            seq = 41,
            view = PhoneState(
                me, "KXQT", "hottype", "Hot Type", 5, false, null, 12_000,
                Screen.Hunt("reveal", 1, 3, 4, "STRPLONAHECIDWKU".map { it.toString() }, listOf(HuntFound("stone", 800, 1, 800), HuntFound("ton", 100, 2, 0)), 2200),
                rows,
            ),
        ),
        ServerMsg.Tv(
            42,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "hottype", "Hot Type", 4, 1_700_000_200_000, 61_000, false, null, null,
                    game = HotTypeTv(
                        phase = "hunt", round = 1, totalRounds = 3, finalRound = false, size = 4,
                        tiles = "STRPLONAHECIDWKU".map { it.toString() },
                        rail = listOf(HuntRail(PlayerId("p-al"), "Al", 2, 900, listOf(5, 3)), HuntRail(sam, "Sam", 0, 0, emptyList())),
                        wordsFound = 2, longest = 5, bigFind = HuntBigFind(1, PlayerId("p-al"), "Al", 6),
                    ),
                ),
                scores = rows,
                lastResult = null,
                gamesPlayed = 1,
                settings = mapOf("game" to 7, "rounds" to 3, "grid" to 0),
            ),
        ),
        ServerMsg.Tv(
            43,
            TvState(
                roomCode = "KXQT",
                players = listOf(me),
                stage = StageInfo(
                    "hottype", "Hot Type", 6, 1_700_000_300_000, 8_000, false, null, null,
                    game = HotTypeTv(
                        phase = "scores", round = 1, totalRounds = 3, finalRound = false, size = 4,
                        tiles = "STRPLONAHECIDWKU".map { it.toString() },
                        page = listOf(HuntPageWord("ton", 100, 0, listOf(PlayerId("p-al"), sam), false), HuntPageWord("stone", 800, 800, listOf(PlayerId("p-al")), true)),
                        missed = HuntMissed("clappers", 2200),
                        deltas = listOf(HuntDelta(PlayerId("p-al"), "Al", 900, 800, 500, 2200), HuntDelta(sam, "Sam", 100, 0, 0, 100)),
                        drinks = listOf(HuntDrink(sam, "Sam", 2, "Last place! Drink 2 sips")),
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
        ClientMsg.Action("a-bet", 11, JsonObject(mapOf("kind" to JsonPrimitive("bet"), "option" to JsonPrimitive("T1")))),
        ClientMsg.Action("a-fw", 12, JsonObject(mapOf("kind" to JsonPrimitive("finalWager"), "option" to JsonPrimitive("w50")))),
        ClientMsg.Action("a-fa", 13, JsonObject(mapOf("kind" to JsonPrimitive("finalAnswer"), "text" to JsonPrimitive("The Seine")))),
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
        ClientMsg.Action("a-17", 4, JsonObject(mapOf("kind" to JsonPrimitive("word"), "path" to kotlinx.serialization.json.JsonArray(listOf(0, 1, 5, 6, 9).map { JsonPrimitive(it) })))),
        ClientMsg.Host("h-17", HostCommand.SetOption("grid", 1)),
        ClientMsg.Ping,
        ClientMsg.Ink(4, listOf(InkOp.Start(1, 2, 1, 100, 100, 50), InkOp.Pts(1, listOf(110, 105, 50)), InkOp.End(1), InkOp.Undo, InkOp.Clear)),
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
