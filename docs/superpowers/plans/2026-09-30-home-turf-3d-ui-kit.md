# Home Turf 3D UI kit and the camera-locked card: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the Home Turf 3D TV, replace the flat well panel with a paper card built in 3D (SDF text, primitives), locked to the camera, for the simple phases: roll, move, manage, jail, choose, pieces and deal. Other phases keep the DOM well.

**Architecture:** A small kit in `controller/src/tv/turf3d/ui/` (pure sizing and HUD logic, plus R3F primitives that lay out in reference pixels at 1080p). A `Dais` parents a card to the camera and scales it from reference pixels to world units, dropping and lifting it when the panel changes or the shot leaves `wide`. A `PanelHost` picks the panel from `g.phase`. The DOM well hides only once the 3D text has actually synced, so a font or worker failure leaves the DOM well showing.

**Tech Stack:** react-three-fiber 9, drei 10 (`Text`, troika SDF), three, vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-30-home-turf-3d-ui-kit-design.md`. Earlier plans: `2026-09-29-home-turf-3d-scene-and-walk.md`, `2026-09-30-home-turf-3d-dice-and-moments.md` (both merged).

## Global Constraints

- The engine (Kotlin) and the `TurfTv` type are not changed. Panels read the same fields the DOM panels read.
- The DOM well stays for the 2D fallback and for every phase not ported (`teamup`, `buy`, `auction`, `card`, `debt`, `trade`, `tally`).
- Nothing loads from a network at runtime. Fonts are bundled `.woff` files copied into the repo (troika cannot read `.woff2`, and fontsource's `exports` map blocks deep imports). Every text component passes an explicit `font`.
- Labels stay generic: no brand names on any drink or card (deny-list Smirnoff, Absolut, Jinro, Chamisul, Budweiser, Heineken, Solo).
- No new audio. Commit messages carry no attribution lines.
- Minimum on-screen text at 1080p: hero 72 px, title 56 px, body 28 px, label 22 px (the spec's 96 px hero minimum is relaxed to 72 so a two-line call fits the standard card; a `title` size is added for the dense setup panels).
- Performance: 50 fps or better at 1080p at `balanced` (Plan 2 measured 59).
- The write-gate hook blocks the first write to any new file and the first edit to a file in a session; if a call is blocked, state the facts it asks for and retry. Gradle is only needed for the final regression run.
- BSD `sed -i` needs an empty suffix argument on macOS; prefer the Edit tool or a small Python script for file edits.

## Deviations from the spec (rulings)

- `Money.tsx` and the count-up curve are not built: no ported panel shows a changing amount. They belong to sub-project 2 (buy, auction), where a number changes on screen.
- `Row.tsx` is not a separate file; the deal panel lays its rows out directly. `Pill` and `Button` are built because jail and choose use them.
- The 3D card's header shows a coloured disc with the player's drink, the name, the game clock and the timer; the footer shows the ticker (three lines outside setup phases).

## Review Focus

Inputs the spec implies but the happy-path tests do not exercise, most likely first. Each has a test or a check in the task named.

1. **A font or troika worker failure must not blank the panel.** The DOM well hides only after a text `onSync` fires. Task 5 (`panelReady` gate; the off-origin check in Task 8).
2. **The card must never cover a walking piece or the dice.** It hides for every shot except `wide`. Task 5 (`Dais` visibility) and Task 8 screenshots.
3. **A phase change while a card is dropping must not show the wrong panel or leave a card half-scaled.** `stepToward` is tested to snap and never overshoot. Task 1.
4. **Timer and game-clock edge cases:** paused stage with no frozen value, no clock limit, last lap, and a deadline already passed must give sane numbers, never NaN or negative. Task 2.
5. **Long or many names:** six players on the deal panel and a long token name must stay inside the card. Task 7 (wrap widths) and Task 8 screenshots with the six-player fixture.
6. **Nothing may load from off-origin.** Task 8 fails the run if any request in the screenshot pass is off-origin.

---

## File structure

Create under `controller/src/tv/turf3d/ui/`:

| File | Responsibility |
| --- | --- |
| `sizing.ts` (+ test) | Reference-pixel to world conversion, text minimums, font fitting, card and body dimensions, the `stepToward` easing step |
| `hud.ts` (+ test) | Pure HUD logic shared with the DOM stage: game clock, timer, ticker tail, `buildHud`; setup phases and decision lengths |
| `panels.ts` (+ test) | `panelFor(phase)`, `panelSize(name)`, and the copy for each call |
| `theme.ts`, `fonts/*.woff` | Palette and the bundled fonts |
| `Card.tsx` | `roundedRect`, `Plate`, `Card` |
| `Label.tsx`, `Pill.tsx`, `Button.tsx`, `TimerRing.tsx`, `DrinkIcon.tsx` | The kit's text and shape primitives |
| `Frame.tsx` | The card frame: header, body slot, footer |
| `Dais.tsx` | The camera-locked mount with drop and lift |
| `PanelHost.tsx` | Chooses and renders the panel for the phase inside a `Dais` and `Frame` |
| `panels/RollPanel.tsx`, `ManagePanel.tsx`, `JailPanel.tsx`, `ChoosePanel.tsx`, `PiecesPanel.tsx`, `DealPanel.tsx` | One small component per ported phase |

Modify: `TurfStage3D.tsx`, `TurfScene.tsx`, `../TurfStage.tsx`, `../turf.css`, `../TurfGallery.tsx`, `controller/scripts/turf3d-shots.mjs`, `README.md`.

---

### Task 1: Sizing

**Files:**
- Create: `controller/src/tv/turf3d/ui/sizing.ts`
- Test: `controller/src/tv/turf3d/ui/sizing.test.ts`

**Interfaces:**
- Produces: `REF_H = 1080`; `type TextKind = 'hero' | 'title' | 'body' | 'label'`; `TEXT_MIN: Record<TextKind, number>`; `textPx(kind, px): number`; `worldPerPx(fovDeg, dist, refH?): number`; `fitFont(chars, widthPx, maxPx, minPx, em?): number`; `type CardSize = 'std' | 'setup'`; `CARD: Record<CardSize, {w:number;h:number}>`, `HEAD_H = 84`, `FOOT_H = 80`; `bodyOf(size): {w:number;h:number;cy:number}`; `stepToward(cur, target, dt, rate?): number`.

- [ ] **Step 1: Write the failing test**

```ts
// controller/src/tv/turf3d/ui/sizing.test.ts
import { describe, expect, it } from 'vitest'
import { CARD, FOOT_H, HEAD_H, REF_H, TEXT_MIN, bodyOf, fitFont, stepToward, textPx, worldPerPx } from './sizing'

describe('text minimums', () => {
  it('never lets text go below its kind\'s minimum, and never shrinks bigger text', () => {
    expect(TEXT_MIN).toEqual({ hero: 72, title: 56, body: 28, label: 22 })
    expect(textPx('body', 12)).toBe(28)
    expect(textPx('label', 10)).toBe(22)
    expect(textPx('hero', 96)).toBe(96)
    expect(textPx('body', 40)).toBe(40)
  })
})

describe('worldPerPx', () => {
  it('is the world height a reference pixel covers at a distance', () => {
    expect(worldPerPx(38, 9, REF_H)).toBeCloseTo(0.0057388, 6)
    expect(worldPerPx(38, 18, REF_H)).toBeCloseTo(2 * worldPerPx(38, 9, REF_H), 9)
  })
})

describe('fitFont', () => {
  it('shrinks with length but stays inside [min, max]', () => {
    expect(fitFont(5, 600, 96, 72)).toBe(96)
    expect(fitFont(20, 600, 96, 72)).toBe(72)
    expect(fitFont(12, 600, 96, 40)).toBe(96)
    expect(fitFont(30, 600, 96, 40)).toBe(40)
    expect(fitFont(0, 600, 96, 72)).toBe(96)
  })
})

describe('the card', () => {
  it('has a standard size that fits inside the printed middle of the board, and a bigger setup size', () => {
    expect(CARD.std).toEqual({ w: 610, h: 395 })
    expect(CARD.setup.w).toBeGreaterThan(CARD.std.w)
    expect(CARD.setup.h).toBeGreaterThan(CARD.std.h)
  })
  it('splits into a header, a body and a footer', () => {
    const b = bodyOf('std')
    expect(b.h).toBe(CARD.std.h - HEAD_H - FOOT_H)
    expect(b.cy).toBe((FOOT_H - HEAD_H) / 2)
    expect(b.w).toBe(CARD.std.w - 40)
    expect(bodyOf('setup').h).toBeGreaterThan(b.h)
  })
})

describe('stepToward', () => {
  it('moves toward the target without overshooting, and snaps when close', () => {
    let v = 0
    for (let i = 0; i < 300; i++) { const n = stepToward(v, 1, 1 / 60); expect(n).toBeGreaterThanOrEqual(v); expect(n).toBeLessThanOrEqual(1); v = n }
    expect(v).toBe(1)
    expect(stepToward(1, 0, 1 / 60)).toBeLessThan(1)
    expect(stepToward(0.5, 0.5, 1 / 60)).toBe(0.5)
    expect(stepToward(0, 1, 10)).toBeGreaterThan(0.99) // a long frame cannot overshoot either
    expect(stepToward(0, 1, 0)).toBe(0)
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/sizing.test.ts`
Expected: FAIL, cannot resolve `./sizing`.

- [ ] **Step 3: Write the implementation**

```ts
// controller/src/tv/turf3d/ui/sizing.ts
/** The kit lays everything out in reference pixels, as if the TV were 1080 px tall; the Dais scales that to world units. */
export const REF_H = 1080

export type TextKind = 'hero' | 'title' | 'body' | 'label'
/** The smallest each kind of text may be on screen at 1080p: readable from across the room and when drunk. */
export const TEXT_MIN: Record<TextKind, number> = { hero: 72, title: 56, body: 28, label: 22 }
export const textPx = (kind: TextKind, px: number): number => Math.max(TEXT_MIN[kind], px)

/** World units covered by one reference pixel at [dist] from a camera with vertical field of view [fovDeg]. */
export const worldPerPx = (fovDeg: number, dist: number, refH: number = REF_H): number =>
  (2 * dist * Math.tan((fovDeg * Math.PI) / 360)) / refH

/** A font size that lets [chars] characters fit [widthPx], clamped to [minPx, maxPx]. [em] is the average glyph width in ems. */
export function fitFont(chars: number, widthPx: number, maxPx: number, minPx: number, em = 0.5): number {
  const ideal = Math.floor(widthPx / Math.max(chars, 1) / em)
  return Math.min(maxPx, Math.max(minPx, ideal))
}

export type CardSize = 'std' | 'setup'
/** std sits inside the printed middle of the board (about 610 by 395 px on screen); setup is used while nobody is walking. */
export const CARD: Record<CardSize, { w: number; h: number }> = { std: { w: 610, h: 395 }, setup: { w: 800, h: 560 } }
export const HEAD_H = 84
export const FOOT_H = 80

/** The body area of a card: its size and the y of its centre (the card is centred on 0, y up). */
export function bodyOf(size: CardSize): { w: number; h: number; cy: number } {
  const c = CARD[size]
  return { w: c.w - 40, h: c.h - HEAD_H - FOOT_H, cy: (FOOT_H - HEAD_H) / 2 }
}

/** One frame of exponential easing. Frame-rate independent, never overshoots, snaps when within a thousandth. */
export function stepToward(cur: number, target: number, dt: number, rate = 14): number {
  if (Math.abs(target - cur) < 0.001) return target
  return cur + (target - cur) * (1 - Math.exp(-rate * dt))
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/sizing.test.ts && npx tsc -b`
Expected: PASS, 6 tests; no tsc output.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/ui/sizing.ts controller/src/tv/turf3d/ui/sizing.test.ts
git commit -m "feat(turf3d): reference-pixel sizing, text minimums and card dimensions for the 3D UI kit"
```

---

### Task 2: HUD logic shared with the DOM stage

**Files:**
- Create: `controller/src/tv/turf3d/ui/hud.ts`
- Test: `controller/src/tv/turf3d/ui/hud.test.ts`
- Modify: `controller/src/tv/TurfStage.tsx` (use the shared logic)

**Interfaces:**
- Consumes: `TurfTv` from `../../types`.
- Produces: `SETUP_PHASES: Set<string>`; `DECISION_MS: Record<string, number>`; `type Clock = { deadline: number | null; frozen: number | null }`; `gameClockLeft(g, clock, now): number | null`; `clockLabel(left, lastLap): { text: string; tone: 'gold' | 'red' | 'plain' }`; `interface TimerSpec { deadline: number | null; frozen: number | null; total: number }`; `timerFor(g, clock, paused, scale): TimerSpec | null`; `timerLeft(t, now): number`; `timerFraction(t, now): number`; `tickerTail(ticker, n?)`; `interface HudIn { clock: Clock; paused: boolean; timerScale: number; seatName: string }`; `interface Hud { showTurn: boolean; name: string; seatName: string; color: string; piece?: string; clock: ReturnType<typeof clockLabel>; timer: TimerSpec | null; ticker: string[] }`; `buildHud(g, input, now): Hud`.

- [ ] **Step 1: Write the failing test**

```ts
// controller/src/tv/turf3d/ui/hud.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfTv } from '../../types'
import { buildHud, clockLabel, gameClockLeft, tickerTail, timerFor, timerFraction, timerLeft } from './hud'

const tok = (name: string, color: string, piece?: string) => ({ name, color, piece, members: [], cash: 0, pos: 0, jailed: false, jailCards: 0, bankrupt: false, worth: 0, sets: 0 })
const g = (over: Partial<TurfTv> = {}): TurfTv => ({
  t: 'turf', phase: 'roll', teams: false, board: [], chanceName: '', chestName: '', owner: [], level: [], mortgaged: [],
  tokens: [tok('Amanda', '#7a3cff', 'boombox'), tok('Daniel', '#ff9a1f')], turn: 0, dice: [], doubles: 0, housesLeft: 18, hotelsLeft: 11, buy: -1,
  lastLap: false, timed: true, drinks: true, beats: [], ticker: ['a', 'b', 'c', 'd'], pieces: [], tally: [], clockLeftMs: 600_000, phaseMs: 20_000, ...over,
})

describe('gameClockLeft', () => {
  it('is null with no limit, whole during setup, and counts down with the phase clock otherwise', () => {
    expect(gameClockLeft(g({ clockLeftMs: undefined }), { deadline: null, frozen: null }, 0)).toBeNull()
    expect(gameClockLeft(g({ phase: 'pieces' }), { deadline: null, frozen: null }, 0)).toBe(600_000)
    expect(gameClockLeft(g(), { deadline: null, frozen: 5000 }, 0)).toBe(585_000)
    expect(gameClockLeft(g(), { deadline: 100_000, frozen: null }, 95_000)).toBe(585_000)
  })
  it('never goes negative or NaN, even with a deadline in the past', () => {
    expect(gameClockLeft(g({ clockLeftMs: 1000 }), { deadline: 1, frozen: null }, 999_999)).toBe(0)
    expect(gameClockLeft(g({ phaseMs: undefined }), { deadline: null, frozen: null }, 0)).toBe(600_000)
  })
})

describe('clockLabel', () => {
  it('reads as minutes and seconds, red under five minutes, with the special states spelled out', () => {
    expect(clockLabel(585_000, false)).toEqual({ text: '9:45', tone: 'gold' })
    expect(clockLabel(200_000, false)).toEqual({ text: '3:20', tone: 'red' })
    expect(clockLabel(5_000, false)).toEqual({ text: '0:05', tone: 'red' })
    expect(clockLabel(null, false)).toEqual({ text: 'NO TIME LIMIT', tone: 'plain' })
    expect(clockLabel(585_000, true)).toEqual({ text: 'LAST LAP', tone: 'red' })
  })
})

describe('the timer', () => {
  const idle = { deadline: null, frozen: null }
  it('shows only for a timed game with a running or frozen clock', () => {
    expect(timerFor(g({ timed: false }), { deadline: 5, frozen: null }, false, 1)).toBeNull()
    expect(timerFor(g(), idle, false, 1)).toBeNull()
    expect(timerFor(g(), { deadline: 5, frozen: null }, false, 1)).toEqual({ deadline: 5, frozen: null, total: 20_000 })
  })
  it('uses the decision length for the phase, stretched by the timer setting, and 6 s once an auction has bids', () => {
    expect(timerFor(g({ phase: 'jail' }), { deadline: 5, frozen: null }, false, 1.5)?.total).toBe(22_500)
    expect(timerFor(g({ phase: 'auction', auction: { id: 1, space: 3, top: 10, leader: 0, bids: 2 } }), { deadline: 5, frozen: null }, false, 1)?.total).toBe(6_000)
    expect(timerFor(g({ phase: 'card' }), { deadline: 5, frozen: null }, false, 1)?.total).toBe(20_000)
  })
  it('freezes at the frozen value when paused, and at zero if there is none', () => {
    expect(timerFor(g(), { deadline: 5, frozen: 7000 }, true, 1)).toEqual({ deadline: null, frozen: 7000, total: 20_000 })
    expect(timerFor(g(), { deadline: 5, frozen: null }, true, 1)).toEqual({ deadline: null, frozen: 0, total: 20_000 })
  })
  it('reports time left and a 0 to 1 fraction that cannot leave that range', () => {
    const t = { deadline: 30_000, frozen: null, total: 20_000 }
    expect(timerLeft(t, 20_000)).toBe(10_000)
    expect(timerFraction(t, 20_000)).toBe(0.5)
    expect(timerFraction(t, 40_000)).toBe(0)
    expect(timerFraction(t, 0)).toBe(1)
    expect(timerFraction({ deadline: null, frozen: 3000, total: 20_000 }, 0)).toBeCloseTo(0.15, 9)
    expect(timerFraction({ deadline: null, frozen: null, total: 0 }, 0)).toBe(0)
  })
})

describe('tickerTail and buildHud', () => {
  it('keeps the last three lines', () => { expect(tickerTail(['a', 'b', 'c', 'd'])).toEqual(['b', 'c', 'd']); expect(tickerTail([])).toEqual([]) })
  it('describes the turn during play', () => {
    const h = buildHud(g(), { clock: { deadline: null, frozen: 5000 }, paused: false, timerScale: 1, seatName: 'Amanda P' }, 0)
    expect(h).toMatchObject({ showTurn: true, name: 'Amanda', seatName: 'Amanda P', color: '#7a3cff', piece: 'boombox', ticker: ['b', 'c', 'd'] })
    expect(h.clock.text).toBe('9:45'); expect(h.timer?.total).toBe(20_000)
  })
  it('drops the turn header and the ticker in setup phases', () => {
    const h = buildHud(g({ phase: 'deal' }), { clock: { deadline: null, frozen: null }, paused: false, timerScale: 1, seatName: '' }, 0)
    expect(h.showTurn).toBe(false); expect(h.ticker).toEqual([])
  })
  it('copes with no tokens at all', () => {
    const h = buildHud(g({ tokens: [] }), { clock: { deadline: null, frozen: null }, paused: false, timerScale: 1, seatName: '' }, 0)
    expect(h.showTurn).toBe(false); expect(h.name).toBe('')
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/hud.test.ts`
Expected: FAIL, cannot resolve `./hud`.

- [ ] **Step 3: Write the implementation**

```ts
// controller/src/tv/turf3d/ui/hud.ts
import type { TurfTv } from '../../types'

/** Phases before the game proper: no turn header, no ticker, and the game clock is not counting yet. */
export const SETUP_PHASES = new Set<string>(['teamup', 'pieces', 'deal', 'tally', 'podium'])

/** How long the engine gives each decision (before the timer setting stretches it); the TV's pie drains over this. */
export const DECISION_MS: Record<string, number> = { roll: 20_000, jail: 15_000, buy: 15_000, auction: 10_000, choose: 10_000, manage: 20_000, debt: 60_000, trade: 30_000, pieces: 15_000, teamup: 15_000 }

export type Clock = { deadline: number | null; frozen: number | null }

/** Game clock left in ms, or null when the game has no time limit. The engine sends a value at the start of each phase; the phase clock ticks it down between updates. */
export function gameClockLeft(g: Pick<TurfTv, 'clockLeftMs' | 'phaseMs' | 'phase'>, clock: Clock, now: number): number | null {
  if (g.clockLeftMs == null) return null
  if (SETUP_PHASES.has(g.phase)) return Math.max(0, g.clockLeftMs)
  const left = clock.frozen ?? (clock.deadline ? Math.max(0, clock.deadline - now) : g.phaseMs ?? 0)
  return Math.max(0, g.clockLeftMs - Math.max(0, (g.phaseMs ?? 0) - left))
}

export function clockLabel(left: number | null, lastLap: boolean): { text: string; tone: 'gold' | 'red' | 'plain' } {
  if (lastLap) return { text: 'LAST LAP', tone: 'red' }
  if (left == null) return { text: 'NO TIME LIMIT', tone: 'plain' }
  const m = Math.floor(left / 60_000), s = Math.floor((left % 60_000) / 1000)
  return { text: `${m}:${String(s).padStart(2, '0')}`, tone: left < 5 * 60_000 ? 'red' : 'gold' }
}

export interface TimerSpec { deadline: number | null; frozen: number | null; total: number }

/** The decision timer for the phase, or null when the game is untimed or no clock is running. Mirrors the DOM well. */
export function timerFor(g: Pick<TurfTv, 'timed' | 'phase' | 'auction'>, clock: Clock, paused: boolean, scale: number): TimerSpec | null {
  if (!g.timed || (clock.deadline == null && clock.frozen == null)) return null
  const total = (g.phase === 'auction' && (g.auction?.bids ?? 0) > 0 ? 6_000 : DECISION_MS[g.phase] ?? 20_000) * scale
  return { deadline: paused ? null : clock.deadline, frozen: paused ? clock.frozen ?? 0 : clock.frozen, total }
}

export const timerLeft = (t: TimerSpec, now: number): number => t.frozen ?? (t.deadline ? Math.max(0, t.deadline - now) : 0)
export const timerFraction = (t: TimerSpec, now: number): number => (t.total > 0 ? Math.min(1, Math.max(0, timerLeft(t, now) / t.total)) : 0)

export const tickerTail = (ticker: string[], n = 3): string[] => ticker.slice(-n)

export interface HudIn { clock: Clock; paused: boolean; timerScale: number; seatName: string }
export interface Hud {
  showTurn: boolean
  name: string
  seatName: string
  color: string
  piece?: string
  clock: ReturnType<typeof clockLabel>
  timer: TimerSpec | null
  ticker: string[]
}

/** Everything the card's header and footer show, from the game state and the stage's clock. */
export function buildHud(g: TurfTv, input: HudIn, now: number): Hud {
  const cur = g.tokens[g.turn]
  return {
    showTurn: !!cur && !SETUP_PHASES.has(g.phase),
    name: cur?.name ?? '',
    seatName: input.seatName,
    color: cur?.color ?? '#888888',
    piece: cur?.piece,
    clock: clockLabel(gameClockLeft(g, input.clock, now), g.lastLap),
    timer: timerFor(g, input.clock, input.paused, input.timerScale),
    ticker: SETUP_PHASES.has(g.phase) ? [] : tickerTail(g.ticker),
  }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/hud.test.ts && npx tsc -b`
Expected: PASS, 11 tests; no tsc output.

- [ ] **Step 5: Make the DOM stage use the shared logic**

In `controller/src/tv/TurfStage.tsx` (edits by exact match; read the file first):

1. Add to the imports: `import { DECISION_MS, SETUP_PHASES, gameClockLeft, type Clock } from './turf3d/ui/hud'`.
2. Delete the line `type Clock = { deadline: number | null; frozen: number | null }` and the line `const DECISION_MS: Record<string, number> = { ... }`.
3. Replace `const SETUP = new Set(['teamup', 'pieces', 'deal', 'tally', 'podium'])` with `const SETUP = SETUP_PHASES`.
4. Replace the body of `useGameClock` after the `useEffect` line with `return gameClockLeft(g, clock, now)` (delete the lines that compute `left`).

- [ ] **Step 6: Verify nothing moved**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; all tests pass (170 plus the new ones); `✓ built in`.

- [ ] **Step 7: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/ui/hud.ts controller/src/tv/turf3d/ui/hud.test.ts controller/src/tv/TurfStage.tsx
git commit -m "feat(turf3d): shared HUD logic (game clock, timer, ticker) for the DOM well and the 3D card"
```

---

### Task 3: Which panel, and what it says

**Files:**
- Create: `controller/src/tv/turf3d/ui/panels.ts`
- Test: `controller/src/tv/turf3d/ui/panels.test.ts`

**Interfaces:**
- Consumes: `TurfPhase, TurfTv` from `../../types`; `CardSize` from `./sizing`.
- Produces: `type PanelName = 'roll' | 'move' | 'manage' | 'jail' | 'choose' | 'pieces' | 'deal'`; `panelFor(phase): PanelName | null`; `panelSize(name): CardSize`; `rollCall(doubles, seatName): string`; `chooseCall(choose?): string`; `bankLine(g): string`; `PIECE_ORDER: string[]`.

- [ ] **Step 1: Write the failing test**

```ts
// controller/src/tv/turf3d/ui/panels.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfPhase } from '../../types'
import { PIECE_ORDER, bankLine, chooseCall, panelFor, panelSize, rollCall } from './panels'

describe('panelFor', () => {
  it('maps the ported phases to their own panel', () => {
    for (const p of ['roll', 'move', 'manage', 'jail', 'choose', 'pieces', 'deal'] as TurfPhase[]) expect(panelFor(p)).toBe(p)
  })
  it('leaves every other phase to the DOM well', () => {
    for (const p of ['teamup', 'buy', 'auction', 'card', 'debt', 'trade', 'tally', 'podium'] as TurfPhase[]) expect(panelFor(p), p).toBeNull()
  })
})

describe('panelSize', () => {
  it('uses the big card only where nobody is walking (setup) and the standard card everywhere else', () => {
    expect(panelSize('pieces')).toBe('setup'); expect(panelSize('deal')).toBe('setup')
    for (const n of ['roll', 'move', 'manage', 'jail', 'choose'] as const) expect(panelSize(n)).toBe('std')
  })
})

describe('the copy', () => {
  it('says who rolls, or that it is doubles', () => {
    expect(rollCall(0, 'Amanda')).toBe('AMANDA ROLLS')
    expect(rollCall(1, 'Amanda')).toBe('DOUBLES! ROLL AGAIN')
    expect(rollCall(0, '')).toBe(' ROLLS')
  })
  it('names the choice', () => {
    expect(chooseCall('bus')).toBe('BUS! PICK A MOVE'); expect(chooseCall('triples')).toBe('TRIPLES! GO ANYWHERE'); expect(chooseCall(undefined)).toBe('TRIPLES! GO ANYWHERE')
  })
  it('counts the bank', () => { expect(bankLine({ housesLeft: 18, hotelsLeft: 11 })).toBe('Bank: 18 houses · 11 hotels') })
  it('lists the six pieces the engine offers', () => { expect(PIECE_ORDER).toEqual(['cup', 'pizza', 'sneaker', 'boombox', 'cone', 'duck']) })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/panels.test.ts`
Expected: FAIL, cannot resolve `./panels`.

- [ ] **Step 3: Write the implementation**

```ts
// controller/src/tv/turf3d/ui/panels.ts
import type { TurfPhase, TurfTv } from '../../types'
import type { CardSize } from './sizing'

export type PanelName = 'roll' | 'move' | 'manage' | 'jail' | 'choose' | 'pieces' | 'deal'

const PORTED: Partial<Record<TurfPhase, PanelName>> = { roll: 'roll', move: 'move', manage: 'manage', jail: 'jail', choose: 'choose', pieces: 'pieces', deal: 'deal' }

/** The 3D panel for a phase, or null while that phase still uses the DOM well. */
export const panelFor = (phase: TurfPhase): PanelName | null => PORTED[phase] ?? null

/** The setup panels are dense and nobody is walking then, so they get the bigger card. */
export const panelSize = (name: PanelName): CardSize => (name === 'pieces' || name === 'deal' ? 'setup' : 'std')

export const rollCall = (doubles: number, seatName: string): string => (doubles > 0 ? 'DOUBLES! ROLL AGAIN' : `${seatName.toUpperCase()} ROLLS`)
export const chooseCall = (choose?: 'bus' | 'triples'): string => (choose === 'bus' ? 'BUS! PICK A MOVE' : 'TRIPLES! GO ANYWHERE')
export const bankLine = (g: Pick<TurfTv, 'housesLeft' | 'hotelsLeft'>): string => `Bank: ${g.housesLeft} houses · ${g.hotelsLeft} hotels`

/** The piece ids the engine hands out today, in the order the picker shows them. */
export const PIECE_ORDER = ['cup', 'pizza', 'sneaker', 'boombox', 'cone', 'duck']
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/panels.test.ts && npx tsc -b`
Expected: PASS, 7 tests; no tsc output.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/ui/panels.ts controller/src/tv/turf3d/ui/panels.test.ts
git commit -m "feat(turf3d): which 3D panel each phase gets, and the copy on each"
```

---

### Task 4: The kit's primitives

**Files:**
- Create: `controller/src/tv/turf3d/ui/fonts/anton.woff`, `zilla-500.woff`, `zilla-700.woff`, `dseg7.woff` (copied)
- Create: `controller/src/tv/turf3d/ui/theme.ts`, `Card.tsx`, `Label.tsx`, `Pill.tsx`, `Button.tsx`, `TimerRing.tsx`, `DrinkIcon.tsx`

**Interfaces:**
- Consumes: `textPx, type TextKind` from `./sizing`; `timerLeft, timerFraction, type TimerSpec` from `./hud`; `Drink` from `../Drinks`; `drinkFor` from `../pieces`.
- Produces (all coordinates in reference pixels, centred, y up, drawn in the z = 0 plane of their parent):
  - `theme.ts`: `INK, PAPER, PAPER_DARK, RED, GOLD, GREEN, MUTED, SHADOW, LED_BG` colours; `FONT: { display, body, bodyBold, led }` (URLs); `type FontName`.
  - `Plate({ w, h, r?, color, x?, y?, z? })`, `Card({ w, h, children })`, `roundedRect(w, h, r)`.
  - `Label({ children: string, px, kind?, color?, x?, y?, z?, maxWidth?, align?, anchorX?, font?, outline?, onSync? })`.
  - `Pill({ w, h?, fill?, text, textColor?, px?, x?, y? })`, `Button({ w, label, fill?, x?, y? })`.
  - `TimerRing({ timer, r?, x?, y? })`, `DrinkIcon({ piece, color, size?, x?, y? })`.

No unit tests cover R3F components; this task is verified by type-check and build, and looked at in Task 5.

- [ ] **Step 1: Copy the fonts in**

Run:
```bash
cd /Users/jjahn/HoopDreams/controller && mkdir -p src/tv/turf3d/ui/fonts && cp node_modules/@fontsource/anton/files/anton-latin-400-normal.woff src/tv/turf3d/ui/fonts/anton.woff && cp node_modules/@fontsource/zilla-slab/files/zilla-slab-latin-500-normal.woff src/tv/turf3d/ui/fonts/zilla-500.woff && cp node_modules/@fontsource/zilla-slab/files/zilla-slab-latin-700-normal.woff src/tv/turf3d/ui/fonts/zilla-700.woff && cp node_modules/@fontsource/dseg7-classic/files/dseg7-classic-latin-400-normal.woff src/tv/turf3d/ui/fonts/dseg7.woff && ls -la src/tv/turf3d/ui/fonts
```
Expected: four `.woff` files, each under 100 KB.

- [ ] **Step 2: Theme**

```ts
// controller/src/tv/turf3d/ui/theme.ts
import antonUrl from './fonts/anton.woff?url'
import zilla500Url from './fonts/zilla-500.woff?url'
import zilla700Url from './fonts/zilla-700.woff?url'
import dsegUrl from './fonts/dseg7.woff?url'

export const INK = '#1a1a1a'
export const PAPER = '#fbf3dc'
export const PAPER_DARK = '#efe2bd'
export const RED = '#e2483d'
export const GOLD = '#ffd23f'
export const GREEN = '#2fbf55'
export const MUTED = '#6b5a3e'
export const SHADOW = '#2a1a0e'
export const LED_BG = '#241710'

/** Bundled fonts (troika reads .woff, not .woff2). Every Label passes one explicitly so nothing is fetched from a CDN. */
export const FONT = { display: antonUrl, body: zilla500Url, bodyBold: zilla700Url, led: dsegUrl }
export type FontName = keyof typeof FONT
```

- [ ] **Step 3: Card and plates**

```tsx
// controller/src/tv/turf3d/ui/Card.tsx
import { useEffect, useMemo, type ReactNode } from 'react'
import * as THREE from 'three'
import { INK, PAPER, SHADOW } from './theme'

/** A rounded rectangle centred on the origin, facing +z. */
export function roundedRect(w: number, h: number, r: number): THREE.ShapeGeometry {
  const rr = Math.min(r, w / 2, h / 2), x = -w / 2, y = -h / 2
  const s = new THREE.Shape()
  s.moveTo(x + rr, y); s.lineTo(x + w - rr, y); s.quadraticCurveTo(x + w, y, x + w, y + rr)
  s.lineTo(x + w, y + h - rr); s.quadraticCurveTo(x + w, y + h, x + w - rr, y + h)
  s.lineTo(x + rr, y + h); s.quadraticCurveTo(x, y + h, x, y + h - rr)
  s.lineTo(x, y + rr); s.quadraticCurveTo(x, y, x + rr, y)
  return new THREE.ShapeGeometry(s, 8)
}

/** A flat, unlit rounded plate. Unlit so paper stays paper whatever the scene lights are doing. */
export function Plate({ w, h, r = 14, color, x = 0, y = 0, z = 0 }: { w: number; h: number; r?: number; color: string; x?: number; y?: number; z?: number }) {
  const geo = useMemo(() => roundedRect(w, h, r), [w, h, r])
  useEffect(() => () => geo.dispose(), [geo])
  return (
    <mesh geometry={geo} position={[x, y, z]}>
      <meshBasicMaterial color={color} toneMapped={false} />
    </mesh>
  )
}

/** The paper card: a hard offset shadow, an ink outline and a paper face. Children draw at z >= 1. */
export function Card({ w, h, children }: { w: number; h: number; children?: ReactNode }) {
  return (
    <group>
      <Plate w={w + 16} h={h + 16} r={26} color={SHADOW} x={10} y={-10} z={-3} />
      <Plate w={w + 16} h={h + 16} r={26} color={INK} z={-2} />
      <Plate w={w} h={h} r={20} color={PAPER} z={-1} />
      {children}
    </group>
  )
}
```

- [ ] **Step 4: Label**

```tsx
// controller/src/tv/turf3d/ui/Label.tsx
import { Text } from '@react-three/drei'
import { FONT, INK, type FontName } from './theme'
import { textPx, type TextKind } from './sizing'

/** Hero and title text is Anton; body and label text is Zilla Slab, unless [font] says otherwise. */
const DEFAULT_FONT: Record<TextKind, FontName> = { hero: 'display', title: 'display', body: 'body', label: 'body' }

export interface LabelProps {
  children: string
  px: number
  kind?: TextKind
  color?: string
  x?: number; y?: number; z?: number
  maxWidth?: number
  align?: 'left' | 'center' | 'right'
  anchorX?: 'left' | 'center' | 'right'
  font?: FontName
  /** An ink outline that makes light text read like a sticker. */
  outline?: string
  onSync?: () => void
}

/** SDF text in reference pixels. The size is clamped up to the minimum for its kind, so nothing here can be too small to read. */
export function Label({ children, px, kind = 'body', color = INK, x = 0, y = 0, z = 1, maxWidth, align = 'center', anchorX = 'center', font, outline, onSync }: LabelProps) {
  const size = textPx(kind, px)
  return (
    <Text
      font={FONT[font ?? DEFAULT_FONT[kind]]}
      fontSize={size}
      color={color}
      maxWidth={maxWidth}
      textAlign={align}
      anchorX={anchorX}
      anchorY="middle"
      lineHeight={1.05}
      position={[x, y, z]}
      outlineWidth={outline ? size * 0.07 : 0}
      outlineColor={outline ?? INK}
      onSync={onSync}
    >
      {children}
    </Text>
  )
}
```

- [ ] **Step 5: Pill and Button**

```tsx
// controller/src/tv/turf3d/ui/Pill.tsx
import { Plate } from './Card'
import { Label } from './Label'
import { INK, PAPER } from './theme'

/** A small rounded tag with ink outline. */
export function Pill({ w, h = 40, fill = PAPER, text, textColor = INK, px = 22, x = 0, y = 0 }: { w: number; h?: number; fill?: string; text: string; textColor?: string; px?: number; x?: number; y?: number }) {
  return (
    <group position={[x, y, 0]}>
      <Plate w={w + 6} h={h + 6} r={(h + 6) / 2} color={INK} z={1} />
      <Plate w={w} h={h} r={h / 2} color={fill} z={2} />
      <Label px={px} kind="label" color={textColor} z={3} maxWidth={w - 16} font="bodyBold">{text}</Label>
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/Button.tsx
import { Plate } from './Card'
import { Label } from './Label'
import { GOLD, INK, SHADOW } from './theme'

/** A chunky button with a hard shadow. Display only: the TV has no pointer; it shows what players can pick on their phones. */
export function Button({ w, label, fill = GOLD, x = 0, y = 0 }: { w: number; label: string; fill?: string; x?: number; y?: number }) {
  const h = 52
  return (
    <group position={[x, y, 0]}>
      <Plate w={w + 6} h={h + 6} r={14} color={SHADOW} x={5} y={-5} z={1} />
      <Plate w={w + 6} h={h + 6} r={14} color={INK} z={2} />
      <Plate w={w} h={h} r={11} color={fill} z={3} />
      <Label px={24} kind="label" z={4} maxWidth={w - 16} font="bodyBold">{label}</Label>
    </group>
  )
}
```

- [ ] **Step 6: Timer ring and drink icon**

```tsx
// controller/src/tv/turf3d/ui/TimerRing.tsx
import { Text } from '@react-three/drei'
import { useFrame } from '@react-three/fiber'
import { useRef } from 'react'
import * as THREE from 'three'
import { Plate } from './Card'
import { timerFraction, timerLeft, type TimerSpec } from './hud'
import { FONT, GOLD, INK, PAPER, RED } from './theme'

interface TroikaText { text: string; sync: () => void }

/** A countdown pie in a ring: the wedge drains from the top as the phase timer runs out, with the seconds in the middle. */
export function TimerRing({ timer, r = 34, x = 0, y = 0 }: { timer: TimerSpec; r?: number; x?: number; y?: number }) {
  const wedge = useRef<THREE.Mesh>(null)
  const mat = useRef<THREE.MeshBasicMaterial>(null)
  const secs = useRef<TroikaText | null>(null)
  const last = useRef({ step: -1, s: -1 })
  const spec = useRef(timer)
  spec.current = timer
  useFrame(() => {
    const now = Date.now(), t = spec.current
    const frac = timerFraction(t, now), s = Math.ceil(timerLeft(t, now) / 1000)
    const step = Math.round(frac * 90)
    if (step !== last.current.step && wedge.current) {
      last.current.step = step
      wedge.current.geometry.dispose()
      wedge.current.geometry = new THREE.CircleGeometry(r - 7, 48, Math.PI / 2, Math.PI * 2 * Math.max(frac, 0.0001))
      mat.current?.color.set(frac < 0.25 ? RED : GOLD)
    }
    if (s !== last.current.s && secs.current) { last.current.s = s; secs.current.text = String(s); secs.current.sync() }
  })
  return (
    <group position={[x, y, 0]}>
      <Plate w={r * 2 + 6} h={r * 2 + 6} r={r + 3} color={INK} z={1} />
      <Plate w={r * 2} h={r * 2} r={r} color={PAPER} z={2} />
      <mesh ref={wedge} position={[0, 0, 3]}>
        <circleGeometry args={[r - 7, 48, Math.PI / 2, Math.PI * 2]} />
        <meshBasicMaterial ref={mat} color={GOLD} toneMapped={false} />
      </mesh>
      <Text ref={secs as never} font={FONT.display} fontSize={28} color={INK} anchorX="center" anchorY="middle" position={[0, -1, 4]} outlineWidth={2} outlineColor={PAPER}>{String(Math.ceil(timerLeft(timer, Date.now()) / 1000))}</Text>
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/DrinkIcon.tsx
import { Drink } from '../Drinks'
import { drinkFor } from '../pieces'

/**
 * One of the six drinks, standing on its coaster, tilted to be seen from above like the ones on the board. [size] is its
 * height in reference pixels (a drink is about one unit tall). Lit by the Dais's own light, so it looks the same in every shot.
 */
export function DrinkIcon({ piece, color, size = 70, x = 0, y = 0 }: { piece?: string; color: string; size?: number; x?: number; y?: number }) {
  return (
    <group position={[x, y, 30]} scale={size} rotation-x={0.75}>
      <Drink kind={drinkFor(piece)} color={color} />
    </group>
  )
}
```

- [ ] **Step 7: Type-check and build**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; `✓ built in`. If tsc rejects `ref={secs as never}`, cast differently (`as unknown as React.Ref<never>`); `vite/client` types already declare `*?url`, so the font imports need no extra declaration.

- [ ] **Step 8: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/ui
git commit -m "feat(turf3d): the 3D UI kit primitives: paper card, text, pill, button, timer ring, drink icon, bundled fonts"
```

---

### Task 5: The frame, the Dais, and the first panel on screen

**Files:**
- Create: `controller/src/tv/turf3d/ui/Frame.tsx`, `Dais.tsx`, `PanelHost.tsx`, `panels/RollPanel.tsx`
- Modify: `controller/src/tv/turf3d/TurfScene.tsx`, `controller/src/tv/turf3d/TurfStage3D.tsx`, `controller/src/tv/TurfStage.tsx`, `controller/src/tv/turf.css`, `controller/src/tv/TurfGallery.tsx`

**Interfaces:**
- Consumes: everything from Tasks 1 to 4; `Craft` from `../useChoreography`.
- Produces: `Frame({ tv, hud, size, onReady, children })`, `Dais({ panelKey, visible, children })` where `children: (key: string) => ReactNode`, `PanelHost({ tv, hud, visible, onReady })`, `RollPanel({ tv, hud, quiet? })` (also used for `move`), and `TurfStage3D` gains a `hud: HudIn` prop.

- [ ] **Step 1: The frame**

```tsx
// controller/src/tv/turf3d/ui/Frame.tsx
import type { ReactNode } from 'react'
import type { TurfTv } from '../../types'
import { Card, Plate } from './Card'
import { DrinkIcon } from './DrinkIcon'
import type { Hud } from './hud'
import { Label } from './Label'
import { CARD, FOOT_H, HEAD_H, bodyOf, type CardSize } from './sizing'
import { GOLD, INK, LED_BG, MUTED, PAPER, RED } from './theme'
import { TimerRing } from './TimerRing'

/**
 * The one card every ported panel sits on: a header (who is up, the game clock, the decision timer), the panel itself
 * in the body, and the last few ticker lines in the footer. [onReady] fires when the first text has been laid out,
 * which is when the DOM well can safely get out of the way.
 */
export function Frame({ tv, hud, size, onReady, children }: { tv: TurfTv; hud: Hud; size: CardSize; onReady?: () => void; children?: ReactNode }) {
  void tv
  const { w, h } = CARD[size]
  const body = bodyOf(size)
  const headY = h / 2 - HEAD_H / 2
  const footY = -h / 2 + FOOT_H / 2
  const left = -w / 2 + 34
  const right = w / 2 - 34
  const clockTone = hud.clock.tone === 'red' ? RED : hud.clock.tone === 'gold' ? GOLD : PAPER
  const ledW = hud.clock.tone === 'plain' ? 0 : 148
  const timerW = hud.timer ? 84 : 0
  const ledX = right - timerW - ledW / 2 - 4
  return (
    <Card w={w} h={h}>
      {/* header */}
      <group position={[0, headY, 0]}>
        {hud.showTurn ? (
          <>
            <Plate w={64} h={64} r={32} color={INK} x={left + 26} z={1} />
            <Plate w={56} h={56} r={28} color={hud.color} x={left + 26} z={2} />
            <DrinkIcon piece={hud.piece} color={hud.color} size={40} x={left + 26} y={-6} />
            <Label px={38} kind="title" font="display" anchorX="left" align="left" x={left + 74} maxWidth={w - 74 - 34 - 160 - timerW} onSync={onReady}>{hud.name.toUpperCase()}</Label>
          </>
        ) : (
          <Label px={44} kind="title" font="display" anchorX="left" align="left" x={left} onSync={onReady}>HOME TURF</Label>
        )}
        {ledW > 0 ? (
          <>
            <Plate w={ledW} h={52} r={9} color={INK} x={ledX} z={1} />
            <Plate w={ledW - 6} h={46} r={7} color={LED_BG} x={ledX} z={2} />
            <Label px={34} kind="body" font="led" color={clockTone} x={ledX} z={3}>{hud.clock.text}</Label>
          </>
        ) : (
          <Label px={22} kind="label" color={MUTED} anchorX="right" align="right" x={right - timerW} font="bodyBold">{hud.clock.text}</Label>
        )}
        {hud.timer && <TimerRing timer={hud.timer} x={right - 34} />}
      </group>
      <Plate w={w - 40} h={3} r={1.5} color={INK} y={h / 2 - HEAD_H} z={1} />
      {/* body */}
      <group position={[0, body.cy, 0]}>{children}</group>
      {/* footer */}
      {hud.ticker.length > 0 && (
        <>
          <Plate w={w - 40} h={3} r={1.5} color={INK} y={-h / 2 + FOOT_H} z={1} />
          <group position={[0, footY, 0]}>
            {hud.ticker.map((line, k, all) => (
              <Label key={k} px={22} kind="label" color={k === all.length - 1 ? INK : MUTED} font={k === all.length - 1 ? 'bodyBold' : 'body'} anchorX="left" align="left" x={left - 6} y={(all.length - 1) * 12 - k * 25} maxWidth={w - 60}>{line}</Label>
            ))}
          </group>
        </>
      )}
    </Card>
  )
}
```

- [ ] **Step 2: The Dais**

```tsx
// controller/src/tv/turf3d/ui/Dais.tsx
import { useFrame, useThree } from '@react-three/fiber'
import { Suspense, useMemo, useRef, useState, type ReactNode } from 'react'
import * as THREE from 'three'
import { REF_H, stepToward, worldPerPx } from './sizing'

/** How far in front of the camera the card hangs. Its size on screen is set by REF_H and the field of view, not by this. */
const DIST = 9

/**
 * Hangs whatever it is given in front of the camera, always at the same spot and size on screen, whatever the shot.
 * When [panelKey] changes the old card drops away, then the new one lifts in; it also drops away while [visible] is
 * false (any shot but the wide one, so it never covers a walking piece). Children are laid out in reference pixels
 * (1080 tall, centred, y up).
 */
export function Dais({ panelKey, visible, children }: { panelKey: string | null; visible: boolean; children: (key: string) => ReactNode }) {
  const { camera } = useThree()
  const group = useRef<THREE.Group>(null)
  const inner = useRef<THREE.Group>(null)
  const grow = useRef(0)
  const [shown, setShown] = useState<string | null>(panelKey)
  const tmp = useMemo(() => new THREE.Vector3(), [])

  useFrame((_, dt) => {
    const g = group.current, i = inner.current
    if (!g || !i) return
    const cam = camera as THREE.PerspectiveCamera
    const want = visible && shown != null && panelKey === shown ? 1 : 0
    grow.current = stepToward(grow.current, want, Math.min(dt, 0.1))
    if (panelKey !== shown && grow.current < 0.02) setShown(panelKey) // the old card has dropped away: swap
    g.visible = grow.current > 0.01
    g.quaternion.copy(cam.quaternion)
    g.position.copy(cam.position).add(tmp.set(0, 0, -DIST).applyQuaternion(cam.quaternion))
    g.scale.setScalar(worldPerPx(cam.fov, DIST, REF_H) * grow.current)
    i.position.y = -(1 - grow.current) * 90
  })

  return (
    <group ref={group} visible={false}>
      <pointLight position={[0, 140, 420]} intensity={14} distance={6} decay={2} />
      <group ref={inner} rotation-z={-0.018}>
        <Suspense fallback={null}>{shown ? children(shown) : null}</Suspense>
      </group>
    </group>
  )
}
```

- [ ] **Step 3: The roll panel and the host**

```tsx
// controller/src/tv/turf3d/ui/panels/RollPanel.tsx
import type { TurfTv } from '../../../types'
import { Plate } from '../Card'
import type { Hud } from '../hud'
import { Label } from '../Label'
import { rollCall } from '../panels'
import { bodyOf, fitFont } from '../sizing'
import { GOLD, GREEN, INK, RED } from '../theme'

/** The roll turn, and (with nothing to say) the move phase, whose dice are on the table. */
export function RollPanel({ tv, hud, quiet }: { tv: TurfTv; hud: Hud; quiet?: boolean }) {
  if (quiet) return null
  const body = bodyOf('std')
  const call = rollCall(tv.doubles, hud.seatName)
  return (
    <group>
      {[[-46, RED], [0, GOLD], [46, GREEN]].map(([x, c], k) => (
        <group key={k} position={[x as number, 62, 0]}>
          <Plate w={38} h={38} r={6} color={INK} z={1} />
          <Plate w={30} h={30} r={4} color={c as string} z={2} />
        </group>
      ))}
      <Label px={fitFont(call.length, body.w, 96, 72)} kind="hero" y={-14} maxWidth={body.w}>{call}</Label>
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/PanelHost.tsx
import type { ReactNode } from 'react'
import type { TurfTv } from '../../types'
import { Dais } from './Dais'
import { Frame } from './Frame'
import type { Hud } from './hud'
import { RollPanel } from './panels/RollPanel'
import { panelFor, panelSize, type PanelName } from './panels'

function panelBody(name: PanelName, tv: TurfTv, hud: Hud): ReactNode {
  switch (name) {
    case 'roll': return <RollPanel tv={tv} hud={hud} />
    case 'move': return <RollPanel tv={tv} hud={hud} quiet />
    default: return null
  }
}

/** The 3D card for the current phase, or nothing (the DOM well shows) when the phase is not ported. */
export function PanelHost({ tv, hud, visible, onReady }: { tv: TurfTv; hud: Hud; visible: boolean; onReady: () => void }) {
  return (
    <Dais panelKey={panelFor(tv.phase)} visible={visible}>
      {(key) => (
        <Frame tv={tv} hud={hud} size={panelSize(key as PanelName)} onReady={onReady}>
          {panelBody(key as PanelName, tv, hud)}
        </Frame>
      )}
    </Dais>
  )
}
```

- [ ] **Step 4: Put it in the scene**

In `controller/src/tv/turf3d/TurfScene.tsx`:

```tsx
import type { Hud } from './ui/hud'
import { PanelHost } from './ui/PanelHost'
```
Change the signature to `export function TurfScene({ tv, craft, hud, onPanelReady }: { tv: TurfTv; craft: Craft; hud: Hud; onPanelReady: () => void })` and add, right after `<CameraRig ... />` (so its frame runs after the camera has moved):

```tsx
      <PanelHost tv={tv} hud={hud} visible={craft.shot === 'wide'} onReady={onPanelReady} />
```

In `controller/src/tv/turf3d/TurfStage3D.tsx`: add imports `import { buildHud, type HudIn } from './ui/hud'` and `import { panelFor } from './ui/panels'`; extend the props to `{ g, hud, children, onLost }: { g: TurfTv; hud: HudIn; children: ReactNode; onLost: () => void }`; add state `const [panelReady, setPanelReady] = useState(false)` and a clock tick `const [now, setNow] = useState(() => Date.now())` with `useEffect(() => { const id = setInterval(() => setNow(Date.now()), 500); return () => clearInterval(id) }, [])`; compute `const hudNow = useMemo(() => buildHud(g, hud, now), [g, hud, now])`; pass `hud={hudNow} onPanelReady={() => setPanelReady(true)}` to `<TurfScene>`; and change the well wrapper to:

```tsx
      <div className={`turf-well3d ${craft.shot === 'wide' ? '' : 'dim'} ${panelReady && fontsReady && panelFor(g.phase) ? 'hidden3d' : ''}`}>{children}</div>
```

In `controller/src/tv/turf.css`, after the `.turf-well3d.dim` rule add `.turf-well3d.hidden3d { display: none; }`.

In `controller/src/tv/TurfStage.tsx`, in `Turf`: compute

```tsx
  const timerScale = useTimerScale()
  const curTok = g.tokens[g.turn]
  const seatName = curTok?.seat ? people.get(curTok.seat)?.name ?? curTok.name : curTok?.name ?? ''
```
and pass `hud={{ clock, paused: !!stage.paused, timerScale, seatName }}` to `<TurfStage3D ...>` (`useTimerScale` is already imported there).

- [ ] **Step 5: Add the gallery beat for a doubles roll**

In `controller/src/tv/TurfGallery.tsx` add inside `BEATS`:

```tsx
  'doubles-roll': { g: { ...base, doubles: 1 } },
```

- [ ] **Step 6: Type-check, build, and look at it**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; all tests pass; `✓ built in`.

Start the preview server if it is not running (`npx vite preview --port 4173 --strictPort --host 127.0.0.1` in the background), then capture with a scratch script (outside the repo, in the scratchpad directory):

```bash
SP=/private/tmp/claude-501/-Users-jjahn-HoopDreams/6c7b9067-a001-4e4c-ba79-093b85603b3a/scratchpad
cat > $SP/ui-look.cjs <<'EOF'
const { createRequire } = require('module')
const { chromium } = createRequire('/Users/jjahn/HoopDreams/controller/package.json')('@playwright/test')
const shots = JSON.parse(process.argv[3])
;(async () => {
  const b = await chromium.launch({ args: ['--use-angle=metal', '--enable-gpu', '--ignore-gpu-blocklist'] })
  for (const [beat, ms] of shots) {
    const p = await b.newPage({ viewport: { width: 1920, height: 1080 } })
    const errs = [], off = []
    p.on('pageerror', (e) => errs.push(String(e))); p.on('console', (m) => m.type() === 'error' && errs.push(m.text()))
    p.on('request', (r) => { if (!r.url().startsWith('http://127.0.0.1:4173') && !r.url().startsWith('data:') && !r.url().startsWith('blob:')) off.push(r.url()) })
    await p.goto(`http://127.0.0.1:4173/tv?gallery=turf&beat=${beat}`); await p.waitForTimeout(ms)
    await p.screenshot({ path: `${process.argv[2]}/${beat}-${ms}.png` }); console.log(beat, ms, errs.length ? errs.slice(0, 3) : 'ok', off.length ? 'OFF-ORIGIN ' + off.slice(0, 3) : 'same-origin')
    await p.close()
  }
  await b.close()
})()
EOF
mkdir -p $SP/ui-shots && cd /Users/jjahn/HoopDreams/controller && node $SP/ui-look.cjs $SP/ui-shots '[["roll",2500],["doubles-roll",2500]]'
```
Look at both images. Expected: a cream paper card with a thick ink outline and a hard shadow sits in the middle of the board; the header shows a colour disc with a drink, the player's name in Anton, a gold seven-segment clock on a dark plate and the timer ring; three little blocks above "AMANDA ROLLS" (or "DOUBLES! ROLL AGAIN" on two lines) in the body; the last three ticker lines in the footer. The DOM well is gone. The console line reads `ok` and `same-origin`.

Check specifically: (a) the postprocessing does not blur or tint the card badly (if it does, note it and lower the TiltShift blur only if the card is unreadable); (b) the seven-segment clock reads as digits; (c) the drink on the header disc is recognisable and lit (if it is black or washed out, tune the `pointLight` `intensity` in `Dais.tsx` between 6 and 40); (d) no text is clipped by the card edge. Fix what is wrong and retake before committing.

- [ ] **Step 7: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/ui controller/src/tv/turf3d/TurfScene.tsx controller/src/tv/turf3d/TurfStage3D.tsx controller/src/tv/TurfStage.tsx controller/src/tv/turf.css controller/src/tv/TurfGallery.tsx
git commit -m "feat(turf3d): the camera-locked paper card with header, footer and the roll panel"
```

---

### Task 6: Manage, jail and choose

**Files:**
- Create: `controller/src/tv/turf3d/ui/panels/ManagePanel.tsx`, `JailPanel.tsx`, `ChoosePanel.tsx`
- Modify: `controller/src/tv/turf3d/ui/PanelHost.tsx`, `controller/src/tv/TurfGallery.tsx`

**Interfaces:**
- Consumes: `bankLine, chooseCall` from `../panels`; kit primitives.
- Produces: `ManagePanel({ tv })`, `JailPanel()`, `ChoosePanel({ tv, hud })`; gallery beat `choose`.

- [ ] **Step 1: The three panels**

```tsx
// controller/src/tv/turf3d/ui/panels/ManagePanel.tsx
import type { TurfTv } from '../../../types'
import { Label } from '../Label'
import { bankLine } from '../panels'
import { bodyOf } from '../sizing'
import { MUTED } from '../theme'

export function ManagePanel({ tv }: { tv: TurfTv }) {
  const body = bodyOf('std')
  return (
    <group>
      <Label px={72} kind="hero" y={28} maxWidth={body.w}>BUILD, TRADE, OR END</Label>
      <Label px={28} kind="body" color={MUTED} y={-78} font="bodyBold">{bankLine(tv)}</Label>
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/panels/JailPanel.tsx
import { Button } from '../Button'
import { Label } from '../Label'
import { bodyOf } from '../sizing'
import { GOLD, PAPER_DARK } from '../theme'

export function JailPanel() {
  const body = bodyOf('std')
  return (
    <group>
      <Label px={84} kind="hero" y={44} maxWidth={body.w}>IN TIMEOUT</Label>
      <Button w={150} label="PAY $50" x={-190} y={-52} />
      <Button w={170} label="USE A CARD" x={0} y={-52} fill={PAPER_DARK} />
      <Button w={200} label="ROLL DOUBLES" x={195} y={-52} fill={GOLD} />
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/panels/ChoosePanel.tsx
import type { TurfTv } from '../../../types'
import type { Hud } from '../hud'
import { Label } from '../Label'
import { chooseCall } from '../panels'
import { bodyOf, fitFont } from '../sizing'
import { MUTED } from '../theme'

export function ChoosePanel({ tv, hud }: { tv: TurfTv; hud: Hud }) {
  const body = bodyOf('std')
  const call = chooseCall(tv.choose)
  return (
    <group>
      <Label px={fitFont(call.length, body.w, 84, 72)} kind="hero" y={34} maxWidth={body.w}>{call}</Label>
      <Label px={28} kind="body" color={MUTED} y={-80} font="bodyBold" maxWidth={body.w}>{`${hud.seatName} is choosing`}</Label>
    </group>
  )
}
```

- [ ] **Step 2: Route them**

In `controller/src/tv/turf3d/ui/PanelHost.tsx`, add the three imports and extend the switch:

```tsx
    case 'manage': return <ManagePanel tv={tv} />
    case 'jail': return <JailPanel />
    case 'choose': return <ChoosePanel tv={tv} hud={hud} />
```

- [ ] **Step 3: Add a gallery beat for choose**

In `controller/src/tv/TurfGallery.tsx` add inside `BEATS`:

```tsx
  choose: { g: { ...base, phase: 'choose', choose: 'bus', timed: true } },
```

- [ ] **Step 4: Type-check, build, and look**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror" && SP=/private/tmp/claude-501/-Users-jjahn-HoopDreams/6c7b9067-a001-4e4c-ba79-093b85603b3a/scratchpad && node $SP/ui-look.cjs $SP/ui-shots '[["manage",2500],["jail",2500],["choose",2500]]'`
Expected: no tsc output; tests pass; `✓ built in`; three `ok same-origin` lines. Look at each frame: "BUILD, TRADE, OR END" on two lines above the bank line; "IN TIMEOUT" over three chunky buttons that fit inside the card; "BUS! PICK A MOVE" with "Amanda is choosing". Nothing clipped; the timer ring counts down; fix and retake if not.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/ui controller/src/tv/TurfGallery.tsx
git commit -m "feat(turf3d): the manage, jail and choose panels in 3D"
```

---

### Task 7: Pieces and deal on the big card

**Files:**
- Create: `controller/src/tv/turf3d/ui/panels/PiecesPanel.tsx`, `DealPanel.tsx`
- Modify: `controller/src/tv/turf3d/ui/PanelHost.tsx`

**Interfaces:**
- Consumes: `PIECE_ORDER` from `../panels`; `DRINK_NAMES` from `../../drinkSpecs`; `drinkFor` from `../../pieces`; `DrinkIcon`, `Label`.
- Produces: `PiecesPanel({ tv })`, `DealPanel({ tv })`.

- [ ] **Step 1: The panels**

```tsx
// controller/src/tv/turf3d/ui/panels/PiecesPanel.tsx
import type { TurfTv } from '../../../types'
import { DRINK_NAMES } from '../../drinkSpecs'
import { drinkFor } from '../../pieces'
import { DrinkIcon } from '../DrinkIcon'
import { Label } from '../Label'
import { PIECE_ORDER } from '../panels'
import { MUTED } from '../theme'

const GHOST = '#cbbf9f'

/** Six drinks in a grid: free ones pale, taken ones in the owner's colour with the owner's name. */
export function PiecesPanel({ tv }: { tv: TurfTv }) {
  return (
    <group>
      <Label px={60} kind="title" y={158}>GRAB YOUR PIECE!</Label>
      {PIECE_ORDER.map((p, k) => {
        const owner = tv.tokens.find((t) => t.piece === p)
        const x = (k % 3 - 1) * 240, y = k < 3 ? 40 : -96
        return (
          <group key={p}>
            <DrinkIcon piece={p} color={owner ? owner.color : GHOST} size={82} x={x} y={y + 8} />
            <Label px={24} kind="label" y={y - 52} maxWidth={220} font="bodyBold" color={owner ? undefined : MUTED}>{owner ? owner.name : DRINK_NAMES[drinkFor(p)]}</Label>
          </group>
        )
      })}
      <Label px={28} kind="body" y={-172} color={MUTED} font="bodyBold">First tap on your phone wins</Label>
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/panels/DealPanel.tsx
import type { TurfTv } from '../../../types'
import { DrinkIcon } from '../DrinkIcon'
import { Label } from '../Label'
import { MUTED } from '../theme'

/** Each player's drink, name and the starter places dealt to them (two columns of up to three). */
export function DealPanel({ tv }: { tv: TurfTv }) {
  const dealt = tv.tokens.map((_, i) => tv.beats.filter((b) => b.kind === 'deal' && b.token === i).flatMap((b) => b.tokens))
  return (
    <group>
      <Label px={60} kind="title" y={166}>STARTER PLACES</Label>
      {tv.tokens.slice(0, 6).map((t, i) => {
        const x = (i % 2 === 0 ? -1 : 1) * 190, y = 78 - Math.floor(i / 2) * 92
        const places = dealt[i].map((s) => tv.board[s]?.name).filter((n): n is string => !!n).join(', ')
        return (
          <group key={i}>
            <DrinkIcon piece={t.piece} color={t.color} size={58} x={x - 150} y={y - 10} />
            <Label px={30} kind="body" font="bodyBold" anchorX="left" align="left" x={x - 112} y={y + 16} maxWidth={250}>{t.name}</Label>
            <Label px={22} kind="label" anchorX="left" align="left" x={x - 112} y={y - 18} maxWidth={250} color={MUTED}>{places || '...'}</Label>
          </group>
        )
      })}
      <Label px={28} kind="body" y={-176} color={MUTED} font="bodyBold">Paid for out of everyone's $1,500</Label>
    </group>
  )
}
```

- [ ] **Step 2: Route them**

In `controller/src/tv/turf3d/ui/PanelHost.tsx`, add the two imports and extend the switch:

```tsx
    case 'pieces': return <PiecesPanel tv={tv} />
    case 'deal': return <DealPanel tv={tv} />
```
(`PanelName` is now fully covered, so TypeScript may flag the `default: return null` as unreachable: delete it if `tsc` says so.)

- [ ] **Step 3: Type-check, build, and look**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror" && SP=/private/tmp/claude-501/-Users-jjahn-HoopDreams/6c7b9067-a001-4e4c-ba79-093b85603b3a/scratchpad && node $SP/ui-look.cjs $SP/ui-shots '[["pieces",2500],["deal",2500]]'`
Expected: no tsc output; tests pass; build OK; two `ok same-origin` lines. Look: the bigger card with "GRAB YOUR PIECE!" and six drinks (three coloured with owner names, three pale with drink names) and the hint; "STARTER PLACES" with six player rows in two columns, drinks, names and place lists that do not spill out of their cell, and the hint at the bottom. Tune the drink `size`, `y` values and `maxWidth`s until nothing overlaps or clips; six players is the worst case (the fixtures already have six).

- [ ] **Step 4: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/ui
git commit -m "feat(turf3d): the pieces and deal panels on the big card"
```

---

### Task 8: Screenshots, checks, docs

**Files:**
- Modify: `controller/scripts/turf3d-shots.mjs`, `README.md`

**Interfaces:**
- Consumes: gallery beats `roll`, `doubles-roll`, `manage`, `jail`, `choose`, `pieces`, `deal`, plus the earlier ones.

- [ ] **Step 1: Capture the panels and flag any off-origin request**

In `controller/scripts/turf3d-shots.mjs`, inside `open`, after the `page.errors = []` line add:

```js
  page.offOrigin = []
  page.on('request', (r) => { const u = r.url(); if (!u.startsWith(base) && !u.startsWith('data:') && !u.startsWith('blob:')) page.offOrigin.push(u) })
```
extend the `shots` list with `['doubles-roll', 2500], ['manage', 2500], ['jail', 2500], ['choose', 2500], ['pieces', 2500], ['deal', 2500]` (keep the earlier entries), and change the per-shot console line to:

```js
  console.log(`${beat}@${ms}`.padEnd(20), page.errors.length ? `ERRORS ${page.errors.join(' | ')}` : 'ok', page.offOrigin.length ? `OFF-ORIGIN ${page.offOrigin.join(' ')}` : '')
```
The existing `roll@3500` and `lineup@3500` shots now show the 3D card.

- [ ] **Step 2: Run it and read every frame**

Run: `cd /Users/jjahn/HoopDreams/controller && uptime && npx vite build 2>&1 | grep -E "built|rror" && OUT=/private/tmp/claude-501/-Users-jjahn-HoopDreams/6c7b9067-a001-4e4c-ba79-093b85603b3a/scratchpad/final-ui node scripts/turf3d-shots.mjs 2>&1 | tail -30`
Expected: `ok` for every shot with no `OFF-ORIGIN`, and the three `fps` lines with no `ERRORS`. `balanced` must be 50 fps or better with `p95ms` 25 or under (Plan 2 measured 59 and 18). Read the panel frames (`roll`, `doubles-roll`, `manage`, `jail`, `choose`, `pieces`, `deal`) for legibility. If the load average is above 5, wait and re-run before judging fps. If the frame rate drops below 50, report the numbers and the frames rather than shipping quietly.

Also confirm the card is hidden when the camera leaves `wide`: the `diced-move@2300` and `doubles-move@3400` frames must show the dice and no card.

- [ ] **Step 3: Document it**

In `README.md`, in the **In 3D** bullet, add after the sentence about landing moments: "The roll, move, manage, Timeout, choose, piece-picking and starter-place panels are paper cards built in 3D and locked to the camera (they drop away for close shots); the other panels (buy, auction, card, debt, trade, tally, team-up) still use the flat well, which is also the 2D fallback."

- [ ] **Step 4: Run everything**

Run:
```bash
cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Test Files|Tests " && npx vite build 2>&1 | grep -E "built|rror"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd /Users/jjahn/HoopDreams/tv && ./gradlew :engine:test :server:test 2>&1 | grep -E "BUILD|FAILED"
cd /Users/jjahn/HoopDreams/controller && npx playwright test e2e/turf.spec.ts 2>&1 | tail -6
```
Expected: no tsc output; every unit test passes; the build succeeds; Gradle BUILD SUCCESSFUL (the engine is untouched); the Playwright Home Turf spec passes.

- [ ] **Step 5: Clean up and commit**

```bash
cd /Users/jjahn/HoopDreams
rm -rf controller/turf3d-shots
git add controller/scripts/turf3d-shots.mjs README.md
git commit -m "feat(turf3d): screenshot every 3D panel, flag off-origin requests, and document the card"
```

---

## Deferred (sub-projects 2 to 4)

- Buy deed, card, auction call, the banner and the flash callouts as 3D (sub-project 2), with `Money` and its count-up.
- Trade, debt, tally, the auction bid view and team-up (avatar faces) (sub-project 3).
- Player rails as trays or coasters, the ticker as its own object and the game clock at the table edge (sub-project 4).
- The speed die still appears only in the DOM well; the team-up panel keeps the DOM well.
