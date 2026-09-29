# Answer & Question (Jeopardy) Revamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Answer & Question as a real Jeopardy: phone buzzers, board control, wrong-answer risk, Daily Doubles, Double Jeopardy and Final Jeopardy, picked from the captain's or picker's phone, in a new Studio-set theme.

**Architecture:** The Kotlin `Jeopardy` module is rewritten as a phase state machine whose rules live in a small pure `JeopardyRules` object. Two new phone screens (`board`, `buzzer`) and a richer `JeopardyTv` payload go over the wire; the phone gets `BoardScreen` and `BuzzerScreen`, the TV gets a themed stage split by phase. The runtime is unchanged apart from one additive field (`GameContext.captain`) and one lobby option (`show`).

**Tech Stack:** Kotlin 2 / JDK 17 / kotlinx.serialization / Ktor (tv/), React 19 + TypeScript + Vite + Vitest + Playwright (controller/).

**Spec:** `docs/superpowers/specs/2026-09-29-jeopardy-revamp-design.md`

## Global Constraints

- Game id `jeopardy`, title `Answer & Question`, `minPlayers = 2`, `maxPlayers = 16`, `lateJoin = LateJoin.ANYTIME`, individual scoring.
- A board is 5 categories × 5 clues; a clue's value is `200 × (row + 1)` on board 1 and `400 × (row + 1)` on board 2 (row 0 is the cheapest). Daily Doubles: 1 on board 1, 2 on board 2, on rows 1–4 only, at most one per category.
- `show` lobby option: `0` Short (board 1 then Final), `1` Full (board 1, board 2, Final); default 0; range `0..1`.
- Board control: round 1 starts with the captain (else the earliest-joined player); round 2 starts with the lowest score (ties: earliest joined); afterwards the last player to answer right picks, and if nobody did the picker stays. The captain can always pick (`GameContext.captain`); the TV keyboard's `HostCmd.GameAction("pick:<clue id>")` still works.
- Timers (ms): pick 20 000, read `clamp(2500 + 55 × chars, 3000, 8000)`, buzz 10 000, rebuzz 8 000, answer 15 000, lockout 1 000, Daily Double wager 20 000 / answer 30 000, reveal 5 000, intro 8 000, break 8 000, final category 6 000 / wager 30 000 / answer 30 000 / reveal step 5 000, podium 15 000. Decision timers (pick, buzz, rebuzz, answer, wagers, final wager/answer) use `ctx.timer(...)`; reveals, intro, break and the read do not.
- A wrong answer subtracts the full stake and scores may go negative. Right answers use `AnswerMatch.accepts(asAnswer(text), answer)` where `asAnswer` drops a leading "what/who/where/when is/are/was/were/'s" (already in `Jeopardy.kt`).
- Daily Double wager: minimum 5, maximum `max(score, top value of the round, 5)`; no wager in time counts as 5. Final wager: `0..max(0, score)`, only for players with a score above 0; anyone else wagers 0. No positive score at Final time skips Final.
- Drink calls: a wrong buzz-in answer is 1 sip; a wrong Daily Double or Final answer with a wager is 2 sips. They go through `ofWater(...)` and honour `(ctx.settings["drinks"] ?: 1) != 0`.
- Pack format is **version 2**: `categories` (id, name, exactly 5 clues listed cheapest first) and `finals`. At least 12 categories and 4 finals are required to load; the shipped pack has 14 and 8.
- Web TV only: `Jeopardy()` stays registered in `tv/devserver/.../Main.kt`; do not touch `tv/app`.
- No emoji, light bulbs or synth music in the UI; every choice is colour and shape and text; buttons are thumb-sized.
- Gradle needs JDK 17: prefix Gradle commands with `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`.
- Commit messages carry no attribution lines.

## Deviations from the spec (decided while reading the code, confirm at review)

1. **No `up` buzzer state.** The player who wins the floor gets the existing `TextEntry` screen, so `Screen.Buzzer.state` is `reading | open | locked | beaten | tried | out`.
2. **`Screen.Buzzer` carries `live`.** The server cannot push a view when a 1 s lockout expires, so the phone needs to know the buzz window is open in order to flip `locked` to `open` by itself. `live` is true during the `buzz` phase.
3. **No "shown" counter for the intro.** The TV staggers the category reveal with CSS; the payload only carries the ordered category names.
4. **The old `JeopardyTest.kt` is replaced wholesale,** and the old v1 pack file is rewritten in v2 (its five categories and 25 clues are kept verbatim).

## Review Focus

- The picker disconnects (or is not connected) while the board is up: the pick must be made for them immediately, not hang. (Task 2 test.)
- Two phones buzz at once: exactly one gets the floor and the other is refused. (Task 3 test.)
- Everyone in the room answers wrong: the clue must end, never reopen forever. (Task 3 test.)
- Every player is at zero or below when the last clue is used: Final Jeopardy is skipped and the show goes straight to the podium. (Task 5 test.)
- A Daily Double for a player with a negative or tiny score: the wager ceiling is the round's top value, not their score. (Task 1 and Task 4 tests.)
- A party saved mid-clue restores with the same floor and phase. (Task 5 test.)
- A player who joins mid-clue watches that clue and can ring in on the next one. (Task 3 test.)

## File Structure

| File | Responsibility |
| --- | --- |
| `tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/JeopardyRules.kt` (new) | Pure rules: values, Daily Double placement, read time, wager bounds, first picker, auto-pick, Final order |
| `.../games/jeopardy/JeopardyPack.kt` (rewrite) | Pack v2 format, validation, loader |
| `tv/engine/src/main/resources/packs/jeopardy-core.json` (rewrite) | 14 categories × 5 clues + 8 finals |
| `.../games/jeopardy/Jeopardy.kt` (rewrite) | The `GameModule`: state, phases, actions, views |
| `tv/engine/src/main/kotlin/partyos/engine/JeopardyViews.kt` (rewrite) | `JeopardyTv` and row types |
| `tv/engine/src/main/kotlin/partyos/engine/Views.kt` (modify) | `Screen.Board`, `Screen.Buzzer`, `BoardCell` |
| `tv/engine/src/main/kotlin/partyos/engine/Game.kt`, `PartyEngine.kt` (modify) | `GameContext.captain`; the `show` option |
| `tv/server/src/test/kotlin/partyos/server/ProtocolFixturesTest.kt` (modify) | Wire samples; `JeopardySimulatedPartyTest.kt` (new) is the 16-bot run |
| `controller/src/protocol.ts`, `protocol.test.ts` (modify) | `board`/`buzzer` screens, `show` option key |
| `controller/src/screens/BoardScreen.tsx`, `BuzzerScreen.tsx`, `jeopardy-phone.css` (new); `ScreenView.tsx` (modify) | The phone board and BUZZ button |
| `controller/src/tv/JeopardyStage.tsx` (rewrite) + `JeopardyBoardView.tsx`, `JeopardyClueView.tsx`, `JeopardyFinalView.tsx`, `jeopardy.css` (new) | The Studio-set TV stage |
| `controller/src/tv/types.ts`, `TvPage.tsx`, `ThemeGallery.tsx`, `tv/fixtures/jeopardy-theme.ts` (modify/new); `theme/gameTheme.ts`, `theme/games.css` (modify) | Types, routing, theme, gallery beats |
| `controller/src/pages/Captain.tsx` (modify) | "Show: Short / Full" stepper |
| `controller/e2e/jeopardy.spec.ts` (rewrite), `jeopardy-gallery.spec.ts` (new) | Real-phone play and layout/contrast checks |
| `README.md` (modify) | Document the new game |

---

### Task 1: Pure rules

**Files:**
- Create: `tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/JeopardyRules.kt`
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/jeopardy/JeopardyRulesTest.kt`

**Interfaces:**
- Produces: `data class Cell(val id: String, val col: Int, val row: Int, val value: Int)`; `object JeopardyRules { const val CATEGORIES_PER_BOARD = 5; const val CLUES_PER_CATEGORY = 5; const val MIN_WAGER = 5; const val LOCKOUT_MS = 1_000L; fun valueOf(round: Int, row: Int): Int; fun topValue(round: Int): Int; fun dailyDoubleCount(round: Int): Int; fun readMs(clue: String): Long; fun dailyDoubles(categories: Int, count: Int, random: Random): List<Pair<Int, Int>> /* (col,row) */; fun wagerRange(score: Int, round: Int): IntRange; fun finalWagerRange(score: Int): IntRange; fun firstPicker(round: Int, players: List<String>, scores: Map<String, Int>, captain: String?): String; fun autoPick(unused: List<Cell>, random: Random): Cell; fun finalOrder(players: List<String>, scores: Map<String, Int>): List<String>; fun clueDelta(stake: Int, right: Boolean): Int }`. Player ids are plain `PlayerId.v` strings; `players` lists are in join order.

- [ ] **Step 1: Write the failing test**

```kotlin
package partyos.engine.games.jeopardy

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JeopardyRulesTest {
    @Test fun valuesDoubleOnTheSecondBoard() {
        assertEquals(listOf(200, 400, 600, 800, 1000), (0..4).map { JeopardyRules.valueOf(1, it) })
        assertEquals(listOf(400, 800, 1200, 1600, 2000), (0..4).map { JeopardyRules.valueOf(2, it) })
        assertEquals(1000, JeopardyRules.topValue(1))
        assertEquals(2000, JeopardyRules.topValue(2))
    }

    @Test fun oneDailyDoubleOnTheFirstBoardAndTwoOnTheSecond() {
        assertEquals(1, JeopardyRules.dailyDoubleCount(1))
        assertEquals(2, JeopardyRules.dailyDoubleCount(2))
    }

    @Test fun readTimeGrowsWithTheClueButStaysBetweenThreeAndEightSeconds() {
        assertEquals(3_000L, JeopardyRules.readMs(""))
        assertEquals(3_050L, JeopardyRules.readMs("x".repeat(10)))
        assertEquals(2_500L + 55L * 80, JeopardyRules.readMs("x".repeat(80)))
        assertEquals(8_000L, JeopardyRules.readMs("x".repeat(500)))
    }

    @Test fun dailyDoublesAreInDifferentColumnsAndNeverOnTheCheapRow() {
        repeat(50) { seed ->
            val spots = JeopardyRules.dailyDoubles(5, 2, Random(seed))
            assertEquals(2, spots.size)
            assertEquals(2, spots.map { it.first }.toSet().size, "columns for seed $seed")
            assertTrue(spots.all { it.first in 0..4 && it.second in 1..4 }, "spots $spots for seed $seed")
        }
        assertEquals(JeopardyRules.dailyDoubles(5, 1, Random(3)), JeopardyRules.dailyDoubles(5, 1, Random(3)))
    }

    @Test fun aDailyDoubleWagerHasAFloorOfFiveAndAnUpperBoundOfScoreOrTheTopValue() {
        assertEquals(5..1200, JeopardyRules.wagerRange(1200, 1))
        assertEquals(5..1000, JeopardyRules.wagerRange(300, 1))
        assertEquals(5..1000, JeopardyRules.wagerRange(-400, 1))
        assertEquals(5..2000, JeopardyRules.wagerRange(900, 2))
    }

    @Test fun aFinalWagerIsBetweenZeroAndTheScore() {
        assertEquals(0..750, JeopardyRules.finalWagerRange(750))
        assertEquals(0..0, JeopardyRules.finalWagerRange(0))
        assertEquals(0..0, JeopardyRules.finalWagerRange(-300))
    }

    @Test fun theCaptainPicksFirstOnBoardOneAndTheLowestScorePicksFirstOnBoardTwo() {
        val players = listOf("a", "b", "c")
        assertEquals("b", JeopardyRules.firstPicker(1, players, emptyMap(), "b"))
        assertEquals("a", JeopardyRules.firstPicker(1, players, emptyMap(), null))
        assertEquals("a", JeopardyRules.firstPicker(1, players, emptyMap(), "gone"))
        assertEquals("c", JeopardyRules.firstPicker(2, players, mapOf("a" to 400, "b" to 200, "c" to -200), "a"))
        assertEquals("b", JeopardyRules.firstPicker(2, players, mapOf("a" to 400, "b" to 0, "c" to 0), "a")) // tie: earliest joined
    }

    @Test fun anAutoPickTakesARandomCellFromTheCheapestRowLeft() {
        val cells = listOf(Cell("a", 0, 0, 200), Cell("b", 1, 0, 200), Cell("c", 0, 1, 400), Cell("d", 1, 2, 600))
        repeat(20) { seed -> assertTrue(JeopardyRules.autoPick(cells, Random(seed)).id in setOf("a", "b")) }
        assertEquals("c", JeopardyRules.autoPick(cells.drop(2).take(1) + cells.drop(3), Random(1)).id)
        assertEquals(JeopardyRules.autoPick(cells, Random(9)), JeopardyRules.autoPick(cells, Random(9)))
    }

    @Test fun theFinalIsRevealedFromTheLowestScoreUpAndTiesKeepJoinOrder() {
        val players = listOf("a", "b", "c", "d")
        val scores = mapOf("a" to 500, "b" to 100, "c" to 500, "d" to -50)
        assertEquals(listOf("d", "b", "a", "c"), JeopardyRules.finalOrder(players, scores))
    }

    @Test fun rightWinsTheStakeAndWrongLosesIt() {
        assertEquals(600, JeopardyRules.clueDelta(600, true))
        assertEquals(-600, JeopardyRules.clueDelta(600, false))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :engine:test --tests 'partyos.engine.games.jeopardy.JeopardyRulesTest' --console=plain`
Expected: FAIL to compile, `Unresolved reference: JeopardyRules`.

- [ ] **Step 3: Write the implementation**

```kotlin
package partyos.engine.games.jeopardy

import kotlin.random.Random

/** One square on a board. [id] is the id of the clue behind it. */
data class Cell(val id: String, val col: Int, val row: Int, val value: Int)

/** Answer & Question's numbers and choices as pure functions, so they are tested without the game module. */
object JeopardyRules {
    const val CATEGORIES_PER_BOARD = 5
    const val CLUES_PER_CATEGORY = 5
    const val MIN_WAGER = 5
    const val LOCKOUT_MS = 1_000L

    /** Board 1 runs $200 to $1000, board 2 ($400 to $2000) doubles it; [row] 0 is the cheapest. */
    fun valueOf(round: Int, row: Int): Int = (if (round == 1) 200 else 400) * (row + 1)

    fun topValue(round: Int): Int = valueOf(round, CLUES_PER_CATEGORY - 1)

    fun dailyDoubleCount(round: Int): Int = if (round == 1) 1 else 2

    /** How long the clue is "read" before phones can ring in: longer clues get longer, between 3 and 8 seconds. */
    fun readMs(clue: String): Long = (2_500 + 55 * clue.length).coerceIn(3_000, 8_000).toLong()

    /** Hidden Daily Double spots as (column, row): different columns, rows 1..4 (never the cheapest row). */
    fun dailyDoubles(categories: Int, count: Int, random: Random): List<Pair<Int, Int>> =
        (0 until categories).shuffled(random).take(count).map { col -> col to (1 until CLUES_PER_CATEGORY).random(random) }

    /** A Daily Double wager: at least 5, at most the player's score or the round's top value, whichever is more. */
    fun wagerRange(score: Int, round: Int): IntRange = MIN_WAGER..maxOf(score, topValue(round), MIN_WAGER)

    fun finalWagerRange(score: Int): IntRange = 0..maxOf(0, score)

    /** Who has the board at the start of [round]. [players] and the result are player ids in join order. */
    fun firstPicker(round: Int, players: List<String>, scores: Map<String, Int>, captain: String?): String =
        if (round == 1) captain?.takeIf { it in players } ?: players.first()
        else players.minByOrNull { scores[it] ?: 0 }!! // minByOrNull keeps the earliest of equal scores

    /** When the picker runs out of time: a random cell on the cheapest row that still has one. */
    fun autoPick(unused: List<Cell>, random: Random): Cell {
        val cheapest = unused.minOf { it.row }
        return unused.filter { it.row == cheapest }.random(random)
    }

    /** Final Jeopardy is revealed from the lowest score up; equal scores keep join order. */
    fun finalOrder(players: List<String>, scores: Map<String, Int>): List<String> = players.sortedBy { scores[it] ?: 0 }

    fun clueDelta(stake: Int, right: Boolean): Int = if (right) stake else -stake
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :engine:test --tests 'partyos.engine.games.jeopardy.JeopardyRulesTest' --console=plain`
Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/JeopardyRules.kt tv/engine/src/test/kotlin/partyos/engine/games/jeopardy/JeopardyRulesTest.kt
git commit -m "feat(jeopardy): pure rules for values, Daily Doubles, wagers, control and Final order"
```

---

### Task 2: Foundation: pack v2, wire types, and the module through the board pick

This task replaces the old module, pack and views in one go, because they share types and must compile together. Nothing runs past "a clue has been opened" until Tasks 3–5.

**Files:**
- Rewrite: `tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/JeopardyPack.kt`
- Rewrite: `tv/engine/src/main/resources/packs/jeopardy-core.json`
- Rewrite: `tv/engine/src/main/kotlin/partyos/engine/JeopardyViews.kt`
- Modify: `tv/engine/src/main/kotlin/partyos/engine/Views.kt` (add `Screen.Board`, `Screen.Buzzer`, `BoardCell`)
- Modify: `tv/engine/src/main/kotlin/partyos/engine/Game.kt` (`GameContext.captain`)
- Modify: `tv/engine/src/main/kotlin/partyos/engine/PartyEngine.kt` (`captain` in `ctx()`, `show` in `optionRange`)
- Rewrite: `tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/Jeopardy.kt`
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/jeopardy/JeopardyPackTest.kt` (new), `JeopardyTest.kt` (replace)

**Interfaces:**
- Consumes: `JeopardyRules`, `Cell` (Task 1); `AnswerMatch.accepts(guess, answer)`; `normalise` from `partyos.engine.games.bluff`; `ofWater`.
- Produces:
  - `JeopardyClue(id, clue, answer)`, `JeopardyCategory(id, name, clues)`, `JeopardyFinal(id, category, clue, answer)`, `JeopardyPack(packId, title, game, version, categories, finals)` with `JeopardyPack.parse/core/validate` and `MIN_CATEGORIES = 12`, `MIN_FINALS = 4`.
  - `Screen.Board(prompt: String, categories: List<String>, cells: List<BoardCell>, canPick: Boolean, pickFor: String? = null, note: String? = null)`; `BoardCell(id: String, col: Int, row: Int, value: Int, used: Boolean)`; `Screen.Buzzer(state: String, category: String, value: Int, detail: String? = null, lockedMs: Int = 0, live: Boolean = false)`.
  - `JeopardyTv` (fields below), `JeopardyCellTv(id, col, row, value, used)`, `JeopardyDelta(id, name, points)`, `JeopardyDrink(id, name, sips, text)`, `JeopardyFinalStep(id, name, answer, wager, right, delta, total)`, `JeopardyFinalTv(category, clue, answer, wagers, expected, steps)`.
  - `GameContext.captain: PlayerId?`; lobby option `show` in `0..1`.
  - `class Jeopardy(pack: JeopardyPack = JeopardyPack.core()) : GameModule<JeopardyState>`, phase constants `Jeopardy.INTRO/PICK/WAGER/CLUE/BUZZ/ANSWER/REVEAL/BREAK/FINAL_CATEGORY/FINAL_WAGER/FINAL_ANSWER/FINAL_REVEAL/PODIUM` (`"intro"`, `"pick"`, ...), and the actions `pick` (`cell`) from the controller or captain, plus `HostCmd.GameAction("pick:<clue id>")`. Tasks 3–5 add `buzz`, `answer`, `wager` and the rest of the deadlines.

- [ ] **Step 1: Write the failing pack test**

Create `JeopardyPackTest.kt`:

```kotlin
package partyos.engine.games.jeopardy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JeopardyPackTest {
    private fun clues(cat: Int) = (0..4).map { JeopardyClue("c$cat-$it", "Clue $cat $it", "Ans${cat}x$it") }
    private fun cats(n: Int) = (0 until n).map { JeopardyCategory("c$it", "Cat $it", clues(it)) }
    private fun finals(n: Int) = (0 until n).map { JeopardyFinal("f$it", "Final $it", "Final clue $it", "Fin$it") }
    private fun pack(categories: List<JeopardyCategory> = cats(12), finals: List<JeopardyFinal> = finals(4), game: String = "jeopardy", version: Int = 2) =
        JeopardyPack("t", "T", game, version, categories, finals)

    @Test fun theShippedPackLoadsAndIsBigEnough() {
        val p = JeopardyPack.core()
        assertTrue(p.categories.size >= 14, "categories: ${p.categories.size}")
        assertTrue(p.categories.all { it.clues.size == 5 })
        assertTrue(p.finals.size >= 8, "finals: ${p.finals.size}")
        val ids = p.categories.flatMap { c -> c.clues.map { it.id } } + p.categories.map { it.id } + p.finals.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun aValidSmallPackPasses() {
        JeopardyPack.validate(pack())
    }

    @Test fun rejectsAPackForAnotherGameOrVersion() {
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(game = "bluff")) }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(version = 1)) }
    }

    @Test fun needsEnoughCategoriesAndFinals() {
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(categories = cats(11))) }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(finals = finals(3))) }
    }

    @Test fun everyCategoryHasExactlyFiveClues() {
        val short = cats(12).toMutableList().also { it[3] = it[3].copy(clues = it[3].clues.take(4)) }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(categories = short)) }
    }

    @Test fun rejectsDuplicateIdsBlankTextAndDuplicateAnswers() {
        val dup = cats(12).toMutableList().also { it[1] = it[1].copy(id = "c0") }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(categories = dup)) }
        val blank = cats(12).toMutableList().also { c -> c[2] = c[2].copy(clues = c[2].clues.mapIndexed { i, x -> if (i == 0) x.copy(clue = " ") else x }) }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(categories = blank)) }
        val sameAnswer = cats(12).toMutableList().also { c -> c[4] = c[4].copy(clues = c[4].clues.mapIndexed { i, x -> if (i == 1) x.copy(answer = "Ans4x0!") else x }) }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(categories = sameAnswer)) }
        val blankFinal = finals(4).toMutableList().also { it[0] = it[0].copy(answer = "") }
        assertFailsWith<IllegalArgumentException> { JeopardyPack.validate(pack(finals = blankFinal)) }
    }
}
```

- [ ] **Step 2: Write the failing module tests**

Replace `JeopardyTest.kt` entirely with:

```kotlin
package partyos.engine.games.jeopardy

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.JeopardyTv
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JeopardyTest {
    private val clock = FakeClock(0)
    private fun clues(cat: Int) = (0..4).map { JeopardyClue("c$cat-$it", "Clue c$cat-$it", "Ans${cat}x$it") }
    private fun pack() = JeopardyPack(
        "test", "Test", "jeopardy", 2,
        (0 until 12).map { JeopardyCategory("c$it", "Cat $it", clues(it)) },
        (0 until 4).map { JeopardyFinal("f$it", "Final $it", "Final clue $it", "Fin$it") },
    )

    private lateinit var e: PartyEngine
    private var n = 0

    /** Starts Answer & Question with [count] players, the tutorial skipped, in the intro phase. */
    private fun start(count: Int, show: Int = 0): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Jeopardy(pack()))))
        val ids = (1..count).map { e.add("P$it") }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("jeopardy", mapOf("show" to show))))
        e.host(HostCmd.SkipPhase)
        return ids
    }

    private val tv get() = e.tvState().stage!!.game as JeopardyTv
    private val seq get() = e.tvState().stage!!.phaseSeq
    private fun act(who: PlayerId, kind: String, vararg kv: Pair<String, Any>) = e.action(
        who, "a${n++}", seq,
        buildJsonObject { put("kind", JsonPrimitive(kind)); kv.forEach { (k, v) -> put(k, if (v is Int) JsonPrimitive(v) else JsonPrimitive(v.toString())) } },
    )
    private fun skip() { e.host(HostCmd.SkipPhase) }
    private fun screen(who: PlayerId) = e.phoneState(who).screen
    private fun toPick() { repeat(3) { if (tv.phase != "pick") skip() } }
    private fun pickCell(id: String) = e.host(HostCmd.GameAction("pick:$id"))

    @Test fun startsInTheIntroWithTheFiveCategoriesOfTheFirstBoard() {
        start(3)
        assertEquals("intro", tv.phase)
        assertEquals(1, tv.round)
        assertEquals(5, tv.categories.size)
        assertEquals(25, tv.cells.size)
        assertEquals(listOf(200, 400, 600, 800, 1000), (0..4).map { r -> tv.cells.first { it.row == r && it.col == 0 }.value })
        assertTrue(tv.cells.none { it.used })
    }

    @Test fun theShowLengthSettingChoosesOneBoardOrTwo() {
        start(3, show = 0); assertEquals(1, tv.boards)
        start(3, show = 1); assertEquals(2, tv.boards)
    }

    @Test fun showIsAnAllowedLobbySettingWithTwoValues() {
        val lobby = PartyEngine(clock, SeededEntropy(1), GameRegistry(emptyList()))
        assertEquals(ActionResult.Ack, lobby.host(HostCmd.SetOption("show", 1)))
        assertEquals(ActionResult.Rejected("BAD_OPTION"), lobby.host(HostCmd.SetOption("show", 2)))
    }

    @Test fun theIntroMovesToTheBoardWithTheCaptainHoldingIt() {
        val ids = start(3)
        skip()
        assertEquals("pick", tv.phase)
        assertEquals(ids[0], tv.controller)
    }

    @Test fun theControllerSeesABoardTheyCanPickFromAndOthersSeeWhoIsPicking() {
        val ids = start(3)
        toPick()
        val mine = assertIs<Screen.Board>(screen(ids[0]))
        assertTrue(mine.canPick)
        assertEquals(5, mine.categories.size)
        assertEquals(25, mine.cells.size)
        val theirs = assertIs<Screen.Board>(screen(ids[1]))
        assertFalse(theirs.canPick)
        assertTrue("P1" in theirs.prompt, theirs.prompt)
    }

    @Test fun theControllerPicksACellFromTheirPhone() {
        val ids = start(3)
        toPick()
        val id = tv.cells.first { it.row == 0 && it.col == 2 }.id
        assertEquals(ActionResult.Ack, act(ids[0], "pick", "cell" to id))
        assertEquals("clue", tv.phase)
        assertEquals("Clue $id", tv.clue)
        assertEquals(200, tv.value)
        assertTrue(tv.cells.first { it.id == id }.let { !it.used }) // it is only used once the clue is revealed
    }

    @Test fun onlyTheControllerOrTheCaptainMayPickAndTheCellMustExist() {
        val ids = start(3)
        toPick()
        val id = tv.cells.first { it.row == 0 }.id
        assertEquals(ActionResult.Rejected("NOT_YOUR_PICK"), act(ids[1], "pick", "cell" to id))
        assertEquals(ActionResult.Rejected("BAD_CELL"), act(ids[0], "pick", "cell" to "nope"))
        assertEquals("pick", tv.phase)
    }

    @Test fun theTvKeyboardCanPickWithoutAPhone() {
        start(3)
        toPick()
        val id = tv.cells.first { it.row == 0 && it.col == 4 }.id
        assertEquals(ActionResult.Ack, pickCell(id))
        assertEquals("clue", tv.phase)
    }

    @Test fun runningOutOfPickTimeTakesACellFromTheCheapestRow() {
        start(3)
        toPick()
        skip() // the pick timer runs out
        assertEquals("clue", tv.phase)
        assertEquals(200, tv.value)
    }

    @Test fun aPickerWhoLeavesIsSkippedAtOnce() {
        val ids = start(3)
        toPick()
        e.setPresence(ids[0], false)
        assertEquals("clue", tv.phase)
    }

    @Test fun eachNightGetsDifferentCategoriesWhileThePoolLasts() {
        start(3)
        val first = tv.categories.toSet()
        e.host(HostCmd.EndGame)
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("jeopardy", mapOf("show" to 0))))
        e.host(HostCmd.SkipPhase)
        assertTrue(first.intersect(tv.categories.toSet()).isEmpty(), "second night repeated $first vs ${tv.categories}")
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :engine:test --tests 'partyos.engine.games.jeopardy.*' --console=plain`
Expected: FAIL to compile (`JeopardyFinal`, `Screen.Board`, `tv.round`, ... unresolved).

- [ ] **Step 4: Rewrite the pack**

Replace `tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/JeopardyPack.kt`:

```kotlin
package partyos.engine.games.jeopardy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import partyos.engine.games.bluff.normalise

/** One clue. Its value comes from its position in the category (cheapest first), not from the file. */
@Serializable
data class JeopardyClue(val id: String, val clue: String, val answer: String)

@Serializable
data class JeopardyCategory(val id: String, val name: String, val clues: List<JeopardyClue>)

@Serializable
data class JeopardyFinal(val id: String, val category: String, val clue: String, val answer: String)

/**
 * Question-pack file format (version 2): a pool of categories to draw boards from, and Final Jeopardy clues. Each show
 * deals five categories per board at random, so nights differ.
 */
@Serializable
data class JeopardyPack(
    val packId: String,
    val title: String,
    val game: String,
    val version: Int,
    val categories: List<JeopardyCategory>,
    val finals: List<JeopardyFinal>,
) {
    companion object {
        const val MIN_CATEGORIES = 12
        const val MIN_FINALS = 4
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): JeopardyPack = validate(json.decodeFromString(serializer(), text))

        fun core(): JeopardyPack {
            val stream = requireNotNull(JeopardyPack::class.java.getResourceAsStream("/packs/jeopardy-core.json")) { "core pack missing" }
            return parse(stream.bufferedReader().use { it.readText() })
        }

        fun validate(p: JeopardyPack): JeopardyPack {
            require(p.game == "jeopardy") { "pack ${p.packId} is for ${p.game}, not jeopardy" }
            require(p.version == 2) { "pack ${p.packId} is version ${p.version}; Answer & Question needs version 2" }
            require(p.categories.size >= MIN_CATEGORIES) { "pack ${p.packId} needs at least $MIN_CATEGORIES categories" }
            require(p.finals.size >= MIN_FINALS) { "pack ${p.packId} needs at least $MIN_FINALS Final Jeopardy clues" }
            val ids = p.categories.flatMap { c -> c.clues.map { it.id } } + p.categories.map { it.id } + p.finals.map { it.id }
            require(ids.toSet().size == ids.size) { "pack ${p.packId} has duplicate ids" }
            for (c in p.categories) {
                require(c.name.isNotBlank()) { "category ${c.id} has no name" }
                require(c.clues.size == JeopardyRules.CLUES_PER_CATEGORY) { "category ${c.id} needs exactly ${JeopardyRules.CLUES_PER_CATEGORY} clues" }
                for (clue in c.clues) require(clue.clue.isNotBlank() && clue.answer.isNotBlank()) { "${clue.id}: blank clue or answer" }
                val answers = c.clues.map { normalise(it.answer) }
                require(answers.toSet().size == answers.size) { "category ${c.id} repeats an answer" }
            }
            for (f in p.finals) {
                require(f.category.isNotBlank() && f.clue.isNotBlank() && f.answer.isNotBlank()) { "${f.id}: blank field" }
            }
            return p
        }
    }
}
```

- [ ] **Step 5: Write the core pack**

Replace `tv/engine/src/main/resources/packs/jeopardy-core.json` with the following (the first five categories are the existing clues, kept word for word and now cheapest first; nine are new):

```json
{
  "packId": "jeopardy-core",
  "title": "Answer & Question core",
  "game": "jeopardy",
  "version": 2,
  "categories": [
    { "id": "food", "name": "Food & Drink", "clues": [
      { "id": "food-200", "clue": "This spread is made by mashing avocados with lime and salt.", "answer": "Guacamole" },
      { "id": "food-400", "clue": "This Japanese dish is vinegared rice topped with raw fish.", "answer": "Sushi" },
      { "id": "food-600", "clue": "This French cooking technique means to quickly cook food in a small amount of hot fat.", "answer": "Saute" },
      { "id": "food-800", "clue": "This spice, made from a crocus flower, is the most expensive by weight in the world.", "answer": "Saffron" },
      { "id": "food-1000", "clue": "This fermented Korean side dish is usually made from cabbage and chili paste.", "answer": "Kimchi" } ] },
    { "id": "sports", "name": "Sports", "clues": [
      { "id": "sports-200", "clue": "In basketball, this term describes a shot that goes in without touching the rim or backboard.", "answer": "Swish" },
      { "id": "sports-400", "clue": "This NBA legend won 6 championships with the Chicago Bulls and never lost in the Finals.", "answer": "Michael Jordan" },
      { "id": "sports-600", "clue": "In basketball, this is the term for grabbing the ball after a missed shot.", "answer": "Rebound" },
      { "id": "sports-800", "clue": "This Los Angeles Laker wore number 24 and was nicknamed the Black Mamba.", "answer": "Kobe Bryant" },
      { "id": "sports-1000", "clue": "This is the only player in NBA history to average a triple-double for a full season more than once.", "answer": "Russell Westbrook" } ] },
    { "id": "music", "name": "Music", "clues": [
      { "id": "music-200", "clue": "This King of Pop released the best-selling album of all time, Thriller.", "answer": "Michael Jackson" },
      { "id": "music-400", "clue": "This instrument has 88 black and white keys.", "answer": "Piano" },
      { "id": "music-600", "clue": "This British rock band's members included Freddie Mercury, Brian May and Roger Taylor.", "answer": "Queen" },
      { "id": "music-800", "clue": "This genre of music originated in Jamaica in the late 1960s and was popularized by Bob Marley.", "answer": "Reggae" },
      { "id": "music-1000", "clue": "This composer, deaf by the end of his life, wrote the Ninth Symphony.", "answer": "Beethoven" } ] },
    { "id": "gk", "name": "General Knowledge", "clues": [
      { "id": "gk-200", "clue": "This is the largest planet in our solar system.", "answer": "Jupiter" },
      { "id": "gk-400", "clue": "This is the longest river in the world.", "answer": "Nile" },
      { "id": "gk-600", "clue": "This is the two-letter chemical symbol for gold.", "answer": "Au" },
      { "id": "gk-800", "clue": "This country has the largest population in the world as of 2024.", "answer": "India" },
      { "id": "gk-1000", "clue": "This is the only mammal capable of true, sustained flight.", "answer": "Bat" } ] },
    { "id": "potpourri", "name": "Potpourri", "clues": [
      { "id": "pot-200", "clue": "This is the name of Mickey Mouse's pet dog.", "answer": "Pluto" },
      { "id": "pot-400", "clue": "This planet is known as the Red Planet.", "answer": "Mars" },
      { "id": "pot-600", "clue": "This board game involves buying properties and charging rent.", "answer": "Monopoly" },
      { "id": "pot-800", "clue": "This is the tallest mountain in the world, measured from sea level.", "answer": "Everest" },
      { "id": "pot-1000", "clue": "This is the only Ancient Wonder of the World still standing today.", "answer": "Pyramids" } ] },
    { "id": "movies", "name": "Movies", "clues": [
      { "id": "movies-1", "clue": "This 1997 film about a doomed ocean liner starred Leonardo DiCaprio and Kate Winslet.", "answer": "Titanic" },
      { "id": "movies-2", "clue": "In this Disney film, a lion cub named Simba must reclaim his kingdom.", "answer": "The Lion King" },
      { "id": "movies-3", "clue": "This director made Jaws, E.T. and Jurassic Park.", "answer": "Steven Spielberg" },
      { "id": "movies-4", "clue": "Tom Hanks plays a kind man who says \"Life is like a box of chocolates\" in this 1994 film.", "answer": "Forrest Gump" },
      { "id": "movies-5", "clue": "This 1972 Francis Ford Coppola crime film about the Corleone family won the Oscar for Best Picture.", "answer": "The Godfather" } ] },
    { "id": "science", "name": "Science", "clues": [
      { "id": "science-1", "clue": "H2O is the chemical formula for this liquid.", "answer": "Water" },
      { "id": "science-2", "clue": "Plants take in this gas from the air during photosynthesis.", "answer": "Carbon dioxide" },
      { "id": "science-3", "clue": "This is the lightest element, with atomic number 1.", "answer": "Hydrogen" },
      { "id": "science-4", "clue": "This part of a cell is nicknamed its powerhouse.", "answer": "Mitochondria" },
      { "id": "science-5", "clue": "This is the SI unit of force, named after the physicist famous for the laws of motion.", "answer": "Newton" } ] },
    { "id": "history", "name": "History", "clues": [
      { "id": "history-1", "clue": "This American president's face is on the U.S. penny.", "answer": "Abraham Lincoln" },
      { "id": "history-2", "clue": "The Colosseum stands in this European capital.", "answer": "Rome" },
      { "id": "history-3", "clue": "This wall, torn down in 1989, split this German city in two.", "answer": "Berlin" },
      { "id": "history-4", "clue": "This queen ruled the United Kingdom for 63 years, from 1837 to 1901.", "answer": "Victoria" },
      { "id": "history-5", "clue": "This 1215 charter, sealed by King John, limited the power of English kings.", "answer": "Magna Carta" } ] },
    { "id": "animals", "name": "Animals", "clues": [
      { "id": "animals-1", "clue": "This is the largest land animal on Earth.", "answer": "Elephant" },
      { "id": "animals-2", "clue": "This black-and-white bear from China eats mostly bamboo.", "answer": "Panda" },
      { "id": "animals-3", "clue": "This big cat is the fastest land animal, reaching about 60 miles per hour.", "answer": "Cheetah" },
      { "id": "animals-4", "clue": "This flightless Australian bird is the second-tallest bird in the world.", "answer": "Emu" },
      { "id": "animals-5", "clue": "Despite its name, this gentle giant is a fish, not a mammal, and is the largest fish in the ocean.", "answer": "Whale shark" } ] },
    { "id": "space", "name": "Space", "clues": [
      { "id": "space-1", "clue": "This is the star at the center of our solar system.", "answer": "The Sun" },
      { "id": "space-2", "clue": "This planet, famous for its rings, is sixth from the Sun.", "answer": "Saturn" },
      { "id": "space-3", "clue": "In 1969, Neil Armstrong became the first person to walk on this.", "answer": "The Moon" },
      { "id": "space-4", "clue": "This is the name of the galaxy that contains our solar system.", "answer": "Milky Way" },
      { "id": "space-5", "clue": "This space telescope, launched in 1990, is named after an American astronomer.", "answer": "Hubble" } ] },
    { "id": "books", "name": "Books", "clues": [
      { "id": "books-1", "clue": "This boy wizard with a lightning-bolt scar attends Hogwarts.", "answer": "Harry Potter" },
      { "id": "books-2", "clue": "This English playwright wrote Romeo and Juliet.", "answer": "Shakespeare" },
      { "id": "books-3", "clue": "Dr. Seuss created this green creature who tries to steal Christmas.", "answer": "The Grinch" },
      { "id": "books-4", "clue": "George Orwell's dystopian novel is titled with this year.", "answer": "1984" },
      { "id": "books-5", "clue": "This Herman Melville novel begins with the line \"Call me Ishmael.\"", "answer": "Moby Dick" } ] },
    { "id": "geo", "name": "Geography", "clues": [
      { "id": "geo-1", "clue": "The Eiffel Tower stands in this European capital city.", "answer": "Paris" },
      { "id": "geo-2", "clue": "This is the largest country in the world by area.", "answer": "Russia" },
      { "id": "geo-3", "clue": "This African desert is the largest hot desert in the world.", "answer": "Sahara" },
      { "id": "geo-4", "clue": "This city is the capital of Australia, not Sydney or Melbourne.", "answer": "Canberra" },
      { "id": "geo-5", "clue": "Counting its overseas territories, this country has the most time zones in the world.", "answer": "France" } ] },
    { "id": "tech", "name": "Tech & Games", "clues": [
      { "id": "tech-1", "clue": "Mario's taller brother, who wears green, has this name.", "answer": "Luigi" },
      { "id": "tech-2", "clue": "This company makes the iPhone and the Mac.", "answer": "Apple" },
      { "id": "tech-3", "clue": "This search engine's name became a verb meaning to look something up online.", "answer": "Google" },
      { "id": "tech-4", "clue": "In this Mojang game, players mine blocks and craft items in a world made of cubes.", "answer": "Minecraft" },
      { "id": "tech-5", "clue": "This programming language, popular in data science, is named after the comedy group Monty Python.", "answer": "Python" } ] },
    { "id": "body", "name": "The Human Body", "clues": [
      { "id": "body-1", "clue": "This organ pumps blood around your body.", "answer": "Heart" },
      { "id": "body-2", "clue": "This is the largest organ of the human body.", "answer": "Skin" },
      { "id": "body-3", "clue": "An adult human typically has this many bones.", "answer": "206" },
      { "id": "body-4", "clue": "Your skin makes this vitamin in sunlight, and it helps you absorb calcium.", "answer": "Vitamin D" },
      { "id": "body-5", "clue": "Its name is Latin for \"stirrup\", and it is the smallest bone in the human body.", "answer": "Stapes" } ] }
  ],
  "finals": [
    { "id": "final-1", "category": "World Capitals", "clue": "This capital on the Danube was formed in 1873 by merging Buda, Obuda and Pest.", "answer": "Budapest" },
    { "id": "final-2", "category": "U.S. Presidents", "clue": "He is the only U.S. president elected to four terms.", "answer": "Franklin Roosevelt" },
    { "id": "final-3", "category": "Inventions", "clue": "In 1876, Alexander Graham Bell was granted a patent for this device.", "answer": "Telephone" },
    { "id": "final-4", "category": "Elements", "clue": "This is the only metal that is liquid at room temperature.", "answer": "Mercury" },
    { "id": "final-5", "category": "Rivers", "clue": "This river flows through the Iraqi capital, Baghdad.", "answer": "Tigris" },
    { "id": "final-6", "category": "Art History", "clue": "This Italian Renaissance artist painted the ceiling of the Sistine Chapel.", "answer": "Michelangelo" },
    { "id": "final-7", "category": "Famous Authors", "clue": "She wrote Pride and Prejudice.", "answer": "Jane Austen" },
    { "id": "final-8", "category": "The Middle East", "clue": "The Dead Sea lies between Israel and this Arab kingdom to its east.", "answer": "Jordan" }
  ]
}
```

- [ ] **Step 6: Rewrite the TV payload**

Replace `tv/engine/src/main/kotlin/partyos/engine/JeopardyViews.kt`:

```kotlin
package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One square on the TV board. A hidden Daily Double looks like any other square. */
@Serializable
data class JeopardyCellTv(val id: String, val col: Int, val row: Int, val value: Int, val used: Boolean)

@Serializable
data class JeopardyDelta(val id: PlayerId, val name: String, val points: Int)

/** [text] is the whole drink line, already worded for water. */
@Serializable
data class JeopardyDrink(val id: PlayerId, val name: String, val sips: Int, val text: String)

/** One player's Final Jeopardy result, revealed lowest score first. [total] is their score after this step. */
@Serializable
data class JeopardyFinalStep(val id: PlayerId, val name: String, val answer: String?, val wager: Int, val right: Boolean, val delta: Int, val total: Int)

@Serializable
data class JeopardyFinalTv(
    val category: String,
    /** Shown once the wagers are in. */
    val clue: String? = null,
    /** Shown from the first reveal step. */
    val answer: String? = null,
    val wagers: Int = 0,
    val expected: Int = 0,
    val steps: List<JeopardyFinalStep> = emptyList(),
)

@Serializable @SerialName("jeopardy")
data class JeopardyTv(
    /** intro | pick | wager | clue | buzz | answer | reveal | break | final_category | final_wager | final_answer | final_reveal | podium */
    val phase: String,
    /** 1 or 2 for a board, 3 for Final Jeopardy. */
    val round: Int,
    /** 1 (Short) or 2 (Full). */
    val boards: Int,
    val categories: List<String> = emptyList(),
    val cells: List<JeopardyCellTv> = emptyList(),
    val controller: PlayerId? = null,
    val category: String? = null,
    val value: Int? = null,
    val clue: String? = null,
    val dailyDouble: Boolean = false,
    /** A Daily Double's wager, shown once the picker has committed to it. */
    val wager: Int? = null,
    val buzzOpen: Boolean = false,
    val floor: PlayerId? = null,
    val locked: List<PlayerId> = emptyList(),
    val tried: List<PlayerId> = emptyList(),
    val answer: String? = null,
    val right: Boolean? = null,
    val deltas: List<JeopardyDelta> = emptyList(),
    val drinks: List<JeopardyDrink> = emptyList(),
    val final: JeopardyFinalTv? = null,
) : TvGame
```

- [ ] **Step 7: Add the phone screens**

In `tv/engine/src/main/kotlin/partyos/engine/Views.kt`, directly before the KDoc that starts `     * Home Turf: your token,` (the `/**` line above it), insert:

```kotlin
    /**
     * Answer & Question's board: [categories] across, [cells] by column and row. The phone picks a cell locally and
     * confirms it with `{ kind: "pick", cell }`. [canPick] is true for the player holding the board and for the
     * captain, who then sees [pickFor], the name of the player they are picking for.
     */
    @Serializable @SerialName("board")
    data class Board(
        val prompt: String,
        val categories: List<String>,
        val cells: List<BoardCell>,
        val canPick: Boolean,
        val pickFor: String? = null,
        val note: String? = null,
    ) : Screen

    /**
     * The BUZZ button. [state]: `reading` (clue still being read), `open`, `locked` (rang in too early, [lockedMs] left),
     * `beaten` (someone else has the floor, named in [detail]), `tried` (you already missed this clue) or `out`
     * (you joined after it started). [live] is true while the buzz window is open, so a `locked` phone can open itself
     * when the lockout runs out. A buzz sends `{ kind: "buzz" }`.
     */
    @Serializable @SerialName("buzzer")
    data class Buzzer(
        val state: String,
        val category: String,
        val value: Int,
        val detail: String? = null,
        val lockedMs: Int = 0,
        val live: Boolean = false,
    ) : Screen

```

Then, after the `SecretInput` data class near the bottom of the file, add:

```kotlin
/** One square on the phone's board. */
@Serializable
data class BoardCell(val id: String, val col: Int, val row: Int, val value: Int, val used: Boolean)
```

- [ ] **Step 8: Let games see the captain, and add the `show` option**

In `tv/engine/src/main/kotlin/partyos/engine/Game.kt`, change the end of the `GameContext` constructor from

```kotlin
    val remainingMs: Long? = null,
) {
```
to
```kotlin
    val remainingMs: Long? = null,
    /** The player holding the crown (who may run the show from their phone), or null when phone control is off. */
    val captain: PlayerId? = null,
) {
```

In `tv/engine/src/main/kotlin/partyos/engine/PartyEngine.kt`, in `private fun <S : Any> ctx(g: ActiveGame<S>) = GameContext(` change `remainingMs = g.remaining(clock.now()),\n    )` to `remainingMs = g.remaining(clock.now()),\n        captain = captain(),\n    )`, and in `optionRange` add, after the `"vp" -> 8..10` line:

```kotlin
        // Answer & Question: 0 Short (one board then Final), 1 Full (two boards then Final).
        "show" -> 0..1
```

- [ ] **Step 9: Rewrite the module**

Replace `tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/Jeopardy.kt`. Tasks 3–5 add arms to `onAction`, `onDeadline` and `waitingOn` by inserting above their final `else` lines, so keep those lines exactly.

```kotlin
package partyos.engine.games.jeopardy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.BoardCell
import partyos.engine.Effect
import partyos.engine.GameContext
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.JeopardyCellTv
import partyos.engine.JeopardyDelta
import partyos.engine.JeopardyDrink
import partyos.engine.JeopardyFinalStep
import partyos.engine.JeopardyFinalTv
import partyos.engine.JeopardyTv
import partyos.engine.LateJoin
import partyos.engine.PlayerId
import partyos.engine.Reject
import partyos.engine.ScoreRow
import partyos.engine.Screen
import partyos.engine.Step
import partyos.engine.TutorialCard
import partyos.engine.games.trivia.AnswerMatch
import partyos.engine.ofWater
import kotlin.random.Random

@Serializable
data class JeopardyState(
    val phase: String,
    /** 1 or 2 for a board, 3 for Final Jeopardy. */
    val round: Int,
    /** 1 = Short, 2 = Full. */
    val boards: Int,
    /** Category ids of the board on screen, in column order. */
    val categories: List<String>,
    /** Board 2's category ids, until it is played. */
    val nextCategories: List<String>,
    val finalId: String,
    /** Clue ids hiding a Daily Double on the current board. */
    val dailyDoubles: Set<String> = emptySet(),
    val used: Set<String> = emptySet(),
    /** Player id holding the board. */
    val controller: String? = null,
    /** Clue id in play. */
    val active: String? = null,
    val dailyDouble: Boolean = false,
    val wager: Int = 0,
    /** Who may ring in on the clue in play: everyone in the room when it was opened. */
    val eligible: List<PlayerId> = emptyList(),
    /** player id → engine time until which they are locked out for ringing in early. */
    val lockedUntil: Map<String, Long> = emptyMap(),
    /** Player id who has the floor (rang in first, or the Daily Double picker). */
    val floor: String? = null,
    val tried: Set<String> = emptySet(),
    /** How the clue in play ended, for the reveal. */
    val right: Boolean? = null,
    /** player id → points won or lost on the clue in play. */
    val deltas: Map<String, Int> = emptyMap(),
    /** player id → sips to drink for the clue in play (or Final so far). */
    val sips: Map<String, Int> = emptyMap(),
    val finalPlayers: List<PlayerId> = emptyList(),
    /** Players with a score above zero when Final started: the only ones who bet. */
    val finalWagerers: List<PlayerId> = emptyList(),
    val finalWagers: Map<String, Int> = emptyMap(),
    val finalAnswers: Map<String, String> = emptyMap(),
    /** Player ids from the lowest score up. */
    val finalOrder: List<String> = emptyList(),
    val finalStep: Int = 0,
    val finalRight: Set<String> = emptySet(),
    /** player id → points won or lost in Final so far. */
    val finalResults: Map<String, Int> = emptyMap(),
)

class Jeopardy(private val pack: JeopardyPack = JeopardyPack.core()) : GameModule<JeopardyState> {
    override val info = GameInfo(
        id = "jeopardy",
        title = "Answer & Question",
        tagline = "Ring in. Risk it. Answer as a question.",
        minPlayers = 2,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Ring in", "When your phone lights up, hit BUZZ first. Too early and you're locked out for a second."),
            TutorialCard("Answer as a question", "\"What is guacamole?\" or just \"guacamole\": both count. Right wins the clue's value; wrong loses it."),
            TutorialCard("Wager big", "Daily Doubles hide on the board: pick one and bet your own score. Final Jeopardy lets everyone bet it all."),
        ),
        lateJoin = LateJoin.ANYTIME,
    )
    override val stateSerializer = JeopardyState.serializer()

    private val categoryById = pack.categories.associateBy { it.id }
    private val finalById = pack.finals.associateBy { it.id }
    private val clueById = pack.categories.flatMap { it.clues }.associateBy { it.id }
    private val categoryNameOfClue = pack.categories.flatMap { c -> c.clues.map { it.id to c.name } }.toMap()

    // ---- setup ----------------------------------------------------------------------------

    override fun start(ctx: GameContext): Step<JeopardyState> {
        val boards = if ((ctx.settings["show"] ?: 0) == 1) 2 else 1
        val need = JeopardyRules.CATEGORIES_PER_BOARD * boards
        val fresh = ctx.fresh(pack.categories) { it.id }
        val ordered = fresh.shuffled(ctx.random) + (pack.categories - fresh.toSet()).shuffled(ctx.random)
        val picked = ordered.take(need).map { it.id }
        val final = ctx.fresh(pack.finals) { it.id }.ifEmpty { pack.finals }.random(ctx.random)
        val first = picked.take(JeopardyRules.CATEGORIES_PER_BOARD)
        val players = ctx.players.map { it.id.v }
        val s = JeopardyState(
            phase = INTRO, round = 1, boards = boards,
            categories = first, nextCategories = picked.drop(JeopardyRules.CATEGORIES_PER_BOARD), finalId = final.id,
            dailyDoubles = dealDoubles(1, first, ctx.random),
            controller = JeopardyRules.firstPicker(1, players, emptyMap(), ctx.captain?.v),
        )
        val used: List<Effect> = picked.map { Effect.UseContent(it) } + Effect.UseContent(final.id)
        return Step(s, used + Effect.Phase(INTRO_MS))
    }

    private fun dealDoubles(round: Int, categoryIds: List<String>, random: Random): Set<String> =
        JeopardyRules.dailyDoubles(categoryIds.size, JeopardyRules.dailyDoubleCount(round), random)
            .map { (col, row) -> categoryById.getValue(categoryIds[col]).clues[row].id }.toSet()

    private fun cells(s: JeopardyState): List<Cell> = s.categories.flatMapIndexed { col, id ->
        categoryById.getValue(id).clues.mapIndexed { row, clue -> Cell(clue.id, col, row, JeopardyRules.valueOf(s.round, row)) }
    }

    private fun cellOf(s: JeopardyState, id: String): Cell = cells(s).first { it.id == id }

    // ---- the board ------------------------------------------------------------------------

    private fun toPick(s: JeopardyState, ctx: GameContext): Step<JeopardyState> =
        Step(s.copy(phase = PICK, active = null, floor = null, dailyDouble = false), listOf(Effect.Phase(ctx.timer(PICK_MS))))

    private fun pick(s: JeopardyState, cellId: String, ctx: GameContext): Step<JeopardyState> {
        val cell = cells(s).firstOrNull { it.id == cellId } ?: throw Reject("BAD_CELL")
        if (cell.id in s.used) throw Reject("ALREADY_USED")
        return openClue(s, cell, ctx)
    }

    private fun openClue(s: JeopardyState, cell: Cell, ctx: GameContext): Step<JeopardyState> {
        val dd = cell.id in s.dailyDoubles
        val base = s.copy(
            active = cell.id, dailyDouble = dd, wager = 0, eligible = ctx.players.map { it.id },
            lockedUntil = emptyMap(), floor = null, tried = emptySet(), right = null, deltas = emptyMap(), sips = emptyMap(),
        )
        return if (dd) Step(base.copy(phase = WAGER), listOf(Effect.Phase(ctx.timer(DD_WAGER_MS))))
        else Step(base.copy(phase = CLUE), listOf(Effect.Phase(JeopardyRules.readMs(clueById.getValue(cell.id).clue))))
    }

    // ---- actions and deadlines ------------------------------------------------------------

    override fun onHost(s: JeopardyState, action: String, ctx: GameContext): Step<JeopardyState> {
        if (s.phase != PICK || !action.startsWith("pick:")) throw Reject("NOT_NOW")
        return pick(s, action.removePrefix("pick:"), ctx)
    }

    override fun onAction(s: JeopardyState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<JeopardyState> {
        val kind = payload["kind"]?.jsonPrimitive?.content
        return when {
            s.phase == PICK && kind == "pick" -> {
                if (who.v != s.controller && who != ctx.captain) throw Reject("NOT_YOUR_PICK")
                pick(s, payload["cell"]?.jsonPrimitive?.content ?: "", ctx)
            }
            else -> throw Reject("NOT_NOW")
        }
    }

    override fun onDeadline(s: JeopardyState, ctx: GameContext): Step<JeopardyState> = when (s.phase) {
        INTRO -> toPick(s, ctx)
        PICK -> openClue(s, JeopardyRules.autoPick(cells(s).filter { it.id !in s.used }, ctx.random), ctx)
        else -> Step(s, listOf(Effect.Finish))
    }

    override fun waitingOn(s: JeopardyState): Set<PlayerId>? = when (s.phase) {
        PICK -> s.controller?.let { setOf(PlayerId(it)) }
        else -> null
    }

    override fun restorable(s: JeopardyState) = s.categories.all { it in categoryById } && s.finalId in finalById

    // ---- views ----------------------------------------------------------------------------

    private fun nameOf(ctx: GameContext, id: String?): String = id?.let { ctx.player(PlayerId(it))?.name } ?: "Someone"

    private fun rows(ctx: GameContext) =
        ctx.players.map { ScoreRow(it.id, it.name, it.avatar, ctx.scores[it.id] ?: 0) }.sortedByDescending { it.score }

    private fun drinks(s: JeopardyState, ctx: GameContext): List<JeopardyDrink> {
        if ((ctx.settings["drinks"] ?: 1) == 0) return emptyList()
        return s.sips.mapNotNull { (id, sips) ->
            val p = ctx.player(PlayerId(id)) ?: return@mapNotNull null
            JeopardyDrink(p.id, p.name, sips, "Drink $sips ${if (sips == 1) "sip" else "sips"}${ofWater(p.water)}")
        }
    }

    override fun tvView(s: JeopardyState, ctx: GameContext): JeopardyTv {
        val onBoard = s.phase !in FINAL_PHASES && s.phase != PODIUM
        val clue = s.active?.let(clueById::get)
        val reveal = s.phase == REVEAL
        val cell = s.active?.let { cellOf(s, it) }
        return JeopardyTv(
            phase = s.phase,
            round = s.round,
            boards = s.boards,
            categories = if (onBoard) s.categories.map { categoryById.getValue(it).name } else emptyList(),
            cells = if (onBoard) cells(s).map { JeopardyCellTv(it.id, it.col, it.row, it.value, it.id in s.used) } else emptyList(),
            controller = s.controller?.let(::PlayerId),
            category = s.active?.let { categoryNameOfClue[it] },
            value = cell?.value,
            clue = if (s.phase in CLUE_VISIBLE) clue?.clue else null,
            dailyDouble = s.dailyDouble && s.active != null,
            wager = if (s.dailyDouble && s.phase in COMMITTED) s.wager else null,
            buzzOpen = s.phase == BUZZ,
            floor = s.floor?.let(::PlayerId),
            locked = s.lockedUntil.filterValues { it > ctx.now }.keys.map(::PlayerId),
            tried = s.tried.map(::PlayerId),
            answer = if (reveal) clue?.answer else null,
            right = if (reveal) s.right else null,
            deltas = if (reveal) s.deltas.map { (id, pts) -> JeopardyDelta(PlayerId(id), nameOf(ctx, id), pts) }.sortedByDescending { it.points } else emptyList(),
            drinks = if (reveal || s.phase == FINAL_REVEAL) drinks(s, ctx) else emptyList(),
            final = if (s.phase in FINAL_PHASES) finalTv(s, ctx) else null,
        )
    }

    private fun finalTv(s: JeopardyState, ctx: GameContext): JeopardyFinalTv {
        val f = finalById.getValue(s.finalId)
        val clueShown = s.phase == FINAL_ANSWER || s.phase == FINAL_REVEAL
        val positive = s.finalWagerers.size
        return JeopardyFinalTv(
            category = f.category,
            clue = if (clueShown) f.clue else null,
            answer = if (s.phase == FINAL_REVEAL) f.answer else null,
            wagers = if (s.phase == FINAL_ANSWER || s.phase == FINAL_WAGER) s.finalWagers.size else 0,
            expected = if (s.phase == FINAL_ANSWER) s.finalPlayers.size else positive,
            steps = if (s.phase == FINAL_REVEAL) {
                s.finalOrder.take(s.finalStep + 1).map { id ->
                    JeopardyFinalStep(
                        PlayerId(id), nameOf(ctx, id), s.finalAnswers[id], s.finalWagers[id] ?: 0,
                        id in s.finalRight, s.finalResults[id] ?: 0, ctx.scores[PlayerId(id)] ?: 0,
                    )
                }
            } else emptyList(),
        )
    }

    override fun playerView(s: JeopardyState, who: PlayerId, ctx: GameContext): Screen {
        val me = who.v
        val controllerName = nameOf(ctx, s.controller)
        return when (s.phase) {
            PODIUM -> Screen.Scores("Final scores", rows(ctx))
            INTRO -> Screen.Waiting("Here we go", "Watch the categories come up on the TV")
            BREAK -> Screen.Waiting("Double Jeopardy!", "Every clue is worth double")
            PICK -> boardScreen(s, who, ctx)
            WAGER ->
                if (me == s.controller) Screen.NumberEntry("Daily Double! How much do you wager?", "$", null, "wager")
                else Screen.Waiting("Daily Double!", "$controllerName is placing a wager")
            CLUE, BUZZ, ANSWER -> clueScreen(s, who, ctx)
            REVEAL -> revealScreen(s, who, ctx)
            FINAL_CATEGORY -> Screen.Waiting("Final Jeopardy", finalById.getValue(s.finalId).category)
            FINAL_WAGER ->
                if (who in s.finalWagerers) Screen.NumberEntry("Final Jeopardy: how much do you wager?", "$", s.finalWagers[me]?.toDouble(), "wager")
                else Screen.Waiting("Sit this bet out", "You need a positive score to wager. You can still answer.")
            FINAL_ANSWER ->
                if (who in s.finalPlayers) Screen.TextEntry(finalById.getValue(s.finalId).clue, MAX_ANSWER, s.finalAnswers[me], "answer", "Answer as a question, or just say it")
                else Screen.Waiting("Final Jeopardy", "You'll play the next show")
            FINAL_REVEAL -> Screen.Waiting("Eyes on the TV", "Final answers are coming up, lowest score first")
            else -> Screen.Waiting("Eyes on the TV", null)
        }
    }

    private fun boardScreen(s: JeopardyState, who: PlayerId, ctx: GameContext): Screen {
        val isController = who.v == s.controller
        val isCaptain = ctx.captain == who
        val controllerName = nameOf(ctx, s.controller)
        return Screen.Board(
            prompt = when {
                isController -> "Pick a clue"
                isCaptain -> "Pick for $controllerName"
                else -> "$controllerName is picking"
            },
            categories = s.categories.map { categoryById.getValue(it).name },
            cells = cells(s).map { BoardCell(it.id, it.col, it.row, it.value, it.id in s.used) },
            canPick = isController || isCaptain,
            pickFor = if (!isController && isCaptain) controllerName else null,
            note = if (isController || isCaptain) null else "Watch the TV",
        )
    }

    private fun clueScreen(s: JeopardyState, who: PlayerId, ctx: GameContext): Screen {
        val me = who.v
        val active = requireNotNull(s.active)
        val clue = clueById.getValue(active)
        val category = categoryNameOfClue.getValue(active)
        val value = cellOf(s, active).value
        if (s.dailyDouble) {
            return when {
                s.phase == ANSWER && me == s.floor -> Screen.TextEntry(clue.clue, MAX_ANSWER, null, "answer", "Daily Double! Answer as a question, or just say it")
                me == s.controller -> Screen.Waiting("Daily Double!", "It's all you. Wager: ${s.wager}")
                else -> Screen.Waiting("Daily Double!", "${nameOf(ctx, s.controller)} is on the clue")
            }
        }
        if (s.phase == ANSWER && me == s.floor) return Screen.TextEntry(clue.clue, MAX_ANSWER, null, "answer", "Answer as a question, or just say it")
        fun buzzer(state: String, detail: String? = null, lockedMs: Int = 0) = Screen.Buzzer(state, category, value, detail, lockedMs, s.phase == BUZZ)
        if (who !in s.eligible) return buzzer("out", "You're in on the next clue")
        val locked = ((s.lockedUntil[me] ?: 0L) - ctx.now).coerceAtLeast(0).toInt()
        return when {
            s.phase == ANSWER -> buzzer("beaten", "${nameOf(ctx, s.floor)} rang in first")
            me in s.tried -> buzzer("tried", "You missed this one")
            locked > 0 -> buzzer("locked", "Too early! Hold on...", locked)
            s.phase == BUZZ -> buzzer("open", "Ring in!")
            else -> buzzer("reading", "Wait for the clue to finish")
        }
    }

    private fun revealScreen(s: JeopardyState, who: PlayerId, ctx: GameContext): Screen {
        val me = who.v
        val delta = s.deltas[me] ?: 0
        val drink = drinks(s, ctx).firstOrNull { it.id == who }?.text
        val answer = clueById.getValue(requireNotNull(s.active)).answer
        return when {
            delta > 0 -> Screen.Waiting("You got it! +$delta", answer, tone = "win")
            delta < 0 -> Screen.Waiting("Ouch! $delta", listOfNotNull(answer, drink).joinToString(" · "), tone = "lose")
            else -> Screen.Waiting("Eyes on the TV", answer)
        }
    }

    private fun cleanText(raw: String): String? {
        val t = raw.filterNot { Character.isISOControl(it) }.trim().replace(Regex("\\s+"), " ")
        return t.takeIf { it.isNotEmpty() && it.length <= MAX_ANSWER }
    }

    /**
     * Players answer in the show's form ("What is guacamole?"), which the shared matcher would read as extra wrong
     * words, so the leading "what is / who's / where are..." is dropped first. Only this game does it: Write It Down
     * and Brain Drain take bare answers.
     */
    private fun asAnswer(raw: String): String = raw.trim().replace(QUESTION_LEAD, "").trimEnd('?', ' ')

    private fun matches(text: String?, answer: String) = text != null && AnswerMatch.accepts(asAnswer(text), answer)

    companion object {
        const val INTRO = "intro"
        const val PICK = "pick"
        const val WAGER = "wager"
        const val CLUE = "clue"
        const val BUZZ = "buzz"
        const val ANSWER = "answer"
        const val REVEAL = "reveal"
        const val BREAK = "break"
        const val FINAL_CATEGORY = "final_category"
        const val FINAL_WAGER = "final_wager"
        const val FINAL_ANSWER = "final_answer"
        const val FINAL_REVEAL = "final_reveal"
        const val PODIUM = "podium"
        private val FINAL_PHASES = setOf(FINAL_CATEGORY, FINAL_WAGER, FINAL_ANSWER, FINAL_REVEAL)
        /** The clue text is on screen from the read onward. */
        private val CLUE_VISIBLE = setOf(CLUE, BUZZ, ANSWER, REVEAL)
        /** A Daily Double's wager is shown once it has been placed. */
        private val COMMITTED = setOf(CLUE, ANSWER, REVEAL)
        private val QUESTION_LEAD = Regex("^(?:what|who|where|when)(?:['’]?s|\\s+(?:is|are|was|were))\\s+", RegexOption.IGNORE_CASE)
        const val MAX_ANSWER = 60
        const val PICK_MS = 20_000L
        const val BUZZ_MS = 10_000L
        const val REBUZZ_MS = 8_000L
        const val ANSWER_MS = 15_000L
        const val DD_WAGER_MS = 20_000L
        const val DD_ANSWER_MS = 30_000L
        const val REVEAL_MS = 5_000L
        const val INTRO_MS = 8_000L
        const val BREAK_MS = 8_000L
        const val FINAL_CAT_MS = 6_000L
        const val FINAL_WAGER_MS = 30_000L
        const val FINAL_ANSWER_MS = 30_000L
        const val FINAL_STEP_MS = 5_000L
        const val PODIUM_MS = 15_000L
    }
}
```

`intOrNull`, `AnswerMatch` and `matches` are imported/declared here and used from Task 3 onward; an unused private function is only a compiler warning.

- [ ] **Step 10: Run the tests to verify they pass**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :engine:test --console=plain` and `./gradlew :devserver:compileKotlin :server:compileTestKotlin --console=plain`
Expected: PASS for the whole engine suite (the new `JeopardyPackTest` 6 tests and `JeopardyTest` 12 tests among them) and both compile tasks succeed. If `eachNightGetsDifferentCategoriesWhileThePoolLasts` fails, check that `EndGame` really records the used-content ids (`Effect.UseContent` is applied at `start`, so both nights' ids are recorded) before touching the test.

- [ ] **Step 11: Commit**

```bash
git add tv/engine/src/main tv/engine/src/test
git commit -m "feat(jeopardy): pack v2 with 14 categories and 8 finals, board control and the module through the pick"
```

### Task 3: Ringing in, answering and board control

**Files:**
- Modify: `tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/Jeopardy.kt` (arms in `onAction`, `onDeadline`, `waitingOn`; new `judge`, `reveal`, `afterReveal`)
- Modify: `tv/engine/src/test/kotlin/partyos/engine/games/jeopardy/JeopardyTest.kt`

**Interfaces:**
- Consumes: everything from Task 2 (`openClue`, `toPick`, `cellOf`, `matches`, `asAnswer`, `cleanText`, the phase constants and timing constants).
- Produces: actions `buzz` (no payload) and `answer` (`text`); rejection codes `NOT_NOW`, `LOCKED_OUT`, `BAD_TEXT`; the deadline chain `clue → buzz → answer → (buzz again | reveal) → pick`; `private fun judge(s, text: String?, ctx)`, `private fun reveal(s, effects)`, `private fun afterReveal(s, ctx)` (Task 5 replaces `afterReveal`).

- [ ] **Step 1: Write the failing tests**

Add these imports to `JeopardyTest.kt`: `partyos.engine.Avatar`, `partyos.engine.JoinResult`, `partyos.engine.Role`. Add these helpers and tests inside the class, before its final `}`:

```kotlin
    private fun score(id: PlayerId) = e.tvState().scores.single { it.id == id }.score
    private fun ansOf(cellId: String): String { val (cat, row) = cellId.removePrefix("c").split("-"); return "Ans${cat}x$row" }

    /** Opens a cell on the cheapest row (which never hides a Daily Double) and reads it, ending in the buzz phase. */
    private fun openPlain(col: Int = 0): String {
        toPick()
        val id = tv.cells.first { it.row == 0 && it.col == col && !it.used }.id
        assertEquals(ActionResult.Ack, pickCell(id))
        skip() // the clue has been read
        assertEquals("buzz", tv.phase)
        return id
    }

    @Test fun theClueIsReadBeforePhonesCanRingIn() {
        val ids = start(3)
        toPick()
        pickCell(tv.cells.first { it.row == 0 }.id)
        assertEquals("clue", tv.phase)
        val reading = assertIs<Screen.Buzzer>(screen(ids[1]))
        assertEquals("reading", reading.state)
        assertFalse(reading.live)
        skip()
        assertTrue(tv.buzzOpen)
        val open = assertIs<Screen.Buzzer>(screen(ids[1]))
        assertEquals("open", open.state)
        assertTrue(open.live)
    }

    @Test fun theFirstBuzzGetsTheFloorAndTheSecondIsRefused() {
        val ids = start(3)
        openPlain()
        assertEquals(ActionResult.Ack, act(ids[1], "buzz"))
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(ids[2], "buzz"))
        assertEquals("answer", tv.phase)
        assertEquals(ids[1], tv.floor)
        assertIs<Screen.TextEntry>(screen(ids[1])).also { assertEquals("answer", it.kind) }
        val beaten = assertIs<Screen.Buzzer>(screen(ids[2]))
        assertEquals("beaten", beaten.state)
        assertTrue("P2" in beaten.detail.orEmpty(), beaten.detail)
    }

    @Test fun ringingInBeforeTheClueIsReadLocksYouOutForASecond() {
        val ids = start(3)
        toPick()
        pickCell(tv.cells.first { it.row == 0 }.id)
        assertEquals(ActionResult.Ack, act(ids[1], "buzz")) // far too early
        val locked = assertIs<Screen.Buzzer>(screen(ids[1]))
        assertEquals("locked", locked.state)
        assertEquals(1000, locked.lockedMs)
        skip() // the clue has been read; the lockout is still running
        assertEquals(ActionResult.Rejected("LOCKED_OUT"), act(ids[1], "buzz"))
        clock.advance(1001)
        assertEquals(ActionResult.Ack, act(ids[1], "buzz"))
        assertEquals(ids[1], tv.floor)
    }

    @Test fun aRightAnswerWinsTheValueAndTheBoard() {
        val ids = start(3)
        val id = openPlain(col = 1)
        act(ids[2], "buzz")
        assertEquals(ActionResult.Ack, act(ids[2], "answer", "text" to ansOf(id)))
        assertEquals("reveal", tv.phase)
        assertEquals(true, tv.right)
        assertEquals(ansOf(id), tv.answer)
        assertEquals(200, score(ids[2]))
        skip()
        assertEquals("pick", tv.phase)
        assertEquals(ids[2], tv.controller)
        assertTrue(assertIs<Screen.Board>(screen(ids[2])).canPick)
    }

    @Test fun theCaptainCanPickForWhoeverHoldsTheBoard() {
        val ids = start(3)
        val id = openPlain()
        act(ids[2], "buzz"); act(ids[2], "answer", "text" to ansOf(id))
        skip()
        val captainsView = assertIs<Screen.Board>(screen(ids[0]))
        assertTrue(captainsView.canPick)
        assertEquals("P3", captainsView.pickFor)
        assertEquals(ActionResult.Rejected("NOT_YOUR_PICK"), act(ids[1], "pick", "cell" to tv.cells.first { !it.used }.id))
        assertEquals(ActionResult.Ack, act(ids[0], "pick", "cell" to tv.cells.first { !it.used }.id))
        assertEquals("clue", tv.phase)
    }

    @Test fun answersGivenAsAQuestionCountLikeOnTheShow() {
        for (lead in listOf("What is", "whats", "Who is", "WHERE ARE", "what was")) {
            val ids = start(2)
            val id = openPlain()
            act(ids[1], "buzz")
            act(ids[1], "answer", "text" to "$lead ${ansOf(id)}?")
            assertEquals(true, tv.right, "'$lead' should count")
            assertEquals(200, score(ids[1]), lead)
        }
    }

    @Test fun aWrongAnswerCostsTheValueAndTheClueReopensForTheOthers() {
        val ids = start(3)
        val id = openPlain()
        act(ids[1], "buzz")
        assertEquals(ActionResult.Ack, act(ids[1], "answer", "text" to "nope"))
        assertEquals(-200, score(ids[1])) // scores can go negative
        assertEquals("buzz", tv.phase)
        assertEquals(listOf(ids[1]), tv.tried)
        assertEquals("tried", assertIs<Screen.Buzzer>(screen(ids[1])).state)
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(ids[1], "buzz"))
        act(ids[2], "buzz")
        act(ids[2], "answer", "text" to ansOf(id))
        assertEquals(200, score(ids[2]))
        assertEquals(-200, score(ids[1]))
        assertEquals(ids[2], tv.controller)
    }

    @Test fun ifEveryoneAnswersWrongTheClueEndsAndTheBoardStaysPut() {
        val ids = start(3)
        openPlain()
        for (p in listOf(ids[1], ids[2], ids[0])) { act(p, "buzz"); act(p, "answer", "text" to "nope") }
        assertEquals("reveal", tv.phase)
        assertEquals(false, tv.right)
        assertTrue(ids.all { score(it) == -200 })
        assertEquals(3, tv.drinks.size)
        assertTrue(tv.drinks.all { it.text == "Drink 1 sip" })
        skip()
        assertEquals(ids[0], tv.controller)
    }

    @Test fun ifNobodyRingsInTheAnswerIsRevealedAndNothingChanges() {
        val ids = start(3)
        val id = openPlain()
        skip() // the buzz window closes
        assertEquals("reveal", tv.phase)
        assertEquals(false, tv.right)
        assertEquals(ansOf(id), tv.answer)
        assertTrue(ids.all { score(it) == 0 })
        skip()
        assertEquals(ids[0], tv.controller)
    }

    @Test fun anAnswerThatTimesOutCountsAsWrong() {
        val ids = start(3)
        openPlain()
        act(ids[1], "buzz")
        skip()
        assertEquals(-200, score(ids[1]))
        assertEquals("buzz", tv.phase)
    }

    @Test fun drinkLinesFollowTheLobbySwitch() {
        val ids = start(3, extra = mapOf("drinks" to 0))
        openPlain()
        for (p in listOf(ids[1], ids[2], ids[0])) { act(p, "buzz"); act(p, "answer", "text" to "nope") }
        assertTrue(tv.drinks.isEmpty())
    }

    @Test fun drinkLinesAreWordedAsWaterForAPlayerOnWater() {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Jeopardy(pack()))))
        val a = e.add("P1")
        val b = e.add("P2")
        val wet = (e.join(e.roomCode, "Wet", Avatar("p:00", "#123456"), Role.PLAYER, water = true) as JoinResult.Joined).player.id
        e.setPresence(wet, true)
        e.host(HostCmd.StartGame("jeopardy", mapOf("show" to 0)))
        e.host(HostCmd.SkipPhase)
        openPlain()
        for (p in listOf(wet, a, b)) { act(p, "buzz"); act(p, "answer", "text" to "nope") }
        assertTrue(tv.drinks.single { it.id == wet }.text.endsWith("Drink 1 sip of water"))
    }

    @Test fun aPlayerWhoJoinsMidClueWatchesThatClueAndRingsInOnTheNext() {
        val ids = start(3)
        openPlain()
        val late = e.add("Late")
        assertEquals("out", assertIs<Screen.Buzzer>(screen(late)).state)
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(late, "buzz"))
        skip() // nobody rang in
        skip() // back to the board
        val id = tv.cells.first { it.row == 0 && !it.used }.id
        pickCell(id)
        assertEquals("reading", assertIs<Screen.Buzzer>(screen(late)).state)
    }
```

The `start` helper in the file must take the extra lobby settings used above; change its signature and body to:

```kotlin
    private fun start(count: Int, show: Int = 0, extra: Map<String, Int> = emptyMap()): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Jeopardy(pack()))))
        val ids = (1..count).map { e.add("P$it") }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("jeopardy", mapOf("show" to show) + extra)))
        e.host(HostCmd.SkipPhase)
        return ids
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :engine:test --tests 'partyos.engine.games.jeopardy.JeopardyTest' --console=plain`
Expected: the Task 2 tests still pass; the 12 new ones FAIL (`buzz` is refused with `NOT_NOW`, and skipping a `clue` phase ends the game).

- [ ] **Step 3: Implement**

In `Jeopardy.kt`, insert these arms in `onAction` directly above `            else -> throw Reject("NOT_NOW")`:

```kotlin
            s.phase == CLUE && kind == "buzz" -> {
                // Ringing in before the clue has been read locks you out for a second; a Daily Double has no ringing in.
                if (s.dailyDouble || who !in s.eligible) throw Reject("NOT_NOW")
                Step(s.copy(lockedUntil = s.lockedUntil + (who.v to ctx.now + JeopardyRules.LOCKOUT_MS)))
            }
            s.phase == BUZZ && kind == "buzz" -> {
                if (who !in s.eligible || who.v in s.tried) throw Reject("NOT_NOW")
                if ((s.lockedUntil[who.v] ?: 0L) > ctx.now) throw Reject("LOCKED_OUT")
                // The engine handles one action at a time, so the first buzz to arrive is the winner.
                Step(s.copy(phase = ANSWER, floor = who.v), listOf(Effect.Phase(ctx.timer(ANSWER_MS))))
            }
            s.phase == ANSWER && kind == "answer" -> {
                if (who.v != s.floor) throw Reject("NOT_NOW")
                val text = cleanText(payload["text"]?.jsonPrimitive?.content ?: "") ?: throw Reject("BAD_TEXT")
                judge(s, text, ctx)
            }
```

In `onDeadline`, insert these arms directly above `        else -> Step(s, listOf(Effect.Finish))`:

```kotlin
        CLUE ->
            if (s.dailyDouble) Step(s.copy(phase = ANSWER, floor = s.controller), listOf(Effect.Phase(ctx.timer(DD_ANSWER_MS))))
            else Step(s.copy(phase = BUZZ), listOf(Effect.Phase(ctx.timer(BUZZ_MS))))
        BUZZ -> reveal(s.copy(right = false), emptyList()) // nobody rang in
        ANSWER -> judge(s, null, ctx) // time ran out with nothing typed
        REVEAL -> afterReveal(s, ctx)
```

In `waitingOn`, insert above `        else -> null`:

```kotlin
        BUZZ -> s.eligible.filter { it.v !in s.tried }.toSet()
        ANSWER -> s.floor?.let { setOf(PlayerId(it)) }
```

Add these functions to the class, directly above the `// ---- views ---` comment:

```kotlin
    /** Marks the floor holder's answer (null = they ran out of time). Right takes the board; wrong loses the stake. */
    private fun judge(s: JeopardyState, text: String?, ctx: GameContext): Step<JeopardyState> {
        val active = requireNotNull(s.active)
        val floor = requireNotNull(s.floor)
        val right = matches(text, clueById.getValue(active).answer)
        val stake = if (s.dailyDouble) s.wager else cellOf(s, active).value
        val delta = JeopardyRules.clueDelta(stake, right)
        val deltas = s.deltas + (floor to ((s.deltas[floor] ?: 0) + delta))
        val sips = if (right) s.sips else s.sips + (floor to (if (s.dailyDouble) 2 else 1))
        val award = Effect.Award(PlayerId(floor), delta, if (right) "got it" else "missed")
        if (right) return reveal(s.copy(controller = floor, right = true, deltas = deltas, sips = sips), listOf(award))
        val tried = s.tried + floor
        val next = s.copy(tried = tried, deltas = deltas, sips = sips, floor = null)
        val others = !s.dailyDouble && s.eligible.any { it.v !in tried && ctx.isConnected(it) }
        return if (others) Step(next.copy(phase = BUZZ), listOf(award, Effect.Phase(ctx.timer(REBUZZ_MS))))
        else reveal(next.copy(right = false), listOf(award))
    }

    private fun reveal(s: JeopardyState, effects: List<Effect>): Step<JeopardyState> =
        Step(s.copy(phase = REVEAL, used = s.used + requireNotNull(s.active), lockedUntil = emptyMap()), effects + Effect.Phase(REVEAL_MS))

    private fun afterReveal(s: JeopardyState, ctx: GameContext): Step<JeopardyState> = toPick(s, ctx)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :engine:test --console=plain`
Expected: PASS (the whole engine suite; `JeopardyTest` now has 24 tests).

- [ ] **Step 5: Commit**

```bash
git add tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/Jeopardy.kt tv/engine/src/test/kotlin/partyos/engine/games/jeopardy/JeopardyTest.kt
git commit -m "feat(jeopardy): ring in, lockout, answers, penalties and board control"
```

---

### Task 4: Daily Doubles

**Files:**
- Modify: `tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/Jeopardy.kt`
- Modify: `tv/engine/src/test/kotlin/partyos/engine/games/jeopardy/JeopardyTest.kt`

**Interfaces:**
- Consumes: Task 3's `judge`, `openClue` (which already routes Daily Doubles to the `wager` phase), `JeopardyRules.wagerRange` and `MIN_WAGER`.
- Produces: action `wager` (`value`: Int) in the `wager` phase from the picker; rejection `BAD_WAGER`; `private fun placeWager(s, value, ctx)`.

- [ ] **Step 1: Write the failing tests**

Add inside `JeopardyTest`, before the final `}`:

```kotlin
    /** Skips a plain clue to its end and back to the board. */
    private fun playOut() { repeat(8) { if (tv.phase in setOf("wager", "clue", "buzz", "answer", "reveal")) skip() } }

    /** Opens cells in reading order (never row 0) until one is a Daily Double; returns its id with the phase at "wager". */
    private fun toDailyDouble(): String {
        toPick()
        for (row in 1..4) for (col in 0..4) {
            val cell = tv.cells.first { it.row == row && it.col == col }
            if (cell.used) continue
            pickCell(cell.id)
            if (tv.phase == "wager") return cell.id
            playOut()
        }
        error("no Daily Double found")
    }

    @Test fun aDailyDoubleAsksOnlyThePickerForAWagerAndHidesTheClue() {
        val ids = start(3)
        toDailyDouble()
        assertEquals("wager", tv.phase)
        assertTrue(tv.dailyDouble)
        assertEquals(null, tv.clue)
        assertEquals(null, tv.wager)
        val mine = assertIs<Screen.NumberEntry>(screen(ids[0]))
        assertEquals("wager", mine.kind)
        assertEquals("Daily Double!", assertIs<Screen.Waiting>(screen(ids[1])).title)
    }

    @Test fun aWagerIsAtLeastFiveAndAtMostTheTopValueForAPlayerWithNothing() {
        val ids = start(3)
        toDailyDouble()
        assertEquals(ActionResult.Rejected("BAD_WAGER"), act(ids[0], "wager", "value" to 4))
        assertEquals(ActionResult.Rejected("BAD_WAGER"), act(ids[0], "wager", "value" to 1001))
        assertEquals(ActionResult.Rejected("BAD_WAGER"), act(ids[0], "wager"))
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(ids[1], "wager", "value" to 100))
        assertEquals(ActionResult.Ack, act(ids[0], "wager", "value" to 1000))
        assertEquals("clue", tv.phase)
        assertEquals(1000, tv.wager)
    }

    @Test fun noWagerInTimeCountsAsFive() {
        start(3)
        toDailyDouble()
        skip()
        assertEquals("clue", tv.phase)
        assertEquals(5, tv.wager)
    }

    @Test fun aDailyDoubleHasNoRingingInAndOnlyThePickerAnswers() {
        val ids = start(3)
        toDailyDouble()
        act(ids[0], "wager", "value" to 300)
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(ids[1], "buzz"))
        skip() // the clue has been read
        assertEquals("answer", tv.phase)
        assertEquals(ids[0], tv.floor)
        assertIs<Screen.TextEntry>(screen(ids[0]))
        assertIs<Screen.Waiting>(screen(ids[1]))
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(ids[1], "answer", "text" to "anything"))
    }

    @Test fun aRightDailyDoubleWinsTheWagerAndKeepsTheBoard() {
        val ids = start(3)
        val id = toDailyDouble()
        act(ids[0], "wager", "value" to 300)
        skip()
        act(ids[0], "answer", "text" to ansOf(id))
        assertEquals(300, score(ids[0]))
        assertEquals("reveal", tv.phase)
        skip()
        assertEquals(ids[0], tv.controller)
    }

    @Test fun aWrongDailyDoubleLosesTheWagerEndsTheClueAndCostsTwoSips() {
        val ids = start(3)
        toDailyDouble()
        act(ids[0], "wager", "value" to 300)
        skip()
        act(ids[0], "answer", "text" to "nope")
        assertEquals(-300, score(ids[0]))
        assertEquals("reveal", tv.phase) // no reopening for the others
        assertEquals(2, tv.drinks.single { it.id == ids[0] }.sips)
        assertEquals("Drink 2 sips", tv.drinks.single { it.id == ids[0] }.text)
    }

    @Test fun aBoardHidesExactlyOneDailyDoubleAndNeverOnTheCheapRow() {
        start(3)
        toPick()
        var doubles = 0
        for (row in 0..4) for (col in 0..4) {
            pickCell(tv.cells.first { it.row == row && it.col == col }.id)
            if (tv.phase == "wager") { doubles++; assertTrue(row > 0, "Daily Double on the cheap row") }
            playOut()
        }
        assertEquals(1, doubles)
    }

    @Test fun aPlayerBelowZeroMayStillWagerUpToTheTopValue() {
        val ids = start(3)
        openPlain()
        act(ids[0], "buzz"); act(ids[0], "answer", "text" to "nope") // -200
        skip() // nobody else rings in
        skip() // back to the board
        assertEquals(-200, score(ids[0]))
        toDailyDouble()
        assertEquals(ActionResult.Rejected("BAD_WAGER"), act(ids[0], "wager", "value" to 1001))
        assertEquals(ActionResult.Ack, act(ids[0], "wager", "value" to 1000))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :engine:test --tests 'partyos.engine.games.jeopardy.JeopardyTest' --console=plain`
Expected: the 8 new tests FAIL (`wager` is refused with `NOT_NOW`, and skipping the wager phase ends the game).

- [ ] **Step 3: Implement**

In `Jeopardy.kt`, insert directly above `            else -> throw Reject("NOT_NOW")` in `onAction`:

```kotlin
            s.phase == WAGER && kind == "wager" -> {
                if (who.v != s.controller) throw Reject("NOT_NOW")
                val value = payload["value"]?.jsonPrimitive?.intOrNull ?: throw Reject("BAD_WAGER")
                if (value !in JeopardyRules.wagerRange(ctx.scores[who] ?: 0, s.round)) throw Reject("BAD_WAGER")
                placeWager(s, value)
            }
```

In `onDeadline`, insert directly above `        CLUE ->`:

```kotlin
        WAGER -> placeWager(s, JeopardyRules.MIN_WAGER) // no wager in time counts as the minimum
```

In `waitingOn`, insert above `        BUZZ ->`:

```kotlin
        WAGER -> s.controller?.let { setOf(PlayerId(it)) }
```

Add above `private fun judge`:

```kotlin
    private fun placeWager(s: JeopardyState, value: Int): Step<JeopardyState> =
        Step(s.copy(phase = CLUE, wager = value), listOf(Effect.Phase(JeopardyRules.readMs(clueById.getValue(requireNotNull(s.active)).clue))))
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :engine:test --console=plain`
Expected: PASS (`JeopardyTest` now has 32 tests).

- [ ] **Step 5: Commit**

```bash
git add tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/Jeopardy.kt tv/engine/src/test/kotlin/partyos/engine/games/jeopardy/JeopardyTest.kt
git commit -m "feat(jeopardy): Daily Doubles with wagers"
```

---

### Task 5: The second board, Final Jeopardy and the podium

**Files:**
- Modify: `tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/Jeopardy.kt`
- Modify: `tv/engine/src/test/kotlin/partyos/engine/games/jeopardy/JeopardyTest.kt`

**Interfaces:**
- Consumes: Tasks 2–4 (`toPick`, `reveal`, the state fields `boards`, `nextCategories`, `finalWagerers`, `finalPlayers`, `finalWagers`, `finalAnswers`, `finalOrder`, `finalStep`, `finalRight`, `finalResults`).
- Produces: the rest of the deadline chain (`reveal → pick | break → intro → pick … → final_category → final_wager → final_answer → final_reveal (one step per player) → podium → finish`); actions `wager` and `answer` in the Final phases; `private fun afterReveal` (replaced), `startSecondBoard`, `enterFinal`, `podium`, `beginFinalReveal`, `nextFinalStep`, `revealFinalStep`.

- [ ] **Step 1: Write the failing tests**

Add inside `JeopardyTest`, before the final `}`:

```kotlin
    /** Plays every clue left on the board with nobody ringing in (a Daily Double is wagered at the minimum and missed). */
    private fun playBoardOut() {
        toPick()
        var guard = 0
        while (tv.phase == "pick" && tv.cells.any { !it.used } && guard++ < 40) {
            val cell = tv.cells.filter { !it.used }.minWith(compareBy({ it.row }, { it.col }))
            pickCell(cell.id)
            repeat(8) { if (tv.phase in setOf("wager", "clue", "buzz", "answer", "reveal")) skip() }
        }
    }

    /** [who] rings in on the next cheap cell and answers it right. */
    private fun answerRight(who: PlayerId, col: Int) {
        val id = openPlain(col)
        act(who, "buzz"); act(who, "answer", "text" to ansOf(id))
        skip() // reveal -> pick
    }

    @Test fun aShortShowGoesFromTheLastClueStraightToFinalJeopardy() {
        val ids = start(3)
        answerRight(ids[1], 0)
        playBoardOut()
        assertEquals("final_category", tv.phase)
        assertEquals(3, tv.round)
        assertTrue(tv.cells.isEmpty())
    }

    @Test fun theFullShowPlaysASecondBoardWithDoubleValuesAndTheLowestScorePicksFirst() {
        val ids = start(3, show = 1)
        answerRight(ids[1], 0)
        val id = openPlain(1)
        act(ids[2], "buzz"); act(ids[2], "answer", "text" to "nope") // P3 is now the lowest
        skip(); skip()
        assertEquals(-200, score(ids[2]))
        playBoardOut()
        assertEquals("break", tv.phase)
        skip()
        assertEquals("intro", tv.phase)
        assertEquals(2, tv.round)
        assertEquals(ids[2], tv.controller)
        assertEquals(listOf(400, 800, 1200, 1600, 2000), (0..4).map { r -> tv.cells.first { it.row == r && it.col == 0 }.value })
        assertTrue(tv.cells.none { it.used })
        skip()
        assertEquals("pick", tv.phase)
        assertTrue(id.isNotEmpty())
    }

    @Test fun theSecondBoardHidesTwoDailyDoubles() {
        start(3, show = 1)
        playBoardOut()
        skip(); skip() // break -> intro -> pick
        var doubles = 0
        for (row in 0..4) for (col in 0..4) {
            pickCell(tv.cells.first { it.row == row && it.col == col }.id)
            if (tv.phase == "wager") doubles++
            playOut()
        }
        assertEquals(2, doubles)
    }

    @Test fun finalJeopardyIsSkippedWhenNobodyIsAboveZero() {
        start(3)
        playBoardOut() // nobody scores; the Daily Double costs its picker a little
        assertEquals("podium", tv.phase)
    }

    @Test fun finalJeopardyTakesSecretWagersThenRevealsFromTheLowestScoreUp() {
        val ids = start(3)
        answerRight(ids[1], 0)
        answerRight(ids[2], 1)
        playBoardOut()
        assertEquals("final_category", tv.phase)
        val before = ids.associateWith { score(it) }
        assertEquals(0, before.getValue(ids[0]))
        assertTrue(before.getValue(ids[1]) > 0 && before.getValue(ids[2]) > 0)
        val k = tv.final!!.category.removePrefix("Final ")
        skip() // category -> wager
        assertEquals("final_wager", tv.phase)
        assertIs<Screen.Waiting>(screen(ids[0])) // no score, no wager
        assertEquals("wager", assertIs<Screen.NumberEntry>(screen(ids[1])).kind)
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(ids[0], "wager", "value" to 0))
        assertEquals(ActionResult.Rejected("BAD_WAGER"), act(ids[1], "wager", "value" to before.getValue(ids[1]) + 1))
        assertEquals(ActionResult.Ack, act(ids[1], "wager", "value" to before.getValue(ids[1])))
        assertEquals(ActionResult.Ack, act(ids[2], "wager", "value" to 100))
        assertEquals("final_answer", tv.phase) // both bettors are in, so the phase moves on by itself
        assertEquals("Final clue $k", tv.final!!.clue)
        act(ids[1], "answer", "text" to "What is Fin$k?")
        act(ids[2], "answer", "text" to "nope")
        act(ids[0], "answer", "text" to "Fin$k")
        assertEquals("final_reveal", tv.phase)
        // Lowest first: P1 (no wager), then whichever of P2/P3 had less.
        val order = ids.sortedBy { before.getValue(it) }
        assertEquals(order[0], tv.final!!.steps.single().id)
        skip(); skip()
        assertEquals(3, tv.final!!.steps.size)
        assertEquals(order, tv.final!!.steps.map { it.id })
        assertEquals("Fin$k", tv.final!!.answer)
        assertEquals(before.getValue(ids[1]) * 2, score(ids[1]))
        assertEquals(before.getValue(ids[2]) - 100, score(ids[2]))
        assertEquals(0, score(ids[0]))
        assertEquals(2, tv.drinks.single { it.id == ids[2] }.sips)
        skip()
        assertEquals("podium", tv.phase)
        skip()
        assertEquals(null, e.tvState().stage)
        assertTrue(e.tvState().lastResult != null)
    }

    @Test fun aSavedGameKeepsTheFloorMidClue() {
        val ids = start(3)
        openPlain()
        act(ids[1], "buzz")
        val restored = PartyEngine.restore(e.snapshot(), clock, SeededEntropy(4), GameRegistry(listOf(Jeopardy(pack()))))
        val restoredTv = restored.tvState().stage!!.game as JeopardyTv
        assertEquals("answer", restoredTv.phase)
        assertEquals(ids[1], restoredTv.floor)
        assertIs<Screen.TextEntry>(restored.phoneState(ids[1]).screen)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :engine:test --tests 'partyos.engine.games.jeopardy.JeopardyTest' --console=plain`
Expected: the 5 board/Final tests FAIL (the board never ends; there is no break and no Final); `aSavedGameKeepsTheFloorMidClue` may already pass.

- [ ] **Step 3: Implement**

In `Jeopardy.kt`, insert these arms in `onAction` directly above `            else -> throw Reject("NOT_NOW")`:

```kotlin
            s.phase == FINAL_WAGER && kind == "wager" -> {
                if (who !in s.finalWagerers) throw Reject("NOT_NOW")
                val value = payload["value"]?.jsonPrimitive?.intOrNull ?: throw Reject("BAD_WAGER")
                if (value !in JeopardyRules.finalWagerRange(ctx.scores[who] ?: 0)) throw Reject("BAD_WAGER")
                Step(s.copy(finalWagers = s.finalWagers + (who.v to value)))
            }
            s.phase == FINAL_ANSWER && kind == "answer" -> {
                if (who !in s.finalPlayers) throw Reject("NOT_NOW")
                val text = cleanText(payload["text"]?.jsonPrimitive?.content ?: "") ?: throw Reject("BAD_TEXT")
                Step(s.copy(finalAnswers = s.finalAnswers + (who.v to text)))
            }
```

In `onDeadline`, insert above `        else -> Step(s, listOf(Effect.Finish))`:

```kotlin
        BREAK -> startSecondBoard(s, ctx)
        FINAL_CATEGORY -> Step(s.copy(phase = FINAL_WAGER), listOf(Effect.Phase(ctx.timer(FINAL_WAGER_MS))))
        FINAL_WAGER -> Step(s.copy(phase = FINAL_ANSWER), listOf(Effect.Phase(ctx.timer(FINAL_ANSWER_MS))))
        FINAL_ANSWER -> beginFinalReveal(s, ctx)
        FINAL_REVEAL -> nextFinalStep(s, ctx)
```

In `waitingOn`, insert above `        else -> null`:

```kotlin
        FINAL_WAGER -> s.finalWagerers.filter { it.v !in s.finalWagers }.toSet()
        FINAL_ANSWER -> s.finalPlayers.filter { it.v !in s.finalAnswers }.toSet()
```

Replace the one-line `afterReveal` with:

```kotlin
    /** After the reveal: the next pick, or, once the board is empty, the break, the next board or Final Jeopardy. */
    private fun afterReveal(s: JeopardyState, ctx: GameContext): Step<JeopardyState> {
        val boardDone = cells(s).all { it.id in s.used }
        return when {
            !boardDone -> toPick(s, ctx)
            s.round < s.boards -> Step(s.copy(phase = BREAK, active = null, floor = null, dailyDouble = false), listOf(Effect.Phase(BREAK_MS)))
            else -> enterFinal(s, ctx)
        }
    }

    /** Double Jeopardy: a fresh board, doubled values, two Daily Doubles, and the lowest score picks first. */
    private fun startSecondBoard(s: JeopardyState, ctx: GameContext): Step<JeopardyState> {
        val players = ctx.players.map { it.id.v }
        return Step(
            s.copy(
                phase = INTRO, round = 2, categories = s.nextCategories, nextCategories = emptyList(), used = emptySet(),
                dailyDoubles = dealDoubles(2, s.nextCategories, ctx.random),
                controller = JeopardyRules.firstPicker(2, players, ctx.scores.mapKeys { it.key.v }, ctx.captain?.v),
            ),
            listOf(Effect.Phase(INTRO_MS)),
        )
    }

    /** Final Jeopardy, unless nobody has a score above zero to bet. */
    private fun enterFinal(s: JeopardyState, ctx: GameContext): Step<JeopardyState> {
        val bettors = ctx.players.filter { (ctx.scores[it.id] ?: 0) > 0 }.map { it.id }
        if (bettors.isEmpty()) return podium(s)
        return Step(
            s.copy(phase = FINAL_CATEGORY, round = 3, active = null, floor = null, dailyDouble = false, sips = emptyMap(), finalPlayers = ctx.players.map { it.id }, finalWagerers = bettors),
            listOf(Effect.Phase(FINAL_CAT_MS)),
        )
    }

    private fun podium(s: JeopardyState): Step<JeopardyState> =
        Step(s.copy(phase = PODIUM, active = null, floor = null, dailyDouble = false), listOf(Effect.Phase(PODIUM_MS)))

    private fun beginFinalReveal(s: JeopardyState, ctx: GameContext): Step<JeopardyState> {
        val order = JeopardyRules.finalOrder(s.finalPlayers.map { it.v }, ctx.scores.mapKeys { it.key.v })
        return revealFinalStep(s.copy(phase = FINAL_REVEAL, finalOrder = order, finalStep = 0), ctx)
    }

    private fun nextFinalStep(s: JeopardyState, ctx: GameContext): Step<JeopardyState> =
        if (s.finalStep + 1 < s.finalOrder.size) revealFinalStep(s.copy(finalStep = s.finalStep + 1), ctx) else podium(s)

    /** Shows one player's Final answer: it is marked and their wager is won or lost. */
    private fun revealFinalStep(s: JeopardyState, ctx: GameContext): Step<JeopardyState> {
        val id = s.finalOrder[s.finalStep]
        val wager = s.finalWagers[id] ?: 0
        val right = matches(s.finalAnswers[id], finalById.getValue(s.finalId).answer)
        val delta = if (wager == 0) 0 else JeopardyRules.clueDelta(wager, right)
        val effects = mutableListOf<Effect>()
        if (delta != 0) effects += Effect.Award(PlayerId(id), delta, if (right) "won Final Jeopardy" else "lost Final Jeopardy")
        return Step(
            s.copy(
                finalResults = s.finalResults + (id to delta),
                finalRight = if (right) s.finalRight + id else s.finalRight,
                sips = if (!right && wager > 0) s.sips + (id to 2) else s.sips,
            ),
            effects + Effect.Phase(FINAL_STEP_MS),
        )
    }
```

`finalWagerers` (the players with a score above zero when Final starts) is already part of `JeopardyState` from Task 2.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :engine:test --console=plain` then `./gradlew :devserver:compileKotlin --console=plain`
Expected: PASS (`JeopardyTest` now has 38 tests; the whole engine suite is green).

- [ ] **Step 5: Commit**

```bash
git add tv/engine/src/main/kotlin/partyos/engine/games/jeopardy/Jeopardy.kt tv/engine/src/test/kotlin/partyos/engine/games/jeopardy/JeopardyTest.kt
git commit -m "feat(jeopardy): Double Jeopardy, Final Jeopardy and the podium"
```

### Task 6: Pin the new types on the wire

**Files:**
- Modify: `tv/server/src/test/kotlin/partyos/server/ProtocolFixturesTest.kt`
- Modify (regenerated): `controller/src/protocol/fixtures/server-messages.json`, `client-messages.json`
- Modify: `controller/src/protocol.ts`, `controller/src/protocol.test.ts`

**Interfaces:**
- Consumes: `Screen.Board`, `Screen.Buzzer`, `BoardCell`, `JeopardyTv` and its row types (Task 2).
- Produces (TypeScript): `BoardCell { id; col; row; value; used }`; `Screen` gains `{ t: 'board'; prompt; categories; cells; canPick; pickFor?; note? }` and `{ t: 'buzzer'; state: 'reading' | 'open' | 'locked' | 'beaten' | 'tried' | 'out'; category; value; detail?; lockedMs; live }`; `OptionKey` gains `'show'`; client actions `pick` (`cell`), `buzz`, `wager` (`value`) and host command `gameAction` are covered by fixtures. `tv/types.ts` is untouched here (Task 8 rewrites the Jeopardy types together with the stage that uses them).

- [ ] **Step 1: Add the wire samples to the Kotlin fixtures test**

In `ProtocolFixturesTest.kt` add imports `partyos.engine.BoardCell`, `partyos.engine.JeopardyCellTv`, `partyos.engine.JeopardyDelta`, `partyos.engine.JeopardyDrink`, `partyos.engine.JeopardyFinalStep`, `partyos.engine.JeopardyFinalTv`, `partyos.engine.JeopardyTv`. In `serverMessages`, directly before `        ServerMsg.Ack("a-1"),` insert:

```kotlin
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
```

In `clientMessages`, directly before `        ClientMsg.Ping,` insert:

```kotlin
        ClientMsg.Action("a-14", 4, JsonObject(mapOf("kind" to JsonPrimitive("pick"), "cell" to JsonPrimitive("food-200")))),
        ClientMsg.Action("a-15", 6, JsonObject(mapOf("kind" to JsonPrimitive("buzz")))),
        ClientMsg.Action("a-16", 8, JsonObject(mapOf("kind" to JsonPrimitive("wager"), "value" to JsonPrimitive(300)))),
        ClientMsg.Host("h-15", HostCommand.SetOption("show", 1)),
        ClientMsg.Host("h-16", HostCommand.GameAction("pick:food-200")),
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :server:test --tests 'partyos.server.ProtocolFixturesTest' --console=plain`
Expected: FAIL, `server-messages.json is stale; rerun with -PupdateFixtures` (and the same for `client-messages.json`).

- [ ] **Step 3: Regenerate the fixtures and re-run**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :server:test --tests 'partyos.server.ProtocolFixturesTest' -PupdateFixtures --console=plain`
Then: `./gradlew :server:cleanTest :server:test --tests 'partyos.server.ProtocolFixturesTest' --console=plain`
Expected: both PASS. `git diff --stat controller/src/protocol/fixtures` shows additions only (`"t": "board"`, `"t": "buzzer"`, `"t": "jeopardy"`, the new client actions). If it rewrites unrelated samples, stop and investigate.

- [ ] **Step 4: Update the controller tests (failing)**

In `controller/src/protocol.test.ts` change the covered screen kinds to end `..., 'turf', 'sprawl', 'secret', 'board', 'buzzer']))`, and in the `ours` list, directly before `      { t: 'ping' },`, add:

```ts
      { t: 'action', id: 'a-14', round: 4, payload: { kind: 'pick', cell: 'food-200' } },
      { t: 'action', id: 'a-15', round: 6, payload: { kind: 'buzz' } },
      { t: 'action', id: 'a-16', round: 8, payload: { kind: 'wager', value: 300 } },
      { t: 'host', id: 'h-15', cmd: { t: 'setOption', key: 'show', value: 1 } },
      { t: 'host', id: 'h-16', cmd: { t: 'gameAction', action: 'pick:food-200' } },
```

Run: `cd controller && npm test`
Expected: FAIL, `parses every server message sample` (the `board` and `buzzer` screens are unknown).

- [ ] **Step 5: Add the TypeScript wire types**

In `controller/src/protocol.ts`: after the `SecretInput` interface add `export interface BoardCell { id: string; col: number; row: number; value: number; used: boolean }`; in the `Screen` union, after the `secret` member, add:

```ts
  | { t: 'board'; prompt: string; categories: string[]; cells: BoardCell[]; canPick: boolean; pickFor?: string; note?: string }
  | { t: 'buzzer'; state: 'reading' | 'open' | 'locked' | 'beaten' | 'tried' | 'out'; category: string; value: number; detail?: string; lockedMs: number; live: boolean }
```

add `| 'show'` to the `OptionKey` union, and add `'board', 'buzzer'` to the `SCREENS` set.

- [ ] **Step 6: Run everything**

Run: `cd controller && npm test && npx tsc -b`
Expected: PASS, 17 tests, no type errors.

- [ ] **Step 7: Commit**

```bash
git add tv/server/src/test/kotlin/partyos/server/ProtocolFixturesTest.kt controller/src/protocol/fixtures controller/src/protocol.ts controller/src/protocol.test.ts
git commit -m "feat(jeopardy): pin the board, buzzer and Jeopardy TV payload on the wire"
```

---

### Task 7: The phone: board, BUZZ button, theme and a gallery fixture

**Files:**
- Create: `controller/src/screens/BoardScreen.tsx`, `controller/src/screens/BuzzerScreen.tsx`, `controller/src/screens/jeopardy-phone.css`
- Modify: `controller/src/screens/ScreenView.tsx`
- Modify: `controller/src/theme/gameTheme.ts`, `controller/src/theme/games.css`
- Create: `controller/src/tv/fixtures/jeopardy-theme.ts`
- Modify: `controller/src/tv/ThemeGallery.tsx` (fixture only; the TV stage case comes in Task 8)
- Test: `controller/e2e/jeopardy-gallery.spec.ts` (new)

**Interfaces:**
- Consumes: the `board` and `buzzer` `Screen` types (Task 6); `ActionPayload` from `../protocol`.
- Produces: `BoardScreen({ screen, disabled, onAction })` (sends `{ kind: 'pick', cell }` after tap-then-confirm); `BuzzerScreen({ screen, disabled, onAction })` (sends `{ kind: 'buzz' }`); the `jeopardy` theme; gallery beats at `/tv?gallery=themes&game=jeopardy&beat=<beat>[&view=phone]`: `board`, `boardwait`, `boardcaptain`, `reading`, `open`, `lockedlive`, `beaten`, `tried`, `wager`, `answer`, plus TV-only beats `intro`, `clue`, `dailydouble`, `reveal`, `finalwager`, `finalreveal` (the TV payloads follow Task 2's `JeopardyTv`). CSS classes the specs use: `.jb-cell`, `.jb-head`, `.jz-buzz`.

- [ ] **Step 1: Write the failing gallery spec (phone part)**

Create `controller/e2e/jeopardy-gallery.spec.ts`:

```ts
import { expect, test, type Locator } from '@playwright/test'

const gallery = (beat: string) => `/tv?gallery=themes&game=jeopardy&beat=${beat}`
const phoneView = (beat: string) => `${gallery(beat)}&view=phone`

/** Perceived brightness (0-1) of an element's text colour, read back through a canvas so any colour notation works. */
const brightness = (el: Locator) => el.evaluate((node) => {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 1
  const g = canvas.getContext('2d')!
  g.fillStyle = getComputedStyle(node).color
  g.fillRect(0, 0, 1, 1)
  const [r, gr, b] = g.getImageData(0, 0, 1, 1).data
  return (0.2126 * r + 0.7152 * gr + 0.0722 * b) / 255
})

test.describe('the phone board', () => {
  test('tap a square, then confirm: nothing is picked by a single tap', async ({ page }) => {
    await page.goto(phoneView('board'))
    await expect(page.locator('.jb-head')).toHaveCount(5)
    await expect(page.locator('.jb-cell')).toHaveCount(25)
    await expect(page.getByRole('button', { name: /^Pick / })).toHaveCount(0)
    await page.getByRole('button', { name: 'Sports for 400' }).click()
    const confirm = page.getByRole('button', { name: 'Pick Sports for $400' })
    await expect(confirm).toBeVisible()
    await confirm.click()
    await expect(page.getByRole('status')).toContainText('Preview only')
  })

  test('squares already played cannot be chosen', async ({ page }) => {
    await page.goto(phoneView('board'))
    await expect(page.locator('.jb-cell.used')).toHaveCount(2)
    for (const c of await page.locator('.jb-cell.used').all()) await expect(c).toBeDisabled()
  })

  test('someone who does not hold the board can look but not pick', async ({ page }) => {
    await page.goto(phoneView('boardwait'))
    await expect(page.getByText('Ben is picking')).toBeVisible()
    for (const c of await page.locator('.jb-cell').all()) await expect(c).toBeDisabled()
  })

  test('the captain sees who they are picking for', async ({ page }) => {
    await page.goto(phoneView('boardcaptain'))
    await page.getByRole('button', { name: 'Music for 600' }).click()
    await expect(page.getByRole('button', { name: 'Pick Music for $600 (for Ben)' })).toBeVisible()
  })

  test('the board fits a phone with thumb-sized squares and readable text', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto(phoneView('boardwait'))
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(375)
    for (const c of (await page.locator('.jb-cell').all()).slice(0, 5)) expect((await c.boundingBox())!.height).toBeGreaterThanOrEqual(44)
    expect(await brightness(page.locator('.muted, .jb-note').first()), 'note text is too dark on the dark stage').toBeGreaterThan(0.5)
  })
})

test.describe('the BUZZ button', () => {
  test('waits while the clue is read, then lights up', async ({ page }) => {
    await page.goto(phoneView('reading'))
    await expect(page.locator('.jz-buzz')).toContainText('WAIT')
    await page.goto(phoneView('open'))
    const buzz = page.locator('.jz-buzz')
    await expect(buzz).toContainText('BUZZ!')
    await expect(buzz).toBeEnabled()
    await buzz.click()
    await expect(page.getByRole('status')).toContainText('Preview only')
  })

  test('a locked-out phone opens itself when the lockout ends and the window is open', async ({ page }) => {
    await page.goto(phoneView('lockedlive'))
    await expect(page.locator('.jz-buzz')).toContainText('TOO EARLY')
    await expect(page.locator('.jz-buzz')).toContainText('BUZZ!', { timeout: 3_000 })
  })

  test('someone who lost the race or missed the clue cannot buzz', async ({ page }) => {
    await page.goto(phoneView('beaten'))
    await expect(page.locator('.jz-buzz')).toContainText('TOO SLOW')
    await expect(page.locator('.jz-buzz')).toBeDisabled()
    await page.goto(phoneView('tried'))
    await expect(page.locator('.jz-buzz')).toBeDisabled()
  })

  test('the buzz button is big', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto(phoneView('open'))
    const box = (await page.locator('.jz-buzz').boundingBox())!
    expect(box.height).toBeGreaterThan(300)
    expect(box.width).toBeGreaterThan(300)
  })
})

test('wagers and answers use the keypad and the text box already on the phone', async ({ page }) => {
  await page.goto(phoneView('wager'))
  await expect(page.getByText('How much do you wager?')).toBeVisible()
  await expect(page.locator('.numpad')).toBeVisible()
  await page.goto(phoneView('answer'))
  await expect(page.getByRole('textbox')).toBeVisible()
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd controller && npx playwright test e2e/jeopardy-gallery.spec.ts --reporter=line`
Expected: FAIL (the `jeopardy` gallery game does not exist yet, so the beat pages render the default).

- [ ] **Step 3: Register the theme**

In `controller/src/theme/gameTheme.ts` add `'jeopardy'` to the `THEMED_GAMES` list. In `controller/src/theme/games.css`, after the `[data-game-theme='imposter']` block, add:

```css
[data-game-theme='jeopardy'] {
  --ink: #15183f;
  --ink-soft: #4a4f86;
  --paper: #f8f1dc;
  --paper-2: #e4dbbd;
  --font-display: 'Anton', system-ui, sans-serif;
  --game-bg: #1b1f57;
  --game-accent: #f2c14e;
}
```

and after the `.game-scene[data-game-theme='imposter']` rule add:

```css
/* The Studio set: a spotlight from above on a deep indigo stage. */
.game-scene[data-game-theme='jeopardy'] {
  background-image: radial-gradient(ellipse 70% 55% at 50% -8%, #ffffff2e 0, transparent 70%), radial-gradient(ellipse 90% 40% at 50% 118%, #0000005c 0, transparent 70%);
}
/* The stage is dark, so text that sits straight on it (not inside a paper tile) reads in paper, never ink. */
[data-game-theme='jeopardy'] .status-text { color: var(--paper); }
[data-game-theme='jeopardy'] .muted { color: var(--paper); opacity: .85; }
[data-game-theme='jeopardy'] h1.prompt:not(.small) { color: var(--paper); }
```

- [ ] **Step 4: Write the board screen**

Create `controller/src/screens/BoardScreen.tsx`:

```tsx
import { useState, type CSSProperties } from 'react'
import type { ActionPayload, Screen } from '../protocol'
import './jeopardy-phone.css'

type BoardScreenT = Extract<Screen, { t: 'board' }>

const buzz = (ms: number) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }

/** The clue board on a phone. Tap a square, then confirm, so nobody picks by accident. */
export function BoardScreen({ screen, disabled, onAction }: { screen: BoardScreenT; disabled: boolean; onAction(p: ActionPayload): void }) {
  const [selected, setSelected] = useState<string | null>(null)
  const rows = [...new Set(screen.cells.map((c) => c.row))].sort((a, b) => a - b)
  const cellAt = (col: number, row: number) => screen.cells.find((c) => c.col === col && c.row === row)
  const cell = screen.cells.find((c) => c.id === selected)
  return (
    <div className="stack jb">
      <h1 className="prompt small">{screen.prompt}</h1>
      <div className="jb-board" style={{ '--cols': screen.categories.length } as CSSProperties}>
        {screen.categories.map((name, col) => <div key={`h${col}`} className="jb-head"><span>{name}</span></div>)}
        {rows.flatMap((row) => screen.categories.map((_, col) => {
          const c = cellAt(col, row)
          if (!c) return <div key={`${col}-${row}`} />
          const on = selected === c.id
          return (
            <button
              key={c.id}
              type="button"
              className={`jb-cell ${c.used ? 'used' : ''} ${on ? 'on' : ''}`}
              disabled={disabled || !screen.canPick || c.used}
              aria-pressed={on}
              aria-label={c.used ? 'Played' : `${screen.categories[col]} for ${c.value}`}
              onClick={() => { buzz(12); setSelected(on ? null : c.id) }}
            >
              {c.used ? '' : `$${c.value}`}
            </button>
          )
        }))}
      </div>
      {screen.canPick && cell
        ? (
          <button className="primary big" disabled={disabled} onClick={() => { buzz(40); onAction({ kind: 'pick', cell: cell.id }) }}>
            Pick {screen.categories[cell.col]} for ${cell.value}{screen.pickFor ? ` (for ${screen.pickFor})` : ''}
          </button>
        )
        : <p className="muted jb-note">{screen.canPick ? 'Tap a square to choose it' : screen.note ?? ''}</p>}
    </div>
  )
}
```

- [ ] **Step 5: Write the BUZZ screen**

Create `controller/src/screens/BuzzerScreen.tsx`:

```tsx
import { useEffect, useState } from 'react'
import type { ActionPayload, Screen } from '../protocol'
import './jeopardy-phone.css'

type BuzzerScreenT = Extract<Screen, { t: 'buzzer' }>

const buzz = (ms: number | number[]) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }
const LABELS: Record<BuzzerScreenT['state'], string> = { reading: 'WAIT', open: 'BUZZ!', locked: 'TOO EARLY', beaten: 'TOO SLOW', tried: 'MISSED', out: 'NEXT CLUE' }

/**
 * One giant button. It can be pressed while the clue is still being read (that is ringing in early, and the server
 * locks you out) and while it is open. A locked-out phone opens itself when the lockout ends, if the buzz window is
 * still open: the server has no way to push a view at that moment.
 */
export function BuzzerScreen({ screen, disabled, onAction }: { screen: BuzzerScreenT; disabled: boolean; onAction(p: ActionPayload): void }) {
  const [unlocked, setUnlocked] = useState(false)
  useEffect(() => {
    setUnlocked(false)
    if (screen.state !== 'locked' || !screen.live) return
    const id = setTimeout(() => setUnlocked(true), screen.lockedMs)
    return () => clearTimeout(id)
  }, [screen.state, screen.lockedMs, screen.live])
  const state = screen.state === 'locked' && unlocked ? 'open' : screen.state
  const pressable = state === 'reading' || state === 'open' || state === 'locked'
  return (
    <div className="stack jz">
      <p className="jz-clue"><b>{screen.category}</b> · ${screen.value}</p>
      <button
        type="button"
        className={`jz-buzz ${state}`}
        disabled={disabled || !pressable}
        onClick={() => { buzz(state === 'open' ? [30, 30, 30] : 20); onAction({ kind: 'buzz' }) }}
      >
        {LABELS[state]}
      </button>
      {screen.detail && state !== 'open' && <p className="muted jz-detail">{screen.detail}</p>}
    </div>
  )
}
```

- [ ] **Step 6: Style them and wire them in**

Create `controller/src/screens/jeopardy-phone.css`:

```css
/* Answer & Question on the phone: the board and the BUZZ button, on the dark Studio stage. */
.jb-board { display: grid; grid-template-columns: repeat(var(--cols, 5), minmax(0, 1fr)); gap: 6px; }
.jb-head {
  display: grid; place-items: center; min-height: 52px; padding: 4px 3px; text-align: center;
  background: #2f3fb5; color: var(--paper); border: 3px solid var(--ink); border-radius: 10px;
  font: 800 11px/1.1 var(--font-body, system-ui); letter-spacing: .04em; text-transform: uppercase; overflow-wrap: anywhere;
}
.jb-cell {
  min-height: 52px; padding: 0; border: 3px solid var(--ink); border-radius: 10px; background: var(--paper); color: var(--ink);
  font: 900 17px var(--font-display, system-ui); box-shadow: 0 4px 0 var(--ink);
}
.jb-cell.on { background: var(--game-accent); transform: translateY(2px); box-shadow: 0 2px 0 var(--ink); }
.jb-cell.used { background: transparent; border-style: dashed; box-shadow: none; opacity: .4; }
.jb-cell:disabled:not(.used) { opacity: .85; }

.jz { align-items: center; gap: 18px; }
.jz-clue { color: var(--paper); font: 800 22px var(--font-body, system-ui); text-align: center; }
.jz-clue b { color: var(--game-accent); }
.jz-buzz {
  width: 100%; min-height: 46vh; border: 5px solid var(--ink); border-radius: 28px; padding: 12px;
  font: 400 clamp(46px, 17vw, 88px)/1 var(--font-display, system-ui); letter-spacing: .04em; box-shadow: 0 9px 0 var(--ink);
  background: var(--paper-2); color: var(--ink);
}
.jz-buzz.open { background: var(--lime, #7bd66a); animation: jz-pulse .9s ease-in-out infinite; }
.jz-buzz.open:active { transform: translateY(6px); box-shadow: 0 3px 0 var(--ink); }
.jz-buzz.locked { background: var(--tomato, #ff5a4a); color: var(--white, #fff); }
.jz-buzz.reading { background: var(--game-accent); }
.jz-buzz:disabled { box-shadow: 0 4px 0 var(--ink); opacity: .9; }
.jz-detail { text-align: center; font-size: 22px; }
@keyframes jz-pulse { 50% { transform: scale(1.02); } }
```

In `controller/src/screens/ScreenView.tsx` add `import { BoardScreen } from './BoardScreen'` and `import { BuzzerScreen } from './BuzzerScreen'`, and in the `switch (screen.t)` add:

```tsx
    case 'board': return <BoardScreen screen={screen} disabled={disabled} onAction={onAction} />
    case 'buzzer': return <BuzzerScreen screen={screen} disabled={disabled} onAction={onAction} />
```

- [ ] **Step 7: Write the gallery fixture**

Create `controller/src/tv/fixtures/jeopardy-theme.ts`:

```ts
import type { PlayerSummary, ScoreRow, Screen, StageInfo } from '../../protocol'

// Synthetic beats for the theme gallery (six fictional players, invented clues). No tokens or real sessions.
const palette = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF71', '#2F6BFF', '#9B5DE5']
const names = ['Ana', 'Ben', 'Cleo', 'Dev', 'Eli', 'Fay']
const players: PlayerSummary[] = names.map((name, i) => ({ id: `p${i}`, name, avatar: { face: `p:0${i}`, color: palette[i] }, role: 'PLAYER', connected: true }))
const totals = [1200, 800, 600, 200, -200, -400]
const scores: ScoreRow[] = players.map((p, i) => ({ id: p.id, name: p.name, avatar: p.avatar, score: totals[i] }))
const me = players[0]

const categories = ['Food & Drink', 'Sports', 'Music', 'Movies', 'Space']
const cells = (usedIds: string[] = [], round = 1) =>
  categories.flatMap((_, col) => [0, 1, 2, 3, 4].map((row) => {
    const id = `c${col}-${row}`
    return { id, col, row, value: (round === 1 ? 200 : 400) * (row + 1), used: usedIds.includes(id) }
  }))
const used = ['c0-0', 'c1-0']

const game = (over: Record<string, unknown>) => ({
  t: 'jeopardy', phase: 'pick', round: 1, boards: 2, categories, cells: cells(used), controller: 'p1',
  dailyDouble: false, buzzOpen: false, locked: [], tried: [], deltas: [], drinks: [], ...over,
})
const stage = (g: object, remainingMs = 18_000): StageInfo => ({ gameId: 'jeopardy', title: 'Answer & Question', phaseSeq: 5, remainingMs, paused: false, game: g as StageInfo['game'] })
const clueText = 'This spread is made by mashing avocados with lime and salt.'
const board = (over: Partial<Extract<Screen, { t: 'board' }>> = {}): Screen => ({
  t: 'board', prompt: 'Pick a clue', categories, cells: cells(used), canPick: true, ...over,
})
const buzzer = (over: Partial<Extract<Screen, { t: 'buzzer' }>>): Screen => ({
  t: 'buzzer', state: 'reading', category: 'Food & Drink', value: 200, lockedMs: 0, live: false, ...over,
})
const beat = (g: object, screen: Screen, remainingMs?: number) => ({ stage: stage(g, remainingMs), scores, phone: { me, screen } })

export default {
  players,
  beats: {
    board: beat(game({ controller: 'p0' }), board()),
    boardwait: beat(game({}), board({ prompt: 'Ben is picking', canPick: false, note: 'Watch the TV' })),
    boardcaptain: beat(game({}), board({ prompt: 'Pick for Ben', pickFor: 'Ben' })),
    intro: beat(game({ phase: 'intro', cells: cells() }), { t: 'waiting', title: 'Here we go', detail: 'Watch the categories come up on the TV' }),
    reading: beat(game({ phase: 'clue', category: 'Food & Drink', value: 200, clue: clueText, active: 'c0-0' }), buzzer({ state: 'reading', detail: 'Wait for the clue to finish' })),
    open: beat(game({ phase: 'buzz', buzzOpen: true, category: 'Food & Drink', value: 200, clue: clueText }), buzzer({ state: 'open', detail: 'Ring in!', live: true })),
    lockedlive: beat(game({ phase: 'buzz', buzzOpen: true, locked: ['p0'], category: 'Food & Drink', value: 200, clue: clueText }), buzzer({ state: 'locked', detail: 'Too early! Hold on...', lockedMs: 700, live: true })),
    beaten: beat(game({ phase: 'answer', floor: 'p2', category: 'Food & Drink', value: 200, clue: clueText }), buzzer({ state: 'beaten', detail: 'Cleo rang in first' })),
    tried: beat(game({ phase: 'buzz', buzzOpen: true, tried: ['p0'], category: 'Food & Drink', value: 200, clue: clueText }), buzzer({ state: 'tried', detail: 'You missed this one', live: true })),
    answer: beat(game({ phase: 'answer', floor: 'p0', category: 'Food & Drink', value: 200, clue: clueText }), { t: 'text', prompt: clueText, maxLen: 60, kind: 'answer', hint: 'Answer as a question, or just say it' }),
    dailydouble: beat(game({ phase: 'wager', controller: 'p0', dailyDouble: true, category: 'Music', value: 600 }), { t: 'number', prompt: 'Daily Double! How much do you wager?', unit: '$', kind: 'wager' }),
    wager: beat(game({ phase: 'wager', controller: 'p0', dailyDouble: true, category: 'Music', value: 600 }), { t: 'number', prompt: 'Daily Double! How much do you wager?', unit: '$', kind: 'wager' }),
    reveal: beat(
      game({
        phase: 'reveal', category: 'Food & Drink', value: 200, clue: clueText, floor: 'p2', tried: ['p1'], answer: 'Guacamole', right: true,
        deltas: [{ id: 'p2', name: 'Cleo', points: 200 }, { id: 'p1', name: 'Ben', points: -200 }],
        drinks: [{ id: 'p1', name: 'Ben', sips: 1, text: 'Drink 1 sip' }],
      }),
      { t: 'waiting', title: 'You got it! +200', detail: 'Guacamole', tone: 'win' }, 4_000,
    ),
    finalwager: beat(
      game({ phase: 'final_wager', round: 3, categories: [], cells: [], final: { category: 'World Capitals', wagers: 2, expected: 4, steps: [] } }),
      { t: 'number', prompt: 'Final Jeopardy: how much do you wager?', unit: '$', kind: 'wager' },
    ),
    finalreveal: beat(
      game({
        phase: 'final_reveal', round: 3, categories: [], cells: [],
        final: {
          category: 'World Capitals', clue: 'This capital on the Danube was formed in 1873 by merging Buda, Obuda and Pest.', answer: 'Budapest', wagers: 3, expected: 3,
          steps: [
            { id: 'p4', name: 'Eli', answer: 'Vienna', wager: 0, right: false, delta: 0, total: -200 },
            { id: 'p1', name: 'Ben', answer: 'What is Budapest?', wager: 500, right: true, delta: 500, total: 1300 },
          ],
        },
      }),
      { t: 'waiting', title: 'Eyes on the TV', detail: 'Final answers are coming up, lowest score first' },
      3_000,
    ),
  },
}
```

In `controller/src/tv/ThemeGallery.tsx` add `import jeopardy from './fixtures/jeopardy-theme'` and change the `raw` line to also include `jeopardy` (`const raw = { turf, sprawl, blackjack, bluff, writeitdown, imposter, jeopardy } as unknown ...`).

- [ ] **Step 8: Run everything**

Run: `cd controller && npx tsc -b && npm test && npm run build && npx playwright test e2e/jeopardy-gallery.spec.ts --reporter=line`
Expected: PASS, 10 tests. If the contrast test fails, the note text is not picking up the theme's `.muted` rule: check that `data-game-theme` reaches the phone page (`Play.tsx`/gallery set it via `gameThemeOf`).

- [ ] **Step 9: Commit**

```bash
git add controller/src/screens controller/src/theme controller/src/tv/fixtures/jeopardy-theme.ts controller/src/tv/ThemeGallery.tsx controller/e2e/jeopardy-gallery.spec.ts
git commit -m "feat(jeopardy): phone board and BUZZ button in the Studio theme"
```

### Task 8: The TV stage in the Studio set

**Files:**
- Modify: `controller/src/tv/types.ts` (replace the Jeopardy types)
- Rewrite: `controller/src/tv/JeopardyStage.tsx`
- Create: `controller/src/tv/JeopardyBoardView.tsx`, `controller/src/tv/JeopardyClueView.tsx`, `controller/src/tv/JeopardyFinalView.tsx`, `controller/src/tv/jeopardy.css`
- Modify: `controller/src/tv/TvPage.tsx` (route through `GameScene`), `controller/src/tv/ThemeGallery.tsx` (TV case)
- Test: `controller/e2e/jeopardy-gallery.spec.ts` (add a TV section)

**Interfaces:**
- Consumes: the gallery fixture and `jeopardy` theme (Task 7); `GameHeader`, `Fill`, `Podium`, `Tutorial` from `./Shared`; `AvatarFace`, `Burst`, `Bubble`, `C`, `Panel`, `Pop`, `Slam` from `./toon`; `useTimerScale`; `HostCommand` from `../protocol`.
- Produces: `JeopardyStage({ stage, players, scores, clock, cmd })`; the TS types `JeopardyTv`, `JeopardyCellTv`, `JeopardyFinalTv`, `JeopardyFinalStep`; CSS classes the specs use: `.jeo-head`, `.jeo-cell`, `.jeo-note`, `.jeo-clue`, `.jeo-buzz`, `.jeo-answer`, `.jeo-delta`, `.jeo-chip`, `.jeo-step`.

- [ ] **Step 1: Write the failing TV checks**

Append to `controller/e2e/jeopardy-gallery.spec.ts`:

```ts
test.describe('the TV stage', () => {
  const fits = async (el: Locator) => {
    const b = (await el.boundingBox())!
    expect(b.x).toBeGreaterThanOrEqual(0)
    expect(b.y).toBeGreaterThanOrEqual(0)
    expect(b.x + b.width).toBeLessThanOrEqual(1280.5)
    expect(b.y + b.height).toBeLessThanOrEqual(720.5)
  }
  const tvPage = async (page: import('@playwright/test').Page, beat: string) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery(beat))
    await page.waitForTimeout(1400) // tiles settle
  }

  test('the board shows five categories, 25 squares and who has it, all on screen', async ({ page }) => {
    await tvPage(page, 'board')
    await expect(page.locator('.jeo-head')).toHaveCount(5)
    await expect(page.locator('.jeo-cell')).toHaveCount(25)
    await expect(page.locator('.jeo-cell.used')).toHaveCount(2)
    for (const el of await page.locator('.jeo-head, .jeo-cell').all()) await fits(el)
    await expect(page.getByText('Ana has the board')).toBeVisible()
    expect(await brightness(page.locator('.jeo-note').first()), 'the pick hint is too dark on the stage').toBeGreaterThan(0.6)
  })

  test('the scoreboard strip shows everyone, the board holder and negative scores', async ({ page }) => {
    await tvPage(page, 'board')
    await expect(page.locator('.jeo-chip')).toHaveCount(6)
    await expect(page.locator('.jeo-chip.holds')).toHaveCount(1)
    await expect(page.locator('.jeo-chip', { hasText: 'Fay' })).toContainText('-400')
    for (const el of await page.locator('.jeo-chip').all()) await fits(el)
  })

  test('the clue is big, read-only until phones can ring in, then BUZZ IN pulses', async ({ page }) => {
    await tvPage(page, 'reading')
    await expect(page.locator('.jeo-clue')).toContainText('mashing avocados')
    await fits(page.locator('.jeo-clue'))
    await expect(page.locator('.jeo-buzz')).toHaveCount(0)
    await tvPage(page, 'open')
    await expect(page.locator('.jeo-buzz')).toContainText('BUZZ IN')
  })

  test('a Daily Double is announced and the clue stays hidden until the wager', async ({ page }) => {
    await tvPage(page, 'dailydouble')
    await expect(page.getByText('DAILY DOUBLE', { exact: false }).first()).toBeVisible()
    await expect(page.getByText('Ana is placing a wager')).toBeVisible()
    await expect(page.locator('.jeo-clue')).toHaveCount(0)
  })

  test('the reveal shows the answer, who scored or lost, and the drink calls', async ({ page }) => {
    await tvPage(page, 'reveal')
    await expect(page.locator('.jeo-answer')).toContainText('Guacamole')
    await expect(page.locator('.jeo-delta', { hasText: 'Cleo' })).toContainText('+200')
    await expect(page.locator('.jeo-delta', { hasText: 'Ben' })).toContainText('-200')
    await expect(page.getByText('Drink 1 sip')).toBeVisible()
    for (const el of await page.locator('.jeo-answer, .jeo-delta').all()) await fits(el)
  })

  test('Final Jeopardy counts the bets in and reveals answers from the lowest score up', async ({ page }) => {
    await tvPage(page, 'finalwager')
    await expect(page.getByText('World Capitals')).toBeVisible()
    await expect(page.getByText('2/4')).toBeVisible()
    await tvPage(page, 'finalreveal')
    await expect(page.locator('.jeo-step')).toHaveCount(2)
    await expect(page.locator('.jeo-step').first()).toContainText('Eli')
    await expect(page.getByText('Budapest').first()).toBeVisible()
    for (const el of await page.locator('.jeo-step').all()) await fits(el)
  })
})
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd controller && npx playwright test e2e/jeopardy-gallery.spec.ts -g "the TV stage" --reporter=line`
Expected: FAIL (the gallery has no `jeopardy` TV case, and the old stage does not use the new payload).

- [ ] **Step 3: Replace the TV types**

In `controller/src/tv/types.ts` replace the old block, from `export interface JeopardyCellTv { id: string; category: string; value: number; used: boolean }` through the closing `}` of `export interface JeopardyTv`, with:

```ts
export interface JeopardyCellTv { id: string; col: number; row: number; value: number; used: boolean }
export interface JeopardyFinalStep { id: string; name: string; answer?: string; wager: number; right: boolean; delta: number; total: number }
export interface JeopardyFinalTv { category: string; clue?: string; answer?: string; wagers: number; expected: number; steps: JeopardyFinalStep[] }
export interface JeopardyTv {
  t: 'jeopardy'
  phase: 'intro' | 'pick' | 'wager' | 'clue' | 'buzz' | 'answer' | 'reveal' | 'break' | 'final_category' | 'final_wager' | 'final_answer' | 'final_reveal' | 'podium'
  round: number; boards: number
  categories: string[]; cells: JeopardyCellTv[]
  controller?: string
  category?: string; value?: number; clue?: string
  dailyDouble: boolean; wager?: number
  buzzOpen: boolean; floor?: string; locked: string[]; tried: string[]
  answer?: string; right?: boolean
  deltas: { id: string; name: string; points: number }[]
  drinks: { id: string; name: string; sips: number; text: string }[]
  final?: JeopardyFinalTv
}
```

- [ ] **Step 4: Write the stage**

Replace `controller/src/tv/JeopardyStage.tsx`:

```tsx
import type { HostCommand, PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { Fill, GameHeader, Podium, Tutorial } from './Shared'
import { useTimerScale } from './timerScale'
import { AvatarFace, C } from './toon'
import { JeopardyBoardView } from './JeopardyBoardView'
import { JeopardyClueView } from './JeopardyClueView'
import { JeopardyFinalView } from './JeopardyFinalView'
import type { JeopardyTv } from './types'
import './jeopardy.css'

type Clock = { deadline: number | null; frozen: number | null }

/** How long each phase lasts, for the clock face. Decision phases stretch with the lobby's timer setting. */
const PHASE_MS: Record<string, number> = {
  intro: 8_000, pick: 20_000, wager: 20_000, clue: 5_000, buzz: 10_000, answer: 15_000, reveal: 5_000, break: 8_000,
  final_category: 6_000, final_wager: 30_000, final_answer: 30_000, final_reveal: 5_000, podium: 15_000,
}
const DECISIONS = new Set(['pick', 'wager', 'buzz', 'answer', 'final_wager', 'final_answer'])

export function JeopardyStage({ stage, players, scores, clock, cmd }: {
  stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock; cmd: (c: HostCommand) => void
}) {
  const scale = useTimerScale()
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="Answer & Question" stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.paper]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} />
      </div>
    )
  }
  const g = stage.game as unknown as JeopardyTv
  const chip = g.round === 3 ? 'FINAL JEOPARDY' : g.boards === 2 ? (g.round === 1 ? 'JEOPARDY!' : 'DOUBLE JEOPARDY!') : 'THE BOARD'
  const base = g.dailyDouble && g.phase === 'answer' ? 30_000 : PHASE_MS[g.phase] ?? 5_000
  const total = base * (DECISIONS.has(g.phase) ? scale : 1)
  const status = g.phase === 'final_wager' && g.final ? `${g.final.wagers}/${g.final.expected} BETS IN`
    : g.phase === 'final_answer' && g.final ? `${g.final.wagers}/${g.final.expected} ANSWERS IN` : null
  const view =
    g.phase === 'podium' ? <Podium scores={scores} />
      : g.phase.startsWith('final') ? <JeopardyFinalView g={g} players={players} />
        : ['wager', 'clue', 'buzz', 'answer', 'reveal'].includes(g.phase) ? <JeopardyClueView g={g} players={players} />
          : <JeopardyBoardView g={g} players={players} cmd={cmd} />
  return (
    <div className="stage-pad jeo">
      <GameHeader title="Answer & Question" stage={stage} total={total} clock={clock} chips={[[chip, C.paper]]} status={status} />
      <Fill>{view}</Fill>
      {g.phase !== 'podium' && <Scoreboard g={g} players={players} scores={scores} />}
    </div>
  )
}

/** Everyone's score along the bottom: the board holder is marked, the player with the floor lights up. */
function Scoreboard({ g, players, scores }: { g: JeopardyTv; players: PlayerSummary[]; scores: ScoreRow[] }) {
  const score = new Map(scores.map((s) => [s.id, s.score]))
  const crew = players.filter((p) => p.role === 'PLAYER')
  return (
    <div className={`jeo-strip ${crew.length > 8 ? 'many' : ''}`}>
      {crew.map((p) => {
        const n = score.get(p.id) ?? 0
        const cls = [g.controller === p.id && 'holds', g.floor === p.id && 'floor', g.locked.includes(p.id) && 'locked', g.tried.includes(p.id) && 'tried'].filter(Boolean).join(' ')
        return (
          <div key={p.id} className={`jeo-chip ${cls}`}>
            <AvatarFace avatar={p.avatar} size={crew.length > 8 ? 34 : 46} />
            <b>{p.name}</b>
            <span className={`jeo-score ${n < 0 ? 'neg' : ''}`}>{n < 0 ? `-$${-n}` : `$${n}`}</span>
          </div>
        )
      })}
    </div>
  )
}
```

The chip text shows a negative score as `-$400`; the spec above checks for `-400`, which is contained in that string.

- [ ] **Step 5: Write the board view**

Create `controller/src/tv/JeopardyBoardView.tsx`:

```tsx
import { useEffect, useState, type CSSProperties } from 'react'
import type { HostCommand, PlayerSummary } from '../protocol'
import { Burst, C } from './toon'
import type { JeopardyTv } from './types'

const ROWS = 5

/** The wall of squares. During the intro the categories drop in one by one; while picking, the TV keyboard still works. */
export function JeopardyBoardView({ g, players, cmd }: { g: JeopardyTv; players: PlayerSummary[]; cmd(c: HostCommand): void }) {
  const [at, setAt] = useState({ col: 0, row: 0 })
  const cols = g.categories.length
  const picking = g.phase === 'pick'
  const cell = (col: number, row: number) => g.cells.find((c) => c.col === col && c.row === row)
  const holder = players.find((p) => p.id === g.controller)?.name ?? 'Someone'
  useEffect(() => {
    if (!picking) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowUp') setAt((a) => ({ ...a, row: Math.max(0, a.row - 1) }))
      else if (e.key === 'ArrowDown') setAt((a) => ({ ...a, row: Math.min(ROWS - 1, a.row + 1) }))
      else if (e.key === 'ArrowLeft') setAt((a) => ({ ...a, col: Math.max(0, a.col - 1) }))
      else if (e.key === 'ArrowRight') setAt((a) => ({ ...a, col: Math.min(cols - 1, a.col + 1) }))
      else if (e.key === 'Enter') {
        const c = cell(at.col, at.row)
        if (c && !c.used) cmd({ t: 'gameAction', action: `pick:${c.id}` })
      } else return
      e.preventDefault()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })
  return (
    <div className="jeo-boardwrap">
      <div className="jeo-board" data-phase={g.phase} style={{ '--cols': cols } as CSSProperties}>
        {g.categories.map((name, col) => (
          <div key={name} className="jeo-head" style={{ '--i': col } as CSSProperties}><span>{name}</span></div>
        ))}
        {Array.from({ length: ROWS }, (_, row) => g.categories.map((_, col) => {
          const c = cell(col, row)
          if (!c) return <div key={`${col}-${row}`} />
          const active = picking && at.col === col && at.row === row
          return (
            <div key={c.id} className={`jeo-cell ${c.used ? 'used' : ''} ${active ? 'cursor' : ''}`} style={{ '--i': col + row } as CSSProperties}>
              <span>{c.used ? '' : `$${c.value}`}</span>
            </div>
          )
        }))}
      </div>
      {picking && (
        <p className="jeo-note"><b>{holder}</b> has the board. Pick on your phone. <small>Arrow keys and Enter work here too.</small></p>
      )}
      {g.phase === 'intro' && <p className="jeo-note">{g.round === 2 ? 'Double Jeopardy! Every clue is worth double.' : 'Here are your categories.'}</p>}
      {g.phase === 'break' && (
        <div className="jeo-break">
          <Burst text="DOUBLE JEOPARDY!" sub="Every clue is worth double" fill={C.sun} ink={C.ink} width={1000} height={420} size={96} tilt={-3} />
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 6: Write the clue view**

Create `controller/src/tv/JeopardyClueView.tsx`:

```tsx
import type { PlayerSummary } from '../protocol'
import { Burst, C, Panel, Pop, Slam } from './toon'
import type { JeopardyTv } from './types'

const signed = (n: number) => `${n > 0 ? '+' : ''}${n}`

/** From the Daily Double wager to the reveal: the clue, who has the floor, and the verdict. */
export function JeopardyClueView({ g, players }: { g: JeopardyTv; players: PlayerSummary[] }) {
  const name = (id?: string) => players.find((p) => p.id === id)?.name ?? 'Someone'
  return (
    <div className="jeo-cluewrap">
      <div className="jeo-cat">
        <Pop><span>{g.category}</span></Pop>
        <b>${g.value}</b>
        {g.dailyDouble && <em>DAILY DOUBLE{g.wager != null ? ` · WAGER $${g.wager}` : ''}</em>}
      </div>
      {g.phase === 'wager' ? (
        <div className="jeo-dd">
          <Burst text="DAILY DOUBLE" fill={C.sun} ink={C.ink} width={900} height={380} size={104} tilt={-4} />
          <p className="jeo-note"><b>{name(g.controller)}</b> is placing a wager</p>
        </div>
      ) : (
        <>
          <Slam from={1.25} tilt={-1} style={{ width: '100%' }}>
            <Panel className="jeo-clue" fill={C.paper} tilt={0}>{g.clue}</Panel>
          </Slam>
          {g.phase === 'clue' && <p className="jeo-note">{g.dailyDouble ? `${name(g.controller)}, this one is all yours` : 'Get ready to ring in...'}</p>}
          {g.phase === 'buzz' && (
            <div className="jeo-buzzrow">
              <span className="jeo-buzz">BUZZ IN!</span>
              {g.tried.length > 0 && <p className="jeo-note">Missed: {g.tried.map(name).join(', ')}</p>}
            </div>
          )}
          {g.phase === 'answer' && <p className="jeo-note"><b>{name(g.floor)}</b> {g.dailyDouble ? 'is answering' : 'rang in first'}</p>}
          {g.phase === 'reveal' && (
            <div className="jeo-revealrow">
              <Panel className="jeo-answer" fill={g.right ? C.lime : C.sun} tilt={-1}>{g.answer}</Panel>
              <p className="jeo-note">{g.right ? 'Got it!' : 'Nobody got it'}</p>
              <div className="jeo-deltas">
                {g.deltas.map((d) => (
                  <Pop key={d.id}><span className={`jeo-delta ${d.points < 0 ? 'neg' : ''}`}>{d.name} {signed(d.points)}</span></Pop>
                ))}
              </div>
              {g.drinks.length > 0 && <ul className="jeo-drinks">{g.drinks.map((d) => <li key={d.id}><b>{d.name}</b> {d.text}</li>)}</ul>}
            </div>
          )}
        </>
      )}
    </div>
  )
}
```

- [ ] **Step 7: Write the Final Jeopardy view**

Create `controller/src/tv/JeopardyFinalView.tsx`:

```tsx
import type { PlayerSummary } from '../protocol'
import { AvatarFace, Burst, C, Deal, Panel } from './toon'
import type { JeopardyTv } from './types'

const signed = (n: number) => `${n > 0 ? '+' : ''}${n}`

/** The category card, the secret-bet wait, the think time, and the lowest-score-first reveal. */
export function JeopardyFinalView({ g, players }: { g: JeopardyTv; players: PlayerSummary[] }) {
  const f = g.final!
  const who = new Map(players.map((p) => [p.id, p]))
  return (
    <div className="jeo-finalwrap">
      <Burst text="FINAL JEOPARDY" fill={C.sun} ink={C.ink} width={880} height={300} size={84} tilt={-3} />
      <p className="jeo-final-cat">{f.category}</p>
      {g.phase === 'final_category' && <p className="jeo-note">Get your bets ready</p>}
      {g.phase === 'final_wager' && <p className="jeo-note">Place your bet on your phone. {f.wagers}/{f.expected} in</p>}
      {(g.phase === 'final_answer' || g.phase === 'final_reveal') && f.clue && <Panel className="jeo-clue small" fill={C.paper} tilt={0}>{f.clue}</Panel>}
      {g.phase === 'final_answer' && <p className="jeo-note">Think! {f.wagers}/{f.expected} answers in</p>}
      {g.phase === 'final_reveal' && (
        <>
          <p className="jeo-note">The answer: <b>{f.answer}</b></p>
          <div className="jeo-steps">
            {f.steps.map((s, i) => {
              const p = who.get(s.id)
              return (
                <Deal key={s.id} i={i}>
                  <div className={`jeo-step ${s.right ? 'right' : 'wrong'}`}>
                    {p && <AvatarFace avatar={p.avatar} size={52} />}
                    <b>{s.name}</b>
                    <span className="ans">{s.answer ?? 'no answer'}</span>
                    <span className="bet">bet ${s.wager}</span>
                    <strong>{signed(s.delta)}</strong>
                    <span className="tot">${s.total}</span>
                  </div>
                </Deal>
              )
            })}
          </div>
        </>
      )}
    </div>
  )
}
```

- [ ] **Step 8: Style the stage**

Create `controller/src/tv/jeopardy.css`:

```css
/* Answer & Question on the TV: the Studio set. Indigo stage, blue tiles with gold prices, cream clue cards. */
.jeo { display: flex; flex-direction: column; gap: 16px; }
.jeo-boardwrap, .jeo-cluewrap, .jeo-finalwrap { flex: 1; display: flex; flex-direction: column; gap: 18px; min-height: 0; }
.jeo-cluewrap, .jeo-finalwrap { align-items: center; justify-content: center; text-align: center; }

.jeo-board { flex: 1; display: grid; grid-template-columns: repeat(var(--cols, 5), minmax(0, 1fr)); grid-template-rows: auto repeat(5, minmax(0, 1fr)); gap: 12px; min-height: 0; }
.jeo-head {
  display: grid; place-items: center; min-height: 92px; padding: 10px 8px; text-align: center;
  background: #232a86; color: var(--paper); border: 4px solid var(--ink); border-radius: 14px; box-shadow: 0 6px 0 var(--ink);
  font: 400 34px/1.05 var(--font-display); letter-spacing: .04em; text-transform: uppercase;
  animation: jeo-drop .5s both; animation-delay: calc(var(--i, 0) * .9s);
}
.jeo-cell {
  display: grid; place-items: center; background: #2f3fb5; border: 4px solid var(--ink); border-radius: 14px; box-shadow: 0 6px 0 var(--ink);
  color: var(--game-accent); font: 400 58px var(--font-display); text-shadow: 0 3px 0 var(--ink);
}
.jeo-cell.used { background: transparent; border-style: dashed; box-shadow: none; opacity: .3; }
.jeo-cell.cursor { outline: 6px solid var(--paper); outline-offset: 3px; background: #4356e0; transform: translateY(-3px); }
[data-phase='intro'] .jeo-cell { animation: jeo-fade .6s both; animation-delay: calc(4.5s + var(--i, 0) * .05s); }
@keyframes jeo-drop { from { transform: translateY(-40px); opacity: 0; } to { transform: none; opacity: 1; } }
@keyframes jeo-fade { from { opacity: 0; } to { opacity: 1; } }

.jeo-note { font: 700 32px/1.3 var(--font-body); color: var(--paper); text-align: center; max-width: 1500px; }
.jeo-note b { color: var(--game-accent); font-weight: 900; }
.jeo-note small { display: block; margin-top: 4px; font-size: 24px; opacity: .8; }
.jeo-break { position: absolute; inset: 0; display: grid; place-items: center; background: #1b1f57e6; }

.jeo-cat { display: flex; align-items: baseline; justify-content: center; gap: 22px; flex-wrap: wrap; color: var(--game-accent); }
.jeo-cat span { font: 400 46px var(--font-display); letter-spacing: .05em; text-transform: uppercase; }
.jeo-cat b { font: 400 56px var(--font-display); color: var(--paper); }
.jeo-cat em { font: 800 30px var(--font-body); font-style: normal; letter-spacing: .1em; background: var(--game-accent); color: var(--ink); padding: 4px 16px; border-radius: 10px; }
.jeo-clue { width: 100%; max-width: 1640px; padding: 52px 64px; font: 800 62px/1.2 var(--font-body); color: var(--ink); text-align: center; }
.jeo-clue.small { font-size: 44px; padding: 32px 48px; }
.jeo-buzzrow, .jeo-revealrow, .jeo-dd { display: flex; flex-direction: column; align-items: center; gap: 14px; }
.jeo-buzz { font: 400 120px var(--font-display); letter-spacing: .06em; color: var(--game-accent); text-shadow: 0 6px 0 var(--ink); animation: jeo-pulse .9s ease-in-out infinite; }
@keyframes jeo-pulse { 50% { transform: scale(1.06); } }
.jeo-answer { padding: 24px 60px; font: 400 76px var(--font-display); color: var(--ink); text-transform: uppercase; }
.jeo-deltas { display: flex; flex-wrap: wrap; justify-content: center; gap: 12px 20px; }
.jeo-delta { font: 900 38px var(--font-body); background: var(--paper); color: var(--ink); padding: 6px 22px; border: 4px solid var(--ink); border-radius: 12px; box-shadow: 0 5px 0 var(--ink); }
.jeo-delta.neg { background: var(--tomato); color: var(--white); }
.jeo-drinks { list-style: none; display: flex; flex-wrap: wrap; justify-content: center; gap: 10px 28px; padding: 0; margin: 0; }
.jeo-drinks li { font: 700 30px var(--font-body); color: var(--paper); }
.jeo-drinks b { color: var(--game-accent); }

.jeo-final-cat { font: 400 84px var(--font-display); letter-spacing: .05em; text-transform: uppercase; color: var(--paper); }
.jeo-steps { display: flex; flex-direction: column; gap: 10px; width: 100%; max-width: 1500px; }
.jeo-step { display: grid; grid-template-columns: 60px 200px 1fr 180px 130px 170px; align-items: center; gap: 14px; padding: 8px 18px; background: var(--paper); color: var(--ink); border: 4px solid var(--ink); border-radius: 14px; font: 800 30px var(--font-body); text-align: left; }
.jeo-step.right strong { color: #1f7a2e; }
.jeo-step.wrong strong { color: #b3261e; }
.jeo-step .ans { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.jeo-strip { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px 14px; }
.jeo-chip { display: flex; align-items: center; gap: 10px; padding: 6px 16px 6px 8px; background: var(--paper); color: var(--ink); border: 4px solid var(--ink); border-radius: 16px; box-shadow: 0 4px 0 var(--ink); font: 800 28px var(--font-body); }
.jeo-chip.holds { outline: 5px solid var(--game-accent); outline-offset: 2px; }
.jeo-chip.floor { background: var(--lime); }
.jeo-chip.locked { background: var(--tomato); color: var(--white); }
.jeo-chip.tried { opacity: .6; }
.jeo-strip.many .jeo-chip { font-size: 20px; padding: 3px 10px 3px 5px; gap: 6px; }
.jeo-score.neg { color: #b3261e; }
.jeo-chip.locked .jeo-score.neg { color: var(--white); }
```

- [ ] **Step 9: Route the stage and add the gallery case**

In `controller/src/tv/TvPage.tsx` replace the line

```tsx
                : stage.gameId === 'jeopardy' ? <JeopardyStage stage={stage} players={players} scores={tv.scores} clock={clock} cmd={cmd} />
```

with

```tsx
                : stage.gameId === 'jeopardy' ? <GameScene game="jeopardy"><JeopardyStage stage={stage} players={players} scores={tv.scores} clock={clock} cmd={cmd} /></GameScene>
```

In `controller/src/tv/ThemeGallery.tsx` add `import { JeopardyStage } from './JeopardyStage'` and, in the `Stage` switch, above `case 'imposter':` add:

```tsx
    case 'jeopardy': return <GameScene game="jeopardy"><JeopardyStage {...props} cmd={() => undefined} /></GameScene>
```

- [ ] **Step 10: Run everything**

Run: `cd controller && npx tsc -b && npm test && npm run build && npx playwright test e2e/jeopardy-gallery.spec.ts --reporter=line`
Expected: PASS (all 16 gallery tests). If a `fits` assertion fails, look at the screenshot the failure attaches; the usual causes are a clue that wraps to four lines (lower `.jeo-clue` font size) or a category header that wraps three lines (lower `.jeo-head` font size).

- [ ] **Step 11: Commit**

```bash
git add controller/src/tv controller/e2e/jeopardy-gallery.spec.ts
git commit -m "feat(jeopardy): Studio-set TV stage with board, clue, Daily Double and Final Jeopardy"
```

---

### Task 9: Short or Full from the lobby

**Files:**
- Modify: `controller/src/tv/TvPage.tsx` (the `Lobby` state, key `S`, the controls row)
- Modify: `controller/src/pages/Captain.tsx` (the "Show" stepper)
- Test: `controller/e2e/jeopardy-lobby.spec.ts` (new)

**Interfaces:**
- Consumes: the `show` option in the engine (Task 2) and in `OptionKey` (Task 6).
- Produces: TV lobby key `S` toggling Short/Full while Answer & Question is focused; the same setting as a "Show" stepper on the captain's phone; `D` (drink calls) and the Timers setting also apply to this game; the Rounds stepper is hidden for it.

- [ ] **Step 1: Write the failing test**

Create `controller/e2e/jeopardy-lobby.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

test('the lobby lets the TV and the captain choose a Short or Full show, with no Rounds setting', async ({ browser }) => {
  test.setTimeout(90_000)
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const captain = await phone(browser, room, 'Ana')
  await phone(browser, room, 'Bo')
  const tv = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage()
  await tv.goto('/tv')
  await tv.mouse.click(960, 540) // dismiss the GO LIVE gate

  await captain.getByRole('radio', { name: /Answer & Question/ }).click()
  const controls = tv.locator('.controls-row')
  await expect(controls).toContainText('Show')
  await expect(controls).toContainText('SHORT')
  await expect(controls).not.toContainText('Rounds')

  await tv.keyboard.press('s')
  await expect(controls).toContainText('FULL')
  await expect(captain.locator('.setting-row', { hasText: 'Show' })).toContainText('Full')

  await captain.getByRole('button', { name: 'Less Show' }).click()
  await expect(controls).toContainText('SHORT')
  await captain.getByRole('button', { name: 'More Show' }).click()
  await expect(controls).toContainText('FULL')
  await expect(captain.locator('.setting-row', { hasText: 'Drink calls' })).toBeVisible()
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd controller && npx playwright test e2e/jeopardy-lobby.spec.ts --reporter=line`
Expected: FAIL (there is no Show setting for this game, and a Rounds stepper is shown).

- [ ] **Step 3: Implement on the TV**

In `controller/src/tv/TvPage.tsx` make these edits.

1. `interface Lobby { rounds: number; teams: number; drinks: boolean; game: number; phones: boolean; turfMode: number; minutes: number; vp: number; timers: number }` → append `; show: number` before the closing `}`.
2. In `lobbyOf`, after `timers: s.timers ?? 0,` add `show: s.show ?? 0,`.
3. After `const sprawl = focused?.id === 'sprawl'` add `const jeopardy = focused?.id === 'jeopardy'`.
4. Replace the two ArrowUp/ArrowDown lines with:

```tsx
      else if (e.key === 'ArrowUp' && focused?.id !== 'blackjack' && !jeopardy) { setOption('rounds', Math.min(8, lobby.rounds + 1)); sfx.focus() }
      else if (e.key === 'ArrowDown' && focused?.id !== 'blackjack' && !jeopardy) { setOption('rounds', Math.max(3, lobby.rounds - 1)); sfx.focus() }
```

5. Above `      else if (k === 'r') {` add `      else if (k === 's' && jeopardy) { setOption('show', lobby.show ? 0 : 1); sfx.focus() }`.
6. Change `else if (k === 'd' && (trivia || turf || sprawl))` to `else if (k === 'd' && (trivia || turf || sprawl || jeopardy))`.
7. In the controls row, replace

```tsx
              : turf || sprawl ? <span className="stepper"><Keycap label="↑" /><Keycap label="↓" /> Game clock <b>{turfMinutesText(lobby.minutes)}</b></span>
```

with

```tsx
              : turf || sprawl ? <span className="stepper"><Keycap label="↑" /><Keycap label="↓" /> Game clock <b>{turfMinutesText(lobby.minutes)}</b></span>
              : jeopardy ? <span className="stepper"><Keycap label="S" /> Show <b>{lobby.show ? 'FULL' : 'SHORT'}</b></span>
```

8. Change `{(trivia || turf || sprawl) && <span className="stepper"><Keycap label="D" /> Drink calls` to `{(trivia || turf || sprawl || jeopardy) && <span className="stepper"><Keycap label="D" /> Drink calls`.

- [ ] **Step 4: Implement on the captain's phone**

In `controller/src/pages/Captain.tsx`: in `settingsOf` add `show: s.show ?? 0,` after `timers: s.timers ?? 0,`; after `const sprawl = game?.id === 'sprawl'` add `const jeopardy = game?.id === 'jeopardy'`; directly above the line `          : <Stepper label={trivia ? 'Questions per round' : 'Rounds'} value={String(s.rounds)}` add:

```tsx
          : jeopardy ? <Stepper label="Show" value={s.show ? 'Full' : 'Short'} onDown={() => set('show', 0)} onUp={() => set('show', 1)} />
```

and change `{(trivia || turf || sprawl) && (` to `{(trivia || turf || sprawl || jeopardy) && (`.

- [ ] **Step 5: Run everything**

Run: `cd controller && npx tsc -b && npm test && npm run build && npx playwright test e2e/jeopardy-lobby.spec.ts e2e/lobby.spec.ts --reporter=line`
Expected: PASS (the new lobby spec and the existing four-size lobby layout spec).

- [ ] **Step 6: Commit**

```bash
git add controller/src/tv/TvPage.tsx controller/src/pages/Captain.tsx controller/e2e/jeopardy-lobby.spec.ts
git commit -m "feat(jeopardy): Short or Full show as a lobby setting on the TV and the captain's phone"
```

### Task 10: Sixteen flaky bots play a whole Short show

**Files:**
- Create: `tv/server/src/test/kotlin/partyos/server/JeopardySimulatedPartyTest.kt`

**Interfaces:**
- Consumes: `Jeopardy`, `JeopardyPack.core()` (Task 2), `JeopardyTv`/`JeopardyDelta`/`JeopardyFinalStep` (Task 2), `Screen.Board`/`Screen.Buzzer`/`Screen.TextEntry`/`Screen.NumberEntry` (Tasks 2 and existing), the `show` option (Task 2), and the `ImposterSimulatedPartyTest` structure (copy its setup, `join`, `obj`, `Bot` shell).
- Produces: nothing later tasks use.

- [ ] **Step 1: Write the test**

Copy `tv/server/src/test/kotlin/partyos/server/ImposterSimulatedPartyTest.kt` to `JeopardySimulatedPartyTest.kt`, rename the class to `JeopardySimulatedPartyTest`, swap the imports `partyos.engine.ImposterTv` and `partyos.engine.games.imposter.Imposter` for `partyos.engine.JeopardyTv` and `partyos.engine.games.jeopardy.Jeopardy` and `partyos.engine.games.jeopardy.JeopardyPack`, construct the engine with `GameRegistry(listOf(Jeopardy()))`, and replace the `Bot`'s payload `when` and the `@Test` with the following.

The payload `when` inside `Bot.run` (the bots know the pack, answer right about six times in ten, and buzz on about two thirds of the clues they see open, so lockouts, misses and reopened buzzers all happen):

```kotlin
                        val payload: JsonObject? = when (val s = v.screen) {
                            is Screen.Tutorial -> if (!s.acknowledged) obj("kind" to "ack") else null
                            is Screen.Board -> if (s.canPick) {
                                val open = s.cells.filter { !it.used }
                                if (open.isEmpty()) null else obj("kind" to "pick", "cell" to open.random(rnd).id)
                            } else null
                            is Screen.Buzzer -> if (s.state == "open" && rnd.nextDouble() < 0.67) obj("kind" to "buzz") else null
                            is Screen.NumberEntry -> if (s.value == null) JsonObject(mapOf("kind" to JsonPrimitive(s.kind), "value" to JsonPrimitive(100 * (1 + rnd.nextInt(5))))) else null
                            is Screen.TextEntry -> if (s.value == null) {
                                val known = answers[s.prompt]
                                val text = if (known != null && rnd.nextDouble() < 0.6) "What is $known?" else "nonsense${rnd.nextInt(1000)}"
                                obj("kind" to s.kind, "text" to text)
                            } else null
                            else -> null
                        }
```

(Wagers go out as JSON numbers, as the phone sends them. A wager outside the allowed range is rejected, and the wager deadline then counts the minimum, so the show still finishes.)

Add to the class body, next to `http`:

```kotlin
    /** Every clue text in the pack mapped to its answer, so bots can "know" the right answer. */
    private val answers: Map<String, String> = JeopardyPack.core().let { p ->
        p.categories.flatMap { it.clues }.associate { it.clue to it.answer } + p.finals.associate { it.clue to it.answer }
    }
```

If `JeopardyFinal` in Task 2 names its answer field differently, use that name; the pack test in Task 2 pins the field names.

The test:

```kotlin
    @Test fun sixteenFlakyPhonesFinishAShortShowWithConsistentScores() = runBlocking {
        val bots = (1..16).map { Bot("Bot$it", join("Bot$it"), dropRate = 0.2, seed = it) }
        bots.map { b -> scope.async { b.run() } }
        withTimeout(20_000) { host.tv.first { it.players.count { p -> p.connected } == 16 } }

        // What the TV showed: each clue's reveal (once, by phase sequence) and each Final Jeopardy step (once, by player).
        val reveals = ConcurrentHashMap<Int, JeopardyTv>()
        val finalSteps = ConcurrentHashMap<String, JeopardyFinalStep>()
        val driver = scope.launch {
            var skipped = -1
            host.tv.collect { tv ->
                val st = tv.stage ?: return@collect
                val g = st.game as? JeopardyTv
                if (g != null && g.phase == "reveal") reveals[st.phaseSeq] = g
                if (g != null && g.phase == "final_reveal") g.final?.steps?.forEach { finalSteps[it.id.v] = it }
                if (st.paused && tv.players.count { it.connected } >= 2) host.mutate { host(HostCmd.Resume) }
                // Speed the show up: the intro, the category card and the reveal steps do not need their full time.
                if (g != null && g.phase in setOf("intro", "final_category", "final_reveal", "podium") && st.phaseSeq != skipped) {
                    skipped = st.phaseSeq
                    delay(30)
                    host.mutate { host(HostCmd.SkipPhase) }
                }
            }
        }
        assertEquals(partyos.engine.ActionResult.Ack, host.mutate { host(HostCmd.StartGame("jeopardy", mapOf("show" to 0))) })

        val result = withTimeout(240_000) { host.tv.first { it.lastResult != null }.lastResult!! }
        driver.cancel()
        bots.forEach { it.dropRate = 0.0 }

        // Every bot, once reconnected, converges on the same final scoreboard.
        withTimeout(20_000) {
            while (bots.any { b -> b.last?.let { it.gameId == null && it.scores == result.standings } != true }) delay(50)
        }
        bots.forEach { it.stop = true }
        assertTrue(bots.sumOf { it.sessions } > 16, "expected some reconnects")

        // Scores equal a recomputation from what the TV showed: every clue's deltas plus every Final Jeopardy delta.
        assertTrue(reveals.size >= 10, "the show should have played at least ten clues, saw ${reveals.size}")
        val expected = HashMap<String, Int>()
        reveals.values.forEach { r -> r.deltas.forEach { d -> expected.merge(d.id.v, d.points, Int::plus) } }
        finalSteps.values.forEach { expected.merge(it.id.v, it.delta, Int::plus) }
        val actual = result.standings.associate { it.id.v to it.score }.filterValues { it != 0 }
        assertEquals(expected.filterValues { it != 0 }, actual)
    }
```

- [ ] **Step 2: Run it**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :server:test --tests '*JeopardySimulatedPartyTest*' 2>&1 | tail -40`
Expected: PASS. A Short show plays roughly a dozen clues with 16 bots; the clock scales with the machine, so allow a few minutes.

If it fails on the score comparison, read the assertion diff for one player before changing anything: a delta the TV never showed (a reveal the collector missed because the `StateFlow` conflated two phases) is a test problem, fixed by raising the reveal's visibility time in the test (skip nothing after `reveal`); a delta the TV showed twice would mean two reveals shared a `phaseSeq`, which is an engine bug to debug with `superpowers:systematic-debugging`, not to patch in the test.

- [ ] **Step 3: Run the whole Kotlin suite**

Run: `cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew test 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL. (`PhotoRoutesTest.photosInAFolderOutliveTheServer` is a known pre-existing flake; if it is the only failure, re-run once and report it by name.)

- [ ] **Step 4: Commit**

```bash
git add tv/server/src/test/kotlin/partyos/server/JeopardySimulatedPartyTest.kt
git commit -m "test(jeopardy): sixteen flaky bot phones play a Short show and the scores recompute from the TV"
```

---

### Task 11: A real-phone end to end

**Files:**
- Rewrite: `controller/e2e/jeopardy.spec.ts`

**Interfaces:**
- Consumes: `hostPage`, `clearParty`, `phone` from `./helpers`; the phone UI (Tasks 7): the board's cells are buttons named `<Category> for <value>` with a `Pick <Category> for $<value>` confirm button, the buzzer button contains `BUZZ!`, the answer box is a `textbox` with a `Lock it in` button; the TV (Task 8): `.jeo-head`, `.jeo-cell`, `.jeo-clue`, `.jeo-buzz`, `.jeo-answer`, `.jeo-delta`; the lobby (Task 9). The pack file `tv/engine/src/main/resources/jeopardy-core.json`, read with Node's `fs` to look up answers from clue text.
- Produces: nothing later tasks use.

The captain (first phone to join) starts with the board, and the shared party holds up to three phones, so this test uses three: Ana (captain), Bo and Cy.

- [ ] **Step 1: Replace the spec**

Replace `controller/e2e/jeopardy.spec.ts` entirely (this keeps the contrast helper, because the TV must stay readable on the dark stage):

```ts
import { readFileSync } from 'node:fs'
import { expect, test, type Locator, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

/** Perceived brightness (0-1) of an element's text colour, read through a canvas because Chrome reports `oklch()`. */
const brightness = (el: Locator) => el.evaluate((node) => {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 1
  const g = canvas.getContext('2d')!
  g.fillStyle = getComputedStyle(node).color
  g.fillRect(0, 0, 1, 1)
  const [r, gr, b] = g.getImageData(0, 0, 1, 1).data
  return (0.2126 * r + 0.7152 * gr + 0.0722 * b) / 255
})

/** Every clue in the shipped pack, so the test can type the real answer for whatever the board serves up. */
const pack = JSON.parse(readFileSync('../tv/engine/src/main/resources/jeopardy-core.json', 'utf8')) as {
  categories: { name: string; clues: { id: string; clue: string; answer: string }[] }[]
}
const answerFor = (clue: string) => pack.categories.flatMap((c) => c.clues).find((c) => c.clue === clue)!.answer

/** Pick the first square on the current board from this phone. */
async function pickFirst(page: Page) {
  const cell = page.locator('.jb-cell:not([disabled])').first()
  await expect(cell).toBeVisible()
  await cell.click()
  await page.getByRole('button', { name: /^Pick / }).click()
}

test('Answer & Question: the captain picks from the phone, a wrong buzz costs points, a right one wins the board', async ({ browser }) => {
  test.setTimeout(150_000)
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const ana = await phone(browser, room, 'Ana') // the first phone in holds the crown
  const bo = await phone(browser, room, 'Bo')
  const cy = await phone(browser, room, 'Cy')
  const tv = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage()
  const errors: string[] = []
  tv.on('pageerror', (e) => errors.push(e.message))
  await tv.goto('/tv')
  await tv.mouse.click(960, 540) // dismiss the GO LIVE gate
  await host.getByRole('button', { name: /Answer & Question/ }).click() // the host page starts the game, as the party spec does
  for (const p of [ana, bo, cy]) await p.getByRole('button', { name: 'Ready!' }).click()

  // The board is on the TV; only Ana's phone can pick, and the TV says so in a readable colour.
  await expect(tv.locator('.jeo-head')).toHaveCount(5)
  await expect(tv.locator('.jeo-cell')).toHaveCount(25)
  const hint = tv.locator('.jeo-note').first()
  await expect(hint).toContainText('Ana has the board')
  expect(await brightness(hint), 'the pick hint is too dark on the stage').toBeGreaterThan(0.6)
  await expect(bo.locator('.jb-cell:not([disabled])')).toHaveCount(0)

  await pickFirst(ana)
  const clue = tv.locator('.jeo-clue')
  await expect(clue).toBeVisible()
  const text = (await clue.textContent())!.trim()
  const answer = answerFor(text)

  // Buzzing before the reading is over locks that phone out; Cy waits.
  await expect(cy.locator('.jz-buzz')).toContainText('WAIT')
  await bo.locator('.jz-buzz').click()
  await expect(bo.locator('.jz-buzz')).toContainText('TOO EARLY')

  // Then the buzzers open. Cy buzzes and answers wrong.
  await expect(cy.locator('.jz-buzz')).toContainText('BUZZ!', { timeout: 15_000 })
  await cy.locator('.jz-buzz').click()
  await cy.getByRole('textbox').fill('a completely wrong answer')
  await cy.getByRole('button', { name: /Lock it in/ }).click()

  // A wrong answer costs the value and reopens the buzzers to everyone who has not tried.
  await expect(tv.locator('.jeo-chip', { hasText: 'Cy' })).toContainText('-$', { timeout: 10_000 })
  await expect(ana.locator('.jz-buzz')).toContainText('BUZZ!', { timeout: 10_000 })
  await expect(cy.locator('.jz-buzz')).toContainText('MISSED')
  await ana.locator('.jz-buzz').click()
  await ana.getByRole('textbox').fill(`What is ${answer}?`)
  await ana.getByRole('button', { name: /Lock it in/ }).click()

  // The right answer, given in the show's form, is revealed with who scored and lost.
  await expect(tv.locator('.jeo-answer')).toContainText(answer, { timeout: 10_000 })
  await expect(tv.locator('.jeo-delta', { hasText: 'Ana' })).toContainText('+')
  await expect(tv.locator('.jeo-delta', { hasText: 'Cy' })).toContainText('-')
  const gotIt = tv.getByText('Got it!', { exact: true })
  await expect(gotIt).toBeVisible()
  expect(await brightness(gotIt), 'the verdict is too dark on the stage').toBeGreaterThan(0.6)

  // Ana answered right, so she keeps the board and picks again.
  await expect(tv.locator('.jeo-cell.used')).toHaveCount(1, { timeout: 15_000 })
  await expect(tv.locator('.jeo-chip.holds')).toContainText('Ana')
  await expect(ana.locator('.jb-cell:not([disabled])').first()).toBeVisible()
  expect(errors).toEqual([])
})

test('Answer & Question: the captain can pick when someone else has the board', async ({ browser }) => {
  test.setTimeout(150_000)
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const ana = await phone(browser, room, 'Ana')
  const bo = await phone(browser, room, 'Bo')
  const cy = await phone(browser, room, 'Cy')
  const tv = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage()
  await tv.goto('/tv')
  await tv.mouse.click(960, 540)
  await host.getByRole('button', { name: /Answer & Question/ }).click() // the host page starts the game, as the party spec does
  for (const p of [ana, bo, cy]) await p.getByRole('button', { name: 'Ready!' }).click()

  // Ana holds the board first. Bo buzzes in and answers right, so Bo takes the board.
  await pickFirst(ana)
  const text = (await tv.locator('.jeo-clue').textContent())!.trim()
  await expect(bo.locator('.jz-buzz')).toContainText('BUZZ!', { timeout: 15_000 })
  await bo.locator('.jz-buzz').click()
  await bo.getByRole('textbox').fill(answerFor(text))
  await bo.getByRole('button', { name: /Lock it in/ }).click()
  await expect(tv.locator('.jeo-chip.holds')).toContainText('Bo', { timeout: 15_000 })

  // Bo picks on Bo's own phone, but Ana, the captain, can also pick "for Bo" and the TV moves on either way.
  await expect(bo.locator('.jb-cell:not([disabled])').first()).toBeVisible()
  const cell = ana.locator('.jb-cell:not([disabled])').first()
  await expect(cell).toBeVisible()
  await cell.click()
  await expect(ana.getByRole('button', { name: /\(for Bo\)/ })).toBeVisible()
  await ana.getByRole('button', { name: /^Pick / }).click()
  await expect(tv.locator('.jeo-clue')).toBeVisible()
  await expect(tv.locator('.jeo-cell.used')).toHaveCount(2)
})
```

The second test's final assertion runs while the second clue is on screen: the board (with two used squares) is not visible then, so replace the last line with `await expect(bo.locator('.jz-buzz')).toBeVisible()` if `.jeo-cell.used` is not in the DOM during the clue. Both facts (a clue opened, and it opened because of the captain's pick) are already established by `.jeo-clue` becoming visible after the captain's tap, so keep only that line if the count is not observable.

- [ ] **Step 2: Run it**

Run: `cd controller && npx playwright test e2e/jeopardy.spec.ts --reporter=line`
Expected: PASS (2 tests). The waits are bounded by the game clocks (reading time about 3-8 s plus the answer windows), so expect about a minute for both.

If a selector fails because the phone-side markup differs from Task 7 (for example the Start button label), open the failure screenshot and fix the selector in the spec to match what the phone shows; do not weaken an assertion about behaviour.

- [ ] **Step 3: Run the whole Playwright suite**

Run: `cd controller && npx playwright test --reporter=line`
Expected: PASS (every spec, workers:1). If tests fail only under machine load, re-run the failing spec alone before deciding it is a real failure.

- [ ] **Step 4: Commit**

```bash
git add controller/e2e/jeopardy.spec.ts
git commit -m "test(jeopardy): real phones pick from the board, buzz in early and late, lose points and win the board"
```

---

### Task 12: Look at it, document it, verify everything

**Files:**
- Modify: `README.md` (one paragraph about the game and the lobby setting)
- Verify only: everything else

**Interfaces:**
- Consumes: everything above.
- Produces: the branch, ready to review.

- [ ] **Step 1: Look at every screen in the gallery**

Start the dev server if it is not already running (`preview_start` with the project's dev config, or the existing `npm run dev` on the port the e2e config uses). For each beat listed under Task 7's Produces (`board`, `reading`, `open`, `lockedlive`, `beaten`, `tried`, `wager`, `answer`, `boardcaptain`, `boardwait`, and TV-only `intro`, `clue`, `dailydouble`, `reveal`, `finalwager`, `finalreveal`), open `/tv?gallery=themes&game=jeopardy&beat=<beat>` (add `&view=phone` for the phone beats) at 1280x720 for the TV and 390x844 for the phone, take a screenshot, and look at it. Check against the design taste notes: cartoon, readable at arm's length on a dark stage, no emoji, no glowing bulbs, nothing clipped, nothing stacked under something else, blue tiles with gold prices on the TV and the big cream BUZZ button on the phone.

Fix what is wrong in `jeopardy.css` / `jeopardy-phone.css` / `games.css`, re-run `npx playwright test e2e/jeopardy-gallery.spec.ts`, and commit as `fix(jeopardy): polish from the visual review`. If everything already looks right, write nothing and commit nothing.

- [ ] **Step 2: Document the game**

In `README.md`, find the paragraph or list that describes the games and replace the Answer & Question entry with this text (keeping the surrounding list's style):

```markdown
**Answer & Question** is a Jeopardy-style quiz show. Five categories, five clues each, and whoever last answered right picks the next square from their own phone (the captain can always pick too). Read the clue, wait for the BUZZ button to light up (buzz early and you are locked out for a second), then type your answer; a wrong answer costs the clue's value and reopens the buzzers to everyone else. Watch for Daily Doubles, and finish with a secret-wager Final Jeopardy. In the lobby, choose a **Short** show (one board plus Final Jeopardy) or a **Full** one (with Double Jeopardy), press D for drink calls, and set the answer timers like any other game.
```

- [ ] **Step 3: Full verification**

Run each and read the tail of the output:

```bash
cd tv && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew test 2>&1 | tail -30
cd controller && npx tsc -b && npm test && npm run build
cd controller && npx playwright test --reporter=line
git status --short
```

Expected: Gradle BUILD SUCCESSFUL (the known `PhotoRoutesTest` flake aside, named if it appears); tsc/vitest/build clean; every Playwright spec green; `git status` shows only the README change (and the unrelated `brag-output-*` deletions the user already knows about, which are not to be staged).

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: describe the revamped Answer & Question and its Short or Full setting"
```
