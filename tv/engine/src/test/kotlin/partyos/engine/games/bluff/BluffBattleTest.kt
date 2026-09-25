package partyos.engine.games.bluff

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.BluffTv
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BluffBattleTest {
    private val clock = FakeClock(0)
    private val q1 = BluffQuestion("q1", "Wombat poop is shaped like ____.", "cubes", listOf("cube", "squares"), listOf("spirals", "stars", "hearts"))
    private val q2 = BluffQuestion("q2", "Scotland's national animal is the ____.", "The Unicorn", emptyList(), listOf("stag", "eagle"))
    private val q3 = BluffQuestion("q3", "Octopuses have ____ hearts.", "three", listOf("3"), listOf("two", "five"))
    private fun pack(vararg qs: BluffQuestion) = BluffPack("test", "Test", "bluff", 1, qs.toList())

    private lateinit var e: PartyEngine
    private var n = 0

    /** Starts Bluff Battle with [names], tutorial skipped, rounds=[rounds], and the given questions. */
    private fun start(names: List<String>, rounds: Int = 3, vararg qs: BluffQuestion = arrayOf(q1, q2, q3)): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(BluffBattle(pack(*qs)))))
        val ids = names.map { e.add(it) }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("bluff", mapOf("rounds" to rounds))))
        e.host(HostCmd.SkipPhase)
        return ids
    }

    private val tv get() = e.tvState().stage!!.game as BluffTv
    private val round get() = e.tvState().stage!!.phaseSeq
    private fun write(who: PlayerId, text: String) =
        e.action(who, "w${n++}", round, buildJsonObject { put("kind", JsonPrimitive("write")); put("text", JsonPrimitive(text)) })
    private fun pickText(who: PlayerId, text: String): ActionResult {
        val screen = assertIs<Screen.ChoiceList>(e.phoneState(who).screen)
        val opt = screen.options.first { it.text == text }
        return e.action(who, "p${n++}", round, buildJsonObject { put("kind", JsonPrimitive("pick")); put("option", JsonPrimitive(opt.id)) })
    }
    private fun score(id: PlayerId) = e.tvState().scores.single { it.id == id }.score
    private fun toPick() { e.host(HostCmd.SkipPhase); assertEquals("pick", tv.phase) }
    private fun toReveal() { e.host(HostCmd.SkipPhase); assertEquals("reveal", tv.phase) }

    @Test fun tooTrueIsRejectedAfterNormalisation() {
        val (a) = start(listOf("A", "B", "C"))
        val prompt = tv.prompt
        val truths = if (prompt == q1.prompt) listOf("CUBES!", "  cube ", "Squares") else if (prompt == q2.prompt) listOf("unicorn", "a UNICORN.") else listOf("Three", "3")
        for (t in truths) assertEquals(ActionResult.Rejected("TOO_TRUE"), write(a, t), t)
        assertEquals(ActionResult.Ack, write(a, "something else"))
    }

    @Test fun blankOrTooLongFakesAreRejected() {
        val (a) = start(listOf("A", "B", "C"))
        assertEquals(ActionResult.Rejected("BAD_TEXT"), write(a, "   "))
        assertEquals(ActionResult.Rejected("BAD_TEXT"), write(a, "x".repeat(61)))
    }

    @Test fun identicalFakesMergeAndCreditBothAuthors() {
        val (a, b, c, d) = start(listOf("A", "B", "C", "D"), rounds = 3)
        write(a, "Pyramids"); write(b, "pyramids!"); write(c, "zzz"); write(d, "yyy")
        assertEquals("pick", tv.phase)
        assertEquals(1, tv.options.count { it.equals("Pyramids", ignoreCase = true) })
        pickText(c, "Pyramids"); pickText(d, "Pyramids")
        pickText(a, "zzz"); pickText(b, "zzz")
        assertEquals("reveal", tv.phase)
        assertEquals(1000, score(a)); assertEquals(1000, score(b))
    }

    @Test fun playersNeverSeeTheirOwnFake() {
        val (a, b, c) = start(listOf("A", "B", "C"))
        write(a, "mine"); write(b, "bees"); write(c, "cats")
        val options = assertIs<Screen.ChoiceList>(e.phoneState(a).screen).options.map { it.text }
        assertTrue("mine" !in options)
        assertTrue("bees" in options)
        val ownId = assertIs<Screen.ChoiceList>(e.phoneState(b).screen).options.first { it.text == "mine" }.id
        assertEquals(ActionResult.Rejected("OWN_ANSWER"), e.action(a, "own", round, buildJsonObject { put("kind", JsonPrimitive("pick")); put("option", JsonPrimitive(ownId)) }))
    }

    @Test fun decoysFillToThreeOptions() {
        start(listOf("A", "B", "C"))
        toPick()
        assertEquals(3, tv.options.size)
    }

    @Test fun truthEarnsThousandAndEachFooledPlayerEarnsFiveHundred() {
        val (a, b, c, d) = start(listOf("A", "B", "C", "D"))
        val truth = currentQuestion().answer
        write(a, "fake a"); write(b, "fake b"); write(c, "fake c"); write(d, "fake d")
        pickText(a, truth); pickText(b, "fake a"); pickText(c, "fake a"); pickText(d, "fake c")
        assertEquals(1000 + 1000, score(a))
        assertEquals(0, score(b))
        assertEquals(500, score(c))
        assertEquals(0, score(d))
    }

    @Test fun finalRoundDoublesPoints() {
        val (a, b, c) = start(listOf("A", "B", "C"), rounds = 3)
        repeat(2) { e.host(HostCmd.SkipPhase); e.host(HostCmd.SkipPhase); e.host(HostCmd.SkipPhase); e.host(HostCmd.SkipPhase) }
        assertEquals(3, tv.round); assertTrue(tv.finalRound)
        write(a, "fa"); write(b, "fb"); write(c, "fc")
        pickText(a, currentQuestion().answer); pickText(b, "fa"); pickText(c, "fa")
        assertEquals(2000 + 2 * 1000, score(a))
    }

    @Test fun lateJoinerWaitsForNextRound() {
        val (a) = start(listOf("A", "B", "C"))
        val late = e.add("Late")
        assertEquals(ActionResult.Rejected("NEXT_ROUND"), write(late, "hello"))
        assertIs<Screen.Waiting>(e.phoneState(late).screen)
        repeat(4) { e.host(HostCmd.SkipPhase) }
        assertEquals("write", tv.phase); assertEquals(2, tv.round)
        assertEquals(ActionResult.Ack, write(late, "hello"))
        assertIs<Screen.TextEntry>(e.phoneState(a).screen)
    }

    @Test fun noQuestionRepeatsWithinParty() {
        start(listOf("A", "B", "C"), rounds = 3)
        val seen = mutableListOf(tv.prompt)
        repeat(2) { repeat(4) { e.host(HostCmd.SkipPhase) }; seen += tv.prompt }
        assertEquals(3, seen.toSet().size)
        repeat(4) { e.host(HostCmd.SkipPhase) }
        assertEquals("podium", tv.phase)
        e.host(HostCmd.SkipPhase)
        assertNull(e.tvState().stage)
        e.host(HostCmd.StartGame("bluff", mapOf("rounds" to 3))); e.host(HostCmd.SkipPhase)
        assertEquals("podium", tv.phase, "exhausted pack must end rather than repeat")
    }

    @Test fun awayAuthorsFakeStillCounts() {
        val (a, b, c, d) = start(listOf("A", "B", "C", "D"))
        write(a, "sneaky")
        e.setPresence(a, false)
        write(b, "fb"); write(c, "fc"); write(d, "fd")
        assertEquals("pick", tv.phase)
        pickText(b, "sneaky"); pickText(c, "sneaky"); pickText(d, "fb")
        assertEquals("reveal", tv.phase)
        assertEquals(1000, score(a))
    }

    @Test fun revealOrderIsAscendingByFooledWithTruthLast() {
        val (a, b, c, d) = start(listOf("A", "B", "C", "D"))
        write(a, "one"); write(b, "two"); write(c, "none"); write(d, "dd")
        pickText(b, "one"); pickText(c, "one"); pickText(d, "two"); pickText(a, "two")
        val reveal = tv.reveal
        assertEquals("truth", reveal.last().kind)
        val fooled = reveal.dropLast(1).map { it.fooled.size }
        assertEquals(fooled.sorted(), fooled)
        assertEquals(listOf("B", "C"), reveal.first { it.text == "one" }.fooled)
        assertEquals(listOf("A"), reveal.first { it.text == "one" }.authors)
    }

    @Test fun podiumFinishesGameWithStandings() {
        start(listOf("A", "B", "C"), rounds = 3)
        repeat(12) { e.host(HostCmd.SkipPhase) }
        assertEquals("podium", tv.phase)
        assertIs<Screen.Scores>(e.phoneState(e.tvState().players.first().id).screen)
        e.host(HostCmd.SkipPhase)
        assertEquals("bluff", assertNotNull(e.tvState().lastResult).gameId)
    }

    @Test fun packValidationRejectsBadItems() {
        assertFailsWith<IllegalArgumentException> { BluffPack.validate(pack(q1.copy(decoys = listOf("x")))) }
        assertFailsWith<IllegalArgumentException> { BluffPack.validate(pack(q1.copy(decoys = listOf("Cubes", "x")))) }
        assertFailsWith<IllegalArgumentException> { BluffPack.validate(pack(q1, q1)) }
        assertFailsWith<IllegalArgumentException> { BluffPack.validate(pack(q1).copy(game = "other")) }
    }

    @Test fun corePackLoadsWithAtLeastSixtyQuestions() {
        val core = BluffPack.core()
        assertTrue(core.items.size >= 60, "only ${core.items.size}")
    }

    @Test fun normaliseStripsCasePunctuationAndLeadingArticles() {
        assertEquals("eiffeltower", normalise("  The Eiffel-Tower!! "))
        assertEquals("unicorn", normalise("a unicorn"))
        assertEquals("anteater", normalise("An anteater"))
        assertEquals("theatre", normalise("Theatre"))
    }

    private fun currentQuestion() = listOf(q1, q2, q3).single { it.prompt == tv.prompt }
}
