# Home Turf 3D UI rails: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat player cards on the left and right rails with 3D paper cards locked to the camera (drink, name, faces, counting cash, worth, place squares, badges, turn glow, OUT stamp), once the 3D text has synced.

**Architecture:** Pure helpers (tested) for count-up, pill flow, place-square layout, change notes and rail geometry. `Dais` gains `offsetX` and a `lit` switch so one Dais can host a whole rail with one light while the callout Daises drop theirs. A `Rails` component renders both sides once the panel text is ready; the DOM cards keep their layout but turn invisible.

**Tech Stack:** react-three-fiber 9, drei `Text`, three, vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-30-home-turf-3d-ui-rails-design.md` (builds on the merged kit, hero and dense specs).

## Global Constraints

- Engine and `TurfTv` unchanged. Nothing loads from a network. Labels generic. No new audio. Commit messages carry no attribution lines.
- Text minimums from `ui/sizing.ts`; only Latin glyphs present in the bundled fonts (a missing glyph makes troika fetch a fallback from a CDN, which the off-origin check catches; `×` is in Latin-1 and fine). At most three point lights in the scene; 50 fps or better at `balanced`.
- The DOM rails stay mounted (invisible, layout kept) and remain the 2D fallback.
- The write-gate hook blocks the first write to a new file and the first edit to a file per session: state the facts it asks for and retry. BSD `sed -i` needs an empty suffix; prefer the Edit tool or Python.

## Review Focus

1. **Six cards with faces, squares and pills must hold frame rate.** Task 3 fps loop.
2. **A long token name, many badges, or 28 owned places must not overflow a 400 px card.** Task 1 (`flowPills`, `pipPos`) and Task 3 screenshots.
3. **A bankrupt or jailed token, a team game, and an untouched new game (no places, cash unchanged)** must all read correctly. Task 3 screenshots with the existing fixtures.
4. **A cash change note must go away**, and a cash value that jumps twice quickly must not leave the text wrong. Task 2 (`CountText` always ends on the target).
5. **No off-origin request** and the DOM rails never on screen together with the 3D ones. Task 4.

---

### Task 1: Pure helpers

**Files:**
- Create: `controller/src/tv/turf3d/ui/rails.ts`, `controller/src/tv/turf3d/ui/rails.test.ts`

**Interfaces:**
- Produces: `RAIL = { w: 400, h: 250, gap: 22, dx: 728, dy: -30 }`, `countAt(from, to, u): number`, `pillWidth(text): number`, `flowPills(texts, maxW, gap?): { x: number; row: number; w: number }[]`, `pipPos(i, perRow?, step?): { x: number; row: number }`, `changeNote(before, after): { text: string; tone: 'up' | 'down' } | null`, `railSlots(count): { left: number[]; right: number[] }`, `railY(k, n): number`, `badgesFor(t): string[]`.

- [ ] **Step 1: Write the failing test**

```ts
// controller/src/tv/turf3d/ui/rails.test.ts
import { describe, expect, it } from 'vitest'
import { RAIL, badgesFor, changeNote, countAt, flowPills, pillWidth, pipPos, railSlots, railY } from './rails'

describe('countAt', () => {
  it('eases from the old value to the new one and ends exactly on it', () => {
    expect(countAt(0, 100, 0)).toBe(0); expect(countAt(0, 100, 1)).toBe(100); expect(countAt(0, 100, 0.5)).toBe(88)
    expect(countAt(500, 300, 1)).toBe(300); expect(countAt(500, 300, 0.5)).toBe(325)
  })
  it('clamps time outside 0 to 1', () => { expect(countAt(10, 20, -3)).toBe(10); expect(countAt(10, 20, 9)).toBe(20) })
})

describe('pills', () => {
  it('sizes a pill from its text, with a floor', () => { expect(pillWidth('A')).toBe(64); expect(pillWidth('IN TIMEOUT')).toBe(153) })
  it('flows pills left to right and wraps to a new row when one would overflow', () => {
    expect(flowPills(['IN TIMEOUT', '2 SETS', 'GET OUT ×1'], 370)).toEqual([{ x: 0, row: 0, w: 153 }, { x: 161, row: 0, w: 103 }, { x: 0, row: 1, w: 153 }])
    expect(flowPills([], 370)).toEqual([])
    expect(flowPills(['ONLY ONE'], 40)).toEqual([{ x: 0, row: 0, w: 128 }]) // a lone pill never wraps forever
  })
})

describe('place squares', () => {
  it('lays squares out in rows of twenty', () => {
    expect(pipPos(0)).toEqual({ x: 0, row: 0 }); expect(pipPos(19)).toEqual({ x: 342, row: 0 }); expect(pipPos(20)).toEqual({ x: 0, row: 1 }); expect(pipPos(27)).toEqual({ x: 126, row: 1 })
  })
})

describe('changeNote', () => {
  it('describes a rise or a fall in cash, and nothing when it did not change', () => {
    expect(changeNote(100, 300)).toEqual({ text: '+$200', tone: 'up' }); expect(changeNote(1500, 450)).toEqual({ text: '-$1,050', tone: 'down' }); expect(changeNote(5, 5)).toBeNull()
  })
})

describe('rail geometry', () => {
  it('sends even tokens to the left rail and odd ones to the right, as the flat rails do', () => {
    expect(railSlots(5)).toEqual({ left: [0, 2, 4], right: [1, 3] }); expect(railSlots(0)).toEqual({ left: [], right: [] }); expect(railSlots(1)).toEqual({ left: [0], right: [] })
  })
  it('stacks a rail from the top, centred a little below the middle', () => {
    expect(railY(0, 3)).toBe(242); expect(railY(1, 3)).toBe(-30); expect(railY(2, 3)).toBe(-302); expect(railY(0, 1)).toBe(-30); expect(railY(0, 2)).toBe(106); expect(railY(1, 2)).toBe(-166)
    expect(RAIL.w).toBe(400); expect(RAIL.h).toBe(250)
  })
})

describe('badgesFor', () => {
  it('lists timeout, sets and Get Out cards, and hides timeout on a bankrupt token', () => {
    expect(badgesFor({ jailed: true, bankrupt: false, sets: 2, jailCards: 1 })).toEqual(['IN TIMEOUT', '2 SETS', 'GET OUT ×1'])
    expect(badgesFor({ jailed: false, bankrupt: false, sets: 1, jailCards: 0 })).toEqual(['1 SET'])
    expect(badgesFor({ jailed: true, bankrupt: true, sets: 0, jailCards: 0 })).toEqual([])
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/rails.test.ts`
Expected: FAIL, cannot resolve `./rails`.

- [ ] **Step 3: Write the implementation**

```ts
// controller/src/tv/turf3d/ui/rails.ts
/** A rail card is 400 by 250 reference pixels; the rails sit 728 px either side of the centre, a little below the middle. */
export const RAIL = { w: 400, h: 250, gap: 22, dx: 728, dy: -30 }

/** The displayed value [u] (0 to 1) of the way from [from] to [to], easing out; ends exactly on [to]. */
export function countAt(from: number, to: number, u: number): number {
  const k = Math.min(1, Math.max(0, u))
  return Math.round(from + (to - from) * (1 - (1 - k) ** 3))
}

export const pillWidth = (text: string): number => Math.max(64, Math.round(text.length * 12.5) + 28)

/** Left edges and rows for pills laid out in a line that wraps at [maxW]. */
export function flowPills(texts: string[], maxW: number, gap = 8): { x: number; row: number; w: number }[] {
  const out: { x: number; row: number; w: number }[] = []
  let x = 0, row = 0
  for (const t of texts) {
    const w = pillWidth(t)
    if (x > 0 && x + w > maxW) { x = 0; row++ }
    out.push({ x, row, w })
    x += w + gap
  }
  return out
}

/** Where the i-th owned-place square goes: [step] px apart, [perRow] to a row. */
export function pipPos(i: number, perRow = 20, step = 18): { x: number; row: number } {
  return { x: (i % perRow) * step, row: Math.floor(i / perRow) }
}

/** The "+$200" or "-$50" note for a cash change, or null when nothing changed. */
export function changeNote(before: number, after: number): { text: string; tone: 'up' | 'down' } | null {
  if (before === after) return null
  const d = after - before
  return { text: `${d > 0 ? '+' : '-'}$${Math.abs(d).toLocaleString()}`, tone: d > 0 ? 'up' : 'down' }
}

/** Which tokens sit on which rail: even to the left, odd to the right (as the flat rails do). */
export function railSlots(count: number): { left: number[]; right: number[] } {
  const idx = Array.from({ length: count }, (_, i) => i)
  return { left: idx.filter((i) => i % 2 === 0), right: idx.filter((i) => i % 2 === 1) }
}

/** The y (reference pixels, up) of the k-th of [n] cards on a rail. */
export const railY = (k: number, n: number): number => ((n - 1) / 2 - k) * (RAIL.h + RAIL.gap) + RAIL.dy

export function badgesFor(t: { jailed: boolean; bankrupt: boolean; sets: number; jailCards: number }): string[] {
  const b: string[] = []
  if (t.jailed && !t.bankrupt) b.push('IN TIMEOUT')
  if (t.sets > 0) b.push(`${t.sets} SET${t.sets > 1 ? 'S' : ''}`)
  if (t.jailCards > 0) b.push(`GET OUT ×${t.jailCards}`)
  return b
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/rails.test.ts && npx tsc -b`
Expected: PASS; no tsc output.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/ui/rails.ts controller/src/tv/turf3d/ui/rails.test.ts
git commit -m "feat(turf3d): rail geometry, cash count-up, pill flow and place-square helpers"
```

---

### Task 2: Kit changes and the counting text

**Files:**
- Create: `controller/src/tv/turf3d/ui/CountText.tsx`
- Modify: `controller/src/tv/turf3d/ui/Dais.tsx`, `controller/src/tv/turf3d/ui/Callouts.tsx`, `controller/src/tv/turf3d/ui/Card.tsx`, `controller/src/tv/turf3d/ui/FaceIcon.tsx`, `controller/src/tv/turf3d/ui/copy.ts`, `controller/src/tv/TurfStage.tsx`

**Interfaces:**
- Consumes: `countAt` from `./rails`; `money` from `./copy`.
- Produces: `CountText({ value, px, color?, x?, y?, z?, anchorX? })`; `Dais` props `offsetX?: number` and `lit?: boolean` (default true); `Plate` prop `opacity?: number`; `FaceIcon` prop `dim?: boolean`; `Person.connected: boolean`.

- [ ] **Step 1: The counting text**

```tsx
// controller/src/tv/turf3d/ui/CountText.tsx
import { Text } from '@react-three/drei'
import { useFrame } from '@react-three/fiber'
import { useEffect, useRef } from 'react'
import { money } from './copy'
import { countAt } from './rails'
import { textPx } from './sizing'
import { FONT, INK } from './theme'

interface TroikaText { text: string; sync: () => void }

/** A dollar amount in Anton that counts to a new value over 700 ms and always ends exactly on it. */
export function CountText({ value, px, color = INK, x = 0, y = 0, z = 3, anchorX = 'left' }: { value: number; px: number; color?: string; x?: number; y?: number; z?: number; anchorX?: 'left' | 'center' | 'right' }) {
  const text = useRef<TroikaText | null>(null)
  const shown = useRef(value)
  const from = useRef(value)
  const target = useRef(value)
  const t0 = useRef(0)
  useEffect(() => {
    if (value === target.current) return
    from.current = shown.current
    target.current = value
    t0.current = performance.now()
  }, [value])
  useFrame(() => {
    if (shown.current === target.current) return
    const n = countAt(from.current, target.current, (performance.now() - t0.current) / 700)
    if (n !== shown.current && text.current) { shown.current = n; text.current.text = money(n); text.current.sync() }
    else if (n === shown.current && performance.now() - t0.current > 700) shown.current = target.current
  })
  return (
    <Text ref={text as never} font={FONT.display} fontSize={textPx('title', px)} color={color} anchorX={anchorX} anchorY="middle" position={[x, y, z]}>{money(value)}</Text>
  )
}
```

- [ ] **Step 2: The kit changes**

In `controller/src/tv/turf3d/ui/Dais.tsx`: extend the props to `{ panelKey, visible, dist = DEFAULT_DIST, offsetX = 0, offsetY = 0, lit = true, children }` (typing `offsetX?: number; lit?: boolean` alongside the others); change the position line to `tmp.set(offsetX * wpp, offsetY * wpp, -dist)`; and render the point light as `{lit && <pointLight position={[0, 140, 420]} intensity={14} distance={6} decay={2} />}`.

In `controller/src/tv/turf3d/ui/Callouts.tsx` add `lit={false}` to both `<Dais ...>` elements.

In `controller/src/tv/turf3d/ui/Card.tsx`, extend `Plate`'s props with `opacity = 1` and make its material `<meshBasicMaterial color={color} transparent={opacity < 1} opacity={opacity} toneMapped={false} />`.

In `controller/src/tv/turf3d/ui/FaceIcon.tsx` add a `dim = false` prop (typed `dim?: boolean`) and make the material `<meshBasicMaterial map={tex} transparent opacity={dim ? 0.35 : 1} toneMapped={false} />`.

In `controller/src/tv/turf3d/ui/copy.ts` add `connected: boolean` to `Person`. In `controller/src/tv/TurfStage.tsx` add `connected: p.connected` to the `faces` mapping.

- [ ] **Step 3: Type-check, test and build**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; tests pass; `✓ built in`.

- [ ] **Step 4: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src
git commit -m "feat(turf3d): a counting dollar text, and the kit changes the rails need (Dais offsetX and lit, dimmed faces, translucent plates)"
```

---

### Task 3: The rails

**Files:**
- Create: `controller/src/tv/turf3d/ui/RailCard.tsx`, `controller/src/tv/turf3d/ui/Rails.tsx`
- Modify: `controller/src/tv/turf3d/TurfScene.tsx`, `controller/src/tv/turf3d/TurfStage3D.tsx`, `controller/src/tv/TurfStage.tsx`, `controller/src/tv/turf.css`

**Interfaces:**
- Consumes: Task 1 and 2 helpers; `Dais`, `Card`, `Plate`, `Pill`, `Label`, `DrinkIcon`, `FaceIcon`, `CountText`; `SETUP_PHASES` from `./hud`.
- Produces: `RailCard({ tv, i, people, y })`, `Rails({ tv, people, ready })`; `TurfScene` prop `railsReady: boolean`.

- [ ] **Step 1: The card and the rails**

```tsx
// controller/src/tv/turf3d/ui/RailCard.tsx
import { useEffect, useMemo, useRef, useState } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../types'
import { Card, Plate, roundedRect } from './Card'
import { CountText } from './CountText'
import { money, type Person } from './copy'
import { DrinkIcon } from './DrinkIcon'
import { FaceIcon } from './FaceIcon'
import { SETUP_PHASES } from './hud'
import { Label } from './Label'
import { Pill } from './Pill'
import { RAIL, badgesFor, changeNote, flowPills, pipPos } from './rails'
import { GOLD, GREEN, INK, MUTED, PAPER, PAPER_DARK, RED } from './theme'

let pipGeo: THREE.ShapeGeometry | null = null
/** One square geometry shared by every owned-place square on every card. */
const sharedPip = () => (pipGeo ??= roundedRect(14, 14, 3))

function Pip({ x, y, color, hollow }: { x: number; y: number; color: string; hollow: boolean }) {
  const geo = useMemo(sharedPip, [])
  return (
    <group position={[x, y, 2]}>
      <mesh geometry={geo} scale={1.22}><meshBasicMaterial color={INK} toneMapped={false} /></mesh>
      <mesh geometry={geo} position={[0, 0, 0.3]}><meshBasicMaterial color={hollow ? PAPER : color} toneMapped={false} /></mesh>
    </group>
  )
}

const BADGE_FILL: Record<string, string> = { 'IN TIMEOUT': '#3b5bdb' }

/** One player's card on a rail: who, cash (counting), worth, places, badges; gold behind it on their turn, dimmed and stamped OUT when bankrupt. */
export function RailCard({ tv, i, people, y }: { tv: TurfTv; i: number; people: Person[]; y: number }) {
  const t = tv.tokens[i]
  const prev = useRef(t?.cash ?? 0)
  const [note, setNote] = useState<{ text: string; tone: 'up' | 'down' } | null>(null)
  useEffect(() => {
    if (!t) return
    const n = changeNote(prev.current, t.cash)
    prev.current = t.cash
    if (!n) return
    setNote(n)
    const id = setTimeout(() => setNote(null), 1500)
    return () => clearTimeout(id)
  }, [t?.cash]) // eslint-disable-line react-hooks/exhaustive-deps
  if (!t) return null
  const active = tv.turn === i && !SETUP_PHASES.has(tv.phase)
  const seat = t.seat ? people.find((p) => p.id === t.seat) : undefined
  const members = t.members.map((id) => people.find((p) => p.id === id)).filter((p): p is Person => !!p)
  const owned = tv.owner.map((o, s) => (o === i ? s : -1)).filter((s) => s >= 0)
  const badges = badgesFor(t)
  const flow = flowPills(badges, RAIL.w - 48)
  const left = -RAIL.w / 2 + 24
  return (
    <group position={[0, y, 0]}>
      {active && <Plate w={RAIL.w + 30} h={RAIL.h + 30} r={32} color={GOLD} z={-4} />}
      <Card w={RAIL.w} h={RAIL.h}>
        <Plate w={70} h={70} r={35} color={INK} x={-152} y={78} z={1} />
        <Plate w={62} h={62} r={31} color={t.color} x={-152} y={78} z={2} />
        <DrinkIcon piece={t.piece} color={t.color} size={44} x={-152} y={72} />
        <Label px={38} kind="title" font="display" anchorX="left" align="left" x={-102} y={96} maxWidth={250}>{t.name}</Label>
        {tv.teams
          ? members.slice(0, 5).map((p, k) => <FaceIcon key={p.id} face={p.face} color={p.color} size={p.id === t.seat ? 40 : 30} dim={!p.connected} x={-82 + k * 38} y={58} />)
          : seat && <FaceIcon face={seat.face} color={seat.color} size={40} dim={!seat.connected} x={-82} y={58} />}
        <CountText value={t.cash} px={60} x={left} y={-2} />
        <Label px={22} kind="label" font="bodyBold" color={MUTED} anchorX="right" align="right" x={RAIL.w / 2 - 24} y={-8}>{`WORTH ${money(t.worth)}`}</Label>
        {note && <Label px={28} kind="body" font="bodyBold" color={note.tone === 'up' ? GREEN : RED} anchorX="right" align="right" x={RAIL.w / 2 - 24} y={24}>{note.text}</Label>}
        {owned.length === 0 && <Label px={22} kind="label" color={MUTED} anchorX="left" align="left" x={left} y={-50}>No places yet</Label>}
        {owned.slice(0, 40).map((s, k) => {
          const p = pipPos(k)
          return <Pip key={s} x={left + 7 + p.x} y={-50 - p.row * 19} color={tv.board[s]?.color ?? '#2b2b2b'} hollow={tv.mortgaged.includes(s)} />
        })}
        {badges.map((b, k) => (
          <Pill key={b} w={flow[k].w} h={30} text={b} px={22} fill={BADGE_FILL[b] ?? (b.startsWith('GET') ? PAPER_DARK : GOLD)} textColor={BADGE_FILL[b] ? '#ffffff' : INK} x={left + flow[k].x + flow[k].w / 2} y={-104 - flow[k].row * 36} />
        ))}
        {t.bankrupt && (
          <>
            <Plate w={RAIL.w} h={RAIL.h} r={20} color={PAPER} opacity={0.62} z={20} />
            <group rotation-z={0.17} position={[70, -20, 22]}><Label px={96} kind="hero" font="display" color={RED} outline={INK}>OUT</Label></group>
          </>
        )}
      </Card>
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/Rails.tsx
import type { TurfTv } from '../../types'
import type { Person } from './copy'
import { Dais } from './Dais'
import { RailCard } from './RailCard'
import { RAIL, railSlots, railY } from './rails'

function Rail({ tv, people, side, ready }: { tv: TurfTv; people: Person[]; side: 'left' | 'right'; ready: boolean }) {
  const slots = railSlots(tv.tokens.length)
  const idx = side === 'left' ? slots.left : slots.right
  return (
    <Dais panelKey="rail" visible={ready && idx.length > 0} dist={9.4} offsetX={side === 'left' ? -RAIL.dx : RAIL.dx}>
      {() => <>{idx.map((tok, k) => <RailCard key={tok} tv={tv} i={tok} people={people} y={railY(k, idx.length)} />)}</>}
    </Dais>
  )
}

/** Both rails of player cards, hung on the camera at the screen edges; they appear once the 3D text is ready. */
export function Rails({ tv, people, ready }: { tv: TurfTv; people: Person[]; ready: boolean }) {
  return (
    <>
      <Rail tv={tv} people={people} side="left" ready={ready} />
      <Rail tv={tv} people={people} side="right" ready={ready} />
    </>
  )
}
```

- [ ] **Step 2: Wire them**

In `controller/src/tv/turf3d/TurfScene.tsx`: import `{ Rails } from './ui/Rails'`; add `railsReady: boolean` to the props; add `<Rails tv={tv} people={people} ready={railsReady} />` right after `<Callouts .../>`.

In `controller/src/tv/turf3d/TurfStage3D.tsx`: pass `railsReady={panelReady}` to `<TurfScene>`.

In `controller/src/tv/TurfStage.tsx`, in `Turf`: change the stage element to `<div className={`turf-stage ${use3d ? 'is3d' : ''} ${use3d && ready3d ? 'rails3d' : ''}`}>`.

In `controller/src/tv/turf.css`, after the `.turf-stage.is3d .turf-rail` rule add `.turf-stage.rails3d .turf-card { visibility: hidden; }`.

- [ ] **Step 3: Type-check, build, and look**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; tests pass; `✓ built in`.

Then with the preview server running (`npx vite preview --port 4173 --strictPort --host 127.0.0.1`) and the scratch `ui-look.cjs` (`SP` is the scratchpad directory): `node $SP/ui-look.cjs $SP/ui-shots '[["roll",6000],["jail",6000],["bankrupt",6000],["teamup",6500]]'` (the `roll` fixture has an active turn and six tokens; `jail` a jailed token; `bankrupt` an out token; `teamup` a team game). Look at each. Expected: six paper cards, three either side, at the same spots as the flat cards; each with a drink on a colour disc, the name, a face, big cash, WORTH at the right, place squares (hollow for mortgaged), badge pills; the active turn's card has a gold plate behind it; a bankrupt card is dimmed with a tilted OUT; no flat cards visible; everything inside its card. Console lines `ok same-origin`. Fix overlaps (the change note over WORTH, and pips over badges, are the likeliest) and retake before committing.

- [ ] **Step 4: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src
git commit -m "feat(turf3d): the player rails as camera-locked 3D paper cards"
```

---

### Task 4: Screenshots, checks, docs

**Files:**
- Modify: `controller/scripts/turf3d-shots.mjs`, `README.md`

- [ ] **Step 1: Capture rail states**

In `controller/scripts/turf3d-shots.mjs`, add `['bankrupt', 6000], ['last-lap', 6000]` to the panel shots (after `['teamup', 6000]`) if the gallery has those fixtures (it does: `bankrupt`, `last-lap`); the existing `roll@5000` and `jail@5000` shots already show the rails once they are ready, so raise those two entries to 6000.

- [ ] **Step 2: Run it and read the frames**

Run: `cd /Users/jjahn/HoopDreams/controller && uptime && npx vite build 2>&1 | grep -E "built|rror" && OUT=$SP/final-rails node scripts/turf3d-shots.mjs 2>&1 | tail -34`
Expected: `ok` for every shot with no `OFF-ORIGIN`; `balanced` at 50 fps or better with `p95ms` 25 or under (three point lights and six cards). If the frame rate drops below 50, report the numbers and the frames rather than shipping quietly. Read the rail frames for legibility.

- [ ] **Step 3: Document**

In `README.md`, in the **In 3D** bullet, change the closing sentence so it says the player rails are 3D cards too (drink, name, faces, counting cash, places, badges, gold on your turn, OUT when bankrupt), that the ticker and the game clock sit on the panel card, and that only the game logo and the podium are flat now, with the flat rails, well and panels remaining the 2D fallback.

- [ ] **Step 4: Run everything**

Run:
```bash
cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Test Files|Tests " && npx vite build 2>&1 | grep -E "built|rror"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd /Users/jjahn/HoopDreams/tv && ./gradlew :engine:test :server:test 2>&1 | grep -E "BUILD|FAILED"
cd /Users/jjahn/HoopDreams/controller && npx playwright test e2e/turf.spec.ts 2>&1 | tail -4
```
Expected: no tsc output; all tests pass; build OK; Gradle BUILD SUCCESSFUL; the Home Turf Playwright spec passes.

- [ ] **Step 5: Clean up and commit**

State the `rm -rf` facts (it deletes only the untracked `controller/turf3d-shots/` output; rollback is re-running the script) before running; the gate blocks the first attempt, so retry:

```bash
cd /Users/jjahn/HoopDreams
rm -rf controller/turf3d-shots
git add controller/scripts/turf3d-shots.mjs README.md
git commit -m "feat(turf3d): screenshot the rails in their states, and document them"
```
