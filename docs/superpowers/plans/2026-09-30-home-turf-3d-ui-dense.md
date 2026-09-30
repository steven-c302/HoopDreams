# Home Turf 3D UI dense panels: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put the debt, trade, tally and team-up panels into the 3D paper-card system, including avatar faces drawn from the existing SVG `Face`, so only the podium keeps the flat stage.

**Architecture:** Pure helpers (tested) feed small R3F panels built from the existing kit. Faces are rendered to SVG markup, their CSS variables replaced from the palette, drawn to a canvas texture, and any photo painted on top. The big (setup) card drops its footer so trade fits.

**Tech Stack:** react-three-fiber 9, drei `Text`, three, `react-dom/server` (dynamic import, faces only), vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-30-home-turf-3d-ui-dense-design.md` (builds on the merged kit and hero specs).

## Global Constraints

- Engine and `TurfTv` unchanged. Nothing loads from a network (face photos come from the app's own `/api/avatar/` endpoint). Labels generic. No new audio. Commit messages carry no attribution lines.
- Text minimums from `ui/sizing.ts`. Only glyphs present in the bundled fonts (Latin): a missing glyph makes troika fetch a fallback font from a CDN, which the off-origin check catches. 50 fps or better at `balanced`.
- The DOM well and the DOM panels stay mounted (hidden) behind the 3D card and remain the 2D fallback.
- The write-gate hook blocks the first write to a new file and the first edit to a file per session: state the facts it asks for and retry. BSD `sed -i` needs an empty suffix; prefer the Edit tool or Python.

## Review Focus

1. **Worst-case rows must fit the big card:** six tokens on the tally, eight rows in a trade, four members on a team. Tasks 3 and 4 screenshots with the six-token fixtures.
2. **A face that cannot load** (a photo 404, a failed import) must not break the panel: the face is simply absent or the preset. Task 2 (`FaceIcon` catches).
3. **A debtor with no known target, or a debt to everyone**, must read sensibly. Task 1 (`debtTo`).
4. **A trade side with nothing on it** must say "nothing". Task 1 and Task 4.
5. **No off-origin request** from any of the four panels. Task 5.

---

### Task 1: Pure helpers

**Files:**
- Modify: `controller/src/tv/turf3d/ui/copy.ts`, `controller/src/tv/turf3d/ui/copy.test.ts`
- Create: `controller/src/tv/turf3d/ui/faceTexture.ts`, `controller/src/tv/turf3d/ui/faceTexture.test.ts`

**Interfaces:**
- Produces from `copy.ts`: `debtTo(d: { to: number }, tokens: { name: string }[]): string`, `debtLine(name: string, amount: number): string`, `tradeTitle(counters: number): string`, `interface DeedTagSpec { name: string; band: string | null }`, `deedTags(spaces: number[], board: { name: string; color?: string }[], mortgaged: number[], max?: number): { tags: DeedTagSpec[]; more: number }`, `barFraction(worth: number, max: number): number`, `inkOn(hex: string): string`.
- Produces from `faceTexture.ts`: `inlineVars(svg: string, p: Palette): string`, `svgDataUrl(svg: string): string`, `photoCircle(size: number): { cx: number; cy: number; r: number; x: number; y: number; w: number }`.

- [ ] **Step 1: Write the failing tests**

Append to `controller/src/tv/turf3d/ui/copy.test.ts` (add `barFraction, debtLine, debtTo, deedTags, inkOn, tradeTitle` to its import from `./copy`):

```ts
describe('debt and trade copy', () => {
  const toks = [{ name: 'Amanda' }, { name: 'Izzy' }]
  it('says who the debt is to', () => {
    expect(debtTo({ to: 1 }, toks)).toBe('Izzy'); expect(debtTo({ to: -1 }, toks)).toBe('the bank'); expect(debtTo({ to: -2 }, toks)).toBe('everyone'); expect(debtTo({ to: 9 }, toks)).toBe('the bank')
  })
  it('states the debt', () => { expect(debtLine('Amanda', 1200)).toBe('AMANDA OWES $1,200') })
  it('titles a trade, counting counter-offers', () => { expect(tradeTitle(0)).toBe('TRADE OFFER!'); expect(tradeTitle(2)).toBe('COUNTER-OFFER #2') })
  it('lists the places in a trade, marks mortgaged ones, and caps the list', () => {
    const board = [{ name: 'A', color: '#111111' }, { name: 'B' }, { name: 'C', color: '#333333' }, { name: 'D' }]
    expect(deedTags([0, 1], board, [1])).toEqual({ tags: [{ name: 'A', band: '#111111' }, { name: 'B (M)', band: null }], more: 0 })
    expect(deedTags([0, 1, 2, 3], board, [], 2)).toEqual({ tags: [{ name: 'A', band: '#111111' }, { name: 'B', band: null }], more: 2 })
    expect(deedTags([], board, [])).toEqual({ tags: [], more: 0 })
    expect(deedTags([7], board, [])).toEqual({ tags: [], more: 0 })
  })
})

describe('tally and team helpers', () => {
  it('scales a bar to the best worth without leaving 0 to 1', () => {
    expect(barFraction(500, 1000)).toBe(0.5); expect(barFraction(2000, 1000)).toBe(1); expect(barFraction(-5, 1000)).toBe(0); expect(barFraction(0, 0)).toBe(0)
  })
  it('picks ink or white text for a hex colour', () => {
    expect(inkOn('#ffd23f')).toBe('#1a1a1a'); expect(inkOn('#1a1a1a')).toBe('#ffffff'); expect(inkOn('#e2483d')).toBe('#ffffff'); expect(inkOn('nonsense')).toBe('#1a1a1a')
  })
})
```

```ts
// controller/src/tv/turf3d/ui/faceTexture.test.ts
import { describe, expect, it } from 'vitest'
import { inlineVars, photoCircle, svgDataUrl } from './faceTexture'

describe('inlineVars', () => {
  it('replaces CSS variables with palette colours, and unknown ones with a fallback', () => {
    expect(inlineVars('<circle fill="var(--tomato)" stroke="var(--ink)"/>', { tomato: '#c8463b', ink: '#111111' })).toBe('<circle fill="#c8463b" stroke="#111111"/>')
    expect(inlineVars('<a fill="var(--white)"/>', {})).toBe('<a fill="#ffffff"/>')
    expect(inlineVars('<a fill="var(--nope)"/>', {})).toBe('<a fill="#1a1a1a"/>')
    expect(inlineVars('<a fill="#abcdef"/>', {})).toBe('<a fill="#abcdef"/>')
  })
})

describe('svgDataUrl', () => {
  it('makes a data URL a browser can load as an image', () => {
    const u = svgDataUrl('<svg xmlns="http://www.w3.org/2000/svg"><g/></svg>')
    expect(u.startsWith('data:image/svg+xml;charset=utf-8,')).toBe(true)
    expect(decodeURIComponent(u.split(',')[1])).toBe('<svg xmlns="http://www.w3.org/2000/svg"><g/></svg>')
  })
})

describe('photoCircle', () => {
  it('maps the face photo circle (centre 50, radius 41 in a -4..104 box) to canvas pixels', () => {
    const c = photoCircle(108)
    expect(c.cx).toBeCloseTo(54, 9); expect(c.cy).toBeCloseTo(54, 9); expect(c.r).toBeCloseTo(41, 9); expect(c.x).toBeCloseTo(13, 9); expect(c.w).toBeCloseTo(82, 9)
    expect(photoCircle(216).r).toBeCloseTo(82, 9)
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/copy.test.ts src/tv/turf3d/ui/faceTexture.test.ts`
Expected: FAIL (new exports missing, `./faceTexture` cannot be resolved).

- [ ] **Step 3: Write the implementation**

Append to `controller/src/tv/turf3d/ui/copy.ts`:

```ts
/** Who a debt is owed to: a token, everyone (-2), or the bank. */
export function debtTo(d: { to: number }, tokens: { name: string }[]): string {
  return d.to >= 0 ? tokens[d.to]?.name ?? 'the bank' : d.to === -2 ? 'everyone' : 'the bank'
}
export const debtLine = (name: string, amount: number): string => `${name.toUpperCase()} OWES ${money(amount)}`
export const tradeTitle = (counters: number): string => (counters > 0 ? `COUNTER-OFFER #${counters}` : 'TRADE OFFER!')

export interface DeedTagSpec { name: string; band: string | null }
/** The places in one side of a trade as tags (mortgaged ones marked), capped at [max] with the rest counted. */
export function deedTags(spaces: number[], board: { name: string; color?: string }[], mortgaged: number[], max = 5): { tags: DeedTagSpec[]; more: number } {
  const real = spaces.filter((s) => board[s])
  return {
    tags: real.slice(0, max).map((s) => ({ name: `${board[s].name}${mortgaged.includes(s) ? ' (M)' : ''}`, band: board[s].color ?? null })),
    more: Math.max(0, real.length - max),
  }
}

export const barFraction = (worth: number, max: number): number => Math.min(1, Math.max(0, worth) / Math.max(1, max))

/** Ink or white text, whichever reads better on a hex colour (ink when it is not a hex colour). */
export function inkOn(hex: string): string {
  const m = /^#([0-9a-f]{6})$/i.exec(hex)
  if (!m) return '#1a1a1a'
  const n = parseInt(m[1], 16)
  return ((n >> 16) & 255) * 0.3 + ((n >> 8) & 255) * 0.59 + (n & 255) * 0.11 > 150 ? '#1a1a1a' : '#ffffff'
}
```

```ts
// controller/src/tv/turf3d/ui/faceTexture.ts
import { FALLBACK_PALETTE, type Palette } from './copy'

/** Replaces CSS variables in an SVG string with palette colours, so it can be drawn as a standalone image. */
export function inlineVars(svg: string, p: Palette): string {
  return svg.replace(/var\(--([\w-]+)\)/g, (_, n: string) => p[n] || FALLBACK_PALETTE[n] || '#1a1a1a')
}

export const svgDataUrl = (svg: string): string => `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`

/** The photo circle of a Face (viewBox -4..104: centre 50, radius 41, image square 9..91) in pixels of a [size] canvas. */
export function photoCircle(size: number): { cx: number; cy: number; r: number; x: number; y: number; w: number } {
  const k = size / 108
  return { cx: 54 * k, cy: 54 * k, r: 41 * k, x: 13 * k, y: 13 * k, w: 82 * k }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/copy.test.ts src/tv/turf3d/ui/faceTexture.test.ts && npx tsc -b`
Expected: PASS; no tsc output.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/ui/copy.ts controller/src/tv/turf3d/ui/copy.test.ts controller/src/tv/turf3d/ui/faceTexture.ts controller/src/tv/turf3d/ui/faceTexture.test.ts
git commit -m "feat(turf3d): helpers for the debt, trade, tally and team-up panels and for face textures"
```

---

### Task 2: Faces, tags, and the plumbing

**Files:**
- Create: `controller/src/tv/turf3d/ui/FaceIcon.tsx`, `controller/src/tv/turf3d/ui/DeedTag.tsx`
- Modify: `controller/src/tv/turf3d/ui/copy.ts`, `controller/src/tv/turf3d/ui/panels.ts`, `controller/src/tv/turf3d/ui/panels.test.ts`, `controller/src/tv/turf3d/ui/Frame.tsx`, `controller/src/tv/turf3d/ui/PanelHost.tsx`, `controller/src/tv/turf3d/TurfScene.tsx`, `controller/src/tv/turf3d/TurfStage3D.tsx`, `controller/src/tv/TurfStage.tsx`

**Interfaces:**
- Consumes: Task 1 helpers; `usePalette`; `Plate`, `Label`.
- Produces: `interface Person { id: string; name: string; face: string; color: string }` (in `ui/copy.ts`), `FaceIcon({ face, color, size?, x?, y? })`, `DeedTag({ w, name, band, x?, y? })`; `PanelName` gains `'debt' | 'trade' | 'tally' | 'teamup'`; `TurfStage3D`, `TurfScene` and `PanelHost` take `people: Person[]`.

- [ ] **Step 1: Extend the mapping test (red)**

In `controller/src/tv/turf3d/ui/panels.test.ts` change the mapping tests to:

```ts
describe('panelFor', () => {
  it('maps every phase but the podium to its own panel', () => {
    for (const p of ['roll', 'move', 'manage', 'jail', 'choose', 'pieces', 'deal', 'buy', 'auction', 'card', 'debt', 'trade', 'tally', 'teamup'] as TurfPhase[]) expect(panelFor(p), p).toBe(p)
  })
  it('leaves the podium to the flat stage', () => { expect(panelFor('podium')).toBeNull() })
})
```
and change the `panelSize` test so the big card is `pieces`, `deal`, `trade`, `tally` and `teamup`, and the standard card is `roll, move, manage, jail, choose, buy, auction, card, debt`.

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/panels.test.ts`
Expected: FAIL.

- [ ] **Step 2: Make it pass**

In `controller/src/tv/turf3d/ui/panels.ts`: widen `PanelName` with `| 'debt' | 'trade' | 'tally' | 'teamup'`; add `debt: 'debt', trade: 'trade', tally: 'tally', teamup: 'teamup'` to `PORTED`; and change `panelSize` to return `'setup'` for `pieces`, `deal`, `trade`, `tally` and `teamup` and `'std'` otherwise.

Run the panels test. Expected: PASS.

- [ ] **Step 3: The person type, the face and the tag**

Add to `controller/src/tv/turf3d/ui/copy.ts`:

```ts
/** A player as the 3D panels need them: an id to match team members, a name, and their avatar. */
export interface Person { id: string; name: string; face: string; color: string }
```

```tsx
// controller/src/tv/turf3d/ui/FaceIcon.tsx
import { createElement, useEffect, useState } from 'react'
import * as THREE from 'three'
import { inlineVars, photoCircle, svgDataUrl } from './faceTexture'
import { usePalette } from './palette'

const load = (src: string) => new Promise<HTMLImageElement>((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src })

/**
 * A player's avatar: the existing SVG Face, rendered to markup (dynamic import, so nothing else pays for it), its CSS
 * variables replaced from the palette, drawn to a canvas; a photo face gets its picture painted on top, clipped to the
 * face circle. Anything that fails leaves the preset (or nothing): the panel never depends on it.
 */
export function FaceIcon({ face, color, size = 48, x = 0, y = 0 }: { face: string; color: string; size?: number; x?: number; y?: number }) {
  const p = usePalette()
  const [tex, setTex] = useState<THREE.CanvasTexture | null>(null)
  useEffect(() => {
    let alive = true
    let made: THREE.CanvasTexture | null = null
    const S = 192
    void (async () => {
      const [{ renderToStaticMarkup }, mod] = await Promise.all([import('react-dom/server'), import('../../../theme/Face')])
      const markup = renderToStaticMarkup(createElement(mod.Face, { face, color, size: S })).replace('<svg ', '<svg xmlns="http://www.w3.org/2000/svg" ')
      const img = await load(svgDataUrl(inlineVars(markup, p)))
      const cv = document.createElement('canvas')
      cv.width = cv.height = S
      const g = cv.getContext('2d')
      if (!g) return
      g.drawImage(img, 0, 0, S, S)
      const pid = mod.photoId(face)
      if (pid) {
        try {
          const photo = await load(mod.photoUrl(pid))
          const c = photoCircle(S)
          g.save(); g.beginPath(); g.arc(c.cx, c.cy, c.r, 0, Math.PI * 2); g.clip(); g.drawImage(photo, c.x, c.y, c.w, c.w); g.restore()
        } catch { /* the preset shows through */ }
      }
      if (!alive) return
      made = new THREE.CanvasTexture(cv)
      made.colorSpace = THREE.SRGBColorSpace
      setTex(made)
    })().catch(() => undefined)
    return () => { alive = false; made?.dispose() }
  }, [face, color, p])
  if (!tex) return null
  return (
    <mesh position={[x, y, 2]}>
      <planeGeometry args={[size, size]} />
      <meshBasicMaterial map={tex} transparent toneMapped={false} />
    </mesh>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/DeedTag.tsx
import { Plate } from './Card'
import { Label } from './Label'
import { INK, PAPER } from './theme'

/** A place as a tag: its name on paper, with a strip of its colour band on the left. */
export function DeedTag({ w, name, band, x = 0, y = 0 }: { w: number; name: string; band: string | null; x?: number; y?: number }) {
  return (
    <group position={[x, y, 0]}>
      <Plate w={w + 6} h={40} r={10} color={INK} z={1} />
      <Plate w={w} h={34} r={8} color={PAPER} z={2} />
      <Plate w={14} h={34} r={5} color={band ?? '#2b2b2b'} x={-w / 2 + 9} z={3} />
      <Label px={22} kind="label" font="bodyBold" anchorX="left" align="left" x={-w / 2 + 26} z={4} maxWidth={w - 34}>{name}</Label>
    </group>
  )
}
```

- [ ] **Step 4: The frame and the people plumbing**

In `controller/src/tv/turf3d/ui/Frame.tsx` change the footer condition from `{hud.ticker.length > 0 && (` to `{size === 'std' && hud.ticker.length > 0 && (`.

In `controller/src/tv/turf3d/ui/PanelHost.tsx`: import `type Person` from `./copy`; add `people: Person[]` to `PanelHost`'s props and to `panelBody`'s parameters (`panelBody(name, tv, hud, people)`); pass it through. (Panels for the new names are added in Tasks 3 and 4; until then the `default: return null` covers them.)

In `controller/src/tv/turf3d/TurfScene.tsx`: import `type Person`; add `people: Person[]` to the props; pass `people={people}` to `<PanelHost>`.

In `controller/src/tv/turf3d/TurfStage3D.tsx`: import `type Person`; add `people: Person[]` to the props; pass `people={people}` to `<TurfScene>`.

In `controller/src/tv/TurfStage.tsx`, in `Turf`: add

```tsx
  const faces = useMemo(() => players.map((p) => ({ id: p.id, name: p.name, face: p.avatar.face, color: p.avatar.color })), [players])
```
and pass `people={faces}` to `<TurfStage3D>`.

- [ ] **Step 5: Type-check and build**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; tests pass; `✓ built in`. The build output should now also contain a chunk for `react-dom/server` reachable only from the 3D stage.

- [ ] **Step 6: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src
git commit -m "feat(turf3d): avatar faces and deed tags for the 3D panels, and every phase but the podium mapped to a 3D card"
```

---

### Task 3: Debt and tally

**Files:**
- Create: `controller/src/tv/turf3d/ui/panels/DebtPanel.tsx`, `controller/src/tv/turf3d/ui/panels/TallyPanel.tsx`
- Modify: `controller/src/tv/turf3d/ui/PanelHost.tsx`

**Interfaces:**
- Consumes: Task 1 and 2 helpers; kit.
- Produces: `DebtPanel({ tv })`, `TallyPanel({ tv })`.

- [ ] **Step 1: The panels**

```tsx
// controller/src/tv/turf3d/ui/panels/DebtPanel.tsx
import type { TurfTv } from '../../../types'
import { Plate } from '../Card'
import { debtLine, debtTo } from '../copy'
import { DrinkIcon } from '../DrinkIcon'
import { Label } from '../Label'
import { GREEN, INK, LED_BG, MUTED, RED } from '../theme'

/** Someone owes money: who, how much, to whom and why, their cash against it, and what happens next. */
export function DebtPanel({ tv }: { tv: TurfTv }) {
  const d = tv.debt
  const t = d ? tv.tokens[d.token] : undefined
  if (!d || !t) return null
  const tone = t.cash >= d.amount ? GREEN : RED
  return (
    <group>
      <DrinkIcon piece={t.piece} color={t.color} size={52} x={-262} y={62} />
      <Label px={56} kind="title" x={26} y={62} maxWidth={470}>{debtLine(t.name, d.amount)}</Label>
      <Label px={28} kind="body" y={14} maxWidth={540} font="bodyBold" color={MUTED}>{`to ${debtTo(d, tv.tokens)} for ${d.why}`}</Label>
      <group position={[0, -42, 0]}>
        <Label px={24} kind="label" font="bodyBold" x={-150} color={MUTED}>CASH</Label>
        <Plate w={230} h={58} r={10} color={INK} x={50} z={1} />
        <Plate w={222} h={50} r={8} color={LED_BG} x={50} z={2} />
        <Label px={40} kind="title" font="display" color={tone} x={-14} z={3}>$</Label>
        <Label px={40} kind="body" font="led" color={tone} x={78} z={3} anchorX="center">{String(t.cash)}</Label>
      </group>
      <Label px={22} kind="label" y={-98} color={MUTED} maxWidth={540}>Sell or mortgage to cover it, or go bankrupt</Label>
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/panels/TallyPanel.tsx
import { useFrame } from '@react-three/fiber'
import { useRef } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../../types'
import { Plate } from '../Card'
import { barFraction, money } from '../copy'
import { DrinkIcon } from '../DrinkIcon'
import { Label } from '../Label'
import { GOLD, INK, MUTED, PAPER } from '../theme'

const BAR_W = 330

/** A bar that grows from its left end to [frac] of its full width, starting after [delay] seconds. */
function GrowBar({ frac, color, delay, y }: { frac: number; color: string; delay: number; y: number }) {
  const g = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  useFrame(() => {
    const u = Math.min(1, Math.max(0, (performance.now() - t0.current) / 1000 - delay) / 1.2)
    g.current?.scale.set(Math.max(0.001, 1 - (1 - u) ** 3), 1, 1)
  })
  const w = Math.max(14, frac * BAR_W)
  return (
    <group ref={g} position={[-110, y, 0]}>
      <Plate w={w + 6} h={36} r={9} color={INK} x={w / 2} z={1} />
      <Plate w={w} h={30} r={7} color={color} x={w / 2} z={2} />
    </group>
  )
}

/** The final tally: best first, a bar per token in proportion to worth. */
export function TallyPanel({ tv }: { tv: TurfTv }) {
  const rows = tv.tally.slice().sort((a, b) => a.rank - b.rank).slice(0, 6)
  const max = Math.max(1, ...rows.map((r) => r.worth))
  return (
    <group>
      <Label px={60} kind="title" y={196}>FINAL TALLY</Label>
      {rows.map((r, k) => {
        const t = tv.tokens[r.token]
        if (!t) return null
        const y = 118 - k * 64
        return (
          <group key={r.token}>
            <Plate w={40} h={40} r={20} color={r.rank === 1 ? GOLD : PAPER} x={-366} y={y} z={1} />
            <Label px={28} kind="body" font="display" x={-366} y={y} z={3}>{String(r.rank)}</Label>
            <DrinkIcon piece={t.piece} color={t.color} size={44} x={-318} y={y - 6} />
            <Label px={28} kind="body" font="bodyBold" anchorX="left" align="left" x={-284} y={y} maxWidth={172}>{t.name}</Label>
            <GrowBar frac={barFraction(r.worth, max)} color={t.color} delay={0.4 + k * 0.2} y={y} />
            <Label px={28} kind="body" font="bodyBold" anchorX="right" align="right" x={372} y={y} maxWidth={120}>{money(r.worth)}</Label>
          </group>
        )
      })}
      <Label px={28} kind="body" y={-206} color={MUTED} font="bodyBold" maxWidth={740}>Cash + places (half if mortgaged) + buildings at cost</Label>
    </group>
  )
}
```

- [ ] **Step 2: Route them**

In `controller/src/tv/turf3d/ui/PanelHost.tsx` import `DebtPanel` and `TallyPanel` and add to the switch: `case 'debt': return <DebtPanel tv={tv} />` and `case 'tally': return <TallyPanel tv={tv} />`.

- [ ] **Step 3: Type-check, build, and look**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; tests pass; `✓ built in`.

Then with the preview server running (`npx vite preview --port 4173 --strictPort --host 127.0.0.1`) and the scratch `ui-look.cjs` (`SP` is the scratchpad directory): `node $SP/ui-look.cjs $SP/ui-shots '[["debt",5000],["tally",6500]]'` (the tally bars need about 1.5 s after the text is ready). Look at both. Expected: `debt` shows the debtor's drink, "NAME OWES $…", the "to X for why" line, CASH with a dark LED plate (`$` in Anton, digits in seven-segment) and the closing hint, nothing clipped; `tally` shows "FINAL TALLY" and six rows of rank, drink, name, bar and worth on the big card, the winner's rank in gold, all inside the card. Every console line must read `ok same-origin`. Fix overlaps and retake before committing.

- [ ] **Step 4: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src
git commit -m "feat(turf3d): the debt and final tally panels in 3D"
```

---

### Task 4: Trade and team-up

**Files:**
- Create: `controller/src/tv/turf3d/ui/panels/TradePanel.tsx`, `controller/src/tv/turf3d/ui/panels/TeamUpPanel.tsx`
- Modify: `controller/src/tv/turf3d/ui/PanelHost.tsx`

**Interfaces:**
- Consumes: Task 1 and 2 helpers; `FaceIcon`, `DeedTag`, kit; `people: Person[]`.
- Produces: `TradePanel({ tv })`, `TeamUpPanel({ tv, people })`.

- [ ] **Step 1: The panels**

```tsx
// controller/src/tv/turf3d/ui/panels/TradePanel.tsx
import { useEffect, useMemo, type ReactNode } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../../types'
import { deedTags, money, tradeTitle } from '../copy'
import { DeedTag } from '../DeedTag'
import { DrinkIcon } from '../DrinkIcon'
import { Label } from '../Label'
import { Pill } from '../Pill'
import { GOLD, INK, MUTED, PAPER_DARK } from '../theme'

/** A chunky arrow pointing right (or left when [dir] is -1), drawn from a shape so no font glyph is involved. */
function Arrow({ dir, y }: { dir: 1 | -1; y: number }) {
  const geo = useMemo(() => {
    const s = new THREE.Shape()
    const pts: [number, number][] = [[-34, -9], [8, -9], [8, -24], [36, 0], [8, 24], [8, 9], [-34, 9]]
    pts.forEach(([x, py], i) => { const px = x * dir; if (i === 0) s.moveTo(px, py); else s.lineTo(px, py) })
    s.closePath()
    return new THREE.ShapeGeometry(s)
  }, [dir])
  useEffect(() => () => geo.dispose(), [geo])
  return <mesh geometry={geo} position={[0, y, 2]}><meshBasicMaterial color={INK} toneMapped={false} side={THREE.DoubleSide} /></mesh>
}

function Side({ tv, token, deeds, cash, cards, x }: { tv: TurfTv; token: number; deeds: number[]; cash: number; cards: number; x: number }) {
  const t = tv.tokens[token]
  if (!t) return null
  const { tags, more } = deedTags(deeds, tv.board, tv.mortgaged)
  const rows: ReactNode[] = []
  let y = 62
  tags.forEach((tag) => { rows.push(<DeedTag key={tag.name} w={300} name={tag.name} band={tag.band} y={y} />); y -= 42 })
  if (more > 0) { rows.push(<Pill key="more" w={130} h={34} text={`+${more} more`} fill={PAPER_DARK} y={y} />); y -= 42 }
  if (cash > 0) { rows.push(<Pill key="cash" w={150} h={34} text={money(cash)} fill={GOLD} y={y} />); y -= 42 }
  if (cards > 0) { rows.push(<Pill key="cards" w={230} h={34} text={`Get Out card x${cards}`} fill={PAPER_DARK} y={y} />); y -= 42 }
  if (rows.length === 0) rows.push(<Label key="none" px={28} kind="body" y={62} color={MUTED}>nothing</Label>)
  return (
    <group position={[x, 0, 0]}>
      <DrinkIcon piece={t.piece} color={t.color} size={46} x={-150} y={114} />
      <Label px={30} kind="body" font="bodyBold" anchorX="left" align="left" x={-116} y={116} maxWidth={250}>{`${t.name} gives`}</Label>
      {rows}
    </group>
  )
}

/** A trade offer: what each side gives, with an arrow each way between them. */
export function TradePanel({ tv }: { tv: TurfTv }) {
  const t = tv.trade
  if (!t) return null
  return (
    <group>
      <Label px={60} kind="title" y={196}>{tradeTitle(t.counters)}</Label>
      <Side tv={tv} token={t.from} deeds={t.give} cash={t.giveCash} cards={t.giveCards} x={-208} />
      <Arrow dir={1} y={40} />
      <Arrow dir={-1} y={-30} />
      <Side tv={tv} token={t.to} deeds={t.get} cash={t.getCash} cards={t.getCards} x={208} />
      <Label px={28} kind="body" y={-208} color={MUTED} font="bodyBold" maxWidth={740}>{`${tv.tokens[t.to]?.name ?? ''} decides on their phone. Heckle freely.`}</Label>
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/panels/TeamUpPanel.tsx
import type { TurfTv } from '../../../types'
import { Plate } from '../Card'
import { inkOn, type Person } from '../copy'
import { FaceIcon } from '../FaceIcon'
import { Label } from '../Label'
import { Pill } from '../Pill'
import { INK, MUTED, PAPER_DARK } from '../theme'

/** The teams: one row each, a colour plate with the team name, then its members' faces and first names. */
export function TeamUpPanel({ tv, people }: { tv: TurfTv; people: Person[] }) {
  const teams = tv.tokens.slice(0, 6)
  const top = tv.notice ? 96 : 118
  return (
    <group>
      <Label px={60} kind="title" y={200}>TEAMS!</Label>
      {tv.notice && <Label px={28} kind="body" y={158} color={MUTED} font="bodyBold" maxWidth={740}>{tv.notice}</Label>}
      {teams.map((t, i) => {
        const y = top - i * 64
        const members = t.members.map((id) => people.find((p) => p.id === id)).filter((p): p is Person => !!p)
        const shown = members.slice(0, 4)
        return (
          <group key={i}>
            <Plate w={206} h={50} r={12} color={INK} x={-270} y={y} z={1} />
            <Plate w={200} h={44} r={9} color={t.color} x={-270} y={y} z={2} />
            <Label px={28} kind="body" font="bodyBold" color={inkOn(t.color)} x={-270} y={y} z={3} maxWidth={186}>{t.name}</Label>
            {shown.map((p, k) => (
              <group key={p.id} position={[-136 + k * 128, y, 0]}>
                <FaceIcon face={p.face} color={p.color} size={46} x={16} />
                <Label px={22} kind="label" anchorX="left" align="left" x={44} maxWidth={78} font="bodyBold">{p.name.split(' ')[0]}</Label>
              </group>
            ))}
            {members.length > 4 && <Pill w={64} h={34} text={`+${members.length - 4}`} fill={PAPER_DARK} x={382} y={y} />}
          </group>
        )
      })}
      <Label px={28} kind="body" y={-206} color={MUTED} font="bodyBold" maxWidth={740}>Shuffle: S on the TV or the captain's phone</Label>
    </group>
  )
}
```

- [ ] **Step 2: Route them**

In `controller/src/tv/turf3d/ui/PanelHost.tsx` import `TradePanel` and `TeamUpPanel` and add to the switch: `case 'trade': return <TradePanel tv={tv} />` and `case 'teamup': return <TeamUpPanel tv={tv} people={people} />`. `PanelName` is now fully covered, so delete the `default: return null` if `tsc` flags it.

- [ ] **Step 3: Type-check, build, and look**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror" && SP=/private/tmp/claude-501/-Users-jjahn-HoopDreams/6c7b9067-a001-4e4c-ba79-093b85603b3a/scratchpad && node $SP/ui-look.cjs $SP/ui-shots '[["trade",5000],["teamup",5000]]'`
Expected: no tsc output; tests pass; build OK; two `ok same-origin` lines. Look: `trade` shows the title, two columns each with a drink, "NAME gives", place tags with colour strips, cash and Get Out pills, two arrows between, and the hint; `teamup` shows "TEAMS!", one row per team with a colour plate and up to four member faces with first names. If faces are missing, wait longer (they load asynchronously) or check that the fixtures give players avatars. Fix overlaps and retake before committing.

- [ ] **Step 4: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src
git commit -m "feat(turf3d): the trade and team-up panels in 3D, with avatar faces"
```

---

### Task 5: Screenshots, checks, docs

**Files:**
- Modify: `controller/scripts/turf3d-shots.mjs`, `README.md`

- [ ] **Step 1: Capture the new panels**

In `controller/scripts/turf3d-shots.mjs`, add `['debt', 5000], ['trade', 5000], ['tally', 6500], ['teamup', 5000]` to the panel shots (after `['card', 5000]`).

- [ ] **Step 2: Run it and read the frames**

Run: `cd /Users/jjahn/HoopDreams/controller && uptime && npx vite build 2>&1 | grep -E "built|rror" && OUT=$SP/final-dense node scripts/turf3d-shots.mjs 2>&1 | tail -32`
Expected: `ok` for every shot with no `OFF-ORIGIN`; `balanced` at 50 fps or better with `p95ms` 25 or under. Read the four new frames for legibility.

- [ ] **Step 3: Document**

In `README.md`, in the **In 3D** bullet, change the sentence listing what still uses the flat well to say that only the podium does (the 3D cards now cover every phase before it, including debt, trade, the tally and team-up with avatar faces), and that the flat well remains the 2D fallback.

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

State the `rm -rf` facts (it deletes only the untracked `controller/turf3d-shots/` output; rollback is re-running the script) before running:

```bash
cd /Users/jjahn/HoopDreams
rm -rf controller/turf3d-shots
git add controller/scripts/turf3d-shots.mjs README.md
git commit -m "feat(turf3d): screenshot the dense panels, and document that only the podium is still flat"
```
