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

    private fun engine() = PartyEngine(clock, SeededEntropy(3), GameRegistry(listOf(BrainDrain(pack)))).also { e = it }

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
    private fun fourInTwoTeams(): List<PlayerId> {
        engine()
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

    @Test fun pickASideScoresEachCall() {
        val (a, b, c, d) = fourInTwoTeams()
        repeat(2) { skipRound(); e.host(HostCmd.SkipPhase) }
        assertEquals("sides", tv.format)
        e.host(HostCmd.SkipPhase) // intro → first item
        assertEquals("Item 1", tv.prompt)
        assertEquals("Left or right?", tv.category)
        listOf(a, b).forEach { answer(it, "Lefty") }
        listOf(c, d).forEach { answer(it, "Righty") }
        assertEquals(BrainDrain.SIDES_POINTS, score("T1"))
        assertEquals(0, score("T2"))
        assertEquals(listOf("T1"), tv.sides!!.history.single().teamsRight)
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

    @Test fun theGauntletGivesHeadStartsCatchUpHelpAndAFinishLine() {
        val (a, b, c, d) = fourInTwoTeams()
        e.host(HostCmd.SkipPhase)
        answer(a, currentRight()); answer(b, currentRight()) // T1 takes the lead
        while (tv.format != "gauntlet") e.host(HostCmd.SkipPhase)
        assertEquals("intro", tv.phase)
        assertEquals(3, tv.teams.single { it.id == "T1" }.position)
        assertEquals(2, tv.teams.single { it.id == "T2" }.position)
        e.host(HostCmd.SkipPhase) // → first prompt
        // T2 trails, so its phones show the wrong option crossed out.
        assertEquals(1, assertIs<Screen.MultiSelect>(e.phoneState(c).screen).eliminated.size)
        assertTrue(assertIs<Screen.MultiSelect>(e.phoneState(a).screen).eliminated.isEmpty())
        repeat(4) {
            val fits = options(a).filter { it.text.startsWith("Fit") }.map { it.id }
            listOf(a, b).forEach { assertEquals(ActionResult.Ack, act(it, "multi", "picks" to fits, "lock" to true)) }
            val miss = options(c).first { it.text.startsWith("Miss") }.id
            listOf(c, d).forEach { assertEquals(ActionResult.Ack, act(it, "multi", "picks" to listOf(miss), "lock" to true)) }
            assertEquals("reveal", tv.phase)
            e.host(HostCmd.SkipPhase)
        }
        // T1 moved +2 a prompt from 3 and crossed 10 on the fourth; T2's crossed-out pick never cost it a space.
        assertEquals("podium", tv.phase)
        assertEquals(listOf("T1", "T2"), tv.podium)
        assertEquals(2, tv.teams.single { it.id == "T2" }.position)
        assertEquals(listOf("T2"), tv.drink?.teams)
        assertEquals("You won!", waiting(a).title)
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
        val restored = PartyEngine.restore(snap, clock, SeededEntropy(9), GameRegistry(listOf(BrainDrain(pack))))
        val game = restored.tvState().stage!!.game as TriviaTv
        assertEquals("question", game.phase)
        assertEquals(1, game.answered)
    }

    @Test fun theCorePackIsValidAndBigEnoughForTwoShows() {
        val core = TriviaPack.core()
        assertTrue(core.mc.size >= 150)
        assertTrue(core.ballpark.size >= 40)
        assertTrue(core.sides.size >= 15)
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
