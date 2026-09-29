# Doodle Dash Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Doodle Dash, a Pictionary-style free-for-all drawing game, to Party OS: the drawer draws on their phone, the TV shows the ink live, everyone else races to guess on their phones.

**Architecture:** A new engine module (`games/doodle`) owns turns, words, hints, guesses and scoring, like Imposter. Live ink does NOT travel through game state (that would push a view to all 16 phones per point): phones send `ink` socket messages, the engine only says who may draw (`InkAware`), a server `InkBoard` validates, buffers and relays strokes to the TV sockets, and the TV rebuilds the picture (and replays it as a time-lapse and a gallery). Phone screens `draw` and `guess` are new controller components; the TV gets `DoodleStage` and the "Crayon Studio" theme.

**Tech Stack:** Kotlin + kotlinx.serialization + Ktor (engine/server), React 19 + TypeScript + Vite + Vitest + Playwright (controller), motion (animations), canvas 2D, `PointerEvent.getCoalescedEvents`.

**Spec:** `docs/superpowers/specs/2026-09-29-doodle-dash-design.md`

## Global Constraints

- Game id `doodle`, title **Doodle Dash**, 3-16 players, solo only, `LateJoin.NEXT_ROUND`; number of turns is the `rounds` lobby setting (3-8).
- Phases and lengths: `pick` 12 s, `draw` 75 s (both through `ctx.timer`), `reveal` 7 s, `scores` 6 s, `podium` (this plan: 24 s so the gallery fits, see Refinements).
- Ink grid: integers on 0-1000 by 0-750 (4:3); pressure 0-100; 8 colours (index 0-7); 3 brushes (index 0-2). Server limits: at most 400 numbers per `pts` op, 64 ops per message, 200 strokes and 40 000 points per turn. Ink has its own token bucket (not the action bucket).
- Phones never receive ink; only host (TV) sockets do.
- The word is never in the TV payload or any guesser's view before `reveal`; the drawer's phone shows it for the whole draw phase.
- Drink calls are game text worded through `ofWater(water)`, switched off by the lobby `drinks` setting (default on).
- Design taste (user memory): no emoji, no bulb frames, no synthesised music; cartoon style like the other games; large readable text (readable when drunk).
- Words are family-friendly, one to three words, letters and spaces only, no proper names.
- Commit messages: conventional style (`feat(doodle): ...`), **no attribution lines**.
- Run Gradle with `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` from `tv/`. Run Vitest from `controller/` (`npx vitest run`; it only includes `src/**/*.test.ts`, node environment, so keep tested logic in `.ts` files, not `.tsx`).

## Refinements to the spec (decided while reading the code; Task 0 writes these into the spec)

1. **Default turns is 5, not 6.** The lobby shows 5 when `rounds` is unset, so a different engine default would disagree with the TV.
2. **New `Screen.Guess`.** The spec said guessing reuses the text-entry screen, but its "Lock it in / Update answer" flow does not fit rapid-fire guessing (clear the field and go again, show "so close", show your last miss). `Screen.Draw` (drawer) and `Screen.Guess` (guessers) are both new.
3. **"So close" rule:** a wrong guess is "close" when its normalised edit distance to the word is at most `len / 3 + 1` (the fuzzy matcher already accepts small typos, so "one edit away" would almost never fire).
4. **Hint stages via `Effect.Deadline`.** Views only re-push on a state change, so hints reveal at the stage deadlines (40% / 70% of the draw time). `phaseSeq` does not change during a draw, so in-flight guesses stay valid. The phone and TV clocks add `tailMs` (time in later stages) to the stage countdown. Host "Skip" during a draw advances one stage (a third skip ends the draw).
5. **Ink archive is keyed by turn number, not phase.** `InkAware.inkTurn` returns the turn; `InkBoard` keeps every turn's strokes until the game ends (for TV reconnects, the time-lapse and the gallery). Each accepted batch gets a server sequence `n` so a reconnecting TV can dedupe (`inkSync.upTo`).
6. **Word pack format** lists words per difficulty inside each category (`easy` / `medium` / `hard`) instead of one item per word: same data, far less JSON.
7. **Podium** lasts 24 s: the podium blocks for 11 s, then the gallery of the night's drawings.
8. **Rejection code `NOT_NOW`** replaces the spec's `WRONG_PHASE`, matching every other game.

## Review Focus

The spec is silent on these; a person at the party would hit them. Each line has a test in the task that owns the code.

1. **The drawer's phone drops, locks or reloads mid-draw.** The turn must still end (early when every connected guesser has it, else at the deadline) and never hang; a reloaded drawer pad starts blank and tells the TV to clear so both agree. (Task 4 away-drawer test; Task 8 `DrawPad` mount clear.)
2. **A guess in odd shapes:** `"  PIZZA "`, `"the pizza"`, a typo, blank, 41+ characters, or a guess after you already got it. Right ones count once, blank/long ones are `BAD_TEXT`, repeats are `NOT_GUESSING`. (Task 4.)
3. **A TV that reloads or connects late mid-turn** must show the exact picture so far, with no doubled strokes when live batches race the sync. (Task 5 `InkBoard.sync`/`upTo`, Task 9 `InkStore`.)
4. **Undo and clear ordering** must give the same picture in the Kotlin `InkBoard` and the TypeScript reducer, including undo on an empty canvas, ending a stroke that was already undone, and reusing a stroke id. (Task 5 and Task 7 share a golden sequence.)
5. **Secrets:** the word must not leak through the TV payload, a guesser's view, the wrong-guess bubbles, or `Screen.Guess.blanks` at any hint stage; a correct guess must never appear as a bubble. (Task 4 leak tests.)

## File Structure

Engine (`tv/engine/src/main/kotlin/partyos/engine/`):
- Create `games/doodle/DoodleRules.kt`: pure rules (scoring, drawer choice, hints, blanks, near miss).
- Create `games/doodle/DoodlePack.kt`: pack format, loader, validation.
- Create `games/doodle/Doodle.kt`: `DoodleState` and the `GameModule`.
- Create `DoodleViews.kt`: `DoodleTv` payload and its row types.
- Modify `Views.kt`: add `Screen.Draw` and `Screen.Guess`.
- Modify `Game.kt`: add `InkAware`. Modify `PartyEngine.kt`: add `inkTurn`.
- Create `tv/engine/src/main/resources/packs/doodle-core.json`.

Server (`tv/server/src/main/kotlin/partyos/server/`):
- Create `Ink.kt`: `InkOp`, `InkStroke`, `InkTurn`, `InkBoard`.
- Modify `Protocol.kt`, `PartyHost.kt`, `PartyModule.kt`.

Devserver: modify `tv/devserver/src/main/kotlin/partyos/devserver/Main.kt`.

Controller (`controller/src/`):
- Create `ink/types.ts`, `ink/board.ts`, `ink/codec.ts`, `ink/paint.ts`, `ink/store.ts` (+ tests): the shared stroke model.
- Modify `protocol.ts`, `net/connection.ts`; regenerate `protocol/fixtures/*.json`; modify `protocol.test.ts`.
- Create `screens/DrawPad.tsx`, `screens/GuessPad.tsx`, `screens/draw.css`; modify `screens/ScreenView.tsx`, `pages/Play.tsx`.
- Create `tv/DoodleStage.tsx`, `tv/InkCanvas.tsx`, `tv/doodle.css`, `tv/doodle.ts` (+ test), `tv/fixtures/doodle-theme.ts`; modify `tv/types.ts`, `tv/TvPage.tsx`, `tv/ThemeGallery.tsx`, `tv/director.ts`.
- Modify `theme/gameTheme.ts`, `theme/games.css`.
- Create `e2e/doodle.spec.ts`.

Tests: engine `DoodleRulesTest`, `DoodlePackTest`, `DoodleViewsTest`, `DoodleTest`; server `InkBoardTest`, `InkRoutesTest`, `DoodleSimulatedPartyTest`; `ProtocolFixturesTest` gets Doodle and ink samples.

---

### Task 0: Branch, spec refinements

Another session is committing Jeopardy work to `jeopardy-revamp`, so Doodle goes on its own branch from `main` (which already has Imposter). The spec commit `027fd67` currently lives only on `jeopardy-revamp`; cherry-pick it and never rewrite that branch.

**Files:**
- Modify: `docs/superpowers/specs/2026-09-29-doodle-dash-design.md`
- Create: `docs/superpowers/plans/2026-09-29-doodle-dash.md` (this plan, copied in)

**Interfaces:** none.

- [ ] **Step 1: Create the worktree and branch**

```bash
cd /Users/jjahn/HoopDreams
git worktree add ../HoopDreams-doodle -b doodle-dash main
cd ../HoopDreams-doodle
git cherry-pick 027fd67
cp /Users/jjahn/HoopDreams/docs/superpowers/plans/2026-09-29-doodle-dash.md docs/superpowers/plans/
```

Expected: worktree at `/Users/jjahn/HoopDreams-doodle` on branch `doodle-dash`, with the spec present. All later tasks run in this worktree. Install controller deps there once: `cd controller && npm ci`.

- [ ] **Step 2: Baseline is green**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd tv && ./gradlew :engine:test :server:test -q
cd ../controller && npx vitest run
```

Expected: all pass. If anything fails before we touch code, stop and report it.

- [ ] **Step 3: Write the refinements into the spec**

Append this section to the end of `docs/superpowers/specs/2026-09-29-doodle-dash-design.md`:

```markdown
## Refinements decided during planning (2026-09-29)

- Default number of turns is 5 (the lobby shows 5 when `rounds` is unset), not 6.
- Guessers get a dedicated `guess` phone screen and the drawer a `draw` screen (text-entry's lock-in flow does not fit rapid guessing).
- "So close" means the normalised edit distance is at most `len / 3 + 1`, because the fuzzy matcher already accepts small typos.
- Hint letters reveal at draw-stage deadlines (`Effect.Deadline`); the phone and TV clocks add `tailMs`. Host skip during a draw advances one stage.
- The server ink archive is keyed by turn number; each accepted batch carries a sequence `n` and `inkSync` carries `upTo` so a late TV never doubles strokes.
- The word pack lists `easy` / `medium` / `hard` words inside each category.
- The podium phase lasts 24 s: podium blocks first, then the gallery.
- A guess or pick in the wrong phase is rejected with `NOT_NOW` (the code every other game uses), not `WRONG_PHASE`.
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-09-29-doodle-dash-design.md docs/superpowers/plans/2026-09-29-doodle-dash.md
git commit -m "docs(doodle): implementation plan and spec refinements"
```

---

### Task 1: Pure rules

**Files:**
- Create: `tv/engine/src/main/kotlin/partyos/engine/games/doodle/DoodleRules.kt`
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/doodle/DoodleRulesTest.kt`

**Interfaces:**
- Produces (used by Tasks 3 and 4):
  - `DoodleRules.STAGES: Int` (= 3)
  - `guesserPoints(difficulty: Int, fractionLeft: Double, order: Int, multiplier: Int): Int`
  - `drawerPoints(difficulty: Int, correct: Int, guessers: Int, multiplier: Int): Int`
  - `pickDrawer(candidates: List<PlayerId>, turns: Map<String, Int>, random: Random): PlayerId`
  - `stageMs(total: Long, stage: Int): Long`, `tailMs(total: Long, stage: Int): Long`
  - `hintCap(word: String): Int`, `nextHint(word: String, revealed: List<Int>, random: Random): List<Int>`
  - `blanks(word: String, revealed: Collection<Int>, revealAll: Boolean = false): String`
  - `nearMiss(guess: String, word: String): Boolean`

- [ ] **Step 1: Write the failing test**

```kotlin
package partyos.engine.games.doodle

import partyos.engine.PlayerId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoodleRulesTest {
    @Test fun guesserPointsFallWithTimeOrderAndEase() {
        assertEquals(1000, DoodleRules.guesserPoints(2, 1.0, 0, 1))
        assertEquals(800, DoodleRules.guesserPoints(1, 1.0, 0, 1))
        assertEquals(700, DoodleRules.guesserPoints(2, 0.5, 0, 1))
        assertEquals(336, DoodleRules.guesserPoints(3, 0.0, 2, 1))
        assertEquals(850, DoodleRules.guesserPoints(2, 1.0, 1, 1))
        assertEquals(550, DoodleRules.guesserPoints(2, 1.0, 5, 1)) // 4th and later share the floor factor
    }

    @Test fun theLastTurnDoubles() {
        assertEquals(2000, DoodleRules.guesserPoints(2, 1.0, 0, 2))
    }

    @Test fun timeLeftIsClamped() {
        assertEquals(DoodleRules.guesserPoints(2, 1.0, 0, 1), DoodleRules.guesserPoints(2, 1.7, 0, 1))
        assertEquals(DoodleRules.guesserPoints(2, 0.0, 0, 1), DoodleRules.guesserPoints(2, -3.0, 0, 1))
    }

    @Test fun drawerEarnsPerGuesserAndABonusWhenEveryoneGetsIt() {
        assertEquals(0, DoodleRules.drawerPoints(2, 0, 4, 1))
        assertEquals(500, DoodleRules.drawerPoints(2, 2, 4, 1))
        assertEquals(1500, DoodleRules.drawerPoints(2, 4, 4, 1))
        assertEquals(600, DoodleRules.drawerPoints(1, 1, 1, 1))
        assertEquals(3000, DoodleRules.drawerPoints(3, 3, 3, 2))
    }

    @Test fun drawerGoesToWhoeverHasDrawnLeast() {
        val ids = listOf("a", "b", "c").map(::PlayerId)
        repeat(20) { seed ->
            val d = DoodleRules.pickDrawer(ids, mapOf("a" to 1, "b" to 1), Random(seed))
            assertEquals(PlayerId("c"), d)
        }
        val seen = (0 until 60).map { DoodleRules.pickDrawer(ids, emptyMap(), Random(it)) }.toSet()
        assertEquals(ids.toSet(), seen) // a tie is broken at random
    }

    @Test fun stagesAddUpToTheDrawTime() {
        assertEquals(listOf(30_000L, 22_500L, 22_500L), (0..2).map { DoodleRules.stageMs(75_000, it) })
        assertEquals(listOf(45_000L, 22_500L, 0L), (0..2).map { DoodleRules.tailMs(75_000, it) })
        val odd = 99_999L
        assertEquals(odd, (0..2).sumOf { DoodleRules.stageMs(odd, it) })
    }

    @Test fun hintsRevealLettersUpToHalfTheWord() {
        assertEquals(2, DoodleRules.hintCap("pizza"))
        assertEquals(0, DoodleRules.hintCap("ox"))
        val r = Random(1)
        val one = DoodleRules.nextHint("pizza", emptyList(), r)
        val two = DoodleRules.nextHint("pizza", one, r)
        val three = DoodleRules.nextHint("pizza", two, r)
        assertEquals(1, one.size)
        assertEquals(2, two.size)
        assertEquals(two, three) // capped
        assertEquals(two.size, two.toSet().size)
    }

    @Test fun hintsNeverPickASpace() {
        val word = "ice cream"
        val r = Random(3)
        var got = emptyList<Int>()
        repeat(6) { got = DoodleRules.nextHint(word, got, r) }
        assertTrue(got.none { word[it] == ' ' })
    }

    @Test fun blanksShowOneCellPerLetterAndAWideGapBetweenWords() {
        assertEquals("_ _ _ _ _", DoodleRules.blanks("pizza", emptyList()))
        assertEquals("_ _ _   _ _ _ _ _", DoodleRules.blanks("ice cream", emptyList()))
        assertEquals("I _ _   _ _ _ _ _", DoodleRules.blanks("ice cream", listOf(0)))
        assertEquals("P I Z Z A", DoodleRules.blanks("pizza", emptyList(), revealAll = true))
    }

    @Test fun nearMissIsCloseButNotRight() {
        assertTrue(DoodleRules.nearMiss("pizqq", "pizza"))
        assertFalse(DoodleRules.nearMiss("pizza", "pizza"))
        assertFalse(DoodleRules.nearMiss("banana", "flamingo"))
        assertFalse(DoodleRules.nearMiss("cot", "cat")) // too short to say
        assertTrue(DoodleRules.nearMiss("kqqqaroo", "kangaroo"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd tv && ./gradlew :engine:test --tests "partyos.engine.games.doodle.DoodleRulesTest" -q`
Expected: FAIL (unresolved reference `DoodleRules`).

- [ ] **Step 3: Implement**

```kotlin
package partyos.engine.games.doodle

import partyos.engine.PlayerId
import partyos.engine.games.bluff.normalise
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Doodle Dash's turns, hints and scoring as pure functions, so they are tested without the game module. */
object DoodleRules {
    /** A draw is played in three stages; a hint letter is revealed when each of the last two begins. */
    const val STAGES = 3

    fun difficultyFactor(difficulty: Int) = 0.6 + 0.2 * difficulty

    /** 1st guesser 1.0, 2nd 0.85, 3rd 0.7, every later one 0.55. */
    fun orderFactor(order: Int) = when (order) {
        0 -> 1.0
        1 -> 0.85
        2 -> 0.7
        else -> 0.55
    }

    /** [order] is how many players had already got it; [fractionLeft] is the share of the draw time still on the clock. */
    fun guesserPoints(difficulty: Int, fractionLeft: Double, order: Int, multiplier: Int): Int {
        val t = fractionLeft.coerceIn(0.0, 1.0)
        return ((400 + 600 * t) * difficultyFactor(difficulty) * orderFactor(order) * multiplier).roundToInt()
    }

    fun drawerPoints(difficulty: Int, correct: Int, guessers: Int, multiplier: Int): Int {
        if (correct == 0) return 0
        val bonus = if (guessers > 0 && correct >= guessers) 500 else 0
        return ((250 * correct + bonus) * difficultyFactor(difficulty) * multiplier).roundToInt()
    }

    /** Whoever has drawn least goes next; a tie is broken at random. [candidates] must not be empty. */
    fun pickDrawer(candidates: List<PlayerId>, turns: Map<String, Int>, random: Random): PlayerId {
        val fewest = candidates.minOf { turns[it.v] ?: 0 }
        return candidates.filter { (turns[it.v] ?: 0) == fewest }.random(random)
    }

    /** Stage lengths are 40%, 30% and the rest of [total]. */
    fun stageMs(total: Long, stage: Int): Long = when (stage) {
        0 -> (total * 0.4).toLong()
        1 -> (total * 0.3).toLong()
        else -> total - (total * 0.4).toLong() - (total * 0.3).toLong()
    }

    /** Time in the stages after [stage]. */
    fun tailMs(total: Long, stage: Int): Long = (stage + 1 until STAGES).sumOf { stageMs(total, it) }

    private fun letterIndexes(word: String) = word.indices.filter { word[it].isLetter() }

    /** At most half the letters are ever shown. */
    fun hintCap(word: String) = letterIndexes(word).size / 2

    fun nextHint(word: String, revealed: List<Int>, random: Random): List<Int> {
        if (revealed.size >= hintCap(word)) return revealed
        val open = letterIndexes(word).filter { it !in revealed }
        return revealed + open.random(random)
    }

    /** "_ _ _ _ _", a wider gap between words, revealed letters in capitals; [revealAll] shows the whole word. */
    fun blanks(word: String, revealed: Collection<Int>, revealAll: Boolean = false): String =
        word.mapIndexed { i, ch ->
            when {
                ch == ' ' -> " "
                revealAll || i in revealed -> ch.uppercaseChar().toString()
                else -> "_"
            }
        }.joinToString(" ")

    /** Not right, but close: the fuzzy matcher takes small typos already, so this is a little further out than that. */
    fun nearMiss(guess: String, word: String): Boolean {
        val g = normalise(guess)
        val w = normalise(word)
        if (g == w || w.length < 4) return false
        return editDistance(g, w) <= w.length / 3 + 1
    }

    internal fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd tv && ./gradlew :engine:test --tests "partyos.engine.games.doodle.DoodleRulesTest" -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tv/engine/src/main/kotlin/partyos/engine/games/doodle/DoodleRules.kt tv/engine/src/test/kotlin/partyos/engine/games/doodle/DoodleRulesTest.kt
git commit -m "feat(doodle): pure rules for scoring, drawer choice, hints and near misses"
```

---

### Task 2: Word pack

**Files:**
- Create: `tv/engine/src/main/kotlin/partyos/engine/games/doodle/DoodlePack.kt`
- Create: `tv/engine/src/main/resources/packs/doodle-core.json`
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/doodle/DoodlePackTest.kt`

**Interfaces:**
- Produces (used by Task 4): `DoodleCategory(id, category, easy, medium, hard)`, `DoodleWord(id, category, word, difficulty)`, `DoodlePack(packId, title, game, version, items)` with `words(): List<DoodleWord>`, `DoodlePack.parse(text)`, `DoodlePack.core()`, `DoodlePack.validate(p)`. A word's id is `"<category id>:<word>"` (same scheme as Imposter).

- [ ] **Step 1: Write the failing test**

```kotlin
package partyos.engine.games.doodle

import partyos.engine.games.bluff.normalise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DoodlePackTest {
    private val ok = DoodleCategory("a", "A", listOf("giraffe"), listOf("kangaroo"), listOf("platypus"))
    private fun pack(vararg items: DoodleCategory, game: String = "doodle") = DoodlePack("t", "T", game, 1, items.toList())

    @Test fun theCorePackParsesAndIsBigEnough() {
        val words = DoodlePack.core().words()
        assertTrue(words.count { it.difficulty == 1 } >= 60, "easy")
        assertTrue(words.count { it.difficulty == 2 } >= 60, "medium")
        assertTrue(words.count { it.difficulty == 3 } >= 40, "hard")
        DoodlePack.core().items.forEach { c ->
            assertTrue(c.easy.size + c.medium.size + c.hard.size >= 40, "${c.id} has too few words")
        }
        assertEquals(words.size, words.map { it.id }.toSet().size)
        assertEquals(words.size, words.map { normalise(it.word) }.toSet().size)
    }

    @Test fun wordIdsNameTheirCategory() {
        val w = pack(ok).words()
        assertEquals(listOf("a:giraffe", "a:kangaroo", "a:platypus"), w.map { it.id })
        assertEquals(listOf(1, 2, 3), w.map { it.difficulty })
    }

    @Test fun validationRejectsBadPacks() {
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok, game = "trivia")) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack()) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok, ok)) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok.copy(easy = listOf("  ")))) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok.copy(easy = listOf("R2D2")))) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok.copy(easy = listOf("a")))) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok.copy(easy = listOf("one two three four")))) }
        assertFailsWith<IllegalArgumentException> { DoodlePack.validate(pack(ok.copy(easy = listOf("Giraffe"), medium = listOf("giraffe")))) }
        DoodlePack.validate(pack(ok, ok.copy(id = "b", easy = listOf("ice cream"), medium = listOf("rainbow"), hard = listOf("sloth"))))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd tv && ./gradlew :engine:test --tests "partyos.engine.games.doodle.DoodlePackTest" -q`
Expected: FAIL (unresolved reference `DoodlePack`).

- [ ] **Step 3: Implement the loader**

```kotlin
package partyos.engine.games.doodle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import partyos.engine.games.bluff.normalise

@Serializable
data class DoodleCategory(val id: String, val category: String, val easy: List<String>, val medium: List<String>, val hard: List<String>)

/** One playable word. [id] is the played-content id `<category id>:<word>`; [difficulty] is 1 easy, 2 medium, 3 hard. */
data class DoodleWord(val id: String, val category: String, val word: String, val difficulty: Int)

/** Word-pack file: the same envelope as the other packs, with each category's words split by difficulty. */
@Serializable
data class DoodlePack(
    val packId: String,
    val title: String,
    val game: String,
    val version: Int,
    val items: List<DoodleCategory>,
) {
    fun words(): List<DoodleWord> = items.flatMap { c ->
        listOf(1 to c.easy, 2 to c.medium, 3 to c.hard).flatMap { (d, ws) -> ws.map { DoodleWord("${c.id}:$it", c.category, it, d) } }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val SHAPE = Regex("[A-Za-z]+( [A-Za-z]+){0,2}")
        const val MIN_LEN = 3
        const val MAX_LEN = 24

        fun parse(text: String): DoodlePack = validate(json.decodeFromString(serializer(), text))

        fun core(): DoodlePack {
            val stream = requireNotNull(DoodlePack::class.java.getResourceAsStream("/packs/doodle-core.json")) { "core pack missing" }
            return parse(stream.bufferedReader().use { it.readText() })
        }

        fun validate(p: DoodlePack): DoodlePack {
            require(p.game == "doodle") { "pack ${p.packId} is for ${p.game}, not doodle" }
            require(p.items.isNotEmpty()) { "pack ${p.packId} is empty" }
            require(p.items.map { it.id }.toSet().size == p.items.size) { "pack ${p.packId} has duplicate category ids" }
            val seen = HashSet<String>()
            for (c in p.items) {
                require(c.category.isNotBlank()) { "${c.id}: blank category name" }
                for (w in c.easy + c.medium + c.hard) {
                    require(w.isNotBlank()) { "${c.id}: blank word" }
                    require(w.length in MIN_LEN..MAX_LEN && SHAPE.matches(w)) { "${c.id}: '$w' must be 1-3 words of letters, $MIN_LEN-$MAX_LEN characters" }
                    require(seen.add(normalise(w))) { "${c.id}: '$w' appears twice in the pack" }
                }
            }
            return p
        }
    }
}
```

- [ ] **Step 4: Write the core pack**

Create `tv/engine/src/main/resources/packs/doodle-core.json` (five categories, 40 words each: 14 easy, 14 medium, 12 hard):

```json
{
  "packId": "doodle-core",
  "title": "Doodle core",
  "game": "doodle",
  "version": 1,
  "items": [
    {
      "id": "person", "category": "Person or Job",
      "easy": ["doctor", "teacher", "farmer", "pirate", "clown", "king", "chef", "cowboy", "witch", "baby", "firefighter", "police officer", "ghost", "snowman"],
      "medium": ["dentist", "mermaid", "wizard", "ninja", "plumber", "magician", "lifeguard", "scarecrow", "superhero", "mailman", "painter", "photographer", "bodybuilder", "fairy"],
      "hard": ["librarian", "accountant", "referee", "detective", "mad scientist", "tour guide", "lumberjack", "mime", "auctioneer", "therapist", "translator", "paparazzi"]
    },
    {
      "id": "place", "category": "Place or Animal",
      "easy": ["beach", "house", "mountain", "castle", "dog", "cat", "fish", "bird", "elephant", "snake", "pig", "cow", "rabbit", "zoo"],
      "medium": ["lighthouse", "volcano", "igloo", "kangaroo", "octopus", "giraffe", "penguin", "jungle", "pyramid", "farm", "hospital", "desert island", "squirrel", "butterfly"],
      "hard": ["aquarium", "skyscraper", "platypus", "flamingo", "chameleon", "hedgehog", "cathedral", "waterfall", "sloth", "campground", "observatory", "swamp"]
    },
    {
      "id": "object", "category": "Object",
      "easy": ["ball", "chair", "cup", "book", "hat", "shoe", "key", "clock", "phone", "spoon", "umbrella", "bed", "door", "sock"],
      "medium": ["backpack", "skateboard", "telescope", "toothbrush", "vacuum", "candle", "hammer", "ladder", "trophy", "suitcase", "headphones", "sunglasses", "scissors", "balloon"],
      "hard": ["microscope", "thermometer", "chandelier", "vending machine", "hourglass", "kaleidoscope", "bagpipes", "treadmill", "snow globe", "lawnmower", "harmonica", "fire hydrant"]
    },
    {
      "id": "action", "category": "Action",
      "easy": ["sleeping", "running", "jumping", "eating", "swimming", "crying", "laughing", "dancing", "singing", "reading", "sneezing", "waving", "clapping", "hugging"],
      "medium": ["fishing", "painting", "skiing", "juggling", "brushing teeth", "sweeping", "yawning", "cooking", "surfing", "hiccuping", "knitting", "whistling", "marching", "tiptoeing"],
      "hard": ["sleepwalking", "babysitting", "meditating", "procrastinating", "eavesdropping", "daydreaming", "skydiving", "moonwalking", "bargaining", "hitchhiking", "sunbathing", "arm wrestling"]
    },
    {
      "id": "difficult", "category": "Difficult",
      "easy": ["rainbow", "birthday", "sunburn", "fireworks", "thunderstorm", "haircut", "traffic jam", "road trip", "picnic", "bedtime", "homework", "sandcastle", "campfire", "treasure map"],
      "medium": ["time travel", "jet lag", "stage fright", "brain freeze", "group project", "full moon", "gravity", "echo", "allergies", "karaoke", "double take", "awkward silence", "sibling rivalry", "photobomb"],
      "hard": ["inflation", "democracy", "nostalgia", "sarcasm", "peer pressure", "midlife crisis", "identity theft", "global warming", "black hole", "jealousy", "freedom", "small talk"]
    }
  ]
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd tv && ./gradlew :engine:test --tests "partyos.engine.games.doodle.DoodlePackTest" -q`
Expected: PASS. If the count assertions fail, add words to the short category (same shape rules) rather than loosening the test.

- [ ] **Step 6: Commit**

```bash
git add tv/engine/src/main/kotlin/partyos/engine/games/doodle/DoodlePack.kt tv/engine/src/main/resources/packs/doodle-core.json tv/engine/src/test/kotlin/partyos/engine/games/doodle/DoodlePackTest.kt
git commit -m "feat(doodle): word pack with 200 words in five categories"
```

---

### Task 3: Views (TV payload and phone screens)

**Files:**
- Create: `tv/engine/src/main/kotlin/partyos/engine/DoodleViews.kt`
- Modify: `tv/engine/src/main/kotlin/partyos/engine/Views.kt` (add `Screen.Draw`, `Screen.Guess` after `Screen.Secret`, before `Screen.Turf`)
- Test: `tv/engine/src/test/kotlin/partyos/engine/DoodleViewsTest.kt`

**Interfaces:**
- Produces (used by Tasks 4, 6, 8, 10):
  - `Screen.Draw(word: String, difficulty: Int, guessed: Int, expected: Int, tailMs: Long, note: String? = null)`, serial name `draw`.
  - `Screen.Guess(drawer: String, blanks: String, kind: String, solved: Boolean, points: Int? = null, close: Boolean = false, last: String? = null, guessed: Int, expected: Int, tailMs: Long)`, serial name `guess`.
  - `DoodleTv` (serial name `doodle`) and `DoodleSolver`, `DoodleMissTv`, `DoodleDrink`, `DoodleDelta`, `DoodleShot`.

- [ ] **Step 1: Write the failing test**

```kotlin
package partyos.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoodleViewsTest {
    private val json = Json { classDiscriminator = "t"; encodeDefaults = true; explicitNulls = false }

    @Test fun drawScreenKeepsItsDiscriminatorAndRoundTrips() {
        val screen: Screen = Screen.Draw("pizza", 2, 1, 4, 45_000, "Draw it!")
        val text = json.encodeToString(Screen.serializer(), screen)
        assertTrue(text.contains("\"t\":\"draw\""), text)
        assertEquals(screen, json.decodeFromString(Screen.serializer(), text))
    }

    @Test fun guessScreenKeepsItsDiscriminatorAndRoundTrips() {
        val screen: Screen = Screen.Guess("Al", "_ _ _ _ _", "guess", solved = false, close = true, last = "pizzq", guessed = 1, expected = 4, tailMs = 22_500)
        val text = json.encodeToString(Screen.serializer(), screen)
        assertTrue(text.contains("\"t\":\"guess\""), text)
        assertEquals(screen, json.decodeFromString(Screen.serializer(), text))
    }

    @Test fun doodleTvKeepsItsDiscriminatorAndRoundTrips() {
        val al = PlayerId("p-al")
        val tv: TvGame = DoodleTv(
            phase = "reveal", turn = 2, totalTurns = 5, finalTurn = false, drawer = al, drawerName = "Al", difficulty = 2,
            blanks = "", guessed = 2, expected = 3, drawMs = 75_000, tailMs = 0,
            solvers = listOf(DoodleSolver(PlayerId("p-bo"), "Bo", 1000)),
            wrong = listOf(DoodleMissTv(PlayerId("p-cy"), "Cy", "pizzq")), missTotal = 9,
            word = "pizza",
            drinks = listOf(DoodleDrink(PlayerId("p-cy"), "Cy", 1, "Missed it. Drink 1 sip")),
            deltas = listOf(DoodleDelta(al, "Al", 1000)),
            gallery = listOf(DoodleShot(1, "pizza", al, "Al", PlayerId("p-bo"), "Bo")),
        )
        val text = json.encodeToString(TvGame.serializer(), tv)
        assertTrue(text.contains("\"t\":\"doodle\""), text)
        assertEquals(tv, json.decodeFromString(TvGame.serializer(), text))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd tv && ./gradlew :engine:test --tests "partyos.engine.DoodleViewsTest" -q`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Add the screens**

In `Views.kt`, inside `sealed interface Screen`, right after the `Secret` data class:

```kotlin
    /**
     * Doodle Dash, the drawer: the word to draw and how the room is doing. The pad itself is the phone's canvas; its
     * strokes travel over the ink channel, not through this screen. [tailMs] is the draw time left after the current
     * hint stage, added to the phone's stage countdown.
     */
    @Serializable @SerialName("draw")
    data class Draw(
        val word: String,
        val difficulty: Int,
        val guessed: Int,
        val expected: Int,
        val tailMs: Long,
        val note: String? = null,
    ) : Screen

    /**
     * Doodle Dash, a guesser: the blanks (revealed letters in capitals), whether you have it, and your last miss.
     * [kind] names the guess action; [close] means your last miss was near.
     */
    @Serializable @SerialName("guess")
    data class Guess(
        val drawer: String,
        val blanks: String,
        val kind: String,
        val solved: Boolean,
        val points: Int? = null,
        val close: Boolean = false,
        val last: String? = null,
        val guessed: Int,
        val expected: Int,
        val tailMs: Long,
    ) : Screen
```

- [ ] **Step 4: Add the TV payload**

```kotlin
package partyos.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("doodle")
data class DoodleTv(
    /** pick | draw | reveal | scores | podium */
    val phase: String,
    val turn: Int,
    val totalTurns: Int,
    val finalTurn: Boolean,
    val drawer: PlayerId? = null,
    val drawerName: String = "",
    /** 1 easy, 2 medium, 3 hard (draw onward). */
    val difficulty: Int = 0,
    /** Draw only: "_ _ _ _ _" with any revealed letters. Never the word. */
    val blanks: String = "",
    /** Players who have it, and how many could. */
    val guessed: Int = 0,
    val expected: Int = 0,
    /** The whole draw time, and the part of it after the current hint stage. */
    val drawMs: Long = 0,
    val tailMs: Long = 0,
    /** Who has it, in order. Points are held back until the reveal. */
    val solvers: List<DoodleSolver> = emptyList(),
    /** The last few wrong guesses, oldest first; [missTotal] counts every wrong guess so far (stable animation keys). */
    val wrong: List<DoodleMissTv> = emptyList(),
    val missTotal: Int = 0,
    /** Reveal and scores only. */
    val word: String? = null,
    val drinks: List<DoodleDrink> = emptyList(),
    /** Scores only. */
    val deltas: List<DoodleDelta> = emptyList(),
    /** Podium only: one entry per finished turn, in order. */
    val gallery: List<DoodleShot> = emptyList(),
) : TvGame

@Serializable
data class DoodleSolver(val id: PlayerId, val name: String, val points: Int? = null)

@Serializable
data class DoodleMissTv(val id: PlayerId, val name: String, val text: String)

/** [text] is the whole drink line, already worded for water. */
@Serializable
data class DoodleDrink(val id: PlayerId, val name: String, val sips: Int, val text: String)

@Serializable
data class DoodleDelta(val id: PlayerId, val name: String, val points: Int)

@Serializable
data class DoodleShot(val turn: Int, val word: String, val drawer: PlayerId, val drawerName: String, val first: PlayerId? = null, val firstName: String? = null)
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd tv && ./gradlew :engine:test --tests "partyos.engine.DoodleViewsTest" -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add tv/engine/src/main/kotlin/partyos/engine/DoodleViews.kt tv/engine/src/main/kotlin/partyos/engine/Views.kt tv/engine/src/test/kotlin/partyos/engine/DoodleViewsTest.kt
git commit -m "feat(doodle): draw and guess phone screens and the TV payload"
```

---

### Task 4: The game module

**Files:**
- Create: `tv/engine/src/main/kotlin/partyos/engine/games/doodle/Doodle.kt`
- Modify: `tv/engine/src/main/kotlin/partyos/engine/Game.kt` (add `InkAware` after `GameModule`)
- Modify: `tv/engine/src/main/kotlin/partyos/engine/PartyEngine.kt` (add `inkTurn`)
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/doodle/DoodleTest.kt`

**Interfaces:**
- Consumes: `DoodleRules.*` (Task 1), `DoodlePack`/`DoodleWord` (Task 2), `Screen.Draw`/`Screen.Guess`/`DoodleTv` and row types (Task 3), `AnswerMatch.accepts(guess, answer)`, `cleanText(raw, maxLen)`.
- Produces (used by Tasks 5, 6, 12):
  - `class Doodle(pack: DoodlePack = DoodlePack.core()) : GameModule<DoodleState>, InkAware<DoodleState>`
  - `interface InkAware<S : Any> { fun inkTurn(s: S, who: PlayerId): Int? }`
  - `PartyEngine.inkTurn(id: PlayerId, round: Int): Int?`: the turn `id` may draw on in the phase numbered `round`, else null (not the drawer, wrong phase, paused, stale round, no game).
  - Phone actions: `{kind:"pick", option:<wordId>}` and `{kind:"guess", text}`. Rejection codes: `NEXT_ROUND`, `NOT_NOW`, `BAD_OPTION`, `NOT_GUESSING`, `BAD_TEXT`.
  - `Doodle.DEFAULT_TURNS = 5`, phase constants `PICK, DRAW, REVEAL, SCORES, PODIUM`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package partyos.engine.games.doodle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.DoodleTv
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PhoneState
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.TvState
import partyos.engine.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoodleTest {
    private val clock = FakeClock(0)
    private val pack = DoodlePack(
        "test", "Test", "doodle", 1,
        listOf(
            DoodleCategory("animals", "Animal", listOf("giraffe", "penguin", "octopus"), listOf("kangaroo", "dolphin", "hedgehog"), listOf("flamingo", "chameleon", "platypus")),
            DoodleCategory("things", "Object", listOf("umbrella", "backpack", "telescope"), listOf("skateboard", "suitcase", "sunglasses"), listOf("microscope", "chandelier", "kaleidoscope")),
        ),
    )
    private val json = Json { classDiscriminator = "t"; encodeDefaults = true; explicitNulls = false }

    private lateinit var e: PartyEngine
    private var n = 0

    /** Starts Doodle Dash with [count] players, the tutorial skipped, in the pick phase of turn 1. */
    private fun start(count: Int, turns: Int = 3, settings: Map<String, Int> = emptyMap()): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Doodle(pack))))
        val ids = (1..count).map { e.add("P$it") }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("doodle", mapOf("rounds" to turns) + settings)))
        e.host(HostCmd.SkipPhase)
        return ids
    }

    private val tv get() = e.tvState().stage!!.game as DoodleTv
    private val seq get() = e.tvState().stage!!.phaseSeq
    private fun act(who: PlayerId, kind: String, vararg kv: Pair<String, String>) =
        e.action(who, "a${n++}", seq, buildJsonObject { put("kind", JsonPrimitive(kind)); kv.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
    private fun skip() { e.host(HostCmd.SkipPhase) }
    private fun score(id: PlayerId) = e.tvState().scores.first { it.id == id }.score
    private fun options(drawer: PlayerId) = assertIs<Screen.ChoiceList>(e.phoneState(drawer).screen).options

    /** The drawer picks the [level] word ("Easy", "Medium" or "Hard") and the draw begins; returns the drawer and the word. */
    private fun startDrawing(level: String = "Medium"): Pair<PlayerId, String> {
        val drawer = tv.drawer!!
        val o = options(drawer).first { it.detail == level }
        assertEquals(ActionResult.Ack, act(drawer, "pick", "option" to o.id))
        assertEquals("draw", tv.phase)
        return drawer to assertIs<Screen.Draw>(e.phoneState(drawer).screen).word
    }
    private fun guessers(ids: List<PlayerId>, drawer: PlayerId) = ids - drawer
    private fun guessScreen(who: PlayerId) = assertIs<Screen.Guess>(e.phoneState(who).screen)
    private fun toTurn(k: Int) { while (!(tv.turn == k && tv.phase == "pick")) skip() }

    // ---- pick -----------------------------------------------------------------------------------

    @Test fun startsInPickWithThreeWordsForTheDrawerAndNothingSecretOnTheTv() {
        val ids = start(4)
        val drawer = tv.drawer!!
        assertEquals("pick", tv.phase)
        assertEquals(1, tv.turn)
        assertEquals(3, tv.totalTurns)
        assertNull(tv.word)
        assertEquals("", tv.blanks)
        val opts = options(drawer)
        assertEquals(listOf("Easy", "Medium", "Hard"), opts.map { it.detail })
        ids.filter { it != drawer }.forEach { assertIs<Screen.Waiting>(e.phoneState(it).screen) }
    }

    @Test fun onlyTheDrawerPicksAndOnlyOfferedWords() {
        val ids = start(4)
        val drawer = tv.drawer!!
        val other = ids.first { it != drawer }
        assertEquals(ActionResult.Rejected("NOT_NOW"), act(other, "pick", "option" to options(drawer).first().id))
        assertEquals(ActionResult.Rejected("BAD_OPTION"), act(drawer, "pick", "option" to "nope:word"))
        assertEquals("pick", tv.phase)
    }

    @Test fun anIdlePickerGetsTheMediumWord() {
        start(4)
        val drawer = tv.drawer!!
        val medium = options(drawer).first { it.detail == "Medium" }
        skip()
        assertEquals("draw", tv.phase)
        assertEquals(medium.text, assertIs<Screen.Draw>(e.phoneState(drawer).screen).word)
    }

    @Test fun theDefaultIsFiveTurns() {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(Doodle(pack))))
        repeat(3) { e.add("P$it") }
        e.host(HostCmd.StartGame("doodle")); e.host(HostCmd.SkipPhase)
        assertEquals(5, tv.totalTurns)
    }

    // ---- secrecy --------------------------------------------------------------------------------

    @Test fun theWordIsOnlyOnTheDrawersPhoneUntilTheReveal() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        assertFalse(json.encodeToString(TvState.serializer(), e.tvState()).contains(word), "TV leaked the word")
        guessers(ids, drawer).forEach { g ->
            assertFalse(json.encodeToString(PhoneState.serializer(), e.phoneState(g)).contains(word), "a guesser's view leaked the word")
            assertEquals(word.count { it.isLetter() }, guessScreen(g).blanks.count { it == '_' })
            assertTrue(guessScreen(g).blanks.none { it.isLetter() }, "no letter before a hint")
        }
    }

    @Test fun hintLettersAppearOnGuessersAndTheTvButNeverTheWholeWord() {
        val ids = start(4)
        val (drawer, word) = startDrawing("Hard")
        val g = guessers(ids, drawer).first()
        skip() // stage 1: first hint
        assertEquals(1, guessScreen(g).blanks.count { it.isLetter() })
        assertEquals(guessScreen(g).blanks, tv.blanks)
        skip() // stage 2: second hint
        assertEquals(2, guessScreen(g).blanks.count { it.isLetter() })
        assertFalse(json.encodeToString(TvState.serializer(), e.tvState()).contains(word))
        assertEquals("draw", tv.phase)
    }

    // ---- guessing -------------------------------------------------------------------------------

    @Test fun aCorrectGuessScoresOnceAndLocksThePlayerOut() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        val g = guessers(ids, drawer).first()
        assertEquals(ActionResult.Ack, act(g, "guess", "text" to "  ${word.uppercase()} "))
        assertEquals(1000, score(g)) // medium word, first to guess, full time left
        assertTrue(guessScreen(g).solved)
        assertEquals(1000, guessScreen(g).points)
        assertEquals(ActionResult.Rejected("NOT_GUESSING"), act(g, "guess", "text" to word))
        assertEquals(1, tv.guessed)
        assertEquals(listOf(g), tv.solvers.map { it.id })
        assertNull(tv.solvers.first().points) // held back until the reveal
    }

    @Test fun laterGuessersGetLessAndTheDrawerIsPaidWhenEveryoneHasIt() {
        val ids = start(3)
        val (drawer, word) = startDrawing()
        val (a, b) = guessers(ids, drawer)
        act(a, "guess", "text" to word)
        clock.advance(15_000)
        act(b, "guess", "text" to word)
        assertEquals(1000, score(a))
        assertEquals(748, score(b)) // (400 + 600 * 0.8) * 0.85
        assertEquals("reveal", tv.phase) // everyone has it: the draw ends early
        assertEquals(1000, score(drawer)) // (250 * 2 + 500 bonus)
        assertEquals(word, tv.word)
        assertEquals(listOf(1000, 748), tv.solvers.map { it.points })
    }

    @Test fun theDrawerCannotGuessAndBadTextIsRefused() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        val g = guessers(ids, drawer).first()
        assertEquals(ActionResult.Rejected("NOT_GUESSING"), act(drawer, "guess", "text" to word))
        assertEquals(ActionResult.Rejected("BAD_TEXT"), act(g, "guess", "text" to "   "))
        assertEquals(ActionResult.Rejected("BAD_TEXT"), act(g, "guess", "text" to "x".repeat(41)))
        assertFalse(guessScreen(g).solved)
    }

    @Test fun aWrongGuessBecomesABubbleOnTheTvButANearMissIsPrivate() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        val g = guessers(ids, drawer).first()
        val close = word.first() + "qqq" + word.drop(4) // three letters wrong: not accepted, but near
        assertEquals(ActionResult.Ack, act(g, "guess", "text" to close))
        assertFalse(guessScreen(g).solved)
        assertTrue(guessScreen(g).close)
        assertEquals(close, guessScreen(g).last)
        assertEquals(listOf(close), tv.wrong.map { it.text })
        assertEquals(1, tv.missTotal)
        act(g, "guess", "text" to "zzzzzzzz")
        assertFalse(guessScreen(g).close)
        assertEquals(2, tv.missTotal)
    }

    @Test fun aCorrectGuessNeverBecomesABubble() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        act(guessers(ids, drawer).first(), "guess", "text" to word)
        assertTrue(tv.wrong.isEmpty())
    }

    @Test fun onlyTheLastEightWrongGuessesAreKept() {
        val ids = start(4)
        val (drawer, _) = startDrawing()
        val g = guessers(ids, drawer).first()
        repeat(12) { act(g, "guess", "text" to "nope$it") }
        assertEquals(8, tv.wrong.size)
        assertEquals("nope11", tv.wrong.last().text)
        assertEquals(12, tv.missTotal)
    }

    // ---- ending a draw --------------------------------------------------------------------------

    @Test fun eachSkipMovesToTheNextHintStageThenTheReveal() {
        start(4)
        startDrawing()
        assertEquals(45_000, tv.tailMs)
        skip(); assertEquals(22_500, tv.tailMs); assertEquals("draw", tv.phase)
        skip(); assertEquals(0, tv.tailMs); assertEquals("draw", tv.phase)
        skip(); assertEquals("reveal", tv.phase)
    }

    @Test fun nobodyGuessingCostsTheDrawerTwoSipsAndPaysNothing() {
        val ids = start(4)
        val (drawer, _) = startDrawing()
        repeat(3) { skip() }
        assertEquals("reveal", tv.phase)
        assertEquals(0, score(drawer))
        assertEquals(listOf(drawer), tv.drinks.map { it.id })
        assertEquals(2, tv.drinks.single().sips)
        assertEquals(ids.size, e.tvState().scores.size)
    }

    @Test fun guessersWhoMissedDrinkOneSipWhenSomeoneElseGotIt() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        val (a, b, c) = guessers(ids, drawer)
        act(a, "guess", "text" to word)
        repeat(3) { skip() }
        assertEquals("reveal", tv.phase)
        assertEquals(setOf(b, c), tv.drinks.map { it.id }.toSet())
        assertTrue(tv.drinks.all { it.sips == 1 })
    }

    @Test fun drinkCallsFollowTheLobbySwitchAndWaterWording() {
        start(3, settings = mapOf("drinks" to 0))
        startDrawing()
        repeat(3) { skip() }
        assertTrue(tv.drinks.isEmpty())

        start(3)
        val (drawer, _) = startDrawing()
        e.setWater(drawer, true)
        repeat(3) { skip() }
        assertTrue(tv.drinks.single().text.endsWith("of water"), tv.drinks.single().text)
    }

    @Test fun theFinalTurnCountsDouble() {
        val ids = start(3, turns = 3)
        toTurn(3)
        val (drawer, word) = startDrawing()
        assertTrue(tv.finalTurn)
        act(guessers(ids, drawer).first(), "guess", "text" to word)
        assertEquals(2000, score(guessers(ids, drawer).first()))
    }

    // ---- turns ----------------------------------------------------------------------------------

    @Test fun everyoneDrawsOnceBeforeAnyoneDrawsTwice() {
        start(3, turns = 3)
        val drawers = mutableListOf<PlayerId>()
        for (t in 1..3) { toTurn(t); drawers += tv.drawer!! }
        assertEquals(3, drawers.toSet().size)
    }

    @Test fun aWordIsNeverOfferedTwiceInAGame() {
        start(3, turns = 5)
        val seen = mutableListOf<String>()
        for (t in 1..5) {
            toTurn(t)
            val (_, word) = startDrawing()
            seen += word
        }
        assertEquals(seen.size, seen.toSet().size)
    }

    @Test fun thePodiumHoldsAGalleryOfEveryTurnThenTheGameFinishes() {
        start(3, turns = 3)
        while (tv.phase != "podium") skip()
        assertEquals(3, tv.gallery.size)
        assertEquals(listOf(1, 2, 3), tv.gallery.map { it.turn })
        assertTrue(tv.gallery.all { it.word.isNotBlank() })
        skip()
        assertNull(e.tvState().stage)
        assertNotEquals(null, e.tvState().lastResult)
    }

    // ---- presence, late joiners, stale actions --------------------------------------------------

    @Test fun aLateJoinerWatchesUntilTheNextTurn() {
        start(3)
        val (_, word) = startDrawing()
        val late = e.add("Late")
        assertEquals(ActionResult.Rejected("NEXT_ROUND"), act(late, "guess", "text" to word))
        assertIs<Screen.Waiting>(e.phoneState(late).screen)
    }

    @Test fun aDrawerWhoDropsBeforePickingIsAutoPickedAndTheTurnGoesOn() {
        val ids = start(4)
        val drawer = tv.drawer!!
        e.setPresence(drawer, false)
        assertEquals("draw", tv.phase)
        val g = guessers(ids, drawer).first()
        val word = assertIs<Screen.Draw>(e.phoneState(drawer).screen).word // the drawer's phone still knows it
        act(g, "guess", "text" to word)
        assertEquals(1, tv.guessed)
    }

    @Test fun aDrawerWhoDropsMidDrawDoesNotStopTheTurnEnding() {
        val ids = start(4)
        val (drawer, word) = startDrawing()
        e.setPresence(drawer, false)
        guessers(ids, drawer).forEach { act(it, "guess", "text" to word) }
        assertEquals("reveal", tv.phase)
    }

    @Test fun aGuessFromAnEarlierPhaseIsStale() {
        val ids = start(4)
        val old = seq
        val (drawer, word) = startDrawing()
        val g = guessers(ids, drawer).first()
        assertEquals(ActionResult.Rejected("STALE"), e.action(g, "late", old, buildJsonObject { put("kind", JsonPrimitive("guess")); put("text", JsonPrimitive(word)) }))
    }

    // ---- ink permission -------------------------------------------------------------------------

    @Test fun onlyTheDrawerMayInkAndOnlyDuringTheDrawOfTheCurrentPhase() {
        val ids = start(4)
        val drawer = tv.drawer!!
        assertNull(e.inkTurn(drawer, seq)) // still picking
        startDrawing()
        assertEquals(1, e.inkTurn(drawer, seq))
        assertNull(e.inkTurn(ids.first { it != drawer }, seq))
        assertNull(e.inkTurn(drawer, seq - 1)) // an old phase number
        e.host(HostCmd.Pause)
        assertNull(e.inkTurn(drawer, seq)) // paused
        e.host(HostCmd.Resume)
        repeat(3) { skip() } // reveal
        assertNull(e.inkTurn(drawer, seq))
    }

    // ---- restore --------------------------------------------------------------------------------

    @Test fun aSavedGameRestoresMidTurn() {
        start(4)
        val (drawer, _) = startDrawing()
        val restored = PartyEngine.restore(e.snapshot(), clock, SeededEntropy(7), GameRegistry(listOf(Doodle(pack))))
        val g = restored.tvState().stage!!.game as DoodleTv
        assertEquals("draw", g.phase)
        assertEquals(drawer, g.drawer)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd tv && ./gradlew :engine:test --tests "partyos.engine.games.doodle.DoodleTest" -q`
Expected: FAIL (unresolved references `Doodle`, `inkTurn`).

- [ ] **Step 3: Add `InkAware` to `Game.kt`**

After the `GameModule` interface:

```kotlin
/** A game whose players draw live: the server relays their strokes (outside game state) once the game says who may. */
interface InkAware<S : Any> {
    /** The turn number [who] may draw on right now, or null when they may not. */
    fun inkTurn(s: S, who: PlayerId): Int?
}
```

- [ ] **Step 4: Add `inkTurn` to `PartyEngine.kt`**

In the "game runtime" section, after `host(...)`:

```kotlin
    /**
     * The drawing turn [id] may ink on in the phase numbered [round], or null: not a player, not the drawer, wrong or
     * old phase, paused, in the tutorial, or the game doesn't draw.
     */
    fun inkTurn(id: PlayerId, round: Int): Int? {
        if (player(id)?.role != Role.PLAYER) return null
        val g = active ?: return null
        if (g.paused || g.tutorialAcks != null || round != g.phaseSeq) return null
        return inkTurnOf(g, id)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <S : Any> inkTurnOf(g: ActiveGame<S>, id: PlayerId): Int? =
        (g.module as? InkAware<S>)?.let { m -> g.state?.let { m.inkTurn(it, id) } }
```

- [ ] **Step 5: Implement the module**

```kotlin
package partyos.engine.games.doodle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.Choice
import partyos.engine.DoodleDelta
import partyos.engine.DoodleDrink
import partyos.engine.DoodleMissTv
import partyos.engine.DoodleShot
import partyos.engine.DoodleSolver
import partyos.engine.DoodleTv
import partyos.engine.Effect
import partyos.engine.GameContext
import partyos.engine.GameInfo
import partyos.engine.GameModule
import partyos.engine.InkAware
import partyos.engine.LateJoin
import partyos.engine.PlayerId
import partyos.engine.Reject
import partyos.engine.ScoreRow
import partyos.engine.Screen
import partyos.engine.Step
import partyos.engine.TutorialCard
import partyos.engine.games.bluff.cleanText
import partyos.engine.games.trivia.AnswerMatch
import partyos.engine.ofWater
import kotlin.random.Random

@Serializable
data class DoodleOption(val id: String, val word: String, val difficulty: Int)

/** A wrong guess: [id] is the guesser. */
@Serializable
data class DoodleMiss(val id: String, val text: String)

/** A finished turn, kept for the podium gallery. */
@Serializable
data class DoodleDone(val turn: Int, val word: String, val drawer: String, val first: String? = null)

@Serializable
data class DoodleState(
    val phase: String,
    val turn: Int,
    val totalTurns: Int,
    /** drawer id → turns drawn so far, this game. */
    val turns: Map<String, Int> = emptyMap(),
    /** Who is in this turn (connected when it began); the drawer is one of them. Later joiners watch. */
    val participants: List<PlayerId> = emptyList(),
    val drawer: PlayerId? = null,
    val options: List<DoodleOption> = emptyList(),
    val wordId: String? = null,
    val word: String = "",
    val difficulty: Int = 0,
    /** The whole draw time, fixed when the draw starts (the lobby timer setting scales it). */
    val drawMs: Long = 0,
    val stage: Int = 0,
    /** Character positions of the letters shown as hints. */
    val revealed: List<Int> = emptyList(),
    /** Guesser ids in the order they got it. */
    val correct: List<String> = emptyList(),
    val points: Map<String, Int> = emptyMap(),
    /** guesser id → their latest wrong guess. */
    val last: Map<String, String> = emptyMap(),
    /** Guessers whose latest wrong guess was near. */
    val close: Set<String> = emptySet(),
    val misses: List<DoodleMiss> = emptyList(),
    val missTotal: Int = 0,
    /** player id → points this game turn so far (guesses, then the drawer's pay at the reveal). */
    val deltas: Map<String, Int> = emptyMap(),
    val done: List<DoodleDone> = emptyList(),
)

class Doodle(pack: DoodlePack = DoodlePack.core()) : GameModule<DoodleState>, InkAware<DoodleState> {
    override val info = GameInfo(
        id = "doodle",
        title = "Doodle Dash",
        tagline = "Pictionary-style: draw it, guess it",
        minPlayers = 3,
        maxPlayers = 16,
        tutorial = listOf(
            TutorialCard("Draw it", "One player at a time gets a word and draws it on their phone. The TV shows every stroke. No letters, no numbers, no talking."),
            TutorialCard("Guess fast", "Everyone else types guesses on their phones. Wrong guesses float across the TV. The quicker you get it, the more you score."),
            TutorialCard("Everyone draws", "Everybody takes a turn drawing. The drawer scores when others guess. Hints show up as the clock runs down."),
        ),
        lateJoin = LateJoin.NEXT_ROUND,
    )
    override val stateSerializer = DoodleState.serializer()

    private val words = pack.words()
    private val byId = words.associateBy { it.id }

    override fun start(ctx: GameContext): Step<DoodleState> {
        val turns = (ctx.settings["rounds"] ?: DEFAULT_TURNS).coerceIn(3, 8)
        return newTurn(DoodleState(PODIUM, 0, turns), 1, ctx)
    }

    private fun newTurn(prev: DoodleState, turn: Int, ctx: GameContext): Step<DoodleState> {
        val fresh = ctx.fresh(words) { it.id }
        val here = ctx.players.filter { it.connected }.ifEmpty { ctx.players }.map { it.id }
        if (fresh.isEmpty() || here.isEmpty()) return Step(prev.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        val drawer = DoodleRules.pickDrawer(here, prev.turns, ctx.random)
        val s = DoodleState(
            phase = PICK, turn = turn, totalTurns = prev.totalTurns,
            turns = prev.turns + (drawer.v to ((prev.turns[drawer.v] ?: 0) + 1)),
            participants = here, drawer = drawer, options = offer(fresh, ctx.random),
            missTotal = prev.missTotal, done = prev.done,
        )
        return Step(s, listOf(Effect.Phase(ctx.timer(PICK_MS))))
    }

    /** One word per difficulty; a pack that ran short of one still offers three words when it can. */
    private fun offer(fresh: List<DoodleWord>, random: Random): List<DoodleOption> {
        val chosen = (1..3).mapNotNull { d -> fresh.filter { it.difficulty == d }.randomOrNull(random) }.toMutableList()
        for (w in fresh.filter { w -> chosen.none { it.id == w.id } }.shuffled(random)) {
            if (chosen.size >= 3) break
            chosen += w
        }
        return chosen.sortedBy { it.difficulty }.map { DoodleOption(it.id, it.word, it.difficulty) }
    }

    override fun onAction(s: DoodleState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<DoodleState> {
        val kind = payload["kind"]?.jsonPrimitive?.content
        if (who !in s.participants) throw Reject("NEXT_ROUND")
        return when {
            s.phase == PICK && kind == "pick" -> {
                if (who != s.drawer) throw Reject("NOT_NOW")
                val id = payload["option"]?.jsonPrimitive?.content
                startDraw(s, s.options.firstOrNull { it.id == id } ?: throw Reject("BAD_OPTION"), ctx)
            }
            s.phase == DRAW && kind == "guess" -> guess(s, who, payload, ctx)
            else -> throw Reject("NOT_NOW")
        }
    }

    private fun startDraw(s: DoodleState, o: DoodleOption, ctx: GameContext): Step<DoodleState> {
        val total = ctx.timer(DRAW_MS)
        return Step(
            s.copy(phase = DRAW, wordId = o.id, word = o.word, difficulty = o.difficulty, drawMs = total, stage = 0),
            listOf(Effect.UseContent(o.id), Effect.Phase(DoodleRules.stageMs(total, 0))),
        )
    }

    private fun guess(s: DoodleState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<DoodleState> {
        if (who == s.drawer || who.v in s.correct) throw Reject("NOT_GUESSING")
        val text = cleanText(payload["text"]?.jsonPrimitive?.content ?: "", MAX_GUESS) ?: throw Reject("BAD_TEXT")
        if (!AnswerMatch.accepts(text, s.word)) {
            return Step(
                s.copy(
                    last = s.last + (who.v to text),
                    close = if (DoodleRules.nearMiss(text, s.word)) s.close + who.v else s.close - who.v,
                    misses = (s.misses + DoodleMiss(who.v, text)).takeLast(MAX_MISSES),
                    missTotal = s.missTotal + 1,
                ),
            )
        }
        val left = ((ctx.remainingMs ?: 0L) + DoodleRules.tailMs(s.drawMs, s.stage)).toDouble() / s.drawMs
        val pts = DoodleRules.guesserPoints(s.difficulty, left, s.correct.size, multiplier(s))
        return Step(
            s.copy(
                correct = s.correct + who.v,
                points = s.points + (who.v to pts),
                close = s.close - who.v,
                deltas = s.deltas + (who.v to ((s.deltas[who.v] ?: 0) + pts)),
            ),
            listOf(Effect.Award(who, pts, "got “${s.word}”")),
        )
    }

    override fun onDeadline(s: DoodleState, ctx: GameContext): Step<DoodleState> = when (s.phase) {
        PICK -> startDraw(s, s.options.firstOrNull { it.difficulty == 2 } ?: s.options[s.options.size / 2], ctx)
        DRAW -> {
            val pending = guessers(s).any { it.v !in s.correct && ctx.isConnected(it) }
            if (!pending || s.stage >= DoodleRules.STAGES - 1) endDraw(s, ctx)
            else {
                val next = s.stage + 1
                Step(
                    s.copy(stage = next, revealed = DoodleRules.nextHint(s.word, s.revealed, ctx.random)),
                    listOf(Effect.Deadline(DoodleRules.stageMs(s.drawMs, next))),
                )
            }
        }
        REVEAL -> Step(s.copy(phase = SCORES), listOf(Effect.Phase(SCORES_MS)))
        SCORES ->
            if (s.turn < s.totalTurns) newTurn(s, s.turn + 1, ctx)
            else Step(s.copy(phase = PODIUM), listOf(Effect.Phase(PODIUM_MS)))
        else -> Step(s, listOf(Effect.Finish))
    }

    /** The draw ends (everyone has it, or the clock ran out): the drawer is paid and the word is revealed. */
    private fun endDraw(s: DoodleState, ctx: GameContext): Step<DoodleState> {
        val drawer = requireNotNull(s.drawer)
        val guessers = guessers(s)
        val pts = DoodleRules.drawerPoints(s.difficulty, s.correct.size, guessers.size, multiplier(s))
        val effects = mutableListOf<Effect>()
        var deltas = s.deltas
        if (pts > 0) {
            deltas = deltas + (drawer.v to pts)
            effects += Effect.Award(drawer, pts, "drew “${s.word}”")
        }
        if (guessers.isNotEmpty() && s.correct.size == guessers.size) {
            ctx.player(drawer)?.let { effects += Effect.Highlight("${it.name} drew “${s.word}” and everyone got it") }
        }
        val done = s.done + DoodleDone(s.turn, s.word, drawer.v, s.correct.firstOrNull())
        return Step(s.copy(phase = REVEAL, deltas = deltas, done = done), effects + Effect.Phase(REVEAL_MS))
    }

    override fun waitingOn(s: DoodleState): Set<PlayerId>? = when (s.phase) {
        PICK -> s.drawer?.let { setOf(it) } ?: emptySet()
        DRAW -> guessers(s).filter { it.v !in s.correct }.toSet()
        else -> null
    }

    override fun inkTurn(s: DoodleState, who: PlayerId): Int? = if (s.phase == DRAW && who == s.drawer) s.turn else null

    override fun restorable(s: DoodleState) = s.wordId == null || s.wordId in byId

    private fun guessers(s: DoodleState) = s.participants.filter { it != s.drawer }
    private fun multiplier(s: DoodleState) = if (s.turn == s.totalTurns) 2 else 1
    private fun tail(s: DoodleState) = if (s.phase == DRAW) DoodleRules.tailMs(s.drawMs, s.stage) else 0L

    // ---- views ----------------------------------------------------------------------------------

    override fun tvView(s: DoodleState, ctx: GameContext): DoodleTv {
        val name = { id: PlayerId? -> id?.let { ctx.player(it)?.name } ?: "?" }
        val shown = s.phase == REVEAL || s.phase == SCORES
        val inTurn = s.phase in TURN_PHASES
        return DoodleTv(
            phase = s.phase,
            turn = s.turn,
            totalTurns = s.totalTurns,
            finalTurn = s.turn == s.totalTurns,
            drawer = if (inTurn) s.drawer else null,
            drawerName = if (inTurn) name(s.drawer) else "",
            difficulty = if (s.phase == PICK || !inTurn) 0 else s.difficulty,
            blanks = if (s.phase == DRAW) DoodleRules.blanks(s.word, s.revealed) else "",
            guessed = if (inTurn && s.phase != PICK) s.correct.size else 0,
            expected = if (inTurn) guessers(s).size else 0,
            drawMs = if (s.phase == DRAW) s.drawMs else 0,
            tailMs = tail(s),
            solvers = if (inTurn && s.phase != PICK) {
                s.correct.map { DoodleSolver(PlayerId(it), name(PlayerId(it)), if (shown) s.points[it] else null) }
            } else emptyList(),
            wrong = if (s.phase == DRAW) s.misses.map { DoodleMissTv(PlayerId(it.id), name(PlayerId(it.id)), it.text) } else emptyList(),
            missTotal = s.missTotal,
            word = if (shown) s.word else null,
            drinks = if (shown) drinks(s, ctx) else emptyList(),
            deltas = if (s.phase == SCORES) {
                s.deltas.map { (id, pts) -> DoodleDelta(PlayerId(id), name(PlayerId(id)), pts) }.sortedByDescending { it.points }
            } else emptyList(),
            gallery = if (s.phase == PODIUM) {
                s.done.map { d -> DoodleShot(d.turn, d.word, PlayerId(d.drawer), name(PlayerId(d.drawer)), d.first?.let(::PlayerId), d.first?.let { name(PlayerId(it)) }) }
            } else emptyList(),
        )
    }

    private fun drinks(s: DoodleState, ctx: GameContext): List<DoodleDrink> {
        if ((ctx.settings["drinks"] ?: 1) == 0) return emptyList()
        val drawer = s.drawer?.let { ctx.player(it) }
        if (s.correct.isEmpty()) {
            return listOfNotNull(drawer?.let { DoodleDrink(it.id, it.name, 2, "Nobody got it! Drink 2 sips${ofWater(it.water)}") })
        }
        return guessers(s).filter { it.v !in s.correct }.mapNotNull { id ->
            ctx.player(id)?.let { DoodleDrink(it.id, it.name, 1, "Missed it. Drink 1 sip${ofWater(it.water)}") }
        }
    }

    override fun playerView(s: DoodleState, who: PlayerId, ctx: GameContext): Screen {
        if (s.phase == PODIUM) return Screen.Scores("Final scores", rows(ctx))
        if (who !in s.participants) return Screen.Waiting("You're in next turn", "Watch the TV and get ready")
        val drawerName = s.drawer?.let { ctx.player(it)?.name } ?: "?"
        val guessed = s.correct.size
        val expected = guessers(s).size
        return when (s.phase) {
            PICK ->
                if (who == s.drawer) Screen.ChoiceList("Pick a word to draw", s.options.map { Choice(it.id, it.word, detail = LEVELS[it.difficulty]) }, null, "pick")
                else Screen.Waiting("$drawerName is picking a word", "Get your guessing fingers ready")
            DRAW ->
                if (who == s.drawer) Screen.Draw(s.word, s.difficulty, guessed, expected, tail(s), "Draw it! No letters or numbers.")
                else Screen.Guess(
                    drawer = drawerName, blanks = DoodleRules.blanks(s.word, s.revealed), kind = "guess",
                    solved = who.v in s.correct, points = s.points[who.v], close = who.v in s.close, last = s.last[who.v],
                    guessed = guessed, expected = expected, tailMs = tail(s),
                )
            REVEAL -> revealScreen(s, who, ctx)
            else -> Screen.Scores("Turn ${s.turn} scores", rows(ctx))
        }
    }

    private fun revealScreen(s: DoodleState, who: PlayerId, ctx: GameContext): Screen {
        val gained = s.deltas[who.v] ?: 0
        val title = "It was ${s.word.uppercase()}"
        val drink = drinks(s, ctx).firstOrNull { it.id == who }?.text
        val detail = when {
            drink != null -> drink
            gained > 0 -> "+$gained"
            else -> "Eyes on the TV"
        }
        return Screen.Waiting(title, detail, tone = if (gained > 0) "win" else if (drink != null) "lose" else "neutral")
    }

    private fun rows(ctx: GameContext) =
        ctx.players.map { ScoreRow(it.id, it.name, it.avatar, ctx.scores[it.id] ?: 0) }.sortedByDescending { it.score }

    companion object {
        const val PICK = "pick"
        const val DRAW = "draw"
        const val REVEAL = "reveal"
        const val SCORES = "scores"
        const val PODIUM = "podium"
        private val TURN_PHASES = setOf(PICK, DRAW, REVEAL, SCORES)
        private val LEVELS = mapOf(1 to "Easy", 2 to "Medium", 3 to "Hard")
        const val DEFAULT_TURNS = 5
        const val MAX_GUESS = 40
        const val MAX_MISSES = 8
        const val PICK_MS = 12_000L
        const val DRAW_MS = 75_000L
        const val REVEAL_MS = 7_000L
        const val SCORES_MS = 6_000L
        const val PODIUM_MS = 24_000L
    }
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `cd tv && ./gradlew :engine:test --tests "partyos.engine.games.doodle.*" -q`
Expected: PASS. If the near-miss test fails because a word is too short for three replaced letters, check that the word under test has at least 7 letters (all Medium words in the test pack do).

- [ ] **Step 7: Run the whole engine suite**

Run: `cd tv && ./gradlew :engine:test -q`
Expected: PASS (nothing else changed behaviour).

- [ ] **Step 8: Commit**

```bash
git add tv/engine/src/main/kotlin/partyos/engine/games/doodle/Doodle.kt tv/engine/src/main/kotlin/partyos/engine/Game.kt tv/engine/src/main/kotlin/partyos/engine/PartyEngine.kt tv/engine/src/test/kotlin/partyos/engine/games/doodle/DoodleTest.kt
git commit -m "feat(doodle): the game module with turns, hints, guessing, scoring and ink permission"
```

---

### Task 5: The ink board (server-side stroke store and validator)

**Files:**
- Create: `tv/server/src/main/kotlin/partyos/server/Ink.kt`
- Modify: `tv/server/src/main/kotlin/partyos/server/Protocol.kt` (new messages)
- Test: `tv/server/src/test/kotlin/partyos/server/InkBoardTest.kt`

**Interfaces:**
- Produces (used by Tasks 6, 7, 9):
  - `sealed interface InkOp` with `Start(s, c, w, x, y, p = 50)`, `Pts(s, pts)`, `End(s)`, `Undo`, `Clear`; wire tag `t` = `start | pts | end | undo | clear`.
  - `InkStroke(s, c, w, pts, open = false)` and `InkTurn(turn, strokes)`.
  - `InkBoard`: `apply(turn: Int, ops: List<InkOp>): Pair<Int, List<InkOp>>?` (the sequence number and the ops that were valid, or null if none), `sync(): ServerMsg.InkSync`, `reset()`, `seq: Int`.
  - `ClientMsg.Ink(round: Int, ops: List<InkOp>)` (tag `ink`), `ServerMsg.Ink(turn: Int, n: Int, ops: List<InkOp>)` (tag `ink`), `ServerMsg.InkSync(turns: List<InkTurn>, upTo: Int)` (tag `inkSync`).
  - Limits: `InkBoard.MAX_OPS = 64`, `MAX_NUMBERS = 400`, `MAX_STROKES = 200`, `MAX_POINTS = 40_000`, `COLOURS = 8`, `BRUSHES = 3`, `WIDTH = 1000`, `HEIGHT = 750`.

- [ ] **Step 1: Write the failing test**

```kotlin
package partyos.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InkBoardTest {
    private fun start(s: Int, c: Int = 0, w: Int = 0, x: Int = 10, y: Int = 10, p: Int = 50) = InkOp.Start(s, c, w, x, y, p)
    private fun InkBoard.strokes(turn: Int = 1) = sync().turns.firstOrNull { it.turn == turn }?.strokes ?: emptyList()

    @Test fun aStrokeGrowsAndCloses() {
        val b = InkBoard()
        b.apply(1, listOf(start(1, c = 2, w = 1, x = 100, y = 100)))
        b.apply(1, listOf(InkOp.Pts(1, listOf(110, 105, 50, 120, 110, 60))))
        assertEquals(listOf(InkStroke(1, 2, 1, listOf(100, 100, 50, 110, 105, 50, 120, 110, 60), open = true)), b.strokes())
        b.apply(1, listOf(InkOp.End(1)))
        assertEquals(false, b.strokes().single().open)
    }

    @Test fun eachAcceptedBatchGetsTheNextSequenceNumberAndOnlyTheValidOpsComeBack() {
        val b = InkBoard()
        val (n1, ops1) = b.apply(1, listOf(start(1), InkOp.Pts(9, listOf(1, 1, 1))))!!
        assertEquals(1, n1)
        assertEquals(listOf(start(1)), ops1) // the pts for a stroke that doesn't exist were dropped
        assertNull(b.apply(1, listOf(InkOp.Pts(9, listOf(1, 1, 1))))) // nothing valid: no event, no number used
        assertEquals(2, b.apply(1, listOf(InkOp.End(1)))!!.first)
        assertEquals(2, b.seq)
    }

    @Test fun invalidOpsAreDropped() {
        val b = InkBoard()
        assertNull(b.apply(1, listOf(start(1, x = 1001))))
        assertNull(b.apply(1, listOf(start(1, y = 751))))
        assertNull(b.apply(1, listOf(start(1, x = -1))))
        assertNull(b.apply(1, listOf(start(1, p = 101))))
        assertNull(b.apply(1, listOf(start(1, c = 8))))
        assertNull(b.apply(1, listOf(start(1, w = 3))))
        b.apply(1, listOf(start(1)))
        assertNull(b.apply(1, listOf(start(1)))) // a stroke id is used once
        assertNull(b.apply(1, listOf(InkOp.Pts(1, listOf(1, 2)))))          // not whole points
        assertNull(b.apply(1, listOf(InkOp.Pts(1, emptyList()))))
        assertNull(b.apply(1, listOf(InkOp.Pts(1, listOf(2000, 1, 1)))))    // off the grid
        assertNull(b.apply(1, listOf(InkOp.Pts(1, List(402) { 5 }))))       // too many numbers at once
        assertNull(b.apply(1, emptyList()))
        assertNull(b.apply(1, List(65) { InkOp.Undo }))                     // too many ops in one message
        assertEquals(1, b.strokes().size)
    }

    @Test fun aClosedStrokeCannotGrowOrCloseAgain() {
        val b = InkBoard()
        b.apply(1, listOf(start(1), InkOp.End(1)))
        assertNull(b.apply(1, listOf(InkOp.Pts(1, listOf(5, 5, 5)))))
        assertNull(b.apply(1, listOf(InkOp.End(1))))
    }

    @Test fun undoRemovesTheLastStrokeAndClearRemovesAll() {
        val b = InkBoard()
        b.apply(1, listOf(start(1), InkOp.End(1), start(2), InkOp.End(2)))
        b.apply(1, listOf(InkOp.Undo))
        assertEquals(listOf(1), b.strokes().map { it.s })
        b.apply(1, listOf(start(3), InkOp.Clear))
        assertTrue(b.strokes().isEmpty())
        assertNull(b.apply(1, listOf(InkOp.Undo)))  // nothing to undo
        assertNull(b.apply(1, listOf(InkOp.Clear))) // nothing to clear
    }

    @Test fun aStrokeIdCanComeBackOnceItsStrokeWasUndone() {
        val b = InkBoard()
        b.apply(1, listOf(start(1), InkOp.Undo))
        assertNotNull(b.apply(1, listOf(start(1))))
    }

    /** The same sequence and result are pinned in the controller's board.test.ts, so both reducers agree. */
    @Test fun theGoldenSequenceEndsWhereTheControllerDoes() {
        val b = InkBoard()
        val ops = listOf(
            start(1, 2, 1, 100, 100, 50), InkOp.Pts(1, listOf(110, 105, 50, 120, 110, 60)), InkOp.End(1),
            start(2, 0, 0, 500, 500, 50), InkOp.Pts(2, listOf(510, 500, 50)), InkOp.Undo,
            start(3, 7, 2, 10, 20, 30), InkOp.End(3), InkOp.End(3),
            InkOp.Undo, InkOp.Undo, InkOp.Undo,
            start(1, 1, 1, 1, 2, 3), InkOp.Clear, start(4, 3, 0, 900, 700, 100),
        )
        ops.forEach { b.apply(1, listOf(it)) }
        assertEquals(listOf(InkStroke(4, 3, 0, listOf(900, 700, 100), open = true)), b.strokes())
    }

    @Test fun aTurnHasAtMost200Strokes() {
        val b = InkBoard()
        repeat(200) { assertNotNull(b.apply(1, listOf(start(it)))) }
        assertNull(b.apply(1, listOf(start(200))))
    }

    @Test fun aTurnHasAtMost40000Points() {
        val b = InkBoard()
        b.apply(1, listOf(start(1)))
        val chunk = InkOp.Pts(1, List(399) { 5 }) // 133 points
        var points = 1
        while (b.apply(1, listOf(chunk)) != null) points += 133
        assertTrue(points <= InkBoard.MAX_POINTS, "$points points got in")
        assertTrue(points > InkBoard.MAX_POINTS - 133)
    }

    @Test fun turnsAreKeptSeparatelyAndOlderTurnsAreIgnored() {
        val b = InkBoard()
        b.apply(1, listOf(start(1)))
        b.apply(2, listOf(start(1)))
        assertEquals(false, b.strokes(1).single().open) // moving on closes the old turn's open stroke
        assertEquals(listOf(1, 2), b.sync().turns.map { it.turn })
        assertNull(b.apply(1, listOf(start(2)))) // a late batch for turn 1
    }

    @Test fun resetForgetsTheDrawingsButNotTheSequence() {
        val b = InkBoard()
        b.apply(1, listOf(start(1)))
        b.reset()
        assertTrue(b.sync().turns.isEmpty())
        assertEquals(1, b.sync().upTo)
        assertEquals(2, b.apply(1, listOf(start(1)))!!.first)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd tv && ./gradlew :server:test --tests "partyos.server.InkBoardTest" -q`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Add the wire messages to `Protocol.kt`**

In `sealed interface ClientMsg`, after `Action`:

```kotlin
    /** A drawer's strokes for the phase numbered [round]; fire-and-forget (no id, no ack, invalid batches are dropped). */
    @Serializable @SerialName("ink") data class Ink(val round: Int, val ops: List<InkOp>) : ClientMsg
```

In `sealed interface ServerMsg`, after `Tv`:

```kotlin
    /** Host (TV) sockets only. [n] is the server's running batch number, so a TV that just synced can skip what it has. */
    @Serializable @SerialName("ink") data class Ink(val turn: Int, val n: Int, val ops: List<InkOp>) : ServerMsg
    /** Host (TV) sockets only: everything drawn so far this game, sent when the socket connects. */
    @Serializable @SerialName("inkSync") data class InkSync(val turns: List<InkTurn>, val upTo: Int) : ServerMsg
```

- [ ] **Step 4: Implement `Ink.kt`**

```kotlin
package partyos.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One drawing operation. Coordinates are integers on a 1000 by 750 grid; pressure is 0-100. */
@Serializable
sealed interface InkOp {
    @Serializable @SerialName("start") data class Start(val s: Int, val c: Int, val w: Int, val x: Int, val y: Int, val p: Int = 50) : InkOp
    /** Flat [x, y, pressure, x, y, pressure, ...]. */
    @Serializable @SerialName("pts") data class Pts(val s: Int, val pts: List<Int>) : InkOp
    @Serializable @SerialName("end") data class End(val s: Int) : InkOp
    @Serializable @SerialName("undo") data object Undo : InkOp
    @Serializable @SerialName("clear") data object Clear : InkOp
}

@Serializable
data class InkStroke(val s: Int, val c: Int, val w: Int, val pts: List<Int>, val open: Boolean = false)

@Serializable
data class InkTurn(val turn: Int, val strokes: List<InkStroke>)

/**
 * Every drawing of the current game, turn by turn. Ink never touches game state: the drawer's phone sends batches, the
 * game says whether that player may draw right now, and this validates and keeps them so a TV that reconnects (or the
 * time-lapse and gallery) can rebuild the picture. Not thread-safe: PartyHost calls it under its lock.
 */
class InkBoard {
    private class Stroke(val id: Int, val c: Int, val w: Int) {
        val pts = ArrayList<Int>()
        var open = true
    }

    private val turns = LinkedHashMap<Int, ArrayList<Stroke>>()
    private var current = -1

    /** Batches accepted so far this run; never goes back, not even on [reset]. */
    var seq = 0
        private set

    /**
     * Applies [ops] to [turn]'s drawing. Returns the new batch number and the ops that were valid (dropped ones are
     * simply not there), or null when nothing was valid, the message was malformed, or [turn] is older than the current one.
     */
    fun apply(turn: Int, ops: List<InkOp>): Pair<Int, List<InkOp>>? {
        if (ops.isEmpty() || ops.size > MAX_OPS || turn < current) return null
        if (turn != current) {
            turns[current]?.forEach { it.open = false }
            current = turn
        }
        val strokes = turns.getOrPut(turn) { ArrayList() }
        val accepted = ops.filter { accept(strokes, it) }
        if (accepted.isEmpty()) return null
        seq += 1
        return seq to accepted
    }

    fun sync() = ServerMsg.InkSync(
        turns.map { (turn, strokes) -> InkTurn(turn, strokes.map { InkStroke(it.id, it.c, it.w, it.pts.toList(), it.open) }) },
        seq,
    )

    fun reset() {
        turns.clear()
        current = -1
    }

    private fun total(strokes: List<Stroke>) = strokes.sumOf { it.pts.size / 3 }

    private fun onGrid(x: Int, y: Int, p: Int) = x in 0..WIDTH && y in 0..HEIGHT && p in 0..100

    private fun accept(strokes: ArrayList<Stroke>, op: InkOp): Boolean = when (op) {
        is InkOp.Start ->
            if (strokes.size >= MAX_STROKES || total(strokes) >= MAX_POINTS || op.c !in 0 until COLOURS || op.w !in 0 until BRUSHES ||
                !onGrid(op.x, op.y, op.p) || strokes.any { it.id == op.s }
            ) false
            else {
                strokes += Stroke(op.s, op.c, op.w).also { it.pts += listOf(op.x, op.y, op.p) }
                true
            }
        is InkOp.Pts -> {
            val stroke = strokes.firstOrNull { it.id == op.s && it.open }
            when {
                stroke == null || op.pts.isEmpty() || op.pts.size % 3 != 0 || op.pts.size > MAX_NUMBERS -> false
                total(strokes) + op.pts.size / 3 > MAX_POINTS -> false
                !op.pts.indices.step(3).all { onGrid(op.pts[it], op.pts[it + 1], op.pts[it + 2]) } -> false
                else -> { stroke.pts += op.pts; true }
            }
        }
        is InkOp.End -> strokes.firstOrNull { it.id == op.s && it.open }?.let { it.open = false; true } ?: false
        InkOp.Undo -> if (strokes.isEmpty()) false else { strokes.removeAt(strokes.lastIndex); true }
        InkOp.Clear -> if (strokes.isEmpty()) false else { strokes.clear(); true }
    }

    companion object {
        const val MAX_OPS = 64
        const val MAX_NUMBERS = 400
        const val MAX_STROKES = 200
        const val MAX_POINTS = 40_000
        const val COLOURS = 8
        const val BRUSHES = 3
        const val WIDTH = 1000
        const val HEIGHT = 750
    }
}
```

Note for `theTurnHasAtMost40000Points`: `Pts` with 399 numbers = 133 points; the loop stops at the first refusal, which is within one chunk of the cap.

- [ ] **Step 5: Run to verify it passes**

Run: `cd tv && ./gradlew :server:test --tests "partyos.server.InkBoardTest" -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add tv/server/src/main/kotlin/partyos/server/Ink.kt tv/server/src/main/kotlin/partyos/server/Protocol.kt tv/server/src/test/kotlin/partyos/server/InkBoardTest.kt
git commit -m "feat(doodle): ink board with strict validation, and the ink wire messages"
```

---

### Task 6: Relay ink to the TV, register the game, pin the wire format

**Files:**
- Modify: `tv/server/src/main/kotlin/partyos/server/PartyHost.kt`
- Modify: `tv/server/src/main/kotlin/partyos/server/PartyModule.kt`
- Modify: `tv/devserver/src/main/kotlin/partyos/devserver/Main.kt`
- Modify: `tv/server/src/test/kotlin/partyos/server/ProtocolFixturesTest.kt`
- Regenerate: `controller/src/protocol/fixtures/client-messages.json`, `server-messages.json`
- Test: `tv/server/src/test/kotlin/partyos/server/InkRoutesTest.kt`

**Interfaces:**
- Consumes: `PartyEngine.inkTurn` (Task 4), `InkBoard`, the ink messages (Task 5).
- Produces (used by Tasks 7, 9): the socket contract. A phone sends `{"t":"ink","round":N,"ops":[...]}`; a host socket first receives one `{"t":"inkSync","turns":[...],"upTo":K}` then `{"t":"ink","turn":T,"n":K+1,...}` batches in order. Phones never receive ink. `PartyHost.ink(who, round, ops)`, `PartyHost.inkSync()`, `PartyHost.inkEvents: SharedFlow<ServerMsg.Ink>`.

- [ ] **Step 1: Write the failing test**

```kotlin
package partyos.server

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.DoodleTv
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.doodle.Doodle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InkRoutesTest {
    private fun doodleHost(): PartyHost {
        val engine = PartyEngine(SystemClock, SecureEntropy(), GameRegistry(listOf(Doodle())))
        engine.setPin("1234")
        return PartyHost(engine, SystemClock, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    private class Party(val host: PartyHost, val tokens: Map<PlayerId, String>) {
        val tv get() = host.tv.value.stage!!.game as DoodleTv
        val round get() = host.tv.value.stage!!.phaseSeq
        val drawer get() = tv.drawer!!
        val guesser get() = tokens.keys.first { it != drawer }
    }

    /** Three joined, present players; Doodle started, tutorial skipped, the drawer has picked and the draw is on. */
    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.drawing(host: PartyHost): Party {
        serve(host)
        val room = host.tv.value.roomCode
        val tokens = (1..3).associate { i ->
            val token = tokenOf(client.join(room, "P$i").second)
            host.read { resolve(token)!! } to token
        }
        tokens.keys.forEach { host.connected(it) }
        host.hostCommand(null, HostCmd.StartGame("doodle", mapOf("rounds" to 3)))
        host.hostCommand(null, HostCmd.SkipPhase)
        val party = Party(host, tokens)
        val drawerView = host.read { phoneState(party.drawer).screen }
        val pick = assertIs<Screen.ChoiceList>(drawerView).options.first()
        host.mutate {
            action(party.drawer, "pick1", party.round, buildJsonObject { put("kind", JsonPrimitive("pick")); put("option", JsonPrimitive(pick.id)) })
        }
        assertEquals("draw", party.tv.phase)
        return party
    }

    private val stroke = listOf(InkOp.Start(1, 0, 0, 10, 10, 50), InkOp.Pts(1, listOf(20, 20, 50)), InkOp.End(1))

    @Test fun theDrawersStrokesReachTheTvAndOnlyTheirs() = testApplication {
        val host = doodleHost()
        val p = drawing(host)
        val c = createClient { install(WebSockets) }
        val hostToken = host.issueHostToken()
        c.webSocket("/ws?host=$hostToken") {
            val tvSocket: DefaultClientWebSocketSession = this
            assertEquals(emptyList(), nextOf<ServerMsg.InkSync>().turns)
            c.webSocket("/ws?token=${p.tokens.getValue(p.guesser)}") {
                nextOf<ServerMsg.View>()
                sendMsg(ClientMsg.Ink(p.round, stroke)) // not the drawer: dropped
            }
            c.webSocket("/ws?token=${p.tokens.getValue(p.drawer)}") {
                nextOf<ServerMsg.View>()
                sendMsg(ClientMsg.Ink(p.round - 1, stroke)) // an old phase: dropped
                sendMsg(ClientMsg.Ink(p.round, stroke))
                val ink = tvSocket.nextOf<ServerMsg.Ink>()
                assertEquals(1, ink.turn)
                assertEquals(1, ink.n) // the two dropped batches used no number
                assertEquals(stroke, ink.ops)
                // Phones never get ink, not even their own.
                assertNull(withTimeoutOrNull(300) { nextOf<ServerMsg.Ink>() })
            }
        }
    }

    @Test fun aTvThatConnectsLateGetsTheDrawingSoFar() = testApplication {
        val host = doodleHost()
        val p = drawing(host)
        val c = createClient { install(WebSockets) }
        c.webSocket("/ws?token=${p.tokens.getValue(p.drawer)}") {
            nextOf<ServerMsg.View>()
            sendMsg(ClientMsg.Ink(p.round, stroke))
            withTimeoutOrNull(3_000) { while (host.inkSync().upTo < 1) kotlinx.coroutines.delay(20) }
        }
        c.webSocket("/ws?host=${host.issueHostToken()}") {
            val sync = nextOf<ServerMsg.InkSync>()
            assertEquals(1, sync.upTo)
            assertEquals(listOf(InkStroke(1, 0, 0, listOf(10, 10, 50, 20, 20, 50), open = false)), sync.turns.single().strokes)
        }
    }

    @Test fun inkDoesNotChangeGameStateOrPushViews() = testApplication {
        val host = doodleHost()
        val p = drawing(host)
        val before = host.version.value
        host.ink(p.drawer, p.round, stroke)
        assertEquals(before, host.version.value)
    }

    @Test fun aFloodOfInkIsCappedByTheRateLimit() = testApplication {
        val host = doodleHost()
        val p = drawing(host)
        createClient { install(WebSockets) }.webSocket("/ws?token=${p.tokens.getValue(p.drawer)}") {
            nextOf<ServerMsg.View>()
            repeat(100) { i -> sendMsg(ClientMsg.Ink(p.round, listOf(InkOp.Start(i + 1, 0, 0, 10, 10, 50)))) }
            kotlinx.coroutines.delay(600)
        }
        val strokes = host.inkSync().turns.single().strokes.size
        assertTrue(strokes in 30..60, "$strokes of 100 batches got through") // a burst of 40, then 25 a second
    }

    @Test fun theDrawingsGoWhenTheGameEnds() = testApplication {
        val host = doodleHost()
        val p = drawing(host)
        host.ink(p.drawer, p.round, stroke)
        assertTrue(host.inkSync().turns.isNotEmpty())
        host.hostCommand(null, HostCmd.EndGame)
        assertTrue(host.inkSync().turns.isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd tv && ./gradlew :server:test --tests "partyos.server.InkRoutesTest" -q`
Expected: FAIL (unresolved `host.ink`, `inkSync`).

- [ ] **Step 3: `PartyHost` keeps the board and publishes batches**

Add imports `kotlinx.coroutines.flow.MutableSharedFlow`, `kotlinx.coroutines.flow.SharedFlow`, `kotlinx.coroutines.flow.asSharedFlow`. In the class body, next to the other flows:

```kotlin
    private val inkBoard = InkBoard()
    private val _ink = MutableSharedFlow<ServerMsg.Ink>(extraBufferCapacity = 4096)

    /** Accepted ink batches, in order, for host (TV) sockets. */
    val inkEvents: SharedFlow<ServerMsg.Ink> = _ink.asSharedFlow()
```

After `mutate`/`read`:

```kotlin
    /**
     * A drawer's strokes: checked against the game, kept for TV reconnects and relayed to the TV. This never changes
     * game state and never pushes a view, so drawing costs nothing on the phones.
     */
    suspend fun ink(who: PlayerId, round: Int, ops: List<InkOp>) {
        mutex.withLock {
            val turn = engine.inkTurn(who, round) ?: return@withLock
            val (n, accepted) = inkBoard.apply(turn, ops) ?: return@withLock
            _ink.tryEmit(ServerMsg.Ink(turn, n, accepted))
        }
    }

    /** Everything drawn this game, and the batch number it is up to date with. */
    suspend fun inkSync(): ServerMsg.InkSync = mutex.withLock { inkBoard.sync() }
```

In `commit()`, before `scheduleDeadline()`:

```kotlin
        if (_tv.value.stage == null) inkBoard.reset() // the drawings belong to the game that just ended
```

- [ ] **Step 4: `PartySession` routes and relays**

In `PartyModule.kt` add imports `kotlinx.coroutines.CoroutineStart`, `kotlinx.coroutines.channels.Channel`, `kotlinx.coroutines.coroutineScope`. In `PartySession`:

```kotlin
    private val inkBucket = TokenBucket(40, 25.0, cfg.clock::now)
```

In `run()`, after `val sender = ...`:

```kotlin
        val inkRelay = if (!isHost) null else ws.launch { relayInk() }
```

and in the `finally` block, `inkRelay?.cancel()` next to `sender.cancel()`.

In `handle(msg)`'s `when`, add before `is ClientMsg.Host`:

```kotlin
            is ClientMsg.Ink -> if (pid != null && inkBucket.tryTake()) host.ink(pid, msg.round, msg.ops)
```

Add the relay to the class:

```kotlin
    /**
     * Sends a TV socket the whole drawing, then every new batch. It subscribes before it syncs so nothing slips between
     * the two; batches the sync already contains are skipped by their number.
     */
    private suspend fun relayInk() = coroutineScope {
        val queue = Channel<ServerMsg.Ink>(Channel.UNLIMITED)
        launch(start = CoroutineStart.UNDISPATCHED) { host.inkEvents.collect { queue.trySend(it) } }
        val sync = host.inkSync()
        ws.send(sync)
        for (event in queue) if (event.n > sync.upTo) ws.send(event)
    }
```

- [ ] **Step 5: Register the game**

In `Main.kt` add `import partyos.engine.games.doodle.Doodle` and append `Doodle()` to the registry list (after `Imposter()`).

- [ ] **Step 6: Run the route tests**

Run: `cd tv && ./gradlew :server:test --tests "partyos.server.InkRoutesTest" -q`
Expected: PASS. If `theDrawersStrokesReachTheTvAndOnlyTheirs` hangs on `nextOf<ServerMsg.Ink>()`, print what the TV socket received: the usual cause is the drawer's `round` not matching `phaseSeq` (read `p.round` after the pick, as the test does).

- [ ] **Step 7: Add wire samples**

In `ProtocolFixturesTest.kt` add the imports (`partyos.engine.DoodleTv`, `DoodleSolver`, `DoodleMissTv`) and append to `serverMessages`:

```kotlin
        view(Screen.Draw("pizza", 2, 1, 4, 45_000, "Draw it! No letters or numbers."), round = 4),
        view(Screen.Guess("Al", "P _ _ _ A", "guess", solved = false, close = true, last = "pizqq", guessed = 1, expected = 4, tailMs = 22_500), round = 4),
        ServerMsg.Ink(1, 7, listOf(InkOp.Start(1, 2, 1, 100, 100, 50), InkOp.Pts(1, listOf(110, 105, 50, 120, 110, 60)), InkOp.End(1), InkOp.Undo, InkOp.Clear)),
        ServerMsg.InkSync(listOf(InkTurn(1, listOf(InkStroke(1, 2, 1, listOf(100, 100, 50, 110, 105, 50), open = false), InkStroke(2, 0, 0, listOf(5, 5, 20), open = true)))), 7),
        ServerMsg.Tv(
            15,
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
```

and to `clientMessages`:

```kotlin
        ClientMsg.Ink(4, listOf(InkOp.Start(1, 2, 1, 100, 100, 50), InkOp.Pts(1, listOf(110, 105, 50)), InkOp.End(1), InkOp.Undo, InkOp.Clear)),
```

- [ ] **Step 8: Regenerate the shared fixtures**

```bash
cd tv && ./gradlew :server:test --tests "partyos.server.ProtocolFixturesTest" -PupdateFixtures -q
cd .. && git diff --stat controller/src/protocol/fixtures
```

Expected: both JSON files changed (new entries only, nothing existing altered). Then run the whole server suite: `cd tv && ./gradlew :server:test -q` → PASS.

- [ ] **Step 9: Commit**

```bash
git add tv/server tv/devserver controller/src/protocol/fixtures
git commit -m "feat(doodle): relay ink to the TV, register Doodle Dash, pin the wire format"
```

---

### Task 7: Controller protocol and the shared ink library

**Files:**
- Create: `controller/src/ink/types.ts`, `ink/board.ts`, `ink/codec.ts`, `ink/paint.ts`, `ink/store.ts`, `ink/blanks.ts`
- Test: `controller/src/ink/board.test.ts`, `ink/codec.test.ts`, `ink/paint.test.ts`, `ink/store.test.ts`, `ink/blanks.test.ts`
- Modify: `controller/src/protocol.ts`, `controller/src/net/connection.ts`, `controller/src/net/connection.test.ts`, `controller/src/protocol.test.ts`

**Interfaces:**
- Consumes: the wire format from Tasks 5-6 and the regenerated fixtures.
- Produces (used by Tasks 8-10):
  - `ink/types.ts`: `INK_W = 1000`, `INK_H = 750`, `CRAYONS: readonly string[]` (8), `CRAYON_NAMES`, `BRUSHES = [6, 14, 30]`, `BRUSH_NAMES`, `type InkOp`, `interface Stroke { s; c; w; pts: number[]; open: boolean }`, `interface InkSyncMsg`.
  - `ink/board.ts`: `applyOp(strokes: Stroke[], op: InkOp): void`, `applyOps(strokes, ops): Stroke[]`.
  - `ink/codec.ts`: `class InkBatcher { start(id,c,w,x,y,p); point(x,y,p); end(); op(o); take(): InkOp[] }`, `strokeOps(st: Stroke): InkOp[]`, `chunkOps(ops: InkOp[], size = 60): InkOp[][]`, `toGrid(clientX, clientY, rect): [number, number]`, `pressureOf(e: {pointerType: string; pressure: number}): number`.
  - `ink/paint.ts`: `paintStroke(ctx, st, k, from?, to?, tail?)`, `paintAll(ctx, strokes, k, w, h)`, `paintUpTo(ctx, strokes, k, fraction, w, h)`.
  - `ink/store.ts`: `class InkStore { strokes(turn): Stroke[]; turns(): number[]; applyEvent(m); applySync(m); reset(); subscribe(fn): () => void; version: number }`.
  - `ink/blanks.ts`: `blankCells(blanks: string): string[][]` (words of cells, `'_'` for a blank).
  - `protocol.ts`: `Screen` variants `draw` and `guess`; `ClientMsg` `{t:'ink'; round; ops}`; `ServerMsg` `{t:'ink'; turn; n; ops}` and `{t:'inkSync'; turns; upTo}`; `Connection.ink(round, ops)`.

- [ ] **Step 1: Write the failing tests**

`ink/board.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { applyOp, applyOps } from './board'
import type { InkOp, Stroke } from './types'

describe('ink board reducer', () => {
  it('grows and closes a stroke', () => {
    const strokes: Stroke[] = []
    applyOps(strokes, [
      { t: 'start', s: 1, c: 2, w: 1, x: 100, y: 100, p: 50 },
      { t: 'pts', s: 1, pts: [110, 105, 50, 120, 110, 60] },
      { t: 'end', s: 1 },
    ])
    expect(strokes).toEqual([{ s: 1, c: 2, w: 1, pts: [100, 100, 50, 110, 105, 50, 120, 110, 60], open: false }])
  })

  it('undo and clear on an empty board do nothing', () => {
    const strokes: Stroke[] = []
    applyOp(strokes, { t: 'undo' })
    applyOp(strokes, { t: 'clear' })
    expect(strokes).toEqual([])
  })

  // The same sequence and result are pinned in InkBoardTest.kt (theGoldenSequenceEndsWhereTheControllerDoes).
  it('ends the golden sequence where the server does', () => {
    const ops: InkOp[] = [
      { t: 'start', s: 1, c: 2, w: 1, x: 100, y: 100, p: 50 }, { t: 'pts', s: 1, pts: [110, 105, 50, 120, 110, 60] }, { t: 'end', s: 1 },
      { t: 'start', s: 2, c: 0, w: 0, x: 500, y: 500, p: 50 }, { t: 'pts', s: 2, pts: [510, 500, 50] }, { t: 'undo' },
      { t: 'start', s: 3, c: 7, w: 2, x: 10, y: 20, p: 30 }, { t: 'end', s: 3 }, { t: 'end', s: 3 },
      { t: 'undo' }, { t: 'undo' }, { t: 'undo' },
      { t: 'start', s: 1, c: 1, w: 1, x: 1, y: 2, p: 3 }, { t: 'clear' }, { t: 'start', s: 4, c: 3, w: 0, x: 900, y: 700, p: 100 },
    ]
    const strokes: Stroke[] = []
    for (const op of ops) applyOp(strokes, op)
    expect(strokes).toEqual([{ s: 4, c: 3, w: 0, pts: [900, 700, 100], open: true }])
  })
})
```

`ink/codec.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { InkBatcher, chunkOps, pressureOf, strokeOps, toGrid } from './codec'
import type { Stroke } from './types'

describe('InkBatcher', () => {
  it('turns a stroke into start, pts and end', () => {
    const b = new InkBatcher()
    b.start(1, 2, 1, 10, 20, 50)
    b.point(11, 21, 50); b.point(12, 22, 60)
    b.end()
    expect(b.take()).toEqual([
      { t: 'start', s: 1, c: 2, w: 1, x: 10, y: 20, p: 50 },
      { t: 'pts', s: 1, pts: [11, 21, 50, 12, 22, 60] },
      { t: 'end', s: 1 },
    ])
    expect(b.take()).toEqual([])
  })

  it('keeps ops in order around points', () => {
    const b = new InkBatcher()
    b.start(1, 0, 0, 1, 1, 50); b.point(2, 2, 50); b.op({ t: 'undo' }); b.start(2, 0, 0, 3, 3, 50); b.point(4, 4, 50)
    expect(b.take().map((o) => o.t)).toEqual(['start', 'pts', 'undo', 'start', 'pts'])
  })

  it('flushes long runs so no pts op passes 300 numbers', () => {
    const b = new InkBatcher()
    b.start(1, 0, 0, 0, 0, 50)
    for (let i = 0; i < 250; i++) b.point(i % 1000, 5, 50)
    const pts = b.take().filter((o) => o.t === 'pts')
    expect(pts.length).toBeGreaterThan(1)
    for (const o of pts) expect((o as { pts: number[] }).pts.length).toBeLessThanOrEqual(300)
  })
})

describe('strokeOps and chunkOps', () => {
  it('rebuilds a stroke as ops, splitting long point lists and leaving open strokes open', () => {
    const st: Stroke = { s: 5, c: 1, w: 2, pts: Array.from({ length: 3 * 250 }, (_, i) => (i % 3 === 2 ? 50 : i % 900)), open: true }
    const ops = strokeOps(st)
    expect(ops[0]).toEqual({ t: 'start', s: 5, c: 1, w: 2, x: 0, y: 1, p: 50 })
    expect(ops.some((o) => o.t === 'end')).toBe(false)
    const numbers = ops.filter((o) => o.t === 'pts').flatMap((o) => (o as { pts: number[] }).pts)
    expect(numbers).toEqual(st.pts.slice(3))
    expect(strokeOps({ ...st, open: false }).at(-1)).toEqual({ t: 'end', s: 5 })
  })

  it('chunks ops for the 64-op server limit', () => {
    const ops = Array.from({ length: 130 }, () => ({ t: 'undo' as const }))
    expect(chunkOps(ops).map((c) => c.length)).toEqual([60, 60, 10])
    expect(chunkOps([])).toEqual([])
  })
})

describe('pointer helpers', () => {
  const rect = { left: 100, top: 50, width: 400, height: 300 } as DOMRect
  it('maps client pixels onto the 1000 by 750 grid and clamps', () => {
    expect(toGrid(300, 200, rect)).toEqual([500, 375])
    expect(toGrid(0, 0, rect)).toEqual([0, 0])
    expect(toGrid(9999, 9999, rect)).toEqual([1000, 750])
  })
  it('uses pen pressure only when there is a pen', () => {
    expect(pressureOf({ pointerType: 'pen', pressure: 0.8 })).toBe(80)
    expect(pressureOf({ pointerType: 'pen', pressure: 0 })).toBe(50)
    expect(pressureOf({ pointerType: 'touch', pressure: 1 })).toBe(50)
    expect(pressureOf({ pointerType: 'mouse', pressure: 0.5 })).toBe(50)
  })
})
```

`ink/paint.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { paintAll, paintStroke, paintUpTo } from './paint'
import { CRAYONS, type Stroke } from './types'

/** A canvas context that only writes down what it is asked to draw. */
function recorder() {
  const calls: string[] = []
  const ctx = {
    strokeStyle: '', fillStyle: '', lineWidth: 0, lineCap: '', lineJoin: '',
    beginPath: () => calls.push('begin'), moveTo: () => calls.push('move'), lineTo: () => calls.push('line'),
    quadraticCurveTo: () => calls.push('curve'), stroke: () => calls.push('stroke'), arc: () => calls.push('arc'),
    fill: () => calls.push('fill'), clearRect: () => calls.push('clear'),
  }
  return { ctx: ctx as unknown as CanvasRenderingContext2D, calls, raw: ctx }
}
const stroke = (n: number, open = false, c = 3): Stroke => ({ s: 1, c, w: 1, open, pts: Array.from({ length: n * 3 }, (_, i) => (i % 3 === 2 ? 50 : 100 + i * 7)) })
const count = (calls: string[], what: string) => calls.filter((c) => c === what).length

describe('paintStroke', () => {
  it('draws a tap as a dot', () => {
    const { ctx, calls } = recorder()
    paintStroke(ctx, stroke(1, true), 1)
    expect(calls).toEqual(['begin', 'arc', 'fill'])
  })

  it('draws one curve per new point and closes a finished stroke with a straight run', () => {
    const closed = recorder()
    paintStroke(closed.ctx, stroke(3), 1)
    expect(count(closed.calls, 'curve')).toBe(2)
    expect(count(closed.calls, 'line')).toBe(1)
    const open = recorder()
    paintStroke(open.ctx, stroke(3, true), 1)
    expect(count(open.calls, 'curve')).toBe(2)
    expect(count(open.calls, 'line')).toBe(0)
  })

  it('paints only the new points when asked to continue', () => {
    const { ctx, calls } = recorder()
    paintStroke(ctx, stroke(5, true), 1, 3, 5, false)
    expect(count(calls, 'curve')).toBe(2)
  })

  it('uses the stroke colour', () => {
    const { ctx, raw } = recorder()
    paintStroke(ctx, stroke(2, true, 5), 1)
    expect(raw.strokeStyle).toBe(CRAYONS[5])
  })
})

describe('paintAll and paintUpTo', () => {
  it('clears first, then paints every stroke', () => {
    const { ctx, calls } = recorder()
    paintAll(ctx, [stroke(2), stroke(2)], 1, 100, 75)
    expect(calls[0]).toBe('clear')
    expect(count(calls, 'curve')).toBe(2)
  })

  it('a time-lapse paints a share of the points', () => {
    const half = recorder()
    paintUpTo(half.ctx, [stroke(10), stroke(10)], 1, 0.5, 100, 75)
    const all = recorder()
    paintUpTo(all.ctx, [stroke(10), stroke(10)], 1, 1, 100, 75)
    expect(count(half.calls, 'curve')).toBeLessThan(count(all.calls, 'curve'))
    expect(count(half.calls, 'curve')).toBeGreaterThan(0)
    const none = recorder()
    paintUpTo(none.ctx, [stroke(10)], 1, 0, 100, 75)
    expect(count(none.calls, 'curve')).toBe(0)
  })
})
```

`ink/store.test.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'
import { InkStore } from './store'

const stroke = { t: 'start' as const, s: 1, c: 0, w: 0, x: 1, y: 2, p: 50 }

describe('InkStore', () => {
  it('builds a turn from live batches', () => {
    const s = new InkStore()
    s.applyEvent({ turn: 1, n: 1, ops: [stroke] })
    s.applyEvent({ turn: 1, n: 2, ops: [{ t: 'pts', s: 1, pts: [3, 4, 50] }, { t: 'end', s: 1 }] })
    expect(s.strokes(1)).toEqual([{ s: 1, c: 0, w: 0, pts: [1, 2, 50, 3, 4, 50], open: false }])
    expect(s.turns()).toEqual([1])
  })

  it('a sync replaces everything and live batches it already contains are skipped', () => {
    const s = new InkStore()
    s.applyEvent({ turn: 1, n: 1, ops: [stroke] })
    s.applySync({ turns: [{ turn: 1, strokes: [{ s: 1, c: 0, w: 0, pts: [1, 2, 50, 3, 4, 50], open: true }] }], upTo: 2 })
    s.applyEvent({ turn: 1, n: 2, ops: [{ t: 'pts', s: 1, pts: [9, 9, 50] }] }) // already in the sync
    expect(s.strokes(1)[0].pts).toEqual([1, 2, 50, 3, 4, 50])
    s.applyEvent({ turn: 1, n: 3, ops: [{ t: 'pts', s: 1, pts: [5, 6, 50] }] })
    expect(s.strokes(1)[0].pts).toEqual([1, 2, 50, 3, 4, 50, 5, 6, 50])
    s.applyEvent({ turn: 1, n: 4, ops: [{ t: 'end', s: 1 }] })
    expect(s.strokes(1)[0].open).toBe(false)
  })

  it('a synced stroke with no open flag is closed', () => {
    const s = new InkStore()
    s.applySync({ turns: [{ turn: 1, strokes: [{ s: 1, c: 0, w: 0, pts: [1, 2, 50] }] }], upTo: 1 })
    expect(s.strokes(1)[0].open).toBe(false)
  })

  it('a stroke the server said was open stays open', () => {
    const s = new InkStore()
    s.applySync({ turns: [{ turn: 2, strokes: [{ s: 1, c: 0, w: 0, pts: [1, 2, 50], open: true }] }], upTo: 1 })
    expect(s.strokes(2)[0].open).toBe(true)
  })

  it('reset forgets the drawings but not how far it got', () => {
    const s = new InkStore()
    s.applyEvent({ turn: 1, n: 5, ops: [stroke] })
    s.reset()
    expect(s.turns()).toEqual([])
    s.applyEvent({ turn: 1, n: 4, ops: [stroke] }) // older than what it saw: ignored
    expect(s.turns()).toEqual([])
  })

  it('tells subscribers when anything changes and lets them stop listening', () => {
    const s = new InkStore()
    const fn = vi.fn()
    const off = s.subscribe(fn)
    s.applyEvent({ turn: 1, n: 1, ops: [stroke] })
    s.applySync({ turns: [], upTo: 1 })
    s.reset()
    expect(fn).toHaveBeenCalledTimes(3)
    off()
    s.applyEvent({ turn: 1, n: 2, ops: [stroke] })
    expect(fn).toHaveBeenCalledTimes(3)
  })
})
```

`ink/blanks.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { blankCells } from './blanks'

describe('blankCells', () => {
  it('splits the server blanks into words of cells', () => {
    expect(blankCells('_ _ _ _ _')).toEqual([['_', '_', '_', '_', '_']])
    expect(blankCells('I _ _   _ _ _ _ _')).toEqual([['I', '_', '_'], ['_', '_', '_', '_', '_']])
    expect(blankCells('')).toEqual([])
  })
})
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd controller && npx vitest run src/ink`
Expected: FAIL (modules not found).

- [ ] **Step 3: Implement `ink/types.ts`**

```ts
/**
 * Ink is drawn on a 1000 by 750 grid whatever the screen, so the phone and the TV always agree.
 * Keep the grid, the counts and the op shapes in step with InkBoard.kt (tv/server).
 */
export const INK_W = 1000
export const INK_H = 750

/** The eight crayons; an op carries the index. */
export const CRAYONS = ['#3b2f2a', '#e5484d', '#f5851f', '#f7c331', '#3fa34d', '#2f8ff0', '#7c4dd6', '#ec5fa5'] as const
export const CRAYON_NAMES = ['Brown', 'Red', 'Orange', 'Yellow', 'Green', 'Blue', 'Purple', 'Pink'] as const
/** Brush widths in grid units; an op carries the index. */
export const BRUSHES = [6, 14, 30] as const
export const BRUSH_NAMES = ['Thin', 'Medium', 'Fat'] as const

export type InkOp =
  | { t: 'start'; s: number; c: number; w: number; x: number; y: number; p: number }
  | { t: 'pts'; s: number; pts: number[] }
  | { t: 'end'; s: number }
  | { t: 'undo' }
  | { t: 'clear' }

/** [pts] is flat: x, y, pressure (0-100), x, y, pressure, ... */
export interface Stroke { s: number; c: number; w: number; pts: number[]; open: boolean }

export interface InkSyncMsg {
  turns: { turn: number; strokes: { s: number; c: number; w: number; pts: number[]; open?: boolean }[] }[]
  upTo: number
}
```

- [ ] **Step 4: Implement `ink/board.ts`**

```ts
import type { InkOp, Stroke } from './types'

/** Applies one op to a turn's strokes in place. The same rules as InkBoard.kt, minus the validation. */
export function applyOp(strokes: Stroke[], op: InkOp): void {
  switch (op.t) {
    case 'start':
      strokes.push({ s: op.s, c: op.c, w: op.w, pts: [op.x, op.y, op.p], open: true })
      break
    case 'pts': {
      const st = strokes.find((k) => k.s === op.s && k.open)
      if (st) for (const n of op.pts) st.pts.push(n)
      break
    }
    case 'end': {
      const st = strokes.find((k) => k.s === op.s)
      if (st) st.open = false
      break
    }
    case 'undo':
      strokes.pop()
      break
    case 'clear':
      strokes.length = 0
      break
  }
}

export function applyOps(strokes: Stroke[], ops: InkOp[]): Stroke[] {
  for (const op of ops) applyOp(strokes, op)
  return strokes
}
```

- [ ] **Step 5: Implement `ink/codec.ts`**

```ts
import { INK_H, INK_W, type InkOp, type Stroke } from './types'

const MAX_PTS_NUMBERS = 300
const MAX_OPS_PER_MESSAGE = 60

/** Collects a stroke's points as the pen moves and hands them out as ops, in order, whenever asked. */
export class InkBatcher {
  private ops: InkOp[] = []
  private buf: number[] = []
  private sid = 0

  start(id: number, c: number, w: number, x: number, y: number, p: number) {
    this.flushPts()
    this.sid = id
    this.ops.push({ t: 'start', s: id, c, w, x, y, p })
  }

  point(x: number, y: number, p: number) {
    this.buf.push(x, y, p)
    if (this.buf.length >= MAX_PTS_NUMBERS) this.flushPts()
  }

  end() {
    this.flushPts()
    this.ops.push({ t: 'end', s: this.sid })
  }

  /** undo, clear and anything else that isn't a point. */
  op(o: InkOp) {
    this.flushPts()
    this.ops.push(o)
  }

  take(): InkOp[] {
    this.flushPts()
    const out = this.ops
    this.ops = []
    return out
  }

  private flushPts() {
    if (this.buf.length) {
      this.ops.push({ t: 'pts', s: this.sid, pts: this.buf })
      this.buf = []
    }
  }
}

/** A stroke as ops (start, points in chunks, end if it is closed): used to resend a drawing after a reconnect. */
export function strokeOps(st: Stroke): InkOp[] {
  const ops: InkOp[] = [{ t: 'start', s: st.s, c: st.c, w: st.w, x: st.pts[0], y: st.pts[1], p: st.pts[2] }]
  for (let i = 3; i < st.pts.length; i += MAX_PTS_NUMBERS) ops.push({ t: 'pts', s: st.s, pts: st.pts.slice(i, i + MAX_PTS_NUMBERS) })
  if (!st.open) ops.push({ t: 'end', s: st.s })
  return ops
}

/** The server takes at most 64 ops per message. */
export function chunkOps(ops: InkOp[], size = MAX_OPS_PER_MESSAGE): InkOp[][] {
  const out: InkOp[][] = []
  for (let i = 0; i < ops.length; i += size) out.push(ops.slice(i, i + size))
  return out
}

export function toGrid(clientX: number, clientY: number, rect: DOMRect): [number, number] {
  const x = Math.round(((clientX - rect.left) / rect.width) * INK_W)
  const y = Math.round(((clientY - rect.top) / rect.height) * INK_H)
  return [Math.min(INK_W, Math.max(0, x)), Math.min(INK_H, Math.max(0, y))]
}

/** Only a pen reports a pressure worth using; a finger or mouse gets a steady middle. */
export function pressureOf(e: { pointerType: string; pressure: number }): number {
  return e.pointerType === 'pen' && e.pressure > 0 ? Math.round(e.pressure * 100) : 50
}
```

- [ ] **Step 6: Implement `ink/paint.ts`**

```ts
import { BRUSHES, CRAYONS, INK_W, type Stroke } from './types'

const widthAt = (base: number, pressure: number) => base * (0.55 + 0.9 * (pressure / 100))

/** A crayon wobbles a little: the width breathes by up to 7% along the line. */
function segmentWidth(st: Stroke, i: number, k: number) {
  const jitter = 1 + 0.07 * Math.sin(i * 1.9 + st.s * 3.1)
  return widthAt(BRUSHES[st.w] ?? BRUSHES[1], st.pts[i * 3 + 2]) * jitter * k
}

/**
 * Draws points [from, to) of a stroke as smoothed curves: each segment runs midpoint to midpoint with the point as its
 * control, so a line drawn a few points at a time joins up seamlessly. [tail] adds the last straight run once the
 * stroke is finished. [k] is canvas pixels per grid unit.
 */
export function paintStroke(ctx: CanvasRenderingContext2D, st: Stroke, k: number, from = 0, to = st.pts.length / 3, tail = !st.open): void {
  const n = Math.min(to, st.pts.length / 3)
  if (n <= 0) return
  const colour = CRAYONS[st.c] ?? CRAYONS[0]
  ctx.strokeStyle = colour
  ctx.fillStyle = colour
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  const px = (i: number) => st.pts[i * 3] * k
  const py = (i: number) => st.pts[i * 3 + 1] * k
  const mid = (i: number, j: number): [number, number] => [(px(i) + px(j)) / 2, (py(i) + py(j)) / 2]
  if (n === 1) {
    if (from === 0) {
      ctx.beginPath()
      ctx.arc(px(0), py(0), segmentWidth(st, 0, k) / 2, 0, Math.PI * 2)
      ctx.fill()
    }
    return
  }
  for (let i = Math.max(from, 1); i < n; i++) {
    const a: [number, number] = i === 1 ? [px(0), py(0)] : mid(i - 2, i - 1)
    const b = mid(i - 1, i)
    ctx.lineWidth = segmentWidth(st, i, k)
    ctx.beginPath()
    ctx.moveTo(a[0], a[1])
    ctx.quadraticCurveTo(px(i - 1), py(i - 1), b[0], b[1])
    ctx.stroke()
  }
  if (tail && to >= st.pts.length / 3) {
    const a = mid(n - 2, n - 1)
    ctx.lineWidth = segmentWidth(st, n - 1, k)
    ctx.beginPath()
    ctx.moveTo(a[0], a[1])
    ctx.lineTo(px(n - 1), py(n - 1))
    ctx.stroke()
  }
}

/** Clears the canvas and redraws every stroke. */
export function paintAll(ctx: CanvasRenderingContext2D, strokes: Stroke[], k: number, w: number, h: number): void {
  ctx.clearRect(0, 0, w, h)
  for (const st of strokes) paintStroke(ctx, st, k)
}

/** A time-lapse frame: the first [fraction] (0-1) of all the points, in the order they were drawn. */
export function paintUpTo(ctx: CanvasRenderingContext2D, strokes: Stroke[], k: number, fraction: number, w: number, h: number): void {
  ctx.clearRect(0, 0, w, h)
  let budget = Math.floor(strokes.reduce((sum, st) => sum + st.pts.length / 3, 0) * Math.min(1, Math.max(0, fraction)))
  for (const st of strokes) {
    const n = st.pts.length / 3
    const take = Math.min(n, budget)
    if (take <= 0) break
    paintStroke(ctx, st, k, 0, take, take === n)
    budget -= take
  }
}

/** Pixels per grid unit for a canvas of the given width. */
export const scaleFor = (canvasWidth: number) => canvasWidth / INK_W
```

- [ ] **Step 7: Implement `ink/store.ts` and `ink/blanks.ts`**

```ts
import { applyOp } from './board'
import type { InkOp, InkSyncMsg, Stroke } from './types'

/**
 * The TV's copy of every drawing this game, built from the server's `inkSync` and `ink` messages. Batches carry the
 * server's running number, so a batch already contained in a sync is ignored and a reconnecting TV never doubles a stroke.
 */
export class InkStore {
  private byTurn = new Map<number, Stroke[]>()
  private upTo = 0
  private subs = new Set<() => void>()
  /** Bumps on every change; canvases repaint when it moves. */
  version = 0

  strokes(turn: number): Stroke[] {
    return this.byTurn.get(turn) ?? []
  }

  turns(): number[] {
    return [...this.byTurn.keys()].sort((a, b) => a - b)
  }

  applyEvent(m: { turn: number; n: number; ops: InkOp[] }) {
    if (m.n <= this.upTo) return
    this.upTo = m.n
    let list = this.byTurn.get(m.turn)
    if (!list) this.byTurn.set(m.turn, (list = []))
    for (const op of m.ops) applyOp(list, op)
    this.bump()
  }

  applySync(m: InkSyncMsg) {
    this.byTurn.clear()
    for (const t of m.turns) this.byTurn.set(t.turn, t.strokes.map((s) => ({ s: s.s, c: s.c, w: s.w, pts: [...s.pts], open: s.open ?? false })))
    this.upTo = m.upTo
    this.bump()
  }

  /** The game ended: forget the drawings. What it has seen so far is kept, so late batches stay ignored. */
  reset() {
    this.byTurn.clear()
    this.bump()
  }

  subscribe(fn: () => void): () => void {
    this.subs.add(fn)
    return () => { this.subs.delete(fn) }
  }

  private bump() {
    this.version += 1
    for (const fn of this.subs) fn()
  }
}
```

```ts
/** Splits the server's "_ _ _   _ _" (or "I _ _   _ _") into words of cells: '_' is a blank, anything else a shown letter. */
export function blankCells(blanks: string): string[][] {
  if (!blanks) return []
  return blanks.split('   ').map((word) => word.split(' ').filter((c) => c !== ''))
}
```

- [ ] **Step 8: Run to verify the ink tests pass**

Run: `cd controller && npx vitest run src/ink`
Expected: PASS.

- [ ] **Step 9: Extend `protocol.ts`**

Add at the top: `import type { InkOp, InkSyncMsg } from './ink/types'`. Then:

In `Screen`, after the `secret` variant:

```ts
  | { t: 'draw'; word: string; difficulty: number; guessed: number; expected: number; tailMs: number; note?: string }
  | { t: 'guess'; drawer: string; blanks: string; kind: string; solved: boolean; points?: number; close: boolean; last?: string; guessed: number; expected: number; tailMs: number }
```

In `ClientMsg`, after `action`:

```ts
  | { t: 'ink'; round: number; ops: InkOp[] }
```

In `ServerMsg`, after `tv`:

```ts
  | { t: 'ink'; turn: number; n: number; ops: InkOp[] }
  | ({ t: 'inkSync' } & InkSyncMsg)
```

Change the `SCREENS` set to include `'draw', 'guess'`. In `parseServerMsg`'s `switch`, before `default`:

```ts
    case 'ink': return typeof m.turn === 'number' && typeof m.n === 'number' && Array.isArray(m.ops) ? (m as ServerMsg) : null
    case 'inkSync': return Array.isArray(m.turns) && typeof m.upTo === 'number' ? (m as ServerMsg) : null
```

In `rejectMessage`, before the Home Turf comment:

```ts
    // Doodle Dash
    case 'NOT_GUESSING': return "You've already got it!"
    case 'BAD_OPTION': return 'Pick one of the words.'
```

- [ ] **Step 10: `Connection.ink`**

In `net/connection.ts` after `act(...)`:

```ts
  /** Strokes for the phase numbered [round]. Fire-and-forget: no id, no resend (the drawer's pad resends its drawing on reconnect). */
  ink(round: number, ops: InkOp[]) {
    this.send({ t: 'ink', round, ops })
  }
```

and add `type InkOp` to the imports from `'../protocol'`... `InkOp` lives in `../ink/types`: `import type { InkOp } from '../ink/types'`.

In `net/connection.test.ts` add inside `describe('Connection', ...)`:

```ts
  it('sends ink without keeping it for a resend', () => {
    const { c, sock } = setup()
    sock().open()
    c.ink(4, [{ t: 'clear' }])
    expect(sock().sentOf('ink')).toEqual([{ t: 'ink', round: 4, ops: [{ t: 'clear' }] }])
    sock().close(); vi.advanceTimersByTime(250); sock().open()
    expect(sock().sentOf('ink')).toEqual([])
    vi.useRealTimers()
  })
```

- [ ] **Step 11: Update `protocol.test.ts`**

In 'covers every screen kind', add `'draw', 'guess'` to the expected set. In `ours` (client samples), add as the last element, after `{ t: 'ping' }` (the Kotlin fixture list gets it last too):

```ts
      { t: 'ink', round: 4, ops: [{ t: 'start', s: 1, c: 2, w: 1, x: 100, y: 100, p: 50 }, { t: 'pts', s: 1, pts: [110, 105, 50] }, { t: 'end', s: 1 }, { t: 'undo' }, { t: 'clear' }] },
```

- [ ] **Step 12: Run the whole controller test suite and type-check**

Run: `cd controller && npx vitest run && npx tsc -b`
Expected: PASS, no type errors.

- [ ] **Step 13: Commit**

```bash
git add controller/src/ink controller/src/protocol.ts controller/src/protocol.test.ts controller/src/net
git commit -m "feat(doodle): controller protocol and the shared ink library (reducer, codec, painter, store)"
```

---

### Task 8: The phone: drawing pad and guess pad

**Files:**
- Create: `controller/src/screens/DrawPad.tsx`, `controller/src/screens/GuessPad.tsx`, `controller/src/screens/draw.css`
- Modify: `controller/src/screens/ScreenView.tsx`, `controller/src/pages/Play.tsx`

**Interfaces:**
- Consumes: `Screen` `draw`/`guess` and `Connection.ink` (Task 7), `InkBatcher`, `strokeOps`, `chunkOps`, `toGrid`, `pressureOf`, `paintStroke`, `paintAll`, `blankCells`, crayon constants.
- Produces: `ScreenView` gets an optional `ink?: { online: boolean; send(ops: InkOp[]): void }` prop (used by `Play`; the theme gallery omits it). CSS class names used by Task 10's e2e: `.draw-pad`, `.draw-canvas`, `.draw-word`, `.guess-pad`, `.blank-cells`.

The pad is an imperative canvas: React re-renders (every guess pushes a new view) must never clear it, so strokes live in refs and are painted directly.

- [ ] **Step 1: `DrawPad.tsx`**

```tsx
import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import type { Screen } from '../protocol'
import { InkBatcher, chunkOps, pressureOf, strokeOps, toGrid } from '../ink/codec'
import { paintAll, paintStroke, scaleFor } from '../ink/paint'
import { BRUSHES, BRUSH_NAMES, CRAYONS, CRAYON_NAMES, INK_H, INK_W, type InkOp, type Stroke } from '../ink/types'
import './draw.css'

type DrawScreen = Extract<Screen, { t: 'draw' }>

const LEVELS = ['', 'Easy', 'Medium', 'Hard']
const buzz = (ms: number) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }

/**
 * The drawer's canvas. Strokes are kept in refs and painted straight onto the canvas, so the view updates that arrive
 * with every guess never touch the drawing. Points go out in ~50 ms batches over the ink channel; if the socket drops
 * and comes back the whole drawing is resent, and a fresh pad (a page reload) tells the TV to start clean.
 */
export function DrawPad({ screen, online, sendInk }: { screen: DrawScreen; online: boolean; sendInk(ops: InkOp[]): void }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const strokes = useRef<Stroke[]>([])
  const batch = useRef(new InkBatcher())
  const nextId = useRef(1)
  const active = useRef<{ pointer: number } | null>(null)
  const timer = useRef<ReturnType<typeof setInterval> | null>(null)
  const scale = useRef(1)
  const send = useRef(sendInk)
  send.current = sendInk
  const [colour, setColour] = useState(0)
  const [brush, setBrush] = useState(1)
  const [count, setCount] = useState(0)

  const ctx = () => canvas.current?.getContext('2d') ?? null
  const flush = useCallback(() => {
    for (const c of chunkOps(batch.current.take())) send.current(c)
  }, [])

  const repaint = useCallback(() => {
    const el = canvas.current
    const c = ctx()
    if (el && c) paintAll(c, strokes.current, scale.current, el.width, el.height)
  }, [])

  const fit = useCallback(() => {
    const el = canvas.current
    if (!el || !el.clientWidth) return
    const dpr = Math.min(window.devicePixelRatio || 1, 3)
    const w = Math.round(el.clientWidth * dpr)
    if (el.width !== w) {
      el.width = w
      el.height = Math.round((w * INK_H) / INK_W)
    }
    scale.current = scaleFor(el.width)
    repaint()
  }, [repaint])

  const endStroke = useCallback(() => {
    const a = active.current
    if (!a) return
    active.current = null
    const st = strokes.current[strokes.current.length - 1]
    if (st) {
      st.open = false
      const c = ctx()
      if (c) paintStroke(c, st, scale.current, st.pts.length / 3, st.pts.length / 3, true)
    }
    batch.current.end()
    if (timer.current) clearInterval(timer.current)
    timer.current = null
    flush()
  }, [flush])

  useEffect(() => {
    fit()
    const el = canvas.current!
    const ro = new ResizeObserver(fit)
    ro.observe(el)
    send.current([{ t: 'clear' }]) // a fresh pad: the TV's canvas must match it
    const stop = () => endStroke()
    window.addEventListener('blur', stop)
    document.addEventListener('visibilitychange', stop)
    return () => {
      ro.disconnect()
      window.removeEventListener('blur', stop)
      document.removeEventListener('visibilitychange', stop)
      if (timer.current) clearInterval(timer.current)
    }
  }, [fit, endStroke])

  const wasOnline = useRef(online)
  useEffect(() => {
    if (online && !wasOnline.current) {
      const ops: InkOp[] = [{ t: 'clear' }, ...strokes.current.flatMap(strokeOps)]
      for (const c of chunkOps(ops)) send.current(c)
    }
    wasOnline.current = online
  }, [online])

  const down = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (active.current) return
    e.currentTarget.setPointerCapture(e.pointerId)
    const [x, y] = toGrid(e.clientX, e.clientY, e.currentTarget.getBoundingClientRect())
    const p = pressureOf(e)
    const st: Stroke = { s: nextId.current++, c: colour, w: brush, pts: [x, y, p], open: true }
    strokes.current.push(st)
    active.current = { pointer: e.pointerId }
    batch.current.start(st.s, colour, brush, x, y, p)
    const c = ctx()
    if (c) paintStroke(c, st, scale.current, 0, 1, false)
    timer.current = setInterval(flush, 50)
    setCount(strokes.current.length)
  }

  const move = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (!active.current || active.current.pointer !== e.pointerId) return
    const st = strokes.current[strokes.current.length - 1]
    const c = ctx()
    if (!st || !c) return
    const rect = e.currentTarget.getBoundingClientRect()
    const coalesced = e.nativeEvent.getCoalescedEvents?.() ?? []
    for (const ev of coalesced.length ? coalesced : [e.nativeEvent]) {
      const [x, y] = toGrid(ev.clientX, ev.clientY, rect)
      const n = st.pts.length
      if (Math.abs(x - st.pts[n - 3]) + Math.abs(y - st.pts[n - 2]) < 2) continue
      const p = pressureOf(ev)
      const from = n / 3
      st.pts.push(x, y, p)
      batch.current.point(x, y, p)
      paintStroke(c, st, scale.current, from, from + 1, false)
    }
  }

  const undo = () => {
    endStroke()
    if (!strokes.current.length) return
    strokes.current.pop()
    batch.current.op({ t: 'undo' })
    flush()
    repaint()
    setCount(strokes.current.length)
    buzz(15)
  }

  const clear = () => {
    endStroke()
    if (!strokes.current.length) return
    strokes.current.length = 0
    batch.current.op({ t: 'clear' })
    flush()
    repaint()
    setCount(0)
    buzz(25)
  }

  return (
    <div className="draw-pad">
      <div className="draw-word">
        <small>YOU'RE DRAWING · {LEVELS[screen.difficulty]}</small>
        <b>{screen.word}</b>
      </div>
      <div className="draw-paper">
        <canvas
          ref={canvas}
          className="draw-canvas"
          aria-label="Drawing pad"
          onPointerDown={down}
          onPointerMove={move}
          onPointerUp={endStroke}
          onPointerCancel={endStroke}
          onContextMenu={(e) => e.preventDefault()}
        />
      </div>
      <div className="draw-tools">
        <div className="draw-crayons" role="radiogroup" aria-label="Crayon colour">
          {CRAYONS.map((hex, i) => (
            <button key={hex} type="button" role="radio" aria-checked={colour === i} aria-label={CRAYON_NAMES[i]} className={`crayon ${colour === i ? 'on' : ''}`} style={{ background: hex }} onClick={() => setColour(i)} />
          ))}
        </div>
        <div className="draw-brushes" role="radiogroup" aria-label="Brush size">
          {BRUSHES.map((w, i) => (
            <button key={w} type="button" role="radio" aria-checked={brush === i} aria-label={BRUSH_NAMES[i]} className={`brush ${brush === i ? 'on' : ''}`} onClick={() => setBrush(i)}>
              <span style={{ width: 6 + i * 9, height: 6 + i * 9 }} />
            </button>
          ))}
          <button type="button" className="tool" disabled={!count} onClick={undo}>Undo</button>
          <button type="button" className="tool" disabled={!count} onClick={clear}>Clear</button>
        </div>
      </div>
      <p className="draw-status" role="status">{screen.guessed} of {screen.expected} have it</p>
    </div>
  )
}
```

- [ ] **Step 2: `GuessPad.tsx`**

```tsx
import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { ActionPayload, Screen } from '../protocol'
import { blankCells } from '../ink/blanks'
import './draw.css'

type GuessScreen = Extract<Screen, { t: 'guess' }>

const buzz = (ms: number | number[]) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }

/** A guesser: the blanks, a field that clears and stays focused after every guess, and quiet feedback on the last miss. */
export function GuessPad({ screen, disabled, onAction }: { screen: GuessScreen; disabled: boolean; onAction(p: ActionPayload): void }) {
  const [text, setText] = useState('')
  const field = useRef<HTMLInputElement>(null)
  useEffect(() => { if (screen.solved) buzz([40, 60, 40]) }, [screen.solved])

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    if (!t || disabled) return
    buzz(25)
    onAction({ kind: screen.kind, text: t })
    setText('')
    field.current?.focus()
  }

  const cells = blankCells(screen.blanks)
  return (
    <div className="guess-pad stack">
      <h1 className="prompt small">{screen.drawer} is drawing</h1>
      <div className="blank-cells" aria-label={`The word: ${screen.blanks.replace(/_/g, 'blank')}`}>
        {cells.map((word, wi) => (
          <span key={wi} className="blank-word">
            {word.map((c, ci) => <span key={ci} className={`blank-cell ${c === '_' ? '' : 'shown'}`}>{c === '_' ? '' : c}</span>)}
          </span>
        ))}
      </div>
      {screen.solved ? (
        <div className="guess-solved" role="status">
          <b>You got it!</b>
          {screen.points != null && <span>+{screen.points.toLocaleString()}</span>}
          <small>Now watch the rest of the room sweat.</small>
        </div>
      ) : (
        <form className="stack" onSubmit={submit}>
          <input
            ref={field}
            value={text}
            maxLength={40}
            autoFocus
            autoComplete="off"
            autoCorrect="off"
            autoCapitalize="none"
            spellCheck={false}
            enterKeyHint="send"
            aria-label="Your guess"
            placeholder="Type a guess"
            disabled={disabled}
            onChange={(e) => setText(e.target.value)}
          />
          <button className="primary big" disabled={disabled || !text.trim()}>Guess</button>
          <p className={`guess-note ${screen.close ? 'close' : ''}`} role="status">
            {screen.close ? 'So close!' : screen.last ? `Not “${screen.last}”` : ' '}
          </p>
        </form>
      )}
      <p className="muted draw-status">{screen.guessed} of {screen.expected} have it</p>
    </div>
  )
}
```

- [ ] **Step 3: `draw.css`**

```css
/* Doodle Dash on the phone. Paper, crayons and big thumb targets; colours come from the theme tokens. */
.draw-pad { display: flex; flex-direction: column; gap: 12px; width: 100%; }
.draw-word {
  position: relative; align-self: center; padding: 8px 22px 10px; background: var(--sun); color: var(--ink);
  border: 3px solid var(--ink); border-radius: 6px; box-shadow: 4px 4px 0 var(--ink); transform: rotate(-1.5deg); text-align: center;
}
.draw-word::before { /* washi tape */
  content: ''; position: absolute; left: 50%; top: -12px; width: 64px; height: 20px; transform: translateX(-50%) rotate(3deg);
  background: color-mix(in oklab, var(--bubblegum) 70%, transparent); border: 2px solid color-mix(in oklab, var(--ink) 40%, transparent);
}
.draw-word small { display: block; font: 800 12px var(--font-body); letter-spacing: .14em; opacity: .75; }
.draw-word b { display: block; font: 400 34px/1.1 var(--font-display); overflow-wrap: anywhere; }
.draw-paper {
  width: 100%; aspect-ratio: 4 / 3; background: #fffaf0; border: 4px solid var(--ink); border-radius: 12px; box-shadow: 5px 5px 0 var(--ink);
  background-image: radial-gradient(circle at 20% 30%, #0000000a 0 1px, transparent 2px), radial-gradient(circle at 70% 60%, #0000000a 0 1px, transparent 2px);
  background-size: 9px 9px, 13px 13px; overflow: hidden;
}
.draw-canvas { display: block; width: 100%; height: 100%; touch-action: none; user-select: none; -webkit-user-select: none; -webkit-touch-callout: none; cursor: crosshair; }
.draw-tools { display: flex; flex-direction: column; gap: 10px; }
.draw-crayons, .draw-brushes { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: center; }
.crayon { flex: none; width: 44px; height: 44px; border-radius: 50%; border: 3px solid var(--ink); box-shadow: 2px 2px 0 var(--ink); padding: 0; }
.crayon.on { transform: scale(1.15); outline: 4px solid var(--ink); outline-offset: 2px; }
.brush { flex: none; width: 48px; height: 48px; display: grid; place-items: center; background: var(--white); border: 3px solid var(--ink); border-radius: 12px; padding: 0; }
.brush span { display: block; border-radius: 50%; background: var(--ink); }
.brush.on { background: var(--sun); box-shadow: 3px 3px 0 var(--ink); }
.tool { min-height: 48px; padding: 0 16px; font: 800 16px var(--font-body); background: var(--paper); color: var(--ink); border: 3px solid var(--ink); border-radius: 12px; }
.tool:disabled { opacity: .4; }
.draw-status { text-align: center; font: 800 15px var(--font-body); margin: 0; }

.guess-pad input {
  width: 100%; min-height: 60px; padding: 10px 16px; font: 800 26px var(--font-body); color: var(--ink);
  background: var(--white); border: 4px solid var(--ink); border-radius: 14px; box-shadow: 4px 4px 0 var(--ink);
}
.blank-cells { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px 22px; padding: 6px 0; }
.blank-word { display: inline-flex; gap: 6px; }
.blank-cell { width: 28px; height: 40px; display: grid; place-items: end center; border-bottom: 5px solid var(--ink); font: 400 32px/1 var(--font-display); color: var(--ink); }
.blank-cell.shown { color: var(--tomato); }
.guess-note { min-height: 28px; margin: 0; text-align: center; font: 800 19px var(--font-body); color: var(--ink); }
.guess-note.close { color: var(--tomato); font-size: 24px; animation: boing var(--dur-base, .3s) var(--ease-out, ease-out); }
.guess-solved { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 20px; background: var(--lime); border: 4px solid var(--ink); border-radius: 18px; box-shadow: 5px 5px 0 var(--ink); }
.guess-solved b { font: 400 34px var(--font-display); color: var(--ink); }
.guess-solved span { font: 400 44px var(--font-display); color: var(--ink); }
.guess-solved small { font: 800 15px var(--font-body); color: var(--ink); }
@media (prefers-reduced-motion: reduce) { .guess-note.close { animation: none; } }
```

- [ ] **Step 4: Wire `ScreenView.tsx`**

Add imports:

```tsx
import type { InkOp } from '../ink/types'
import { DrawPad } from './DrawPad'
import { GuessPad } from './GuessPad'
```

Change the props and signature:

```tsx
interface Props { screen: Screen; disabled: boolean; meId: string; people: Map<string, ScoreRow>; onAction(p: ActionPayload): void; ink?: { online: boolean; send(ops: InkOp[]): void } }

export function ScreenView({ screen, disabled, meId, people, onAction, ink }: Props) {
```

Add to the switch (after `secret`):

```tsx
    case 'draw': return <DrawPad screen={screen} online={ink?.online ?? true} sendInk={ink?.send ?? (() => undefined)} />
    case 'guess': return <GuessPad screen={screen} disabled={disabled} onAction={onAction} />
```

- [ ] **Step 5: Wire `Play.tsx`**

Add `type InkOp` import: `import type { InkOp } from '../ink/types'`. After `const send = ...`:

```tsx
  const sendInk = (ops: InkOp[]) => { if (view) conn.current?.ink(view.round, ops) }
```

Replace the `seconds` display so a draw or guess shows the whole draw clock, not just the current hint stage:

```tsx
  const tail = view.screen.t === 'draw' || view.screen.t === 'guess' ? Math.ceil(view.screen.tailMs / 1000) : 0
```

(place it next to `const timed = ...`), and render `{seconds != null && view.gameId && timed && <span className={`timer ${seconds + tail <= 5 ? 'hot' : ''}`}>{seconds + tail}</span>}`. Pass the pad its socket state:

```tsx
          : <ScreenView screen={view.screen} disabled={view.paused} onAction={send} meId={view.me.id} people={people} ink={{ online: status === 'online', send: sendInk }} />}
```

`Play.tsx` already keys the screen section by `${view.round}-${view.screen.t}`, and a draw's hint stages never change `round`, so the pad stays mounted while guesses come in.

- [ ] **Step 6: Type-check and unit tests**

Run: `cd controller && npx tsc -b && npx vitest run`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add controller/src/screens controller/src/pages/Play.tsx
git commit -m "feat(doodle): the drawing pad and the guess pad on the phone"
```

### Task 8b: Show word difficulty on the pick list

The pick screen is the engine's plain `choice` list, which ignores `Choice.detail`; players must see Easy / Medium / Hard because harder words score more.

**Files:**
- Modify: `controller/src/screens/ScreenView.tsx` (the plain-list branch at the end of `ChoiceList`)
- Modify: `controller/src/screens/draw.css`

- [ ] **Step 1: Render the detail line**

In `ChoiceList`'s last `return` (the branch with no `style`), change the button to:

```tsx
          <button key={o.id} className={`choice ${screen.selected === o.id ? 'on' : ''}`} disabled={disabled} onClick={() => pick(o)}>
            {o.text}
            {o.detail && <small className="choice-detail">{o.detail}</small>}
          </button>
```

Append to `draw.css`:

```css
.choice-detail { display: block; margin-top: 2px; font: 800 13px var(--font-body); letter-spacing: .1em; text-transform: uppercase; opacity: .75; }
```

- [ ] **Step 2: Type-check and commit**

```bash
cd controller && npx tsc -b
git add controller/src/screens
git commit -m "feat(doodle): show Easy, Medium or Hard on the word pick list"
```

---

### Task 9: The TV stage

**Files:**
- Create: `controller/src/tv/InkCanvas.tsx`, `controller/src/tv/DoodleStage.tsx`, `controller/src/tv/doodle.css`
- Modify: `controller/src/tv/types.ts`, `controller/src/tv/TvPage.tsx`, `controller/src/tv/director.ts`

**Interfaces:**
- Consumes: `InkStore` and the painters (Task 7), `blankCells`, shared TV pieces (`GameHeader`, `Tutorial`, `ScoreBoard`, `Podium`, `Fill`, `Panel`, `Bubble`, `AvatarFace`, `Pop`, `Slam`, `Deal`, `Chip`, `C`, `fireConfetti`).
- Produces (used by Task 10 and the e2e): `DoodleStage({ stage, players, scores, clock, ink })`; class names `.dd-paper`, `.dd-blanks`, `.blank-cell`, `.dd-bubble`, `.dd-solver`, `.dd-word`, `.dd-gallery`, `.dd-shot`; `InkCanvas({ store, turn, mode: 'live' | 'still' | 'replay', replayMs? })`.

- [ ] **Step 1: TV types** (append to `tv/types.ts`)

```ts
// ---- Doodle Dash (tv/engine/.../DoodleViews.kt) ---------------------------------------------

export interface DoodleTv {
  t: 'doodle'; phase: 'pick' | 'draw' | 'reveal' | 'scores' | 'podium'
  turn: number; totalTurns: number; finalTurn: boolean
  drawer?: string; drawerName: string; difficulty: number
  /** Draw only: "_ _ _ _ _" with any revealed letters in capitals. */
  blanks: string
  guessed: number; expected: number
  /** The whole draw time, and the part of it after the current hint stage. */
  drawMs: number; tailMs: number
  solvers: { id: string; name: string; points?: number }[]
  wrong: { id: string; name: string; text: string }[]; missTotal: number
  word?: string
  drinks: { id: string; name: string; sips: number; text: string }[]
  deltas: { id: string; name: string; points: number }[]
  gallery: { turn: number; word: string; drawer: string; drawerName: string; first?: string; firstName?: string }[]
}
```

- [ ] **Step 2: `InkCanvas.tsx`**

```tsx
import { useEffect, useRef } from 'react'
import { paintAll, paintStroke, paintUpTo, scaleFor } from '../ink/paint'
import type { InkStore } from '../ink/store'
import { INK_H, INK_W } from '../ink/types'

const BACKING = 1440

/**
 * One turn's drawing. `live` follows the store as strokes arrive (painting only what's new, with a glowing pen tip at
 * the end of the open stroke), `still` shows the finished picture, `replay` draws it again as a time-lapse.
 * It fills its parent; the parent sets the aspect ratio (4:3) and `position: relative`.
 */
export function InkCanvas({ store, turn, mode, replayMs = 3200 }: { store: InkStore; turn: number; mode: 'live' | 'still' | 'replay'; replayMs?: number }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const tip = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    const el = canvas.current!
    const ctx = el.getContext('2d')!
    const k = scaleFor(el.width)
    let raf = 0
    let painted: { s: number; n: number; closed: boolean }[] = []

    const full = () => {
      const strokes = store.strokes(turn)
      paintAll(ctx, strokes, k, el.width, el.height)
      painted = strokes.map((st) => ({ s: st.s, n: st.pts.length / 3, closed: !st.open }))
    }

    const moveTip = () => {
      const t = tip.current
      if (!t) return
      const strokes = store.strokes(turn)
      const st = strokes[strokes.length - 1]
      if (st?.open) {
        t.style.opacity = '1'
        t.style.left = `${(st.pts[st.pts.length - 3] / INK_W) * 100}%`
        t.style.top = `${(st.pts[st.pts.length - 2] / INK_H) * 100}%`
      } else {
        t.style.opacity = '0'
      }
    }

    const update = () => {
      const strokes = store.strokes(turn)
      const intact = strokes.length >= painted.length && painted.every((p, i) => strokes[i].s === p.s && strokes[i].pts.length / 3 >= p.n)
      if (!intact) full()
      else {
        strokes.forEach((st, i) => {
          const p = (painted[i] ??= { s: st.s, n: 0, closed: false })
          const n = st.pts.length / 3
          if (n > p.n) { paintStroke(ctx, st, k, p.n, n, false); p.n = n }
          if (!st.open && !p.closed) { paintStroke(ctx, st, k, n, n, true); p.closed = true }
        })
      }
      moveTip()
    }

    if (mode === 'replay') {
      const strokes = store.strokes(turn)
      const started = performance.now()
      const step = (now: number) => {
        const t = Math.min(1, (now - started) / replayMs)
        const eased = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2
        paintUpTo(ctx, strokes, k, eased, el.width, el.height)
        if (t < 1) raf = requestAnimationFrame(step)
      }
      raf = requestAnimationFrame(step)
      return () => cancelAnimationFrame(raf)
    }

    full()
    moveTip()
    const off = store.subscribe(() => {
      if (raf) return
      raf = requestAnimationFrame(() => { raf = 0; mode === 'live' ? update() : full() })
    })
    return () => { off(); cancelAnimationFrame(raf) }
  }, [store, turn, mode, replayMs])

  return (
    <>
      <canvas ref={canvas} width={BACKING} height={(BACKING * INK_H) / INK_W} aria-label="The drawing" />
      {mode === 'live' && <span ref={tip} className="pen-tip" />}
    </>
  )
}
```

- [ ] **Step 3: `DoodleStage.tsx`**

```tsx
import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { blankCells } from '../ink/blanks'
import type { InkStore } from '../ink/store'
import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { InkCanvas } from './InkCanvas'
import { Deal, AvatarFace, Bubble, C, Panel, Pop, Slam, fireConfetti } from './toon'
import { Fill, GameHeader, Podium, ScoreBoard, Tutorial } from './Shared'
import { useTimerScale } from './timerScale'
import type { DoodleTv } from './types'
import './doodle.css'

const PICK_MS = 12_000, REVEAL_MS = 7_000, SCORES_MS = 6_000, PODIUM_MS = 24_000
/** The podium gives way to the gallery when this much of the phase is left. */
const GALLERY_LEFT_MS = 13_000
const LEVEL = ['', 'EASY', 'MEDIUM', 'HARD']
const LEVEL_FILL = ['', C.lime, C.sun, C.tomato]

type Clock = { deadline: number | null; frozen: number | null }
type Who = Map<string, PlayerSummary>

export function DoodleStage({ stage, players, scores, clock, ink }: { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock; ink: InkStore }) {
  const scale = useTimerScale()
  const who = useMemo(() => new Map(players.map((p) => [p.id, p])), [players])
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="Doodle Dash" stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.paper]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} />
      </div>
    )
  }
  const g = stage.game as unknown as DoodleTv
  const chips: [string, string][] = [[g.phase === 'podium' ? 'FINAL RESULTS' : `TURN ${g.turn} OF ${g.totalTurns}`, C.paper]]
  if (g.finalTurn && g.phase !== 'podium') chips.push(['LAST TURN: DOUBLE POINTS', C.sun])
  if (g.phase === 'draw' && g.difficulty) chips.push([LEVEL[g.difficulty], LEVEL_FILL[g.difficulty]])
  const total = { pick: PICK_MS * scale, draw: g.drawMs, reveal: REVEAL_MS, scores: SCORES_MS, podium: PODIUM_MS }[g.phase]
  // A draw runs in hint stages; the stage clock only knows the current one, so add what is left after it.
  const shown: Clock = g.phase === 'draw'
    ? { deadline: clock.deadline != null ? clock.deadline + g.tailMs : null, frozen: clock.frozen != null ? clock.frozen + g.tailMs : null }
    : clock
  const status = g.phase === 'draw' ? `${g.guessed}/${g.expected} GOT IT` : null
  return (
    <div className="stage-pad dd">
      <GameHeader title="Doodle Dash" stage={stage} total={total} clock={shown} chips={chips} status={status} />
      <Fill>
        {g.phase === 'pick' && <Picking g={g} who={who} />}
        {g.phase === 'draw' && <Drawing g={g} who={who} ink={ink} />}
        {g.phase === 'reveal' && <Reveal g={g} who={who} ink={ink} />}
        {g.phase === 'scores' && <ScoreBoard scores={scores} deltas={Object.fromEntries(g.deltas.map((d) => [d.id, d.points]))} />}
        {g.phase === 'podium' && <Finale g={g} scores={scores} stage={stage} ink={ink} />}
      </Fill>
    </div>
  )
}

function Picking({ g, who }: { g: DoodleTv; who: Who }) {
  const d = g.drawer ? who.get(g.drawer) : undefined
  return (
    <div className="dd-center">
      <div className="dd-cards" aria-hidden="true">
        {[0, 1, 2].map((i) => (
          <Deal key={i} i={i}>
            <Panel fill={[C.lime, C.sun, C.tomato][i]} tilt={[-5, 2, 6][i]} className="dd-card"><b>?</b></Panel>
          </Deal>
        ))}
      </div>
      <div className="dd-picker">
        {d && <AvatarFace avatar={d.avatar} size={120} />}
        <p className="dd-line"><b>{g.drawerName}</b> is picking a word</p>
      </div>
    </div>
  )
}

function Cells({ blanks }: { blanks: string }) {
  return (
    <div className="dd-blanks" role="img" aria-label="The word, hidden">
      {blankCells(blanks).map((word, wi) => (
        <span key={wi} className="blank-word">
          {word.map((c, ci) => <span key={ci} className={`blank-cell ${c === '_' ? '' : 'shown'}`}>{c === '_' ? '' : c}</span>)}
        </span>
      ))}
    </div>
  )
}

function Easel({ children }: { children: ReactNode }) {
  return (
    <div className="dd-frame">
      <div className="dd-paper">{children}</div>
    </div>
  )
}

function Drawing({ g, who, ink }: { g: DoodleTv; who: Who; ink: InkStore }) {
  const d = g.drawer ? who.get(g.drawer) : undefined
  return (
    <div className="dd-draw">
      <aside className="dd-side">
        <Panel fill={C.paper} tilt={-1.5} className="dd-drawer">
          {d && <AvatarFace avatar={d.avatar} size={150} />}
          <span className="label">NOW DRAWING</span>
          <span className="name">{g.drawerName}</span>
        </Panel>
      </aside>
      <div className="dd-easel">
        <Cells blanks={g.blanks} />
        <Easel><InkCanvas store={ink} turn={g.turn} mode="live" /></Easel>
      </div>
      <aside className="dd-side dd-right">
        <div className="dd-solvers">
          <AnimatePresence>
            {g.solvers.map((s) => {
              const p = who.get(s.id)
              return p ? (
                <Pop key={s.id}><div className="dd-solver"><AvatarFace avatar={p.avatar} size={72} /><span className="sticker">GOT IT</span></div></Pop>
              ) : null
            })}
          </AnimatePresence>
        </div>
        <div className="dd-bubbles">
          <AnimatePresence initial={false}>
            {g.wrong.map((m, i) => {
              const p = who.get(m.id)
              const key = g.missTotal - (g.wrong.length - i)
              return (
                <motion.div key={key} layout className="dd-bubble" initial={{ opacity: 0, x: 50, scale: 0.8 }} animate={{ opacity: 1, x: 0, scale: 1 }} exit={{ opacity: 0, x: -30 }} transition={{ type: 'spring', stiffness: 500, damping: 28 }}>
                  {p && <AvatarFace avatar={p.avatar} size={52} />}
                  <Bubble tail="left"><span>{m.text}</span></Bubble>
                </motion.div>
              )
            })}
          </AnimatePresence>
        </div>
      </aside>
    </div>
  )
}

function Reveal({ g, who, ink }: { g: DoodleTv; who: Who; ink: InkStore }) {
  useEffect(() => { if (g.expected > 0 && g.guessed === g.expected) fireConfetti() }, [g.turn]) // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <div className="dd-draw dd-reveal">
      <aside className="dd-side">
        <Slam from={2} tilt={-6}><Panel fill={C.sun} tilt={-3} className="dd-word"><small>IT WAS</small><b>{g.word?.toUpperCase()}</b></Panel></Slam>
      </aside>
      <div className="dd-easel"><Easel><InkCanvas store={ink} turn={g.turn} mode="replay" /></Easel></div>
      <aside className="dd-side dd-right">
        {g.solvers.length === 0 && <p className="dd-line">Nobody got it!</p>}
        <ol className="dd-results">
          {g.solvers.map((s, i) => {
            const p = who.get(s.id)
            return (
              <Deal key={s.id} i={i}>
                <li>{p && <AvatarFace avatar={p.avatar} size={56} />}<span className="who">{s.name}</span>{s.points != null && <span className="pts">+{s.points.toLocaleString()}</span>}</li>
              </Deal>
            )
          })}
        </ol>
        <ul className="dd-drinks">{g.drinks.map((d) => <li key={d.id}>{d.text.startsWith(d.name) ? d.text : <><b>{d.name}</b>: {d.text}</>}</li>)}</ul>
      </aside>
    </div>
  )
}

function Finale({ g, scores, stage, ink }: { g: DoodleTv; scores: ScoreRow[]; stage: StageInfo; ink: InkStore }) {
  const wait = Math.max(0, (stage.remainingMs ?? PODIUM_MS) - GALLERY_LEFT_MS)
  const [gallery, setGallery] = useState(wait === 0)
  useEffect(() => {
    if (wait === 0) { setGallery(true); return }
    const id = setTimeout(() => setGallery(true), wait)
    return () => clearTimeout(id)
  }, [wait, stage.phaseSeq])
  if (!gallery || g.gallery.length === 0) return <Podium scores={scores} />
  return (
    <div className="dd-gallery-wrap">
      <h2 className="dd-gallery-title">THE GALLERY</h2>
      <div className="dd-gallery">
        {g.gallery.map((s, i) => (
          <Deal key={s.turn} i={i}>
            <motion.figure className="dd-shot" animate={{ rotate: [(i % 2 ? 1 : -1) * 1.2, (i % 2 ? -1 : 1) * 1.2] }} transition={{ repeat: Infinity, repeatType: 'mirror', duration: 3 + (i % 3) * 0.6, ease: 'easeInOut' }}>
              <div className="dd-still"><InkCanvas store={ink} turn={s.turn} mode="still" /></div>
              <figcaption><b>{s.word}</b><small>by {s.drawerName}{s.firstName ? ` · first: ${s.firstName}` : ' · nobody got it'}</small></figcaption>
            </motion.figure>
          </Deal>
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 4: `doodle.css`**

```css
/* Doodle Dash TV stage: the Crayon Studio. A big wooden easel in the middle, the drawer on the left, who has it and every
   wrong guess on the right. 1920 by 1080 stage; type stays large for a room across the floor. */
.dd-center { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 56px; }
.dd-cards { display: flex; gap: 40px; }
.dd-card { width: 220px; height: 300px; display: grid; place-items: center; }
.dd-card b { font: 400 150px/1 var(--font-display); color: var(--paper); -webkit-text-stroke: 12px var(--ink); paint-order: stroke fill; }
.dd-picker { display: flex; align-items: center; gap: 28px; }
.dd-line { font: 700 44px/1.2 var(--font-body); color: var(--paper); text-align: center; }
.dd-line b { color: var(--game-accent); font-weight: 900; }

.dd-draw { flex: 1; display: grid; grid-template-columns: 330px 1fr 460px; gap: 32px; align-items: start; min-height: 0; padding-top: 6px; }
.dd-side { display: flex; flex-direction: column; gap: 22px; align-items: center; min-width: 0; }
.dd-drawer { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 24px 18px; text-align: center; width: 100%; }
.dd-drawer .label { font: 900 20px var(--font-body); letter-spacing: .16em; }
.dd-drawer .name { font: 400 44px/1.1 var(--font-display); overflow-wrap: anywhere; }
.dd-easel { display: flex; flex-direction: column; align-items: center; gap: 18px; min-width: 0; }

.dd-blanks { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px 44px; min-height: 92px; }
.dd-blanks .blank-word { display: inline-flex; gap: 10px; }
.dd-blanks .blank-cell { width: 56px; height: 80px; display: grid; place-items: end center; border-bottom: 9px solid var(--paper); font: 400 66px/1 var(--font-display); color: var(--paper); }
.dd-blanks .blank-cell.shown { color: var(--game-accent); animation: dd-pop .4s var(--ease-out, ease-out); }

.dd-frame {
  position: relative; padding: 22px; background: #c98b4b; border: 6px solid var(--ink); border-radius: 14px; box-shadow: 12px 12px 0 var(--ink);
  background-image: repeating-linear-gradient(92deg, #ffffff12 0 3px, transparent 3px 12px);
}
.dd-frame::before, .dd-frame::after { /* washi tape on two corners */
  content: ''; position: absolute; width: 130px; height: 38px; background: color-mix(in oklab, var(--bubblegum) 75%, transparent);
  border: 3px solid color-mix(in oklab, var(--ink) 35%, transparent); z-index: 2;
}
.dd-frame::before { left: -40px; top: 18px; transform: rotate(-38deg); }
.dd-frame::after { right: -40px; bottom: 18px; transform: rotate(-38deg); background: color-mix(in oklab, var(--sky) 75%, transparent); }
.dd-paper { position: relative; width: 820px; aspect-ratio: 4 / 3; background: #fffaf0; border: 5px solid var(--ink); overflow: hidden; }
.dd-paper canvas { position: absolute; inset: 0; width: 100%; height: 100%; }
.dd-paper::after { /* paper grain over the crayon, so a line looks waxy rather than digital */
  content: ''; position: absolute; inset: 0; pointer-events: none; opacity: .4; mix-blend-mode: multiply;
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 .36  0 0 0 0 .3  0 0 0 0 .25  0 0 0 .8 0'/></filter><rect width='160' height='160' filter='url(%23n)'/></svg>");
}
.pen-tip {
  position: absolute; z-index: 1; width: 30px; height: 30px; margin: -15px 0 0 -15px; border-radius: 50%; pointer-events: none; opacity: 0;
  background: radial-gradient(circle, #fff 0 18%, var(--game-accent) 19% 52%, transparent 62%); filter: drop-shadow(0 0 12px var(--game-accent));
  transition: left 70ms linear, top 70ms linear, opacity .25s; animation: dd-twinkle .45s ease-in-out infinite alternate;
}

.dd-right { align-items: stretch; }
.dd-solvers { display: flex; flex-wrap: wrap; gap: 14px; min-height: 84px; }
.dd-solver { position: relative; }
.dd-solver .sticker { position: absolute; left: 50%; bottom: -12px; transform: translateX(-50%) rotate(-6deg); padding: 2px 8px; background: var(--lime); border: 3px solid var(--ink); border-radius: 6px; font: 900 15px var(--font-body); letter-spacing: .06em; white-space: nowrap; }
.dd-bubbles { display: flex; flex-direction: column; gap: 12px; }
.dd-bubble { display: flex; align-items: center; gap: 12px; }
.dd-bubble .bubble { font: 800 34px/1.15 var(--font-body); overflow-wrap: anywhere; }

.dd-word { display: flex; flex-direction: column; align-items: center; padding: 22px 18px; gap: 4px; width: 100%; }
.dd-word small { font: 900 22px var(--font-body); letter-spacing: .18em; }
.dd-word b { font: 400 62px/1.05 var(--font-display); overflow-wrap: anywhere; text-align: center; }
.dd-results { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; width: 100%; }
.dd-results li { display: flex; align-items: center; gap: 14px; padding: 8px 14px; background: var(--paper); border: 4px solid var(--ink); border-radius: 14px; box-shadow: 5px 5px 0 var(--ink); }
.dd-results .who { flex: 1; font: 900 32px var(--font-body); }
.dd-results .pts { font: 400 34px var(--font-display); color: var(--felt-deep, var(--ink)); }
.dd-drinks { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
.dd-drinks li { font: 700 28px/1.2 var(--font-body); color: var(--paper); }
.dd-drinks b { color: var(--game-accent); }

.dd-gallery-wrap { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 16px; min-height: 0; }
.dd-gallery-title { font: 400 64px var(--font-display); color: var(--paper); -webkit-text-stroke: 10px var(--ink); paint-order: stroke fill; }
.dd-gallery { display: grid; grid-template-columns: repeat(4, 1fr); gap: 22px 28px; width: 100%; max-width: 1760px; }
.dd-shot { margin: 0; padding: 12px 12px 10px; background: #c98b4b; border: 5px solid var(--ink); border-radius: 10px; box-shadow: 7px 7px 0 var(--ink); display: flex; flex-direction: column; gap: 8px; }
.dd-still { position: relative; aspect-ratio: 4 / 3; background: #fffaf0; border: 4px solid var(--ink); overflow: hidden; }
.dd-still canvas { position: absolute; inset: 0; width: 100%; height: 100%; }
.dd-shot figcaption { display: flex; flex-direction: column; align-items: center; color: var(--ink); }
.dd-shot figcaption b { font: 400 30px/1.1 var(--font-display); color: var(--paper); -webkit-text-stroke: 6px var(--ink); paint-order: stroke fill; }
.dd-shot figcaption small { font: 800 20px var(--font-body); color: var(--ink); }

@keyframes dd-twinkle { from { transform: scale(.85); } to { transform: scale(1.15); } }
@keyframes dd-pop { 0% { transform: scale(.4) rotate(-12deg); } 70% { transform: scale(1.2) rotate(4deg); } 100% { transform: none; } }
@media (prefers-reduced-motion: reduce) { .pen-tip, .dd-blanks .blank-cell.shown { animation: none; } }
```

- [ ] **Step 5: Wire `TvPage.tsx`**

Imports:

```tsx
import { InkStore } from '../ink/store'
import { DoodleStage } from './DoodleStage'
```

In `Show`, next to the other refs:

```tsx
  const ink = useRef(new InkStore()).current
```

In the `Connection` `onMessage`, after the `tv` line:

```tsx
        if (m.t === 'ink') ink.applyEvent(m)
        if (m.t === 'inkSync') ink.applySync(m)
```

(add `ink` to that effect's dependency array). After the `clock` line:

```tsx
  // The drawings belong to the game that just ended.
  const gameOver = !!tv && !tv.stage
  useEffect(() => { if (gameOver) ink.reset() }, [gameOver, ink])
```

In the stage `AnimatePresence` chain, after the `imposter` branch:

```tsx
                : stage.gameId === 'doodle' ? <GameScene game="doodle"><DoodleStage stage={stage} players={players} scores={tv.scores} clock={clock} ink={ink} /></GameScene>
```

- [ ] **Step 6: Sound cues (`director.ts`)**

Add `import type { DoodleTv } from './types'` (extend the existing `./types` import). Right after the `const newPhase = ...` line in `useCueDirector`:

```ts
    if ((gb as { t: string }).t === 'doodle') {
      const d = gb as unknown as DoodleTv
      const before = ga && (ga as { t: string }).t === 'doodle' ? (ga as unknown as DoodleTv) : null
      if (newPhase) {
        if (d.phase === 'draw') sfx.roundStart(d.finalTurn)
        if (d.phase === 'reveal') sfx.whoosh()
        if (d.phase === 'podium') sfx.drumroll(2.4)
      } else if (before && d.guessed > before.guessed) {
        sfx.ding()
      }
    }
```

In `musicFor`, before the final `switch (g.phase)`:

```ts
  if ((g as { t: string }).t === 'doodle') {
    const p = (g as unknown as DoodleTv).phase
    return p === 'podium' ? 'podium' : p === 'reveal' ? 'reveal' : p === 'scores' ? 'standings' : 'bluff'
  }
```

- [ ] **Step 7: Type-check**

Run: `cd controller && npx tsc -b`
Expected: no errors. (The theme name `doodle` is added in Task 10; until then `GameScene game="doodle"` fails to type-check, so do Task 10 Step 1 first if you build these in order: it is a one-line change.)

- [ ] **Step 8: Commit**

```bash
git add controller/src/tv
git commit -m "feat(doodle): the TV stage with live ink, time-lapse reveal and a gallery"
```

---

### Task 10: The Crayon Studio theme and design-preview fixtures

**Files:**
- Modify: `controller/src/theme/gameTheme.ts`, `controller/src/theme/games.css`, `controller/src/tv/ThemeGallery.tsx`
- Create: `controller/src/tv/fixtures/doodle-theme.ts`

**Interfaces:**
- Consumes: `DoodleStage`, `InkStore` (Tasks 7, 9).
- Produces (used by the e2e in Task 11): `/tv?gallery=themes&game=doodle&beat=<beat>[&view=phone]` with beats `pick`, `draw`, `reveal`, `scores`, `podium`, `gallery` (TV) and phone views `pickme`, `drawer`, `guess`, `solved`. The draw beat's picture is a house (word "house", hints `H _ _ _ E`).

- [ ] **Step 1: Register the theme**

`gameTheme.ts`: change the list to `['turf', 'sprawl', 'blackjack', 'bluff', 'writeitdown', 'imposter', 'doodle'] as const`.

Append to `games.css` (next to the other game tokens):

```css
[data-game-theme='doodle'] {
  --ink: #3b2f2a;
  --ink-soft: #6d5d54;
  --paper: #fff7e6;
  --paper-2: #f2e5c9;
  --game-bg: #2f6f7e;
  --game-accent: #f5851f;
}
/* The Crayon Studio: a teal pegboard wall with the light coming from the left. */
.game-scene[data-game-theme='doodle'] {
  background-image:
    radial-gradient(circle, #ffffff1c 2.5px, transparent 3.5px),
    linear-gradient(100deg, #ffffff14, transparent 45%, #0000001f);
  background-size: 38px 38px, 100% 100%;
}
/* The wall is dark, so text sitting straight on it reads in paper, never ink (as in the Imposter room). */
[data-game-theme='doodle'] .status-text { color: var(--paper); }
[data-game-theme='doodle'] .muted { color: var(--ink-soft); }
```

- [ ] **Step 2: Fixtures** — `tv/fixtures/doodle-theme.ts`

```ts
import { InkStore } from '../../ink/store'
import type { InkOp } from '../../ink/types'
import type { PlayerSummary, ScoreRow, Screen, StageInfo } from '../../protocol'

// Synthetic beats for the theme gallery (six fictional players). No tokens or real sessions.
const palette = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF71', '#2F6BFF', '#9B5DE5']
const names = ['Ava', 'Ben', 'Cleo', 'Dev', 'Eli', 'Fay']
const players: PlayerSummary[] = names.map((name, i) => ({ id: `p${i}`, name, avatar: { face: `p:0${i}`, color: palette[i] }, role: 'PLAYER', connected: true }))
const scores: ScoreRow[] = players.map((p, i) => ({ id: p.id, name: p.name, avatar: p.avatar, score: 4200 - i * 600 }))
const me = players[0]

// ---- a demo drawing: a house, a roof, a door, a sun and some grass, as real strokes ----------------------------------
type Pt = [number, number]
const line = (points: Pt[], per = 14): number[] => {
  const out: number[] = []
  for (let i = 1; i < points.length; i++) {
    const [x0, y0] = points[i - 1], [x1, y1] = points[i]
    for (let k = 1; k <= per; k++) out.push(Math.round(x0 + ((x1 - x0) * k) / per), Math.round(y0 + ((y1 - y0) * k) / per), 50)
  }
  return out
}
const circle = (cx: number, cy: number, r: number, n = 40): Pt[] => Array.from({ length: n + 1 }, (_, i) => [Math.round(cx + r * Math.cos((i / n) * Math.PI * 2)), Math.round(cy + r * Math.sin((i / n) * Math.PI * 2))])
const stroke = (s: number, c: number, w: number, points: Pt[], per = 14): InkOp[] => [
  { t: 'start', s, c, w, x: points[0][0], y: points[0][1], p: 50 }, { t: 'pts', s, pts: line(points, per) }, { t: 'end', s },
]
const house: InkOp[] = [
  ...stroke(1, 0, 1, [[300, 400], [300, 650], [620, 650], [620, 400], [300, 400]]),
  ...stroke(2, 1, 2, [[265, 410], [460, 225], [655, 410], [265, 410]]),
  ...stroke(3, 5, 1, [[430, 650], [430, 520], [500, 520], [500, 650]]),
  ...stroke(4, 3, 2, circle(790, 150, 70), 3),
  ...stroke(5, 4, 0, [[70, 700], [110, 660], [150, 700], [190, 660], [230, 700], [270, 660], [310, 700]]),
  ...stroke(6, 2, 0, [[680, 700], [760, 660], [860, 700], [940, 660]]),
]
export function demoInk(turns = [1, 2, 3, 4, 5]): InkStore {
  const store = new InkStore()
  turns.forEach((turn, i) => store.applyEvent({ turn, n: i + 1, ops: house }))
  return store
}

const game = (over: Record<string, unknown>) => ({
  t: 'doodle', phase: 'draw', turn: 2, totalTurns: 5, finalTurn: false, drawer: 'p1', drawerName: 'Ben', difficulty: 2, blanks: '',
  guessed: 0, expected: 5, drawMs: 75_000, tailMs: 22_500, solvers: [], wrong: [], missTotal: 0, drinks: [], deltas: [], gallery: [], ...over,
})
const stage = (g: object, remainingMs = 18_000): StageInfo => ({ gameId: 'doodle', title: 'Doodle Dash', phaseSeq: 4, remainingMs, paused: false, game: g as StageInfo['game'] })
const shots = [1, 2, 3, 4, 5].map((turn, i) => ({ turn, word: ['house', 'sun', 'cottage', 'roof', 'home'][i], drawer: `p${i % 6}`, drawerName: names[i % 6], first: `p${(i + 2) % 6}`, firstName: names[(i + 2) % 6] }))
const rows: Screen = { t: 'scores', title: 'Final scores', rows: scores }

export default {
  players,
  beats: {
    pick: { stage: stage(game({ phase: 'pick', difficulty: 0 }), 9_000), scores, phone: { me, screen: { t: 'waiting', title: 'Ben is picking a word', detail: 'Get your guessing fingers ready' } as Screen } },
    draw: {
      stage: stage(game({
        blanks: 'H _ _ _ E', guessed: 2, missTotal: 9,
        solvers: [{ id: 'p2', name: 'Cleo' }, { id: 'p3', name: 'Dev' }],
        wrong: ['cabin', 'castle', 'barn', 'igloo', 'shed'].map((text, i) => ({ id: `p${(i + 3) % 6}`, name: names[(i + 3) % 6], text })),
      })),
      scores,
      phone: { me, screen: { t: 'guess', drawer: 'Ben', blanks: 'H _ _ _ E', kind: 'guess', solved: false, close: false, last: 'shed', guessed: 2, expected: 5, tailMs: 22_500 } as Screen },
    },
    drawer: { stage: stage(game({ blanks: '_ _ _ _ _' })), scores, phone: { me, screen: { t: 'draw', word: 'house', difficulty: 2, guessed: 2, expected: 5, tailMs: 22_500, note: 'Draw it! No letters or numbers.' } as Screen } },
    pickme: {
      stage: stage(game({ phase: 'pick', difficulty: 0 }), 9_000), scores,
      phone: { me, screen: { t: 'choice', prompt: 'Pick a word to draw', options: [{ id: 'a', text: 'sun', detail: 'Easy' }, { id: 'b', text: 'house', detail: 'Medium' }, { id: 'c', text: 'lighthouse', detail: 'Hard' }], kind: 'pick' } as Screen },
    },
    solved: {
      stage: stage(game({ blanks: 'H _ _ _ E', guessed: 3, solvers: [{ id: 'p0', name: 'Ava' }, { id: 'p2', name: 'Cleo' }, { id: 'p3', name: 'Dev' }] })), scores,
      phone: { me, screen: { t: 'guess', drawer: 'Ben', blanks: 'H _ _ _ E', kind: 'guess', solved: true, points: 850, close: false, guessed: 3, expected: 5, tailMs: 22_500 } as Screen },
    },
    reveal: {
      stage: stage(game({
        phase: 'reveal', word: 'house', guessed: 3, tailMs: 0,
        solvers: [{ id: 'p2', name: 'Cleo', points: 1000 }, { id: 'p3', name: 'Dev', points: 850 }, { id: 'p0', name: 'Ava', points: 700 }],
        drinks: [{ id: 'p4', name: 'Eli', sips: 1, text: 'Missed it. Drink 1 sip' }, { id: 'p5', name: 'Fay', sips: 1, text: 'Missed it. Drink 1 sip' }],
      }), 5_000),
      scores, phone: { me, screen: { t: 'waiting', title: 'It was HOUSE', detail: '+700', tone: 'win' } as Screen },
    },
    scores: {
      stage: stage(game({ phase: 'scores', word: 'house', deltas: [{ id: 'p1', name: 'Ben', points: 1500 }, { id: 'p2', name: 'Cleo', points: 1000 }, { id: 'p3', name: 'Dev', points: 850 }, { id: 'p0', name: 'Ava', points: 700 }] }), 4_000),
      scores, phone: { me, screen: { t: 'scores', title: 'Turn 2 scores', rows: scores } as Screen },
    },
    podium: { stage: stage(game({ phase: 'podium', turn: 5, finalTurn: true, gallery: shots }), 24_000), scores, phone: { me, screen: rows } },
    gallery: { stage: stage(game({ phase: 'podium', turn: 5, finalTurn: true, gallery: shots }), 8_000), scores, phone: { me, screen: rows } },
  },
}
```

- [ ] **Step 3: Gallery wiring** (`ThemeGallery.tsx`)

Add imports:

```tsx
import { useMemo } from 'react'   // merge into the existing react import
import { DoodleStage } from './DoodleStage'
import doodle, { demoInk } from './fixtures/doodle-theme'
import type { InkStore } from '../ink/store'
```

Extend `raw` with `doodle`: `const raw = { turf, sprawl, blackjack, bluff, writeitdown, imposter, doodle } as unknown as Record<GameTheme, Raw>`.

Add `ink?: InkStore` to `StageProps`, and in `Stage`'s switch:

```tsx
    case 'doodle': return <GameScene game="doodle"><DoodleStage {...props} ink={ink ?? demoInk()} /></GameScene>
```

(destructure `ink` out of the props in `Stage`: `function Stage({ game, ink, ...props }: StageProps & { game: GameTheme })`). In `ThemeGallery`, create the store once: `const ink = useMemo(() => (game === 'doodle' ? demoInk() : undefined), [game])` (it must be called before the early `if (params.get('view') === 'phone')` return so the hook order is stable), and pass `ink={ink}` to `<Stage ...>`.

- [ ] **Step 4: Look at it**

```bash
cd controller && npm run build
cd ../tv && ./gradlew :devserver:installDist -q
```

Start the devserver (`tv/devserver/build/install/devserver/bin/devserver --port 8090 --pin 4242 --static ../../controller/dist`) or the Vite dev server, and open, at 1920×1080: `/tv?gallery=themes&game=doodle&beat=draw`, `reveal`, `pick`, `scores`, `podium`, `gallery`, and at 390×844 with `&view=phone`: `drawer`, `guess`, `solved`, `pickme`. For each, check: nothing overflows the stage, the picture is crisp, hint letters are orange, bubbles readable across a room, the frame and tape look right, and the reveal plays the time-lapse. Fix CSS until they look good (this is a design task: judge with screenshots, not just the DOM).

- [ ] **Step 5: Type-check, unit tests, commit**

```bash
cd controller && npx tsc -b && npx vitest run
git add controller/src
git commit -m "feat(doodle): the Crayon Studio theme and design-preview fixtures"
```

---

### Task 11: Sixteen bots over real sockets

**Files:**
- Test: `tv/server/src/test/kotlin/partyos/server/DoodleSimulatedPartyTest.kt`

**Interfaces:**
- Consumes: everything in Tasks 4-6. No production code changes: if the test finds a bug, fix it in the owning task's file and add a regression test there.

- [ ] **Step 1: Write the test**

```kotlin
package partyos.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import partyos.engine.ActionResult
import partyos.engine.DoodleDelta
import partyos.engine.DoodleTv
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PhoneState
import partyos.engine.Screen
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.doodle.Doodle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sixteen bot phones play three turns of Doodle Dash against a real server on a real port: each turn one bot draws real
 * strokes over the ink channel while the others guess, and bots drop and rejoin at random. A TV socket listens to the ink.
 */
class DoodleSimulatedPartyTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = PartyEngine(SystemClock, SecureEntropy(), GameRegistry(listOf(Doodle())))
    private val host = PartyHost(engine, SystemClock, scope)
    private val server = PartyServer.start(host, MemoryStatic, ports = 0..0, bindHost = "127.0.0.1")
    private val base = "http://127.0.0.1:${server.port}"
    private val http = HttpClient(CIO) { install(WebSockets) }

    @AfterTest fun stop() {
        http.close(); server.stop()
    }

    private suspend fun join(name: String): String = tokenOf(
        http.post("$base/api/join") {
            contentType(ContentType.Application.Json)
            setBody("""{"room":"${host.tv.value.roomCode}","name":"$name","avatar":{"emoji":"🤖","color":"#445566"}}""")
        }.bodyAsText(),
    )

    private fun obj(vararg kv: Pair<String, String>) = JsonObject(kv.associate { it.first to JsonPrimitive(it.second) })

    /** The word the latest drawer was given: stands in for a guesser who can read a drawing. */
    @Volatile private var secret: String? = null
    private val inked = ConcurrentHashMap.newKeySet<Int>()

    private inner class Bot(val name: String, val token: String, @Volatile var dropRate: Double, seed: Int) {
        private val rnd = Random(seed)
        private var n = 0
        @Volatile var last: PhoneState? = null
        @Volatile var stop = false
        @Volatile var sessions = 0

        private suspend fun DefaultClientWebSocketSession.action(round: Int, payload: JsonObject) =
            send(Frame.Text(PartyJson.encodeToString(ClientMsg.serializer(), ClientMsg.Action("$name-${n++}", round, payload))))

        private suspend fun DefaultClientWebSocketSession.draw(round: Int) {
            val ops = listOf(
                InkOp.Start(1, 0, 1, 100, 100, 50), InkOp.Pts(1, listOf(150, 140, 50, 200, 180, 50)), InkOp.End(1),
                InkOp.Start(2, 1, 1, 300, 300, 50), InkOp.Pts(2, listOf(350, 340, 50)), InkOp.End(2),
                InkOp.Start(3, 2, 2, 500, 200, 50), InkOp.Pts(3, listOf(520, 260, 50, 560, 300, 50)), InkOp.End(3),
            )
            send(Frame.Text(PartyJson.encodeToString(ClientMsg.serializer(), ClientMsg.Ink(round, ops))))
            inked += round
        }

        suspend fun run() {
            while (!stop) {
                sessions++
                try {
                    http.webSocket("ws://127.0.0.1:${server.port}/ws?token=$token") {
                        var dropped = false
                        while (!stop && !dropped) {
                            val frame = withTimeoutOrNull(150) { incoming.receive() }
                            if (frame is Frame.Text) {
                                val m = PartyJson.decodeFromString(ServerMsg.serializer(), frame.readText())
                                if (m is ServerMsg.View) last = m.view
                            }
                            val v = last ?: continue
                            if (v.gameId == null || v.paused) continue
                            if (rnd.nextDouble() < dropRate) { dropped = true; continue } // drop the connection mid-turn
                            when (val s = v.screen) {
                                is Screen.Tutorial -> if (!s.acknowledged) action(v.round, obj("kind" to "ack"))
                                is Screen.ChoiceList -> if (s.kind == "pick" && s.selected == null) action(v.round, obj("kind" to "pick", "option" to s.options.random(rnd).id))
                                is Screen.Draw -> {
                                    secret = s.word
                                    if (v.round !in inked) draw(v.round)
                                }
                                is Screen.Guess -> if (!s.solved && rnd.nextDouble() < 0.3) {
                                    val text = secret?.takeIf { rnd.nextDouble() < 0.5 } ?: "nope${rnd.nextInt(1000)}"
                                    action(v.round, obj("kind" to s.kind, "text" to text))
                                }
                                else -> Unit
                            }
                        }
                    }
                } catch (_: Exception) {
                    // the socket closed under us: reconnect
                }
                if (!stop) delay(50L + rnd.nextLong(150))
            }
        }
    }

    @Test fun sixteenFlakyPhonesFinishAGameWithConsistentScoresAndInk() = runBlocking {
        val bots = (1..16).map { Bot("Bot$it", join("Bot$it"), dropRate = 0.004, seed = it) }
        bots.map { b -> scope.async { b.run() } }
        withTimeout(20_000) { host.tv.first { it.players.count { p -> p.connected } == 16 } }

        // A TV socket: it must see every turn's ink arrive, in order.
        val tvInk = ConcurrentLinkedQueue<ServerMsg.Ink>()
        val hostToken = host.issueHostToken()
        val tvSocket = scope.launch {
            http.webSocket("ws://127.0.0.1:${server.port}/ws?host=$hostToken") {
                for (frame in incoming) {
                    val m = PartyJson.decodeFromString(ServerMsg.serializer(), (frame as Frame.Text).readText())
                    if (m is ServerMsg.Ink) tvInk += m
                }
            }
        }

        // What the TV showed: each turn's deltas (from the scores screen) and how many strokes had arrived by the reveal.
        val deltas = ConcurrentHashMap<Int, List<DoodleDelta>>()
        val strokesAtReveal = ConcurrentHashMap<Int, Int>()
        val driver = scope.launch {
            val skipped = HashSet<Int>()
            var drawTurn = -1
            var drawSince = 0L
            while (isActive) {
                delay(100)
                val tv = host.tv.value
                val st = tv.stage ?: continue
                if (st.paused && tv.players.count { it.connected } >= 2) { host.mutate { host(HostCmd.Resume) }; continue }
                val g = st.game as? DoodleTv ?: continue
                fun strokes() = host.inkSync().turns.firstOrNull { it.turn == g.turn }?.strokes?.size ?: 0
                if (g.phase == "scores") deltas[g.turn] = g.deltas
                if (g.phase == "reveal") strokesAtReveal[g.turn] = strokes()
                when (g.phase) {
                    "reveal", "scores", "podium" -> if (skipped.add(st.phaseSeq)) { delay(30); host.mutate { host(HostCmd.SkipPhase) } }
                    "draw" -> {
                        if (drawTurn != g.turn) { drawTurn = g.turn; drawSince = System.currentTimeMillis() }
                        val waited = System.currentTimeMillis() - drawSince
                        // Let the drawing land, then walk the hint stages quickly; each skip is one stage.
                        if ((strokes() >= 3 && waited > 1_500) || waited > 12_000) {
                            host.mutate { host(HostCmd.SkipPhase) }
                            drawSince = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
        assertEquals(ActionResult.Ack, host.mutate { host(HostCmd.StartGame("doodle", mapOf("rounds" to 3))) })

        val result = withTimeout(180_000) { host.tv.first { it.lastResult != null }.lastResult!! }
        driver.cancel()
        bots.forEach { it.dropRate = 0.0 }

        // Every bot, once reconnected, converges on the same final scoreboard.
        withTimeout(20_000) {
            while (bots.any { b -> b.last?.let { it.gameId == null && it.scores == result.standings } != true }) delay(50)
        }
        bots.forEach { it.stop = true }
        tvSocket.cancel()
        assertTrue(bots.sumOf { it.sessions } > 16, "expected some reconnects")

        // Scores equal a recomputation from what the TV showed: every point is in a turn's deltas.
        assertEquals(setOf(1, 2, 3), deltas.keys)
        val expected = HashMap<String, Int>()
        for (turn in deltas.values) for (d in turn) expected.merge(d.id.v, d.points, Int::plus)
        val actual = result.standings.associate { it.id.v to it.score }.filterValues { it != 0 }
        assertEquals(expected.filterValues { it != 0 }, actual)

        // The ink: each played turn's three strokes reached the server's board, and the TV socket saw them in order.
        assertEquals(setOf(1, 2, 3), strokesAtReveal.keys)
        assertTrue(strokesAtReveal.values.all { it == 3 }, "strokes per turn: $strokesAtReveal")
        val seen = tvInk.toList()
        assertEquals(setOf(1, 2, 3), seen.map { it.turn }.toSet())
        assertEquals(seen.map { it.n }, seen.map { it.n }.sorted())
        assertEquals(seen.size, seen.map { it.n }.toSet().size)
    }
}
```

- [ ] **Step 2: Run it**

Run: `cd tv && ./gradlew :server:test --tests "partyos.server.DoodleSimulatedPartyTest" -q`
Expected: PASS in roughly 30-60 s. If it flakes on `strokes per turn`, the drawer bot dropped for the whole window: check the driver's 12 s cap and the bot's drop rate before loosening anything. If it fails on scores, print `deltas` and `result.standings` side by side: a mismatch is a real accounting bug in `Doodle` (an `Effect.Award` without a matching delta, or the reverse).

- [ ] **Step 3: Commit**

```bash
git add tv/server/src/test/kotlin/partyos/server/DoodleSimulatedPartyTest.kt
git commit -m "test(doodle): sixteen bot phones play a game with live ink, drops and consistent scores"
```

---

### Task 12: Browser tests

**Files:**
- Create: `controller/e2e/doodle.spec.ts`

**Interfaces:**
- Consumes: the theme-gallery beats (Task 10), the class names and labels from Tasks 8-9, `hostPage`/`phone`/`clearParty` helpers.

- [ ] **Step 1: Write the spec**

```ts
import { expect, test, type Locator, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

const gallery = (beat: string) => `/tv?gallery=themes&game=doodle&beat=${beat}`
const phoneView = (beat: string) => `${gallery(beat)}&view=phone`

/** How many pixels of a canvas have been drawn on. */
const inkPixels = (canvas: Locator) =>
  canvas.evaluate((c: HTMLCanvasElement) => {
    const d = c.getContext('2d')!.getImageData(0, 0, c.width, c.height).data
    let n = 0
    for (let i = 3; i < d.length; i += 4) if (d[i] > 0) n++
    return n
  })

async function scribble(page: Page, canvas: Locator) {
  const box = (await canvas.boundingBox())!
  await page.mouse.move(box.x + 30, box.y + 30)
  await page.mouse.down()
  for (let i = 1; i <= 20; i++) await page.mouse.move(box.x + 30 + i * 12, box.y + 30 + Math.sin(i / 2) * 40)
  await page.mouse.up()
}

test.describe('the drawing pad', () => {
  test('a stroke paints on the pad, and undo takes it back off', async ({ page }) => {
    await page.goto(phoneView('drawer'))
    const canvas = page.locator('.draw-canvas')
    await expect(canvas).toBeVisible()
    expect(await inkPixels(canvas)).toBe(0)
    await expect(page.getByRole('button', { name: 'Undo' })).toBeDisabled()
    await scribble(page, canvas)
    expect(await inkPixels(canvas)).toBeGreaterThan(500)
    await page.getByRole('button', { name: 'Undo' }).click()
    expect(await inkPixels(canvas)).toBe(0)
  })

  test('there are eight crayons and three brushes, all thumb-sized', async ({ page }) => {
    await page.goto(phoneView('drawer'))
    await expect(page.getByRole('radio', { name: /^(Brown|Red|Orange|Yellow|Green|Blue|Purple|Pink)$/ })).toHaveCount(8)
    await expect(page.getByRole('radio', { name: /^(Thin|Medium|Fat)$/ })).toHaveCount(3)
    for (const b of await page.locator('.crayon, .brush, .tool').all()) expect((await b.boundingBox())!.height).toBeGreaterThanOrEqual(44)
    await page.getByRole('radio', { name: 'Blue' }).click()
    await expect(page.getByRole('radio', { name: 'Blue' })).toHaveAttribute('aria-checked', 'true')
  })

  test('the word shows to the drawer', async ({ page }) => {
    await page.goto(phoneView('drawer'))
    await expect(page.locator('.draw-word b')).toHaveText('house')
  })
})

test.describe('the guess pad', () => {
  test('shows the blanks, clears the field after a guess, and never shows the word', async ({ page }) => {
    await page.goto(phoneView('guess'))
    await expect(page.locator('.blank-cell')).toHaveCount(5)
    await expect(page.locator('.blank-cell.shown')).toHaveText(['H', 'E'])
    const field = page.getByLabel('Your guess')
    await field.fill('barn')
    await page.getByRole('button', { name: 'Guess' }).click()
    await expect(field).toHaveValue('')
    await expect(page.getByText('house', { exact: true })).toHaveCount(0)
  })

  test('once you have it the pad says so and the field goes away', async ({ page }) => {
    await page.goto(phoneView('solved'))
    await expect(page.getByText('You got it!')).toBeVisible()
    await expect(page.getByText('+850')).toBeVisible()
    await expect(page.getByLabel('Your guess')).toHaveCount(0)
  })

  test('the word list shows how hard each word is', async ({ page }) => {
    await page.goto(phoneView('pickme'))
    await expect(page.locator('.choice-detail')).toHaveText(['Easy', 'Medium', 'Hard'])
  })
})

test.describe('the TV stage', () => {
  const fits = async (el: Locator) => {
    const b = (await el.boundingBox())!
    expect(b.x).toBeGreaterThanOrEqual(0)
    expect(b.y).toBeGreaterThanOrEqual(0)
    expect(b.x + b.width).toBeLessThanOrEqual(1280.5)
    expect(b.y + b.height).toBeLessThanOrEqual(720.5)
  }

  test('the draw scene has the easel with the picture, hint letters, wrong-guess bubbles and who has it', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('draw'))
    await expect(page.locator('.dd-blanks .blank-cell')).toHaveCount(5)
    await expect(page.locator('.dd-blanks .blank-cell.shown')).toHaveText(['H', 'E'])
    await expect(page.locator('.dd-bubble')).toHaveCount(5)
    await expect(page.locator('.dd-solver')).toHaveCount(2)
    await page.waitForTimeout(1200) // the bubbles spring in
    await fits(page.locator('.dd-frame'))
    for (const b of await page.locator('.dd-bubble').all()) await fits(b)
    expect(await inkPixels(page.locator('.dd-paper canvas'))).toBeGreaterThan(2000)
    await expect(page.getByText('HOUSE', { exact: true })).toHaveCount(0) // the word is not on the TV mid-draw
  })

  test('the pick scene shows three face-down cards and who is choosing', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('pick'))
    await expect(page.locator('.dd-card')).toHaveCount(3)
    await expect(page.getByText('is picking a word')).toBeVisible()
  })

  test('the reveal names the word, replays the drawing and lists who got it and who drinks', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('reveal'))
    await expect(page.locator('.dd-word b')).toHaveText('HOUSE')
    await expect(page.locator('.dd-results li')).toHaveCount(3)
    await expect(page.locator('.dd-drinks li')).toHaveCount(2)
    const canvas = page.locator('.dd-paper canvas')
    await page.waitForTimeout(4_500) // the time-lapse runs about 3 seconds
    const after = await inkPixels(canvas)
    expect(after).toBeGreaterThan(2000)
    await fits(page.locator('.dd-frame'))
  })

  test('the gallery hangs every drawing of the night', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('gallery'))
    const shots = page.locator('.dd-shot')
    await expect(shots).toHaveCount(5)
    await page.waitForTimeout(1500)
    for (const s of await shots.all()) await fits(s)
    await expect(page.locator('.dd-shot figcaption b').first()).toHaveText('house')
  })

  test('the podium comes before the gallery', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('podium'))
    await expect(page.locator('.podium-block')).toHaveCount(3)
    await expect(page.locator('.dd-shot')).toHaveCount(0)
  })
})

test('three phones play a turn of Doodle Dash: pick, draw live on the TV, guess, reveal', async ({ browser }) => {
  test.setTimeout(180_000)
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const phones = await Promise.all(['Ana', 'Bo', 'Cy'].map((n) => phone(browser, room, n)))
  await expect(host.locator('.players li')).toHaveCount(3)
  // A real TV page watches the turn, fed by live game state and the ink channel.
  const tv = await (await browser.newContext({ viewport: { width: 1280, height: 720 } })).newPage()
  const tvErrors: string[] = []
  tv.on('pageerror', (e) => tvErrors.push(e.message))
  await tv.goto('/tv')
  await host.getByRole('button', { name: /Doodle Dash/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()

  // Whoever is drawing gets the word list.
  let drawer!: Page
  await expect.poll(async () => {
    for (const p of phones) if (await p.getByRole('heading', { name: 'Pick a word to draw' }).isVisible()) { drawer = p; return true }
    return false
  }, { timeout: 30_000 }).toBe(true)
  const guessers = phones.filter((p) => p !== drawer)
  await drawer.locator('.choice', { hasText: 'Medium' }).click()
  await expect(drawer.locator('.draw-canvas')).toBeVisible()
  const word = ((await drawer.locator('.draw-word b').textContent()) ?? '').trim()
  expect(word.length).toBeGreaterThan(2)

  // The TV shows blanks, never the word, and the ink as it is drawn.
  await expect(tv.locator('.dd-blanks .blank-cell').first()).toBeVisible({ timeout: 20_000 })
  expect(await tv.locator('.dd-blanks .blank-cell').count()).toBe(word.replace(/ /g, '').length)
  await expect(tv.getByText(word, { exact: true })).toHaveCount(0)
  await scribble(drawer, drawer.locator('.draw-canvas'))
  await expect.poll(() => inkPixels(tv.locator('.dd-paper canvas')), { timeout: 10_000 }).toBeGreaterThan(500)

  // A wrong guess floats across the TV; the right one locks the phone.
  const first = guessers[0]
  await first.getByLabel('Your guess').fill('zzzzz')
  await first.getByRole('button', { name: 'Guess' }).click()
  await expect(tv.locator('.dd-bubble', { hasText: 'zzzzz' })).toBeVisible({ timeout: 10_000 })
  for (const g of guessers) {
    await g.getByLabel('Your guess').fill(word)
    await g.getByRole('button', { name: 'Guess' }).click()
    await expect(g.getByText('You got it!')).toBeVisible({ timeout: 10_000 })
  }

  // Everyone has it: the draw ends early and the TV reveals the word.
  await expect(tv.locator('.dd-word b')).toHaveText(word.toUpperCase(), { timeout: 20_000 })
  await expect(drawer.getByRole('heading', { name: /^It was/ })).toBeVisible({ timeout: 20_000 })
  expect(tvErrors).toEqual([])
})
```

- [ ] **Step 2: Build everything the config needs, then run**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd tv && ./gradlew :devserver:installDist -q
cd ../controller && npm run build && npx playwright test e2e/doodle.spec.ts
```

Expected: PASS. A failure in the gallery-based tests is a layout or fixture problem (fix the CSS or the fixture, then look at it again); a failure in the live test is a wiring problem (open the TV page's console: `tvErrors` shows what threw).

- [ ] **Step 3: Run the neighbours, so nothing regressed**

```bash
cd controller && npx playwright test e2e/imposter.spec.ts e2e/themes.spec.ts e2e/lobby.spec.ts
```

Expected: PASS (the lobby lists a ninth game now; if `lobby.spec.ts` or `themes.spec.ts` counts games or themes, update the count there).

- [ ] **Step 4: Commit**

```bash
git add controller/e2e/doodle.spec.ts controller/e2e
git commit -m "test(doodle): browser tests for the pads, the TV scenes and a live turn"
```

---

### Task 13: Docs and the final pass

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Describe the game**

In `README.md`, directly after the Imposter paragraph (search for `**Imposter.**`), add:

```markdown
**Doodle Dash.** Pictionary-style: one player at a time gets a word (pick easy, medium or hard) and draws it on their phone while
the TV shows every stroke live, on a big easel. Everyone else types guesses on their phones; wrong guesses float across the TV
as speech bubbles, a "so close!" is private, and hint letters appear as the clock runs down. Quick guessers score more, harder
words score more, and the drawer scores when others get it. Everyone draws (five turns by default, the last counts double), then
the TV replays each drawing as a time-lapse and hangs the whole night's drawings in a gallery. Three to sixteen players. Strokes
travel over their own socket message (not game state), so drawing never slows the other phones.
```

- [ ] **Step 2: Full verification (evidence before claims)**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd tv && ./gradlew :engine:test :server:test -q
cd ../controller && npx tsc -b && npx vitest run && npm run build
cd ../tv && ./gradlew :devserver:installDist -q
cd ../controller && npx playwright test
```

Expected: every suite green. Paste the pass counts into your report; do not summarise a failure away.

- [ ] **Step 3: Play it for real**

Start the devserver (`Start Party OS.command` or `tv/devserver/build/install/devserver/bin/devserver --static ../../controller/dist`), open `/tv` in the browser pane, join three phone tabs (390×844) from the printed URL, and play two turns end to end. Check by eye: the ink on the TV keeps up with the finger with no visible steps, the pen tip glows, hint letters appear at the two stage changes, the reveal replays the drawing, drinks read correctly (also with "Water tonight" on), a phone that reloads mid-draw comes back sensibly, pausing freezes the clock, and the gallery shows every drawing. Take screenshots of the TV in each phase and of a phone in each screen and look at them.

- [ ] **Step 4: Review**

Use `superpowers:requesting-code-review` on the whole branch (`git diff main...doodle-dash`). Fix what it finds, re-run Step 2, then commit.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: describe Doodle Dash"
```

Do not merge or push: hand the branch back to the user (`superpowers:finishing-a-development-branch`). Mention that spec commit `027fd67` also exists on `jeopardy-revamp`, where another session is working, and was left alone.
