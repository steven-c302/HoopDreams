# Home Turf 3D, Plan 2: physics dice and the landing moments

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the 3D Home Turf board, two real physics dice tumble and always land on the engine's numbers, and landings get their own moments: rent coins, a tax burst, a Payday rain, a card flip, a hopeful deed over the tile, houses that pop in, a Timeout cage, a bankrupt drink tipping off the board, and a lifted, glowing active piece.

**Architecture:** Each roll is simulated once, headless, with Rapier (seeded by the roll's beat number). The whole trajectory is recorded and the scene simply replays it, so the replay is identical by construction. The die faces are then relabelled by a real cube rotation so the engine's number ends up on top. Landing moments are new timeline cues (`dice`, `moment`) that flow into the existing craft state and are rendered by small R3F components. The engine is not touched and no new audio is added (every sound already plays from the existing beat handler).

**Tech Stack:** react-three-fiber 9, drei, three, `@dimforge/rapier3d-compat` (used directly, no React wrapper), vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-29-home-turf-3d-design.md` (milestones 3 and 4, plus the Plan 1 review's deferred rows: turn-start lift and the buy/auction tile highlight). Plan 1 is `docs/superpowers/plans/2026-09-29-home-turf-3d-scene-and-walk.md` and is merged. Plan 3 (piece-id rename, pre-warm, soak test, audio) follows.

**Design note (a change from the spec's wording):** the spec says the visible dice "run the identical throw" of a hidden simulation. This plan records the simulation and replays the recording instead of running a second live physics world. It gives the same guarantee (the replay cannot differ from the simulation), needs no physics inside React, and is unit-testable in Node.

## Global Constraints

Carried from the spec and Plan 1; every task's requirements include these.

- Labels stay generic: no brand names on any drink or card (deny-list Smirnoff, Absolut, Jinro, Chamisul, Budweiser, Heineken, Solo).
- Nothing loads from a network at runtime; nothing 3D loads for other games (the module stays behind `React.lazy`; Rapier is loaded with a dynamic `import()` from inside it).
- The engine (Kotlin) is not changed in this plan, and no new audio is added: existing cues (`sfx.diceRoll`, `payday`, `register`, `cardDraw`, `jailClang`, `hammer`) already fire from `useBeatSounds`.
- WebGL2 missing, context lost, or a Rapier failure: the TV keeps working (2D fallback, or the show without dice); the dice banner still appears.
- Colour + shape + text: every effect is decoration on top of information the DOM and the rails already carry.
- Pacing is unchanged: `DICE_MS = 2600` stays the dice theatre's length, so the Kotlin formula and its shared tests do not change. The banner now appears at 1800 ms, after the dice settle.
- Performance target: 50 fps or better at 1080p on an Apple-silicon Mac at the default `balanced` quality; effects use instancing and reuse objects.
- Commit messages carry no attribution lines. Gradle (not needed here) needs `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`.
- The write-gate hook blocks the first write to any new file and any first edit to a file; if a tool call is blocked, state the facts it asks for and retry.

## Review Focus

Inputs the spec implies but the happy-path tests do not exercise, most likely first. Each has a test in the task named.

1. **The dice must show the engine's numbers for every possible pair, including doubles and 1 to 6 on each die, for any seed.** Tasks 2 and 3 (`labelingFor` over all 36 up/want pairs; 40 seeds by every value).
2. **A die must never come to rest cocked against the wall, stacked on the other die, or overlapping it.** Task 3 (flatness over 40 seeds).
3. **Rapier failing to load or throwing must not crash the TV or block the banner.** Task 3 (`safeThrow` returns null) and Task 5 (the dice cue still plans the banner).
4. **A landing moment for a token whose shown space is unknown, or that is bankrupt or off the board, must not produce NaN positions or a crash.** Task 4 (`coinsFor` skips it).
5. **A `payday` beat with no walk in the same update, and moments that arrive while another show is running, must still fire once, in order.** Task 5.
6. **A skip, or a reset to the resting state, must not silence later moments** (ids stay increasing after `restCraft`). Task 5.

---

## File structure

Create (under `controller/src/tv/turf3d/`):

| File | Responsibility |
| --- | --- |
| `diceFaces.ts` (+ test) | Face slots and normals, the 24 cube rotations, which slot is up, and the relabelling that puts a wanted number on top |
| `diceSim.ts` (+ test) | Loads Rapier, simulates and records one throw, retries until flat, relabels, and `safeThrow` |
| `trajectory.ts` (+ test) | Samples a recorded trajectory at a time |
| `moments.ts` (+ test) | Pure motion math for coins, the card flip, the fall, the cage drop, house pop-in, and `coinsFor` |
| `scene/Dice.tsx` | The two dice: pip materials and playback of a recorded throw |
| `scene/Moments.tsx` | Coin effects and the card flip |
| `scene/JailBars.tsx` | The cage around a jailed piece |
| `scene/Deed.tsx` | The hopeful deed floating over the tile on offer |

Modify: `timeline.ts` (+ test), `useChoreography.ts` (+ test), `scene/Pieces.tsx`, `scene/Houses.tsx`, `TurfScene.tsx`, `TurfGallery.tsx`, `TurfStage.tsx`, `controller/scripts/turf3d-shots.mjs`, `controller/package.json`, `README.md`.

---

### Task 1: Use Rapier directly

**Files:**
- Modify: `controller/package.json`, `controller/package-lock.json`

**Interfaces:**
- Produces: `@dimforge/rapier3d-compat` resolvable from `controller/src`; `@react-three/rapier` no longer a dependency.

- [ ] **Step 1: Confirm nothing imports the React wrapper**

Run: `cd /Users/jjahn/HoopDreams/controller && grep -rn "react-three/rapier" src | head`
Expected: no output.

- [ ] **Step 2: Swap the dependency**

Run: `cd /Users/jjahn/HoopDreams/controller && npm uninstall @react-three/rapier && npm install @dimforge/rapier3d-compat@0.19.2 2>&1 | tail -3 && grep -n "rapier" package.json`
Expected: `package.json` lists `"@dimforge/rapier3d-compat": "^0.19.2"` (or `0.19.2`) and no `@react-three/rapier`.

- [ ] **Step 3: Verify it still builds and the suite is green**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; `Tests  129 passed (129)`; `✓ built in`.

- [ ] **Step 4: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/package.json controller/package-lock.json
git commit -m "chore(turf3d): use Rapier directly for the dice simulation; drop the unused React wrapper"
```

---

### Task 2: Die faces and the relabelling

**Files:**
- Create: `controller/src/tv/turf3d/diceFaces.ts`
- Test: `controller/src/tv/turf3d/diceFaces.test.ts`

**Interfaces:**
- Produces:
  - `type V3 = [number, number, number]`, `type Quat = [number, number, number, number]` (x, y, z, w)
  - `SLOT_NORMALS: V3[]` (BoxGeometry material order: +x, -x, +y, -y, +z, -z), `BASE_LABELS: number[]` (`[3, 4, 1, 6, 2, 5]`)
  - `ROTATIONS: number[][]` (the 24 cube rotations as slot permutations)
  - `slotUp(q: Quat): { slot: number; alignment: number }`
  - `labelingFor(up: number, want: number): number[]`

- [ ] **Step 1: Write the failing test**

```ts
// controller/src/tv/turf3d/diceFaces.test.ts
import { describe, expect, it } from 'vitest'
import { BASE_LABELS, ROTATIONS, SLOT_NORMALS, labelingFor, slotUp, type Quat } from './diceFaces'

const axisAngle = (axis: [number, number, number], angle: number): Quat => {
  const s = Math.sin(angle / 2)
  return [axis[0] * s, axis[1] * s, axis[2] * s, Math.cos(angle / 2)]
}

/** Which way a labelled die is "handed": the sign of the triple product of the directions of faces 1, 2 and 3. */
const chirality = (labels: number[]) => {
  const at = (v: number) => SLOT_NORMALS[labels.indexOf(v)]
  const [a, b, c] = [at(1), at(2), at(3)]
  return Math.sign(a[0] * (b[1] * c[2] - b[2] * c[1]) - a[1] * (b[0] * c[2] - b[2] * c[0]) + a[2] * (b[0] * c[1] - b[1] * c[0]))
}

describe('the cube rotations', () => {
  it('are 24 distinct permutations that keep opposite faces opposite', () => {
    expect(ROTATIONS).toHaveLength(24)
    expect(new Set(ROTATIONS.map((r) => r.join(','))).size).toBe(24)
    for (const r of ROTATIONS) {
      expect([...r].sort()).toEqual([0, 1, 2, 3, 4, 5])
      for (let i = 0; i < 6; i++) expect(r[i ^ 1]).toBe(r[i] ^ 1) // slots 0/1, 2/3, 4/5 are opposite pairs
    }
  })
})

describe('slotUp', () => {
  it('finds the face pointing up for the identity and for quarter turns', () => {
    expect(slotUp([0, 0, 0, 1]).slot).toBe(2) // +y is up
    expect(slotUp(axisAngle([0, 0, 1], Math.PI)).slot).toBe(3) // flipped: -y is now up
    expect(slotUp(axisAngle([0, 0, 1], Math.PI / 2)).slot).toBe(0) // a quarter turn about z sends the +x face to point up
    expect(slotUp(axisAngle([1, 0, 0], -Math.PI / 2)).slot).toBe(4) // +z tips to +y
    expect(slotUp([0, 0, 0, 1]).alignment).toBeCloseTo(1, 9)
  })
  it('reports a poor alignment for a die balanced on an edge', () => {
    expect(slotUp(axisAngle([0, 0, 1], Math.PI / 4)).alignment).toBeLessThan(0.75)
  })
})

describe('labelingFor', () => {
  it('puts the wanted number on the up face for every up slot and every number', () => {
    for (let up = 0; up < 6; up++) for (let want = 1; want <= 6; want++) {
      expect(labelingFor(up, want)[up], `up ${up}, want ${want}`).toBe(want)
    }
  })
  it('always returns a real die: 1 to 6 once each, opposite faces summing to 7', () => {
    for (let up = 0; up < 6; up++) for (let want = 1; want <= 6; want++) {
      const l = labelingFor(up, want)
      expect([...l].sort()).toEqual([1, 2, 3, 4, 5, 6])
      for (const pair of [[0, 1], [2, 3], [4, 5]]) expect(l[pair[0]] + l[pair[1]]).toBe(7)
    }
  })
  it('never mirrors the die: the handedness of 1-2-3 is the same as a fresh die\'s', () => {
    const base = chirality(BASE_LABELS)
    expect(base).not.toBe(0)
    for (let up = 0; up < 6; up++) for (let want = 1; want <= 6; want++) expect(chirality(labelingFor(up, want))).toBe(base)
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/diceFaces.test.ts`
Expected: FAIL, cannot resolve `./diceFaces`.

- [ ] **Step 3: Write the implementation**

```ts
// controller/src/tv/turf3d/diceFaces.ts
export type V3 = [number, number, number]
/** A rotation as x, y, z, w (the order Rapier and three.js both use). */
export type Quat = [number, number, number, number]

/** The outward normal of each face, in the order BoxGeometry takes its six materials. */
export const SLOT_NORMALS: V3[] = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]]
/** The number printed on each slot of a fresh die (opposite faces sum to 7). */
export const BASE_LABELS = [3, 4, 1, 6, 2, 5]

const slotOf = (n: V3) => SLOT_NORMALS.findIndex((s) => s[0] === n[0] && s[1] === n[1] && s[2] === n[2])

/** All 24 rotations of a cube, each as a slot permutation: entry i is where slot i's face ends up. */
export const ROTATIONS: number[][] = (() => {
  const out: number[][] = []
  const perms = [[0, 1, 2], [0, 2, 1], [1, 0, 2], [1, 2, 0], [2, 0, 1], [2, 1, 0]]
  const parity = (p: number[]) => { let inv = 0; for (let i = 0; i < 3; i++) for (let j = i + 1; j < 3; j++) if (p[i] > p[j]) inv++; return inv % 2 === 0 ? 1 : -1 }
  for (const p of perms) for (let mask = 0; mask < 8; mask++) {
    const signs = [mask & 1 ? -1 : 1, mask & 2 ? -1 : 1, mask & 4 ? -1 : 1]
    if (parity(p) * signs[0] * signs[1] * signs[2] !== 1) continue // reflections are not rotations
    // The matrix sends the unit vector along axis p[k] to signs[k] along axis k... applied to each face normal:
    out.push(SLOT_NORMALS.map((n) => {
      const m: V3 = [0, 0, 0]
      for (let k = 0; k < 3; k++) m[k] = signs[k] * n[p[k]]
      return slotOf(m)
    }))
  }
  return out
})()

/** Rotates a vector by a unit quaternion. */
function rotate(q: Quat, v: V3): V3 {
  const [x, y, z, w] = q
  const tx = 2 * (y * v[2] - z * v[1]), ty = 2 * (z * v[0] - x * v[2]), tz = 2 * (x * v[1] - y * v[0])
  return [v[0] + w * tx + (y * tz - z * ty), v[1] + w * ty + (z * tx - x * tz), v[2] + w * tz + (x * ty - y * tx)]
}

/** The slot whose face points most nearly up, and how nearly (1 is perfectly flat). */
export function slotUp(q: Quat): { slot: number; alignment: number } {
  let slot = 0, alignment = -2
  SLOT_NORMALS.forEach((n, i) => { const up = rotate(q, n)[1]; if (up > alignment) { alignment = up; slot = i } })
  return { slot, alignment }
}

/**
 * The six labels for a die so that slot [up] shows [want]. The labelling is the fresh die's turned by a real cube
 * rotation, so it stays a proper die: opposite faces sum to 7 and it is never mirrored.
 */
export function labelingFor(up: number, want: number): number[] {
  const from = BASE_LABELS.indexOf(want)
  const r = ROTATIONS.find((rot) => rot[from] === up)!
  const labels: number[] = new Array(6)
  BASE_LABELS.forEach((v, i) => { labels[r[i]] = v })
  return labels
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/diceFaces.test.ts && npx tsc -b`
Expected: PASS, 6 tests; no tsc output.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/diceFaces.ts controller/src/tv/turf3d/diceFaces.test.ts
git commit -m "feat(turf3d): die faces, cube rotations and the relabelling that puts a number on top"
```

---

### Task 3: Simulate, record and relabel a throw

**Files:**
- Create: `controller/src/tv/turf3d/diceSim.ts`
- Test: `controller/src/tv/turf3d/diceSim.test.ts`

**Interfaces:**
- Consumes: `labelingFor, slotUp, type Quat` from `./diceFaces`.
- Produces:
  - `type Rapier = typeof RAPIER` (the `@dimforge/rapier3d-compat` default export), `loadRapier(): Promise<Rapier>` (dynamic import, initialised once)
  - `DT = 1 / 60`, `STEPS = 104` (a 1.73 s recording)
  - `interface Throw { steps: number; frames: [Float32Array, Float32Array]; labels: [number[], number[]]; seed: number }`; each die's frames hold `STEPS` records of `x, y, z, qx, qy, qz, qw`
  - `mulberry32(seed: number): () => number`
  - `throwFor(R: Rapier, seed: number, values: [number, number]): Throw`
  - `safeThrow(R: Rapier | null, seed: number, values: [number, number]): Throw | null`

- [ ] **Step 1: Write the failing test**

```ts
// controller/src/tv/turf3d/diceSim.test.ts
import { beforeAll, describe, expect, it } from 'vitest'
import { slotUp, type Quat } from './diceFaces'
import { STEPS, loadRapier, mulberry32, safeThrow, throwFor, type Rapier, type Throw } from './diceSim'

let R: Rapier
beforeAll(async () => { R = await loadRapier() })

const frame = (t: Throw, k: number, step: number) => Array.from(t.frames[k].slice(step * 7, step * 7 + 7))
const restQuat = (t: Throw, k: number): Quat => { const f = frame(t, k, STEPS - 1); return [f[3], f[4], f[5], f[6]] }
const restPos = (t: Throw, k: number) => { const f = frame(t, k, STEPS - 1); return [f[0], f[1], f[2]] }

describe('mulberry32', () => {
  it('repeats for the same seed and stays in [0, 1)', () => {
    const a = mulberry32(42), b = mulberry32(42)
    for (let i = 0; i < 20; i++) { const v = a(); expect(v).toBe(b()); expect(v).toBeGreaterThanOrEqual(0); expect(v).toBeLessThan(1) }
  })
})

describe('throwFor', () => {
  it('is deterministic: the same seed and numbers give the same recording and labels', () => {
    const a = throwFor(R, 7, [3, 5]), b = throwFor(R, 7, [3, 5])
    expect(Array.from(a.frames[0])).toEqual(Array.from(b.frames[0]))
    expect(Array.from(a.frames[1])).toEqual(Array.from(b.frames[1]))
    expect(a.labels).toEqual(b.labels)
  })

  it('records two dice that start above the board and come to rest', () => {
    const t = throwFor(R, 11, [2, 6])
    expect(t.steps).toBe(STEPS)
    for (const k of [0, 1]) {
      expect(frame(t, k, 0)[1]).toBeGreaterThan(1.2)
      const last = frame(t, k, STEPS - 1), before = frame(t, k, STEPS - 2)
      expect(Math.hypot(last[0] - before[0], last[1] - before[1], last[2] - before[2])).toBeLessThan(0.004)
    }
  })

  it('lands flat and shows the engine\'s numbers, for 40 seeds and every value', () => {
    for (let seed = 1; seed <= 40; seed++) {
      const values: [number, number] = [((seed * 3) % 6) + 1, ((seed * 5 + 2) % 6) + 1]
      const t = throwFor(R, seed, values)
      for (const k of [0, 1]) {
        const up = slotUp(restQuat(t, k))
        expect(up.alignment, `seed ${seed} die ${k} flat`).toBeGreaterThan(0.985)
        expect(t.labels[k][up.slot], `seed ${seed} die ${k} shows the engine's number`).toBe(values[k])
        expect(Math.abs(restPos(t, k)[1] - 0.35), `seed ${seed} die ${k} on the board`).toBeLessThan(0.03)
      }
      const [p, q] = [restPos(t, 0), restPos(t, 1)]
      expect(Math.hypot(p[0] - q[0], p[2] - q[2]), `seed ${seed} dice apart`).toBeGreaterThan(0.72)
    }
  })

  it('covers doubles and every value on a die', () => {
    for (let v = 1; v <= 6; v++) {
      const t = throwFor(R, 100 + v, [v, v])
      for (const k of [0, 1]) expect(t.labels[k][slotUp(restQuat(t, k)).slot]).toBe(v)
    }
  })
})

describe('safeThrow', () => {
  it('returns null when Rapier is not loaded', () => { expect(safeThrow(null, 1, [1, 2])).toBeNull() })
  it('returns null instead of throwing when the simulation fails', () => { expect(safeThrow({} as Rapier, 1, [1, 2])).toBeNull() })
  it('returns null for an impossible die value rather than a broken throw', () => { expect(safeThrow(R, 1, [0, 7])).toBeNull() })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/diceSim.test.ts`
Expected: FAIL, cannot resolve `./diceSim`.

- [ ] **Step 3: Write the implementation**

```ts
// controller/src/tv/turf3d/diceSim.ts
import type RAPIER from '@dimforge/rapier3d-compat'
import { labelingFor, slotUp, type Quat } from './diceFaces'

/** The Rapier physics module (loaded on demand, so nothing but the 3D stage ever pays for it). */
export type Rapier = typeof RAPIER

let loading: Promise<Rapier> | null = null
export function loadRapier(): Promise<Rapier> {
  loading ??= import('@dimforge/rapier3d-compat')
    .then(async (m) => { await m.default.init(); return m.default })
    .catch((e) => { loading = null; throw e })
  return loading
}

export const DT = 1 / 60
/** A recording is 104 frames (1.73 s); the dice are damped to rest well before it ends. */
export const STEPS = 104
const HALF = 0.35 // half the edge of a die (0.7 across)

export interface Throw {
  steps: number
  frames: [Float32Array, Float32Array]
  /** For each die, the number printed on each of its six slots, arranged so the engine's number ends up on top. */
  labels: [number[], number[]]
  seed: number
}

export function mulberry32(seed: number): () => number {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** A uniformly random rotation. */
function randomQuat(rnd: () => number): Quat {
  const [u1, u2, u3] = [rnd(), rnd(), rnd()]
  const a = Math.sqrt(1 - u1), b = Math.sqrt(u1)
  return [a * Math.sin(2 * Math.PI * u2), a * Math.cos(2 * Math.PI * u2), b * Math.sin(2 * Math.PI * u3), b * Math.cos(2 * Math.PI * u3)]
}

/** Runs one throw of two dice into the tray in the middle of the board and records every frame. */
function simulate(R: Rapier, seed: number): { frames: [Float32Array, Float32Array]; rest: [Quat, Quat] } {
  const rnd = mulberry32(seed)
  const world = new R.World({ x: 0, y: -22, z: 0 })
  world.timestep = DT
  world.createCollider(R.ColliderDesc.cuboid(6, 0.2, 6).setTranslation(0, -0.2, 0).setFriction(0.8).setRestitution(0.25))
  ;([[3.3, 0], [-3.3, 0], [0, 3.3], [0, -3.3]] as const).forEach(([x, z], i) =>
    world.createCollider(R.ColliderDesc.cuboid(i < 2 ? 0.2 : 3.5, 2, i < 2 ? 3.5 : 0.2).setTranslation(x, 2, z).setFriction(0.3).setRestitution(0.3)))
  const bodies = [0, 1].map((k) => {
    const q = randomQuat(rnd)
    const body = world.createRigidBody(R.RigidBodyDesc.dynamic()
      .setTranslation(2.6, 1.6 + k * 0.8, 0.6 + k * 0.9)
      .setRotation({ x: q[0], y: q[1], z: q[2], w: q[3] })
      .setLinvel(-8 + (rnd() - 0.5) * 2, 1, (rnd() - 0.5) * 3)
      .setAngvel({ x: (rnd() - 0.5) * 40, y: (rnd() - 0.5) * 40, z: (rnd() - 0.5) * 40 })
      .setLinearDamping(0.15).setAngularDamping(0.2))
    world.createCollider(R.ColliderDesc.cuboid(HALF, HALF, HALF).setRestitution(0.35).setFriction(0.7), body)
    return body
  })
  const frames: [Float32Array, Float32Array] = [new Float32Array(STEPS * 7), new Float32Array(STEPS * 7)]
  for (let s = 0; s < STEPS; s++) {
    if (s === 60) bodies.forEach((b) => { b.setAngularDamping(4); b.setLinearDamping(1.5) }) // settle before the recording ends
    world.step()
    bodies.forEach((b, k) => { const t = b.translation(), r = b.rotation(); frames[k].set([t.x, t.y, t.z, r.x, r.y, r.z, r.w], s * 7) })
  }
  const rest = bodies.map((b) => { const r = b.rotation(); return [r.x, r.y, r.z, r.w] as Quat }) as [Quat, Quat]
  world.free()
  return { frames, rest }
}

/** 1 for a clean throw (both dice flat on the board, still, and apart), lower or 0 otherwise. */
function flatScore(frames: [Float32Array, Float32Array], rest: [Quat, Quat]): number {
  const at = (k: number, step: number) => Array.from(frames[k].slice(step * 7, step * 7 + 3))
  let score = 1
  for (const k of [0, 1]) {
    const p = at(k, STEPS - 1), prev = at(k, STEPS - 2)
    if (Math.abs(p[1] - HALF) > 0.03) return 0 // stacked or propped up
    if (Math.hypot(p[0] - prev[0], p[1] - prev[1], p[2] - prev[2]) > 0.004) return 0 // still moving
    score = Math.min(score, slotUp(rest[k]).alignment)
  }
  const [a, b] = [at(0, STEPS - 1), at(1, STEPS - 1)]
  if (Math.hypot(a[0] - b[0], a[2] - b[2]) < 0.72) return 0 // overlapping
  return score
}

/**
 * A throw whose dice end flat, seeded so the same roll always plays the same. It tries the seed, then nearby seeds,
 * until a throw lands clean, then relabels each die so the engine's number is the one on top.
 */
export function throwFor(R: Rapier, seed: number, values: [number, number]): Throw {
  let best: { frames: [Float32Array, Float32Array]; rest: [Quat, Quat]; score: number; seed: number } | null = null
  for (let tries = 0; tries < 16; tries++) {
    const s = seed + tries * 7919
    const sim = simulate(R, s)
    const score = flatScore(sim.frames, sim.rest)
    if (!best || score > best.score) best = { ...sim, score, seed: s }
    if (score >= 0.985) break
  }
  const b = best!
  const labels = b.rest.map((q, k) => labelingFor(slotUp(q).slot, values[k])) as [number[], number[]]
  return { steps: STEPS, frames: b.frames, labels, seed: b.seed }
}

/** [throwFor], but null (so the show goes on without dice) if Rapier is missing, the numbers are bad, or anything throws. */
export function safeThrow(R: Rapier | null, seed: number, values: [number, number]): Throw | null {
  if (!R) return null
  try { return throwFor(R, seed, values) } catch { return null }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/diceSim.test.ts && npx tsc -b`
Expected: PASS, 8 tests; no tsc output. Rapier prints one "deprecated parameters for the initialization function" line; that is its own noise, not a failure. If the 40-seed flatness test fails for some seed, do not loosen the assertion: raise the retry count in `throwFor` (16) or the damping ramp and record the ruling.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/diceSim.ts controller/src/tv/turf3d/diceSim.test.ts
git commit -m "feat(turf3d): headless dice simulation that records a flat throw and relabels the faces"
```

---

### Task 4: Motion math for moments and the recording sampler

**Files:**
- Create: `controller/src/tv/turf3d/moments.ts`, `controller/src/tv/turf3d/trajectory.ts`
- Test: `controller/src/tv/turf3d/moments.test.ts`, `controller/src/tv/turf3d/trajectory.test.ts`

**Interfaces:**
- Consumes: `spacePos` from `./layout`; `type V3, Quat` from `./diceFaces`.
- Produces from `trajectory.ts`: `interface Pose { pos: V3; quat: Quat }`, `sampleTrajectory(frames: Float32Array, steps: number, tSec: number, dt: number): Pose`.
- Produces from `moments.ts`:
  - `type Moment = { type: 'rent'; from: number; to: number; amount: number } | { type: 'tax'; token: number; amount: number } | { type: 'payday'; token: number } | { type: 'card'; token: number } | { type: 'fall'; token: number }`
  - `coinArc(t, from, to, lift): V3`, `coinBurst(i, n, t, at): V3`, `coinRain(i, n, t, at): V3`
  - `flipPose(t): { rotX: number; y: number; opacity: number }`, `fallPose(t): { rotZ: number; x: number; y: number }`, `barsDrop(t): number`, `popScale(t): number`
  - `interface Coin { kind: 'arc' | 'burst' | 'rain'; t0: number; life: number; i: number; n: number; from: V3; to: V3 }`, `coinsFor(moment: Moment, shown: number[], now: number): Coin[]`, `coinPos(c: Coin, now: number): V3 | null`

- [ ] **Step 1: Write the failing tests**

```ts
// controller/src/tv/turf3d/trajectory.test.ts
import { describe, expect, it } from 'vitest'
import { sampleTrajectory } from './trajectory'

// Two frames: at the origin with no turn, then at (2, 4, 6) turned a quarter about y.
const s = Math.SQRT1_2
const frames = new Float32Array([0, 0, 0, 0, 0, 0, 1, 2, 4, 6, 0, s, 0, s])

describe('sampleTrajectory', () => {
  it('starts on the first frame and holds the last frame after the end', () => {
    expect(sampleTrajectory(frames, 2, 0, 1 / 60).pos).toEqual([0, 0, 0])
    const end = sampleTrajectory(frames, 2, 5, 1 / 60)
    expect(end.pos[0]).toBeCloseTo(2, 5); expect(end.pos[1]).toBeCloseTo(4, 5); expect(end.pos[2]).toBeCloseTo(6, 5)
    expect(end.quat[1]).toBeCloseTo(s, 5)
  })
  it('blends between frames', () => {
    const mid = sampleTrajectory(frames, 2, 1 / 120, 1 / 60)
    expect(mid.pos).toEqual([1, 2, 3])
    expect(Math.hypot(...mid.quat)).toBeCloseTo(1, 6)
  })
  it('takes the short way round when neighbouring rotations have opposite signs', () => {
    const flipped = new Float32Array([0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, -1])
    const q = sampleTrajectory(flipped, 2, 1 / 120, 1 / 60).quat
    expect(Math.abs(q[3])).toBeCloseTo(1, 6) // it stays a no-op turn, it does not spin through zero
  })
})
```

```ts
// controller/src/tv/turf3d/moments.test.ts
import { describe, expect, it } from 'vitest'
import { spacePos } from './layout'
import { barsDrop, coinArc, coinBurst, coinPos, coinRain, coinsFor, fallPose, flipPose, popScale, type Moment } from './moments'

describe('coin paths', () => {
  it('an arc runs from the payer to the owner and peaks half way', () => {
    expect(coinArc(0, [0, 1, 0], [4, 1, 2], 1.6)).toEqual([0, 1, 0])
    expect(coinArc(1, [0, 1, 0], [4, 1, 2], 1.6)[0]).toBeCloseTo(4, 9)
    const mid = coinArc(0.5, [0, 1, 0], [4, 1, 2], 1.6)
    expect(mid[0]).toBeCloseTo(2, 9); expect(mid[1]).toBeCloseTo(2.6, 9); expect(mid[2]).toBeCloseTo(1, 9)
  })
  it('a burst throws coins up and out and brings them back to the start height', () => {
    expect(coinBurst(0, 12, 0, [1, 0.5, 1])[1]).toBeCloseTo(0.5, 9)
    expect(coinBurst(0, 12, 1, [1, 0.5, 1])[1]).toBeCloseTo(0.5, 9)
    expect(coinBurst(3, 12, 0.5, [1, 0.5, 1])[1]).toBeGreaterThan(1.5)
    const a = coinBurst(0, 12, 1, [0, 0, 0]), b = coinBurst(6, 12, 1, [0, 0, 0])
    expect(Math.hypot(a[0] - b[0], a[2] - b[2])).toBeGreaterThan(1) // opposite sides of the fan
  })
  it('rain falls from three units up to the ground, scattered inside a small patch', () => {
    expect(coinRain(5, 24, 0, [2, 0.2, 2])[1]).toBeCloseTo(3.2, 9)
    expect(coinRain(5, 24, 1, [2, 0.2, 2])[1]).toBeCloseTo(0.2, 9)
    for (let i = 0; i < 24; i++) {
      const p = coinRain(i, 24, 0.5, [2, 0.2, 2])
      expect(Math.abs(p[0] - 2)).toBeLessThanOrEqual(0.75); expect(Math.abs(p[2] - 2)).toBeLessThanOrEqual(0.75)
    }
  })
})

describe('shapes over time', () => {
  it('a card flips from face-down to face-up, rises, and fades at the end', () => {
    expect(flipPose(0)).toEqual({ rotX: 0, y: 0.5, opacity: 1 })
    expect(flipPose(0.6).rotX).toBeCloseTo(Math.PI, 5)
    expect(flipPose(0.5).y).toBeGreaterThan(flipPose(0).y)
    expect(flipPose(1).opacity).toBeCloseTo(0, 9)
    expect(flipPose(0.7).opacity).toBe(1)
  })
  it('a fallen piece tips over, slides off the edge and drops below the table', () => {
    expect(fallPose(0)).toEqual({ rotZ: 0, x: 0, y: 0 })
    const end = fallPose(1)
    expect(end.rotZ).toBeCloseTo(Math.PI / 2, 9); expect(end.x).toBeGreaterThan(0.5); expect(end.y).toBeLessThan(-2)
    expect(fallPose(0.3).y).toBe(0)
  })
  it('the cage drops from above and lands at zero', () => {
    expect(barsDrop(0)).toBeCloseTo(2.2, 9); expect(barsDrop(1)).toBe(0); expect(barsDrop(2)).toBe(0)
    expect(barsDrop(0.5)).toBeLessThan(barsDrop(0.25))
  })
  it('a new house pops in from nothing, overshoots, and settles at full size', () => {
    expect(popScale(0)).toBe(0); expect(popScale(1)).toBeCloseTo(1, 9); expect(popScale(0.6)).toBeGreaterThan(1.1)
  })
})

describe('coinsFor', () => {
  const shown = [0, 5, 12, 14, 9, 36]
  const rent: Moment = { type: 'rent', from: 4, to: 1, amount: 600 }

  it('sends a rent stream from the payer\'s space to the owner\'s, staggered', () => {
    const coins = coinsFor(rent, shown, 1000)
    expect(coins).toHaveLength(14)
    expect(coins.every((c) => c.kind === 'arc')).toBe(true)
    expect(coins[0].t0).toBe(1000); expect(coins[13].t0).toBe(1000 + 13 * 45)
    const p = spacePos(9), q = spacePos(5)
    expect(coins[0].from).toEqual([p.x, 0.9, p.z]); expect(coins[0].to).toEqual([q.x, 0.9, q.z])
  })
  it('bursts at the payer for tax and rains over Payday for a pass', () => {
    const tax = coinsFor({ type: 'tax', token: 3, amount: 200 }, shown, 0)
    expect(tax).toHaveLength(12); expect(tax.every((c) => c.kind === 'burst')).toBe(true)
    const rain = coinsFor({ type: 'payday', token: 3 }, shown, 0)
    expect(rain).toHaveLength(24); expect(rain.every((c) => c.kind === 'rain')).toBe(true)
    const p = spacePos(0); expect(rain[0].from[0]).toBeCloseTo(p.x, 9)
  })
  it('makes no coins for a token whose space is unknown, and none for moments that are not coins', () => {
    expect(coinsFor({ type: 'rent', from: 9, to: 1, amount: 5 }, shown, 0)).toEqual([])
    expect(coinsFor({ type: 'tax', token: -1, amount: 5 }, shown, 0)).toEqual([])
    expect(coinsFor({ type: 'card', token: 1 }, shown, 0)).toEqual([])
    expect(coinsFor({ type: 'fall', token: 1 }, shown, 0)).toEqual([])
    expect(coinsFor(rent, [0, Number.NaN, 2, 3, 4, 5], 0)).toEqual([]) // the owner (token 1) has a NaN space: no coins, no NaN positions
  })
  it('reports no position before a coin starts or after it ends', () => {
    const [c] = coinsFor(rent, shown, 1000)
    expect(coinPos(c, 999)).toBeNull(); expect(coinPos(c, 1000 + 901)).toBeNull()
    expect(coinPos(c, 1450)).not.toBeNull()
  })
})
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/trajectory.test.ts src/tv/turf3d/moments.test.ts`
Expected: FAIL, cannot resolve `./trajectory` and `./moments`.

- [ ] **Step 3: Write the implementations**

```ts
// controller/src/tv/turf3d/trajectory.ts
import type { Quat, V3 } from './diceFaces'

export interface Pose { pos: V3; quat: Quat }

/**
 * The pose at [tSec] of a recording made at [dt] seconds per frame (seven numbers per frame: position, then rotation).
 * Positions blend linearly, rotations by normalised lerp taking the short way; after the end the last frame holds.
 */
export function sampleTrajectory(frames: Float32Array, steps: number, tSec: number, dt: number): Pose {
  const f = Math.max(0, tSec / dt)
  const i = Math.min(Math.floor(f), steps - 1), j = Math.min(i + 1, steps - 1), a = Math.min(1, f - i)
  const A = i * 7, B = j * 7
  const lerp = (x: number, y: number) => x + (y - x) * a
  const pos: V3 = [lerp(frames[A], frames[B]), lerp(frames[A + 1], frames[B + 1]), lerp(frames[A + 2], frames[B + 2])]
  let dot = 0
  for (let k = 3; k < 7; k++) dot += frames[A + k] * frames[B + k]
  const sign = dot < 0 ? -1 : 1
  const q = [0, 1, 2, 3].map((c) => lerp(frames[A + 3 + c], sign * frames[B + 3 + c]))
  const len = Math.hypot(q[0], q[1], q[2], q[3]) || 1
  return { pos, quat: [q[0] / len, q[1] / len, q[2] / len, q[3] / len] }
}
```

```ts
// controller/src/tv/turf3d/moments.ts
import type { V3 } from './diceFaces'
import { spacePos } from './layout'

/** Something worth a flourish on the board. Built from engine beats by the timeline. */
export type Moment =
  | { type: 'rent'; from: number; to: number; amount: number }
  | { type: 'tax'; token: number; amount: number }
  | { type: 'payday'; token: number }
  | { type: 'card'; token: number }
  | { type: 'fall'; token: number }

const clamp01 = (t: number) => Math.min(1, Math.max(0, t))
const easeOutCubic = (u: number) => 1 - (1 - u) ** 3

/** A coin flying [from] to [to] along a parabola that peaks [lift] above the straight line. */
export function coinArc(t: number, from: V3, to: V3, lift: number): V3 {
  const u = clamp01(t)
  return [from[0] + (to[0] - from[0]) * u, from[1] + (to[1] - from[1]) * u + Math.sin(Math.PI * u) * lift, from[2] + (to[2] - from[2]) * u]
}

/** Coin [i] of [n] thrown up and out around [at]: a fan that rises 1.4 and lands back at the start height. */
export function coinBurst(i: number, n: number, t: number, at: V3): V3 {
  const u = clamp01(t), a = (i / n) * Math.PI * 2, r = 0.9 * u
  return [at[0] + Math.cos(a) * r, at[1] + 4 * 1.4 * u * (1 - u), at[2] + Math.sin(a) * r]
}

/** Coin [i] of [n] raining onto a small patch around [at], falling faster as it goes. */
export function coinRain(i: number, n: number, t: number, at: V3): V3 {
  void n
  const u = clamp01(t)
  const col = ((i * 7919) % 97) / 97, row = ((i * 104729) % 89) / 89
  return [at[0] + (col - 0.5) * 1.5, at[1] + 3 * (1 - u * u), at[2] + (row - 0.5) * 1.5]
}

/** A card turning over: face-down (rotX 0) to face-up (π) by 60% of its life, rising, then fading out. */
export function flipPose(t: number): { rotX: number; y: number; opacity: number } {
  const u = clamp01(t), f = easeOutCubic(clamp01(u / 0.6))
  return { rotX: Math.PI * f, y: 0.5 + 1.0 * f, opacity: u < 0.8 ? 1 : 1 - (u - 0.8) / 0.2 }
}

/** A piece tipping over (first half), then sliding off and dropping below the table (second half). */
export function fallPose(t: number): { rotZ: number; x: number; y: number } {
  const u = clamp01(t)
  const tip = clamp01(u / 0.5)
  const drop = clamp01((u - 0.5) / 0.5)
  return { rotZ: (Math.PI / 2) * tip * tip, x: 0.9 * clamp01((u - 0.4) / 0.6), y: -2.2 * drop * drop }
}

/** Height of a cage dropping over a piece: 2.2 at the start, accelerating down to 0. */
export function barsDrop(t: number): number {
  const u = clamp01(t)
  return 2.2 * (1 - u * u)
}

/** Scale of something popping in: 0, overshooting to 1.18 at 60%, settling to 1. */
export function popScale(t: number): number {
  const u = clamp01(t)
  return u < 0.6 ? 1.18 * (1 - (1 - u / 0.6) ** 2) : 1.18 - 0.18 * ((u - 0.6) / 0.4)
}

export interface Coin { kind: 'arc' | 'burst' | 'rain'; t0: number; life: number; i: number; n: number; from: V3; to: V3 }

/** The coins a moment throws (none for moments that are not about money, or for a token with no known space). */
export function coinsFor(moment: Moment, shown: number[], now: number): Coin[] {
  const spot = (token: number, y: number): V3 | null => {
    const s = shown[token]
    if (s === undefined || !Number.isFinite(s) || s < 0) return null
    const p = spacePos(s)
    return [p.x, y, p.z]
  }
  switch (moment.type) {
    case 'rent': {
      const from = spot(moment.from, 0.9), to = spot(moment.to, 0.9)
      if (!from || !to) return []
      return Array.from({ length: 14 }, (_, i) => ({ kind: 'arc' as const, t0: now + i * 45, life: 900, i, n: 14, from, to }))
    }
    case 'tax': {
      const at = spot(moment.token, 0.5)
      if (!at) return []
      return Array.from({ length: 12 }, (_, i) => ({ kind: 'burst' as const, t0: now, life: 1000, i, n: 12, from: at, to: at }))
    }
    case 'payday': {
      const p = spacePos(0)
      const at: V3 = [p.x, 0.2, p.z]
      return Array.from({ length: 24 }, (_, i) => ({ kind: 'rain' as const, t0: now + i * 30, life: 1400, i, n: 24, from: at, to: at }))
    }
    default: return []
  }
}

/** Where a coin is at [now], or null before it starts and after it ends. */
export function coinPos(c: Coin, now: number): V3 | null {
  const u = (now - c.t0) / c.life
  if (u < 0 || u > 1) return null
  if (c.kind === 'arc') return coinArc(u, c.from, c.to, 1.6)
  return c.kind === 'burst' ? coinBurst(c.i, c.n, u, c.from) : coinRain(c.i, c.n, u, c.from)
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/trajectory.test.ts src/tv/turf3d/moments.test.ts && npx tsc -b`
Expected: PASS, 15 tests; no tsc output. Note `coinsFor` with `shown[token] = NaN` is skipped by the `Number.isFinite` guard, so the NaN assertion above holds by returning `[]`.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/moments.ts controller/src/tv/turf3d/moments.test.ts controller/src/tv/turf3d/trajectory.ts controller/src/tv/turf3d/trajectory.test.ts
git commit -m "feat(turf3d): motion math for coins, card flip, fall, cage and pop-in, plus the dice recording sampler"
```

---

### Task 5: Dice and moment cues, and a pure cue reducer

**Files:**
- Modify: `controller/src/tv/turf3d/timeline.ts` (replace the whole file)
- Modify: `controller/src/tv/turf3d/timeline.test.ts` (change one expectation, append tests)
- Modify: `controller/src/tv/turf3d/useChoreography.ts` (replace the whole file)
- Modify: `controller/src/tv/turf3d/useChoreography.test.ts` (append tests)

**Interfaces:**
- Consumes: `Moment` from `./moments`.
- Produces from `timeline.ts`: everything Plan 1 exported, plus `DICE_BANNER_MS = 1800`, `Moment` re-export not needed, and two new `Cue` kinds: `{ at: number; kind: 'dice'; values: [number, number]; seed: number }` and `{ at: number; kind: 'moment'; moment: Moment }`.
- Produces from `useChoreography.ts`: `Craft` gains `dice: { n: number; values: [number, number]; seed: number } | null` and `moments: (Moment & { n: number })[]`; `interface Counters { hop: number; moment: number; dice: number }`; `applyCue(cr: Craft, c: Cue, n: Counters): Craft`.

- [ ] **Step 1: Write the failing tests**

In `timeline.test.ts`, change the imports and the one expectation the new banner time breaks:

```ts
import { DICE_BANNER_MS, DICE_MS, LAND_PAD_MS, hopMs, moveDwellMs, planBeats, walkMs, type Cue } from './timeline'
```

and inside `'plays a Theatre roll: dice shot and banner first, ...'` replace

```ts
    expect(p.cues).toContainEqual({ at: 1500, kind: 'banner', text: '4 + 5 = 9' })
```
with
```ts
    expect(p.cues).toContainEqual({ at: DICE_BANNER_MS, kind: 'banner', text: '4 + 5 = 9' })
```

Then append this block to the end of the file:

```ts
describe('planBeats: dice cues and moments', () => {
  const moments = (p: { cues: Cue[] }) => p.cues.filter((c): c is Extract<Cue, { kind: 'moment' }> => c.kind === 'moment')

  it('adds a dice cue seeded by the roll beat, and a doubles banner', () => {
    const roll = beat('roll', { token: 3, dice: [4, 4, 1] })
    const p = planBeats([roll, beat('move', { token: 3, path: walk(0, 8) })], { quick: false })
    expect(p.cues).toContainEqual({ at: 0, kind: 'dice', values: [4, 4], seed: roll.seq })
    expect(p.cues).toContainEqual({ at: DICE_BANNER_MS, kind: 'banner', text: '4 + 4 = 8 DOUBLES!' })
    expect(DICE_BANNER_MS).toBeLessThan(DICE_MS)
  })

  it('skips the dice cue for a value no die can show, but still plans the banner', () => {
    const p = planBeats([beat('roll', { token: 3, dice: [0, 9, 1] }), beat('move', { token: 3, path: walk(0, 4) })], { quick: false })
    expect(p.cues.some((c) => c.kind === 'dice')).toBe(false)
    expect(p.cues.some((c) => c.kind === 'banner' && c.text === '0 + 9 = 9')).toBe(true)
  })

  it('plans no dice cue at Quick pace', () => {
    const p = planBeats([beat('roll', { token: 3, dice: [2, 3, 1] }), beat('move', { token: 3, path: walk(0, 5) })], { quick: true })
    expect(p.cues.some((c) => c.kind === 'dice')).toBe(false)
  })

  it('turns rent, tax, card and bankrupt beats into moments at the start of the update', () => {
    const p = planBeats([
      beat('rent', { token: 4, other: 1, space: 9, amount: 600 }),
      beat('tax', { token: 3, space: 4, amount: 200 }),
      beat('card', { token: 2, text: 'Last call' }),
      beat('bankrupt', { token: 4, other: 1 }),
    ], { quick: false })
    expect(moments(p).map((c) => [c.at, c.moment])).toEqual([
      [0, { type: 'rent', from: 4, to: 1, amount: 600 }],
      [0, { type: 'tax', token: 3, amount: 200 }],
      [0, { type: 'card', token: 2 }],
      [0, { type: 'fall', token: 4 }],
    ])
    expect(p.totalMs).toBe(0) // moments never hold the queue
  })

  it('fires the Payday rain as the piece reaches Payday, not at the end of the walk', () => {
    const p = planBeats([beat('move', { token: 3, path: [38, 39, 0, 1, 2, 3] }), beat('payday', { token: 3, amount: 200 })], { quick: false })
    expect(moments(p)).toEqual([{ at: 690, kind: 'moment', moment: { type: 'payday', token: 3 } }]) // three early hops of 230 ms
    const q = planBeats([beat('move', { token: 3, path: [38, 39, 0, 1, 2, 3] }), beat('payday', { token: 3, amount: 200 })], { quick: true })
    expect(moments(q)[0].at).toBe(780) // three hops of 260 ms
  })

  it('still fires a Payday moment when no walk came in the same update', () => {
    const p = planBeats([beat('payday', { token: 1, amount: 200 })], { quick: false })
    expect(moments(p)).toEqual([{ at: 0, kind: 'moment', moment: { type: 'payday', token: 1 } }])
  })

  it('queues a moment behind a walk in the same update', () => {
    const p = planBeats([beat('move', { token: 0, path: walk(0, 3) }), beat('rent', { token: 0, other: 1, space: 3, amount: 50 })], { quick: false })
    expect(moments(p)[0].at).toBe(moveDwellMs(3, false, false))
  })
})
```

In `useChoreography.test.ts`, extend the import and append:

```ts
import { applyCue, initialSeen, restCraft, snapFor, type Counters } from './useChoreography'
```

```ts
describe('applyCue', () => {
  const counters = (): Counters => ({ hop: 0, moment: 0, dice: 0 })
  const g = tv([0, 5, 9])

  it('moves a token and numbers each hop', () => {
    const n = counters()
    const a = applyCue(restCraft(g), { at: 0, kind: 'hop', token: 1, space: 6, ms: 230, height: 0.7, last: false }, n)
    expect(a.shown).toEqual([0, 6, 9]); expect(a.hop[1]).toEqual({ n: 1, ms: 230, height: 0.7, last: false })
    const b = applyCue(a, { at: 0, kind: 'hop', token: 1, space: 7, ms: 230, height: 0.7, last: true }, n)
    expect(b.hop[1]?.n).toBe(2)
  })

  it('snaps a token into place with no hop', () => {
    const c = applyCue(restCraft(g), { at: 0, kind: 'snap', token: 2, space: 10 }, counters())
    expect(c.shown[2]).toBe(10); expect(c.hop[2]).toBeNull()
  })

  it('appends moments with ever-increasing ids, keeps the last eight, and keeps counting after a reset', () => {
    const n = counters()
    let c = restCraft(g)
    for (let i = 0; i < 10; i++) c = applyCue(c, { at: 0, kind: 'moment', moment: { type: 'tax', token: 0, amount: i } }, n)
    expect(c.moments).toHaveLength(8)
    expect(c.moments.map((m) => m.n)).toEqual([3, 4, 5, 6, 7, 8, 9, 10])
    c = applyCue(restCraft(g), { at: 0, kind: 'moment', moment: { type: 'card', token: 1 } }, n) // a skip resets the craft
    expect(c.moments[0].n).toBe(11) // later moments are never ignored as already handled
  })

  it('numbers dice throws and passes the seed and values through', () => {
    const n = counters()
    const a = applyCue(restCraft(g), { at: 0, kind: 'dice', values: [2, 5], seed: 77 }, n)
    expect(a.dice).toEqual({ n: 1, values: [2, 5], seed: 77 })
    expect(applyCue(a, { at: 0, kind: 'dice', values: [1, 1], seed: 78 }, n).dice?.n).toBe(2)
  })

  it('sets the shot, banner and target, and counts landings', () => {
    const n = counters()
    let c = applyCue(restCraft(g), { at: 0, kind: 'shot', shot: 'close', focus: 6 }, n)
    c = applyCue(c, { at: 0, kind: 'banner', text: '4 + 5 = 9' }, n)
    c = applyCue(c, { at: 0, kind: 'target', space: 6 }, n)
    c = applyCue(c, { at: 0, kind: 'land', token: 1, space: 6 }, n)
    expect([c.shot, c.focus, c.banner, c.target, c.landed]).toEqual(['close', 6, '4 + 5 = 9', 6, { token: 1, space: 6, n: 1 }])
  })

  it('leaves the craft alone for a sound cue', () => {
    const c = restCraft(g)
    expect(applyCue(c, { at: 0, kind: 'sfx', name: 'hop', arg: 0 }, counters())).toBe(c)
  })
})
```

Also update the existing `'rests pieces where the engine says they are'` test in that file by adding two assertions after its existing `expect`s:

```ts
    expect(c.dice).toBeNull(); expect(c.moments).toEqual([])
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/timeline.test.ts src/tv/turf3d/useChoreography.test.ts`
Expected: FAIL (`DICE_BANNER_MS` and `applyCue` not exported; moment cues missing).

- [ ] **Step 3: Write `timeline.ts`** (replace the whole file)

```ts
// controller/src/tv/turf3d/timeline.ts
import type { TurfBeat } from '../types'
import type { Shot } from './camera'
import type { Moment } from './moments'

/** Kept in step with HomeTurf.kt (DICE_MS, LAND_PAD_MS, hopMs, moveDwellMs); both tests assert the same table. */
export const DICE_MS = 2600
/** The dice settle by about 1.73 s; the total appears just after. */
export const DICE_BANNER_MS = 1800
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
  | { at: number; kind: 'dice'; values: [number, number]; seed: number }
  | { at: number; kind: 'moment'; moment: Moment }

const isDie = (v: number) => Number.isInteger(v) && v >= 1 && v <= 6

/**
 * Turns the beats that arrived since the last update into timed cues (ms from now, sorted, ties keep insertion order).
 * A roll only counts as "diced" for the move that follows it; a turn change or Timeout clears it. Money and drama
 * beats become moments; they never lengthen the show, so they cannot hold the queue.
 */
export function planBeats(fresh: TurfBeat[], { quick }: { quick: boolean }): { cues: Cue[]; totalMs: number } {
  const cues: Cue[] = []
  let t = 0
  let roll: { dice: number[]; seq: number } | null = null
  let walk: { path: number[]; ends: number[] } | null = null
  const moment = (at: number, m: Moment) => cues.push({ at, kind: 'moment', moment: m })
  for (const b of fresh) {
    if (b.kind === 'roll') { roll = { dice: b.dice, seq: b.seq }; continue }
    if (b.kind === 'turn') { roll = null; continue }
    if (b.kind === 'jail') { roll = null; cues.push({ at: t, kind: 'snap', token: b.token, space: b.space }); continue }
    if (b.kind === 'rent') { moment(t, { type: 'rent', from: b.token, to: b.other, amount: b.amount }); continue }
    if (b.kind === 'tax') { moment(t, { type: 'tax', token: b.token, amount: b.amount }); continue }
    if (b.kind === 'card') { moment(t, { type: 'card', token: b.token }); continue }
    if (b.kind === 'bankrupt') { moment(t, { type: 'fall', token: b.token }); continue }
    if (b.kind === 'payday') {
      const i = walk ? walk.path.indexOf(0) : -1
      moment(walk && i >= 0 ? walk.ends[i] : t, { type: 'payday', token: b.token })
      continue
    }
    if (b.kind !== 'move' || b.path.length === 0) continue

    const rolled = roll
    roll = null
    const path = b.path, n = path.length, dest = path[n - 1]
    if (rolled && !quick) {
      const [a = 0, c = 0] = rolled.dice
      cues.push({ at: t, kind: 'shot', shot: 'dice', focus: null })
      if (isDie(a) && isDie(c)) cues.push({ at: t, kind: 'dice', values: [a, c], seed: rolled.seq })
      cues.push({ at: t + DICE_BANNER_MS, kind: 'banner', text: `${a} + ${c} = ${a + c}${a === c ? ' DOUBLES!' : ''}` })
      cues.push({ at: t + DICE_MS, kind: 'banner', text: null })
      t += DICE_MS
    }
    const ends: number[] = []
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
      ends.push(t)
    }
    walk = { path, ends }
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

- [ ] **Step 4: Write `useChoreography.ts`** (replace the whole file)

```ts
// controller/src/tv/turf3d/useChoreography.ts
import { useEffect, useRef, useState } from 'react'
import { sfx } from '../audio'
import type { TurfTv } from '../types'
import type { Shot } from './camera'
import type { Moment } from './moments'
import { ShowQueue, shouldSnap } from './showQueue'
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
  /** The latest dice throw to play (numbered, so the scene plays each once). */
  dice: { n: number; values: [number, number]; seed: number } | null
  /** Recent moments, oldest first, each with an id that only ever goes up (even across a skip). */
  moments: (Moment & { n: number })[]
}

const latest = (g: TurfTv) => g.beats.reduce((m, b) => Math.max(m, b.seq), 0)

/** The highest beat seq already on the board: everything at or below it is history, not something to animate. */
export const initialSeen = (g: TurfTv): number => latest(g)

export const restCraft = (g: TurfTv): Craft => ({
  shown: g.tokens.map((t) => t.pos), hop: g.tokens.map(() => null), shot: 'wide', focus: null, banner: null, target: null,
  landed: null, dice: null, moments: [],
})

/** Puts every piece back where the engine says it is (a restore, a Timeout, a new token); same object when already right. */
export function snapFor(c: Craft, g: TurfTv): Craft {
  const right = c.shown.length === g.tokens.length && c.shown.every((s, i) => s === g.tokens[i].pos)
  return right ? c : { ...c, shown: g.tokens.map((t) => t.pos), hop: g.tokens.map(() => null) }
}

/** Running ids for hops, moments and dice throws; owned by the hook so they keep climbing across a reset. */
export interface Counters { hop: number; moment: number; dice: number }

/** What one cue does to the craft. Sound cues change nothing here (the hook plays them). */
export function applyCue(cr: Craft, c: Cue, n: Counters): Craft {
  switch (c.kind) {
    case 'hop': {
      const shown = cr.shown.slice(); shown[c.token] = c.space
      const hop = cr.hop.slice(); hop[c.token] = { n: ++n.hop, ms: c.ms, height: c.height, last: c.last }
      return { ...cr, shown, hop }
    }
    case 'snap': {
      const shown = cr.shown.slice(); shown[c.token] = c.space
      const hop = cr.hop.slice(); hop[c.token] = null
      return { ...cr, shown, hop }
    }
    case 'shot': return { ...cr, shot: c.shot, focus: c.focus }
    case 'banner': return { ...cr, banner: c.text }
    case 'target': return { ...cr, target: c.space }
    case 'land': return { ...cr, landed: { token: c.token, space: c.space, n: (cr.landed?.n ?? 0) + 1 } }
    case 'dice': return { ...cr, dice: { n: ++n.dice, values: c.values, seed: c.seed } }
    case 'moment': return { ...cr, moments: [...cr.moments, { ...c.moment, n: ++n.moment }].slice(-8) }
    case 'sfx': return cr
  }
}

/**
 * Plays new engine beats as a timed show. Beats that arrive while a show is running queue behind it. Beats that skip
 * ahead of what this TV has seen (a reconnect), or amount to a huge backlog, snap straight to the engine's positions
 * instead. Any [skip] change jumps to the final state. Only used by the 3D stage; the 2D board keeps its own hops.
 */
export function useChoreography(g: TurfTv, quick: boolean, skip: number): Craft {
  const [craft, setCraft] = useState<Craft>(() => restCraft(g))
  const seen = useRef(initialSeen(g))
  const counters = useRef<Counters>({ hop: 0, moment: 0, dice: 0 })
  const gRef = useRef(g)
  gRef.current = g
  const applyRef = useRef<(c: Cue) => void>(() => undefined)
  const queue = useRef<ShowQueue | null>(null)
  if (!queue.current) queue.current = new ShowQueue((c) => applyRef.current(c), () => setCraft((cr) => snapFor(cr, gRef.current)))

  useEffect(() => {
    const fresh = g.beats.filter((b) => b.seq > seen.current)
    const before = seen.current
    seen.current = Math.max(seen.current, latest(g))
    const q = queue.current!
    if (fresh.length > 0) {
      const plan = planBeats(fresh, { quick })
      if (plan.cues.length > 0) {
        if (shouldSnap(before, fresh, plan.totalMs)) { q.skip(); setCraft(restCraft(g)); return }
        q.enqueue(plan)
        return
      }
    }
    if (!q.isBusy()) setCraft((c) => snapFor(c, g))
  }, [g, quick])

  useEffect(() => {
    if (skip === 0) return
    queue.current!.skip()
    setCraft(restCraft(gRef.current))
  }, [skip])

  useEffect(() => () => queue.current?.skip(), [])

  applyRef.current = (c: Cue) => {
    if (c.kind === 'sfx') { if (c.name === 'hop') sfx.hop(c.arg); else sfx.drumroll(c.arg); return }
    setCraft((cr) => applyCue(cr, c, counters.current))
  }

  return craft
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d && npx tsc -b`
Expected: PASS (the whole `turf3d` folder), no tsc output. If `tsc` reports `Craft` is missing `dice` or `moments` somewhere else (for example a test helper that builds a `Craft` by hand), add the two fields there.

- [ ] **Step 6: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/timeline.ts controller/src/tv/turf3d/timeline.test.ts controller/src/tv/turf3d/useChoreography.ts controller/src/tv/turf3d/useChoreography.test.ts
git commit -m "feat(turf3d): dice and moment cues, and a pure cue reducer with ids that survive a skip"
```

---

### Task 6: The physics dice in the scene

**Files:**
- Create: `controller/src/tv/turf3d/scene/Dice.tsx`
- Modify: `controller/src/tv/turf3d/TurfScene.tsx`
- Modify: `controller/src/tv/TurfGallery.tsx` (fixtures)

**Interfaces:**
- Consumes: `Craft['dice']`, `loadRapier, safeThrow, DT, type Rapier, type Throw` from `../diceSim`, `sampleTrajectory` from `../trajectory`.
- Produces: `Dice({ dice }: { dice: Craft['dice'] })`; gallery beat `doubles-move`.

No unit test covers R3F components; this task is verified by type-check plus looking at the dice.

- [ ] **Step 1: Write the dice component**

```tsx
// controller/src/tv/turf3d/scene/Dice.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef, useState } from 'react'
import * as THREE from 'three'
import { DT, loadRapier, safeThrow, type Rapier, type Throw } from '../diceSim'
import { sampleTrajectory } from '../trajectory'
import type { Craft } from '../useChoreography'

const PIPS: number[][][] = [[], [[.5, .5]], [[.25, .25], [.75, .75]], [[.25, .25], [.5, .5], [.75, .75]],
  [[.25, .25], [.75, .25], [.25, .75], [.75, .75]], [[.25, .25], [.75, .25], [.5, .5], [.25, .75], [.75, .75]],
  [[.25, .25], [.75, .25], [.25, .5], [.75, .5], [.25, .75], [.75, .75]]]

/** One material per number (index 1 to 6): ivory with ink pips, and a red single pip on the one. */
function pipMaterials(): THREE.MeshPhysicalMaterial[] {
  const mats: THREE.MeshPhysicalMaterial[] = [new THREE.MeshPhysicalMaterial()]
  for (let n = 1; n <= 6; n++) {
    const cv = document.createElement('canvas'); cv.width = cv.height = 128
    const g = cv.getContext('2d')!
    g.fillStyle = '#fbf7ee'; g.fillRect(0, 0, 128, 128)
    g.fillStyle = n === 1 ? '#e2483d' : '#1a1a1a'
    PIPS[n].forEach(([x, y]) => { g.beginPath(); g.arc(x * 128, y * 128, n === 1 ? 17 : 12, 0, Math.PI * 2); g.fill() })
    const map = new THREE.CanvasTexture(cv); map.colorSpace = THREE.SRGBColorSpace
    mats.push(new THREE.MeshPhysicalMaterial({ map, roughness: 0.3, clearcoat: 1, clearcoatRoughness: 0.08 }))
  }
  return mats
}

/**
 * Two dice that replay a recorded throw. The recording was made by a headless simulation seeded by the roll and
 * relabelled so the engine's numbers end up on top, so what plays is what the game decided. If Rapier is not ready
 * or fails, no dice appear and the show (and the total banner) carries on.
 */
export function Dice({ dice }: { dice: Craft['dice'] }) {
  const groups = useRef<(THREE.Group | null)[]>([null, null])
  const rapier = useRef<Rapier | null>(null)
  const play = useRef<{ throw: Throw; t0: number } | null>(null)
  const pips = useMemo(pipMaterials, [])
  const [labels, setLabels] = useState<number[][] | null>(null)

  useEffect(() => {
    let alive = true
    loadRapier().then((r) => { if (alive) rapier.current = r }, () => undefined)
    return () => { alive = false }
  }, [])
  useEffect(() => () => pips.forEach((m) => { m.map?.dispose(); m.dispose() }), [pips])

  useEffect(() => {
    if (!dice) return
    const t = safeThrow(rapier.current, dice.seed, dice.values)
    if (!t) return
    play.current = { throw: t, t0: performance.now() }
    setLabels(t.labels)
  }, [dice?.n])

  useFrame(() => {
    const p = play.current
    if (!p || !labels) return
    const elapsed = (performance.now() - p.t0) / 1000
    for (let k = 0; k < 2; k++) {
      const g = groups.current[k]
      if (!g) continue
      const s = sampleTrajectory(p.throw.frames[k], p.throw.steps, elapsed, DT)
      g.position.set(s.pos[0], s.pos[1], s.pos[2]); g.quaternion.set(s.quat[0], s.quat[1], s.quat[2], s.quat[3]); g.visible = true
    }
  })

  return (
    <>
      {[0, 1].map((k) => (
        <group key={k} ref={(g) => { groups.current[k] = g }} visible={false}>
          <mesh castShadow material={labels ? labels[k].map((v) => pips[v]) : pips.slice(1, 7)}>
            <boxGeometry args={[0.7, 0.7, 0.7]} />
          </mesh>
        </group>
      ))}
    </>
  )
}
```

- [ ] **Step 2: Put the dice in the scene**

In `controller/src/tv/turf3d/TurfScene.tsx` add the import and the element:

```tsx
import { Dice } from './scene/Dice'
```

```tsx
      <Effects craft={craft} />
      <Dice dice={craft.dice} />
      <Post />
```

- [ ] **Step 3: Add the doubles fixture**

In `controller/src/tv/TurfGallery.tsx`, add this entry inside `BEATS` (next to the existing `'diced-move'`):

```tsx
  'doubles-move': { g: { ...base, phase: 'move', timed: false }, later: [beat('roll', { token: 3, dice: [4, 4, 1] }), beat('move', { token: 3, space: 22, path: [15, 16, 17, 18, 19, 20, 21, 22] })], after: { tokens: withTokens((_, i) => (i === 3 ? { pos: 22 } : {})) } },
```

- [ ] **Step 4: Type-check, build, and look at the dice**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; all tests pass; `✓ built in`; the build output shows Rapier in its own chunk (a separate `rapier*.js` asset), not in the main chunk.

Start the preview server if it is not running (`npx vite preview --port 4173 --strictPort --host 127.0.0.1` in the background), then capture two frames of the dice with this scratch script (it lives outside the repo):

```bash
SP=${TMPDIR:-/tmp}; cat > $SP/dice-look.cjs <<'EOF'
const { createRequire } = require('module')
const { chromium } = createRequire('/Users/jjahn/HoopDreams/controller/package.json')('@playwright/test')
;(async () => {
  const b = await chromium.launch({ args: ['--use-angle=metal', '--enable-gpu', '--ignore-gpu-blocklist'] })
  for (const [beat, ms] of [['doubles-move', 2300], ['doubles-move', 3400]]) {
    const p = await b.newPage({ viewport: { width: 1920, height: 1080 } })
    const errs = []; p.on('pageerror', (e) => errs.push(String(e))); p.on('console', (m) => m.type() === 'error' && errs.push(m.text()))
    await p.goto(`http://127.0.0.1:4173/tv?gallery=turf&beat=${beat}`); await p.waitForTimeout(ms)
    await p.screenshot({ path: `${process.argv[2]}/${beat}-${ms}.png` }); console.log(beat, ms, errs.length ? errs.slice(0, 3) : 'ok'); await p.close()
  }
  await b.close()
})()
EOF
mkdir -p $SP/dice-shots && cd /Users/jjahn/HoopDreams/controller && node $SP/dice-look.cjs $SP/dice-shots
```
Look at both images. Expected: at 2300 ms two ivory dice are mid-tumble over the middle of the board (the well panel is faded out because the shot is `dice`); at 3400 ms they rest flat, and the banner reads "4 + 4 = 8 DOUBLES!". Read the two top faces: each must show four pips. If no dice appear, check the console line printed by the script and that `loadRapier()` resolved before the roll (the stage preloads it in Task 8; until then a roll in the first second after load may have none, so wait a moment after the page opens).

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/scene/Dice.tsx controller/src/tv/turf3d/TurfScene.tsx controller/src/tv/TurfGallery.tsx
git commit -m "feat(turf3d): physics dice that replay a recorded throw and always show the engine's numbers"
```

---

### Task 7: Landing moments: coins, the card flip, the cage, the fall, and the lifted active piece

**Files:**
- Create: `controller/src/tv/turf3d/scene/Moments.tsx`, `controller/src/tv/turf3d/scene/JailBars.tsx`
- Modify: `controller/src/tv/turf3d/scene/Pieces.tsx` (replace the whole file)
- Modify: `controller/src/tv/turf3d/TurfScene.tsx`
- Modify: `controller/src/tv/TurfGallery.tsx` (fixtures)

**Interfaces:**
- Consumes: `coinsFor, coinPos, flipPose, fallPose, barsDrop, type Coin` from `../moments`; `Craft`; `spacePos, tileOf, crowdIndex, crowdSlot` from `../layout`.
- Produces: `CoinFx({ craft })`, `CardFlips({ craft })` (both from `Moments.tsx`), `JailBars()`, and a `Pieces` that also handles falling, cages and the active-piece lift.

- [ ] **Step 1: The coin effects and the card flip**

```tsx
// controller/src/tv/turf3d/scene/Moments.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef } from 'react'
import * as THREE from 'three'
import type { V3 } from '../diceFaces'
import { spacePos } from '../layout'
import { coinPos, coinsFor, flipPose, type Coin } from '../moments'
import type { Craft } from '../useChoreography'

const MAX_COINS = 72

/** Gold coins for rent (a stream from payer to owner), tax (a burst) and Payday (a rain), all in one instanced mesh. */
export function CoinFx({ craft }: { craft: Craft }) {
  const mesh = useRef<THREE.InstancedMesh>(null)
  const coins = useRef<Coin[]>([])
  const handled = useRef(0)
  const dummy = useMemo(() => new THREE.Object3D(), [])

  useEffect(() => {
    const now = performance.now()
    for (const m of craft.moments) {
      if (m.n <= handled.current) continue
      handled.current = m.n
      coins.current.push(...coinsFor(m, craft.shown, now))
    }
    coins.current = coins.current.slice(-MAX_COINS)
  }, [craft.moments])

  useFrame(() => {
    const inst = mesh.current
    if (!inst) return
    const now = performance.now()
    coins.current = coins.current.filter((c) => now < c.t0 + c.life)
    for (let i = 0; i < MAX_COINS; i++) {
      const c = coins.current[i]
      const p = c ? coinPos(c, now) : null
      if (p) { dummy.position.set(p[0], p[1], p[2]); dummy.rotation.set(now / 120 + i, now / 90, 0); dummy.scale.setScalar(1) }
      else { dummy.position.set(0, -10, 0); dummy.scale.setScalar(0) }
      dummy.updateMatrix()
      inst.setMatrixAt(i, dummy.matrix)
    }
    inst.instanceMatrix.needsUpdate = true
  })

  return (
    <instancedMesh ref={mesh} args={[undefined, undefined, MAX_COINS]} frustumCulled={false}>
      <cylinderGeometry args={[0.1, 0.1, 0.03, 20]} />
      <meshStandardMaterial color="#ffc94a" metalness={0.9} roughness={0.25} emissive="#7a4d00" emissiveIntensity={0.4} />
    </instancedMesh>
  )
}

function cardTexture(): THREE.CanvasTexture {
  const cv = document.createElement('canvas'); cv.width = 256; cv.height = 360
  const g = cv.getContext('2d')!
  g.fillStyle = '#fff3c9'; g.fillRect(0, 0, 256, 360)
  g.strokeStyle = '#1a1a1a'; g.lineWidth = 12; g.strokeRect(6, 6, 244, 348)
  g.fillStyle = '#1a1a1a'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.font = '220px Anton, sans-serif'; g.fillText('?', 128, 190)
  const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace
  return t
}

/** A card that turns over above the piece that drew it (the words are on the DOM well; this is the flourish). */
export function CardFlips({ craft }: { craft: Craft }) {
  const group = useRef<THREE.Group>(null)
  const pivot = useRef<THREE.Group>(null)
  const mats = useRef<(THREE.MeshBasicMaterial | null)[]>([null, null])
  const flip = useRef<{ t0: number; at: V3 } | null>(null)
  const handled = useRef(0)
  const face = useMemo(cardTexture, [])
  useEffect(() => () => face.dispose(), [face])

  useEffect(() => {
    for (const m of craft.moments) {
      if (m.n <= handled.current) continue
      handled.current = m.n
      if (m.type !== 'card') continue
      const s = craft.shown[m.token]
      if (s === undefined || !Number.isFinite(s)) continue
      const p = spacePos(s)
      flip.current = { t0: performance.now(), at: [p.x, 0, p.z] }
    }
  }, [craft.moments])

  useFrame(() => {
    const g = group.current, pv = pivot.current, f = flip.current
    if (!g || !pv) return
    const u = f ? (performance.now() - f.t0) / 1800 : 2
    g.visible = !!f && u >= 0 && u <= 1
    if (!g.visible || !f) return
    const pose = flipPose(u)
    g.position.set(f.at[0], pose.y, f.at[2])
    pv.rotation.x = Math.PI - pose.rotX // starts face-down, ends face-up
    mats.current.forEach((m) => { if (m) m.opacity = pose.opacity })
  })

  return (
    <group ref={group} visible={false} rotation={[-0.35, 0, 0]}>
      <group ref={pivot}>
        <mesh><planeGeometry args={[0.7, 0.98]} /><meshBasicMaterial ref={(m) => { mats.current[0] = m }} map={face} transparent toneMapped={false} /></mesh>
        <mesh rotation={[0, Math.PI, 0]}><planeGeometry args={[0.7, 0.98]} /><meshBasicMaterial ref={(m) => { mats.current[1] = m }} color="#e2483d" transparent toneMapped={false} /></mesh>
      </group>
    </group>
  )
}
```

- [ ] **Step 2: The cage**

```tsx
// controller/src/tv/turf3d/scene/JailBars.tsx
import { useFrame } from '@react-three/fiber'
import { useRef } from 'react'
import * as THREE from 'three'
import { barsDrop } from '../moments'

/** A cage that drops over a piece when it is sent to Timeout and stays while it is there. Mount it to drop it. */
export function JailBars() {
  const ref = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  useFrame(() => { if (ref.current) ref.current.position.y = barsDrop((performance.now() - t0.current) / 500) })
  return (
    <group ref={ref}>
      {Array.from({ length: 8 }, (_, i) => {
        const a = (i / 8) * Math.PI * 2
        return (
          <mesh key={i} position={[Math.cos(a) * 0.5, 0.6, Math.sin(a) * 0.5]} castShadow>
            <cylinderGeometry args={[0.03, 0.03, 1.2, 8]} />
            <meshStandardMaterial color="#2b2b2b" metalness={0.8} roughness={0.35} />
          </mesh>
        )
      })}
      <mesh position={[0, 1.2, 0]} rotation-x={Math.PI / 2}>
        <torusGeometry args={[0.5, 0.035, 8, 32]} />
        <meshStandardMaterial color="#2b2b2b" metalness={0.8} roughness={0.35} />
      </mesh>
    </group>
  )
}
```

- [ ] **Step 3: Rewrite the pieces** (replace the whole of `scene/Pieces.tsx`)

```tsx
// controller/src/tv/turf3d/scene/Pieces.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../types'
import { Drink } from '../Drinks'
import { crowdIndex, crowdSlot, spacePos, tileOf } from '../layout'
import { fallPose } from '../moments'
import { drinkFor } from '../pieces'
import type { Craft } from '../useChoreography'
import { JailBars } from './JailBars'

interface Motion { n: number; from: THREE.Vector3; t0: number }

const FALL_MS = 1500
/** Phases in which the turn's piece is the one everyone is waiting on. */
const WAITING_ON_TURN = new Set(['roll', 'jail', 'buy', 'manage'])

/**
 * The six drinks. They hop when the craft says so, otherwise ease to their slot on the shown space. The piece whose
 * turn it is bobs and glows, a jailed piece sits in a cage, and a bankrupt piece tips over and drops off the table.
 */
export function Pieces({ tv, craft }: { tv: TurfTv; craft: Craft }) {
  const groups = useRef<(THREE.Group | null)[]>([])
  const rings = useRef<(THREE.Mesh | null)[]>([])
  const motion = useRef<(Motion | null)[]>([])
  const falls = useRef<Record<number, number>>({})
  const handled = useRef(0)
  const landedAt = useRef(0)
  const placed = useRef<boolean[]>([])
  const alive = tv.tokens.map((t) => !t.bankrupt)
  const crowd = useMemo(() => crowdIndex(craft.shown, alive), [craft.shown, tv.tokens])

  /** Where token [k] stands (crowds spread along the tile) and how big it is there (crowds shrink). */
  const slotOf = (k: number) => {
    const space = craft.shown[k] ?? tv.tokens[k].pos
    const p = spacePos(space), slot = crowdSlot(crowd[k]?.rank ?? 0, crowd[k]?.count ?? 1, tileOf(space))
    return { pos: new THREE.Vector3(p.x + slot.dx, 0, p.z + slot.dz), scale: slot.scale }
  }

  useEffect(() => { if (craft.landed) landedAt.current = performance.now() }, [craft.landed?.n])
  useEffect(() => {
    for (const m of craft.moments) {
      if (m.n <= handled.current) continue
      handled.current = m.n
      if (m.type === 'fall') falls.current[m.token] = performance.now()
    }
  }, [craft.moments])

  useFrame(() => {
    const now = performance.now()
    tv.tokens.forEach((tok, k) => {
      const g = groups.current[k]
      if (!g) return
      const { pos: target, scale: base } = slotOf(k)
      const fallAt = falls.current[k]
      const falling = fallAt !== undefined && now - fallAt < FALL_MS
      g.visible = !tok.bankrupt || falling
      if (falling) {
        const f = fallPose((now - fallAt) / FALL_MS)
        g.position.set(target.x + f.x, f.y, target.z)
        g.rotation.z = -f.rotZ
        g.scale.setScalar(base)
        return
      }
      const active = k === tv.turn && !tok.bankrupt && WAITING_ON_TURN.has(tv.phase)
      const ring = rings.current[k]
      if (ring) {
        ring.visible = active
        if (active) (ring.material as THREE.MeshBasicMaterial).opacity = 0.5 + 0.3 * Math.sin(now / 200)
      }
      const hop = craft.hop[k]
      if (hop && motion.current[k]?.n !== hop.n) motion.current[k] = { n: hop.n, from: g.position.clone().setY(0), t0: now }
      const m = motion.current[k]
      if (hop && m && m.n === hop.n && now - m.t0 < hop.ms) {
        const t = (now - m.t0) / hop.ms, e = hop.last ? t * t * (3 - 2 * t) : t
        g.position.lerpVectors(m.from, target, e)
        g.position.y = Math.sin(Math.PI * t) * hop.height
        const stretch = 1 + Math.sin(Math.PI * t) * 0.14
        g.scale.set(base / Math.sqrt(stretch), base * stretch, base / Math.sqrt(stretch))
        g.rotation.z = Math.sin(Math.PI * t) * 0.2
      } else {
        g.position.x += (target.x - g.position.x) * 0.25
        g.position.z += (target.z - g.position.z) * 0.25
        g.position.y += ((active ? 0.12 + Math.sin(now / 260) * 0.04 : 0) - g.position.y) * 0.3
        g.scale.lerp(new THREE.Vector3(base, base, base), 0.3)
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
            if (gr && !placed.current[k]) { const s = slotOf(k); gr.position.copy(s.pos); gr.scale.setScalar(s.scale); placed.current[k] = true }
          }}
        >
          <Drink kind={drinkFor(tok.piece)} color={tok.color} />
          <mesh ref={(m) => { rings.current[k] = m }} rotation-x={-Math.PI / 2} position={[0, 0.075, 0]} visible={false}>
            <ringGeometry args={[0.46, 0.56, 48]} />
            <meshBasicMaterial color="#ffd23f" transparent toneMapped={false} />
          </mesh>
          {tok.jailed && craft.shown[k] === 10 && <JailBars />}
        </group>
      ))}
    </>
  )
}
```

- [ ] **Step 4: Put the effects in the scene**

In `controller/src/tv/turf3d/TurfScene.tsx` add the import and elements:

```tsx
import { CardFlips, CoinFx } from './scene/Moments'
```

```tsx
      <Dice dice={craft.dice} />
      <CoinFx craft={craft} />
      <CardFlips craft={craft} />
      <Post />
```

- [ ] **Step 5: Add fixtures that trigger each moment**

In `controller/src/tv/TurfGallery.tsx`, add these entries inside `BEATS` (`rent` and `jail` already exist; `bankrupt` starts with the token already gone, so it needs its own `bankrupt-fall` that starts alive):

```tsx
  tax: { g: { ...base, phase: 'manage', tokens: withTokens((_, i) => (i === 3 ? { pos: 4 } : {})) }, later: [beat('tax', { token: 3, space: 4, amount: 200 })] },
  'payday-pass': { g: { ...base, phase: 'move', timed: false, tokens: withTokens((_, i) => (i === 3 ? { pos: 37 } : {})) }, later: [beat('move', { token: 3, space: 3, path: [38, 39, 0, 1, 2, 3] }), beat('payday', { token: 3, amount: 200 })], after: { tokens: withTokens((_, i) => (i === 3 ? { pos: 3 } : {})) } },
  'card-moment': { g: { ...base, phase: 'card', timed: false, card: { deck: 'chance', deckName: 'Plot Twist', text: 'Last call! Everyone drinks.', sips: 0 } }, later: [beat('card', { token: 3, text: 'Last call! Everyone drinks.' })] },
  'bankrupt-fall': { g: { ...base, phase: 'manage' }, later: [beat('bankrupt', { token: 4, other: 1 })], after: { tokens: withTokens((_, i) => (i === 4 ? { bankrupt: true, cash: 0 } : {})) } },
  'jail-walk': { g: { ...base, phase: 'move', timed: false, tokens: withTokens((_, i) => (i === 3 ? { pos: 27 } : {})) }, later: [beat('move', { token: 3, space: 30, path: [28, 29, 30] }), beat('jail', { token: 3, space: 10 })], after: { tokens: withTokens((_, i) => (i === 3 ? { pos: 10, jailed: true } : {})) } },
```

- [ ] **Step 6: Type-check, build, and look at each moment**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; all tests pass; `✓ built in`.

Capture each with the scratch script below (timings are milliseconds after the page opens; the gallery injects the beats about 0.7 s after the stage mounts, so effects start near 1.2 s):

```bash
SP=${TMPDIR:-/tmp}; cat > $SP/moments-look.cjs <<'EOF'
const { createRequire } = require('module')
const { chromium } = createRequire('/Users/jjahn/HoopDreams/controller/package.json')('@playwright/test')
const shots = [['rent', 1900], ['tax', 1900], ['payday-pass', 2400], ['card-moment', 2100], ['bankrupt-fall', 2000], ['jail-walk', 4600], ['roll', 3500]]
;(async () => {
  const b = await chromium.launch({ args: ['--use-angle=metal', '--enable-gpu', '--ignore-gpu-blocklist'] })
  for (const [beat, ms] of shots) {
    const p = await b.newPage({ viewport: { width: 1920, height: 1080 } })
    const errs = []; p.on('pageerror', (e) => errs.push(String(e))); p.on('console', (m) => m.type() === 'error' && errs.push(m.text()))
    await p.goto(`http://127.0.0.1:4173/tv?gallery=turf&beat=${beat}`); await p.waitForTimeout(ms)
    await p.screenshot({ path: `${process.argv[2]}/${beat}.png` }); console.log(beat, errs.length ? errs.slice(0, 3) : 'ok'); await p.close()
  }
  await b.close()
})()
EOF
mkdir -p $SP/moment-shots && cd /Users/jjahn/HoopDreams/controller && node $SP/moments-look.cjs $SP/moment-shots
```
Look at each image. Expected: `rent` shows a stream of gold coins between two pieces; `tax` a fan of coins over a piece; `payday-pass` coins raining over the bottom-right corner; `card-moment` a card turning over above a piece; `bankrupt-fall` a piece tipped on its side and sliding off the edge; `jail-walk` a black cage around a piece on the Timeout corner; `roll` (the plain roll turn) the active piece lifted a little with a glowing ring. If a fixture shows nothing, its beats were injected before the 3D stage mounted; raise the `700` in `TurfGallery.tsx`'s injection `setTimeout` to `1500` and retake.

- [ ] **Step 7: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/scene controller/src/tv/turf3d/TurfScene.tsx controller/src/tv/TurfGallery.tsx
git commit -m "feat(turf3d): rent coins, tax burst, Payday rain, card flip, jail cage, bankrupt fall and the lifted active piece"
```

---

### Task 8: Houses that pop in, the deed over the tile on offer, and an early preload

**Files:**
- Create: `controller/src/tv/turf3d/scene/Deed.tsx`
- Modify: `controller/src/tv/turf3d/scene/Houses.tsx`
- Modify: `controller/src/tv/turf3d/TurfScene.tsx`
- Modify: `controller/src/tv/TurfStage.tsx`
- Modify: `controller/src/tv/TurfGallery.tsx` (one fixture)

**Interfaces:**
- Consumes: `popScale` from `../moments`; `TurfTv`, `TurfSpace` from `../../types`; `CLASSIC` and `fitSize` from `../boardTexture`; `tileOf` from `../layout`.
- Produces: `Deed({ tv })`; houses that pop when built after the stage has been open a moment; a stage that preloads the 3D chunk and Rapier during the tutorial.

- [ ] **Step 1: Make new buildings pop in**

In `controller/src/tv/turf3d/scene/Houses.tsx`, add the imports and this wrapper at the top of the file (after the existing imports):

```tsx
import { useFrame } from '@react-three/fiber'
import { useRef, type ReactNode } from 'react'
import type * as THREE from 'three'
import { popScale } from '../moments'

const LOADED = performance.now()

/** Grows its children from nothing with a little overshoot, unless they were already there when the stage opened. */
function Pop({ children }: { children: ReactNode }) {
  const ref = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  const animate = useRef(performance.now() - LOADED > 2500)
  useFrame(() => {
    const g = ref.current
    if (g) g.scale.setScalar(animate.current ? popScale((performance.now() - t0.current) / 500) : 1)
  })
  return <group ref={ref}>{children}</group>
}
```

and wrap the body of `HouseMesh` in it: change its `return (` `<group scale={0.85}> ... </group>` `)` so the outer element is `<Pop><group scale={0.85}> ... </group></Pop>`.

- [ ] **Step 2: The deed over the tile on offer**

```tsx
// controller/src/tv/turf3d/scene/Deed.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef } from 'react'
import * as THREE from 'three'
import type { TurfSpace, TurfTv } from '../../types'
import { CLASSIC, fitSize } from '../boardTexture'
import { tileOf } from '../layout'

function deedTexture(space: TurfSpace): THREE.CanvasTexture {
  const W = 256, H = 360
  const cv = document.createElement('canvas'); cv.width = W; cv.height = H
  const g = cv.getContext('2d')!
  g.fillStyle = CLASSIC.paper; g.fillRect(0, 0, W, H)
  g.fillStyle = space.color ?? '#888888'; g.fillRect(0, 0, W, 86)
  g.strokeStyle = CLASSIC.ink; g.lineWidth = 10; g.strokeRect(5, 5, W - 10, H - 10); g.lineWidth = 6; g.strokeRect(5, 86, W - 10, 1)
  g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillStyle = CLASSIC.text
  const words = space.label.toUpperCase().split(' ')
  const measure = (t: string, s: number) => { g.font = `${s}px Anton, sans-serif`; return g.measureText(t).width }
  const size = fitSize(measure, words, W - 40, 58, 24)
  g.font = `${size}px Anton, sans-serif`
  words.forEach((w, i) => g.fillText(w, W / 2, 150 + i * (size + 6)))
  g.font = '64px Anton, sans-serif'; g.fillText(`$${space.price}`, W / 2, H - 56)
  const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace
  return t
}

/** While a place is on offer (a buy decision or an auction), its deed rises off the tile and bobs above it. */
export function Deed({ tv }: { tv: TurfTv }) {
  const space = tv.phase === 'buy' ? tv.buy : tv.phase === 'auction' ? tv.auction?.space ?? -1 : -1
  const spec = space >= 0 ? tv.board[space] : undefined
  const tex = useMemo(() => (spec ? deedTexture(spec) : null), [space, spec?.label, spec?.price, spec?.color])
  useEffect(() => () => tex?.dispose(), [tex])
  const group = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  useEffect(() => { t0.current = performance.now() }, [space])

  useFrame(() => {
    const g = group.current
    if (!g) return
    g.visible = space >= 0
    if (space < 0) return
    const now = performance.now(), u = Math.min(1, (now - t0.current) / 600)
    const t = tileOf(space)
    g.position.set(t.cx, 0.3 + 0.9 * (1 - (1 - u) ** 3) + Math.sin(now / 400) * 0.04, t.cz)
  })

  return (
    <group ref={group} visible={false} rotation={[-0.6, 0, 0]}>
      {tex && (
        <mesh>
          <planeGeometry args={[0.9, 1.26]} />
          <meshBasicMaterial map={tex} side={THREE.DoubleSide} toneMapped={false} />
        </mesh>
      )}
    </group>
  )
}
```

In `controller/src/tv/turf3d/TurfScene.tsx` add the import and element:

```tsx
import { Deed } from './scene/Deed'
```

```tsx
      <CardFlips craft={craft} />
      <Deed tv={tv} />
```

- [ ] **Step 3: Preload the 3D chunk and Rapier during the tutorial**

In `controller/src/tv/TurfStage.tsx`, at the top of the exported `TurfStage` function (before its first `if`), add:

```tsx
  useEffect(() => {
    if (!wants3d(location.search, hasWebGL2())) return
    void import('./turf3d/TurfStage3D') // the board appears the moment the tutorial ends
    void import('./turf3d/diceSim').then((m) => m.loadRapier()).catch(() => undefined) // and the dice are ready for the first roll
  }, [])
```

- [ ] **Step 4: A fixture for a new building**

In `controller/src/tv/TurfGallery.tsx` add inside `BEATS`:

```tsx
  build: { g: { ...base, phase: 'manage' }, later: [beat('build', { token: 1, space: 6, amount: 4 })], after: { level: base.level.map((l, i) => (i === 6 ? 4 : l)) } },
```

- [ ] **Step 5: Type-check, build, and look**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; all tests pass; `✓ built in`.

Capture `build` (at about 1500 ms, mid-pop) and `buy` (at about 3500 ms) with the scratch script from Task 7 (edit its `shots` list to `[['build', 1500], ['buy', 3500]]`). Expected: `build` shows a new hotel growing on the sixth place along the bottom row; `buy` shows a deed card floating above the tile on offer with its colour band, name and price.

- [ ] **Step 6: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/scene controller/src/tv/turf3d/TurfScene.tsx controller/src/tv/TurfStage.tsx controller/src/tv/TurfGallery.tsx
git commit -m "feat(turf3d): houses pop in, the deed floats over the tile on offer, and the stage preloads 3D and Rapier"
```

---

### Task 9: Verification script, measurement and docs

**Files:**
- Modify: `controller/scripts/turf3d-shots.mjs`
- Modify: `README.md`

**Interfaces:**
- Consumes: the gallery beats `lineup`, `roll`, `diced-move`, `doubles-move`, `rent`, `tax`, `payday-pass`, `card-moment`, `bankrupt-fall`, `jail-walk`, `build`, `buy`.

- [ ] **Step 1: Capture every moment at the right time**

In `controller/scripts/turf3d-shots.mjs`, replace the screenshot loop (the `for (const beat of [...])` block that waits 3500 ms) with a list of `[beat, milliseconds]` pairs so the effects are caught while they play:

```js
// [beat, ms after the page opens]. The gallery injects each beat about 0.7 s after the stage mounts, so effects
// start near 1.2 s; the times below catch each one in the middle of playing.
const shots = [
  ['lineup', 3500], ['roll', 3500], ['diced-move', 2300], ['diced-move', 3400], ['doubles-move', 3400],
  ['rent', 1900], ['tax', 1900], ['payday-pass', 2400], ['card-moment', 2100], ['bankrupt-fall', 2000],
  ['jail-walk', 4600], ['build', 1500], ['buy', 3500],
]
for (const [beat, ms] of shots) {
  const page = await open(beat, extra)
  await page.waitForTimeout(ms)
  await page.screenshot({ path: `${out}/${beat}-${ms}.png` })
  console.log(`${beat}@${ms}`.padEnd(20), page.errors.length ? `ERRORS ${page.errors.join(' | ')}` : 'ok')
  await page.close()
}
```

- [ ] **Step 2: Run it on a calm machine and read the frames**

Run: `cd /Users/jjahn/HoopDreams/controller && uptime && npx vite build 2>&1 | grep -E "built|rror" && node scripts/turf3d-shots.mjs 2>&1 | tail -20`
Expected: `ok` for all 13 shots and the three `fps` lines with no `ERRORS`. If the load average is above 5, wait and re-run: a busy Mac makes the fps meaningless. The frame-rate loop now includes the dice throw and its shadows, so compare the `balanced` figure with the 59 fps measured in Plan 1: it must still be 50 or better with `p95ms` at 25 or under. If it is not, report the numbers and the frames rather than shipping quietly.

- [ ] **Step 3: Document it**

In `README.md`, in the **In 3D** bullet added by Plan 1, replace the sentence "In Theatre, a rolled move gets a dice beat and a slowed, close-up finish;" with:

```markdown
In Theatre, a rolled move gets two real tumbling dice that always show the game's numbers, and a slowed, close-up finish; landings get their own moments (rent coins, a tax burst, a Payday rain, a card flip, a cage for Timeout, a bankrupt drink tipping off the table, houses that pop in, and a deed floating over a place on offer);
```

- [ ] **Step 4: Run everything**

Run:
```bash
cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Test Files|Tests " && npx vite build 2>&1 | grep -E "built|rror"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd /Users/jjahn/HoopDreams/tv && ./gradlew :engine:test :server:test 2>&1 | grep -E "BUILD|FAILED"
cd /Users/jjahn/HoopDreams/controller && npx playwright test e2e/turf.spec.ts 2>&1 | tail -6
```
Expected: no tsc output; every unit test passes; the build succeeds; Gradle BUILD SUCCESSFUL (the engine is untouched); the Playwright Home Turf spec passes.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/scripts/turf3d-shots.mjs README.md
git commit -m "feat(turf3d): capture every landing moment in the screenshot script and document them"
```

---

## Deferred (for Plan 3 or later)

- Piece-id rename to `soju/vodka/beer/can/shot/cup` in the engine, the phone picker and token-card drawings, and the saved-party id mapping (Plan 3).
- Shader pre-warm to remove the roll hitch, a memory soak test across three back-to-back bot games, and new audio cues (glass clink, bottle tip, coin stream) (Plan 3; audio needs the user's ElevenLabs credits, so ask first).
- The 2D fallback idling for 3 to 4 s after each Theatre move, the jail-walk-after-debt dead air (`HomeTurf.kt`), and the scaled well covering some tile names (Plan 1's deferred minors that are not about dice or moments).
- The third, speed die still shows only in the DOM well; only the two number dice are physical.

