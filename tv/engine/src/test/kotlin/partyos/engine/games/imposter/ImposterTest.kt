package partyos.engine.games.imposter

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.Avatar
import partyos.engine.JoinResult
import partyos.engine.Role
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

    private data class Round(val imps: List<PlayerId>, val crew: List<PlayerId>, val word: String)

    /** Plays a fresh round from the role phase to the vote phase, everyone giving a harmless clue. */
    private fun toVote(ids: List<PlayerId>): Round {
        val imps = imposters(ids)
        val crew = ids - imps.toSet()
        val word = card(crew.first()).face
        allSeen(ids)
        ids.forEachIndexed { i, p -> assertEquals(ActionResult.Ack, act(p, "clue", "text" to "hint$i")) }
        assertEquals("discuss", tv.phase)
        skip()
        assertEquals("vote", tv.phase)
        return Round(imps, crew, word)
    }
    private fun vote(who: PlayerId, target: PlayerId) = act(who, "vote", "option" to target.v)
    private fun score(id: PlayerId) = e.tvState().scores.single { it.id == id }.score

    @Test fun theVoteListShowsEveryoneElseAsFaces() {
        val ids = start(4)
        toVote(ids)
        val screen = assertIs<Screen.ChoiceList>(e.phoneState(ids[0]).screen)
        assertEquals("vote", screen.kind)
        assertEquals("faces", screen.style)
        assertEquals(ids.drop(1).map { it.v }, screen.options.map { it.id })
    }

    @Test fun cannotVoteForYourselfOrAStranger() {
        val ids = start(4)
        toVote(ids)
        assertEquals(ActionResult.Rejected("OWN_VOTE"), vote(ids[0], ids[0]))
        assertEquals(ActionResult.Rejected("BAD_OPTION"), act(ids[0], "vote", "option" to "nobody"))
        assertEquals(ActionResult.Ack, vote(ids[0], ids[1]))
    }

    @Test fun crewWhoNameTheImposterScoreAndTheImposterIsAccused() {
        val ids = start(4)
        val r = toVote(ids)
        val imp = r.imps.single()
        r.crew.forEach { vote(it, imp) }
        vote(imp, r.crew.first())
        assertEquals("result", tv.phase)
        assertEquals(listOf(imp), tv.accused)
        assertEquals(listOf(imp), tv.imposters)
        assertEquals(4, tv.votes.size)
        r.crew.forEach { assertEquals(1000, score(it)) }
        assertEquals(0, score(imp))
        assertNull(tv.word) // the caught imposter still has to guess
        assertEquals("Caught! Drink 2 sips", tv.drinks.single { it.id == imp }.text)
        assertTrue(tv.drinks.none { it.id in r.crew })
    }

    @Test fun aTieForFirstLetsTheImposterSurvive() {
        val ids = start(4)
        val r = toVote(ids)
        val imp = r.imps.single()
        val (c0, c1, c2) = r.crew
        vote(c0, imp); vote(c1, c2); vote(c2, c1); vote(imp, c0)
        assertEquals("result", tv.phase)
        assertTrue(tv.accused.isEmpty())
        assertEquals(1000, score(c0)) // named the imposter, even though the room did not accuse them
        assertEquals(1500, score(imp))
        assertEquals(0, score(c1))
        assertEquals(r.word, tv.word) // nobody to guess, so the word can show now
        assertEquals("Voted for an innocent. Drink 1 sip", tv.drinks.single { it.id == c1 }.text)
    }

    @Test fun aVoteCanChangeUntilTheEnd() {
        val ids = start(4)
        val r = toVote(ids)
        val imp = r.imps.single()
        val (c0, c1, c2) = r.crew
        vote(c0, c1); vote(c0, imp)
        vote(c1, imp); vote(c2, imp)
        vote(imp, c0)
        assertEquals(1000, score(c0))
    }

    @Test fun noVotesMeansTheImposterSurvives() {
        val ids = start(4)
        val r = toVote(ids)
        skip()
        assertEquals("result", tv.phase)
        assertTrue(tv.accused.isEmpty())
        assertEquals(1500, score(r.imps.single()))
    }

    @Test fun resultMovesOnToScoresWhenNobodyWasCaught() {
        val ids = start(4)
        toVote(ids)
        skip() // no votes
        assertEquals("result", tv.phase)
        skip()
        assertEquals("scores", tv.phase)
        assertTrue(tv.deltas.isNotEmpty())
    }

    @Test fun drinkLinesFollowTheLobbySwitch() {
        val ids = start(4, settings = mapOf("drinks" to 0))
        val r = toVote(ids)
        r.crew.forEach { vote(it, r.imps.single()) }
        vote(r.imps.single(), r.crew.first())
        assertTrue(tv.drinks.isEmpty())
    }

    /** Everyone names the imposter (who names a crew member), so the imposter is accused and reaches the guess phase. */
    private fun catchTheImposter(r: Round) {
        val imp = r.imps.single()
        r.crew.forEach { vote(it, imp) }
        vote(imp, r.crew.first())
        assertEquals("result", tv.phase)
        skip()
        assertEquals("guess", tv.phase)
    }

    @Test fun aCaughtImposterGuessesTheWordAndScores() {
        val ids = start(4)
        val r = toVote(ids)
        catchTheImposter(r)
        val imp = r.imps.single()
        val screen = assertIs<Screen.TextEntry>(e.phoneState(imp).screen)
        assertEquals("guess", screen.kind)
        assertIs<Screen.Waiting>(e.phoneState(r.crew.first()).screen)
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(r.crew.first(), "guess", "text" to r.word))
        assertNull(tv.word)
        assertEquals(ActionResult.Ack, act(imp, "guess", "text" to r.word.uppercase()))
        assertEquals("scores", tv.phase) // the only accused imposter has guessed, so the phase ends early
        assertEquals(1000, score(imp))
        assertEquals(r.word, tv.word)
        assertTrue(tv.guesses.single().right)
    }

    @Test fun aWrongGuessScoresNothing() {
        val ids = start(4)
        val r = toVote(ids)
        catchTheImposter(r)
        act(r.imps.single(), "guess", "text" to "spaceship")
        assertEquals("scores", tv.phase)
        assertEquals(0, score(r.imps.single()))
        assertFalse(tv.guesses.single().right)
    }

    @Test fun aBlankGuessIsRejected() {
        val ids = start(4)
        val r = toVote(ids)
        catchTheImposter(r)
        assertEquals(ActionResult.Rejected("BAD_TEXT"), act(r.imps.single(), "guess", "text" to "   "))
    }

    @Test fun theGuessPhaseEndsWhenTheCaughtImposterDrops() {
        val ids = start(4)
        val r = toVote(ids)
        catchTheImposter(r)
        e.setPresence(r.imps.single(), false)
        assertEquals("scores", tv.phase)
    }

    @Test fun theRoundsRunToAPodiumAndTheGameFinishes() {
        val ids = start(4, rounds = 3)
        for (round in 1..3) {
            assertEquals(round, tv.round)
            val r = toVote(ids)
            val imp = r.imps.single()
            r.crew.forEach { vote(it, imp) }
            vote(imp, r.crew.first())
            skip() // result -> guess
            skip() // guess -> scores (no guess made)
            assertEquals("scores", tv.phase)
            if (round == 3) {
                assertTrue(tv.finalRound)
                r.crew.forEach { c -> assertEquals(2000, tv.deltas.single { it.id == c }.points) } // the last round doubles
            } else {
                r.crew.forEach { c -> assertEquals(1000, tv.deltas.single { it.id == c }.points) }
            }
            skip() // scores -> next round | podium
        }
        assertEquals("podium", tv.phase)
        skip()
        assertNull(e.tvState().stage)
        assertTrue(e.tvState().lastResult != null)
    }

    @Test fun aSavedGameRestoresMidRoundWithTheSameRoles() {
        val ids = start(5)
        allSeen(ids)
        act(ids[0], "clue", "text" to "hint0"); act(ids[1], "clue", "text" to "hint1")
        val roles = ids.associateWith { card(it).role }
        val restored = PartyEngine.restore(e.snapshot(), clock, SeededEntropy(4), GameRegistry(listOf(Imposter(pack))))
        assertEquals("clue", (restored.tvState().stage!!.game as ImposterTv).phase)
        ids.forEach { assertEquals(roles.getValue(it), assertIs<Screen.Secret>(restored.phoneState(it).screen).role) }
        assertEquals("hint0", assertIs<Screen.Secret>(restored.phoneState(ids[0]).screen).input?.value)
    }
}
