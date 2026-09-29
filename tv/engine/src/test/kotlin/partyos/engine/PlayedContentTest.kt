package partyos.engine

import partyos.engine.games.bluff.BluffBattle
import partyos.engine.games.bluff.BluffPack
import partyos.engine.games.trivia.BallparkItem
import partyos.engine.games.trivia.BrainDrain
import partyos.engine.games.trivia.McItem
import partyos.engine.games.trivia.SidesItem
import partyos.engine.games.trivia.SidesSet
import partyos.engine.games.trivia.TriviaPack
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlayedContentTest {
    /** [used] oldest first; [tonight] is the part of it played at this party. */
    private fun ctx(used: List<String>, tonight: Set<String> = emptySet()) =
        GameContext(0, Random(1), emptyList(), emptyMap(), emptyMap(), used.toCollection(LinkedHashSet()), playedThisParty = tonight)

    @Test fun freshPrefersUnplayedThenTheOlderHalf() {
        val items = listOf("a", "b", "c", "d", "e", "f")
        assertEquals(listOf("e", "f"), ctx(listOf("a", "b", "c", "d")).fresh(items) { it })
        // All played, "c" longest ago: the older half comes back, the most recent never.
        assertEquals(listOf("c", "a", "f"), ctx(listOf("c", "a", "f", "b", "e", "d")).fresh(items) { it })
        assertEquals(listOf("x"), ctx(listOf("x")).fresh(listOf("x")) { it })
        assertTrue(ctx(emptyList()).fresh(emptyList<String>()) { it }.isEmpty())
    }

    @Test fun nothingPlayedTonightComesBackTonight() {
        val items = listOf("a", "b", "c", "d")
        // a and b are from earlier nights; c and d were played tonight: only an earlier one can come back.
        assertEquals(listOf("a"), ctx(listOf("a", "b", "c", "d"), tonight = setOf("c", "d")).fresh(items) { it })
        // Everything played tonight: nothing comes back (the round ends early, as a used-up pack always did).
        assertTrue(ctx(listOf("a", "b", "c", "d"), tonight = items.toSet()).fresh(items) { it }.isEmpty())
    }

    @Test fun rememberedIdsAreKeptInOrderAndPlayingAgainMovesToTheEnd() {
        val e = PartyEngine(FakeClock(0), SeededEntropy(1))
        e.rememberPlayed(listOf("t1", "t2", "t3"))
        e.rememberPlayed(listOf("t1"))
        assertEquals(listOf("t2", "t3", "t1"), e.snapshot().usedContent)
        e.rememberPlayed((1..PartyEngine.MAX_PLAYED).map { "n$it" })
        assertEquals(PartyEngine.MAX_PLAYED, e.snapshot().usedContent.size)
        assertEquals("n1", e.snapshot().usedContent.first()) // the oldest dropped off first
    }

    private val pack = TriviaPack.validate(
        TriviaPack(
            mc = (1..8).map { McItem("tm$it", "Test", "Question $it?", "Right $it", listOf("Wrong A$it", "Wrong B$it", "Wrong C$it")) },
            ballpark = (1..3).map { BallparkItem("tb$it", "Test", "Number $it?", 100.0 * it) },
            sides = listOf(SidesSet("ts1", "Left or right?", "Lefty", "Righty", (1..5).map { SidesItem("Item $it", if (it % 2 == 1) "left" else "right") })),
            gauntlet = emptyList(),
        ),
    )

    /** Plays a Brain Drain show through its first Quick Draw round and returns the questions asked. */
    private fun quickDrawQuestions(e: PartyEngine): List<String> {
        repeat(4) { e.add("P$it") }
        e.host(HostCmd.StartGame("trivia", mapOf("rounds" to 3, "teams" to 2)))
        val asked = mutableListOf<String>()
        fun tv() = e.tvState().stage!!.game as TriviaTv
        e.host(HostCmd.SkipPhase) // tutorial
        while (!(tv().format == "quick" && tv().phase == "standings")) {
            if (tv().phase == "question") asked += tv().prompt
            e.host(HostCmd.SkipPhase)
        }
        return asked
    }

    @Test fun questionsPlayedOnAnEarlierNightDontComeBack() {
        val e = PartyEngine(FakeClock(0), SeededEntropy(2), GameRegistry(listOf(BrainDrain(pack, shuffleRounds = false))))
        e.rememberPlayed((1..5).map { "tm$it" })
        val asked = quickDrawQuestions(e)
        assertEquals(listOf("Question 6?", "Question 7?", "Question 8?"), asked.sorted())
    }

    @Test fun onceEveryQuestionIsPlayedTheOldestComeBackAndTheRoundStillHappens() {
        val e = PartyEngine(FakeClock(0), SeededEntropy(3), GameRegistry(listOf(BrainDrain(pack, shuffleRounds = false))))
        e.rememberPlayed((1..8).map { "tm$it" }) // tm1 longest ago
        val asked = quickDrawQuestions(e)
        assertEquals(3, asked.size)
        // Only the older half comes back. Each one asked becomes the newest, so the window slides by one per question:
        // three questions can reach tm6 at most, and the two played last (tm7, tm8) never come back.
        assertTrue(asked.all { it in (1..6).map { n -> "Question $n?" } }, "asked $asked")
        assertEquals(3, asked.toSet().size) // and none twice in one round
    }

    @Test fun bluffBattleStillPlaysOnceEveryPromptHasBeenUsed() {
        val e = PartyEngine(FakeClock(0), SeededEntropy(4), GameRegistry(listOf(BluffBattle())))
        e.rememberPlayed(BluffPack.core().items.map { it.id })
        repeat(3) { e.add("P$it") }
        e.host(HostCmd.StartGame("bluff", mapOf("rounds" to 3)))
        e.host(HostCmd.SkipPhase) // tutorial
        assertEquals("write", assertIs<BluffTv>(e.tvState().stage!!.game).phase)
    }
}
