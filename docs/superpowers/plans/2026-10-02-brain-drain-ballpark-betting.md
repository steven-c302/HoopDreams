# Brain Drain Ballpark Betting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After every team's Ballpark guess is shown, each team bets on whose guess is closest, with odds by how safe the guess looks, so teams can score by reading the room.

**Architecture:** A pure `Betting` object (odds, stakes, team bet, settlement) is wired into `BrainDrain` as a new `bet` phase between the Ballpark question and reveal. Phones reuse the existing `ChoiceList` screen in `"teams"` style (no new phone component); the TV gets a bet view on the existing number line and bet chips on the reveal.

**Tech Stack:** Kotlin (engine, `kotlin.test`, Gradle), kotlinx.serialization, TypeScript/React (TV stage), Vitest, Playwright screenshots via the gallery.

**Spec:** `docs/superpowers/specs/2026-10-02-brain-drain-ballpark-betting-design.md`. One deliberate change from the spec: the phone `bet` message carries a single `option` field (what `ChoiceList` already sends) instead of `team`/`stake` fields. Option ids: a team id (back that guess), `s250`/`s500`/`s1000` (stake), `skip`, `back` (change guess). Task 6 updates the spec to match. The loss amount on a bet is also capped at what the team actually has (see Task 1), so house money can't take a team below zero and the TV never shows a loss bigger than the score.

## Global Constraints

- Stakes: Skip, 250, 500, 1000. A stake above the team's score is unavailable, except 250, which is always available (house money).
- Odds by distance of a team's guess from the median of all teams' guesses: nearest 1x, next 2x, every other 3x; guesses at the same distance share a tier.
- Win adds `stake x odds`; loss subtracts the stake; a team's score never goes below zero.
- The bet phase is `BET_MS` = 15 000 ms (scaled by `ctx.timer`), skipped when fewer than two teams have a guess, and ends early once every active team member has decided (a stake, or Skip).
- Who backed what is not sent to the TV until the reveal; the true answer is not sent before the reveal.
- Saved shows from before this change must still load (new state fields default empty).
- Run Kotlin tests with `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test --tests "<class>"`. Run TS checks from `controller/`: `npx tsc -b` and `npx vitest run`.

## Review Focus

- Two guesses tie for closest: both bets win (Task 1 `settle`).
- A team with score 0 betting house money and losing: score stays 0 and the reported delta is 0, never negative (Task 1 `settle`, Task 2 flow test).
- A player who locked a stake then taps a different guess: their stake resets and the phase waits for them again (Task 2 flow test).
- A saved show from before this change (no `line`/`bets` in its JSON) decodes and plays (Task 2 `oldSavedShowStillLoads`).
- The host skips the phase mid-bet, or skips the question with fewer than two guesses: only locked bets settle, and with one guess the bet phase never opens (Task 2 flow tests).

---

## File Structure

- Create `tv/engine/src/main/kotlin/partyos/engine/games/trivia/Betting.kt`: `TBet` and the pure betting rules. Kept out of `BrainDrain.kt` (already 1,197 lines).
- Create `tv/engine/src/test/kotlin/partyos/engine/games/trivia/BettingTest.kt`: unit tests for the pure rules.
- Modify `tv/engine/src/main/kotlin/partyos/engine/TriviaViews.kt`: `BetOption`, `BetResult`, `BetInfo`, `TeamAnswer.bet`, `TriviaTv.bet`.
- Modify `tv/engine/src/main/kotlin/partyos/engine/games/trivia/BrainDrain.kt`: state, phase, input, scoring, views.
- Modify `tv/engine/src/test/kotlin/partyos/engine/games/trivia/BrainDrainTest.kt`: update the existing Ballpark test, add flow and view tests.
- Modify `tv/server/src/test/kotlin/partyos/server/ProtocolFixturesTest.kt` and regenerate `controller/src/protocol/fixtures/*.json`.
- Modify `controller/src/protocol.test.ts`, `controller/src/tv/types.ts`, `controller/src/tv/TriviaStage.tsx`, `controller/src/tv/trivia.css`, `controller/src/tv/Gallery.tsx`, `controller/scripts/shots.mjs`.
- Modify `docs/party-os/show-bible.md` and the spec.

---

### Task 1: The betting rules (pure)

**Files:**
- Create: `tv/engine/src/main/kotlin/partyos/engine/games/trivia/Betting.kt`
- Modify: `tv/engine/src/main/kotlin/partyos/engine/TriviaViews.kt` (add three data classes and one `TeamAnswer` field)
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/trivia/BettingTest.kt`

**Interfaces:**
- Produces (used by Tasks 2 and 3):
  - `BetOption(team: String, number: Double, odds: Int)`, `BetResult(on: String? = null, stake: Int = 0, odds: Int = 0, won: Boolean = false, delta: Int = 0)`, `BetInfo(line: List<BetOption>, locked: List<String> = emptyList())` in `partyos.engine`
  - `TeamAnswer.bet: BetResult? = null`
  - `TBet(on: String? = null, stake: Int? = null, at: Long = 0)` in `partyos.engine.games.trivia`
  - `Betting.BET_MS: Long`, `Betting.STAKES: List<Int>`, `Betting.HOUSE_STAKE: Int`, `Betting.SKIP: String`, `Betting.BACK: String`
  - `Betting.stakeId(amount: Int): String`, `Betting.stakeOf(id: String): Int?`
  - `Betting.line(guesses: Map<String, Double>): List<BetOption>` (sorted by number)
  - `Betting.allowedStakes(score: Int): List<Int>`
  - `Betting.teamBet(members: List<PlayerId>, bets: Map<String, TBet>): Pair<String, Int>?`
  - `Betting.settle(line: List<BetOption>, bets: Map<String, Pair<String, Int>>, answer: Double, available: Map<String, Int>): Map<String, BetResult>`

- [ ] **Step 1: Add the view data classes**

In `tv/engine/src/main/kotlin/partyos/engine/TriviaViews.kt`, add `val bet: BetResult? = null,` as the last field of `TeamAnswer` (after `val text: String? = null,`), and add these classes right after `data class DrinkCall(...)` at the end of the file:

```kotlin
/** Ballpark betting: one backable guess and what backing it pays (odds is the multiplier on the stake). */
@Serializable
data class BetOption(val team: String, val number: Double, val odds: Int)

/** How one team's bet came out. [on] is null when they didn't bet; [delta] is the real change to their score. */
@Serializable
data class BetResult(val on: String? = null, val stake: Int = 0, val odds: Int = 0, val won: Boolean = false, val delta: Int = 0)

/** The bet phase on the TV: every guess with its odds, and which teams have a bet in (not who backed what). */
@Serializable
data class BetInfo(val line: List<BetOption>, val locked: List<String> = emptyList())
```

- [ ] **Step 2: Write the failing tests**

Create `tv/engine/src/test/kotlin/partyos/engine/games/trivia/BettingTest.kt`:

```kotlin
package partyos.engine.games.trivia

import partyos.engine.BetOption
import partyos.engine.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BettingTest {
    private fun odds(vararg guesses: Pair<String, Double>) = Betting.line(guesses.toMap()).associate { it.team to it.odds }

    @Test fun oddsGrowWithDistanceFromTheMiddleOfThePack() {
        // Median of 100, 200, 210, 900 is 205: B and C tie as nearest, then A, then D.
        assertEquals(mapOf("A" to 2, "B" to 1, "C" to 1, "D" to 3), odds("A" to 100.0, "B" to 200.0, "C" to 210.0, "D" to 900.0))
    }

    @Test fun twoGuessesAreBothTheMiddleAndPayEvenly() {
        assertEquals(mapOf("A" to 1, "B" to 1), odds("A" to 10.0, "B" to 500.0))
    }

    @Test fun theLineIsSortedByGuess() {
        assertEquals(listOf("B", "A"), Betting.line(mapOf("A" to 50.0, "B" to 5.0)).map { it.team })
    }

    @Test fun stakesAreCappedAtTheScoreExceptHouseMoney() {
        assertEquals(listOf(250), Betting.allowedStakes(0))
        assertEquals(listOf(250), Betting.allowedStakes(499))
        assertEquals(listOf(250, 500), Betting.allowedStakes(500))
        assertEquals(listOf(250, 500, 1000), Betting.allowedStakes(5000))
    }

    @Test fun stakeIdsRoundTrip() {
        assertEquals("s500", Betting.stakeId(500))
        assertEquals(500, Betting.stakeOf("s500"))
        assertNull(Betting.stakeOf("s300"))
        assertNull(Betting.stakeOf("T1"))
    }

    private val p = listOf("p1", "p2", "p3").map(::PlayerId)

    @Test fun theTeamBacksTheGuessMostPlayersTapped() {
        val bets = mapOf(
            "p1" to TBet("T2", 500, at = 10), "p2" to TBet("T1", 250, at = 5), "p3" to TBet("T2", 250, at = 20),
        )
        // T2 has two backers; among them the stakes tie 500 v 250, so the lower stake wins.
        assertEquals("T2" to 250, Betting.teamBet(p, bets))
    }

    @Test fun aTieOnTheGuessGoesToTheFirstTap() {
        val bets = mapOf("p1" to TBet("T2", 500, at = 10), "p2" to TBet("T1", 500, at = 5))
        assertEquals("T1" to 500, Betting.teamBet(p, bets))
    }

    @Test fun skipsAndUnfinishedBetsDoNotCountAndNobodyBettingMeansNoBet() {
        assertNull(Betting.teamBet(p, mapOf("p1" to TBet(null, 0, at = 1), "p2" to TBet("T1", null, at = 2))))
        assertEquals("T1" to 250, Betting.teamBet(p, mapOf("p1" to TBet(null, 0, at = 1), "p2" to TBet("T1", 250, at = 2))))
    }

    private val line = listOf(BetOption("T1", 100.0, 2), BetOption("T2", 110.0, 1), BetOption("T3", 400.0, 3))

    @Test fun aWinningBetPaysStakeTimesOddsAndALosingBetCostsTheStake() {
        val r = Betting.settle(line, mapOf("T1" to ("T3" to 250), "T2" to ("T2" to 500)), answer = 112.0, available = mapOf("T1" to 1000, "T2" to 1000))
        assertEquals(false, r.getValue("T1").won)
        assertEquals(-250, r.getValue("T1").delta)
        assertEquals(true, r.getValue("T2").won)
        assertEquals(500, r.getValue("T2").delta) // odds 1
        assertEquals(1, r.getValue("T2").odds)
    }

    @Test fun aLongShotPaysTripleAndTheClosestGuessWinsTiesBothWin() {
        val r = Betting.settle(line, mapOf("T1" to ("T3" to 250)), answer = 400.0, available = mapOf("T1" to 0))
        assertEquals(750, r.getValue("T1").delta)
        // 105 is exactly between 100 and 110: both are closest, so a bet on either wins.
        val tie = Betting.settle(line, mapOf("T1" to ("T1" to 250), "T2" to ("T2" to 250)), answer = 105.0, available = mapOf("T1" to 0, "T2" to 0))
        assertEquals(true, tie.getValue("T1").won)
        assertEquals(true, tie.getValue("T2").won)
    }

    @Test fun aLossNeverTakesMoreThanTheTeamHas() {
        val r = Betting.settle(line, mapOf("T1" to ("T3" to 250)), answer = 100.0, available = mapOf("T1" to 100))
        assertEquals(-100, r.getValue("T1").delta)
        val broke = Betting.settle(line, mapOf("T1" to ("T3" to 250)), answer = 100.0, available = mapOf("T1" to 0))
        assertEquals(0, broke.getValue("T1").delta)
        assertEquals(false, broke.getValue("T1").won)
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test --tests "partyos.engine.games.trivia.BettingTest"`
Expected: compile FAIL, `Unresolved reference: Betting` and `TBet`.

- [ ] **Step 4: Write the implementation**

Create `tv/engine/src/main/kotlin/partyos/engine/games/trivia/Betting.kt`:

```kotlin
package partyos.engine.games.trivia

import kotlinx.serialization.Serializable
import partyos.engine.BetOption
import partyos.engine.BetResult
import partyos.engine.PlayerId
import kotlin.math.abs

/**
 * One player's bet in the Ballpark bet phase. [on] is the team whose guess they back; [stake] is null until they pick
 * one. A player who skipped has `on = null, stake = 0`; one who is still choosing has `stake = null`.
 */
@Serializable
data class TBet(val on: String? = null, val stake: Int? = null, val at: Long = 0)

/** The rules of Ballpark betting. Pure: no state, no engine. */
object Betting {
    const val BET_MS = 15_000L
    val STAKES = listOf(250, 500, 1000)
    /** Always available, so a team with nothing can still take a swing. */
    const val HOUSE_STAKE = 250
    /** Phone option ids besides team ids and stake ids. */
    const val SKIP = "skip"
    const val BACK = "back"

    fun stakeId(amount: Int) = "s$amount"
    fun stakeOf(id: String): Int? = if (id.startsWith("s")) id.drop(1).toIntOrNull()?.takeIf { it in STAKES } else null

    /**
     * Every team's guess with its odds: ranked by distance from the median of all the guesses, nearest 1x, next 2x,
     * the rest 3x; equal distances share a tier. Sorted by guess so the TV can lay them out left to right.
     */
    fun line(guesses: Map<String, Double>): List<BetOption> {
        if (guesses.isEmpty()) return emptyList()
        val middle = BrainDrain.median(guesses.values.toList())!!
        val distance = guesses.mapValues { abs(it.value - middle) }
        val tiers = distance.values.distinct().sorted()
        return guesses.map { (team, g) -> BetOption(team, g, minOf(3, tiers.indexOf(distance.getValue(team)) + 1)) }.sortedBy { it.number }
    }

    /** The stakes a team with [score] may offer. */
    fun allowedStakes(score: Int): List<Int> = STAKES.filter { it <= score || it == HOUSE_STAKE }

    /**
     * The team's bet: the guess most of its locked-in players back (ties to the first tap) and, among those backers,
     * the stake most of them chose (ties to the lower). Skips and unfinished bets don't count; null if nobody bet.
     */
    fun teamBet(members: List<PlayerId>, bets: Map<String, TBet>): Pair<String, Int>? {
        val locked = members.mapNotNull { bets[it.v] }.filter { it.on != null && (it.stake ?: 0) > 0 }
        if (locked.isEmpty()) return null
        val backed = locked.groupBy { it.on!! }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<TBet>>> { it.value.size }.thenBy { e -> e.value.minOf { it.at } })
            .first()
        val stake = backed.value.groupingBy { it.stake!! }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .first().key
        return backed.key to stake
    }

    /**
     * How each team's bet came out. A bet wins when the guess it backs is among the closest to [answer] (a tie for
     * closest wins for all of them). A win pays `stake x odds`; a loss costs the stake, but never more than the team
     * has ([available] = its score plus this round's points), so scores never go below zero.
     */
    fun settle(line: List<BetOption>, bets: Map<String, Pair<String, Int>>, answer: Double, available: Map<String, Int>): Map<String, BetResult> {
        val best = line.minOfOrNull { abs(it.number - answer) } ?: return emptyMap()
        val winners = line.filter { abs(it.number - answer) == best }.map { it.team }.toSet()
        return bets.mapNotNull { (team, bet) ->
            val (on, stake) = bet
            val option = line.firstOrNull { it.team == on } ?: return@mapNotNull null
            val won = on in winners
            val delta = if (won) stake * option.odds else -minOf(stake, (available[team] ?: 0).coerceAtLeast(0))
            team to BetResult(on, stake, option.odds, won, delta)
        }.toMap()
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test --tests "partyos.engine.games.trivia.BettingTest"`
Expected: BUILD SUCCESSFUL, all `BettingTest` tests pass.

- [ ] **Step 6: Commit**

```bash
git add tv/engine/src/main/kotlin/partyos/engine/TriviaViews.kt tv/engine/src/main/kotlin/partyos/engine/games/trivia/Betting.kt tv/engine/src/test/kotlin/partyos/engine/games/trivia/BettingTest.kt
git commit -m "feat(trivia): Ballpark betting rules (odds, stakes, team bet, settlement)"
```

---

### Task 2: The bet phase in the engine

**Files:**
- Modify: `tv/engine/src/main/kotlin/partyos/engine/TriviaViews.kt` (add `TriviaTv.bet`, needed by the tests in this task)
- Modify: `tv/engine/src/main/kotlin/partyos/engine/games/trivia/BrainDrain.kt`
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/trivia/BrainDrainTest.kt`

**Interfaces:**
- Consumes: everything Task 1 produced.
- Produces (used by Task 3 and the TV): phase constant `BrainDrain.BET = "bet"`; `TriviaState.line: List<BetOption>`, `TriviaState.bets: Map<String, TBet>`; the `"bet"` action with an `option` field; `TeamAnswer.bet` filled at the Ballpark reveal; `TriviaTv.bet: BetInfo?`, filled in the bet phase.

- [ ] **Step 1: Add `TriviaTv.bet` so the tests can compile**

In `TriviaViews.kt`, in `TriviaTv`, add after `val drink: DrinkCall? = null,`:

```kotlin
    /** The bet phase: every guess with its odds and which teams have bet. Absent in every other phase. */
    val bet: BetInfo? = null,
```

and update the `phase` doc comment above it to `teamup | intro | question | bet | reveal | victim | steal | standings | podium`.

- [ ] **Step 2: Update the existing Ballpark test and add the new flow tests**

In `BrainDrainTest.kt`, in `ballparkUsesTheMedianAndRewardsTheClosestTeam`, replace the line `val r = assertNotNull(tv.reveal)` with:

```kotlin
        assertEquals("bet", tv.phase) // two guesses on the board: teams get to bet before the answer
        e.host(HostCmd.SkipPhase)
        val r = assertNotNull(tv.reveal)
```

Then add this block right after that test (before `pickASideScoresEachCall`). `fourInTwoTeams`, `skipRound`, `act`, `score`, `tv` are existing helpers in this class.

```kotlin
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
        val screen = assertIs<Screen.ChoiceList>(e.phoneState(a).screen)
        assertTrue(screen.prompt.startsWith("How much on"))
        assertTrue(screen.options.any { it.id == Betting.BACK })
        lockBet(d, "T2", 250)
        assertEquals("bet", tv.phase, "a is still choosing a stake")
        assertEquals(ActionResult.Ack, bet(a, Betting.stakeId(250)))
        assertEquals("reveal", tv.phase)
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
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test --tests "partyos.engine.games.trivia.BrainDrainTest"`
Expected: compile FAIL (`TriviaState.line` and `Betting` wiring don't exist yet).

- [ ] **Step 4: Add the state, constant and imports**

In `BrainDrain.kt`:

1. Add imports (alphabetical, among the other `partyos.engine.*` imports): `import partyos.engine.BetInfo`, `import partyos.engine.BetOption`, `import partyos.engine.BetResult`.
2. In `TriviaState`, after `val awards: List<TriviaAward> = emptyList(),` add:

```kotlin
    /** Ballpark betting: each team's guess with its odds, frozen when the bet phase opens. */
    val line: List<BetOption> = emptyList(),
    /** player id → their bet this question (the guess they back and their stake). */
    val bets: Map<String, TBet> = emptyMap(),
```

3. In the companion object, next to `const val STANDINGS = "standings"`, add `const val BET = "bet"`.
4. In `startRound`'s `s0.copy(...)` (the one that sets `phase = INTRO`), add `line = emptyList(), bets = emptyMap(),` to the argument list. In `nextQuestion`, add the same two arguments to `val base = s0.copy(q = q, votes = emptyMap(), ...)`.
5. Update the Ballpark rule text in the companion's `ROUND_RULES`: `BALLPARK to "Guess the number. Your team's guess is the middle of everyone's. Closest wins. Then bet on whose guess is closest.",`

- [ ] **Step 5: Open the bet phase, take bets, and end it**

1. In `onDeadline`, replace `QUESTION -> score(s, ctx)` with:

```kotlin
        QUESTION -> if (s.format == BALLPARK && teamGuesses(s).values.count { it != null } >= 2) enterBet(s, ctx) else score(s, ctx)
        BET -> score(s, ctx)
```

2. In `onAction`, add this branch to the `when (s.phase)` just before `VICTIM -> {`:

```kotlin
            BET -> {
                val team = teamOf(s, who) ?: throw Reject("NEXT_ROUND")
                if (kind != "bet") throw Reject("NOT_NOW")
                val option = str("option") ?: throw Reject("BAD_OPTION")
                val bet = when {
                    option == Betting.SKIP -> TBet(null, 0, ctx.now)
                    option == Betting.BACK -> TBet(null, null, ctx.now)
                    option.startsWith("s") -> {
                        val on = s.bets[who.v]?.on ?: throw Reject("NOT_NOW")
                        val amount = Betting.stakeOf(option)?.takeIf { it in Betting.allowedStakes(team.score) } ?: throw Reject("BAD_STAKE")
                        TBet(on, amount, ctx.now)
                    }
                    else -> {
                        if (s.line.none { it.team == option }) throw Reject("BAD_OPTION")
                        TBet(option, null, ctx.now)
                    }
                }
                Step(s.copy(bets = s.bets + (who.v to bet)))
            }
```

3. Add these helpers in the `// ---- scoring ----` section, above `private fun score`:

```kotlin
    /** Each active team's Ballpark guess (the median of its members' numbers), or null if nobody typed one. */
    private fun teamGuesses(s: TriviaState): Map<String, Double?> =
        s.teams.filter { it.members.isNotEmpty() }.associate { t -> t.id to median(t.members.mapNotNull { s.votes[it.v]?.number }) }

    /** Ballpark guesses are in: show them with their odds and let teams bet before the answer. */
    private fun enterBet(s: TriviaState, ctx: GameContext): Step<TriviaState> {
        val line = Betting.line(teamGuesses(s).mapNotNull { (id, g) -> g?.let { id to it } }.toMap())
        val duration = ctx.timer(Betting.BET_MS)
        val next = s.copy(
            phase = BET, line = line, bets = emptyMap(), startedAt = ctx.now, durationMs = duration,
            hostLine = pick(ctx, "Back a guess. Bigger odds, bigger risk.", "Who's closest? Put your points where your mouth is."),
        )
        return Step(next, listOf(Effect.Phase(duration)))
    }
```

4. In `waitingOn`, add before `VICTIM ->`:

```kotlin
        BET -> s.teams.flatMap { it.members }.filter { s.bets[it.v]?.stake == null }.toSet()
```

- [ ] **Step 6: Settle the bets in `score`**

In `score()`:

1. Just before `when (s.format) {`, add `var betDeltas = emptyMap<String, Int>()`.
2. In the `BALLPARK ->` branch, replace the line `val guesses = active.associate { t -> t.id to median(t.members.mapNotNull { s.votes[it.v]?.number }) }` with `val guesses = teamGuesses(s)`.
3. Still in that branch, add this block immediately before `answers = active.map { t ->` (it needs `diffs`, `distinct` and `item`, all defined just above):

```kotlin
                val roundPoints = active.associate { t ->
                    val rank = diffs[t.id]?.let { distinct.indexOf(it) + 1 }
                    val bull = diffs[t.id]?.let { it <= abs(item.answer) * BULLSEYE_TOLERANCE } == true
                    t.id to ((when { rank == 1 -> CLOSEST_POINTS; rank == 2 && active.size >= 3 -> SECOND_POINTS; else -> 0 }) + if (bull) BULLSEYE_POINTS else 0)
                }
                val teamBets = if (s.line.isEmpty()) emptyMap() else active.mapNotNull { t -> Betting.teamBet(t.members, s.bets)?.let { t.id to it } }.toMap()
                val settled = Betting.settle(s.line, teamBets, item.answer, active.associate { it.id to it.score + (roundPoints[it.id] ?: 0) })
                betDeltas = settled.mapValues { it.value.delta }
```

   and change the `TeamAnswer(...)` call inside `answers = active.map { ... }` to also pass `bet = settled[t.id]`.
4. In the same branch, change the first line of the host-line expression from `line = when {` to `val closestLine = when {` (keep its body), and add right after that expression's closing `}`:

```kotlin
                line = listOfNotNull(closestLine, betTalk(s, settled)).joinToString(" ")
```

   `line` is the `val line: String?` declared before the `when (s.format)`, so keep it assigned exactly once in this branch.
5. After the `when (s.format) { ... }` block, replace the score application

```kotlin
        teams = teams.map { t -> points[t.id]?.let { t.copy(score = t.score + it) } ?: t }
        val effects = points.flatMap { (teamId, pts) -> awardTeam(s.teams, teamId, pts, "${s.format} answer") }
```

with:

```kotlin
        teams = teams.map { t ->
            val add = (points[t.id] ?: 0) + (betDeltas[t.id] ?: 0)
            if (add == 0) t else t.copy(score = (t.score + add).coerceAtLeast(0))
        }
        val winnings = points.toMutableMap().also { won -> betDeltas.filterValues { it > 0 }.forEach { (team, d) -> won[team] = (won[team] ?: 0) + d } }
        val effects = winnings.flatMap { (teamId, pts) -> awardTeam(s.teams, teamId, pts, "${s.format} answer") }
```

6. Add the host-line helper below `score`:

```kotlin
    /** What Brainy says about the bets, if anything is worth saying. */
    private fun betTalk(s: TriviaState, settled: Map<String, BetResult>): String? {
        if (s.line.isNotEmpty() && settled.isEmpty()) return "Nobody dared to bet."
        val big = settled.entries.filter { it.value.won && (it.value.odds == 3 || it.value.stake == Betting.STAKES.last()) }.maxByOrNull { it.value.delta }
        if (big != null) return "${nameOf(s, big.key)} bet big and it paid: +${big.value.delta}."
        val bust = settled.entries.filter { !it.value.won && it.value.stake >= 500 }.maxByOrNull { it.value.stake }
        if (bust != null) return "${nameOf(s, bust.key)} lost a ${bust.value.stake} bet. Ouch."
        return null
    }
```

- [ ] **Step 7: Fill in the TV view (so `tv.bet` works) and run the tests**

In `BrainDrain.tvView`:

1. Change `val live = s.phase == QUESTION || showAnswer` to `val live = s.phase == QUESTION || s.phase == BET || showAnswer`.
2. In the `TriviaTv(...)` constructor call, after `drink = s.drink,` add:

```kotlin
            bet = if (s.phase == BET) BetInfo(s.line, s.teams.filter { Betting.teamBet(it.members, s.bets) != null }.map { it.id }) else null,
```

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test --tests "partyos.engine.games.trivia.*"`
Expected: BUILD SUCCESSFUL. The test `changingYourMindResetsTheStakeAndTheTeamWaitsForYouAgain` needs the phone screens from Task 3 (it asserts a `ChoiceList` prompt). If only that test fails, it's expected until Task 3 Step 3; otherwise fix the failure now.

- [ ] **Step 8: Commit**

```bash
git add tv/engine/src/main/kotlin/partyos/engine/TriviaViews.kt tv/engine/src/main/kotlin/partyos/engine/games/trivia/BrainDrain.kt tv/engine/src/test/kotlin/partyos/engine/games/trivia/BrainDrainTest.kt
git commit -m "feat(trivia): the Ballpark bet phase (open, take bets, settle at the reveal)"
```

---

### Task 3: What the phones see

**Files:**
- Modify: `tv/engine/src/main/kotlin/partyos/engine/games/trivia/BrainDrain.kt` (`playerView`, `revealScreen`)
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/trivia/BrainDrainTest.kt`

**Interfaces:**
- Consumes: Tasks 1 and 2 outputs.
- Produces: the phone `Screen.ChoiceList(kind = "bet", style = "teams")` described below; the Ballpark reveal screen mentions the bet.

- [ ] **Step 1: Write the failing phone-screen tests**

Add to `BrainDrainTest.kt` after the Task 2 tests:

```kotlin
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
        assertTrue(screen.prompt.contains("Brainiacs"))
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
    }

    @Test fun theRevealScreenMentionsTheBet() {
        val (a, b, c, d) = atBallparkBet()
        lockBet(a, "T1", 250); lockBet(b, "T1", 250); lockBet(c, "T1", 250); lockBet(d, "T1", 250)
        assertTrue(waiting(a).detail!!.contains("bet +250"), waiting(a).detail)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test --tests "partyos.engine.games.trivia.BrainDrainTest"`
Expected: FAIL (the phone still shows the "Eyes on the TV" waiting screen during the bet phase).

- [ ] **Step 3: Phone screens**

In `BrainDrain.playerView`, add this branch to the `when (s.phase)` just before `REVEAL -> revealScreen(s, team, tag)`:

```kotlin
            BET -> {
                if (team == null) return Screen.Waiting("You're in next question", "We'll put you on the smallest team")
                betScreen(s, team, tag, s.bets[who.v])
            }
```

Add this function next to `revealScreen`:

```kotlin
    /** Two taps on the phone: back a guess, then pick a stake. A locked bet shows its guess and can be changed. */
    private fun betScreen(s: TriviaState, team: TTeam, tag: TeamTag?, mine: TBet?): Screen {
        val backers = LinkedHashMap<String, MutableList<PlayerId>>()
        for (m in team.members) s.bets[m.v]?.on?.let { backers.getOrPut(it) { mutableListOf() } += m }
        val on = mine?.on
        if (on != null && mine.stake == null) {
            val odds = s.line.firstOrNull { it.team == on }?.odds ?: 1
            val stakes = Betting.allowedStakes(team.score).map { Choice(Betting.stakeId(it), "$it pts", detail = "wins ${it * odds}") }
            return Screen.ChoiceList(
                "How much on ${nameOf(s, on)}? Pays $odds×", stakes + Choice(Betting.BACK, "Change guess"), null, "bet",
                style = "teams", votes = backers, team = tag,
            )
        }
        val guesses = s.line.map { o ->
            Choice(o.team, nameOf(s, o.team), s.teams.firstOrNull { it.id == o.team }?.color, "guess ${formatNumber(o.number)} · pays ${o.odds}×")
        }
        val skipped = mine != null && mine.on == null && mine.stake == 0
        return Screen.ChoiceList(
            if (on != null) "Backing ${nameOf(s, on)} for ${mine.stake}. Tap to change" else "Who's closest? Back a guess",
            guesses + Choice(Betting.SKIP, "Skip betting"), on ?: if (skipped) Betting.SKIP else null, "bet",
            style = "teams", votes = backers, team = tag,
        )
    }
```

- [ ] **Step 4: The reveal screen mentions the bet**

In `revealScreen`, change `BALLPARK -> when {` to `BALLPARK -> withBet(a, when {` and the closing `}` of that `when` (the one just before `WRITE -> when {`) to `})`. Add this function next to `betScreen`:

```kotlin
    /** Adds "bet +250" or "bet -250" to a Ballpark result screen when the team had a bet. */
    private fun withBet(a: TeamAnswer, screen: Screen): Screen {
        val bet = a.bet?.takeIf { it.on != null }
        if (bet == null || screen !is Screen.Waiting) return screen
        val note = "bet " + if (bet.won) "+${bet.delta}" else if (bet.delta == 0) "lost" else "${bet.delta}"
        return screen.copy(detail = listOfNotNull(screen.detail, note).joinToString(" · "))
    }
```

- [ ] **Step 5: Run the whole engine suite**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test`
Expected: BUILD SUCCESSFUL, the whole engine suite passes (Task 1 and 2 tests included).

- [ ] **Step 6: Commit**

```bash
git add tv/engine/src/main/kotlin/partyos/engine/games/trivia/BrainDrain.kt tv/engine/src/test/kotlin/partyos/engine/games/trivia/BrainDrainTest.kt
git commit -m "feat(trivia): bet screens for phones"
```

---

### Task 4: Wire protocol fixtures and TypeScript types

**Files:**
- Modify: `tv/server/src/test/kotlin/partyos/server/ProtocolFixturesTest.kt`
- Regenerate: `controller/src/protocol/fixtures/server-messages.json`, `client-messages.json`
- Modify: `controller/src/protocol.test.ts`
- Modify: `controller/src/tv/types.ts`

**Interfaces:**
- Consumes: `TriviaTv.bet`, `TeamAnswer.bet`, `BetOption/BetResult/BetInfo` from Tasks 1 and 2.
- Produces (used by Task 5): TS `TriviaPhase` includes `'bet'`; `BetOption`, `BetResult` interfaces; `TeamAnswer.bet?: BetResult`; `TriviaTv.bet?: { line: BetOption[]; locked: string[] }`.

- [ ] **Step 1: Add the fixtures to the Kotlin test**

In `ProtocolFixturesTest.kt`:

1. Add imports `partyos.engine.BetInfo`, `partyos.engine.BetOption`, `partyos.engine.BetResult` (alphabetical, with the other `partyos.engine.*` imports).
2. In the `serverMessages` list, immediately after the trivia `ServerMsg.Tv(13, ...)` entry (the one with `phase = "reveal", format = "quick"`), add two more `ServerMsg.Tv` entries. Copy that entry's `TvState(...)` arguments exactly (same `roomCode`, `players`, `scores`, `captain`, `settings`, and so on), change the first argument (the seq) to the next two unused values, and replace `game = TriviaTv(...)` with these:

```kotlin
                    game = TriviaTv(
                        phase = "bet", format = "ballpark", round = 2, totalRounds = 5, q = 1, qTotal = 3, durationMs = 15_000,
                        prompt = "How many bones are in the adult human body?", category = "Body", unit = "bones",
                        teams = listOf(TriviaTeam("T1", "Quizzards", "#FF4B3E", listOf(sam), 1250)),
                        bet = BetInfo(listOf(BetOption("T1", 180.0, 2), BetOption("T2", 206.0, 1)), locked = listOf("T2")),
                        hostLine = "Back a guess. Bigger odds, bigger risk.",
                    ),
```

and

```kotlin
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
```

3. Add a phone view for the bet screen next to the `Screen.NumberEntry(...)` view line: `view(Screen.ChoiceList("Who's closest? Back a guess", listOf(Choice("T1", "Quizzards", "#FF4B3E", "guess 180 · pays 2×"), Choice("skip", "Skip betting")), null, "bet", style = "teams", team = team)),`
4. Append, as the last element of the `clientMessages` list: `ClientMsg.Action("a-6", 11, JsonObject(mapOf("kind" to JsonPrimitive("bet"), "option" to JsonPrimitive("T1")))),`

- [ ] **Step 2: Regenerate the fixtures**

Run: `cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :server:cleanTest :server:test --tests "partyos.server.ProtocolFixturesTest" -PupdateFixtures`
Expected: BUILD SUCCESSFUL; `git diff --stat controller/src/protocol/fixtures` shows both JSON files changed. Re-run the same command without `-PupdateFixtures` and confirm it still passes.

- [ ] **Step 3: Update the TypeScript types**

In `controller/src/tv/types.ts`:

```ts
export type TriviaPhase = 'teamup' | 'intro' | 'question' | 'bet' | 'reveal' | 'victim' | 'steal' | 'standings' | 'podium' | 'awards'
```

Add before `export interface TeamAnswer`:

```ts
/** Ballpark betting: one backable guess and what backing it pays (the multiplier on the stake). */
export interface BetOption { team: string; number: number; odds: number }
/** How a team's bet came out; `delta` is the real change to their score. */
export interface BetResult { on?: string; stake: number; odds: number; won: boolean; delta: number }
```

Add `bet?: BetResult` to `TeamAnswer` (after `text?: string`), and add this to `TriviaTv` (after `drink?:`):

```ts
  /** The bet phase only: every guess with its odds, and which teams have a bet in (not who backed what). */
  bet?: { line: BetOption[]; locked: string[] }
```

Also update the `ballpark` entry of `ROUND_RULES` in this file to match the engine: `"Guess the number. Your team's guess is the middle of everyone's. Closest wins. Then bet on whose guess is closest."`

- [ ] **Step 4: Add the client message to the TS protocol test**

In `controller/src/protocol.test.ts`, in the `ours` array, append after the last entry (matching the Kotlin order): `{ t: 'action', id: 'a-6', round: 11, payload: { kind: 'bet', option: 'T1' } },`

- [ ] **Step 5: Run the checks**

Run: `cd controller && npx tsc -b && npx vitest run src/protocol.test.ts`
Expected: no type errors; protocol tests pass.

- [ ] **Step 6: Commit**

```bash
git add tv/server/src/test/kotlin/partyos/server/ProtocolFixturesTest.kt controller/src/protocol/fixtures controller/src/protocol.test.ts controller/src/tv/types.ts
git commit -m "feat(trivia): protocol fixtures and TV types for Ballpark betting"
```

---

### Task 5: The TV bet view and reveal

**Files:**
- Modify: `controller/src/tv/TriviaStage.tsx`
- Modify: `controller/src/tv/trivia.css`
- Modify: `controller/src/tv/Gallery.tsx`
- Modify: `controller/scripts/shots.mjs`

**Interfaces:**
- Consumes: Task 4's TS types.
- Produces: gallery beats `ballpark-bet` and `ballpark-bet-reveal`.

- [ ] **Step 1: Add the gallery beats**

In `Gallery.tsx`, add these entries to `BEATS` right after `'ballpark-reveal'`:

```ts
  'ballpark-bet': { ...base, phase: 'bet', format: 'ballpark', prompt: 'How many bones are in the adult human body?', category: 'Body', unit: 'bones', options: [], durationMs: 15000,
    bet: { line: [{ team: 'T1', number: 180, odds: 2 }, { team: 'T2', number: 206, odds: 1 }, { team: 'T3', number: 320, odds: 3 }], locked: ['T2'] },
    hostLine: 'Back a guess. Bigger odds, bigger risk.' },
  'ballpark-bet-reveal': { ...base, format: 'ballpark', phase: 'reveal', prompt: 'How many bones are in the adult human body?', category: 'Body', unit: 'bones', options: [],
    reveal: { correct: [], answerText: '206 bones', number: 206, answers: [
      { team: 'T1', number: 180, picks: [], correct: false, points: 0, rank: 2, bullseye: false, bet: { on: 'T2', stake: 500, odds: 1, won: true, delta: 500 } },
      { team: 'T2', number: 206, picks: [], correct: true, points: 1500, rank: 1, bullseye: true },
      { team: 'T3', number: 320, picks: [], correct: false, points: 0, rank: 3, bullseye: false, bet: { on: 'T3', stake: 250, odds: 3, won: false, delta: -250 } }] },
    hostLine: 'Smarty Pints backed the right horse: +500.', fact: 'Babies are born with around 300.' },
```

In `controller/scripts/shots.mjs`, add `'ballpark-bet', 'ballpark-bet-reveal'` after `'ballpark-reveal'` in the `trivia` list.

- [ ] **Step 2: Build the bet view**

In `TriviaStage.tsx`:

1. Update `deltasOf` to include bets:

```ts
function deltasOf(g: TriviaTv): Record<string, number> {
  const out: Record<string, number> = {}
  for (const a of g.reveal?.answers ?? []) {
    const d = a.points + (a.bet?.delta ?? 0)
    if (d) out[a.team] = d
  }
  return out
}
```

2. In `Ballpark`, replace the `{!revealed ? (<div className="ballpark-wait">...</div>) : <NumberLine g={g} />}` expression with:

```tsx
      {g.phase === 'bet' ? <BetLine g={g} /> : !revealed ? (
        <div className="ballpark-wait">
          <Panel className="unit-card" fill={C.paper} tilt={-2}>
            <span>Type a number on your phone</span>
            {g.unit && <b className="display">in {g.unit}</b>}
            <small>Your team's guess is the middle of everyone's.</small>
          </Panel>
          <Brainy mood="happy" size={230} />
        </div>
      ) : <NumberLine g={g} />}
```

and change the foot line `{revealed ? <RevealTalk g={g} /> : <div />}` in that component to `{revealed ? <RevealTalk g={g} /> : g.phase === 'bet' ? <HostSays line={g.hostLine} mood="smug" size={120} /> : <div />}`.

3. Add the bet view after the `fmt` helper (before `NumberLine`):

```tsx
/** Every team's guess planted on the number line with its odds; the answer stays hidden. */
function BetLine({ g }: { g: TriviaTv }) {
  const line = g.bet?.line ?? []
  const locked = g.bet?.locked ?? []
  useEffect(() => { if (locked.length > 0) sfx.stamp() }, [locked.length])
  const nums = line.map((l) => l.number)
  let lo = Math.min(...nums), hi = Math.max(...nums)
  if (hi === lo) { lo -= Math.max(1, Math.abs(lo) * 0.1); hi += Math.max(1, Math.abs(hi) * 0.1) }
  const pad = (hi - lo) * 0.1
  lo -= pad; hi += pad
  const x = (v: number) => `${Math.min(90, Math.max(10, ((v - lo) / (hi - lo)) * 100))}%`
  return (
    <div className="numberline bet">
      <div className="nl-axis" />
      {line.map((l, i) => {
        const t = g.teams.find((tt) => tt.id === l.team)
        if (!t) return null
        return (
          <motion.div key={l.team} className="nl-flag" style={{ left: x(l.number), top: 20 + (i % 3) * 80 }}
            initial={{ y: -300, opacity: 0 }} animate={{ y: 0, opacity: 1 }} transition={{ type: 'spring', stiffness: 380, damping: 18, delay: 0.12 * i }}>
            <span className="pole" style={{ height: 250 - (i % 3) * 80 }} />
            <span className="nl-team" style={{ background: t.color, color: inkOn(t.color) }}>{t.name}<em>{fmt(l.number)}</em><i className="odds">×{l.odds}</i></span>
          </motion.div>
        )
      })}
    </div>
  )
}
```

4. In `TeamStrip`, after the `{g.phase === 'question' && (...)}` pips block, add:

```tsx
          {g.phase === 'bet' && g.bet?.locked.includes(t.id) && <span className="bet-in">BET IN</span>}
```

5. In `NumberLine`, inside the `guesses.map((a, i) => { ... })` flag, after the `nl-team` span, add the settled bets for that flag (bets backing this team's guess):

```tsx
            {r.answers.filter((b) => b.bet?.on === a.team).map((b) => {
              const bt = g.teams.find((tt) => tt.id === b.team)
              const d = b.bet!.delta
              return (
                <Pop key={b.team} delay={1.9}>
                  <span className={`bet-chip ${b.bet!.won ? 'won' : 'lost'}`}>{bt?.name} {b.bet!.won ? `+${d.toLocaleString()}` : d ? d.toLocaleString() : 'LOST'}</span>
                </Pop>
              )
            })}
```

   and extend the jackpot trigger: change `const bull = guesses.some((a) => a.bullseye)` to `const bull = guesses.some((a) => a.bullseye)` followed by a new line `const longShot = r.answers.some((a) => a.bet?.won && a.bet.odds === 3)` and change the `useLater` line to `useLater(`bp${g.q}`, 1300, () => { sfx.stamp(); if (bull || longShot) sfx.jackpot() })`. Leave the `<Burst text="BULLSEYE!" ...>` condition as `bull &&`.

- [ ] **Step 3: Style it**

Append to `controller/src/tv/trivia.css` after the `.nl-bull` rules:

```css
/* betting: odds on each flag, who's in, and the chips that settle at the reveal */
.nl-team .odds { font-style: normal; font-family: var(--font-display); font-size: 24px; background: var(--sun); color: var(--ink); border-radius: 8px; padding: 0 8px; border: 3px solid var(--ink); }
.bet-in { font-size: 20px; font-weight: 900; background: var(--lime); border: 4px solid var(--ink); border-radius: 12px; padding: 2px 10px; box-shadow: 3px 3px 0 var(--ink); }
.bet-chip { display: inline-block; margin-bottom: 8px; font-size: 26px; font-weight: 900; border: 4px solid var(--ink); border-radius: 12px; padding: 2px 12px; box-shadow: 4px 4px 0 var(--ink); white-space: nowrap; }
.bet-chip.won { background: var(--lime); }
.bet-chip.lost { background: var(--tomato); color: var(--white); }
```

- [ ] **Step 4: Check types, tests and the pictures**

Run: `cd controller && npx tsc -b && npx vitest run`
Expected: no type errors, all tests pass.

Then build and screenshot the two beats (the gallery needs `vite preview`; if `http://127.0.0.1:4173` is not already serving `controller/dist`, start it with `cd controller && npx vite preview --port 4173 --host 127.0.0.1` in another terminal first):

```bash
cd controller && npx vite build
cd .. && node controller/scripts/shots.mjs controller/test-results/shots http://127.0.0.1:4173 ballpark-bet,ballpark-bet-reveal trivia
```

Look at both PNGs in `controller/test-results/shots/`. Check: flags don't overlap each other or the question bubble, the odds badges and `BET IN` tags are readable, the chips on the reveal sit on the flag they back and don't cover the fun fact or the team strips, and the "+500" / "-250" deltas appear on the team strips. If the usage line in `shots.mjs`'s header comment differs from the command above, follow the header.

- [ ] **Step 5: Commit**

```bash
git add controller/src/tv/TriviaStage.tsx controller/src/tv/trivia.css controller/src/tv/Gallery.tsx controller/scripts/shots.mjs
git commit -m "feat(trivia): TV bet view with odds, BET IN tags and settled bet chips"
```

---

### Task 6: Docs, the spec, and the full check

**Files:**
- Modify: `docs/party-os/show-bible.md`
- Modify: `docs/superpowers/specs/2026-10-02-brain-drain-ballpark-betting-design.md`
- Modify (only if needed): `controller/scripts/bots.mjs`

- [ ] **Step 1: Document the beat**

In `docs/party-os/show-bible.md`, in the Brain Drain beat sheet table, add a row after `Ballpark reveal`:

```
| Ballpark bet | every team's guess planted on the number line with a ×odds tag, the answer hidden, BET IN tags as teams lock; the reveal drops chips on the flags that were backed | stamp per lock; jackpot on a ×3 win; Brainy reacts to big wins and busts |
```

- [ ] **Step 2: Bring the spec in line with what was built**

In the spec, in the Engine section, replace the "Input:" bullet with: `Input: a "bet" message in the BET phase carrying one option field (what ChoiceList already sends): a team id backs that guess; s250 / s500 / s1000 sets the stake; skip skips; back clears the guess. Rejects: NOT_NOW outside the phase or for a stake before a guess; BAD_OPTION for an unknown team; BAD_STAKE for a stake not in STAKES or above the allowance.` In the Betting rules section, change the settlement bullet's loss sentence to: `A loss subtracts the stake, but never more than the team has (its score plus this round's points), so house money can't push a score below zero, and the reported loss is the real change.` Add one line to the Engine section: `The constants and the pure rules live in a Betting object (Betting.kt).`

- [ ] **Step 3: Run everything**

```bash
cd tv && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --offline :engine:cleanTest :engine:test :server:cleanTest :server:test
cd ../controller && npx tsc -b && npx vitest run
```

Expected: all Kotlin and TypeScript tests pass. If a pre-existing test fails, it is from this change: fix it, never skip it.

- [ ] **Step 4: Try it with bots**

Start the dev server (`partyos-web` in `.claude/launch.json`) and run `node controller/scripts/bots.mjs 4 http://127.0.0.1:8080`, start Brain Drain from the TV, and play until a Ballpark question. Confirm the bet phase appears on the TV, the bots' phones show the guess list, and the reveal shows chips. If `bots.mjs` does not answer the `bet` screen (it answers screens by kind), add a `bet` branch there that picks a random option from the screen's options each time it is shown, and include it in this commit.

- [ ] **Step 5: Commit**

```bash
git add docs/party-os/show-bible.md docs/superpowers/specs/2026-10-02-brain-drain-ballpark-betting-design.md controller/scripts/bots.mjs
git commit -m "docs: Ballpark betting beat and spec update"
```

---

## Self-review notes

- **Spec coverage:** flow (T2), odds, stakes and settlement (T1), team bet by plurality (T1), engine state, input and transition (T2), phone screens (T3), TV bet view and reveal chips (T5), protocol and fixtures (T4), sound (T5: `stamp` on lock-in, jackpot on a 3x win), saved-show compatibility (T2 `oldSavedShowStillLoads`), edge cases (T2 tests), docs (T6). Nothing in the spec is left without a task.
- **Type consistency:** `BetOption`, `BetResult`, `BetInfo`, `TBet` and the `Betting` function names are used identically across tasks; option ids (`skip`, `back`, `s<amount>`) are defined once in `Betting` and reused.
- **Known deviations from the spec:** one `option` field instead of `team`/`stake`; loss capped at what the team has. Both are written into the spec in Task 6.
