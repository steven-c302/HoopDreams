# Brain Drain Final Wager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After the last round, every team makes a secret wager, answers a typed question, and sees a last-place-first reveal that decides the winner.

**Architecture:** A pure `Finale` object (wager options by standing, amounts, a team's wager, settlement) plus four new `BrainDrain` phases (`final_category`, `final_wager`, `final_question`, `final_reveal`) inserted between the last round's standings and the podium. Phones reuse existing screens (`ChoiceList`, `TextEntry`, `Waiting`). The TV gets one `Finale` component that animates the reveal from the phase clock.

**Tech Stack:** Kotlin (engine, `kotlin.test`, Gradle), kotlinx.serialization, TypeScript/React (TV stage), Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-10-02-brain-drain-final-wager-design.md`. Deliberate refinements of the spec, all settled in this plan: (1) "bottom half may go ALL IN" is defined as `teams scoring strictly lower than you < teams / 2` (rounded down), so teams tied for last all qualify and teams tied for first never do; (2) the wager phase ends early once every team has at least one pick (not every player); (3) the phone already vibrates on picks, text submits and win/lose results (`ScreenView.tsx` generic `buzz`), so there is no phone-vibration task.

## Global Constraints

- Phases: `final_category` 7 000 ms, `final_wager` 20 000 ms, `final_question` 40 000 ms (the last two scaled by `ctx.timer`), `final_reveal` `5 000 ms x teams + 4 000 ms` (never scaled).
- Wager options: `w0` (0), `w25`, `w50`, `w75`, and `wall` (ALL IN, 100%), amount = `max(score, 1000) x percent`, rounded to the nearest 50.
- ALL IN only for teams where `count(teams with a strictly lower score) < teams / 2` (integer division).
- A team's wager is the option most of its players picked (among options offered to it), ties to the lower amount; no pick means `w0`.
- Right answer adds the wager; wrong or missing subtracts `min(wager, score)`; scores never go below zero.
- Typed answers: `cleanText(..., MAX_WRITE)` and `AnswerMatch` (as Write It Down); a team's answer is its most-written one (`teamWrite`).
- Question: from `pickMc` with `writable(item) && item.answer.length <= 20`; if none, skip the finale (go to the podium).
- Scores change once, when the reveal ends (deadline or host skip); the TV shows "before" scores during the reveal and animates the changes.
- Brain Drain only: no finale for Write It Down on its own (`Mode.WRITE`), nor for shows containing the retired Gauntlet; fewer than two active teams: no finale.
- Wager amounts are never sent to the TV before `final_reveal`; the answer is not sent before it either.
- Saved shows from before this change must still load (new state field defaults to null).
- Run Kotlin tests with `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test --tests "<class>"`. TS checks from `controller/`: `npx tsc -b` and `npx vitest run`.

## Review Focus

- A last-place team on 0 going ALL IN and being wrong: its score stays 0 and the reported delta is 0 (Task 1 `settle`, Task 2 flow test).
- Teams tied for last place on a 3-team show both get ALL IN; teams tied for first never do (Task 1 `tiesShareTheirStanding`).
- Nobody taps a wager or types an answer: the finale still runs to the podium with no changes and no stall (Task 2 `anEmptyFinaleStillEnds`).
- A leader tries ALL IN: rejected with `BAD_OPTION` (Task 2 `onlyUnderdogsMayGoAllIn`).
- Every question in the pool has a long answer, so no finale question exists: the show goes straight to the podium (Task 2 `noShortQuestionSkipsTheFinale`).
- The host skips the reveal: scores apply exactly once (Task 2 `skippingTheRevealAppliesScoresOnce`).

---

## File Structure

- Create `tv/engine/src/main/kotlin/partyos/engine/games/trivia/Finale.kt`: `FWager`, `FinaleState`, the `Finale` rules. Pure; keeps `BrainDrain.kt` from growing further.
- Create `tv/engine/src/test/kotlin/partyos/engine/games/trivia/FinaleTest.kt`.
- Modify `tv/engine/src/main/kotlin/partyos/engine/TriviaViews.kt`: `FinaleResult`, `FinaleInfo`, `TriviaTv.finale`.
- Modify `tv/engine/src/main/kotlin/partyos/engine/games/trivia/BrainDrain.kt`: phases, state, input, flow, views.
- Modify `tv/engine/src/test/kotlin/partyos/engine/games/trivia/BrainDrainTest.kt`.
- Modify `tv/server/src/test/kotlin/partyos/server/ProtocolFixturesTest.kt`; regenerate `controller/src/protocol/fixtures/*.json`.
- Modify `controller/src/protocol.test.ts`, `controller/src/tv/types.ts`, `controller/src/tv/TriviaStage.tsx`, `controller/src/tv/trivia.css`, `controller/src/tv/Gallery.tsx`, `controller/scripts/shots.mjs`.
- Create `controller/e2e/final-wager.spec.ts`; modify `docs/party-os/show-bible.md`.

---

### Task 1: The finale rules (pure)

**Files:**
- Create: `tv/engine/src/main/kotlin/partyos/engine/games/trivia/Finale.kt`
- Modify: `tv/engine/src/main/kotlin/partyos/engine/TriviaViews.kt` (add `FinaleResult`, `FinaleInfo` at the end of the file)
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/trivia/FinaleTest.kt`

**Interfaces:**
- Produces (used by Tasks 2-5):
  - `partyos.engine.FinaleResult(team: String, text: String? = null, right: Boolean = false, option: String = "w0", wager: Int = 0, delta: Int = 0, before: Int = 0, after: Int = 0)`
  - `partyos.engine.FinaleInfo(category: String, locked: List<String> = emptyList(), results: List<FinaleResult> = emptyList(), answerText: String? = null)`
  - `FWager(option: String, at: Long = 0)`; `FinaleState(category: String = "", answerText: String = "", wagers: Map<String, FWager> = emptyMap(), results: List<FinaleResult> = emptyList())`
  - `Finale.CATEGORY_MS`, `WAGER_MS`, `QUESTION_MS`, `STEP_MS`, `LEAD_PAD_MS` (Long), `FLOOR`, `MAX_ANSWER` (Int), `NONE = "w0"`, `ALL_IN = "wall"`
  - `Finale.revealMs(teams: Int): Long`
  - `Finale.underdog(score: Int, scores: List<Int>): Boolean`
  - `Finale.options(score: Int, scores: List<Int>): List<String>`
  - `Finale.amount(option: String, score: Int): Int`
  - `Finale.teamWager(members: List<PlayerId>, wagers: Map<String, FWager>, offered: List<String>): String`
  - `Finale.settle(score: Int, wager: Int, right: Boolean): Int` (the real change to the score)

- [ ] **Step 1: Add the view data classes**

Append to `tv/engine/src/main/kotlin/partyos/engine/TriviaViews.kt`:

```kotlin
/** One team's result in the Final Wager reveal. [before] and [after] are its score around the wager; [text] is its typed answer. */
@Serializable
data class FinaleResult(
    val team: String,
    val text: String? = null,
    val right: Boolean = false,
    val option: String = "w0",
    val wager: Int = 0,
    val delta: Int = 0,
    val before: Int = 0,
    val after: Int = 0,
)

/** The Final Wager on the TV: the category, which teams have wagered, and (reveal only) the answer and every result, last place first. */
@Serializable
data class FinaleInfo(
    val category: String,
    val locked: List<String> = emptyList(),
    val results: List<FinaleResult> = emptyList(),
    val answerText: String? = null,
)
```

- [ ] **Step 2: Write the failing tests**

Create `tv/engine/src/test/kotlin/partyos/engine/games/trivia/FinaleTest.kt`:

```kotlin
package partyos.engine.games.trivia

import partyos.engine.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinaleTest {
    private val base = listOf("w0", "w25", "w50", "w75")

    @Test fun onlyTheBottomHalfMayGoAllIn() {
        // Two teams: only the one behind. Three: only last. Four: the bottom two. Five: the bottom two.
        assertEquals(base, Finale.options(1500, listOf(1500, 0)))
        assertEquals(base + "wall", Finale.options(0, listOf(1500, 0)))
        assertEquals(base, Finale.options(2000, listOf(2000, 1500, 0)))
        assertEquals(base, Finale.options(1500, listOf(2000, 1500, 0)))
        assertEquals(base + "wall", Finale.options(0, listOf(2000, 1500, 0)))
        assertEquals(base, Finale.options(3000, listOf(3000, 2000, 1000, 0)))
        assertEquals(base, Finale.options(2000, listOf(3000, 2000, 1000, 0)))
        assertEquals(base + "wall", Finale.options(1000, listOf(3000, 2000, 1000, 0)))
        assertEquals(base + "wall", Finale.options(0, listOf(3000, 2000, 1000, 0)))
        assertFalse("wall" in Finale.options(3000, listOf(5000, 4000, 3000, 2000, 1000)))
        assertTrue("wall" in Finale.options(2000, listOf(5000, 4000, 3000, 2000, 1000)))
        assertTrue("wall" in Finale.options(1000, listOf(5000, 4000, 3000, 2000, 1000)))
    }

    @Test fun tiesShareTheirStanding() {
        // Two teams tied for last of three both qualify; two tied for first of three never do.
        assertTrue("wall" in Finale.options(0, listOf(1500, 0, 0)))
        assertFalse("wall" in Finale.options(1500, listOf(1500, 1500, 0)))
        // Everyone level: everyone is an underdog.
        assertTrue("wall" in Finale.options(500, listOf(500, 500, 500)))
    }

    @Test fun amountsAreAPercentOfTheScoreWithAFloorRoundedToFifty() {
        assertEquals(0, Finale.amount("w0", 4000))
        assertEquals(1000, Finale.amount("w25", 4000))
        assertEquals(3000, Finale.amount("w75", 4000))
        assertEquals(4000, Finale.amount("wall", 4000))
        assertEquals(300, Finale.amount("w25", 1234)) // 308 rounds to 300
        assertEquals(250, Finale.amount("w25", 0)) // the floor: a team on nothing bets from 1000
        assertEquals(1000, Finale.amount("wall", 200))
        assertEquals(0, Finale.amount("nonsense", 4000))
    }

    private val p = listOf("p1", "p2", "p3").map(::PlayerId)
    private val offered = base + "wall"

    @Test fun theTeamWagersWhatMostPlayersPickedAndTiesGoLower() {
        val w = mapOf("p1" to FWager("w50", 1), "p2" to FWager("w50", 2), "p3" to FWager("wall", 3))
        assertEquals("w50", Finale.teamWager(p, w, offered))
        val tie = mapOf("p1" to FWager("wall", 1), "p2" to FWager("w25", 2))
        assertEquals("w25", Finale.teamWager(p, tie, offered))
    }

    @Test fun nobodyPickingOrAPickNotOfferedMeansNoWager() {
        assertEquals("w0", Finale.teamWager(p, emptyMap(), offered))
        assertEquals("w0", Finale.teamWager(p, mapOf("p1" to FWager("wall", 1)), base))
    }

    @Test fun rightAddsTheWagerAndWrongCostsAtMostTheScore() {
        assertEquals(750, Finale.settle(1500, 750, right = true))
        assertEquals(-750, Finale.settle(1500, 750, right = false))
        assertEquals(-200, Finale.settle(200, 1000, right = false)) // never more than the team has
        assertEquals(0, Finale.settle(0, 1000, right = false)) // a team on 0 loses nothing
        assertEquals(1000, Finale.settle(0, 1000, right = true))
    }

    @Test fun theRevealLastsAStepPerTeamPlusTheLeadersPause() {
        assertEquals(5_000L * 4 + 4_000L, Finale.revealMs(4))
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test --tests "partyos.engine.games.trivia.FinaleTest"`
Expected: compile FAIL, `Unresolved reference: Finale` and `FWager`.

- [ ] **Step 4: Write the implementation**

Create `tv/engine/src/main/kotlin/partyos/engine/games/trivia/Finale.kt`:

```kotlin
package partyos.engine.games.trivia

import kotlinx.serialization.Serializable
import partyos.engine.FinaleResult
import partyos.engine.PlayerId

/** A player's wager pick in the Final Wager: one of the option ids in [Finale] (`w0`, `w25`, `w50`, `w75`, `wall`). */
@Serializable
data class FWager(val option: String, val at: Long = 0)

/** The Final Wager as it plays: the category, the answer, everyone's wager picks, and (from the reveal on) the results. */
@Serializable
data class FinaleState(
    val category: String = "",
    val answerText: String = "",
    /** player id → their pick. */
    val wagers: Map<String, FWager> = emptyMap(),
    /** Last place first; the leader is last. Empty until the question closes. */
    val results: List<FinaleResult> = emptyList(),
)

/** The rules of the Final Wager. Pure: no state, no engine. */
object Finale {
    const val CATEGORY_MS = 7_000L
    const val WAGER_MS = 20_000L
    const val QUESTION_MS = 40_000L
    /** One team's moment in the reveal, and the extra pause before the leader's. */
    const val STEP_MS = 5_000L
    const val LEAD_PAD_MS = 4_000L
    /** Percentages are of at least this, so a team on little still has a meaningful bet. */
    const val FLOOR = 1000
    const val MAX_ANSWER = 20
    const val NONE = "w0"
    const val ALL_IN = "wall"

    private val PERCENT = linkedMapOf("w0" to 0, "w25" to 25, "w50" to 50, "w75" to 75, ALL_IN to 100)

    fun revealMs(teams: Int): Long = STEP_MS * teams + LEAD_PAD_MS

    /** In the bottom half: fewer than half the teams (rounded down) score strictly less than this one. Ties share a standing. */
    fun underdog(score: Int, scores: List<Int>): Boolean = scores.count { it < score } < scores.size / 2

    /** The wagers a team may pick, safest first; ALL IN only for underdogs. */
    fun options(score: Int, scores: List<Int>): List<String> =
        PERCENT.keys.filter { it != ALL_IN } + if (underdog(score, scores)) listOf(ALL_IN) else emptyList()

    /** What an option is worth to a team on [score]: that percent of `max(score, FLOOR)`, to the nearest 50. */
    fun amount(option: String, score: Int): Int {
        val percent = PERCENT[option] ?: return 0
        val raw = maxOf(score, FLOOR) * percent / 100
        return (raw + 25) / 50 * 50
    }

    /**
     * The team's wager: the offered option most of its players picked, ties to the smaller one; [NONE] if nobody
     * picked (or what they picked wasn't offered to this team).
     */
    fun teamWager(members: List<PlayerId>, wagers: Map<String, FWager>, offered: List<String>): String {
        val picks = members.mapNotNull { wagers[it.v]?.option }.filter { it in offered }
        if (picks.isEmpty()) return NONE
        return picks.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { PERCENT.getValue(it.key) })
            .first().key
    }

    /** The real change to a team's score: the wager if right; if wrong or missing, the wager but never more than the score. */
    fun settle(score: Int, wager: Int, right: Boolean): Int = if (right) wager else -minOf(wager, score.coerceAtLeast(0))
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test --tests "partyos.engine.games.trivia.FinaleTest"`
Expected: BUILD SUCCESSFUL, all `FinaleTest` tests pass.

- [ ] **Step 6: Commit**

```bash
git add tv/engine/src
git commit -m "feat(trivia): Final Wager rules (options by standing, amounts, team wager, settlement)"
```

---

### Task 2: The finale flow in the engine

**Files:**
- Modify: `tv/engine/src/main/kotlin/partyos/engine/TriviaViews.kt` (add `TriviaTv.finale`)
- Modify: `tv/engine/src/main/kotlin/partyos/engine/games/trivia/BrainDrain.kt`
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/trivia/BrainDrainTest.kt`

**Interfaces:**
- Consumes: everything Task 1 produced; existing `pickMc`, `writable`, `teamWrite`, `teamOf`, `awardTeam`, `podium`, `cleanText`, `MAX_WRITE`.
- Produces (used by Tasks 3-5): phase constants `BrainDrain.FINAL = "final"` (the `format` during the finale), `FINAL_CATEGORY = "final_category"`, `FINAL_WAGER = "final_wager"`, `FINAL_QUESTION = "final_question"`, `FINAL_REVEAL = "final_reveal"`; `TriviaState.finale: FinaleState?`; actions `"finalWager"` (field `option`) and `"finalAnswer"` (field `text`); `TriviaTv.finale: FinaleInfo?`.

- [ ] **Step 1: Add `TriviaTv.finale` so tests can compile**

In `TriviaViews.kt`, in `TriviaTv`, add after `val bet: BetInfo? = null,`:

```kotlin
    /** The Final Wager: the category, who has wagered, and (reveal only) the answer and every result. Absent outside the finale. */
    val finale: FinaleInfo? = null,
```

and extend the `phase` doc comment to `teamup | intro | question | bet | reveal | victim | steal | standings | final_category | final_wager | final_question | final_reveal | podium`.

- [ ] **Step 2: Write the failing flow tests**

Add to `BrainDrainTest.kt` (before `pickASideScoresEachCall`; helpers `fourInTwoTeams`, `answer`, `currentRight`, `options`, `act`, `score`, `tv`, `e`, `clock`, `pack`, `engine`, `startShow`, `join`, `name` already exist):

```kotlin
    /** Four players in two teams, T1 (a, b) ahead 1500 to 0, skipped through to the Final Wager's category slam. */
    private fun toFinale(): List<PlayerId> {
        val ids = fourInTwoTeams()
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
                mc = (1..8).map { McItem("tm$it", "Test", "Question $it?", "An answer that is far too long to type $it", listOf("Wrong A$it", "Wrong B$it", "Wrong C$it"), "Fact $it") },
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
```

Also, in the existing test `writeItDownOnItsOwnIsThreeRoundsOfTypedAnswersThenAPodium`, inside its `while (tv.phase != "podium") {` loop, add as the first line: `assertTrue(tv.phase !in setOf("final_category", "final_wager", "final_question", "final_reveal"), "Write It Down has no finale")`.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test --tests "partyos.engine.games.trivia.BrainDrainTest"`
Expected: compile FAIL on `TriviaState.finale`.

- [ ] **Step 4: Add the state, constants and the finale's entry points**

In `BrainDrain.kt`:

1. Imports (alphabetical with the other `partyos.engine.*`): `import partyos.engine.FinaleInfo` and `import partyos.engine.FinaleResult`.
2. In `TriviaState`, after `val bets: Map<String, TBet> = emptyMap(),` add:

```kotlin
    /** The Final Wager after the last round; null in shows saved before it existed and in Write It Down. */
    val finale: FinaleState? = null,
```

3. In the companion, after `const val BET = "bet"` add:

```kotlin
        const val FINAL = "final"
        const val FINAL_CATEGORY = "final_category"
        const val FINAL_WAGER = "final_wager"
        const val FINAL_QUESTION = "final_question"
        const val FINAL_REVEAL = "final_reveal"
        private val FINALE_PHASES = setOf(FINAL_CATEGORY, FINAL_WAGER, FINAL_QUESTION, FINAL_REVEAL)
```

4. In `ROUND_TITLES` add `FINAL to "The Final Wager",` and in `ROUND_RULES` add `FINAL to "Bet your points before you see the question. Last place reveals first.",`.
5. In `startRound`, replace `if (round > s0.order.size) return podium(s0, ctx)` with:

```kotlin
        if (round > s0.order.size) return if (hasFinale(s0)) enterFinale(s0, ctx) else podium(s0, ctx)
```

6. Add these functions in the `// ---- scoring ----` section (above `private fun score`):

```kotlin
    /** Brain Drain only (not Write It Down on its own, nor a show saved with the retired Gauntlet), with a match to settle. */
    private fun hasFinale(s: TriviaState) = mode == Mode.SHOW && GAUNTLET !in s.order && s.teams.count { it.members.isNotEmpty() } >= 2

    /** The wagers this team may pick, from where it stands among the active teams. */
    private fun finaleOffered(s: TriviaState, team: TTeam): List<String> =
        Finale.options(team.score, s.teams.filter { it.members.isNotEmpty() }.map { it.score })

    /** After the last round: pick a short typed question and slam its category. The question itself comes after the wagers. */
    private fun enterFinale(s0: TriviaState, ctx: GameContext): Step<TriviaState> {
        val item = pickMc(s0, ctx) { writable(it) && it.answer.length <= Finale.MAX_ANSWER } ?: return podium(s0, ctx)
        val s = s0.copy(
            phase = FINAL_CATEGORY, format = FINAL, teams = sync(s0.teams, ctx), itemId = item.id, live = item.takeIf { it.id !in mcById },
            options = emptyList(), correct = emptyList(), votes = emptyMap(), reveal = null, drink = null, heist = null, line = emptyList(), bets = emptyMap(),
            finale = FinaleState(category = item.category, answerText = item.answer), startedAt = ctx.now, durationMs = Finale.CATEGORY_MS,
            hostLine = pick(ctx, "Final wager. The category is ${item.category}. Choose wisely.", "One last bet. ${item.category}. Go big or go home."),
        )
        return Step(s, listOf(Effect.UseContent(item.id), Effect.Phase(Finale.CATEGORY_MS)))
    }

    private fun enterFinalWager(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val duration = ctx.timer(Finale.WAGER_MS)
        val next = s.copy(
            phase = FINAL_WAGER, startedAt = ctx.now, durationMs = duration, hostLine = "Pick your wager. Nobody sees it until the reveal.",
            finale = s.finale?.copy(wagers = emptyMap()),
        )
        return Step(next, listOf(Effect.Phase(duration)))
    }

    private fun enterFinalQuestion(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val duration = ctx.timer(Finale.QUESTION_MS)
        return Step(s.copy(phase = FINAL_QUESTION, votes = emptyMap(), startedAt = ctx.now, durationMs = duration, hostLine = null), listOf(Effect.Phase(duration)))
    }

    /** The question is closed: work out every team's result, last place first, and start the reveal. Scores wait until it ends. */
    private fun finalReveal(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val fin = s.finale ?: return podium(s, ctx)
        val active = s.teams.filter { it.members.isNotEmpty() }
        val results = active.sortedBy { it.score }.map { t ->
            val option = Finale.teamWager(t.members, fin.wagers, finaleOffered(s, t))
            val wager = Finale.amount(option, t.score)
            val written = teamWrite(t, s.votes, fin.answerText)
            val right = written?.right == true
            val delta = Finale.settle(t.score, wager, right)
            FinaleResult(t.id, written?.text, right, option, wager, delta, t.score, (t.score + delta).coerceAtLeast(0))
        }
        val leader = active.maxByOrNull { it.score }
        val winner = results.maxByOrNull { it.after }
        val line = when {
            winner == null -> null
            leader != null && winner.team != leader.id -> "${nameOf(s, winner.team)} steal the win!"
            else -> "${nameOf(s, winner.team)} hold on to win!"
        }
        val wrong = results.filter { !it.right }
        val sips = if (wrong.any { it.option == "w75" || it.option == Finale.ALL_IN }) 2 else 1
        val drink = if (s.drinks && wrong.isNotEmpty()) DrinkCall(wrong.map { it.team }, sips, "wrong final") else null
        val duration = Finale.revealMs(results.size)
        return Step(
            s.copy(phase = FINAL_REVEAL, finale = fin.copy(results = results), drink = drink, hostLine = line, startedAt = ctx.now, durationMs = duration),
            listOf(Effect.Phase(duration)),
        )
    }

    /** The reveal is over: the wagers land on the scores (once), then the podium. */
    private fun finishFinale(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val results = s.finale?.results.orEmpty().associateBy { it.team }
        val teams = s.teams.map { t -> results[t.id]?.let { t.copy(score = it.after) } ?: t }
        val effects = results.values.filter { it.delta != 0 }.flatMap { awardTeam(s.teams, it.team, it.delta, "final wager") }
        val next = podium(s.copy(teams = teams, finale = null), ctx)
        return next.copy(effects = effects + next.effects)
    }
```

`finishFinale` clears `finale`, so no later phase can apply the results twice.

- [ ] **Step 5: Wire the deadlines, input, waiting and views**

1. In `onDeadline`, after the `BET -> score(s, ctx)` line add:

```kotlin
        FINAL_CATEGORY -> enterFinalWager(s, ctx)
        FINAL_WAGER -> enterFinalQuestion(s, ctx)
        FINAL_QUESTION -> finalReveal(s, ctx)
        FINAL_REVEAL -> finishFinale(s, ctx)
```

2. In `onAction`, add these branches to the `when (s.phase)` just before `VICTIM -> {`:

```kotlin
            FINAL_WAGER -> {
                val team = teamOf(s, who) ?: throw Reject("NEXT_ROUND")
                val fin = s.finale ?: throw Reject("NOT_NOW")
                if (kind != "finalWager") throw Reject("NOT_NOW")
                val option = str("option")?.takeIf { it in finaleOffered(s, team) } ?: throw Reject("BAD_OPTION")
                Step(s.copy(finale = fin.copy(wagers = fin.wagers + (who.v to FWager(option, ctx.now)))))
            }
            FINAL_QUESTION -> {
                teamOf(s, who) ?: throw Reject("NEXT_ROUND")
                if (kind != "finalAnswer") throw Reject("NOT_NOW")
                val text = cleanText(str("text") ?: "", MAX_WRITE) ?: throw Reject("BAD_TEXT")
                val prev = s.votes[who.v]
                Step(s.copy(votes = s.votes + (who.v to if (prev?.text == text) prev else TVote(text = text, at = ctx.now))))
            }
```

3. In `waitingOn`, add before `BET ->`:

```kotlin
        // Every team needs one pick; the question waits for every player's answer.
        FINAL_WAGER -> s.teams.filter { t -> t.members.none { s.finale?.wagers?.containsKey(it.v) == true } }.flatMap { it.members }.toSet()
        FINAL_QUESTION -> s.teams.flatMap { it.members }.filter { s.votes[it.v] == null }.toSet()
```

4. In `tvView`:
   - change `val live = s.phase == QUESTION || s.phase == BET || showAnswer` to `val live = s.phase == QUESTION || s.phase == BET || s.phase == FINAL_QUESTION || s.phase == FINAL_REVEAL || showAnswer`;
   - in the `prompt` computation change `QUICK, HEIST, WRITE -> mcOf(s)?.prompt` to `QUICK, HEIST, WRITE, FINAL -> mcOf(s)?.prompt`;
   - change both `answered = if (s.phase == QUESTION)` occurrences to `answered = if (s.phase == QUESTION || s.phase == FINAL_QUESTION)`;
   - change `credit = if (live && s.format in setOf(QUICK, HEIST, WRITE))` to include `FINAL` in the set;
   - after the `bet = ...,` line add:

```kotlin
            finale = s.finale?.takeIf { s.phase in FINALE_PHASES }?.let { f ->
                FinaleInfo(
                    category = f.category,
                    locked = if (s.phase == FINAL_WAGER) s.teams.filter { t -> t.members.any { f.wagers.containsKey(it.v) } }.map { it.id } else emptyList(),
                    results = if (s.phase == FINAL_REVEAL) f.results else emptyList(),
                    answerText = f.answerText.takeIf { s.phase == FINAL_REVEAL },
                )
            },
```

- [ ] **Step 6: Run the engine and server suites**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test :server:cleanTest :server:test`
Expected: BUILD SUCCESSFUL. Existing tests that skip a whole show to the podium now run through the finale with no wagers or answers; nothing changes for a team that does not wager (the finale's own drink call is replaced by the podium's). Investigate any failure; do not weaken a test without a ledger ruling.

- [ ] **Step 7: Commit**

```bash
git add tv/engine/src
git commit -m "feat(trivia): the Final Wager flow (category, wager, typed question, last-place-first reveal)"
```

---

### Task 3: What the phones see

**Files:**
- Modify: `tv/engine/src/main/kotlin/partyos/engine/games/trivia/BrainDrain.kt` (`playerView`)
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/trivia/BrainDrainTest.kt`

**Interfaces:**
- Consumes: Task 2's phases, `finaleOffered`, `Finale.amount`, `teamWrite`.
- Produces: phone screens: `FINAL_CATEGORY`: `Waiting`; `FINAL_WAGER`: `ChoiceList(kind "finalWager")` with options `w0..w75` (`wall` for underdogs), each `Choice(id, label, detail = amount)`; `FINAL_QUESTION`: `TextEntry(prompt, MAX_WRITE, mine?.text, "finalAnswer", hint, team)`; `FINAL_REVEAL`: `Waiting` with tone.

- [ ] **Step 1: Write the failing tests**

Add to `BrainDrainTest.kt` after the Task 2 tests:

```kotlin
    @Test fun thePhoneOffersWagersWithTheirWorthAndOnlyUnderdogsSeeAllIn() {
        val (a, _, c) = toFinale()
        assertEquals("Final wager", waiting(a).title)
        e.host(HostCmd.SkipPhase)
        val leader = assertIs<Screen.ChoiceList>(e.phoneState(a).screen)
        assertEquals("finalWager", leader.kind)
        assertEquals(listOf("w0", "w25", "w50", "w75"), leader.options.map { it.id })
        assertEquals("1,150", leader.options.first { it.id == "w75" }.detail) // 75% of 1500 is 1125, to the nearest 50
        val last = assertIs<Screen.ChoiceList>(e.phoneState(c).screen)
        assertEquals(listOf("w0", "w25", "w50", "w75", "wall"), last.options.map { it.id })
        assertEquals("1,000", last.options.first { it.id == "wall" }.detail) // the floor
        assertEquals("ALL IN", last.options.first { it.id == "wall" }.text)
    }

    @Test fun aPickedWagerIsHighlightedAndTeammatesSeeIt() {
        val (a, b) = toFinale()
        e.host(HostCmd.SkipPhase)
        wager(a, "w50")
        assertEquals("w50", assertIs<Screen.ChoiceList>(e.phoneState(a).screen).selected)
        assertEquals(listOf(a), assertIs<Screen.ChoiceList>(e.phoneState(b).screen).votes["w50"])
    }

    @Test fun theQuestionPhoneIsATypedAnswerBoxWithTheQuestion() {
        val (a, b, c) = toFinale()
        e.host(HostCmd.SkipPhase)
        wager(a, "w50"); wager(c, "w0")
        val box = assertIs<Screen.TextEntry>(e.phoneState(a).screen)
        assertEquals("finalAnswer", box.kind)
        assertTrue(box.prompt.startsWith("Question "))
        finalAnswer(a, "Rihgt")
        assertEquals("Rihgt", assertIs<Screen.TextEntry>(e.phoneState(a).screen).value)
        assertTrue(assertIs<Screen.TextEntry>(e.phoneState(b).screen).hint!!.contains("Rihgt"))
    }

    @Test fun theRevealPhoneSaysWhatYourTeamWonOrLost() {
        val (a, b, c, d) = toFinale()
        e.host(HostCmd.SkipPhase)
        wager(a, "w50"); wager(c, "w25")
        val right = currentRight()
        finalAnswer(a, right); finalAnswer(b, right); finalAnswer(c, "Nope"); finalAnswer(d, "Nope")
        assertEquals("final_reveal", tv.phase)
        assertEquals("Correct! +750", waiting(a).title)
        assertEquals("win", waiting(a).tone)
        assertEquals("Wrong. -0", waiting(c).title) // a team on 0 loses nothing
        assertEquals("lose", waiting(c).tone)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test --tests "partyos.engine.games.trivia.BrainDrainTest"`
Expected: FAIL: phones still show "Eyes on the TV" (`assertIs<Screen.ChoiceList>` failures, wrong waiting title).

- [ ] **Step 3: Write the phone screens**

In `BrainDrain.playerView`, add these branches to the `when (s.phase)` just before `BET -> {`:

```kotlin
            FINAL_CATEGORY -> Screen.Waiting("Final wager", s.finale?.category?.let { "Category: $it" }, team = tag)
            FINAL_WAGER -> {
                if (team == null) return Screen.Waiting("You're in next question", "We'll put you on the smallest team")
                val labels = mapOf("w0" to "Play it safe", "w25" to "25%", "w50" to "50%", "w75" to "75%", Finale.ALL_IN to "ALL IN")
                val picked = s.finale?.wagers?.get(who.v)?.option
                val backers = LinkedHashMap<String, MutableList<PlayerId>>()
                for (m in team.members) s.finale?.wagers?.get(m.v)?.option?.let { backers.getOrPut(it) { mutableListOf() } += m }
                Screen.ChoiceList(
                    "Wager on ${s.finale?.category ?: "the final"}. Question comes after",
                    finaleOffered(s, team).map { Choice(it, labels.getValue(it), detail = formatNumber(Finale.amount(it, team.score).toDouble())) },
                    picked, "finalWager", votes = backers, team = tag,
                )
            }
            FINAL_QUESTION -> {
                if (team == null) return Screen.Waiting("You're in next question", "We'll put you on the smallest team")
                val names = ctx.players.associate { it.id to it.name }
                val mates = team.members.filter { it != who }.mapNotNull { id -> s.votes[id.v]?.text?.let { "${names[id] ?: "?"}: $it" } }
                Screen.TextEntry(
                    mcOf(s)?.prompt ?: "", MAX_WRITE, mine?.text, "finalAnswer",
                    hint = if (mates.isEmpty()) "Your team's most-written answer counts" else "Team: " + mates.joinToString(" · "),
                    team = tag,
                )
            }
            FINAL_REVEAL -> {
                val r = team?.let { t -> s.finale?.results?.firstOrNull { it.team == t.id } }
                    ?: return Screen.Waiting("Eyes on the TV", "Watch the reveal", team = tag)
                val was = s.finale?.answerText?.let { "It was $it" }
                if (r.right) Screen.Waiting("Correct! +${formatNumber(r.delta.toDouble())}", was, "win", tag)
                else Screen.Waiting("Wrong. -${formatNumber(-r.delta.toDouble())}", was, "lose", tag)
            }
```

- [ ] **Step 4: Run the engine and server suites**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test :server:cleanTest :server:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add tv/engine/src
git commit -m "feat(trivia): Final Wager phone screens (wager, typed answer, result)"
```

---

### Task 4: Wire protocol fixtures and TypeScript types

**Files:**
- Modify: `tv/server/src/test/kotlin/partyos/server/ProtocolFixturesTest.kt`
- Regenerate: `controller/src/protocol/fixtures/server-messages.json`, `client-messages.json`
- Modify: `controller/src/protocol.test.ts`, `controller/src/tv/types.ts`

**Interfaces:**
- Consumes: `TriviaTv.finale`, `FinaleInfo`, `FinaleResult`.
- Produces (Task 5): TS `TriviaPhase` includes the four phases; `TriviaFormat` includes `'final'`; `FinaleResult`, `FinaleInfo` interfaces; `TriviaTv.finale?: FinaleInfo`; `ROUND_TITLES.final`, `ROUND_RULES.final`.

- [ ] **Step 1: Add the fixtures to the Kotlin test**

In `ProtocolFixturesTest.kt`:

1. Add imports `partyos.engine.FinaleInfo` and `partyos.engine.FinaleResult` (alphabetical with the other `partyos.engine.*` imports).
2. In `serverMessages`, find the two trivia `ServerMsg.Tv` entries added for betting (their `game = TriviaTv(phase = "bet", ...)` and the ballpark `reveal`). Immediately after the second of them, add two more `ServerMsg.Tv` entries. Copy the previous entry's whole `TvState(...)` argument list (same `roomCode`, `players`, `scores`, `captain`, `settings`), change the first argument (the seq) to `72` and `73`, and replace `game = TriviaTv(...)` with:

```kotlin
                    game = TriviaTv(
                        phase = "final_wager", format = "final", round = 5, totalRounds = 5, q = 0, qTotal = 0, durationMs = 20_000,
                        prompt = "",
                        teams = listOf(TriviaTeam("T1", "Quizzards", "#FF4B3E", listOf(sam), 4200)),
                        finale = FinaleInfo(category = "Geography", locked = listOf("T1")),
                        hostLine = "Pick your wager. Nobody sees it until the reveal.",
                    ),
```

and

```kotlin
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
```

3. Next to the betting phone view, add: `view(Screen.ChoiceList("Wager on Geography. Question comes after", listOf(Choice("w25", "25%", detail = "1,050"), Choice("wall", "ALL IN", detail = "4,200")), "w25", "finalWager", team = team)),`
4. Append to `clientMessages`: `ClientMsg.Action("a-fw", 12, JsonObject(mapOf("kind" to JsonPrimitive("finalWager"), "option" to JsonPrimitive("w50")))),` and `ClientMsg.Action("a-fa", 13, JsonObject(mapOf("kind" to JsonPrimitive("finalAnswer"), "text" to JsonPrimitive("The Seine")))),`

- [ ] **Step 2: Run to see the fixtures go stale, then regenerate**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :server:cleanTest :server:test --tests "partyos.server.ProtocolFixturesTest"`
Expected: FAIL (`server-messages.json is stale`, `client-messages.json is stale`).

Run the same command with `-PupdateFixtures`. Expected: BUILD SUCCESSFUL, both JSON files changed (`git diff --stat controller/src/protocol/fixtures`). Re-run without `-PupdateFixtures`: BUILD SUCCESSFUL.

- [ ] **Step 3: Update the TypeScript types**

In `controller/src/tv/types.ts`:

1. `TriviaPhase`: add `| 'final_category' | 'final_wager' | 'final_question' | 'final_reveal'` (before `'podium'`).
2. `TriviaFormat`: add `| 'final'`.
3. Before `export interface TriviaTv`, add:

```ts
/** One team's result in the Final Wager reveal; `before`/`after` are its score around the wager. */
export interface FinaleResult { team: string; text?: string; right: boolean; option: string; wager: number; delta: number; before: number; after: number }
/** The Final Wager: the category, which teams have wagered, and (reveal only) the answer and every result, last place first. */
export interface FinaleInfo { category: string; locked: string[]; results: FinaleResult[]; answerText?: string }
```

4. In `TriviaTv`, after the `bet?:` field, add `finale?: FinaleInfo`.
5. In `ROUND_TITLES` add `final: 'The Final Wager',`; in `ROUND_RULES` add `final: 'Bet your points before you see the question. Last place reveals first.',`.

- [ ] **Step 4: Add the client messages to the TS protocol test**

In `controller/src/protocol.test.ts`, append after the existing `a-bet` entry (matching the Kotlin order): `{ t: 'action', id: 'a-fw', round: 12, payload: { kind: 'finalWager', option: 'w50' } },` and `{ t: 'action', id: 'a-fa', round: 13, payload: { kind: 'finalAnswer', text: 'The Seine' } },`.

- [ ] **Step 5: Run the checks**

Run: `cd controller && npx tsc -b && npx vitest run src/protocol.test.ts`
Expected: no type errors; protocol tests pass.

- [ ] **Step 6: Commit**

```bash
git add tv/server/src/test controller/src/protocol controller/src/protocol.test.ts controller/src/tv/types.ts
git commit -m "feat(trivia): protocol fixtures and TV types for the Final Wager"
```

---

### Task 5: The TV

**Files:**
- Modify: `controller/src/tv/TriviaStage.tsx`, `controller/src/tv/trivia.css`, `controller/src/tv/Gallery.tsx`, `controller/scripts/shots.mjs`

**Interfaces:**
- Consumes: Task 4's TS types.
- Produces: gallery beats `final-category`, `final-wager`, `final-question`, `final-reveal`, `final-reveal-end`.

- [ ] **Step 1: Add the gallery beats**

In `Gallery.tsx`, add to `BEATS` after the betting beats (the gallery clock is `deadline = now + durationMs x 0.6`, so a beat shows `durationMs x 0.4` into its phase):

```ts
  'final-category': { ...base, phase: 'final_category', format: 'final', q: 0, qTotal: 0, prompt: '', options: [], durationMs: 7000, finale: { category: 'Geography', locked: [], results: [] },
    hostLine: 'Final wager. The category is Geography. Choose wisely.' },
  'final-wager': { ...base, phase: 'final_wager', format: 'final', q: 0, qTotal: 0, prompt: '', options: [], durationMs: 20000, finale: { category: 'Geography', locked: ['T2'], results: [] },
    hostLine: 'Pick your wager. Nobody sees it until the reveal.' },
  'final-question': { ...base, phase: 'final_question', format: 'final', q: 0, qTotal: 0, prompt: 'Which river runs through Paris?', options: [], durationMs: 40000, finale: { category: 'Geography', locked: [], results: [] } },
  // 25 s x 0.4 = 10 s in: two of the three results have landed.
  'final-reveal': { ...base, phase: 'final_reveal', format: 'final', q: 0, qTotal: 0, prompt: 'Which river runs through Paris?', options: [], durationMs: 25000,
    finale: { category: 'Geography', answerText: 'The Seine', locked: [], results: [
      { team: 'T3', text: 'Thames', right: false, option: 'wall', wager: 3100, delta: -3100, before: 3100, after: 0 },
      { team: 'T1', text: 'the sein', right: true, option: 'w50', wager: 2100, delta: 2100, before: 4200, after: 6300 },
      { team: 'T2', text: 'The Seine', right: true, option: 'w75', wager: 4050, delta: 4050, before: 5400, after: 9450 }] },
    drink: { teams: ['T3'], sips: 2, reason: 'wrong final' }, hostLine: 'Les Quizerables hold on to win!' },
  'final-reveal-end': { ...base, phase: 'final_reveal', format: 'final', q: 0, qTotal: 0, prompt: 'Which river runs through Paris?', options: [], durationMs: 90000,
    finale: { category: 'Geography', answerText: 'The Seine', locked: [], results: [
      { team: 'T3', text: 'Thames', right: false, option: 'wall', wager: 3100, delta: -3100, before: 3100, after: 0 },
      { team: 'T1', text: 'the sein', right: true, option: 'w50', wager: 2100, delta: 2100, before: 4200, after: 6300 },
      { team: 'T2', text: 'The Seine', right: true, option: 'w75', wager: 4050, delta: 4050, before: 5400, after: 9450 }] },
    drink: { teams: ['T3'], sips: 2, reason: 'wrong final' }, hostLine: 'Les Quizerables hold on to win!' },
```

In `controller/scripts/shots.mjs`, add `'final-category', 'final-wager', 'final-question', 'final-reveal', 'final-reveal-end'` to the `trivia` list.

- [ ] **Step 2: Build the component**

In `TriviaStage.tsx`:

1. Add `useState` to the `react` import on line 3.
2. In the `SCENE` map add `final: C.grape`. In the `Beat` switch, before `default:`, add:

```tsx
    case 'final_category': case 'final_wager': case 'final_question': case 'final_reveal': return <Finale g={g} byId={byId} clock={clock} />
```

3. In `Header`, change the `answered` chip condition `g.phase === 'question' && g.expected > 0` to `(g.phase === 'question' || g.phase === 'final_question') && g.expected > 0`. In `TeamStrip` change `{g.phase === 'question' && (` (the pips) to `{(g.phase === 'question' || g.phase === 'final_question') && (` and, next to the existing `bet-in` line, add `{g.phase === 'final_wager' && g.finale?.locked.includes(t.id) && <span className="bet-in">WAGER IN</span>}`.
4. In the TS `DrinkCall` function, extend the `why` expression: add `call.reason === 'wrong final' ? 'Wrong answer. Pay up.' :` as the first test.
5. Append this component to the file (above the `// ---------- pick a side ----------` marker or at the end):

```tsx
// ---------- the final wager ----------

const STEP_MS = 5000
const msIn = (g: TriviaTv, clock: Clock): number => {
  const total = g.durationMs ?? 0
  const left = clock.frozen ?? (clock.deadline != null ? Math.max(0, clock.deadline - Date.now()) : total)
  return Math.max(0, total - left)
}
function useTick(ms: number) {
  const [, set] = useState(0)
  useEffect(() => { const id = setInterval(() => set((n) => n + 1), ms); return () => clearInterval(id) }, [ms])
}
const wagerLabel = (r: { option: string }) => (r.option === 'wall' ? 'ALL IN' : r.option === 'w0' ? 'PLAYED IT SAFE' : `${r.option.slice(1)}%`)

function Finale({ g, byId, clock }: { g: TriviaTv; byId: ById; clock: Clock }) {
  useTick(250)
  const f = g.finale!
  const reveal = g.phase === 'final_reveal'
  const at = msIn(g, clock)
  const results = f.results
  // Step i lands at i x STEP_MS; its score change lands 3.5 s later. The leader's step (the last) gets the drumroll.
  const shown = reveal ? Math.min(results.length, Math.floor(at / STEP_MS) + 1) : 0
  const settled = results.map((_, i) => reveal && at >= i * STEP_MS + 3500)
  const allDone = reveal && results.length > 0 && settled[results.length - 1]
  const scoreNow = (id: string) => {
    const i = results.findIndex((r) => r.team === id)
    return i < 0 ? (g.teams.find((t) => t.id === id)?.score ?? 0) : settled[i] ? results[i].after : results[i].before
  }
  const live = g.teams.map((t) => ({ ...t, score: scoreNow(t.id) }))
  const ladder = [...live].sort((a, b) => b.score - a.score)
  const current = shown > 0 ? results[shown - 1] : null
  useLater(current && shown === results.length ? `lead-${shown}` : null, 100, () => { sfx.drumroll(1.2) })
  useLater(current ? `v${current.team}-${shown}` : null, 1500, () => {
    if (!current) return
    if (current.right) { sfx.correct(); if (current.option === 'wall') sfx.jackpot() } else sfx.wrong()
  })
  useLater(allDone ? 'end' : null, 600, () => { sfx.fanfare(); sfx.applause(2) })
  return (
    <>
      <div className="trivia-header">
        <ShowTitle small />
        <div className="row" style={{ flex: 1, flexWrap: 'wrap' }}>
          <Chip>THE FINAL WAGER</Chip>
          <Chip fill={C.white} ink={C.ink}>{f.category}</Chip>
        </div>
        {g.phase === 'final_question' && g.expected > 0 && <span className="answered"><b>{g.answered}</b>/{g.expected} in</span>}
        {!reveal && (clock.deadline != null || clock.frozen != null) && (g.durationMs ?? 0) > 0 && <Timer deadline={clock.deadline} frozen={clock.frozen} total={g.durationMs ?? 0} size={150} />}
      </div>
      {g.phase === 'final_category' && (
        <div className="final-stage">
          <Burst text={f.category.toUpperCase()} width={1240} height={470} size={110} fill={C.white} tilt={-3} spikes={22} />
        </div>
      )}
      {g.phase === 'final_wager' && (
        <div className="final-stage">
          <Panel className="unit-card" fill={C.paper} tilt={-2}>
            <span>Pick your wager on your phone</span>
            <b className="display">Nobody sees it yet</b>
            <small>Underdogs can go ALL IN. The question comes next.</small>
          </Panel>
        </div>
      )}
      {g.phase === 'final_question' && (
        <>
          <Bubble tail="none" className="q-bubble">{g.prompt}</Bubble>
          <div className="ballpark-wait">
            <Panel className="unit-card" fill={C.paper} tilt={-2}>
              <span>Type the answer on your phone</span>
              <b className="display">Wagers are locked</b>
              <small>Your team's most-written answer counts.</small>
            </Panel>
            <Brainy mood="smug" size={230} />
          </div>
        </>
      )}
      {reveal && (
        <div className="final-reveal">
          <Bubble tail="none" className="q-bubble small">{g.prompt}</Bubble>
          <div className="final-body">
            <div className="final-now">
              {current && (() => {
                const t = g.teams.find((x) => x.id === current.team)
                if (!t) return null
                return (
                  <Slam key={`${current.team}-${shown}`} tilt={-2} from={1.5}>
                    <Panel className="final-card" fill={C.white} tilt={-1}>
                      <span className="flag" style={{ background: t.color, color: inkOn(t.color) }}>{t.name}</span>
                      <span className="final-text">{current.text ? `“${current.text}”` : 'No answer'}</span>
                      <Pop delay={1.5}><span className="write-mark">{current.right ? <Check /> : <Cross />}</span></Pop>
                      <Pop delay={2.3}><span className="final-wager">{wagerLabel(current)} · {current.wager.toLocaleString()}</span></Pop>
                      <Pop delay={3.3}><span className={`final-delta ${current.delta < 0 ? 'neg' : ''}`}>{current.delta > 0 ? '+' : ''}{current.delta.toLocaleString()}</span></Pop>
                    </Panel>
                  </Slam>
                )
              })()}
              {allDone && f.answerText && <Pop delay={0.3}><Panel className="write-answer" fill={C.sun} tilt={1}><small>THE ANSWER</small><b className="display">{f.answerText}</b></Panel></Pop>}
            </div>
            <div className="final-ladder">
              {ladder.map((t, i) => {
                const r = results.findIndex((x) => x.team === t.id)
                const mark = r >= 0 && r < shown ? results[r].right : null
                return (
                  <motion.div key={t.id} layout transition={{ type: 'spring', stiffness: 260, damping: 26 }} className="ladder-row" style={{ background: t.color, color: inkOn(t.color) }}>
                    <b className="rank">{i + 1}</b><span className="nm">{t.name}</span>
                    {mark != null && <span className="mk">{mark ? <Check /> : <Cross />}</span>}
                    <span className="sc">{t.score.toLocaleString()}</span>
                  </motion.div>
                )
              })}
            </div>
          </div>
        </div>
      )}
      <div className="trivia-foot">
        {reveal ? <HostSays line={allDone ? g.hostLine : null} mood="smug" size={120} /> : <HostSays line={g.hostLine} mood="smug" size={g.phase === 'final_category' ? 160 : 120} />}
        <TeamStrip g={{ ...g, teams: live }} byId={byId} />
      </div>
      {allDone && g.drink && <DrinkCall g={g} byId={byId} style={{ right: 60, top: 40 }} />}
    </>
  )
}
```

   If `Timer`, `Chip`, `Slam`, `Pop`, `Burst`, `Check`, `Cross`, `Bubble`, `Brainy`, `HostSays`, `Panel` or `inkOn` are not imported at the top of the file yet, add them to the existing imports (they are all used elsewhere in the same file). Match the `DrinkCall` usage the file already has on the standings and podium beats (`g`, `byId`, `style`).

6. Append to `trivia.css`:

```css
/* the final wager */
.final-stage { flex: 1; display: flex; align-items: center; justify-content: center; gap: 60px; flex-direction: column; }
.final-reveal { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.final-body { flex: 1; display: grid; grid-template-columns: 1.15fr 0.85fr; gap: 40px; min-height: 0; align-items: start; padding-top: 10px; }
.final-now { display: flex; flex-direction: column; gap: 22px; align-items: flex-start; }
.final-card { display: flex; flex-wrap: wrap; align-items: center; gap: 14px 18px; padding: 22px 28px; max-width: 880px; }
.final-text { font-size: 44px; font-weight: 900; flex: 1 1 100%; overflow-wrap: anywhere; }
.final-wager { font-size: 30px; font-weight: 900; background: var(--sun); border: 4px solid var(--ink); border-radius: 12px; padding: 2px 14px; box-shadow: 3px 3px 0 var(--ink); }
.final-delta { font-family: var(--font-display); font-size: 64px; color: var(--lime); -webkit-text-stroke: 8px var(--ink); paint-order: stroke fill; }
.final-delta.neg { color: var(--tomato); }
.final-ladder { display: flex; flex-direction: column; gap: 12px; }
.ladder-row { display: flex; align-items: center; gap: 16px; font-size: 34px; font-weight: 900; border: 5px solid var(--ink); border-radius: 18px; padding: 8px 20px; box-shadow: 5px 5px 0 var(--ink); }
.ladder-row .rank { font-family: var(--font-display); font-weight: 400; font-size: 40px; width: 40px; }
.ladder-row .nm { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ladder-row .mk { display: grid; place-items: center; width: 44px; height: 44px; border: 4px solid var(--ink); border-radius: 50%; background: var(--white); }
.ladder-row .sc { font-family: var(--font-display); font-weight: 400; font-size: 40px; }
```

- [ ] **Step 3: Type-check and screenshot**

Run: `cd controller && npx tsc -b && npx vitest run`
Expected: no type errors; all unit tests pass.

Then build and screenshot the five beats (the gallery needs `vite preview`; if `http://127.0.0.1:4173` is not serving `controller/dist`, start `cd controller && npx vite preview --port 4173 --strictPort --host 127.0.0.1` in the background first):

```bash
cd controller && npx vite build
cd .. && node controller/scripts/shots.mjs controller/test-results/shots http://127.0.0.1:4173 final-category,final-wager,final-question,final-reveal,final-reveal-end trivia
```

Look at the five PNGs in `controller/test-results/shots/`. Check: the category burst is centred and not clipped; the wager view's "WAGER IN" tag sits on the right team strip; the reveal's current card, the ladder and the team strips do not overlap; the ladder is ordered by the running score; the drink card (end beat only) sits in free space; names and numbers read at 1080p. Fix any overlap before committing by adjusting the CSS values above.

- [ ] **Step 4: Commit**

```bash
git add controller/src controller/scripts
git commit -m "feat(trivia): TV Final Wager (category slam, wager, question, last-place-first reveal with a live ladder)"
```

---

### Task 6: Browser test, docs, full check

**Files:**
- Create: `controller/e2e/final-wager.spec.ts`
- Modify: `docs/party-os/show-bible.md`

- [ ] **Step 1: Write the browser test**

Create `controller/e2e/final-wager.spec.ts`:

```ts
import { expect, test, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

async function nameTeams(namers: (readonly [Page, string])[]) {
  for (const [p, name] of namers) {
    const naming = p.getByRole('heading', { name: 'Name your team' })
    await expect.poll(async () => {
      if (await naming.isVisible()) return 'name it'
      if (await p.getByRole('heading', { name: /You.re on/ }).isVisible() || !(await p.locator('.choices.teams').count())) return 'named'
      return 'waiting'
    }, { timeout: 20_000 }).not.toBe('waiting')
    if (!(await naming.isVisible())) continue
    await p.locator('textarea').fill(name)
    await p.getByRole('button', { name: 'Lock it in' }).click()
  }
}

test('four phones play the Final Wager: wager, type the answer, watch the reveal', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const phones = await Promise.all(['Ana', 'Bo', 'Cal', 'Di'].map((n) => phone(browser, room, n)))
  const [ana, , cal] = phones
  const tv = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage()
  await tv.goto('/tv')

  await host.getByRole('button', { name: /Brain Drain/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()
  for (const [i, p] of phones.entries()) await p.locator('.choices.teams button').nth(i < 2 ? 0 : 1).click()
  await nameTeams([[ana, 'Quizzly Bears'], [cal, 'Smarty Pints']])

  // Skip the five rounds (nobody scores, so both teams are level) until the finale's category slam shows on the TV.
  for (let i = 0; i < 300 && !(await tv.getByText('THE FINAL WAGER').count()); i++) {
    await host.getByRole('button', { name: 'Skip phase' }).click()
    await host.waitForTimeout(200)
  }
  await expect(tv.getByText('THE FINAL WAGER')).toBeVisible()
  await host.getByRole('button', { name: 'Skip phase' }).click() // category → wager

  // Everyone is level, so everyone is an underdog and sees ALL IN. The leader rules are covered by the engine tests.
  for (const p of phones) await expect(p.locator('.choices button', { hasText: 'ALL IN' })).toBeVisible()
  await expect(ana.locator('.choices button', { hasText: 'Play it safe' })).toBeVisible()
  await expect(tv.locator('.bet-in')).toHaveCount(0)
  await ana.locator('.choices button', { hasText: '50%' }).click()
  await expect(tv.locator('.bet-in')).toHaveCount(1) // one team's wager is in; what they picked stays hidden
  await cal.locator('.choices button', { hasText: 'ALL IN' }).click()

  // Both teams are in, so the question opens at once; every phone gets a typed-answer box and the TV shows the question.
  for (const p of phones) await expect(p.locator('textarea')).toBeVisible({ timeout: 5_000 })
  await expect(tv.locator('.q-bubble')).not.toBeEmpty()
  for (const [i, p] of phones.entries()) {
    await p.locator('textarea').fill(i < 2 ? 'Paris' : 'Rome')
    await p.getByRole('button', { name: 'Lock it in' }).click()
  }

  // The reveal plays on the TV: last place first, then the ladder; every phone shows its result.
  await expect(tv.locator('.final-reveal')).toBeVisible({ timeout: 5_000 })
  await expect(tv.locator('.ladder-row')).toHaveCount(2)
  for (const p of phones) await expect(p.getByRole('heading', { name: /Correct!|Wrong\./ })).toBeVisible()
  await expect(tv.locator('.final-card')).toBeVisible()

  // Skipping the reveal lands the scores and moves on to the podium.
  await host.getByRole('button', { name: 'Skip phase' }).click()
  await expect(tv.getByText(/Quizzly Bears|Smarty Pints/).first()).toBeVisible()
  await host.getByRole('button', { name: 'End game' }).click()
})
```

- [ ] **Step 2: Run it (just this file)**

Run: `cd controller && npx vite build && npx playwright test e2e/final-wager.spec.ts --reporter=line`
Expected: 1 passed. If it fails because a selector or label differs from the real screen, fix the test to match the real screen, unless the real screen is wrong. Then run it with `--repeat-each=3` and confirm 3 passed.

- [ ] **Step 3: Document the beat**

In `docs/party-os/show-bible.md`, in the Brain Drain beat sheet table, add a row before `Standings`:

```
| Final Wager | the category slams in; secret wagers (WAGER IN tags); a typed question; then the reveal, last place first: answer, check or cross, wager, score, with a live ladder that reshuffles; the leader goes last with a drumroll | stamp on lock; drumroll before the leader; jackpot on a winning ALL IN; fanfare at the end |
```

- [ ] **Step 4: Run the engine, server and controller suites**

```bash
cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test :server:cleanTest :server:test
cd ../controller && npx tsc -b && npx vitest run
```

Expected: all pass. Do not run the whole Playwright suite (it is heavy and has restarted the owner's machine); run only `e2e/final-wager.spec.ts`, `e2e/ballpark-bet.spec.ts` and `e2e/party.spec.ts`.

- [ ] **Step 5: Commit**

```bash
git add controller/e2e/final-wager.spec.ts docs/party-os/show-bible.md
git commit -m "test(trivia): browser test for the Final Wager; document the beat"
```

---

## Self-review notes

- **Spec coverage:** flow and timings (T2), wager rules and the underdog rule (T1), question choice and the skip when none (T2), drinks and host lines (T2), engine state/input/waiting/views (T2), phone screens (T3), protocol and fixtures (T4), TV beats and the live ladder (T5), sound reuse (T5), browser test and docs (T6), saved-show compatibility (T2 `oldSavedShowsHaveNoFinale`), Write It Down exclusion (T2, assertion in the existing test), simulation findings recorded in the spec. Phone vibration needs no task (already generic in `ScreenView`).
- **Type consistency:** `FinaleResult`, `FinaleInfo`, `FWager`, `FinaleState`, `Finale.*` and the option ids (`w0`, `w25`, `w50`, `w75`, `wall`) are defined once (T1) and used identically after.
- **Known refinements from the spec:** tie-aware underdog rule; the wager phase ends once every team has one pick; the finale's drink is 2 sips when a wrong team bet 75% or ALL IN, otherwise 1.
