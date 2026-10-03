package partyos.engine.games.trivia

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import partyos.engine.ActionResult
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.TriviaTv
import partyos.engine.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrainDrainTest {
    private val clock = FakeClock(0)
    private val pack = TriviaPack.validate(
        TriviaPack(
            mc = (1..8).map { McItem("tm$it", "Test", "Question $it?", "Right $it", listOf("Wrong A$it", "Wrong B$it", "Wrong C$it"), "Fact $it") },
            ballpark = (1..3).map { BallparkItem("tb$it", "Test", "Number $it?", 100.0 * it, "units") },
            sides = listOf(SidesSet("ts1", "Left or right?", "Lefty", "Righty", (1..5).map { SidesItem("Item $it", if (it % 2 == 1) "left" else "right") })),
            gauntlet = (1..8).map { GauntletItem("tg$it", "Pick the fits $it", listOf(GauntletOption("Fit A$it", true), GauntletOption("Fit B$it", true), GauntletOption("Miss $it", false))) },
        ),
    )

    private lateinit var e: PartyEngine
    private var n = 0

    private fun engine(game: BrainDrain = BrainDrain(pack, shuffleRounds = false)) = PartyEngine(clock, SeededEntropy(3), GameRegistry(listOf(game))).also { e = it }

    private fun startShow(teams: Int = 2, rounds: Int = 3) {
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("trivia", mapOf("rounds" to rounds, "teams" to teams, "drinks" to 1))))
        e.host(HostCmd.SkipPhase) // tutorial
        assertEquals("teamup", tv.phase)
        assertEquals(teams, tv.teams.size)
    }

    private val tv get() = e.tvState().stage!!.game as TriviaTv
    private val round get() = e.tvState().stage!!.phaseSeq

    private fun act(who: PlayerId, kind: String, vararg kv: Pair<String, Any>): ActionResult {
        val body = LinkedHashMap<String, JsonElement>()
        body["kind"] = JsonPrimitive(kind)
        for ((k, v) in kv) body[k] = when (v) {
            is String -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            is List<*> -> JsonArray(v.map { JsonPrimitive(it as String) })
            else -> error("unsupported value $v")
        }
        return e.action(who, "a${n++}", round, JsonObject(body))
    }

    private fun join(who: PlayerId, team: String) = assertEquals(ActionResult.Ack, act(who, "team", "option" to team))
    private fun name(who: PlayerId, text: String) = act(who, "teamName", "text" to text)
    private fun options(who: PlayerId) = when (val screen = e.phoneState(who).screen) {
        is Screen.ChoiceList -> screen.options
        is Screen.MultiSelect -> screen.options
        else -> error("no options on $screen")
    }
    private fun optionId(who: PlayerId, text: String) = options(who).first { it.text == text }.id
    private fun answer(who: PlayerId, text: String) = assertEquals(ActionResult.Ack, act(who, "answer", "option" to optionId(who, text)))
    private fun currentRight() = "Right " + tv.prompt.removePrefix("Question ").removeSuffix("?")
    private fun score(teamId: String) = tv.teams.single { it.id == teamId }.score
    private fun waiting(who: PlayerId) = assertIs<Screen.Waiting>(e.phoneState(who).screen)

    /** Four players, two named teams (A,B on T1; C,D on T2), now at the Quick Draw intro. */
    private fun fourInTwoTeams(game: BrainDrain = BrainDrain(pack, shuffleRounds = false)): List<PlayerId> {
        engine(game)
        val ids = listOf("A", "B", "C", "D").map { e.add(it) }
        startShow()
        join(ids[0], "T1"); join(ids[1], "T1"); join(ids[2], "T2"); join(ids[3], "T2")
        assertEquals(ActionResult.Ack, name(ids[0], "Quizzards"))
        assertEquals(ActionResult.Ack, name(ids[2], "Brainiacs"))
        assertEquals("intro", tv.phase)
        assertEquals("quick", tv.format)
        return ids
    }

    /** Skips through the current round to its standings. */
    private fun skipRound() {
        while (tv.phase != "standings") e.host(HostCmd.SkipPhase)
    }

    @Test fun teamUpEndsOnceEveryoneHasANamedTeamAndTheTeamsAreRemembered() {
        val (a, b, c, d) = fourInTwoTeams()
        assertEquals(listOf("Quizzards", "Brainiacs"), tv.teams.map { it.name })
        assertEquals(listOf(listOf(a, b), listOf(c, d)), tv.teams.map { it.members })
        // The next show starts with the same teams filled in, waiting for everyone to confirm.
        e.host(HostCmd.EndGame)
        startShow()
        assertEquals(listOf("Quizzards", "Brainiacs"), tv.teams.map { it.name })
        assertEquals(listOf(listOf(a, b), listOf(c, d)), tv.teams.map { it.members })
        assertEquals("Still Quizzards? Tap to confirm", assertIs<Screen.ChoiceList>(e.phoneState(a).screen).prompt)
        listOf(a, b).forEach { join(it, "T1") }
        listOf(c, d).forEach { join(it, "T2") }
        assertEquals("intro", tv.phase)
    }

    @Test fun theHostCanSendATypedTeamNameBackToItsKitName() {
        engine()
        val ids = (1..4).map { e.add("P$it") }
        startShow(teams = 2)
        join(ids[0], "T1"); join(ids[1], "T1"); join(ids[2], "T2"); join(ids[3], "T2")
        val kit = tv.teams.single { it.id == "T1" }.name
        assertEquals(ActionResult.Ack, name(ids[0], "Something Rude"))
        assertEquals("Something Rude", tv.teams.single { it.id == "T1" }.name)

        assertEquals(ActionResult.Ack, e.host(HostCmd.GameAction(BrainDrain.UNNAME + "T1")))
        assertEquals(kit, tv.teams.single { it.id == "T1" }.name)
        assertEquals("Name your team", assertIs<Screen.TextEntry>(e.phoneState(ids[1]).screen).prompt, "they can name it again")
        assertEquals(ActionResult.Rejected("UNKNOWN_TEAM"), e.host(HostCmd.GameAction(BrainDrain.UNNAME + "T9")))

        // Once the show is on, the veto sticks for the next game too.
        assertEquals(ActionResult.Ack, name(ids[2], "Also Rude"))
        e.host(HostCmd.SkipPhase) // Team Up over
        assertEquals(ActionResult.Ack, e.host(HostCmd.GameAction(BrainDrain.UNNAME + "T2")))
        assertTrue("Also Rude" !in e.snapshot().memory.getValue(BrainDrain.MEMORY_KEY))
    }

    @Test fun shuffleDealsEveryoneEvenlyKeepsTheNamesAndGivesTimeToCheck() {
        engine()
        val ids = (1..6).map { e.add("P$it") }
        startShow(teams = 3)
        ids.take(5).forEach { join(it, "T1") }
        join(ids[5], "T2")
        assertEquals(ActionResult.Ack, name(ids[0], "Quizzards"))
        clock.advance(40_000) // 5 seconds of Team Up left

        assertEquals(ActionResult.Ack, e.host(HostCmd.GameAction(BrainDrain.SHUFFLE)))
        assertEquals("teamup", tv.phase)
        assertEquals(listOf(2, 2, 2), tv.teams.map { it.members.size })
        assertEquals(ids.toSet(), tv.teams.flatMap { it.members }.toSet())
        assertEquals("Quizzards", tv.teams.single { it.id == "T1" }.name)
        assertTrue((tv.durationMs ?: 0) >= BrainDrain.SHUFFLE_GRACE_MS)
        // Everyone looks at their new team again before the show moves on.
        val onT1 = tv.teams.single { it.id == "T1" }.members.first()
        assertEquals("Still Quizzards? Tap to confirm", assertIs<Screen.ChoiceList>(e.phoneState(onT1).screen).prompt)

        // The captain (first to join) can shuffle from their phone; nobody else can.
        assertEquals(ActionResult.Rejected("NOT_CAPTAIN"), e.captainCommand(ids[1], HostCmd.GameAction(BrainDrain.SHUFFLE)))
        assertEquals(ActionResult.Ack, e.captainCommand(ids[0], HostCmd.GameAction(BrainDrain.SHUFFLE)))
        assertEquals(ActionResult.Rejected("UNSUPPORTED"), e.host(HostCmd.GameAction("dance")))

        e.host(HostCmd.SkipPhase) // Team Up over
        assertEquals(ActionResult.Rejected("NOT_NOW"), e.host(HostCmd.GameAction(BrainDrain.SHUFFLE)))
    }

    @Test fun theShowEndsWithAwardsForWhoCarriedWhoRebelledAndWhoSatOut() {
        val (a, b, c, d) = fourInTwoTeams()
        e.host(HostCmd.SkipPhase)
        repeat(3) {
            val right = currentRight()
            val wrong = options(a).first { it.text != right }.text
            // A is first and right for T1. On T2, C is first and wrong, so the tie goes C's way; D was right alone.
            answer(a, right); clock.advance(500)
            answer(c, wrong); clock.advance(500)
            answer(b, right); clock.advance(500)
            answer(d, right)
            assertEquals("reveal", tv.phase)
            toNextQuestion()
        }
        while (tv.phase != "podium") e.host(HostCmd.SkipPhase) // nobody answers the rest of the show
        assertTrue(tv.awards.isEmpty()) // saved for their own screen

        e.host(HostCmd.SkipPhase)
        assertEquals("awards", tv.phase)
        assertEquals(
            listOf("Big Brain" to a, "Fastest Thumb" to c, "Lone Wolf" to d, "Ghost" to b),
            tv.awards.map { it.title to it.player },
        )
        assertEquals("3 of 3 right", tv.awards[0].line)
        assertEquals("Went against their team and was right 3 times", tv.awards[2].line)
        assertTrue(tv.awards[3].roast && tv.awards[3].line.startsWith("Sat out "))
        assertEquals("You got Lone Wolf", waiting(d).title)
        assertEquals("win", waiting(d).tone)
        assertEquals("lose", waiting(b).tone)

        e.host(HostCmd.SkipPhase)
        assertNull(e.tvState().stage)
    }

    @Test fun theFirstNameWinsAndDuplicateNamesAreRefused() {
        engine()
        val ids = listOf("A", "B", "C").map { e.add(it) }
        startShow()
        join(ids[0], "T1"); join(ids[1], "T1"); join(ids[2], "T2")
        assertEquals(ActionResult.Ack, name(ids[0], "Ducks"))
        assertEquals(ActionResult.Rejected("TAKEN"), name(ids[1], "Geese"))
        assertEquals(ActionResult.Rejected("NAME_TAKEN"), name(ids[2], "  ducks "))
    }

    @Test fun unpickedPlayersFillTheSmallestTeamAtTheDeadline() {
        engine()
        val ids = listOf("A", "B", "C", "D", "E").map { e.add(it) }
        startShow()
        join(ids[0], "T1"); join(ids[1], "T1"); join(ids[2], "T1")
        e.host(HostCmd.SkipPhase)
        assertEquals("intro", tv.phase)
        assertEquals(listOf(3, 2), tv.teams.map { it.members.size })
        assertEquals(listOf(ids[3], ids[4]), tv.teams[1].members)
    }

    @Test fun everyoneOnOneTeamIsSplitSoThereIsAMatch() {
        engine()
        val ids = listOf("A", "B", "C", "D").map { e.add(it) }
        startShow()
        ids.forEach { join(it, "T1") }
        e.host(HostCmd.SkipPhase)
        assertEquals(listOf(2, 2), tv.teams.map { it.members.size })
    }

    @Test fun theAnswerStaysOffTheTvUntilTheReveal() {
        val ids = fourInTwoTeams()
        e.host(HostCmd.SkipPhase) // intro → question
        assertEquals("question", tv.phase)
        assertNull(tv.reveal)
        assertNull(tv.fact)
        assertEquals(4, tv.options.size)
        ids.forEach { answer(it, currentRight()) }
        assertEquals("reveal", tv.phase) // everyone answered: early end
        assertEquals(currentRight(), assertNotNull(tv.reveal).answerText)
        assertNotNull(tv.fact)
    }

    @Test fun pluralityDecidesTheTeamAnswerAndSpeedAddsABonus() {
        val (a, b, c, d) = fourInTwoTeams()
        e.host(HostCmd.SkipPhase)
        val right = currentRight()
        val wrong = options(a).first { it.text != right }.text
        // T1 splits 1-1: the tie goes to the option picked first (right, instantly).
        answer(a, right)
        clock.advance(10_000)
        answer(b, wrong)
        answer(c, wrong)
        answer(d, wrong)
        val r = assertNotNull(tv.reveal)
        val t1 = r.answers.single { it.team == "T1" }
        assertTrue(t1.correct)
        assertEquals(BrainDrain.QUICK_POINTS + BrainDrain.SPEED_BONUS, t1.points)
        assertEquals(0, r.answers.single { it.team == "T2" }.points)
        assertEquals(1500, score("T1"))
        // Every teammate is credited in the party's standings.
        assertEquals(1500, e.tvState().scores.single { it.id == b }.score)
        assertEquals("Correct!", waiting(b).title)
        assertEquals("lose", waiting(c).tone)
    }

    @Test fun teammatesSeeEachOthersVotesLive() {
        val (a, b) = fourInTwoTeams()
        e.host(HostCmd.SkipPhase)
        answer(a, currentRight())
        val screen = assertIs<Screen.ChoiceList>(e.phoneState(b).screen)
        assertEquals("shapes", screen.style)
        assertEquals(listOf(a), screen.votes[optionId(b, currentRight())])
        assertEquals("T1", screen.team?.id)
    }

    @Test fun ballparkUsesTheMedianAndRewardsTheClosestTeam() {
        val (a, b, c, d) = fourInTwoTeams()
        skipRound()
        e.host(HostCmd.SkipPhase) // standings → ballpark intro
        assertEquals("ballpark", tv.format)
        e.host(HostCmd.SkipPhase) // intro → question
        val target = tv.prompt.removePrefix("Number ").removeSuffix("?").toInt() * 100.0
        // T1's median lands exactly on the answer; T2's is far off.
        assertEquals(ActionResult.Ack, act(a, "guess", "value" to target - 10))
        assertEquals(ActionResult.Ack, act(b, "guess", "value" to target + 10))
        assertEquals(ActionResult.Ack, act(c, "guess", "value" to target * 3))
        assertEquals(ActionResult.Rejected("BAD_NUMBER"), act(d, "guess", "value" to "lots"))
        assertEquals(ActionResult.Ack, act(d, "guess", "value" to target * 5))
        assertEquals("bet", tv.phase) // two guesses on the board: teams get to bet before the answer
        e.host(HostCmd.SkipPhase)
        val r = assertNotNull(tv.reveal)
        val t1 = r.answers.single { it.team == "T1" }
        assertEquals(target, t1.number)
        assertTrue(t1.bullseye)
        assertEquals(BrainDrain.CLOSEST_POINTS + BrainDrain.BULLSEYE_POINTS, t1.points)
        val t2 = r.answers.single { it.team == "T2" }
        assertEquals(2, t2.rank)
        assertEquals(0, t2.points) // second place only pays with three or more teams
        assertEquals("Bullseye!", waiting(a).title)
    }

    private var target = 0.0

    /** Four players in two teams at a Ballpark bet: T1 (a, b) guessed the answer exactly, T2 (c, d) four times too high. Both teams start on 0. */
    private fun atBallparkBet(): List<PlayerId> {
        val ids = fourInTwoTeams()
        skipRound()
        e.host(HostCmd.SkipPhase) // standings → ballpark intro
        e.host(HostCmd.SkipPhase) // intro → question
        assertEquals("ballpark", tv.format)
        assertEquals("question", tv.phase)
        target = tv.prompt.removePrefix("Number ").removeSuffix("?").toInt() * 100.0
        act(ids[0], "guess", "value" to target - 10); act(ids[1], "guess", "value" to target + 10)
        act(ids[2], "guess", "value" to target * 3); act(ids[3], "guess", "value" to target * 5)
        assertEquals("bet", tv.phase)
        return ids
    }

    private fun bet(who: PlayerId, option: String) = act(who, "bet", "option" to option)
    private fun lockBet(who: PlayerId, team: String, stake: Int) {
        assertEquals(ActionResult.Ack, bet(who, team))
        assertEquals(ActionResult.Ack, bet(who, Betting.stakeId(stake)))
    }

    @Test fun theBetPhaseOpensWithEveryGuessAndItsOddsAndNoAnswer() {
        atBallparkBet()
        val info = assertNotNull(tv.bet)
        assertEquals(listOf("T1", "T2"), info.line.map { it.team })
        assertEquals(listOf(target, target * 4), info.line.map { it.number })
        assertEquals(listOf(1, 1), info.line.map { it.odds })
        assertTrue(info.locked.isEmpty())
        assertNull(tv.reveal, "the answer stays hidden until the reveal")
        assertEquals(Betting.BET_MS, tv.durationMs)
    }

    @Test fun betsSettleAtTheRevealOnTopOfTheRoundPoints() {
        val (a, b, c, d) = atBallparkBet()
        lockBet(a, "T1", 250); lockBet(b, "T1", 250)
        lockBet(c, "T2", 250)
        assertEquals("bet", tv.phase, "d has not decided yet")
        assertEquals(listOf("T1", "T2"), tv.bet!!.locked, "only 'bet in' shows, not who backed what")
        lockBet(d, "T2", 250)
        assertEquals("reveal", tv.phase, "the phase ends early once everyone has decided")
        val r = assertNotNull(tv.reveal)
        val t1 = r.answers.single { it.team == "T1" }
        assertEquals(BrainDrain.CLOSEST_POINTS + BrainDrain.BULLSEYE_POINTS, t1.points)
        assertEquals(true, t1.bet!!.won)
        assertEquals(250, t1.bet!!.delta)
        assertEquals(1750, score("T1"))
        val t2 = r.answers.single { it.team == "T2" }
        assertEquals(false, t2.bet!!.won)
        assertEquals(0, t2.bet!!.delta, "house money: a team on 0 loses nothing")
        assertEquals(0, score("T2"))
    }

    @Test fun backingTheOtherTeamPaysWhenTheyWereClosest() {
        val (a, b, c, d) = atBallparkBet()
        listOf(a, b).forEach { assertEquals(ActionResult.Ack, bet(it, Betting.SKIP)) }
        lockBet(c, "T1", 250); lockBet(d, "T1", 250)
        assertEquals("reveal", tv.phase)
        val t2 = tv.reveal!!.answers.single { it.team == "T2" }
        assertEquals("T1", t2.bet!!.on)
        assertEquals(250, t2.bet!!.delta)
        assertNull(tv.reveal!!.answers.single { it.team == "T1" }.bet, "a team that skipped has no bet")
        assertEquals(250, score("T2"))
    }

    @Test fun stakesAreCappedAndAGuessMustBePickedFirst() {
        val (a) = atBallparkBet()
        assertEquals(ActionResult.Rejected("NOT_NOW"), bet(a, Betting.stakeId(250)), "no guess backed yet")
        assertEquals(ActionResult.Rejected("BAD_OPTION"), bet(a, "T9"))
        assertEquals(ActionResult.Ack, bet(a, "T1"))
        assertEquals(ActionResult.Rejected("BAD_STAKE"), bet(a, Betting.stakeId(500)), "the team is on 0, so only house money")
        assertEquals(ActionResult.Rejected("BAD_STAKE"), bet(a, "s300"))
        assertEquals(ActionResult.Ack, bet(a, Betting.stakeId(250)))
    }

    @Test fun changingYourMindResetsTheStakeAndTheTeamWaitsForYouAgain() {
        val (a, b, c, d) = atBallparkBet()
        lockBet(a, "T1", 250); lockBet(b, "T1", 250); lockBet(c, "T2", 250)
        assertEquals(ActionResult.Ack, bet(a, "T2")) // a changes their mind: stake is gone
        lockBet(d, "T2", 250)
        assertEquals("bet", tv.phase, "a is still choosing a stake")
        assertEquals(ActionResult.Ack, bet(a, Betting.stakeId(250)))
        assertEquals("reveal", tv.phase)
    }

    @Test fun aLostBetCostsEveryTeammateInThePartyStandingsToo() {
        val (a, b, c, d) = fourInTwoTeams()
        e.host(HostCmd.SkipPhase) // intro → first Quick Draw question
        val right = currentRight()
        listOf(a, b, c, d).forEach { answer(it, right) }
        assertEquals(1500, score("T2"), "both teams are on 1500 after a quick right answer")
        skipRound()
        e.host(HostCmd.SkipPhase); e.host(HostCmd.SkipPhase) // standings → ballpark intro → question
        val t = tv.prompt.removePrefix("Number ").removeSuffix("?").toInt() * 100.0
        act(a, "guess", "value" to t); act(b, "guess", "value" to t)
        act(c, "guess", "value" to t * 5); act(d, "guess", "value" to t * 5)
        assertEquals("bet", tv.phase)
        listOf(a, b).forEach { assertEquals(ActionResult.Ack, bet(it, Betting.SKIP)) }
        lockBet(c, "T2", 500); lockBet(d, "T2", 500)
        assertEquals("reveal", tv.phase)
        assertEquals(1000, score("T2"), "T2 lost a 500 bet")
        assertEquals(1000, e.tvState().scores.single { it.id == c }.score, "and so did each of its players")
        assertEquals(1000, e.tvState().scores.single { it.id == d }.score)
    }

    @Test fun aBigWinIsAnnouncedWithThousandsSeparators() {
        val (a, b, c, d) = fourInTwoTeams()
        e.host(HostCmd.SkipPhase) // intro → first Quick Draw question
        val right = currentRight()
        listOf(a, b, c, d).forEach { answer(it, right) } // both teams on 1500
        skipRound()
        e.host(HostCmd.SkipPhase); e.host(HostCmd.SkipPhase) // standings → ballpark intro → question
        val t = tv.prompt.removePrefix("Number ").removeSuffix("?").toInt() * 100.0
        act(a, "guess", "value" to t); act(b, "guess", "value" to t)
        act(c, "guess", "value" to t * 5); act(d, "guess", "value" to t * 5)
        lockBet(a, "T1", 1000); lockBet(b, "T1", 1000)
        listOf(c, d).forEach { assertEquals(ActionResult.Ack, bet(it, Betting.SKIP)) }
        assertEquals("reveal", tv.phase)
        assertTrue(tv.hostLine!!.contains("+1,000"), tv.hostLine)
    }

    @Test fun theFirstTapWinsATieEvenIfThatPlayerLocksTheirStakeLast() {
        val (a, b) = atBallparkBet()
        assertEquals(ActionResult.Ack, bet(a, "T1")) // a taps first...
        clock.advance(1_000)
        lockBet(b, "T2", 250) // ...b taps and locks a second later...
        clock.advance(5_000)
        assertEquals(ActionResult.Ack, bet(a, Betting.stakeId(250))) // ...and a only locks a stake much later
        e.host(HostCmd.SkipPhase)
        assertEquals("T1", tv.reveal!!.answers.single { it.team == "T1" }.bet!!.on, "one backer each: the guess tapped first wins")
    }

    @Test fun theHostSkippingTheBetSettlesOnlyTheBetsThatAreIn() {
        val (a, b) = atBallparkBet()
        lockBet(a, "T1", 250); lockBet(b, "T1", 250)
        e.host(HostCmd.SkipPhase)
        assertEquals("reveal", tv.phase)
        assertEquals(250, tv.reveal!!.answers.single { it.team == "T1" }.bet!!.delta)
        assertNull(tv.reveal!!.answers.single { it.team == "T2" }.bet)
    }

    @Test fun withFewerThanTwoGuessesThereIsNothingToBetOn() {
        val (a) = fourInTwoTeams()
        skipRound()
        e.host(HostCmd.SkipPhase); e.host(HostCmd.SkipPhase)
        assertEquals("question", tv.phase)
        act(a, "guess", "value" to 5.0)
        e.host(HostCmd.SkipPhase)
        assertEquals("reveal", tv.phase)
        assertNull(tv.reveal!!.answers.firstNotNullOfOrNull { it.bet })
    }

    @Test fun oldSavedShowStillLoads() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val s = json.decodeFromString(TriviaState.serializer(), """{"phase":"question","format":"ballpark"}""")
        assertTrue(s.line.isEmpty())
        assertTrue(s.bets.isEmpty())
    }

    @Test fun thePhoneFirstOffersEveryGuessAndSkip() {
        val (a) = atBallparkBet()
        val screen = assertIs<Screen.ChoiceList>(e.phoneState(a).screen)
        assertEquals("bet", screen.kind)
        assertEquals("teams", screen.style)
        assertEquals(listOf("T1", "T2", Betting.SKIP), screen.options.map { it.id })
        assertEquals("guess ${BrainDrain.formatNumber(target)} · pays 1×", screen.options.first().detail)
        assertEquals("Quizzards", screen.options.first().text)
    }

    @Test fun afterPickingAGuessThePhoneOffersTheAffordableStakes() {
        val (a) = atBallparkBet()
        bet(a, "T2")
        val screen = assertIs<Screen.ChoiceList>(e.phoneState(a).screen)
        assertTrue(screen.prompt.startsWith("How much on Brainiacs"), screen.prompt)
        assertEquals(listOf(Betting.stakeId(250), Betting.BACK), screen.options.map { it.id }, "a team on 0 can only offer house money")
    }

    @Test fun aLockedBetShowsAndCanBeChanged() {
        val (a, b) = atBallparkBet()
        lockBet(a, "T1", 250)
        val screen = assertIs<Screen.ChoiceList>(e.phoneState(a).screen)
        assertEquals("T1", screen.selected)
        assertTrue(screen.prompt.contains("250"))
        assertEquals(listOf(a), screen.votes["T1"], "teammates see who backed what on their own team")
        assertEquals(listOf(a), assertIs<Screen.ChoiceList>(e.phoneState(b).screen).votes["T1"])
        assertEquals(ActionResult.Ack, bet(a, Betting.BACK))
        assertEquals("Who's closest? Back a guess", assertIs<Screen.ChoiceList>(e.phoneState(a).screen).prompt)
    }

    @Test fun theRevealScreenMentionsTheBet() {
        val (a, b, c, d) = atBallparkBet()
        lockBet(a, "T1", 250); lockBet(b, "T1", 250); lockBet(c, "T1", 250); lockBet(d, "T1", 250)
        assertTrue(waiting(a).detail!!.contains("bet +250"), waiting(a).detail)
        assertTrue(waiting(c).detail!!.contains("bet +250"), waiting(c).detail)
    }

    /** Four players in two teams, T1 (a, b) ahead 1500 to 0, skipped through to the Final Wager's category slam. */
    /** Twelve questions, so after the five rounds have used eight there are still some nobody has played tonight for the finale. */
    private val finalePack = pack.copy(mc = (1..12).map { McItem("tm$it", "Test", "Question $it?", "Right $it", listOf("Wrong A$it", "Wrong B$it", "Wrong C$it"), "Fact $it") })

    private fun toFinale(): List<PlayerId> {
        val ids = fourInTwoTeams(BrainDrain(finalePack, shuffleRounds = false))
        e.host(HostCmd.SkipPhase) // intro → first Quick Draw question
        val right = currentRight()
        val wrong = options(ids[0]).first { it.text != right }.text
        answer(ids[0], right); answer(ids[1], right); answer(ids[2], wrong); answer(ids[3], wrong)
        var guard = 0
        while (tv.phase != "final_category" && guard++ < 400) e.host(HostCmd.SkipPhase)
        assertEquals("final_category", tv.phase)
        assertEquals(1500, score("T1"))
        assertEquals(0, score("T2"))
        return ids
    }

    private fun wager(who: PlayerId, option: String) = act(who, "finalWager", "option" to option)
    private fun finalAnswer(who: PlayerId, text: String) = act(who, "finalAnswer", "text" to text)

    @Test fun theShowEndsWithACategorySlamThatHidesTheQuestion() {
        toFinale()
        assertEquals("final", tv.format)
        val f = assertNotNull(tv.finale)
        assertEquals("Test", f.category)
        assertEquals("", tv.prompt, "the question stays hidden until the wager is made")
        assertTrue(f.results.isEmpty() && f.answerText == null && f.locked.isEmpty())
        e.host(HostCmd.SkipPhase)
        assertEquals("final_wager", tv.phase)
    }

    @Test fun onlyUnderdogsMayGoAllIn() {
        val (a, _, c) = toFinale()
        e.host(HostCmd.SkipPhase) // category → wager
        assertEquals(ActionResult.Rejected("BAD_OPTION"), wager(a, "wall"), "the leader may not go all in")
        assertEquals(ActionResult.Rejected("BAD_OPTION"), wager(a, "w999"))
        assertEquals(ActionResult.Ack, wager(c, "wall"), "last place may")
        assertEquals(ActionResult.Ack, wager(a, "w50"))
    }

    @Test fun theWagerPhaseEndsOnceEveryTeamHasPickedAndShowsOnlyWhoIsIn() {
        val (a, _, c) = toFinale()
        e.host(HostCmd.SkipPhase)
        assertEquals(ActionResult.Ack, wager(a, "w50"))
        assertEquals(listOf("T1"), tv.finale!!.locked, "who is in shows; what they picked never does")
        assertEquals("final_wager", tv.phase)
        assertEquals(ActionResult.Ack, wager(c, "wall"))
        assertEquals("final_question", tv.phase)
        assertTrue(tv.prompt.startsWith("Question "))
        assertNull(tv.finale!!.answerText, "the answer stays hidden until the reveal")
    }

    @Test fun theRevealRunsLastPlaceFirstAndScoresChangeOnlyAtTheEnd() {
        val (a, b, c, d) = toFinale()
        e.host(HostCmd.SkipPhase)
        wager(a, "w50"); wager(c, "wall")
        val right = currentRight()
        finalAnswer(a, right); finalAnswer(b, right.lowercase()); finalAnswer(c, "Nope"); finalAnswer(d, "Nope")
        assertEquals("final_reveal", tv.phase, "the question ends once every player has answered")
        val f = assertNotNull(tv.finale)
        assertEquals(right, f.answerText)
        assertEquals(listOf("T2", "T1"), f.results.map { it.team }, "last place first, the leader last")
        val t2 = f.results[0]
        assertEquals("Nope", t2.text)
        assertEquals(false, t2.right)
        assertEquals(1000, t2.wager) // ALL IN on the 1000 floor
        assertEquals(0, t2.delta, "a team on 0 loses nothing")
        val t1 = f.results[1]
        assertEquals(true, t1.right)
        assertEquals(750, t1.wager) // 50% of 1500
        assertEquals(750, t1.delta)
        assertEquals(1500, t1.before); assertEquals(2250, t1.after)
        assertEquals(1500, score("T1"), "scores hold their old values while the reveal plays")
        assertEquals(Finale.revealMs(2), tv.durationMs)
        e.host(HostCmd.SkipPhase)
        assertEquals("podium", tv.phase)
        assertEquals(2250, score("T1"))
        assertEquals(0, score("T2"))
        assertEquals(listOf("T1", "T2"), tv.podium)
    }

    @Test fun aWrongWagerCostsTheLeader() {
        val (a, b, c, d) = toFinale()
        e.host(HostCmd.SkipPhase)
        wager(a, "w75"); wager(c, "w25")
        val right = currentRight()
        finalAnswer(c, right); finalAnswer(d, right); finalAnswer(a, "Nope"); finalAnswer(b, "Nope")
        e.host(HostCmd.SkipPhase)
        assertEquals("podium", tv.phase)
        assertEquals(1500 - 1150, score("T1")) // 75% of 1500 is 1125, to the nearest 50 is 1150, wrong
        assertEquals(250, score("T2")) // 25% of the 1000 floor, right
        assertEquals(listOf("T1", "T2"), tv.podium)
    }

    @Test fun anEmptyFinaleStillEnds() {
        toFinale()
        var guard = 0
        while (tv.phase != "podium" && guard++ < 20) e.host(HostCmd.SkipPhase)
        assertEquals("podium", tv.phase)
        assertEquals(1500, score("T1"))
        assertEquals(0, score("T2"))
        assertEquals(listOf("T1", "T2"), tv.podium)
    }

    @Test fun skippingTheRevealAppliesScoresOnce() {
        val (a, b, c, d) = toFinale()
        e.host(HostCmd.SkipPhase)
        wager(a, "w50"); wager(c, "w0")
        val right = currentRight()
        listOf(a, b).forEach { finalAnswer(it, right) }
        listOf(c, d).forEach { finalAnswer(it, "Nope") }
        assertEquals("final_reveal", tv.phase)
        e.host(HostCmd.SkipPhase)
        assertEquals("podium", tv.phase)
        assertEquals(2250, score("T1"))
        e.host(HostCmd.SkipPhase) // podium → awards: the finale must not run again
        assertEquals(2250, score("T1"))
    }

    @Test fun noShortQuestionSkipsTheFinale() {
        val longPack = TriviaPack.validate(
            TriviaPack(
                mc = (1..12).map { McItem("tm$it", "Test", "Question $it?", "A long answer to type $it", listOf("Wrong A$it", "Wrong B$it", "Wrong C$it"), "Fact $it") },
                ballpark = pack.ballpark, sides = pack.sides, gauntlet = pack.gauntlet,
            ),
        )
        engine(BrainDrain(longPack, shuffleRounds = false))
        val ids = listOf("A", "B", "C", "D").map { e.add(it) }
        startShow()
        join(ids[0], "T1"); join(ids[1], "T1"); join(ids[2], "T2"); join(ids[3], "T2")
        name(ids[0], "Quizzards"); name(ids[2], "Brainiacs")
        var guard = 0
        while (tv.phase != "podium" && guard++ < 400) {
            assertTrue(tv.phase !in setOf("final_category", "final_wager", "final_question", "final_reveal"), tv.phase)
            e.host(HostCmd.SkipPhase)
        }
        assertEquals("podium", tv.phase)
    }

    @Test fun oldSavedShowsHaveNoFinale() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val s = json.decodeFromString(TriviaState.serializer(), """{"phase":"standings"}""")
        assertNull(s.finale)
    }

    @Test fun pickASideScoresEachCall() {
        val (a, b, c, d) = fourInTwoTeams()
        repeat(2) { skipRound(); e.host(HostCmd.SkipPhase) }
        assertEquals("sides", tv.format)
        e.host(HostCmd.SkipPhase) // intro → first call (in this show's shuffled order)
        assertTrue(tv.prompt.startsWith("Item "))
        assertEquals("Left or right?", tv.category)
        // The test pack puts odd items on the left.
        val (right, wrong) = if (tv.prompt.removePrefix("Item ").toInt() % 2 == 1) "Lefty" to "Righty" else "Righty" to "Lefty"
        listOf(a, b).forEach { answer(it, right) }
        listOf(c, d).forEach { answer(it, wrong) }
        assertEquals(BrainDrain.SIDES_POINTS, score("T1"))
        assertEquals(0, score("T2"))
        assertEquals(listOf("T1"), tv.sides!!.history.single().teamsRight)
    }

    /** Plays one show from [seed] to the end of Pick a Side and returns its calls in play order. */
    private fun sidesOrder(seed: Long): List<String> {
        e = PartyEngine(clock, SeededEntropy(seed), GameRegistry(listOf(BrainDrain(pack, shuffleRounds = false))))
        repeat(4) { e.add("P$it") }
        startShow()
        val calls = mutableListOf<String>()
        while (tv.format != "sides") e.host(HostCmd.SkipPhase)
        while (tv.format == "sides" && tv.phase != "standings") {
            if (tv.phase == "question") calls += tv.prompt
            e.host(HostCmd.SkipPhase)
        }
        return calls
    }

    @Test fun pickASidePlaysEveryCallOnceInAFreshOrderEachShow() {
        val items = pack.sides.single().items.map { it.text }
        val orders = (1L..12L).map(::sidesOrder)
        orders.forEach { assertEquals(items.sorted(), it.sorted()) } // every call, exactly once
        assertTrue(orders.toSet().size >= 6, "orders barely change: $orders")
        // The pack alternates left, right, left…; shows mostly shouldn't.
        val alternating = orders.count { o -> o.map { it.removePrefix("Item ").toInt() % 2 }.zipWithNext().all { (x, y) -> x != y } }
        assertTrue(alternating <= 3, "$alternating of 12 shows alternated")
    }

    @Test fun aRestoredShowKeepsItsPickASideOrder() {
        engine()
        repeat(4) { e.add("P$it") }
        startShow()
        while (!(tv.format == "sides" && tv.phase == "question" && tv.q == 2)) e.host(HostCmd.SkipPhase)
        val snap = e.snapshot()
        val atSnap = tv.prompt
        e.host(HostCmd.SkipPhase); e.host(HostCmd.SkipPhase)
        val next = tv.prompt
        val restored = PartyEngine.restore(snap, clock, SeededEntropy(9), GameRegistry(listOf(BrainDrain(pack, shuffleRounds = false))))
        assertEquals(atSnap, (restored.tvState().stage!!.game as TriviaTv).prompt)
        restored.host(HostCmd.SkipPhase); restored.host(HostCmd.SkipPhase)
        assertEquals(next, (restored.tvState().stage!!.game as TriviaTv).prompt)
    }

    @Test fun theFastestCorrectHeistTeamRobsTheTeamTheyPick() {
        engine()
        val ids = (1..6).map { e.add("P$it") }
        startShow(teams = 3)
        ids.forEachIndexed { i, p -> join(p, "T${i / 2 + 1}") }
        listOf(0, 2, 4).forEach { assertEquals(ActionResult.Ack, name(ids[it], "Team$it")) }
        // Quick Draw: T3 banks points so it's worth robbing.
        e.host(HostCmd.SkipPhase)
        answer(ids[4], currentRight()); answer(ids[5], currentRight())
        while (tv.format != "heist") e.host(HostCmd.SkipPhase)
        e.host(HostCmd.SkipPhase) // heist intro → question
        val right = currentRight()
        answer(ids[2], right); answer(ids[3], right) // T2 is fastest
        clock.advance(2_000)
        listOf(0, 1, 4, 5).forEach { answer(ids[it], right) }
        assertEquals("reveal", tv.phase)
        e.host(HostCmd.SkipPhase)
        assertEquals("victim", tv.phase)
        assertEquals("T2", tv.heist?.thief)
        assertEquals(ActionResult.Rejected("NOT_YOUR_HEIST"), act(ids[0], "victim", "option" to "T3"))
        assertEquals(ActionResult.Rejected("BAD_OPTION"), act(ids[2], "victim", "option" to "T2"))
        val before = score("T3")
        assertEquals(ActionResult.Ack, act(ids[2], "victim", "option" to "T3"))
        assertEquals(ActionResult.Ack, act(ids[3], "victim", "option" to "T3"))
        assertEquals("steal", tv.phase)
        assertEquals(before - BrainDrain.STEAL_POINTS, score("T3"))
        assertEquals(listOf("T3"), tv.drink?.teams)
        assertEquals("win", waiting(ids[2]).tone)
        assertEquals("lose", waiting(ids[4]).tone)
    }

    private fun write(who: PlayerId, text: String) = assertEquals(ActionResult.Ack, act(who, "write", "text" to text))

    @Test fun writeItDownHidesTheOptionsAndForgivesTypos() {
        val (a, b, c, d) = fourInTwoTeams()
        while (tv.format != "write") e.host(HostCmd.SkipPhase)
        assertEquals("intro", tv.phase)
        e.host(HostCmd.SkipPhase)
        assertEquals("question", tv.phase)
        assertTrue(tv.options.isEmpty()) // nothing to pick from, on the TV or the phones
        assertNull(tv.reveal)
        val right = currentRight() // "Right N"
        val entry = assertIs<Screen.TextEntry>(e.phoneState(a).screen)
        assertEquals("write", entry.kind)
        assertEquals(tv.prompt, entry.prompt)

        // T1: a typo and a clean spelling are the same (right) answer. T2 splits 1-1 and c wrote first, wrongly.
        write(a, right.replace("Right", "Rihgt")); clock.advance(500)
        assertEquals("Team: A: ${right.replace("Right", "Rihgt")}", assertIs<Screen.TextEntry>(e.phoneState(b).screen).hint)
        write(c, "Wrong"); clock.advance(500)
        write(b, right.lowercase()); clock.advance(500)
        write(d, right)
        assertEquals("reveal", tv.phase)

        val r = assertNotNull(tv.reveal)
        assertEquals(right, r.answerText)
        val t1 = r.answers.single { it.team == "T1" }
        val t2 = r.answers.single { it.team == "T2" }
        assertTrue(t1.correct); assertEquals(BrainDrain.WRITE_POINTS, t1.points); assertEquals(right.replace("Right", "Rihgt"), t1.text)
        assertFalse(t2.correct); assertEquals(0, t2.points); assertEquals("Wrong", t2.text)
        assertEquals("Correct!", waiting(a).title)
        assertEquals("Nope", waiting(d).title)
        assertEquals("Your team wrote “Wrong”. It was $right", waiting(d).detail)
    }

    @Test fun aNumberAnswerMustBeExactAndBlankAnswersAreRefused() {
        val (a, _, _, _) = fourInTwoTeams()
        while (tv.format != "write") e.host(HostCmd.SkipPhase)
        e.host(HostCmd.SkipPhase)
        assertEquals(ActionResult.Rejected("BAD_TEXT"), act(a, "write", "text" to "   "))
        assertEquals(ActionResult.Rejected("BAD_TEXT"), act(a, "write", "text" to "x".repeat(BrainDrain.MAX_WRITE + 1)))
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(a, "answer", "option" to "a"))
    }

    @Test fun thePodiumRanksByPointsAndLastPlaceDrinks() {
        val (a, b, c, d) = fourInTwoTeams()
        e.host(HostCmd.SkipPhase)
        // T2 gets the only points of the show.
        answer(c, currentRight()); answer(d, currentRight())
        answer(a, options(a).first { it.text != currentRight() }.text); answer(b, options(b).first { it.text != currentRight() }.text)
        while (tv.phase != "podium") e.host(HostCmd.SkipPhase)
        assertEquals(listOf("T2", "T1"), tv.podium)
        assertTrue(score("T2") > score("T1"))
        assertEquals(listOf("T1"), tv.drink?.teams)
        assertEquals(2, tv.drink?.sips)
        assertEquals("You won!", waiting(c).title)
        assertEquals("2nd place", waiting(a).title)
    }

    @Test fun writeItDownOnItsOwnIsThreeRoundsOfTypedAnswersThenAPodium() {
        val bigger = pack.copy(mc = (1..12).map { McItem("tw$it", "Test", "Question $it?", "Right $it", listOf("Wrong A$it", "Wrong B$it", "Wrong C$it")) })
        val game = BrainDrain(bigger, mode = BrainDrain.Mode.WRITE)
        assertEquals(BrainDrain.WRITE_GAME_ID, game.info.id)
        assertEquals("Write It Down", game.info.title)
        // It sits next to Brain Drain in the same party.
        e = PartyEngine(clock, SeededEntropy(3), GameRegistry(listOf(BrainDrain(bigger, shuffleRounds = false), game)))
        val (a, b, c, d) = listOf("A", "B", "C", "D").map { e.add(it) }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame(BrainDrain.WRITE_GAME_ID, mapOf("rounds" to 3, "teams" to 2))))
        assertEquals(BrainDrain.WRITE_GAME_ID, e.tvState().stage!!.gameId)
        e.host(HostCmd.SkipPhase) // tutorial
        join(a, "T1"); join(b, "T1"); join(c, "T2"); join(d, "T2")
        name(a, "Quizzards"); name(c, "Brainiacs")

        val formats = mutableListOf<String>()
        var answered = false
        while (tv.phase != "podium") {
            assertTrue(tv.phase !in setOf("final_category", "final_wager", "final_question", "final_reveal"), "Write It Down has no finale")
            assertEquals(BrainDrain.WRITE_SHOW_ROUNDS, tv.totalRounds)
            if (tv.phase == "intro") formats += tv.format
            if (tv.phase == "question") {
                assertTrue(tv.options.isEmpty())
                if (!answered) {
                    val right = currentRight()
                    write(a, right); write(b, right.lowercase()); write(c, "Nope"); write(d, "Nope")
                    answered = true
                    continue
                }
            }
            e.host(HostCmd.SkipPhase)
        }
        assertEquals(List(BrainDrain.WRITE_SHOW_ROUNDS) { BrainDrain.WRITE }, formats)
        assertEquals(listOf("T1", "T2"), tv.podium)
        assertEquals(BrainDrain.WRITE_POINTS, score("T1"))
        assertEquals("Quizzards win Write It Down!", tv.hostLine)
        while (e.tvState().stage != null) e.host(HostCmd.SkipPhase)
        assertEquals("Quizzards won Write It Down", e.tvState().lastResult!!.highlights.first())
    }

    @Test fun everyShowDealsAllFiveRoundsAndNeverOpensOnTheHeist() {
        val orders = (1..400).map { BrainDrain.dealRounds(kotlin.random.Random(it)) }
        assertTrue(orders.all { it.sorted() == BrainDrain.FORMATS.sorted() })
        assertTrue(orders.none { it.first() == BrainDrain.HEIST })
        assertTrue(orders.toSet().size > 50) // really shuffled
        assertTrue(BrainDrain.FORMATS.filter { it != BrainDrain.HEIST }.all { f -> orders.count { it.first() == f } > 60 })
    }

    @Test fun aShuffledShowPlaysItsOwnOrderAndCountsItsRounds() {
        engine(BrainDrain(pack))
        repeat(4) { e.add("P$it") }
        startShow()
        val order = mutableListOf<String>()
        while (e.tvState().stage != null && tv.phase != "podium") {
            if (tv.phase == "intro") order += tv.format
            assertEquals(5, tv.totalRounds)
            e.host(HostCmd.SkipPhase)
        }
        assertEquals(BrainDrain.FORMATS.sorted(), order.sorted())
    }

    @Test fun lateJoinersGoToTheSmallestTeamAtTheNextQuestion() {
        val ids = fourInTwoTeams()
        e.host(HostCmd.SkipPhase)
        val late = e.add("Late")
        assertEquals(ActionResult.Rejected("NEXT_ROUND"), act(late, "answer", "option" to "a"))
        waiting(late)
        ids.forEach { answer(it, currentRight()) }
        e.host(HostCmd.SkipPhase) // reveal → next question
        assertTrue(tv.teams.any { late in it.members })
        assertIs<Screen.ChoiceList>(e.phoneState(late).screen)
    }

    @Test fun aShowRestoresMidQuestion() {
        val ids = fourInTwoTeams()
        e.host(HostCmd.SkipPhase)
        answer(ids[0], currentRight())
        val snap = e.snapshot()
        assertTrue(snap.memory.containsKey(BrainDrain.MEMORY_KEY))
        val restored = PartyEngine.restore(snap, clock, SeededEntropy(9), GameRegistry(listOf(BrainDrain(pack, shuffleRounds = false))))
        val game = restored.tvState().stage!!.game as TriviaTv
        assertEquals("question", game.phase)
        assertEquals(1, game.answered)
    }

    /** Behaves like the server's feed: hands over what it holds, skipping anything already used. */
    private class FakeFeed(items: List<McItem>) : TriviaFeed {
        val held = items.toMutableList()
        var warmed = 0
        override fun take(used: Set<String>, avoidCategory: String?): McItem? =
            (held.firstOrNull { it.id !in used && it.category != avoidCategory } ?: held.firstOrNull { it.id !in used })?.also { held.remove(it) }
        override fun warm() { warmed++ }
    }

    private fun liveItem(i: Int) =
        McItem("tlive-$i", "Film", "Live question $i?", "Live right $i", listOf("Live wrong A$i", "Live wrong B$i", "Live wrong C$i"), source = "Open Trivia DB")

    private fun toNextQuestion() {
        e.host(HostCmd.SkipPhase)
        while (tv.phase != "question" && tv.phase != "standings") e.host(HostCmd.SkipPhase)
    }

    @Test fun liveQuestionsTakeOverOnceTheBundledOnesRunOutAndSurviveARestore() {
        val feed = FakeFeed((1..3).map(::liveItem))
        val ids = fourInTwoTeams(BrainDrain(pack.copy(mc = pack.mc.take(1)), feed, shuffleRounds = false))
        e.host(HostCmd.SkipPhase) // intro → the one bundled question
        assertEquals("Question 1?", tv.prompt)
        assertNull(tv.credit)
        assertTrue(feed.warmed > 0) // the pack is nearly empty, so the feed starts filling
        ids.forEach { answer(it, "Right 1") }

        toNextQuestion()
        assertEquals("Live question 1?", tv.prompt)
        assertEquals("Film", tv.category)
        assertEquals("Open Trivia DB", tv.credit)
        assertEquals("Live question 1?", (e.phoneState(ids[0]).screen as Screen.ChoiceList).prompt)
        assertTrue("tlive-1" in e.snapshot().usedContent)

        // A restored show carries its live question with it: no feed needed.
        val restored = PartyEngine.restore(e.snapshot(), clock, SeededEntropy(9), GameRegistry(listOf(BrainDrain(pack.copy(mc = pack.mc.take(1)), shuffleRounds = false))))
        assertEquals("Live question 1?", (restored.tvState().stage!!.game as TriviaTv).prompt)

        ids.forEach { answer(it, "Live right 1") }
        val r = assertNotNull(tv.reveal)
        assertEquals("Live right 1", r.answerText)
        assertTrue(r.answers.all { it.correct })
        assertNull(tv.fact) // live questions have no fun fact
        assertEquals("Open Trivia DB", tv.credit)
        toNextQuestion()
        assertEquals("Live question 2?", tv.prompt)
    }

    @Test fun withNoLiveQuestionReadyTheRoundJustEndsEarly() {
        val broken = liveItem(1).copy(wrong = listOf("Only one wrong answer"))
        for (feed in listOf(null, FakeFeed(emptyList()), FakeFeed(listOf(broken)))) {
            val ids = fourInTwoTeams(BrainDrain(pack.copy(mc = pack.mc.take(1)), feed, shuffleRounds = false))
            e.host(HostCmd.SkipPhase)
            ids.forEach { answer(it, "Right 1") }
            toNextQuestion()
            assertEquals("standings", tv.phase, "feed $feed")
            assertEquals("quick", tv.format)
        }
    }

    @Test fun theBundledPackComesFirstAndTheFeedStaysColdWhileThereArePlenty() {
        val feed = FakeFeed((1..3).map(::liveItem))
        val big = pack.copy(mc = (1..40).map { McItem("tm$it", "Test", "Question $it?", "Right $it", listOf("Wrong A$it", "Wrong B$it", "Wrong C$it")) })
        fourInTwoTeams(BrainDrain(big, feed, shuffleRounds = false))
        e.host(HostCmd.SkipPhase)
        assertTrue(tv.prompt.startsWith("Question "))
        assertNull(tv.credit)
        assertEquals(0, feed.warmed)
        assertEquals(3, feed.held.size)
    }

    @Test fun theCorePackIsValidAndBigEnoughForTwoShows() {
        val core = TriviaPack.core()
        assertTrue(core.mc.size >= 300)
        assertTrue(core.ballpark.size >= 60)
        assertTrue(core.sides.size >= 23)
        assertTrue(core.gauntlet.size >= 30)
    }

    @Test fun numbersAndOrdinalsReadNaturally() {
        assertEquals("238,855", BrainDrain.formatNumber(238855.0))
        assertEquals("15.5", BrainDrain.formatNumber(15.5))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st"), listOf(1, 2, 3, 4, 11, 12, 13, 21).map(BrainDrain::ordinal))
        assertEquals(2.5, BrainDrain.median(listOf(1.0, 2.0, 3.0, 4.0)))
        assertEquals(250, BrainDrain.speedBonus(10_000, 20_000))
    }
}
