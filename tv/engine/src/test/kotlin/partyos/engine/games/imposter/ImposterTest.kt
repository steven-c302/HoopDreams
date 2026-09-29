package partyos.engine.games.imposter

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.ImposterTv
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImposterTest {
    private val clock = FakeClock(0)
    private val pack = ImposterPack(
        "test", "Test", "imposter", 1,
        listOf(
            ImposterCategory("food", "Food", listOf("pizza", "taco", "sushi", "burger", "pancake", "lasagna", "burrito", "ramen")),
            ImposterCategory("animals", "Animals", listOf("giraffe", "penguin", "octopus", "kangaroo", "dolphin", "hedgehog", "flamingo", "panda")),
        ),
    )
    private val allWords = pack.words().map { it.word }.toSet()

    private lateinit var e: PartyEngine
    private var n = 0

    /** Starts Imposter with [count] players, the tutorial skipped, in the role phase. */
    private fun start(count: Int, rounds: Int = 3, settings: Map<String, Int> = emptyMap()): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Imposter(pack))))
        val ids = (1..count).map { e.add("P$it") }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("imposter", mapOf("rounds" to rounds) + settings)))
        e.host(HostCmd.SkipPhase)
        return ids
    }

    private val tv get() = e.tvState().stage!!.game as ImposterTv
    private val seq get() = e.tvState().stage!!.phaseSeq
    private fun act(who: PlayerId, kind: String, vararg kv: Pair<String, String>) =
        e.action(who, "a${n++}", seq, buildJsonObject { put("kind", JsonPrimitive(kind)); kv.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
    private fun card(who: PlayerId) = assertIs<Screen.Secret>(e.phoneState(who).screen)
    private fun imposters(ids: List<PlayerId>) = ids.filter { card(it).role == "imposter" }
    private fun skip() { e.host(HostCmd.SkipPhase) }
    private fun allSeen(ids: List<PlayerId>) = ids.forEach { act(it, "seen") }

    @Test fun startsInTheRolePhaseWithNothingSecretOnTheTv() {
        start(4)
        assertEquals("role", tv.phase)
        assertTrue(tv.category in setOf("Food", "Animals"))
        assertNull(tv.word)
        assertEquals(1, tv.round)
        assertEquals(3, tv.totalRounds)
    }

    @Test fun oneImposterUnderNineAndTwoAtNine() {
        assertEquals(1, imposters(start(8)).size)
        assertEquals(2, imposters(start(9)).size)
    }

    @Test fun theImposterCardHidesTheWordAndTheCrewCardShowsIt() {
        val ids = start(5)
        val imps = imposters(ids)
        val crew = ids - imps.toSet()
        imps.forEach { assertEquals("IMPOSTER", card(it).face); assertEquals(tv.category, card(it).category) }
        val words = crew.map { card(it).face }.toSet()
        assertEquals(1, words.size)
        assertTrue(words.single() in allWords)
        assertEquals("seen", card(crew.first()).kind)
    }

    @Test fun everyoneLookingMovesOnToClues() {
        val ids = start(4)
        ids.dropLast(1).forEach { assertEquals(ActionResult.Ack, act(it, "seen")) }
        assertEquals("role", tv.phase)
        assertEquals(3, tv.submitted)
        act(ids.last(), "seen")
        assertEquals("clue", tv.phase)
    }

    @Test fun cluesAreOneWordAndCrewCannotTypeTheWord() {
        val ids = start(5)
        val imps = imposters(ids)
        val crew = ids - imps.toSet()
        val word = card(crew.first()).face
        allSeen(ids)
        val c = crew.first()
        assertEquals(ActionResult.Rejected("BAD_TEXT"), act(c, "clue", "text" to "two words"))
        assertEquals(ActionResult.Rejected("BAD_TEXT"), act(c, "clue", "text" to "   "))
        assertEquals(ActionResult.Rejected("BAD_TEXT"), act(c, "clue", "text" to "x".repeat(21)))
        assertEquals(ActionResult.Rejected("TOO_TRUE"), act(c, "clue", "text" to word.uppercase() + "!"))
        assertEquals(ActionResult.Ack, act(c, "clue", "text" to "well-known"))
        assertEquals(ActionResult.Ack, act(c, "clue", "text" to "cheesy")) // changeable until time's up
        // The imposter cannot know the word, so typing it is never TOO_TRUE for them.
        imps.forEach { assertEquals(ActionResult.Ack, act(it, "clue", "text" to word)) }
    }

    @Test fun allCluesInMovesToDiscussAndShowsTheWall() {
        val ids = start(4)
        allSeen(ids)
        ids.forEachIndexed { i, p -> act(p, "clue", "text" to "hint$i") }
        assertEquals("discuss", tv.phase)
        assertEquals(listOf("hint0", "hint1", "hint2", "hint3"), tv.clues.map { it.text })
    }

    @Test fun aMissingClueShowsAsBlank() {
        val ids = start(4)
        allSeen(ids)
        act(ids[0], "clue", "text" to "hint0"); act(ids[1], "clue", "text" to "hint1"); act(ids[2], "clue", "text" to "hint2")
        skip()
        assertEquals("discuss", tv.phase)
        assertEquals(listOf("hint0", "hint1", "hint2", null), tv.clues.map { it.text })
    }

    @Test fun aLateJoinerWatchesUntilTheNextRound() {
        val ids = start(4)
        val late = e.add("Late")
        assertEquals(ActionResult.Rejected("NEXT_ROUND"), act(late, "seen"))
        assertIs<Screen.Waiting>(e.phoneState(late).screen)
        allSeen(ids)
        assertEquals("clue", tv.phase)
    }

    @Test fun theTvNeverCarriesTheWordBeforeTheResult() {
        val ids = start(4)
        val word = card((ids - imposters(ids).toSet()).first()).face
        allSeen(ids)
        assertFalse(tv.toString().contains(word))
        ids.forEachIndexed { i, p -> act(p, "clue", "text" to "hint$i") }
        assertEquals("discuss", tv.phase)
        assertFalse(tv.toString().contains(word))
    }
}
