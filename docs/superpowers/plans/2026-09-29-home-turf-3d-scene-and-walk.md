# Home Turf 3D, Plan 1: the tabletop scene and the live walk

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Home Turf's TV board becomes a lit 3D tabletop with six drink pieces that hop space to space along the engine's real moves, with a slowed, camera-driven final approach, a Theatre/Quick lobby setting, and a 2D fallback.

**Architecture:** A lazy-loaded `controller/src/tv/turf3d/` module. Pure modules (`layout`, `boardTexture` geometry, `timeline`, `camera`, `pieces`, `drinkSpecs`, `quality`, `webgl`) are unit-tested without WebGL. A `useChoreography` hook turns new engine beats into timed cues and outputs a small "craft" state; a react-three-fiber scene renders that state. The engine keeps its instant, authoritative moves and only lengthens its MOVE-phase dwell through one shared formula (Kotlin and TypeScript mirror it, with the same expected-value table in both tests).

**Tech Stack:** React 19, react-three-fiber 9, drei, @react-three/postprocessing, three, vitest, Playwright; Kotlin engine (kotlinx.serialization, Gradle).

**Spec:** `docs/superpowers/specs/2026-09-29-home-turf-3d-design.md`. This plan implements its milestones 1 and 2. Plan 2 (physics dice with engine-result reconciliation, landing moments) and Plan 3 (piece-id rename, polish, docs) follow. Work happens on branch `turf-3d-spike`.

## Global Constraints

Copied from the spec; every task's requirements include these.

- Labels are generic. Deny-list (case-insensitive, must never appear on a drink or in a piece name): Smirnoff, Absolut, Jinro, Chamisul, Budweiser, Heineken, Solo.
- Fonts: `Anton` for board names and drink labels, `Rammetto One` for the title sticker. No `system-ui` bold. Alfa Slab One is rejected.
- Every name on the board reads upright from the couch.
- Nothing loads from a network at runtime: no CDN, no downloaded HDRI, no downloaded models. Reflections come from local Lightformers.
- Nothing 3D loads for other games: the module is behind `React.lazy`.
- WebGL2 missing, context lost, or `?board=2d`: fall back to the current 2D `TurfBoard` without error.
- Tilt-shift stays at 0.03 or lower (heavier blur made side tiles unreadable).
- Colour + shape + text: an owner is shown by a flag shape and a tint strip, never colour alone.
- Pacing (one formula, Kotlin and TypeScript): `DICE_MS = 2600`, hops of `230` ms then `320`, `460`, `820` for the last three, `LAND_PAD_MS = 900`. Quick keeps today's `hops * 260 + 1400`. The dice theatre applies only to moves that follow a roll.
- Performance target: 50 fps or better at 1080p on an Apple-silicon Mac, dpr capped at 2, a `Quality` setting (`high`, `balanced`, `low`).
- Piece ids do not change in this plan (`cup, pizza, sneaker, boombox, cone, duck`). The scene maps them to drinks. Plan 3 renames them.
- Commit messages carry no attribution lines.
- Gradle needs `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`; run it from `/Users/jjahn/HoopDreams/tv`.

## Review Focus

The inputs the spec implies but the happy path never exercises, most likely first. Each has a test in the task named.

1. **A move whose path is empty, or a `roll` beat with no `move` after it,** must not start any animation or hold the queue (engine returns before the dwell for an empty path). Task 6.
2. **A TV that connects mid-game (or reconnects)** must show pieces at their true positions and must not replay old beats. Task 9 (`initialSeen`, `snapFor`).
3. **Two moves arriving in one update** (for example a card move right after a roll move, or a Timeout snap after a walk to Go to Timeout) must play in order and never overlap. Task 6.
4. **A token with no piece yet, an unknown piece id, a bankrupt token, or six tokens stacked on Payday.** `drinkFor(undefined)` gives the cup, bankrupt tokens are hidden, and six crowd slots stay distinct and inside the tile. Tasks 2, 3 and 10.
5. **No WebGL2, a lost context, or Quick versus Theatre.** `hasWebGL2` and `wants3d` decide the board; the engine dwell for Quick stays exactly `hops * 260 + 1400`. Tasks 4 and 7.

---

## File structure

Create (all under `controller/src/tv/turf3d/` unless noted):

| File | Responsibility |
| --- | --- |
| `layout.ts` (+ `.test.ts`) | Board geometry in world units: `tileOf`, `spacePos`, `crowdSlot`, `crowdIndex` |
| `drinkSpecs.ts` (+ `.test.ts`) | Drink kinds, bottle and label config, display names (pure data) |
| `pieces.ts` (+ `.test.ts`) | Engine piece id to drink kind |
| `quality.ts` (+ `.test.ts`) | Quality levels, URL parsing, React context |
| `webgl.ts` (+ `.test.ts`) | WebGL2 detection and the 3D-or-2D decision |
| `boardTexture.ts` (+ `.test.ts`) | Tile rectangles in texture pixels, name fitting, and the canvas painter |
| `fonts.ts` | Imports the fontsource CSS and waits for the two faces |
| `camera.ts` (+ `.test.ts`) | Named camera shots as pure poses |
| `timeline.ts` (+ `.test.ts`) | Pacing formula and `planBeats` (beats to timed cues) |
| `useChoreography.ts` (+ `.test.ts`) | Runs the cues on timers and exposes the craft state |
| `Drinks.tsx` | The six drink models and the coaster (moved from the spike) |
| `scene/Lights.tsx`, `Table.tsx`, `Board3D.tsx`, `Houses.tsx`, `Flags.tsx`, `Pieces.tsx`, `Effects.tsx`, `CameraRig.tsx`, `Post.tsx` | Scene parts |
| `TurfScene.tsx` | Composes the scene parts |
| `TurfStage3D.tsx` | Canvas, overlays, skip key, font readiness, context-loss report |
| `controller/scripts/turf3d-shots.mjs` | Screenshot and frame-rate script |

Modify: `controller/src/tv/TurfStage.tsx`, `TurfGallery.tsx`, `types.ts`, `turf.css`, `Gallery.tsx`; `controller/src/protocol.ts`, `tv/TvPage.tsx`, `pages/Captain.tsx`; `tv/engine/.../PartyEngine.kt`, `games/turf/HomeTurf.kt`, `games/turf/TurfState.kt`, `TurfViews.kt`, the server fixture `controller/src/protocol/fixtures/server-messages.json`; `tv/engine/src/test/.../HomeTurfTest.kt`; `README.md`.

---

### Task 1: Snapshot the spike so later moves are tracked

**Files:**
- Commit only: `controller/package.json`, `controller/package-lock.json`, `controller/src/tv/Gallery.tsx`, `controller/src/tv/spike3d/`

**Interfaces:**
- Produces: a commit containing `src/tv/spike3d/Drinks.tsx` and `Spike3D.tsx`, so `git mv` in Task 10 preserves history.

- [ ] **Step 1: Confirm the branch and the working tree**

Run: `cd /Users/jjahn/HoopDreams && git branch --show-current && git status --short | grep -v brag`
Expected: branch `turf-3d-spike`; modified `controller/package.json`, `package-lock.json`, `src/tv/Gallery.tsx`; untracked `controller/src/tv/spike3d/` and the older overhaul plan file.

- [ ] **Step 2: Type-check and build the snapshot**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; `✓ built in`.

- [ ] **Step 3: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/package.json controller/package-lock.json controller/src/tv/Gallery.tsx controller/src/tv/spike3d
git commit -m "chore: snapshot the 3D spike and its dependencies as a reference for turf3d"
```

---

### Task 2: Board layout in world units

**Files:**
- Create: `controller/src/tv/turf3d/layout.ts`
- Test: `controller/src/tv/turf3d/layout.test.ts`

**Interfaces:**
- Produces:
  - `HALF, CORNER, EDGE_W` (numbers), `Side = 'bottom' | 'left' | 'top' | 'right'`
  - `interface Tile { cx: number; cz: number; ex: number; ez: number; corner: boolean; side: Side; inward: { x: number; z: number } }`
  - `sideOf(i: number): Side`, `tileOf(i: number): Tile`, `spacePos(i: number): { x: number; z: number }`
  - `crowdSlot(k: number, n: number): { dx: number; dz: number }`
  - `crowdIndex(shown: number[], alive: boolean[]): { rank: number; count: number }[]`

- [ ] **Step 1: Write the failing test**

```ts
// controller/src/tv/turf3d/layout.test.ts
import { describe, expect, it } from 'vitest'
import { CORNER, EDGE_W, HALF, crowdIndex, crowdSlot, sideOf, spacePos, tileOf } from './layout'

const all = Array.from({ length: 40 }, (_, i) => tileOf(i))

describe('board layout', () => {
  it('gives every space a distinct centre', () => {
    const keys = new Set(all.map((t) => `${t.cx.toFixed(3)},${t.cz.toFixed(3)}`))
    expect(keys.size).toBe(40)
  })

  it('tiles the ring exactly: four corners plus 36 edge tiles', () => {
    const area = all.reduce((sum, t) => sum + t.ex * t.ez, 0)
    const expected = (HALF * 2) ** 2 - (HALF * 2 - CORNER * 2) ** 2
    expect(area).toBeCloseTo(expected, 6)
    expect(EDGE_W * 9 + CORNER * 2).toBeCloseTo(HALF * 2, 9)
  })

  it('makes neighbouring spaces share an edge', () => {
    for (let i = 0; i < 40; i++) {
      const a = all[i], b = all[(i + 1) % 40]
      const gapX = Math.abs(a.cx - b.cx) - (a.ex + b.ex) / 2
      const gapZ = Math.abs(a.cz - b.cz) - (a.ez + b.ez) / 2
      expect(gapX).toBeLessThan(1e-9)
      expect(gapZ).toBeLessThan(1e-9)
      // On one axis they touch exactly (gap 0); on the other they overlap (gap below zero).
      expect(Math.max(gapX, gapZ)).toBeGreaterThan(-1e-9)
    }
  })

  it('puts the corners where the board says: Payday bottom right, then clockwise as seen from the couch', () => {
    expect(tileOf(0).cx).toBeGreaterThan(0); expect(tileOf(0).cz).toBeGreaterThan(0)
    expect(tileOf(10).cx).toBeLessThan(0); expect(tileOf(10).cz).toBeGreaterThan(0)
    expect(tileOf(20).cx).toBeLessThan(0); expect(tileOf(20).cz).toBeLessThan(0)
    expect(tileOf(30).cx).toBeGreaterThan(0); expect(tileOf(30).cz).toBeLessThan(0)
    expect([0, 9, 10, 19, 20, 29, 30, 39].map(sideOf)).toEqual(['bottom', 'bottom', 'left', 'left', 'top', 'top', 'right', 'right'])
  })

  it('points every edge tile\'s inward vector at the middle of the board', () => {
    for (const t of all.filter((x) => !x.corner)) {
      expect(-t.cx * t.inward.x + -t.cz * t.inward.z).toBeGreaterThan(0)
    }
  })

  it('wraps and gives spacePos the tile centre', () => {
    expect(spacePos(40)).toEqual(spacePos(0))
    expect(spacePos(5)).toEqual({ x: tileOf(5).cx, z: tileOf(5).cz })
  })
})

describe('crowds on one space', () => {
  it('keeps up to six pieces in distinct slots that stay inside the smallest tile', () => {
    for (let n = 1; n <= 6; n++) {
      const slots = Array.from({ length: n }, (_, k) => crowdSlot(k, n))
      for (let a = 0; a < n; a++) {
        expect(Math.abs(slots[a].dx)).toBeLessThanOrEqual(EDGE_W / 2)
        for (let b = a + 1; b < n; b++) expect(Math.hypot(slots[a].dx - slots[b].dx, slots[a].dz - slots[b].dz)).toBeGreaterThanOrEqual(0.3)
      }
    }
    expect(crowdSlot(0, 1)).toEqual({ dx: 0, dz: 0 })
  })

  it('ranks pieces on the same space and ignores the bankrupt', () => {
    const idx = crowdIndex([0, 0, 0, 5, 0, 5], [true, true, true, true, false, true])
    expect(idx.map((x) => x.count)).toEqual([3, 3, 3, 2, 1, 2])
    expect(idx.slice(0, 3).map((x) => x.rank)).toEqual([0, 1, 2])
    expect([idx[3].rank, idx[5].rank]).toEqual([0, 1])
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/layout.test.ts`
Expected: FAIL, `Failed to resolve import "./layout"`.

- [ ] **Step 3: Write the implementation**

```ts
// controller/src/tv/turf3d/layout.ts
/** Home Turf's board in world units: a 12 x 12 square, 1.8 corners, nine 0.933-wide spaces per side, Payday bottom right. */
export const HALF = 6
export const CORNER = 1.8
export const EDGE_W = (HALF * 2 - CORNER * 2) / 9
const CENTRE = HALF - CORNER / 2

export type Side = 'bottom' | 'left' | 'top' | 'right'

export interface Tile {
  /** Centre in world x and z (y is up). */
  cx: number; cz: number
  /** Full extent along x and along z. */
  ex: number; ez: number
  corner: boolean
  side: Side
  /** Unit vector from this tile toward the middle of the board (zero for corners). */
  inward: { x: number; z: number }
}

const wrap = (i: number) => ((i % 40) + 40) % 40

export function sideOf(i: number): Side {
  const s = wrap(i)
  return s < 10 ? 'bottom' : s < 20 ? 'left' : s < 30 ? 'top' : 'right'
}

export function tileOf(i: number): Tile {
  const s = wrap(i)
  const side = sideOf(s)
  const corner = s % 10 === 0
  const run = (k: number) => HALF - CORNER - (k - 0.5) * EDGE_W
  let cx: number, cz: number
  if (s === 0) { cx = CENTRE; cz = CENTRE }
  else if (s < 10) { cx = run(s); cz = CENTRE }
  else if (s === 10) { cx = -CENTRE; cz = CENTRE }
  else if (s < 20) { cx = -CENTRE; cz = run(s - 10) }
  else if (s === 20) { cx = -CENTRE; cz = -CENTRE }
  else if (s < 30) { cx = -HALF + CORNER + (s - 20 - 0.5) * EDGE_W; cz = -CENTRE }
  else if (s === 30) { cx = CENTRE; cz = -CENTRE }
  else { cx = CENTRE; cz = -HALF + CORNER + (s - 30 - 0.5) * EDGE_W }
  const horizontal = side === 'bottom' || side === 'top'
  const ex = corner ? CORNER : horizontal ? EDGE_W : CORNER
  const ez = corner ? CORNER : horizontal ? CORNER : EDGE_W
  const inward = corner ? { x: 0, z: 0 } : side === 'bottom' ? { x: 0, z: -1 } : side === 'top' ? { x: 0, z: 1 } : side === 'left' ? { x: 1, z: 0 } : { x: -1, z: 0 }
  return { cx, cz, ex, ez, corner, side, inward }
}

export const spacePos = (i: number): { x: number; z: number } => { const t = tileOf(i); return { x: t.cx, z: t.cz } }

/** Where piece [k] of [n] on one space stands, relative to the space's centre, so a crowd never stacks into one spot. */
export function crowdSlot(k: number, n: number): { dx: number; dz: number } {
  if (n <= 1) return { dx: 0, dz: 0 }
  const cols = n <= 2 ? 2 : 3
  const rows = Math.ceil(n / cols)
  const col = k % cols, row = Math.floor(k / cols)
  return { dx: (col - (cols - 1) / 2) * 0.34, dz: (row - (rows - 1) / 2) * 0.42 }
}

/** For each token, its rank among the living tokens shown on the same space and how many share it. */
export function crowdIndex(shown: number[], alive: boolean[]): { rank: number; count: number }[] {
  const groups = new Map<number, number[]>()
  shown.forEach((space, k) => { if (alive[k]) groups.set(space, [...(groups.get(space) ?? []), k]) })
  return shown.map((space, k) => {
    const group = groups.get(space) ?? [k]
    return { rank: Math.max(0, group.indexOf(k)), count: group.length }
  })
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/layout.test.ts`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/layout.ts controller/src/tv/turf3d/layout.test.ts
git commit -m "feat(turf3d): board layout in world units with crowd slots"
```

---

### Task 3: Drink specs, piece mapping and the brand deny-list

**Files:**
- Create: `controller/src/tv/turf3d/drinkSpecs.ts`, `controller/src/tv/turf3d/pieces.ts`
- Test: `controller/src/tv/turf3d/drinkSpecs.test.ts`, `controller/src/tv/turf3d/pieces.test.ts`

**Interfaces:**
- Produces:
  - `DRINK_KINDS`, `type DrinkKind = 'soju' | 'vodka' | 'beer' | 'can' | 'shot' | 'cup'`, `type BottleKind = 'soju' | 'vodka' | 'beer'`
  - `interface BottleLabel { y0: number; y1: number; bg: string; accent: string; text: string; sub: string; ink: string }`
  - `interface BottleCfg { r: number; body: number; neck: number; shoulder: number; top: number; glass: string; att: string; label: BottleLabel; cap: { color: string; metal: boolean } }`
  - `BOTTLES: Record<BottleKind, BottleCfg>`, `CAN_LABEL: { top: string; sub: string }`, `DRINK_NAMES: Record<DrinkKind, string>`, `labelStrings(): string[]`
  - `drinkFor(piece?: string): DrinkKind`

- [ ] **Step 1: Write the failing tests**

```ts
// controller/src/tv/turf3d/drinkSpecs.test.ts
import { describe, expect, it } from 'vitest'
import { BOTTLES, CAN_LABEL, DRINK_KINDS, DRINK_NAMES, labelStrings } from './drinkSpecs'

const DENY = ['smirnoff', 'absolut', 'jinro', 'chamisul', 'budweiser', 'heineken', 'solo']

describe('drink specs', () => {
  it('has six drinks, each with a name', () => {
    expect([...DRINK_KINDS]).toEqual(['soju', 'vodka', 'beer', 'can', 'shot', 'cup'])
    for (const k of DRINK_KINDS) expect(DRINK_NAMES[k].length).toBeGreaterThan(2)
  })

  it('prints only generic words: nothing on the deny-list appears on any label or name', () => {
    const strings = labelStrings().map((s) => s.toLowerCase())
    expect(strings.length).toBeGreaterThanOrEqual(10)
    for (const s of strings) for (const bad of DENY) expect(s).not.toContain(bad)
  })

  it('keeps every bottle label on the straight part of its body', () => {
    for (const b of Object.values(BOTTLES)) {
      expect(b.label.y0).toBeGreaterThan(0)
      expect(b.label.y1).toBeLessThan(b.body)
      expect(b.label.y0).toBeLessThan(b.label.y1)
      expect(b.neck).toBeLessThan(b.r)
    }
    expect(CAN_LABEL.top).toBe('LAGER')
  })
})
```

```ts
// controller/src/tv/turf3d/pieces.test.ts
import { describe, expect, it } from 'vitest'
import { drinkFor } from './pieces'

describe('drinkFor', () => {
  it('maps the six engine piece ids to drinks', () => {
    expect(['cup', 'pizza', 'sneaker', 'boombox', 'cone', 'duck'].map(drinkFor)).toEqual(['cup', 'soju', 'vodka', 'beer', 'can', 'shot'])
  })
  it('already understands the drink ids Plan 3 will rename to', () => {
    expect(['soju', 'vodka', 'beer', 'can', 'shot', 'cup'].map(drinkFor)).toEqual(['soju', 'vodka', 'beer', 'can', 'shot', 'cup'])
  })
  it('falls back to the cup for a missing or unknown piece', () => {
    expect(drinkFor(undefined)).toBe('cup')
    expect(drinkFor('')).toBe('cup')
    expect(drinkFor('unicorn')).toBe('cup')
  })
})
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/drinkSpecs.test.ts src/tv/turf3d/pieces.test.ts`
Expected: FAIL, cannot resolve `./drinkSpecs` and `./pieces`.

- [ ] **Step 3: Write the implementations**

```ts
// controller/src/tv/turf3d/drinkSpecs.ts
/** Home Turf's pieces as drinks. Pure data so it can be tested without WebGL. Labels are generic on purpose: no brands. */
export const DRINK_KINDS = ['soju', 'vodka', 'beer', 'can', 'shot', 'cup'] as const
export type DrinkKind = (typeof DRINK_KINDS)[number]
export type BottleKind = 'soju' | 'vodka' | 'beer'

export interface BottleLabel { y0: number; y1: number; bg: string; accent: string; text: string; sub: string; ink: string }
export interface BottleCfg {
  r: number; body: number; neck: number; shoulder: number; top: number
  glass: string; att: string
  label: BottleLabel
  cap: { color: string; metal: boolean }
}

export const BOTTLES: Record<BottleKind, BottleCfg> = {
  soju: { r: 0.27, body: 0.5, neck: 0.085, shoulder: 0.74, top: 0.94, glass: '#7fe0a4', att: '#3aa866',
    label: { y0: 0.1, y1: 0.4, bg: '#f5f5ec', accent: '#2d9a55', text: 'SOJU', sub: 'ORIGINAL', ink: '#1b6b3a' }, cap: { color: '#2d9a55', metal: false } },
  vodka: { r: 0.25, body: 0.56, neck: 0.09, shoulder: 0.84, top: 1.02, glass: '#eef7ff', att: '#d4e8f5',
    label: { y0: 0.14, y1: 0.52, bg: '#c62828', accent: '#ffffff', text: 'VODKA', sub: 'PREMIUM', ink: '#ffffff' }, cap: { color: '#d7d7de', metal: true } },
  beer: { r: 0.2, body: 0.4, neck: 0.07, shoulder: 0.72, top: 0.96, glass: '#e08a2a', att: '#c46a10',
    label: { y0: 0.1, y1: 0.34, bg: '#f3e2b8', accent: '#b8341f', text: 'BEER', sub: 'COLD LAGER', ink: '#7a1f12' }, cap: { color: '#c9a227', metal: true } },
}

export const CAN_LABEL = { top: 'LAGER', sub: 'COLD & CRISP' }

export const DRINK_NAMES: Record<DrinkKind, string> = {
  soju: 'Soju Bottle', vodka: 'Vodka Bottle', beer: 'Beer Bottle', can: 'Beer Can', shot: 'Shot Glass', cup: 'Red Cup',
}

/** Every string printed on a drink or used as its name, for the brand deny-list test. */
export function labelStrings(): string[] {
  const out: string[] = []
  for (const b of Object.values(BOTTLES)) out.push(b.label.text, b.label.sub)
  out.push(CAN_LABEL.top, CAN_LABEL.sub, ...Object.values(DRINK_NAMES))
  return out
}
```

```ts
// controller/src/tv/turf3d/pieces.ts
import type { DrinkKind } from './drinkSpecs'

/** The engine's piece ids today, plus the drink ids Plan 3 renames them to. Anything else is the red cup. */
const MAP: Record<string, DrinkKind> = {
  cup: 'cup', pizza: 'soju', sneaker: 'vodka', boombox: 'beer', cone: 'can', duck: 'shot',
  soju: 'soju', vodka: 'vodka', beer: 'beer', can: 'can', shot: 'shot',
}

export const drinkFor = (piece?: string): DrinkKind => (piece && MAP[piece]) || 'cup'
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/drinkSpecs.test.ts src/tv/turf3d/pieces.test.ts`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/drinkSpecs.ts controller/src/tv/turf3d/drinkSpecs.test.ts controller/src/tv/turf3d/pieces.ts controller/src/tv/turf3d/pieces.test.ts
git commit -m "feat(turf3d): drink specs, piece mapping and the brand deny-list test"
```

---

### Task 4: Quality levels and the WebGL decision

**Files:**
- Create: `controller/src/tv/turf3d/quality.ts`, `controller/src/tv/turf3d/webgl.ts`
- Test: `controller/src/tv/turf3d/quality.test.ts`, `controller/src/tv/turf3d/webgl.test.ts`

**Interfaces:**
- Produces:
  - `type Quality = 'high' | 'balanced' | 'low'`, `DEFAULT_QUALITY: Quality`, `parseQuality(search: string): Quality`
  - `QualityContext` (React context of `Quality`), `useQuality(): Quality`, `usesAO(q: Quality): boolean`, `usesRealGlass(q: Quality): boolean`, `usesComposer(q: Quality): boolean`
  - `hasWebGL2(make?: () => { getContext(id: string): unknown }): boolean`, `wants3d(search: string, supported: boolean): boolean`

- [ ] **Step 1: Write the failing tests**

```ts
// controller/src/tv/turf3d/quality.test.ts
import { describe, expect, it } from 'vitest'
import { DEFAULT_QUALITY, parseQuality, usesAO, usesComposer, usesRealGlass } from './quality'

describe('quality', () => {
  it('reads ?quality= and falls back to the default', () => {
    expect(parseQuality('?quality=high')).toBe('high')
    expect(parseQuality('?x=1&quality=low')).toBe('low')
    expect(parseQuality('?quality=ultra')).toBe(DEFAULT_QUALITY)
    expect(parseQuality('')).toBe(DEFAULT_QUALITY)
  })
  it('drops effects in the order the spec says: ambient occlusion first, then real glass and the composer', () => {
    expect([usesAO('high'), usesAO('balanced'), usesAO('low')]).toEqual([true, false, false])
    expect([usesRealGlass('high'), usesRealGlass('balanced'), usesRealGlass('low')]).toEqual([true, true, false])
    expect([usesComposer('high'), usesComposer('balanced'), usesComposer('low')]).toEqual([true, true, false])
  })
})
```

```ts
// controller/src/tv/turf3d/webgl.test.ts
import { describe, expect, it } from 'vitest'
import { hasWebGL2, wants3d } from './webgl'

describe('webgl decision', () => {
  it('detects WebGL2 through a canvas factory', () => {
    expect(hasWebGL2(() => ({ getContext: (id) => (id === 'webgl2' ? {} : null) }))).toBe(true)
    expect(hasWebGL2(() => ({ getContext: () => null }))).toBe(false)
    expect(hasWebGL2(() => { throw new Error('no canvas') })).toBe(false)
  })
  it('uses 3D only when supported and not forced to 2D', () => {
    expect(wants3d('', true)).toBe(true)
    expect(wants3d('?board=2d', true)).toBe(false)
    expect(wants3d('', false)).toBe(false)
    expect(wants3d('?board=3d', false)).toBe(false)
  })
})
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/quality.test.ts src/tv/turf3d/webgl.test.ts`
Expected: FAIL, cannot resolve `./quality` and `./webgl`.

- [ ] **Step 3: Write the implementations**

```ts
// controller/src/tv/turf3d/quality.ts
import { createContext, useContext } from 'react'

/** high: everything. balanced: no ambient occlusion. low: also no real glass and no post-processing. */
export type Quality = 'high' | 'balanced' | 'low'
export const DEFAULT_QUALITY: Quality = 'balanced'

export function parseQuality(search: string): Quality {
  const v = new URLSearchParams(search).get('quality')
  return v === 'high' || v === 'balanced' || v === 'low' ? v : DEFAULT_QUALITY
}

export const usesAO = (q: Quality) => q === 'high'
export const usesRealGlass = (q: Quality) => q !== 'low'
export const usesComposer = (q: Quality) => q !== 'low'

export const QualityContext = createContext<Quality>(DEFAULT_QUALITY)
export const useQuality = () => useContext(QualityContext)
```

```ts
// controller/src/tv/turf3d/webgl.ts
export function hasWebGL2(make: () => { getContext(id: string): unknown } = () => document.createElement('canvas')): boolean {
  try { return !!make().getContext('webgl2') } catch { return false }
}

/** 3D when the browser can do it and nobody asked for the flat board with ?board=2d. */
export function wants3d(search: string, supported: boolean): boolean {
  return supported && new URLSearchParams(search).get('board') !== '2d'
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/quality.test.ts src/tv/turf3d/webgl.test.ts`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/quality.ts controller/src/tv/turf3d/quality.test.ts controller/src/tv/turf3d/webgl.ts controller/src/tv/turf3d/webgl.test.ts
git commit -m "feat(turf3d): quality levels and the 3D-or-2D decision"
```

---

### Task 5: Board texture geometry and the painter

**Files:**
- Create: `controller/src/tv/turf3d/fonts.ts`, `controller/src/tv/turf3d/boardTexture.ts`
- Test: `controller/src/tv/turf3d/boardTexture.test.ts`

**Interfaces:**
- Consumes: `tileOf, HALF` from `./layout`; `TurfSpace` from `../types`.
- Produces:
  - `interface TurfLook { paper: string; cornerPaper: string; ink: string; text: string; lineW: number; sticker: string; stickerText: string; nameFont: string; titleFont: string; bandDepth: number }`, `CLASSIC: TurfLook`
  - `interface Rect { x: number; y: number; w: number; h: number }`
  - `tileRect(i: number, S: number): Rect`, `bandRect(i: number, S: number, depth?: number): Rect | null`, `textRect(i: number, S: number, depth?: number): Rect`
  - `fitSize(measure: (text: string, size: number) => number, words: string[], maxW: number, start: number, min: number): number`
  - `drawBoardTexture(board: TurfSpace[], look: TurfLook, S?: number): THREE.CanvasTexture`
  - `ensureFonts(): Promise<void>` (from `fonts.ts`)

- [ ] **Step 1: Write the failing test**

```ts
// controller/src/tv/turf3d/boardTexture.test.ts
import { describe, expect, it } from 'vitest'
import { CLASSIC, bandRect, fitSize, textRect, tileRect } from './boardTexture'

const S = 2048

describe('board texture geometry', () => {
  it('keeps every tile inside the texture and lines neighbours up edge to edge', () => {
    for (let i = 0; i < 40; i++) {
      const r = tileRect(i, S)
      expect(r.x).toBeGreaterThanOrEqual(-1e-6); expect(r.y).toBeGreaterThanOrEqual(-1e-6)
      expect(r.x + r.w).toBeLessThanOrEqual(S + 1e-6); expect(r.y + r.h).toBeLessThanOrEqual(S + 1e-6)
    }
    const a = tileRect(1, S), b = tileRect(2, S)
    expect(a.x).toBeCloseTo(b.x + b.w, 6) // space 2 sits just left of space 1 along the bottom row
    expect(a.y).toBeCloseTo(b.y, 6)
  })

  it('puts each street\'s colour band on the edge nearest the middle of the board', () => {
    const depth = CLASSIC.bandDepth * (S / 12)
    const bottom = tileRect(3, S), bBand = bandRect(3, S)!
    expect(bBand.y).toBeCloseTo(bottom.y, 6); expect(bBand.h).toBeCloseTo(depth, 6); expect(bBand.w).toBeCloseTo(bottom.w, 6)
    const top = tileRect(24, S), tBand = bandRect(24, S)!
    expect(tBand.y + tBand.h).toBeCloseTo(top.y + top.h, 6)
    const left = tileRect(13, S), lBand = bandRect(13, S)!
    expect(lBand.x + lBand.w).toBeCloseTo(left.x + left.w, 6); expect(lBand.w).toBeCloseTo(depth, 6)
    const right = tileRect(34, S), rBand = bandRect(34, S)!
    expect(rBand.x).toBeCloseTo(right.x, 6)
    expect(bandRect(0, S)).toBeNull() // corners have no band
  })

  it('leaves a text area that plus the band exactly covers the tile', () => {
    for (const i of [1, 9, 12, 19, 22, 28, 33, 38]) {
      const tile = tileRect(i, S), band = bandRect(i, S)!, text = textRect(i, S)
      expect(text.w * text.h + band.w * band.h).toBeCloseTo(tile.w * tile.h, 3)
    }
    expect(textRect(0, S)).toEqual(tileRect(0, S))
  })
})

describe('fitSize', () => {
  const measure = (text: string, size: number) => text.length * size * 0.5
  it('returns the start size when everything fits', () => { expect(fitSize(measure, ['ABC'], 500, 40, 10)).toBe(40) })
  it('steps down until the widest word fits', () => {
    const s = fitSize(measure, ['SHORT', 'KAISHUNS'], 100, 56, 10)
    expect(measure('KAISHUNS', s)).toBeLessThanOrEqual(100)
    expect(measure('KAISHUNS', s + 2)).toBeGreaterThan(100)
  })
  it('never goes below the minimum, even if the word cannot fit', () => { expect(fitSize(measure, ['EXTRAORDINARILYLONG'], 20, 40, 14)).toBe(14) })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/boardTexture.test.ts`
Expected: FAIL, cannot resolve `./boardTexture`.

- [ ] **Step 3: Write the implementations**

```ts
// controller/src/tv/turf3d/fonts.ts
import '@fontsource/anton'
import '@fontsource/rammetto-one'

/** The board texture is painted once, so wait until both faces are actually loaded. */
export function ensureFonts(): Promise<void> {
  return Promise.all([document.fonts.load('56px Anton'), document.fonts.load('180px "Rammetto One"')]).then(() => undefined, () => undefined)
}
```

```ts
// controller/src/tv/turf3d/boardTexture.ts
import * as THREE from 'three'
import type { TurfSpace } from '../types'
import { HALF, tileOf } from './layout'

export interface TurfLook {
  paper: string; cornerPaper: string; ink: string; text: string; lineW: number
  sticker: string; stickerText: string
  nameFont: string; titleFont: string
  /** How deep the colour band is, in world units. */
  bandDepth: number
}

/** The classic tabletop: cream paper, black ink, a red title sticker. */
export const CLASSIC: TurfLook = {
  paper: '#f4ead2', cornerPaper: '#efe2bd', ink: '#1a1a1a', text: '#1a1a1a', lineW: 6,
  sticker: '#e2483d', stickerText: '#ffffff', nameFont: 'Anton', titleFont: 'Rammetto One', bandDepth: 0.34,
}

export interface Rect { x: number; y: number; w: number; h: number }

const px = (world: number, S: number) => ((world + HALF) / (HALF * 2)) * S

/** A tile's rectangle in texture pixels. World x maps to texture x; world z (toward the couch) maps to texture y (down). */
export function tileRect(i: number, S: number): Rect {
  const t = tileOf(i), k = S / (HALF * 2)
  return { x: px(t.cx - t.ex / 2, S), y: px(t.cz - t.ez / 2, S), w: t.ex * k, h: t.ez * k }
}

/** The street colour band on the edge nearest the middle of the board; null for corners. */
export function bandRect(i: number, S: number, depth = CLASSIC.bandDepth): Rect | null {
  const t = tileOf(i)
  if (t.corner) return null
  const r = tileRect(i, S), d = depth * (S / (HALF * 2))
  switch (t.side) {
    case 'bottom': return { x: r.x, y: r.y, w: r.w, h: d }
    case 'top': return { x: r.x, y: r.y + r.h - d, w: r.w, h: d }
    case 'left': return { x: r.x + r.w - d, y: r.y, w: d, h: r.h }
    default: return { x: r.x, y: r.y, w: d, h: r.h }
  }
}

/** The tile minus its band: where the name and price go. */
export function textRect(i: number, S: number, depth = CLASSIC.bandDepth): Rect {
  const t = tileOf(i), r = tileRect(i, S), band = bandRect(i, S, depth)
  if (!band) return r
  const d = depth * (S / (HALF * 2))
  switch (t.side) {
    case 'bottom': return { x: r.x, y: r.y + d, w: r.w, h: r.h - d }
    case 'top': return { x: r.x, y: r.y, w: r.w, h: r.h - d }
    case 'left': return { x: r.x, y: r.y, w: r.w - d, h: r.h }
    default: return { x: r.x + d, y: r.y, w: r.w - d, h: r.h }
  }
}

/** The largest size, stepping down by 2, at which the widest word fits [maxW]; never below [min]. */
export function fitSize(measure: (text: string, size: number) => number, words: string[], maxW: number, start: number, min: number): number {
  let size = start
  while (size > min && Math.max(...words.map((w) => measure(w, size))) > maxW) size -= 2
  return Math.max(size, min)
}

const SUBTITLE: Record<string, string> = { payday: 'COLLECT $200', jail: 'JUST VISITING' }

/** Paints the board (tiles, names, prices, the title sticker) to a square canvas texture. Call after [ensureFonts]. */
export function drawBoardTexture(board: TurfSpace[], look: TurfLook, S = 2048): THREE.CanvasTexture {
  const cv = document.createElement('canvas'); cv.width = cv.height = S
  const g = cv.getContext('2d')!
  g.fillStyle = look.paper; g.fillRect(0, 0, S, S)
  g.textAlign = 'center'; g.textBaseline = 'middle'
  const measure = (text: string, size: number) => { g.font = `${size}px ${look.nameFont}, sans-serif`; return g.measureText(text).width }

  board.forEach((space, i) => {
    const tile = tileRect(i, S), corner = tileOf(i).corner
    g.fillStyle = corner ? look.cornerPaper : look.paper; g.fillRect(tile.x, tile.y, tile.w, tile.h)
    g.strokeStyle = look.ink; g.lineWidth = look.lineW; g.strokeRect(tile.x, tile.y, tile.w, tile.h)
    const band = bandRect(i, S)
    if (band && space.kind === 'street' && space.color) {
      g.fillStyle = space.color; g.fillRect(band.x, band.y, band.w, band.h); g.strokeRect(band.x, band.y, band.w, band.h)
    }
    const box = textRect(i, S)
    const words = space.label.toUpperCase().split(' ')
    const size = fitSize(measure, words, box.w - 14, corner ? 66 : 56, 24)
    g.fillStyle = look.text; g.font = `${size}px ${look.nameFont}, sans-serif`
    const line = size + 4, price = space.kind === 'tax' ? space.tax : space.price
    const sub = SUBTITLE[space.kind] ?? (price > 0 ? `$${price}` : '')
    const total = words.length + (sub ? 0.8 : 0)
    let y = box.y + box.h / 2 - ((total - 1) * line) / 2
    for (const w of words) { g.fillText(w, box.x + box.w / 2, y); y += line }
    if (sub) { g.font = `${Math.round(size * 0.72)}px ${look.nameFont}, sans-serif`; g.fillText(sub, box.x + box.w / 2, y - line * 0.1 + line * 0.15) }
  })

  // The title sticker in the middle, fitted so it never touches its border.
  g.save(); g.translate(S / 2, S / 2); g.rotate(-Math.PI / 14)
  const SW = 1040, SH = 330
  g.fillStyle = look.sticker; g.fillRect(-SW / 2, -SH / 2, SW, SH)
  g.strokeStyle = look.ink; g.lineWidth = look.lineW * 1.5; g.strokeRect(-SW / 2, -SH / 2, SW, SH)
  g.strokeStyle = look.stickerText; g.lineWidth = 5; g.strokeRect(-SW / 2 + 24, -SH / 2 + 24, SW - 48, SH - 48)
  let fs = 200
  do { g.font = `${fs}px "${look.titleFont}", serif`; fs -= 4 } while (fs > 60 && g.measureText('HOME TURF').width > SW - 190)
  g.fillStyle = look.stickerText; g.fillText('HOME TURF', 0, -22)
  g.font = `44px ${look.nameFont}, sans-serif`; g.fillText('GOOD NEIGHBORS. BAD LANDLORDS.', 0, 92)
  g.restore()

  const tex = new THREE.CanvasTexture(cv)
  tex.colorSpace = THREE.SRGBColorSpace; tex.anisotropy = 16
  return tex
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/boardTexture.test.ts && npx tsc -b`
Expected: PASS, 6 tests; no tsc output.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/fonts.ts controller/src/tv/turf3d/boardTexture.ts controller/src/tv/turf3d/boardTexture.test.ts
git commit -m "feat(turf3d): upright board texture with fitted names and a fitted title sticker"
```

---

### Task 6: Camera poses and the timeline (pacing formula plus beat planner)

**Files:**
- Create: `controller/src/tv/turf3d/camera.ts`, `controller/src/tv/turf3d/timeline.ts`
- Test: `controller/src/tv/turf3d/camera.test.ts`, `controller/src/tv/turf3d/timeline.test.ts`

**Interfaces:**
- Consumes: `TurfBeat` from `../types`.
- Produces from `camera.ts`: `type Shot = 'wide' | 'dice' | 'follow' | 'close'`, `type V3 = [number, number, number]`, `interface Pose { pos: V3; look: V3 }`, `wideScale(aspect: number): number`, `shotPose(shot: Shot, focus: { x: number; z: number } | null, aspect: number): Pose`.
- Produces from `timeline.ts`: constants `DICE_MS, LAND_PAD_MS, QUICK_HOP_MS, QUICK_PAD_MS`; `hopMs(index, hops, quick): number`, `hopHeight(index, hops, quick): number`, `walkMs(hops, quick): number`, `moveDwellMs(hops, diced, quick): number`; `type Cue` (below); `planBeats(fresh: TurfBeat[], opts: { quick: boolean }): { cues: Cue[]; totalMs: number }`.

`Cue` is exactly:

```ts
export type Cue =
  | { at: number; kind: 'shot'; shot: Shot; focus: number | null }
  | { at: number; kind: 'hop'; token: number; space: number; ms: number; height: number; last: boolean }
  | { at: number; kind: 'banner'; text: string | null }
  | { at: number; kind: 'target'; space: number | null }
  | { at: number; kind: 'land'; token: number; space: number }
  | { at: number; kind: 'snap'; token: number; space: number }
  | { at: number; kind: 'sfx'; name: 'hop' | 'drumroll'; arg: number }
```

- [ ] **Step 1: Write the failing tests**

```ts
// controller/src/tv/turf3d/camera.test.ts
import { describe, expect, it } from 'vitest'
import { shotPose, wideScale } from './camera'

describe('camera poses', () => {
  it('keeps the wide shot at 16:9 and backs off for narrower frames', () => {
    expect(wideScale(16 / 9)).toBe(1)
    expect(wideScale(1)).toBeCloseTo(1.7, 6)
    expect(wideScale(0)).toBeGreaterThan(1) // a zero-size canvas never divides by zero
  })
  it('descends from wide to follow to close and stays near the piece it follows', () => {
    const focus = { x: 3, z: 5 }
    const wide = shotPose('wide', null, 16 / 9), follow = shotPose('follow', focus, 16 / 9), close = shotPose('close', focus, 16 / 9)
    expect(wide.pos[1]).toBeGreaterThan(follow.pos[1]); expect(follow.pos[1]).toBeGreaterThan(close.pos[1])
    expect(close.look).toEqual([3, 0.2, 5])
    expect(Math.hypot(close.pos[0] - 3, close.pos[1], close.pos[2] - 5)).toBeLessThan(6)
  })
  it('looks at the middle of the board for the dice', () => {
    expect(shotPose('dice', null, 16 / 9).look).toEqual([0, 0, 0])
  })
})
```

```ts
// controller/src/tv/turf3d/timeline.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfBeat } from '../types'
import { DICE_MS, LAND_PAD_MS, hopMs, moveDwellMs, planBeats, walkMs, type Cue } from './timeline'

let seq = 0
const beat = (kind: string, b: Partial<TurfBeat> = {}): TurfBeat =>
  ({ seq: ++seq, kind, token: -1, other: -1, space: -1, amount: 0, dice: [], path: [], tokens: [], sips: 0, ...b })
const walk = (from: number, n: number) => Array.from({ length: n }, (_, i) => (from + 1 + i) % 40)
type Hop = Extract<Cue, { kind: 'hop' }>
const hops = (p: { cues: Cue[] }) => p.cues.filter((c): c is Hop => c.kind === 'hop')

describe('pacing formula (the Kotlin HomeTurfTest carries the same table)', () => {
  it('matches the agreed values', () => {
    expect(moveDwellMs(3, true, true)).toBe(2180)
    expect(moveDwellMs(7, false, true)).toBe(3220)
    expect(moveDwellMs(3, true, false)).toBe(5100)
    expect(moveDwellMs(7, true, false)).toBe(6020)
    expect(moveDwellMs(7, false, false)).toBe(3420)
    expect(moveDwellMs(1, false, false)).toBe(1720)
    expect(DICE_MS).toBe(2600); expect(LAND_PAD_MS).toBe(900)
  })
  it('walks 230 ms per early hop, then slows for the last three; Quick is a flat 260', () => {
    expect(Array.from({ length: 7 }, (_, i) => hopMs(i, 7, false))).toEqual([230, 230, 230, 230, 320, 460, 820])
    expect(Array.from({ length: 3 }, (_, i) => hopMs(i, 3, true))).toEqual([260, 260, 260])
    expect(walkMs(7, false)).toBe(2520)
  })
})

describe('planBeats', () => {
  it('plays a Quick move as flat hops with no camera work, ending in the old dwell', () => {
    const p = planBeats([beat('move', { token: 1, path: walk(0, 3) })], { quick: true })
    expect(hops(p).map((h) => h.at)).toEqual([0, 260, 520])
    expect(p.cues.some((c) => c.kind === 'shot')).toBe(false)
    expect(p.totalMs).toBe(3 * 260 + 1400)
  })

  it('plays a Theatre roll: dice shot and banner first, the walk after DICE_MS, a close-up for the last three hops', () => {
    const p = planBeats([beat('roll', { token: 3, dice: [4, 5, 1] }), beat('move', { token: 3, path: walk(14, 9) })], { quick: false })
    expect(p.cues[0]).toEqual({ at: 0, kind: 'shot', shot: 'dice', focus: null })
    expect(p.cues).toContainEqual({ at: 1500, kind: 'banner', text: '4 + 5 = 9' })
    expect(p.cues).toContainEqual({ at: DICE_MS, kind: 'banner', text: null })
    const h = hops(p)
    expect(h).toHaveLength(9)
    expect(h[0].at).toBe(DICE_MS)
    expect(h[8]).toMatchObject({ ms: 820, height: 1.5, last: true, space: 23 })
    const tailAt = h[6].at
    expect(p.cues).toContainEqual({ at: tailAt, kind: 'shot', shot: 'close', focus: 23 })
    expect(p.cues).toContainEqual({ at: tailAt, kind: 'target', space: 23 })
    expect(p.cues.filter((c) => c.kind === 'sfx' && c.name === 'drumroll')).toHaveLength(1)
    expect(p.cues.filter((c) => c.kind === 'sfx' && c.name === 'hop')).toHaveLength(9)
    const land = p.cues.find((c) => c.kind === 'land')!
    expect(land.at).toBe(DICE_MS + walkMs(9, false))
    expect(p.cues.at(-1)).toMatchObject({ kind: 'shot', shot: 'wide' })
    expect(p.totalMs).toBe(moveDwellMs(9, true, false))
  })

  it('skips the dice theatre for a move that did not follow a roll (a card, the bus)', () => {
    const p = planBeats([beat('move', { token: 0, path: walk(4, 5) })], { quick: false })
    expect(p.cues.some((c) => c.kind === 'shot' && c.shot === 'dice')).toBe(false)
    expect(hops(p)[0].at).toBe(0)
    expect(p.totalMs).toBe(moveDwellMs(5, false, false))
  })

  it('always totals exactly the engine dwell', () => {
    for (const quick of [true, false]) for (const diced of [true, false]) for (let n = 1; n <= 12; n++) {
      const beats = [...(diced ? [beat('roll', { token: 0, dice: [3, 4, 1] })] : []), beat('move', { token: 0, path: walk(0, n) })]
      expect(planBeats(beats, { quick }).totalMs).toBe(moveDwellMs(n, diced, quick))
    }
  })

  it('queues two moves in one update so they never overlap', () => {
    const p = planBeats([beat('move', { token: 0, path: walk(0, 3) }), beat('move', { token: 0, path: walk(3, 2) })], { quick: false })
    const h = hops(p)
    const firstEnd = walkMs(3, false) + LAND_PAD_MS
    expect(h[3].at).toBe(firstEnd)
    expect(p.totalMs).toBe(moveDwellMs(3, false, false) + moveDwellMs(2, false, false))
  })

  it('snaps a token into Timeout after its walk finishes', () => {
    const p = planBeats([beat('move', { token: 2, path: walk(28, 2) }), beat('jail', { token: 2, space: 10 })], { quick: true })
    const snap = p.cues.find((c) => c.kind === 'snap')!
    expect(snap).toMatchObject({ token: 2, space: 10 })
    expect(snap.at).toBe(p.totalMs)
  })

  it('does nothing for an empty path or a roll with no move after it', () => {
    expect(planBeats([beat('move', { token: 0, path: [] })], { quick: false })).toEqual({ cues: [], totalMs: 0 })
    expect(planBeats([beat('roll', { token: 0, dice: [2, 3, 1] })], { quick: false })).toEqual({ cues: [], totalMs: 0 })
    expect(planBeats([], { quick: true })).toEqual({ cues: [], totalMs: 0 })
  })

  it('does not treat a later, unrelated move as diced after a turn change', () => {
    const p = planBeats([beat('roll', { token: 0, dice: [2, 3, 1] }), beat('turn', { token: 1 }), beat('move', { token: 1, path: walk(5, 2) })], { quick: false })
    expect(p.cues.some((c) => c.kind === 'shot' && c.shot === 'dice')).toBe(false)
  })
})
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/camera.test.ts src/tv/turf3d/timeline.test.ts`
Expected: FAIL, cannot resolve `./camera` and `./timeline`.

- [ ] **Step 3: Write the implementations**

```ts
// controller/src/tv/turf3d/camera.ts
export type Shot = 'wide' | 'dice' | 'follow' | 'close'
export type V3 = [number, number, number]
export interface Pose { pos: V3; look: V3 }

const WIDE: V3 = [0, 14, 15.4]

/** Backs the wide shot off when the frame is narrower than 16:9 so the whole board still fits. */
export const wideScale = (aspect: number) => Math.max(1, 1.7 / Math.max(aspect, 0.1))

/** The camera pose for a named shot. [focus] is a world x/z (a piece or a tile); ignored by wide and dice. */
export function shotPose(shot: Shot, focus: { x: number; z: number } | null, aspect: number): Pose {
  const f = focus ?? { x: 0, z: 0 }
  switch (shot) {
    case 'wide': { const k = wideScale(aspect); return { pos: [0, WIDE[1] * k, WIDE[2] * k], look: [0, 0, 0.5] } }
    case 'dice': return { pos: [0, 8.6, 8.8], look: [0, 0, 0] }
    case 'follow': return { pos: [f.x * 0.7, 6.4, f.z * 0.7 + 6.8], look: [f.x, 0, f.z] }
    default: return { pos: [f.x * 0.85, 3.5, f.z * 0.85 + 3.7], look: [f.x, 0.2, f.z] }
  }
}
```

```ts
// controller/src/tv/turf3d/timeline.ts
import type { TurfBeat } from '../types'
import type { Shot } from './camera'

/** Kept in step with HomeTurf.kt (DICE_MS, LAND_PAD_MS, hopMs, moveDwellMs); both tests assert the same table. */
export const DICE_MS = 2600
export const LAND_PAD_MS = 900
export const QUICK_HOP_MS = 260
export const QUICK_PAD_MS = 1400
const EARLY_HOP_MS = 230
const TAIL_HOP_MS = [820, 460, 320] // last, second to last, third to last

export const hopMs = (index: number, hops: number, quick: boolean): number => {
  if (quick) return QUICK_HOP_MS
  const left = hops - 1 - index
  return left < 3 ? TAIL_HOP_MS[left] : EARLY_HOP_MS
}

export const hopHeight = (index: number, hops: number, quick: boolean): number => {
  if (quick) return 0.55
  const left = hops - 1 - index
  return left === 0 ? 1.5 : left < 3 ? 0.95 : 0.7
}

export const walkMs = (hops: number, quick: boolean): number =>
  Array.from({ length: hops }, (_, i) => hopMs(i, hops, quick)).reduce((a, b) => a + b, 0)

/** How long the engine holds the MOVE phase, and so how long the TV has to finish showing it. */
export const moveDwellMs = (hops: number, diced: boolean, quick: boolean): number =>
  quick ? hops * QUICK_HOP_MS + QUICK_PAD_MS : (diced ? DICE_MS : 0) + walkMs(hops, false) + LAND_PAD_MS

export type Cue =
  | { at: number; kind: 'shot'; shot: Shot; focus: number | null }
  | { at: number; kind: 'hop'; token: number; space: number; ms: number; height: number; last: boolean }
  | { at: number; kind: 'banner'; text: string | null }
  | { at: number; kind: 'target'; space: number | null }
  | { at: number; kind: 'land'; token: number; space: number }
  | { at: number; kind: 'snap'; token: number; space: number }
  | { at: number; kind: 'sfx'; name: 'hop' | 'drumroll'; arg: number }

/**
 * Turns the beats that arrived since the last update into timed cues (ms from now, sorted, ties keep insertion order).
 * A roll only counts as "diced" for the move that follows it; a turn change or Timeout clears it.
 */
export function planBeats(fresh: TurfBeat[], { quick }: { quick: boolean }): { cues: Cue[]; totalMs: number } {
  const cues: Cue[] = []
  let t = 0
  let dice: number[] | null = null
  for (const b of fresh) {
    if (b.kind === 'roll') { dice = b.dice; continue }
    if (b.kind === 'turn') { dice = null; continue }
    if (b.kind === 'jail') { dice = null; cues.push({ at: t, kind: 'snap', token: b.token, space: b.space }); continue }
    if (b.kind !== 'move' || b.path.length === 0) continue

    const rolled = dice
    dice = null
    const path = b.path, n = path.length, dest = path[n - 1]
    if (rolled && !quick) {
      const [a = 0, c = 0] = rolled
      cues.push({ at: t, kind: 'shot', shot: 'dice', focus: null })
      cues.push({ at: t + 1500, kind: 'banner', text: `${a} + ${c} = ${a + c}` })
      cues.push({ at: t + DICE_MS, kind: 'banner', text: null })
      t += DICE_MS
    }
    const tail = Math.max(0, n - 3)
    for (let i = 0; i < n; i++) {
      const ms = hopMs(i, n, quick)
      if (!quick) {
        if (i < tail) cues.push({ at: t, kind: 'shot', shot: 'follow', focus: path[i] })
        if (i === tail) {
          cues.push({ at: t, kind: 'shot', shot: 'close', focus: dest })
          cues.push({ at: t, kind: 'target', space: dest })
          cues.push({ at: t, kind: 'sfx', name: 'drumroll', arg: 1.6 })
        }
      }
      cues.push({ at: t, kind: 'hop', token: b.token, space: path[i], ms, height: hopHeight(i, n, quick), last: i === n - 1 })
      cues.push({ at: t, kind: 'sfx', name: 'hop', arg: i })
      t += ms
    }
    cues.push({ at: t, kind: 'land', token: b.token, space: dest })
    if (!quick) {
      cues.push({ at: t, kind: 'target', space: null })
      cues.push({ at: t + 600, kind: 'shot', shot: 'wide', focus: null })
    }
    t += quick ? QUICK_PAD_MS : LAND_PAD_MS
  }
  return { cues: cues.sort((a, b) => a.at - b.at), totalMs: t }
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/camera.test.ts src/tv/turf3d/timeline.test.ts && npx tsc -b`
Expected: PASS, 13 tests; no tsc output.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/camera.ts controller/src/tv/turf3d/camera.test.ts controller/src/tv/turf3d/timeline.ts controller/src/tv/turf3d/timeline.test.ts
git commit -m "feat(turf3d): camera poses and the beat-to-cue timeline with the shared pacing formula"
```

---

### Task 7: Engine pacing, the `pace` setting and the `quick` view field (Kotlin)

**Files:**
- Modify: `tv/engine/src/main/kotlin/partyos/engine/games/turf/HomeTurf.kt` (companion constants near line 987; `move` near line 344; call sites at lines 311, 321, 331; state creation near line 80; view build near line 882)
- Modify: `tv/engine/src/main/kotlin/partyos/engine/games/turf/TurfState.kt` (add `quick`, near `drinks` at line 115)
- Modify: `tv/engine/src/main/kotlin/partyos/engine/TurfViews.kt` (`TurfTv`, next to `drinks`)
- Modify: `tv/engine/src/main/kotlin/partyos/engine/PartyEngine.kt` (`optionRange`, near line 184)
- Modify: `controller/src/protocol/fixtures/server-messages.json` (regenerated)
- Test: `tv/engine/src/test/kotlin/partyos/engine/games/turf/HomeTurfTest.kt`

**Interfaces:**
- Produces (Kotlin): `HomeTurf.DICE_MS = 2_600L`, `LAND_PAD_MS = 900L`, `EARLY_HOP_MS = 230L`, `fun hopMs(index: Int, hops: Int, quick: Boolean): Long`, `fun walkMs(hops: Int, quick: Boolean): Long`, `fun moveDwellMs(hops: Int, diced: Boolean, quick: Boolean): Long` (all in the companion), `TurfState.quick: Boolean = false`, `TurfTv.quick: Boolean = false`, lobby setting `pace` (0 Theatre, 1 Quick).

- [ ] **Step 1: Write the failing tests**

In `HomeTurfTest.kt`, first make the existing tests keep their old timings by making Quick the test default. Change `start`:

```kotlin
    private fun start(names: List<String>, vararg opts: Pair<String, Int>): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(3), registry)
        ids = names.map { e.add(it) }
        e.host(HostCmd.SetOption("pace", 1)) // Quick: the old MOVE dwell, so the existing timings hold; tests that need Theatre pass "pace" to 0
        opts.forEach { (k, v) -> e.host(HostCmd.SetOption(k, v)) }
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("turf")))
        e.host(HostCmd.SkipPhase) // tutorial
        return ids
    }
```

Add these tests inside `class HomeTurfTest` (for example after `rollMoveBuyEndTurn`):

```kotlin
    // ---- pacing (the TypeScript timeline test carries the same table) ---------------------------

    @Test fun moveDwellTableMatchesTheTvTimeline() {
        assertEquals(2180L, HomeTurf.moveDwellMs(3, diced = true, quick = true))
        assertEquals(3220L, HomeTurf.moveDwellMs(7, diced = false, quick = true))
        assertEquals(5100L, HomeTurf.moveDwellMs(3, diced = true, quick = false))
        assertEquals(6020L, HomeTurf.moveDwellMs(7, diced = true, quick = false))
        assertEquals(3420L, HomeTurf.moveDwellMs(7, diced = false, quick = false))
        assertEquals(1720L, HomeTurf.moveDwellMs(1, diced = false, quick = false))
        assertEquals(listOf(230L, 230L, 230L, 230L, 320L, 460L, 820L), (0 until 7).map { HomeTurf.hopMs(it, 7, false) })
        assertEquals(listOf(260L, 260L, 260L), (0 until 3).map { HomeTurf.hopMs(it, 3, true) })
    }

    @Test fun paceOptionIsAcceptedAndShownToTheTv() {
        start(listOf("Ava", "Ben"), "pace" to 0); skipSetup()
        assertFalse(tv.quick)
        assertEquals(ActionResult.Ack, e.host(HostCmd.SetOption("pace", 1)))
    }

    @Test fun quickPaceKeepsTheOldMoveDwell() {
        start(listOf("Ava", "Ben")); skipSetup()
        rig { it.clean() }
        rigRoll { !it.doubles && it.move == 3 }
        act(seatOf(state.turn), "roll")
        assertTrue(tv.quick)
        assertEquals("move", tv.phase)
        assertEquals(moveMs(3), remaining)
    }

    @Test fun theatrePaceHoldsTheMoveForTheDiceAndTheSlowedWalk() {
        start(listOf("Ava", "Ben"), "pace" to 0); skipSetup()
        rig { it.clean() }
        rigRoll { !it.doubles && it.move == 3 }
        act(seatOf(state.turn), "roll")
        assertFalse(tv.quick)
        assertEquals("move", tv.phase)
        assertEquals(5_100L, remaining)
        passTime(5_099)
        assertEquals("move", tv.phase)
        passTime(1)
        assertNotEquals("move", tv.phase)
    }
```

- [ ] **Step 2: Run to verify they fail**

Run:
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd /Users/jjahn/HoopDreams/tv && ./gradlew :engine:test --tests '*HomeTurfTest*' 2>&1 | tail -15
```
Expected: compilation FAIL (`Unresolved reference: moveDwellMs`, `quick`).

- [ ] **Step 3: Write the implementation**

In `HomeTurf.kt`'s companion (next to `HOP_MS` and `MOVE_PAD_MS`), add:

```kotlin
        /** The dice theatre before a rolled move, and the pause after landing (Theatre pace). Mirrors controller/src/tv/turf3d/timeline.ts. */
        const val DICE_MS = 2_600L
        const val LAND_PAD_MS = 900L
        const val EARLY_HOP_MS = 230L

        /** How long hop [index] of [hops] lasts: flat at Quick, otherwise slowing over the last three. */
        fun hopMs(index: Int, hops: Int, quick: Boolean): Long {
            if (quick) return HOP_MS
            return when (hops - 1 - index) { 0 -> 820L; 1 -> 460L; 2 -> 320L; else -> EARLY_HOP_MS }
        }

        fun walkMs(hops: Int, quick: Boolean): Long = (0 until hops).sumOf { hopMs(it, hops, quick) }

        /** How long the MOVE phase is held, so the TV can finish showing the dice and the walk. */
        fun moveDwellMs(hops: Int, diced: Boolean, quick: Boolean): Long =
            if (quick) hops * HOP_MS + MOVE_PAD_MS else (if (diced) DICE_MS else 0L) + walkMs(hops, false) + LAND_PAD_MS
```

Change `move` and its three dice-driven call sites (lines 311, 321, 331 pass `diced = true`):

```kotlin
    private fun move(s0: TurfState, t: Int, steps: Int, ctx: GameContext, diced: Boolean = false): Step<TurfState> {
        val from = s0.tokens[t].pos
        val path = TurfRules.path(from, steps)
        val to = path.lastOrNull() ?: from
        val passes = steps > 0 && from + steps >= TurfBoard.SIZE
        var s = s0.tok(t) { it.copy(pos = to, passedPayday = it.passedPayday || passes) }.beat("move", token = t, space = to, path = path)
        if (passes) s = s.cash(t, TurfBoard.PAYDAY_PAY).beat("payday", token = t, amount = TurfBoard.PAYDAY_PAY)
        if (path.isEmpty()) return land(s, ctx)
        return go(s, MOVE, ctx, moveDwellMs(path.size, diced, s.quick))
    }
```

```kotlin
        // line 311
        return move(s.copy(scout = r.scout), t, r.move, ctx, diced = true)
        // line 321
            return move(s, t, r.a + r.b, ctx, diced = true)
        // line 331 (jailWalk)
        return move(s.tok(t) { it.copy(jailed = false) }.copy(jailRoll = true).beat("free", token = t), t, r.a + r.b, ctx, diced = true)
```

In `TurfState.kt`, right after `val drinks: Boolean = true,`:

```kotlin
    /** Quick pace: the old short MOVE dwell and no dice theatre on the TV. Theatre (false) is the default. */
    val quick: Boolean = false,
```

In `HomeTurf.kt`'s state creation (next to `drinks = (ctx.settings["drinks"] ?: 1) != 0,` near line 80):

```kotlin
            quick = (ctx.settings["pace"] ?: 0) != 0,
```

In the view build (near line 882, `timed = s.phase in TIMED, drinks = s.drinks, ...`):

```kotlin
            timed = s.phase in TIMED, drinks = s.drinks, quick = s.quick, beats = s.beats, ticker = s.ticker,
```

In `TurfViews.kt`, in `data class TurfTv`, immediately after `val drinks: Boolean` (keep this exact position: the field order is the JSON order):

```kotlin
    val quick: Boolean = false,
```

In `PartyEngine.kt` `optionRange`, after the `"vp" -> 8..10` branch:

```kotlin
        // Home Turf's pace: 0 Theatre (dice and a slowed walk), 1 Quick (the old short move).
        "pace" -> 0..1
```

- [ ] **Step 4: Run to verify the engine tests pass, then regenerate the protocol fixture**

Run:
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd /Users/jjahn/HoopDreams/tv && ./gradlew :engine:test 2>&1 | tail -8
./gradlew :server:test --tests '*ProtocolFixturesTest*' -PupdateFixtures 2>&1 | tail -6
cd .. && git diff --stat controller/src/protocol/fixtures/server-messages.json && git diff controller/src/protocol/fixtures/server-messages.json | grep '^[+-] ' | sort | uniq -c
```
Expected: engine tests BUILD SUCCESSFUL (all existing Turf tests still pass because `start` defaults to Quick; the four new tests pass); the fixture diff consists only of added `"quick": false,` lines (one per Home Turf TV entry).

- [ ] **Step 5: Run the server tests without the update flag, then commit**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; cd /Users/jjahn/HoopDreams/tv && ./gradlew :server:test 2>&1 | tail -6`
Expected: BUILD SUCCESSFUL.

```bash
cd /Users/jjahn/HoopDreams
git add tv/engine controller/src/protocol/fixtures/server-messages.json
git commit -m "feat(turf): Theatre/Quick pace with a shared move-dwell formula"
```

---

### Task 8: The `pace` setting in the protocol, TV lobby and captain phone

**Files:**
- Modify: `controller/src/protocol.ts` (the `OptionKey` union and its comment near line 207)
- Modify: `controller/src/tv/TvPage.tsx` (the `Lobby` interface and `lobbyOf` near lines 80 to 86; the key handler near line 283; the controls row near line 329)
- Modify: `controller/src/pages/Captain.tsx` (`settingsOf` near line 18; the Home Turf steppers near line 60)
- Modify: `controller/src/tv/types.ts` (`TurfTv`)

**Interfaces:**
- Consumes: the `pace` lobby setting from Task 7 (`0` Theatre, `1` Quick).
- Produces: `OptionKey` includes `'pace'`; `TurfTv.quick?: boolean`; TV key `S` toggles pace while Home Turf is focused; a "Show" stepper on the captain's phone.

- [ ] **Step 1: Add the type and the option key**

In `controller/src/protocol.ts`, extend the comment and the union:

```ts
/**
 * Shared lobby settings. turfMode: Home Turf 0 auto, 1 solo, 2 teams; minutes: Home Turf's and Sprawl's game clock
 * (0 = no limit); vp: Sprawl's points to win (8 or 10); pace: Home Turf's TV show, 0 Theatre or 1 Quick.
 */
export type OptionKey = 'rounds' | 'teams' | 'drinks' | 'game' | 'captain' | 'turfMode' | 'minutes' | 'vp' | 'timers' | 'show' | 'grid' | 'pace'
```

In `controller/src/tv/types.ts`, in `interface TurfTv`, after `drinks: boolean`:

```ts
  /** Quick pace: the short move with no dice theatre. Absent or false means Theatre. */
  quick?: boolean
```

- [ ] **Step 2: Wire the TV lobby**

In `controller/src/tv/TvPage.tsx`:

```ts
interface Lobby { rounds: number; teams: number; drinks: boolean; game: number; phones: boolean; turfMode: number; minutes: number; vp: number; timers: number; show: number; grid: number; pace: number }
```

```ts
    turfMode: s.turfMode ?? 0, minutes: s.minutes ?? 45, vp: s.vp ?? 8, timers: s.timers ?? 0, show: s.show ?? 0, grid: s.grid ?? 0, pace: s.pace ?? 0,
```

Key handler, right after the `k === 's' && jeopardy` line:

```ts
      else if (k === 's' && turf) { setOption('pace', lobby.pace ? 0 : 1); sfx.focus() }
```

Controls row, right after the `{turf && <span className="stepper"><Keycap label="T" /> Play ...` line:

```tsx
            {turf && <span className="stepper"><Keycap label="S" /> Show <b>{lobby.pace ? 'QUICK' : 'THEATRE'}</b></span>}
```

- [ ] **Step 3: Wire the captain phone**

In `controller/src/pages/Captain.tsx` `settingsOf`, append `pace`:

```ts
    turfMode: s.turfMode ?? 0, minutes: s.minutes ?? 45, vp: s.vp ?? 8, timers: s.timers ?? 0, show: s.show ?? 0, pace: s.pace ?? 0,
```

Inside the Home Turf block, right after the `Game clock` stepper:

```tsx
              <Stepper label="Show" value={s.pace ? 'Quick' : 'Theatre'} onDown={() => set('pace', 0)} onUp={() => set('pace', 1)} />
```

- [ ] **Step 4: Verify types and existing tests**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run`
Expected: no tsc output; all vitest tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/protocol.ts controller/src/tv/TvPage.tsx controller/src/pages/Captain.tsx controller/src/tv/types.ts
git commit -m "feat(turf): Theatre/Quick show setting on the TV lobby (key S) and the captain phone"
```

---

### Task 9: The choreography hook

**Files:**
- Create: `controller/src/tv/turf3d/useChoreography.ts`
- Test: `controller/src/tv/turf3d/useChoreography.test.ts`

**Interfaces:**
- Consumes: `planBeats, Cue` from `./timeline`; `Shot` from `./camera`; `sfx` from `../audio`; `TurfTv` from `../types`.
- Produces:
  - `interface HopInfo { n: number; ms: number; height: number; last: boolean }`
  - `interface Craft { shown: number[]; hop: (HopInfo | null)[]; shot: Shot; focus: number | null; banner: string | null; target: number | null; landed: { token: number; space: number; n: number } | null }`
  - `initialSeen(g: TurfTv): number` (the highest beat seq, so a TV that connects mid-game never replays old beats)
  - `restCraft(g: TurfTv): Craft`, `snapFor(craft: Craft, g: TurfTv): Craft` (returns the same object when already true)
  - `useChoreography(g: TurfTv, quick: boolean, skip: number): Craft`

The pure helpers are tested; the hook is a thin timer wrapper verified in Task 11.

- [ ] **Step 1: Write the failing test for the pure helpers**

```ts
// controller/src/tv/turf3d/useChoreography.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfTv } from '../types'
import { initialSeen, restCraft, snapFor } from './useChoreography'

const tv = (pos: number[], seqs: number[] = []): TurfTv => ({
  tokens: pos.map((p) => ({ pos: p })), beats: seqs.map((seq) => ({ seq, kind: 'x', token: 0, other: -1, space: -1, amount: 0, dice: [], path: [], tokens: [], sips: 0 })),
}) as unknown as TurfTv

describe('choreography helpers', () => {
  it('starts already caught up: a TV that joins mid-game does not replay old beats', () => {
    expect(initialSeen(tv([0], [4, 9, 7]))).toBe(9)
    expect(initialSeen(tv([0], []))).toBe(0)
  })
  it('rests pieces where the engine says they are', () => {
    const c = restCraft(tv([3, 14, 0]))
    expect(c.shown).toEqual([3, 14, 0]); expect(c.hop).toEqual([null, null, null]); expect(c.shot).toBe('wide')
  })
  it('snaps only when shown positions are wrong, and returns the same object when they are right', () => {
    const g = tv([3, 14])
    const right = restCraft(g)
    expect(snapFor(right, g)).toBe(right)
    const stale = { ...right, shown: [3, 9] }
    expect(snapFor(stale, g).shown).toEqual([3, 14])
    expect(snapFor({ ...right, shown: [3] }, g).shown).toEqual([3, 14]) // a token joined
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/useChoreography.test.ts`
Expected: FAIL, cannot resolve `./useChoreography`.

- [ ] **Step 3: Write the implementation**

`../audio` starts audio machinery at import time in some setups; if the vitest import of `sfx` fails under Node, move the `sfx` import behind the hook by importing it lazily inside `apply` (`const { sfx } = await import('../audio')` is not needed: the existing `TurfStage.tsx` already imports it at module level and its tests run, so keep the plain import and only change it if the test run proves otherwise).

```ts
// controller/src/tv/turf3d/useChoreography.ts
import { useEffect, useRef, useState } from 'react'
import { sfx } from '../audio'
import type { TurfTv } from '../types'
import type { Shot } from './camera'
import { planBeats, type Cue } from './timeline'

export interface HopInfo { n: number; ms: number; height: number; last: boolean }

/** Everything the 3D scene needs to know about the show right now. */
export interface Craft {
  /** The space each token is shown on (it lags the engine while a move plays). */
  shown: number[]
  /** The hop a token is making, with a counter so the scene can tell a new hop from the same one. */
  hop: (HopInfo | null)[]
  shot: Shot
  focus: number | null
  banner: string | null
  target: number | null
  landed: { token: number; space: number; n: number } | null
}

const latest = (g: TurfTv) => g.beats.reduce((m, b) => Math.max(m, b.seq), 0)

/** The highest beat seq already on the board: everything at or below it is history, not something to animate. */
export const initialSeen = (g: TurfTv): number => latest(g)

export const restCraft = (g: TurfTv): Craft => ({
  shown: g.tokens.map((t) => t.pos), hop: g.tokens.map(() => null), shot: 'wide', focus: null, banner: null, target: null, landed: null,
})

/** Puts every piece back where the engine says it is (a restore, a Timeout, a new token); same object when already right. */
export function snapFor(c: Craft, g: TurfTv): Craft {
  const right = c.shown.length === g.tokens.length && c.shown.every((s, i) => s === g.tokens[i].pos)
  return right ? c : { ...c, shown: g.tokens.map((t) => t.pos), hop: g.tokens.map(() => null) }
}

/**
 * Plays new engine beats as a timed show. Beats that arrive while a show is running queue behind it. Any [skip] change
 * jumps to the final state. Only used by the 3D stage; the 2D board keeps its own hops.
 */
export function useChoreography(g: TurfTv, quick: boolean, skip: number): Craft {
  const [craft, setCraft] = useState<Craft>(() => restCraft(g))
  const seen = useRef(initialSeen(g))
  const busyUntil = useRef(0)
  const timers = useRef<ReturnType<typeof setTimeout>[]>([])
  const hopN = useRef(0)
  const gRef = useRef(g)
  gRef.current = g

  useEffect(() => {
    const fresh = g.beats.filter((b) => b.seq > seen.current)
    seen.current = Math.max(seen.current, latest(g))
    const now = performance.now()
    if (fresh.length > 0) {
      const plan = planBeats(fresh, { quick })
      if (plan.cues.length > 0) {
        const wait = Math.max(0, busyUntil.current - now)
        busyUntil.current = now + wait + plan.totalMs
        for (const cue of plan.cues) timers.current.push(setTimeout(() => apply(cue), wait + cue.at))
        timers.current.push(setTimeout(() => setCraft((c) => snapFor(c, gRef.current)), wait + plan.totalMs + 50))
        return
      }
    }
    if (now >= busyUntil.current) setCraft((c) => snapFor(c, g))
  }, [g, quick])

  useEffect(() => {
    if (skip === 0) return
    timers.current.forEach(clearTimeout); timers.current = []
    busyUntil.current = 0
    setCraft(restCraft(gRef.current))
  }, [skip])

  useEffect(() => () => timers.current.forEach(clearTimeout), [])

  function apply(c: Cue) {
    switch (c.kind) {
      case 'hop':
        setCraft((cr) => {
          const shown = cr.shown.slice(); shown[c.token] = c.space
          const hop = cr.hop.slice(); hop[c.token] = { n: ++hopN.current, ms: c.ms, height: c.height, last: c.last }
          return { ...cr, shown, hop }
        })
        break
      case 'snap':
        setCraft((cr) => {
          const shown = cr.shown.slice(); shown[c.token] = c.space
          const hop = cr.hop.slice(); hop[c.token] = null
          return { ...cr, shown, hop }
        })
        break
      case 'shot': setCraft((cr) => ({ ...cr, shot: c.shot, focus: c.focus })); break
      case 'banner': setCraft((cr) => ({ ...cr, banner: c.text })); break
      case 'target': setCraft((cr) => ({ ...cr, target: c.space })); break
      case 'land': setCraft((cr) => ({ ...cr, landed: { token: c.token, space: c.space, n: (cr.landed?.n ?? 0) + 1 } })); break
      case 'sfx': if (c.name === 'hop') sfx.hop(c.arg); else sfx.drumroll(c.arg); break
    }
  }

  return craft
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/useChoreography.test.ts && npx tsc -b`
Expected: PASS, 3 tests; no tsc output.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/useChoreography.ts controller/src/tv/turf3d/useChoreography.test.ts
git commit -m "feat(turf3d): choreography hook that plays engine beats as a queued, skippable show"
```

---

### Task 10: The scene: drinks, table, board, buildings, flags, pieces, effects, camera, post

**Files:**
- Move: `controller/src/tv/spike3d/Drinks.tsx` to `controller/src/tv/turf3d/Drinks.tsx` (then rewrite as below)
- Create: `controller/src/tv/turf3d/scene/Lights.tsx`, `Table.tsx`, `Board3D.tsx`, `Houses.tsx`, `Flags.tsx`, `Pieces.tsx`, `Effects.tsx`, `CameraRig.tsx`, `Post.tsx`
- Create: `controller/src/tv/turf3d/TurfScene.tsx`

**Interfaces:**
- Consumes: everything from Tasks 2 to 9.
- Produces: `TurfScene({ tv, craft }: { tv: TurfTv; craft: Craft })` for use inside an R3F `Canvas` wrapped in `QualityContext.Provider`; `Drink({ kind, color })`.

There are no unit tests for R3F components; this task's verification is type-check plus the visual check in Task 11.

- [ ] **Step 1: Move the spike's drinks into place**

```bash
cd /Users/jjahn/HoopDreams/controller
mkdir -p src/tv/turf3d/scene
git mv src/tv/spike3d/Drinks.tsx src/tv/turf3d/Drinks.tsx
```

- [ ] **Step 2: Rewrite `Drinks.tsx` to use the specs and the quality context**

```tsx
// controller/src/tv/turf3d/Drinks.tsx
// Home Turf's six pieces as drinks, built from scratch with lathe geometry. Labels are generic on purpose (see drinkSpecs.ts).
import { useMemo } from 'react'
import * as THREE from 'three'
import { BOTTLES, CAN_LABEL, type BottleKind, type BottleLabel, type DrinkKind } from './drinkSpecs'
import { useQuality, usesRealGlass } from './quality'

const v = (pts: number[][]) => pts.map(([x, y]) => new THREE.Vector2(x, y))
/** Smooth (smoothstep) run from radius r0 at y0 to r1 at y1, for bottle shoulders. */
const ease = (r0: number, r1: number, y0: number, y1: number, n = 10) =>
  Array.from({ length: n }, (_, i) => { const t = (i + 1) / n, e = t * t * (3 - 2 * t); return [r0 + (r1 - r0) * e, y0 + (y1 - y0) * t] })

const fit = (g: CanvasRenderingContext2D, text: string, max: number, start: number) => {
  let fs = start
  do { g.font = `${fs}px Anton, sans-serif`; fs -= 4 } while (fs > 18 && g.measureText(text).width > max)
}

function labelTexture(l: BottleLabel): THREE.CanvasTexture {
  const cv = document.createElement('canvas'); cv.width = 512; cv.height = 256
  const g = cv.getContext('2d')!
  g.fillStyle = l.bg; g.fillRect(0, 0, 512, 256)
  g.fillStyle = l.accent; g.fillRect(0, 0, 512, 24); g.fillRect(0, 232, 512, 24)
  g.textAlign = 'center'; g.textBaseline = 'middle'
  // The visible arc from the couch is about a third of the wrap, so keep the name inside ~150px and print it front and back.
  for (const cx of [128, 384]) {
    g.strokeStyle = l.ink; g.lineWidth = 4; g.beginPath(); g.ellipse(cx, 128, 84, 76, 0, 0, Math.PI * 2); g.stroke()
    g.fillStyle = l.ink; fit(g, l.text, 124, 96); g.fillText(l.text, cx, 112)
    g.fillRect(cx - 52, 150, 104, 4)
    fit(g, l.sub, 120, 30); g.fillText(l.sub, cx, 176)
  }
  const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8
  return t
}

function canTexture(): THREE.CanvasTexture {
  const cv = document.createElement('canvas'); cv.width = 512; cv.height = 256
  const g = cv.getContext('2d')!
  g.fillStyle = '#dcdde2'; g.fillRect(0, 0, 512, 256)
  g.fillStyle = '#c62828'; g.fillRect(0, 56, 512, 144)
  g.fillStyle = '#f5c542'; g.fillRect(0, 48, 512, 8); g.fillRect(0, 200, 512, 8)
  g.textAlign = 'center'; g.textBaseline = 'middle'
  for (const cx of [128, 384]) {
    g.fillStyle = '#fff'; fit(g, CAN_LABEL.top, 124, 100); g.fillText(CAN_LABEL.top, cx, 118)
    fit(g, CAN_LABEL.sub, 130, 26); g.fillText(CAN_LABEL.sub, cx, 168)
  }
  const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8
  return t
}

/** Real transmissive glass, or (Low quality) a glossy tinted stand-in that costs no extra render pass. */
function Glass({ color, att, dist = 0.7, thick = 0.5 }: { color: string; att: string; dist?: number; thick?: number }) {
  const real = usesRealGlass(useQuality())
  return real
    ? <meshPhysicalMaterial color={color} transmission={1} thickness={thick} ior={1.45} roughness={0.05} attenuationColor={att} attenuationDistance={dist} clearcoat={1} clearcoatRoughness={0.03} envMapIntensity={1.5} />
    : <meshPhysicalMaterial color={att} roughness={0.06} clearcoat={1} clearcoatRoughness={0.02} metalness={0.1} envMapIntensity={2} />
}

function Bottle({ kind }: { kind: BottleKind }) {
  const c = BOTTLES[kind]
  const geo = useMemo(() => v([[0, 0], [c.r - 0.05, 0], [c.r - 0.015, 0.02], [c.r, 0.06], [c.r, c.body], ...ease(c.r, c.neck, c.body, c.shoulder), [c.neck, c.top - 0.03], [c.neck + 0.022, c.top - 0.02], [c.neck + 0.022, c.top], [0, c.top]]), [kind])
  const tex = useMemo(() => labelTexture(c.label), [kind])
  const ly = (c.label.y0 + c.label.y1) / 2
  return (
    <group>
      <mesh castShadow><latheGeometry args={[geo, 56]} /><Glass color={c.glass} att={c.att} dist={kind === 'vodka' ? 3 : 1.4} /></mesh>
      <mesh position={[0, ly, 0]} rotation-y={-Math.PI / 2} castShadow><cylinderGeometry args={[c.r + 0.006, c.r + 0.006, c.label.y1 - c.label.y0, 56, 1, true]} /><meshStandardMaterial map={tex} roughness={0.55} side={THREE.DoubleSide} /></mesh>
      <mesh position={[0, c.top + 0.02, 0]} castShadow><cylinderGeometry args={[c.neck + 0.03, c.neck + 0.03, 0.09, 32]} />{c.cap.metal ? <meshStandardMaterial color={c.cap.color} metalness={1} roughness={0.25} /> : <meshStandardMaterial color={c.cap.color} roughness={0.4} />}</mesh>
    </group>
  )
}

function Can() {
  const tex = useMemo(canTexture, [])
  return (
    <group>
      <mesh position={[0, 0.36, 0]} rotation-y={-Math.PI / 2} castShadow><cylinderGeometry args={[0.26, 0.26, 0.66, 56]} /><meshStandardMaterial map={tex} metalness={0.35} roughness={0.4} envMapIntensity={1.2} /></mesh>
      <mesh position={[0, 0.69, 0]} castShadow><cylinderGeometry args={[0.22, 0.26, 0.05, 56]} /><meshStandardMaterial color="#cfd2d8" metalness={1} roughness={0.25} /></mesh>
      <mesh position={[0, 0.725, 0]}><cylinderGeometry args={[0.2, 0.2, 0.03, 56]} /><meshStandardMaterial color="#dfe2e8" metalness={1} roughness={0.2} /></mesh>
      <mesh position={[0.04, 0.745, 0]} rotation={[Math.PI / 2, 0, 0]}><torusGeometry args={[0.055, 0.014, 12, 24]} /><meshStandardMaterial color="#aeb2ba" metalness={1} roughness={0.25} /></mesh>
    </group>
  )
}

function Shot() {
  const shell = useMemo(() => v([[0, 0], [0.15, 0], [0.17, 0.02], [0.2, 0.1], [0.27, 0.5], [0.245, 0.5], [0.235, 0.47], [0.15, 0.16], [0.0, 0.16]]), [])
  const liquid = useMemo(() => v([[0, 0.16], [0.15, 0.16], [0.22, 0.4], [0, 0.4]]), [])
  return (
    <group>
      <mesh castShadow><latheGeometry args={[shell, 48]} /><Glass color="#f2f9ff" att="#dbeaf5" dist={2} thick={0.12} /></mesh>
      <mesh><latheGeometry args={[liquid, 48]} /><meshPhysicalMaterial color="#d99a2b" roughness={0.1} transmission={0.4} thickness={0.3} emissive="#7a4a08" emissiveIntensity={0.35} /></mesh>
    </group>
  )
}

function Cup() {
  const shell = useMemo(() => v([[0, 0], [0.17, 0], [0.185, 0.02], [0.335, 0.7], [0.345, 0.7], [0.345, 0.72], [0.32, 0.72], [0.31, 0.7], [0.16, 0.06], [0, 0.06]]), [])
  return (
    <group>
      <mesh castShadow><latheGeometry args={[shell, 56]} /><meshPhysicalMaterial color="#d62828" roughness={0.35} clearcoat={0.5} side={THREE.DoubleSide} /></mesh>
      <mesh position={[0, 0.5, 0]}><cylinderGeometry args={[0.245, 0.245, 0.05, 40]} /><meshPhysicalMaterial color="#d99a2b" roughness={0.15} emissive="#7a4a08" emissiveIntensity={0.3} /></mesh>
      <mesh position={[0, 0.535, 0]}><cylinderGeometry args={[0.245, 0.25, 0.03, 40]} /><meshStandardMaterial color="#fff8e8" roughness={0.9} /></mesh>
    </group>
  )
}

/** One drink standing on a coaster in the player's colour (the coaster, plus the silhouette, is how you tell whose piece it is). */
export function Drink({ kind, color }: { kind: DrinkKind; color: string }) {
  return (
    <group>
      <mesh position={[0, 0.03, 0]} castShadow receiveShadow><cylinderGeometry args={[0.44, 0.44, 0.06, 48]} /><meshStandardMaterial color={color} roughness={0.5} /></mesh>
      <mesh position={[0, 0.062, 0]} rotation-x={-Math.PI / 2}><ringGeometry args={[0.34, 0.38, 48]} /><meshBasicMaterial color="#ffffff" transparent opacity={0.55} toneMapped={false} /></mesh>
      <group position={[0, 0.06, 0]}>
        {kind === 'can' ? <Can /> : kind === 'shot' ? <Shot /> : kind === 'cup' ? <Cup /> : <Bottle kind={kind} />}
      </group>
    </group>
  )
}
```

- [ ] **Step 3: Write the scene parts**

```tsx
// controller/src/tv/turf3d/scene/Lights.tsx
import { Environment, Lightformer } from '@react-three/drei'

/** One warm spotlight with soft shadows, plus local reflections (no downloaded HDRI, so it works offline). */
export function Lights() {
  return (
    <>
      <Environment resolution={256} environmentIntensity={0.6}>
        <Lightformer form="rect" intensity={4} position={[0, 8, 2]} scale={[14, 6, 1]} rotation-x={Math.PI / 2} />
        <Lightformer form="rect" intensity={2} position={[-9, 3, 4]} scale={[8, 4, 1]} rotation-y={Math.PI / 2} color="#ffe2b8" />
        <Lightformer form="rect" intensity={1.5} position={[9, 4, -4]} scale={[8, 4, 1]} rotation-y={-Math.PI / 2} />
      </Environment>
      <spotLight castShadow position={[-3, 15, 6]} angle={0.62} penumbra={0.85} intensity={420} color="#ffd9a6" shadow-mapSize={[2048, 2048]} shadow-bias={-0.0003} shadow-radius={6} />
      <ambientLight intensity={0.18} />
    </>
  )
}
```

```tsx
// controller/src/tv/turf3d/scene/Table.tsx
import { useEffect, useMemo } from 'react'
import * as THREE from 'three'

function woodTexture(): THREE.CanvasTexture {
  const cv = document.createElement('canvas'); cv.width = 1024; cv.height = 1024
  const g = cv.getContext('2d')!
  g.fillStyle = '#7a4b2e'; g.fillRect(0, 0, 1024, 1024)
  for (let i = 0; i < 260; i++) {
    const y = Math.random() * 1024, a = Math.random() * 0.18
    g.strokeStyle = Math.random() < 0.5 ? `rgba(40,20,8,${a})` : `rgba(200,140,90,${a})`
    g.lineWidth = 1 + Math.random() * 3
    g.beginPath(); g.moveTo(0, y)
    for (let x = 0; x <= 1024; x += 64) g.lineTo(x, y + Math.sin(x * 0.01 + i) * 4)
    g.stroke()
  }
  for (let p = 0; p < 4; p++) { g.fillStyle = 'rgba(0,0,0,.35)'; g.fillRect(0, p * 256, 1024, 3) }
  const t = new THREE.CanvasTexture(cv)
  t.colorSpace = THREE.SRGBColorSpace; t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(3, 3); t.anisotropy = 8
  return t
}

export function Table() {
  const tex = useMemo(woodTexture, [])
  useEffect(() => () => tex.dispose(), [tex])
  return (
    <mesh rotation-x={-Math.PI / 2} position={[0, -0.42, 0]} receiveShadow>
      <planeGeometry args={[80, 80]} />
      <meshStandardMaterial map={tex} roughness={0.55} metalness={0.05} />
    </mesh>
  )
}
```

```tsx
// controller/src/tv/turf3d/scene/Board3D.tsx
import { RoundedBox } from '@react-three/drei'
import { useEffect, useMemo } from 'react'
import type { TurfTv } from '../../types'
import { CLASSIC, drawBoardTexture } from '../boardTexture'
import { HALF } from '../layout'

/** The slab and its printed top. The texture is repainted only when the board's names, colours or prices change. */
export function Board3D({ tv }: { tv: TurfTv }) {
  const key = tv.board.map((s) => `${s.label}|${s.color ?? ''}|${s.price}|${s.tax}|${s.kind}`).join(';')
  const tex = useMemo(() => drawBoardTexture(tv.board, CLASSIC, 2048), [key])
  useEffect(() => () => tex.dispose(), [tex])
  return (
    <>
      <RoundedBox args={[HALF * 2 + 0.7, 0.42, HALF * 2 + 0.7]} radius={0.07} smoothness={4} position={[0, -0.21, 0]} castShadow receiveShadow>
        <meshPhysicalMaterial color={CLASSIC.paper} roughness={0.6} clearcoat={0.3} />
      </RoundedBox>
      <mesh rotation-x={-Math.PI / 2} position={[0, 0.003, 0]} receiveShadow>
        <planeGeometry args={[HALF * 2, HALF * 2]} />
        <meshPhysicalMaterial map={tex} roughness={0.6} clearcoat={0.15} />
      </mesh>
    </>
  )
}
```

```tsx
// controller/src/tv/turf3d/scene/Houses.tsx
import type { TurfTv } from '../../types'
import { CLASSIC } from '../boardTexture'
import { tileOf } from '../layout'

function HouseMesh({ hotel }: { hotel?: boolean }) {
  return (
    <group scale={0.85}>
      <mesh castShadow position={[0, 0.16, 0]}><boxGeometry args={[hotel ? 0.6 : 0.3, 0.32, 0.3]} /><meshPhysicalMaterial color={hotel ? '#e2483d' : '#2fbf55'} roughness={0.45} clearcoat={0.5} /></mesh>
      <mesh castShadow position={[0, 0.4, 0]} rotation={[0, Math.PI / 4, 0]}><coneGeometry args={[hotel ? 0.42 : 0.24, 0.24, 4]} /><meshPhysicalMaterial color={hotel ? '#8a2a22' : '#1f8a3f'} roughness={0.45} clearcoat={0.5} /></mesh>
    </group>
  )
}

/** Levels 1 to 3 are houses, level 4 is a hotel, standing on the colour band. */
function TileBuildings({ space, level }: { space: number; level: number }) {
  const t = tileOf(space)
  const depth = t.side === 'bottom' || t.side === 'top' ? t.ez : t.ex
  const along = { x: Math.abs(t.inward.z), z: Math.abs(t.inward.x) }
  const cx = t.cx + t.inward.x * (depth / 2 - CLASSIC.bandDepth / 2)
  const cz = t.cz + t.inward.z * (depth / 2 - CLASSIC.bandDepth / 2)
  if (level >= 4) return <group position={[cx, 0, cz]}><HouseMesh hotel /></group>
  return (
    <>
      {Array.from({ length: level }, (_, k) => {
        const o = (k - (level - 1) / 2) * 0.24
        return <group key={k} position={[cx + along.x * o, 0, cz + along.z * o]}><HouseMesh /></group>
      })}
    </>
  )
}

export function Houses({ tv }: { tv: TurfTv }) {
  return <>{tv.level.map((lvl, i) => (lvl > 0 ? <TileBuildings key={i} space={i} level={lvl} /> : null))}</>
}
```

```tsx
// controller/src/tv/turf3d/scene/Flags.tsx
import { useMemo } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../types'
import { tileOf } from '../layout'

/** Six flag shapes, one per token, so an owner is never told by colour alone. */
function flagGeometry(k: number): THREE.ShapeGeometry {
  const s = new THREE.Shape()
  switch (k % 6) {
    case 0: s.moveTo(0, 0); s.lineTo(0.26, 0); s.lineTo(0.26, 0.2); s.lineTo(0, 0.2); break // square
    case 1: s.moveTo(0, 0); s.lineTo(0.3, 0.1); s.lineTo(0, 0.2); break // pennant
    case 2: s.moveTo(0, 0); s.lineTo(0.28, 0); s.lineTo(0.2, 0.1); s.lineTo(0.28, 0.2); s.lineTo(0, 0.2); break // swallowtail
    case 3: s.absarc(0.11, 0.1, 0.11, 0, Math.PI * 2, false); break // circle
    case 4: s.moveTo(0.12, 0); s.lineTo(0.24, 0.1); s.lineTo(0.12, 0.2); s.lineTo(0, 0.1); break // diamond
    default: s.moveTo(0.06, 0); s.lineTo(0.22, 0); s.lineTo(0.28, 0.1); s.lineTo(0.22, 0.2); s.lineTo(0.06, 0.2); s.lineTo(0, 0.1); break // hexagon
  }
  s.closePath()
  return new THREE.ShapeGeometry(s)
}

function OwnerMark({ space, owner, color, mortgaged }: { space: number; owner: number; color: string; mortgaged: boolean }) {
  const t = tileOf(space)
  const geo = useMemo(() => flagGeometry(owner), [owner])
  const tint = mortgaged ? '#8a8a8a' : color
  const horizontal = t.side === 'bottom' || t.side === 'top'
  const depth = horizontal ? t.ez : t.ex
  const out = { x: -t.inward.x, z: -t.inward.z }
  // The tint strip hugs the outer edge; the flag stands just inside it, near the tile's centre line.
  const stripAt = { x: t.cx + out.x * (depth / 2 - 0.05), z: t.cz + out.z * (depth / 2 - 0.05) }
  const poleAt = { x: t.cx + out.x * (depth / 2 - 0.22), z: t.cz + out.z * (depth / 2 - 0.22) }
  return (
    <>
      <mesh rotation-x={-Math.PI / 2} position={[stripAt.x, 0.006, stripAt.z]} receiveShadow>
        <planeGeometry args={horizontal ? [t.ex - 0.08, 0.09] : [0.09, t.ez - 0.08]} />
        <meshStandardMaterial color={tint} roughness={0.6} />
      </mesh>
      <group position={[poleAt.x, 0, poleAt.z]}>
        <mesh position={[0, 0.25, 0]} castShadow><cylinderGeometry args={[0.012, 0.012, 0.5, 8]} /><meshStandardMaterial color="#d9d9de" metalness={0.9} roughness={0.3} /></mesh>
        <mesh geometry={geo} position={[0.01, 0.3, 0]} castShadow><meshStandardMaterial color={tint} roughness={0.55} side={THREE.DoubleSide} /></mesh>
      </group>
    </>
  )
}

export function Flags({ tv }: { tv: TurfTv }) {
  return (
    <>
      {tv.owner.map((o, i) => {
        const tok = o >= 0 ? tv.tokens[o] : undefined
        if (!tok || tileOf(i).corner) return null
        return <OwnerMark key={i} space={i} owner={o} color={tok.color} mortgaged={tv.mortgaged.includes(i)} />
      })}
    </>
  )
}
```

```tsx
// controller/src/tv/turf3d/scene/Pieces.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../types'
import { Drink } from '../Drinks'
import { crowdIndex, crowdSlot, spacePos } from '../layout'
import { drinkFor } from '../pieces'
import type { Craft } from '../useChoreography'

interface Motion { n: number; from: THREE.Vector3; t0: number }

/** The six drinks. They hop when the craft says so, otherwise ease to their slot on the shown space. */
export function Pieces({ tv, craft }: { tv: TurfTv; craft: Craft }) {
  const groups = useRef<(THREE.Group | null)[]>([])
  const motion = useRef<(Motion | null)[]>([])
  const landedAt = useRef(0)
  const placed = useRef<boolean[]>([])
  const alive = tv.tokens.map((t) => !t.bankrupt)
  const crowd = useMemo(() => crowdIndex(craft.shown, alive), [craft.shown, tv.tokens])

  const targetOf = (k: number) => {
    const p = spacePos(craft.shown[k] ?? tv.tokens[k].pos), slot = crowdSlot(crowd[k]?.rank ?? 0, crowd[k]?.count ?? 1)
    return new THREE.Vector3(p.x + slot.dx, 0, p.z + slot.dz)
  }

  useEffect(() => { if (craft.landed) landedAt.current = performance.now() }, [craft.landed?.n])

  useFrame(() => {
    const now = performance.now()
    tv.tokens.forEach((_, k) => {
      const g = groups.current[k]
      if (!g) return
      const target = targetOf(k), hop = craft.hop[k]
      if (hop && motion.current[k]?.n !== hop.n) motion.current[k] = { n: hop.n, from: g.position.clone().setY(0), t0: now }
      const m = motion.current[k]
      if (hop && m && m.n === hop.n && now - m.t0 < hop.ms) {
        const t = (now - m.t0) / hop.ms, e = hop.last ? t * t * (3 - 2 * t) : t
        g.position.lerpVectors(m.from, target, e)
        g.position.y = Math.sin(Math.PI * t) * hop.height
        const stretch = 1 + Math.sin(Math.PI * t) * 0.14
        g.scale.set(1 / Math.sqrt(stretch), stretch, 1 / Math.sqrt(stretch))
        g.rotation.z = Math.sin(Math.PI * t) * 0.2
      } else {
        g.position.x += (target.x - g.position.x) * 0.25
        g.position.z += (target.z - g.position.z) * 0.25
        g.position.y += (0 - g.position.y) * 0.4
        g.scale.lerp(new THREE.Vector3(1, 1, 1), 0.3)
        const since = (now - landedAt.current) / 1000
        g.rotation.z = craft.landed?.token === k && since < 0.9 ? Math.exp(-5 * since) * Math.sin(since * 26) * 0.2 : 0
      }
    })
  })

  return (
    <>
      {tv.tokens.map((tok, k) => (
        <group
          key={k}
          visible={!tok.bankrupt}
          ref={(gr) => {
            groups.current[k] = gr
            if (gr && !placed.current[k]) { gr.position.copy(targetOf(k)); placed.current[k] = true }
          }}
        >
          <Drink kind={drinkFor(tok.piece)} color={tok.color} />
        </group>
      ))}
    </>
  )
}
```

```tsx
// controller/src/tv/turf3d/scene/Effects.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import { spacePos } from '../layout'
import type { Craft } from '../useChoreography'

/** The pulsing target ring while a piece approaches, and the shock ring plus tile lift when it lands. */
export function Effects({ craft }: { craft: Craft }) {
  const ring = useRef<THREE.Mesh>(null)
  const pulse = useRef<THREE.Mesh>(null)
  const lift = useRef<THREE.Mesh>(null)
  const landedAt = useRef<number | null>(null)

  useEffect(() => {
    if (!craft.landed) return
    landedAt.current = performance.now()
    const p = spacePos(craft.landed.space)
    pulse.current?.position.set(p.x, 0.03, p.z)
    lift.current?.position.set(p.x, 0, p.z)
  }, [craft.landed?.n])

  useFrame(() => {
    if (ring.current) {
      if (craft.target != null) {
        const p = spacePos(craft.target)
        ring.current.position.set(p.x, 0.02, p.z)
        const s = 1 + Math.sin(performance.now() / 110) * 0.12
        ring.current.scale.set(s, 1, s)
        ring.current.visible = true
      } else ring.current.visible = false
    }
    const since = landedAt.current == null ? 1 : (performance.now() - landedAt.current) / 700
    const on = since < 1
    if (pulse.current) {
      pulse.current.visible = on
      if (on) { const s = 0.5 + since * 4.5; pulse.current.scale.set(s, s, 1); (pulse.current.material as THREE.MeshBasicMaterial).opacity = 0.9 * (1 - since) }
    }
    if (lift.current) {
      lift.current.visible = on
      if (on) lift.current.position.y = Math.sin(Math.PI * since) * 0.3
    }
  })

  return (
    <>
      <mesh ref={ring} rotation-x={-Math.PI / 2} visible={false}><ringGeometry args={[0.42, 0.56, 48]} /><meshBasicMaterial color="#ffd23f" toneMapped={false} /></mesh>
      <mesh ref={pulse} rotation-x={-Math.PI / 2} visible={false}><ringGeometry args={[0.4, 0.55, 48]} /><meshBasicMaterial color="#ffffff" transparent toneMapped={false} /></mesh>
      <mesh ref={lift} visible={false}><boxGeometry args={[0.9, 0.05, 0.9]} /><meshStandardMaterial color="#ffd23f" emissive="#ffb000" emissiveIntensity={0.6} transparent opacity={0.85} /></mesh>
    </>
  )
}
```

```tsx
// controller/src/tv/turf3d/scene/CameraRig.tsx
import { useFrame, useThree } from '@react-three/fiber'
import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import { shotPose, type Shot } from '../camera'
import { spacePos } from '../layout'

/** Eases the camera toward the current shot, with a short shake on each landing. */
export function CameraRig({ shot, focus, landedN }: { shot: Shot; focus: number | null; landedN: number }) {
  const { camera, size } = useThree()
  const look = useRef(new THREE.Vector3(0, 0, 0.5))
  const shake = useRef(0)
  useEffect(() => { if (landedN > 0) shake.current = 0.3 }, [landedN])

  useFrame((_, dt) => {
    const f = focus == null ? null : spacePos(focus)
    const pose = shotPose(shot, f, size.width / Math.max(size.height, 1))
    const k = 1 - Math.pow(0.001, dt)
    camera.position.lerp(new THREE.Vector3(...pose.pos), k * (shot === 'close' ? 0.9 : 0.6))
    look.current.lerp(new THREE.Vector3(...pose.look), k * 0.9)
    const s = shake.current
    shake.current *= Math.pow(0.02, dt)
    camera.position.x += (Math.random() - 0.5) * s
    camera.position.y += (Math.random() - 0.5) * s
    camera.lookAt(look.current)
  })
  return null
}
```

```tsx
// controller/src/tv/turf3d/scene/Post.tsx
import { EffectComposer, N8AO, TiltShift2, ToneMapping, Vignette } from '@react-three/postprocessing'
import { ToneMappingMode } from 'postprocessing'
import { useQuality, usesAO, usesComposer } from '../quality'

/** Ambient occlusion (High only), a very light tilt-shift, tone mapping and a vignette. Low renders straight to screen. */
export function Post() {
  const q = useQuality()
  if (!usesComposer(q)) return null
  if (usesAO(q)) {
    return (
      <EffectComposer multisampling={4}>
        <N8AO aoRadius={0.7} intensity={2.6} distanceFalloff={1} />
        <TiltShift2 blur={0.03} />
        <ToneMapping mode={ToneMappingMode.ACES_FILMIC} />
        <Vignette offset={0.3} darkness={0.6} />
      </EffectComposer>
    )
  }
  return (
    <EffectComposer multisampling={4}>
      <TiltShift2 blur={0.03} />
      <ToneMapping mode={ToneMappingMode.ACES_FILMIC} />
      <Vignette offset={0.3} darkness={0.6} />
    </EffectComposer>
  )
}
```

```tsx
// controller/src/tv/turf3d/TurfScene.tsx
import type { TurfTv } from '../types'
import { Board3D } from './scene/Board3D'
import { CameraRig } from './scene/CameraRig'
import { Effects } from './scene/Effects'
import { Flags } from './scene/Flags'
import { Houses } from './scene/Houses'
import { Lights } from './scene/Lights'
import { Pieces } from './scene/Pieces'
import { Post } from './scene/Post'
import { Table } from './scene/Table'
import type { Craft } from './useChoreography'

/** The whole tabletop. Render it inside an R3F Canvas that is wrapped in QualityContext.Provider. */
export function TurfScene({ tv, craft }: { tv: TurfTv; craft: Craft }) {
  return (
    <>
      <color attach="background" args={['#241710']} />
      <CameraRig shot={craft.shot} focus={craft.focus} landedN={craft.landed?.n ?? 0} />
      <Lights />
      <Table />
      <Board3D tv={tv} />
      <Houses tv={tv} />
      <Flags tv={tv} />
      <Pieces tv={tv} craft={craft} />
      <Effects craft={craft} />
      <Post />
    </>
  )
}
```

- [ ] **Step 4: Type-check**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b`
Expected: no output. (The spike route in `Gallery.tsx` still imports `spike3d/Spike3D.tsx`, which imports `./Drinks`; that import is now broken. Fix it by pointing the spike file at the moved module: in `src/tv/spike3d/Spike3D.tsx` change `import { Drink, DRINK_KINDS } from './Drinks'` to `import { Drink } from '../turf3d/Drinks'` and `import { DRINK_KINDS } from '../turf3d/drinkSpecs'`, and wrap its `Canvas` children in `<QualityContext.Provider value="balanced">` imported from `'../turf3d/quality'`. Task 12 deletes the whole spike, so keep this patch minimal.)

Run again: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b`
Expected: no output.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d controller/src/tv/spike3d
git commit -m "feat(turf3d): the tabletop scene, drinks, buildings, owner flags, pieces, effects and camera"
```

---

### Task 11: The 3D stage, integration into the TV, gallery fixtures and CSS

**Files:**
- Create: `controller/src/tv/turf3d/TurfStage3D.tsx`
- Modify: `controller/src/tv/TurfStage.tsx` (imports; `Turf` component near line 68; `useHops` near line 82)
- Modify: `controller/src/tv/TurfGallery.tsx` (fixtures near line 100 and 108)
- Modify: `controller/src/tv/turf.css`

**Interfaces:**
- Consumes: `TurfScene`, `useChoreography`, `QualityContext`/`parseQuality`, `ensureFonts`, `hasWebGL2`/`wants3d`.
- Produces: `TurfStage3D({ g, children, onLost }: { g: TurfTv; children: ReactNode; onLost: () => void })`; `TurfStage` renders it when `wants3d(...)` and falls back to `TurfBoard` otherwise or after a lost context.

- [ ] **Step 1: Write the 3D stage**

```tsx
// controller/src/tv/turf3d/TurfStage3D.tsx
import { Canvas } from '@react-three/fiber'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../types'
import { ensureFonts } from './fonts'
import { QualityContext, parseQuality } from './quality'
import { TurfScene } from './TurfScene'
import { useChoreography } from './useChoreography'

/**
 * The 3D board fills the whole TV stage behind the rails. [children] (the well panel) floats over the middle of the
 * board. Space or Enter skips the animation in progress. A lost WebGL context reports through [onLost] so the caller
 * can fall back to the flat board.
 */
export function TurfStage3D({ g, children, onLost }: { g: TurfTv; children: ReactNode; onLost: () => void }) {
  const [skip, setSkip] = useState(0)
  const [fontsReady, setFontsReady] = useState(false)
  const quality = useMemo(() => parseQuality(location.search), [])
  const craft = useChoreography(g, !!g.quick, skip)

  useEffect(() => { void ensureFonts().then(() => setFontsReady(true)) }, [])
  useEffect(() => {
    const on = (e: KeyboardEvent) => { if (e.key === ' ' || e.key === 'Enter') setSkip((s) => s + 1) }
    window.addEventListener('keydown', on)
    return () => window.removeEventListener('keydown', on)
  }, [])

  return (
    <div className="turf-3d">
      {fontsReady && (
        <Canvas
          shadows
          dpr={[1, 2]}
          camera={{ fov: 38, position: [0, 14, 15.4] }}
          gl={{ antialias: true, toneMapping: THREE.ACESFilmicToneMapping }}
          onCreated={({ gl }) => gl.domElement.addEventListener('webglcontextlost', (e) => { e.preventDefault(); onLost() })}
        >
          <QualityContext.Provider value={quality}>
            <TurfScene tv={g} craft={craft} />
          </QualityContext.Provider>
        </Canvas>
      )}
      <div className="turf-banner3d" aria-live="polite">{craft.banner}</div>
      <div className="turf-well3d">{children}</div>
    </div>
  )
}
```

- [ ] **Step 2: Integrate into `TurfStage.tsx`**

Change the React import and add the lazy import and helpers (top of file):

```tsx
import { lazy, Suspense, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
```

```tsx
import { hasWebGL2, wants3d } from './turf3d/webgl'

const TurfStage3D = lazy(() => import('./turf3d/TurfStage3D').then((m) => ({ default: m.TurfStage3D })))
```

Replace the body of `Turf` (keep `TokenCard`, `FlashView` and the rest unchanged):

```tsx
function Turf({ g, stage, players, clock }: { g: TurfTv; stage: StageInfo; players: PlayerSummary[]; clock: Clock }) {
  const people = useMemo(() => new Map(players.map((p) => [p.id, p])), [players])
  const [lost, setLost] = useState(false)
  const use3d = useMemo(() => !lost && wants3d(location.search, hasWebGL2()), [lost])
  const { display, zoom } = useHops(g, !use3d)
  const flash = useFlashes(g, people)
  useBeatSounds(g)
  const left = g.tokens.map((_, i) => i).filter((i) => i % 2 === 0)
  const right = g.tokens.map((_, i) => i).filter((i) => i % 2 === 1)
  const hot = g.phase === 'buy' ? g.buy : g.phase === 'auction' ? g.auction?.space : undefined
  const well = <Well g={g} stage={stage} clock={clock} people={people} />
  const flat = <TurfBoard tv={g} display={display} zoom={zoom} hot={hot}>{well}</TurfBoard>
  return (
    <div className={`turf-stage ${use3d ? 'is3d' : ''}`}>
      {use3d && <Suspense fallback={null}><TurfStage3D g={g} onLost={() => setLost(true)}>{well}</TurfStage3D></Suspense>}
      <div className="turf-rail left"><GameMark game="turf" />{left.map((i) => <TokenCard key={i} g={g} i={i} people={people} />)}</div>
      <div className="turf-board-wrap">{!use3d && flat}</div>
      <div className="turf-rail right">{right.map((i) => <TokenCard key={i} g={g} i={i} people={people} />)}</div>
      <AnimatePresence>{flash && <FlashView key={flash.id} f={flash} />}</AnimatePresence>
    </div>
  )
}
```

Make `useHops` skip its timers when the 3D stage owns the motion (it still tracks `seen` so switching back never replays):

```tsx
function useHops(g: TurfTv, enabled: boolean) {
  const [display, setDisplay] = useState<number[]>(() => g.tokens.map((t) => t.pos))
  const [zoom, setZoom] = useState<number | null>(null)
  const seen = useRef(latest(g.beats))
  const busy = useRef(0)
  const timers = useRef<ReturnType<typeof setTimeout>[]>([])
  useEffect(() => {
    const fresh = g.beats.filter((b) => b.seq > seen.current)
    seen.current = Math.max(seen.current, latest(g.beats))
    if (!enabled) { setDisplay(g.tokens.map((t) => t.pos)); return }
    const moves = fresh.filter((b) => b.kind === 'move' && b.path.length > 0)
    for (const b of moves) {
      busy.current++
      setZoom(b.path[b.path.length - 1])
      b.path.forEach((space, k) => timers.current.push(setTimeout(() => {
        setDisplay((d) => d.map((v, i) => (i === b.token ? space : v)))
        sfx.hop(k)
        if (k === b.path.length - 1) {
          timers.current.push(setTimeout(() => { busy.current = Math.max(0, busy.current - 1); if (busy.current === 0) setZoom(null) }, 700))
        }
      }, k * HOP_MS)))
    }
    // Anything else that moved a piece (Timeout, a bankruptcy, a restore) snaps straight there.
    if (moves.length === 0 && busy.current === 0) setDisplay(g.tokens.map((t) => t.pos))
  }, [g, enabled])
  useEffect(() => () => timers.current.forEach(clearTimeout), [])
  return { display, zoom }
}
```

- [ ] **Step 3: CSS for the 3D layer**

Append to `controller/src/tv/turf.css`:

```css
/* ---- 3D stage: the canvas fills the stage behind the rails; the well panel floats over the middle of the board ---- */
.turf-stage.is3d .turf-board-wrap { pointer-events: none; }
.turf-3d { position: absolute; inset: 0; z-index: 0; background: #241710; }
.turf-3d canvas { display: block; width: 100% !important; height: 100% !important; }
.turf-stage.is3d .turf-rail { position: relative; z-index: 2; }
.turf-well3d { position: absolute; left: 50%; top: 47%; width: 660px; height: 400px; transform: translate(-50%, -50%); z-index: 1; pointer-events: none; }
.turf-well3d .well { inset: 0; }
.turf-banner3d { position: absolute; top: 7%; left: 0; right: 0; text-align: center; z-index: 3; pointer-events: none;
  font-family: 'Rammetto One', var(--font-display); font-size: 120px; line-height: 1; color: #fff; text-shadow: 0 6px 0 #1a1a1a; }
```

- [ ] **Step 4: Add gallery fixtures**

In `controller/src/tv/TurfGallery.tsx`, add two entries inside `BEATS` (after the existing `move` entry): a lineup of all six pieces on Payday and a rolled move (`4 + 5 = 9` spaces along the existing `path`):

```tsx
  lineup: { g: { ...base, phase: 'roll', tokens: withTokens(() => ({ pos: 0 })) } },
  'diced-move': { g: { ...base, phase: 'move', timed: false }, later: [beat('roll', { token: 3, dice: [4, 5, 1] }), beat('move', { token: 3, space: 23, path: [15, 16, 17, 18, 19, 20, 21, 22, 23] })], after: { tokens: withTokens((_, i) => (i === 3 ? { pos: 23 } : {})) } },
```

- [ ] **Step 5: Type-check, build, and look at it**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vite build 2>&1 | grep -E "built|rror" && npx vitest run`
Expected: no tsc output; `✓ built in`; all vitest tests pass; the build lists a separate `TurfStage3D-*.js` chunk.

Start the preview server if it is not running (`npx vite preview --port 4173 --strictPort --host 127.0.0.1` in the background) and open `http://127.0.0.1:4173/tv?gallery=turf&beat=lineup` at 1920x1080. Check by eye: (1) the six drinks stand on coasters at Payday; (2) tile names are upright and readable, and "HOME TURF" clears its border; (3) the board's bottom edge is at least 24 px clear of both rails, and if it overlaps, raise the `WIDE` position in `camera.ts` (`[0, 14, 15.4]`) by 8% steps until it clears, and update `camera.test.ts` only if a tested value changes; (4) the well panel in `beat=roll` sits inside the middle of the board without spilling over the tile ring, and if it spills, adjust `.turf-well3d` width and height until it fits.

- [ ] **Step 6: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/TurfStage3D.tsx controller/src/tv/TurfStage.tsx controller/src/tv/TurfGallery.tsx controller/src/tv/turf.css
git commit -m "feat(turf3d): the 3D stage in the TV, with a lost-context fallback, skip key and gallery fixtures"
```

---

### Task 12: Verification script, measurement, cleanup and docs

**Files:**
- Create: `controller/scripts/turf3d-shots.mjs`
- Delete: `controller/src/tv/spike3d/` (whatever remains), the spike route in `controller/src/tv/Gallery.tsx`
- Modify: `controller/src/tv/turf3d/quality.ts` (only if the measurement says so), `README.md`, `docs/superpowers/specs/2026-09-29-home-turf-3d-design.md` (only if the default quality changes)

**Interfaces:**
- Consumes: the gallery beats `lineup`, `roll`, `diced-move`, `rent`, `jail`, `bankrupt` (all exist in `TurfGallery.tsx`).

- [ ] **Step 1: Write the screenshot and frame-rate script**

```js
// controller/scripts/turf3d-shots.mjs
// Screenshots every Home Turf 3D gallery beat at 1920x1080 and measures the frame rate over a rolled move.
// Usage (with `npx vite preview --port 4173` running, and nothing else heavy on the Mac):
//   node scripts/turf3d-shots.mjs            -> shots in ./turf3d-shots and one fps line per quality level
//   EXTRA='&quality=high' node scripts/turf3d-shots.mjs
import { mkdirSync } from 'node:fs'
import { chromium } from '@playwright/test'

const base = process.env.BASE ?? 'http://127.0.0.1:4173'
const out = process.env.OUT ?? 'turf3d-shots'
const extra = process.env.EXTRA ?? ''
mkdirSync(out, { recursive: true })

const browser = await chromium.launch({ args: ['--use-angle=metal', '--enable-gpu', '--ignore-gpu-blocklist'] })
const open = async (beat, query = '') => {
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } })
  page.errors = []
  page.on('pageerror', (e) => page.errors.push(String(e)))
  page.on('console', (m) => m.type() === 'error' && page.errors.push(m.text()))
  await page.goto(`${base}/tv?gallery=turf&beat=${beat}${query}`)
  return page
}

for (const beat of ['lineup', 'roll', 'diced-move', 'rent', 'jail', 'bankrupt']) {
  const page = await open(beat, extra)
  await page.waitForTimeout(3500)
  await page.screenshot({ path: `${out}/${beat}.png` })
  console.log(beat.padEnd(11), page.errors.length ? `ERRORS ${page.errors.join(' | ')}` : 'ok')
  await page.close()
}

for (const quality of ['high', 'balanced', 'low']) {
  const page = await open('diced-move', `&quality=${quality}`)
  await page.evaluate(() => {
    window.__ft = []
    let last = performance.now()
    const tick = () => { const n = performance.now(); window.__ft.push(n - last); last = n; requestAnimationFrame(tick) }
    requestAnimationFrame(tick)
  })
  await page.waitForTimeout(9000) // the fixture injects the roll at 0.7 s; a 9-space Theatre move takes about 6.5 s
  const r = await page.evaluate(() => {
    const a = window.__ft.slice(5).sort((x, y) => x - y)
    const avg = a.reduce((s, v) => s + v, 0) / a.length
    return { fps: Math.round(1000 / avg), p95ms: Math.round(a[Math.floor(a.length * 0.95)]), worstMs: Math.round(a[a.length - 1]) }
  })
  console.log(`fps ${quality.padEnd(8)}`, JSON.stringify(r), page.errors.length ? `ERRORS ${page.errors.join(' | ')}` : 'ok')
  await page.close()
}
await browser.close()
```

- [ ] **Step 2: Run it on a calm machine and read the shots**

Run: `cd /Users/jjahn/HoopDreams/controller && uptime && node scripts/turf3d-shots.mjs`
Expected: `ok` for all six beats and three `fps` lines with no `ERRORS`. If the load average is above 5, wait and re-run: a busy Mac makes the fps numbers meaningless.

Look at `turf3d-shots/diced-move.png`, `lineup.png`, `jail.png` and `bankrupt.png`. Confirm: pieces are on the right spaces for the fixture, a bankrupt token's drink is gone, the jailed token sits on the Timeout corner, and there are no console errors. A screenshot taken at the fixed 3.5 s mark may catch the diced move mid-walk; that is fine.

- [ ] **Step 3: Decide the default quality from the numbers**

If `high` shows `fps >= 50` and `p95ms <= 25`, set `DEFAULT_QUALITY` to `'high'` in `controller/src/tv/turf3d/quality.ts`, change the `parseQuality('')` expectation in `quality.test.ts` to `'high'`, and change the spec's Performance bullet to say High is the default. Otherwise leave `'balanced'` and record the three measured lines in the commit message.

- [ ] **Step 4: Remove the spike**

```bash
cd /Users/jjahn/HoopDreams/controller
git rm -r --ignore-unmatch src/tv/spike3d
python3 - <<'EOF'
p='src/tv/Gallery.tsx'
s=open(p).read()
s=s.replace("const Spike3D = lazy(() => import('./spike3d/Spike3D').then(m => ({ default: m.Spike3D })))\n","")
s=s.replace("  if (params.get('gallery') === 'spike3d') return <Suspense fallback={null}><Spike3D /></Suspense>\n","")
open(p,'w').write(s)
EOF
grep -n "spike3d\|Spike3D" src/tv/Gallery.tsx || echo "spike route gone"
```
Expected: `spike route gone`.

- [ ] **Step 5: Document it**

In `README.md`, add a short section after the Home Turf description (find it with `grep -n "Home Turf" README.md`):

```markdown
**Home Turf in 3D.** On the TV the board is a lit tabletop with drink pieces (soju, vodka, beer bottle, beer can, shot glass,
red cup) on colored coasters. Rolled moves get a dice beat and a slowed, close-up finish; **S** in the lobby (or the captain's
"Show" setting) switches between Theatre and Quick, and Space or Enter skips the animation in progress. Add `?quality=low`
to the TV address on a slow machine, or `?board=2d` for the flat board. Close spare browser tabs: two 3D pages share one GPU.
```

- [ ] **Step 6: Run everything**

Run:
```bash
cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run && npx vite build 2>&1 | grep -E "built|rror"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd /Users/jjahn/HoopDreams/tv && ./gradlew :engine:test :server:test 2>&1 | tail -6
cd /Users/jjahn/HoopDreams/controller && npx playwright test e2e/turf.spec.ts 2>&1 | tail -8
```
Expected: no tsc output; all vitest tests pass; build succeeds; Gradle BUILD SUCCESSFUL; the Playwright Home Turf spec passes. If the Playwright spec times out waiting for a buy prompt, raise that `expect`'s timeout to `15_000` (the Theatre move phase is about 5 to 6 s where it used to be about 2 s); phones-only specs do not render the 3D board.

- [ ] **Step 7: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add -A controller/scripts controller/src README.md docs
git commit -m "feat(turf3d): screenshot and fps script, remove the spike, document the 3D board"
```

---

## Deferred from the spec (for Plans 2 and 3)

- Physics dice with engine-result reconciliation, the settle nudge, and doubles flourish (Plan 2). Plan 1 shows the dice as a camera shot and a total banner only; `@react-three/rapier` stays installed but is not imported.
- Landing moments: rent coin stream, deed rise, tax burst, card flip, Timeout bars, Payday rain, bankrupt tip-over, house pop-in (Plan 2).
- Engine piece ids renamed to the six drinks, the phone picker and token-card drawings, saved-party id mapping (Plan 3).
- Shader pre-warm, a memory soak test, the optional new audio cues (Plan 3).
- A captain-phone skip tap (needs a TV-only channel; Space or Enter on the TV works today).
