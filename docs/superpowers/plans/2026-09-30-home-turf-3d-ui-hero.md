# Home Turf 3D UI hero moments: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put the buy, auction and card panels, the total banner and the flash callouts into the 3D paper-card system (camera-locked, drop and lift), keeping the DOM versions as the fallback until the 3D text has synced.

**Architecture:** Pure copy and palette helpers (tested) feed small R3F panels built from the sub-project 1 kit. `Dais` gains a `dist` and `offsetY` so callouts and the banner can share the screen with the card. A palette read from the stage element's CSS variables, provided inside the canvas, colours the callouts.

**Tech Stack:** react-three-fiber 9, drei `Text`, three, vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-30-home-turf-3d-ui-hero-design.md` (builds on `2026-09-30-home-turf-3d-ui-kit-design.md`, merged).

## Global Constraints

- Engine and `TurfTv` unchanged. Nothing loads from a network. Labels stay generic. No new audio. Commit messages carry no attribution lines.
- Text minimums from `ui/sizing.ts` (hero 72, title 56, body 28, label 22 px at 1080p). 50 fps or better at `balanced`.
- The DOM well, DOM banner and DOM flash remain the 2D fallback and show until the 3D text has synced.
- The write-gate hook blocks the first write to a new file and the first edit to a file per session: state the facts it asks for and retry. BSD `sed -i` needs an empty suffix; prefer the Edit tool or Python for edits.

## Review Focus

1. **Callout and card on screen together** must not fight for depth or hide each other's text. Task 3 (distinct distances) and its screenshots.
2. **The longest real callout text** ("DRINK!" with "Everyone but Amanda: 2 SIPS") must stay inside the burst. Task 3 screenshot.
3. **A CSS variable that is missing** must not produce an invisible callout. Task 1 (`resolve` falls back).
4. **A card with no sips, drinking off, or a very long card text** must not overflow the card plate. Task 2 (wrap width; `sipLine` returns null).
5. **An auction with no leader and zero bids** must read sensibly. Task 1 (`auctionHint`) and Task 2 panel.

---

### Task 1: Copy and palette helpers

**Files:**
- Create: `controller/src/tv/turf3d/ui/copy.ts`, `controller/src/tv/turf3d/ui/copy.test.ts`

**Interfaces:**
- Produces: `money(n)`, `sipText(n)`, `interface DeedFacts { kind: string; name: string; band: string | null; pills: string[]; foot: string }`, `deedFacts(space: TurfSpace, ownerName?: string): DeedFacts`, `buyCall(space?: TurfSpace): string`, `auctionHint(bids: number): string`, `sipLine(drinks: boolean, sips: number): string | null`, `interface FlashSpec { id: number; text: string; sub?: string; fill: string; ink?: string; ms: number; small?: boolean }`, `varName(expr: string): string | null`, `type Palette = Record<string, string>`, `PALETTE_NAMES: string[]`, `FALLBACK_PALETTE: Palette`, `resolve(p: Palette, expr: string, fallback?: string): string`.

- [ ] **Step 1: Write the failing test**

```ts
// controller/src/tv/turf3d/ui/copy.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfSpace } from '../../types'
import { FALLBACK_PALETTE, PALETTE_NAMES, auctionHint, buyCall, deedFacts, money, resolve, sipLine, sipText, varName } from './copy'

const street: TurfSpace = { name: "Amanda's Balcony", label: "Amanda's Balcony", kind: 'street', group: 1, color: '#e2483d', price: 220, rent: [18, 90, 250, 700, 875, 1050], houseCost: 150, tax: 0 }
const ride: TurfSpace = { name: 'Rideshare', label: 'Rideshare', kind: 'railroad', group: 9, price: 200, rent: [], houseCost: 0, tax: 0 }
const util: TurfSpace = { name: 'Aux Cord', label: 'Aux Cord', kind: 'utility', group: 10, price: 150, rent: [], houseCost: 0, tax: 0 }

describe('money and sips', () => {
  it('formats dollars with separators', () => { expect(money(220)).toBe('$220'); expect(money(1050)).toBe('$1,050') })
  it('names sips the way the DOM well does', () => {
    expect(sipText(1)).toBe('1 SIP'); expect(sipText(2)).toBe('2 SIPS'); expect(sipText(5)).toBe('A SHOT'); expect(sipText(99)).toBe('FINISH YOUR DRINK')
  })
  it('shows a drink line only when drinking is on and there is something to drink', () => {
    expect(sipLine(true, 2)).toBe('DRINK 2 SIPS'); expect(sipLine(true, 0)).toBeNull(); expect(sipLine(false, 3)).toBeNull()
  })
})

describe('deedFacts', () => {
  it('summarises a street: rent, whole set, hotel, and the house price', () => {
    expect(deedFacts(street)).toEqual({ kind: 'PLACE', name: "Amanda's Balcony", band: '#e2483d', pills: ['RENT $18', 'SET $36', 'HOTEL $1,050'], foot: 'House $150' })
  })
  it('summarises a ride and a utility, and says who owns it', () => {
    expect(deedFacts(ride).pills).toEqual(['1 RIDE $25', 'ALL 4 $200']); expect(deedFacts(ride).kind).toBe('RIDE HOME'); expect(deedFacts(ride).band).toBeNull()
    expect(deedFacts(util).pills).toEqual(['ONE 4 × DICE', 'BOTH 10 × DICE']); expect(deedFacts(util).kind).toBe('UTILITY')
    expect(deedFacts(ride, 'Izzy').foot).toBe('Owner: Izzy'); expect(deedFacts(ride).foot).toBe('For sale')
  })
  it('does not crash on a street with a short rent table', () => {
    expect(deedFacts({ ...street, rent: [] }).pills).toEqual(['RENT $0', 'SET $0', 'HOTEL $0'])
  })
})

describe('the calls', () => {
  it('asks to buy at the price, with a safe default', () => { expect(buyCall(street)).toBe('BUY IT FOR $220?'); expect(buyCall(undefined)).toBe('BUY IT FOR $0?') })
  it('counts bids, with 1 singular and 0 sensible', () => {
    expect(auctionHint(0)).toBe('0 bids · each bid resets the clock'); expect(auctionHint(1)).toBe('1 bid · each bid resets the clock'); expect(auctionHint(3)).toBe('3 bids · each bid resets the clock')
  })
})

describe('the palette', () => {
  it('pulls the variable name out of var(--x)', () => {
    expect(varName('var(--tomato)')).toBe('--tomato'); expect(varName(' var(--sun) ')).toBe('--sun'); expect(varName('#ff0000')).toBeNull(); expect(varName('var(--a, red)')).toBeNull()
  })
  it('resolves a variable through the palette, passes a plain colour through, and falls back when missing', () => {
    const p = { tomato: '#c8463b' }
    expect(resolve(p, 'var(--tomato)')).toBe('#c8463b')
    expect(resolve(p, '#123456')).toBe('#123456')
    expect(resolve(p, 'var(--nope)')).toBe('#1a1a1a')
    expect(resolve(p, 'var(--nope)', '#fff')).toBe('#fff')
    expect(resolve({ tomato: '' }, 'var(--tomato)')).toBe('#1a1a1a')
  })
  it('has a hex fallback for every name it reads', () => {
    for (const n of PALETTE_NAMES) expect(FALLBACK_PALETTE[n], n).toMatch(/^#[0-9a-f]{6}$/i)
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/copy.test.ts`
Expected: FAIL, cannot resolve `./copy`.

- [ ] **Step 3: Write the implementation**

```ts
// controller/src/tv/turf3d/ui/copy.ts
import type { TurfSpace } from '../../types'

export const money = (n: number): string => `$${n.toLocaleString()}`
export const sipText = (n: number): string => (n >= 99 ? 'FINISH YOUR DRINK' : n === 5 ? 'A SHOT' : n === 1 ? '1 SIP' : `${n} SIPS`)
export const sipLine = (drinks: boolean, sips: number): string | null => (drinks && sips > 0 ? `DRINK ${sipText(sips)}` : null)

export interface DeedFacts { kind: string; name: string; band: string | null; pills: string[]; foot: string }

/** The short version of a deed for the 3D card: name on its band, three or two facts, and the foot line. */
export function deedFacts(s: TurfSpace, ownerName?: string): DeedFacts {
  const band = s.color ?? null
  if (s.kind === 'street') {
    const r0 = s.rent[0] ?? 0, r5 = s.rent[5] ?? 0
    return { kind: 'PLACE', name: s.name, band, pills: [`RENT ${money(r0)}`, `SET ${money(r0 * 2)}`, `HOTEL ${money(r5)}`], foot: `House ${money(s.houseCost)}` }
  }
  const foot = ownerName ? `Owner: ${ownerName}` : 'For sale'
  if (s.kind === 'railroad') return { kind: 'RIDE HOME', name: s.name, band, pills: ['1 RIDE $25', 'ALL 4 $200'], foot }
  return { kind: 'UTILITY', name: s.name, band, pills: ['ONE 4 × DICE', 'BOTH 10 × DICE'], foot }
}

export const buyCall = (s?: TurfSpace): string => `BUY IT FOR ${money(s?.price ?? 0)}?`
export const auctionHint = (bids: number): string => `${bids} bid${bids === 1 ? '' : 's'} · each bid resets the clock`

/** A callout as the DOM stage queues it: fill and ink are CSS colours (usually var(--name)). */
export interface FlashSpec { id: number; text: string; sub?: string; fill: string; ink?: string; ms: number; small?: boolean }

/** The CSS variable name inside `var(--x)`, or null for anything else (plain colours, fallbacks). */
export function varName(expr: string): string | null {
  const m = /^var\((--[\w-]+)\)$/.exec(expr.trim())
  return m ? m[1] : null
}

export type Palette = Record<string, string>
export const PALETTE_NAMES = ['ink', 'paper', 'white', 'sun', 'tomato', 'blueberry', 'lime', 'grape', 'bubblegum', 'tangerine', 'sky']
export const FALLBACK_PALETTE: Palette = {
  ink: '#1a1a1a', paper: '#fbf3dc', white: '#ffffff', sun: '#ffd23f', tomato: '#e2483d', blueberry: '#3b5bdb', lime: '#7bd94a',
  grape: '#8a4fd8', bubblegum: '#ff8fc0', tangerine: '#ff9a1f', sky: '#7fc8ff',
}

/** A CSS colour as hex: variables go through the palette (by name, without the dashes); anything else is used as is. */
export function resolve(p: Palette, expr: string, fallback = '#1a1a1a'): string {
  const n = varName(expr)
  if (n == null) return expr
  const v = p[n.slice(2)]
  return v ? v : fallback
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/copy.test.ts && npx tsc -b`
Expected: PASS, 10 tests; no tsc output.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d/ui/copy.ts controller/src/tv/turf3d/ui/copy.test.ts
git commit -m "feat(turf3d): copy and palette helpers for the buy, auction and card panels and the callouts"
```

---

### Task 2: Buy, auction and card panels

**Files:**
- Create: `controller/src/tv/turf3d/ui/palette.tsx`, `controller/src/tv/turf3d/ui/Bump.tsx`, `controller/src/tv/turf3d/ui/panels/DeedHeader.tsx`, `BuyPanel.tsx`, `AuctionPanel.tsx`, `CardPanel.tsx`
- Modify: `controller/src/tv/turf3d/ui/panels.ts`, `controller/src/tv/turf3d/ui/panels.test.ts`, `controller/src/tv/turf3d/ui/PanelHost.tsx`, `controller/src/tv/turf3d/TurfStage3D.tsx`

**Interfaces:**
- Consumes: Task 1 helpers; kit primitives.
- Produces: `PaletteContext`, `usePalette(): Palette`, `readPalette(el: Element): Palette`; `Bump({ k, children })` (scale-bumps its children whenever `k` changes); `DeedHeader({ facts, y?, w? })`; `BuyPanel({ tv })`, `AuctionPanel({ tv })`, `CardPanel({ tv })`; `PanelName` gains `'buy' | 'auction' | 'card'`.

- [ ] **Step 1: Extend the panel mapping test (red)**

In `controller/src/tv/turf3d/ui/panels.test.ts` change the two mapping tests to:

```ts
describe('panelFor', () => {
  it('maps the ported phases to their own panel', () => {
    for (const p of ['roll', 'move', 'manage', 'jail', 'choose', 'pieces', 'deal', 'buy', 'auction', 'card'] as TurfPhase[]) expect(panelFor(p)).toBe(p)
  })
  it('leaves every other phase to the DOM well', () => {
    for (const p of ['teamup', 'debt', 'trade', 'tally', 'podium'] as TurfPhase[]) expect(panelFor(p), p).toBeNull()
  })
})
```
and in `panelSize`'s loop add `'buy', 'auction', 'card'` to the standard list.

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/panels.test.ts`
Expected: FAIL (buy, auction and card map to null).

- [ ] **Step 2: Make it pass**

In `controller/src/tv/turf3d/ui/panels.ts`: widen `PanelName` to `'roll' | 'move' | 'manage' | 'jail' | 'choose' | 'pieces' | 'deal' | 'buy' | 'auction' | 'card'` and add `buy: 'buy', auction: 'auction', card: 'card'` to `PORTED`.

Run: `cd /Users/jjahn/HoopDreams/controller && npx vitest run src/tv/turf3d/ui/panels.test.ts`
Expected: PASS.

- [ ] **Step 3: The palette and the bump**

```tsx
// controller/src/tv/turf3d/ui/palette.tsx
import { createContext, useContext } from 'react'
import { FALLBACK_PALETTE, PALETTE_NAMES, type Palette } from './copy'

export const PaletteContext = createContext<Palette>(FALLBACK_PALETTE)
export const usePalette = (): Palette => useContext(PaletteContext)

/** The game's colours as the browser resolves them for [el] (they differ per game theme); a missing one keeps its fallback. */
export function readPalette(el: Element): Palette {
  const cs = getComputedStyle(el)
  const out: Palette = { ...FALLBACK_PALETTE }
  for (const n of PALETTE_NAMES) {
    const v = cs.getPropertyValue(`--${n}`).trim()
    if (/^#[0-9a-f]{3,8}$/i.test(v)) out[n] = v.length === 4 ? `#${v[1]}${v[1]}${v[2]}${v[2]}${v[3]}${v[3]}` : v
  }
  return out
}
```

```tsx
// controller/src/tv/turf3d/ui/Bump.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useRef, type ReactNode } from 'react'
import * as THREE from 'three'

/** Pops its children (scale 1.45 easing back to 1) every time [k] changes. */
export function Bump({ k, children }: { k: string | number; children: ReactNode }) {
  const g = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  useEffect(() => { t0.current = performance.now() }, [k])
  useFrame(() => {
    const u = Math.min(1, (performance.now() - t0.current) / 320)
    g.current?.scale.setScalar(1 + 0.45 * (1 - u) * (1 - u))
  })
  return <group ref={g}>{children}</group>
}
```

- [ ] **Step 4: The panels**

```tsx
// controller/src/tv/turf3d/ui/panels/DeedHeader.tsx
import { Plate } from '../Card'
import type { DeedFacts } from '../copy'
import { Label } from '../Label'
import { GOLD, INK, PAPER_DARK } from '../theme'

/** A place's name on its colour band, with the kind above it in small type. */
export function DeedHeader({ facts, y = 0, w = 500 }: { facts: DeedFacts; y?: number; w?: number }) {
  const band = facts.band ?? '#2b2b2b'
  const light = band.length === 7 && parseInt(band.slice(1, 3), 16) * 0.3 + parseInt(band.slice(3, 5), 16) * 0.59 + parseInt(band.slice(5, 7), 16) * 0.11 > 150
  const text = light ? INK : '#ffffff'
  return (
    <group position={[0, y, 0]}>
      <Plate w={w + 8} h={64} r={12} color={INK} z={1} />
      <Plate w={w} h={56} r={9} color={band} z={2} />
      <Label px={40} kind="title" color={text} z={3} maxWidth={w - 24}>{facts.name.toUpperCase()}</Label>
      <Plate w={96} h={26} r={13} color={facts.band ? PAPER_DARK : GOLD} x={-w / 2 + 70} y={40} z={3} />
      <Label px={22} kind="label" font="bodyBold" x={-w / 2 + 70} y={40} z={4}>{facts.kind}</Label>
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/panels/BuyPanel.tsx
import type { TurfTv } from '../../../types'
import { buyCall, deedFacts } from '../copy'
import { Label } from '../Label'
import { Pill } from '../Pill'
import { MUTED } from '../theme'
import { DeedHeader } from './DeedHeader'

/** A place is on offer: what it is, what it pays, and the question. */
export function BuyPanel({ tv }: { tv: TurfTv }) {
  const space = tv.board[tv.buy]
  if (!space) return null
  const facts = deedFacts(space, tv.owner[tv.buy] >= 0 ? tv.tokens[tv.owner[tv.buy]]?.name : undefined)
  const w = facts.pills.length === 3 ? 158 : 200
  const xs = facts.pills.length === 3 ? [-170, 0, 170] : [-105, 105]
  return (
    <group>
      <DeedHeader facts={facts} y={68} />
      {facts.pills.map((p, i) => <Pill key={p} w={w} text={p} x={xs[i]} y={6} />)}
      <Label px={60} kind="title" y={-52}>{buyCall(space)}</Label>
      <Label px={28} kind="body" y={-100} color={MUTED} font="bodyBold">or it goes to auction</Label>
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/panels/AuctionPanel.tsx
import type { TurfTv } from '../../../types'
import { Bump } from '../Bump'
import { Plate } from '../Card'
import { auctionHint, deedFacts, money } from '../copy'
import { Label } from '../Label'
import { GOLD, INK, LED_BG, MUTED } from '../theme'
import { DeedHeader } from './DeedHeader'

/** The auction: the place, the top bid (pops on every new bid), who leads, and how it works. */
export function AuctionPanel({ tv }: { tv: TurfTv }) {
  const a = tv.auction
  const space = a ? tv.board[a.space] : undefined
  if (!a || !space) return null
  const leader = a.leader >= 0 ? tv.tokens[a.leader] : undefined
  return (
    <group>
      <DeedHeader facts={deedFacts(space)} y={84} />
      <Label px={22} kind="label" y={36} color={MUTED} font="bodyBold">AUCTION! TOP BID</Label>
      <Bump k={a.top}>
        <group position={[0, -8, 0]}>
          <Plate w={250} h={64} r={10} color={INK} z={1} />
          <Plate w={242} h={56} r={8} color={LED_BG} z={2} />
          <Label px={44} kind="body" font="led" color={GOLD} z={3}>{money(a.top)}</Label>
        </group>
      </Bump>
      <Label px={32} kind="body" y={-62} font="bodyBold" maxWidth={520}>{leader ? `${leader.name} leads` : 'Nobody yet. Bid on your phone!'}</Label>
      <Label px={22} kind="label" y={-98} color={MUTED}>{auctionHint(a.bids)}</Label>
    </group>
  )
}
```

```tsx
// controller/src/tv/turf3d/ui/panels/CardPanel.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useRef, type ReactNode } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../../types'
import { Plate } from '../Card'
import { resolve, sipLine } from '../copy'
import { Label } from '../Label'
import { usePalette } from '../palette'
import { Pill } from '../Pill'
import { bodyOf } from '../sizing'
import { INK, PAPER } from '../theme'

/** Turns over from its back (rotation π) to face-up in about half a second whenever a new card is drawn. */
function Flip({ k, children }: { k: string; children: ReactNode }) {
  const g = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  useEffect(() => { t0.current = performance.now() }, [k])
  useFrame(() => {
    const u = Math.min(1, (performance.now() - t0.current) / 520), e = 1 - (1 - u) ** 3
    if (g.current) g.current.rotation.y = Math.PI * (1 - e)
  })
  return <group ref={g}>{children}</group>
}

/** The drawn card: deck name, the words, and what to drink. */
export function CardPanel({ tv }: { tv: TurfTv }) {
  const c = tv.card
  const p = usePalette()
  if (!c) return null
  const body = bodyOf('std')
  const tint = resolve(p, c.deck === 'chance' ? 'var(--sky)' : 'var(--bubblegum)', '#7fc8ff')
  const sips = sipLine(tv.drinks, c.sips)
  return (
    <Flip k={c.text}>
      <Plate w={body.w - 20} h={body.h - 16} r={18} color={INK} z={1} />
      <Plate w={body.w - 28} h={body.h - 24} r={14} color={tint} z={2} />
      <Label px={40} kind="title" y={78} z={3} color={INK}>{c.deckName.toUpperCase()}</Label>
      <Label px={34} kind="body" y={sips ? 4 : -8} z={3} maxWidth={body.w - 90} font="bodyBold">{c.text}</Label>
      {sips && <Pill w={250} h={44} y={-80} text={sips} fill={PAPER} />}
    </Flip>
  )
}
```

- [ ] **Step 5: Route them, and provide the palette**

In `controller/src/tv/turf3d/ui/PanelHost.tsx` add imports for `AuctionPanel`, `BuyPanel`, `CardPanel` and three switch cases:

```tsx
    case 'buy': return <BuyPanel tv={tv} />
    case 'auction': return <AuctionPanel tv={tv} />
    case 'card': return <CardPanel tv={tv} />
```

In `controller/src/tv/turf3d/TurfStage3D.tsx`: import `{ PaletteContext, readPalette } from './ui/palette'` and `{ FALLBACK_PALETTE, type Palette } from './ui/copy'`; add `const root = useRef<HTMLDivElement>(null)` and `const [palette, setPalette] = useState<Palette>(FALLBACK_PALETTE)`; add `useEffect(() => { if (root.current) setPalette(readPalette(root.current)) }, [])`; put `ref={root}` on the outer `<div className="turf-3d">`; and wrap `<TurfScene ... />` in `<PaletteContext.Provider value={palette}> ... </PaletteContext.Provider>` inside the existing `QualityContext.Provider`.

- [ ] **Step 6: Type-check, build, and look**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; tests pass; `✓ built in`.

Then, with the preview server running (`npx vite preview --port 4173 --strictPort --host 127.0.0.1`) and the scratch `ui-look.cjs` from the previous plan (`SP` is the scratchpad directory), run `node $SP/ui-look.cjs $SP/ui-shots '[["buy",5000],["auction",5000],["card",5000]]'` and look at each frame. Expected: `buy` shows the coloured place name with a small kind tag above it, three fact pills, "BUY IT FOR $220?" and the hint, all inside the card with nothing clipped; `auction` shows the place, "AUCTION! TOP BID", a dark plate with the amount in gold seven-segment digits, the leader line and the bid hint; `card` shows a sky- or bubblegum-tinted plate with the deck name, the card text and the drink pill, flipped face-up. Fix overlaps (the kind tag over the card header rule is the likeliest) and retake before committing.

- [ ] **Step 7: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src/tv/turf3d
git commit -m "feat(turf3d): the buy, auction and card panels in 3D, with the game's colours read from the stage"
```

---

### Task 3: The banner and the callouts

**Files:**
- Create: `controller/src/tv/turf3d/ui/Callouts.tsx`
- Modify: `controller/src/tv/turf3d/ui/Dais.tsx`, `controller/src/tv/turf3d/TurfScene.tsx`, `controller/src/tv/turf3d/TurfStage3D.tsx`, `controller/src/tv/TurfStage.tsx`

**Interfaces:**
- Consumes: `FlashSpec`, `resolve` from `./copy`; `usePalette`; `Dais`; `Label`.
- Produces: `Dais` props `dist?: number` (default 9) and `offsetY?: number` (reference px, default 0); `Callouts({ flash, banner })`; `TurfStage3D` props `flash: FlashSpec | null` and `onReady?: () => void`.

- [ ] **Step 1: Let the Dais sit at another distance and height**

In `controller/src/tv/turf3d/ui/Dais.tsx`: rename the module constant `DIST` to `DEFAULT_DIST`; extend the props to `{ panelKey, visible, dist = DEFAULT_DIST, offsetY = 0, children }` (typed `dist?: number; offsetY?: number`); in the frame use `const wpp = worldPerPx(cam.fov, dist, REF_H)`, position with `tmp.set(0, offsetY * wpp, -dist).applyQuaternion(cam.quaternion)` and `g.scale.setScalar(wpp * grow.current)`.

- [ ] **Step 2: The callouts**

```tsx
// controller/src/tv/turf3d/ui/Callouts.tsx
import { useEffect, useMemo } from 'react'
import * as THREE from 'three'
import { resolve, type FlashSpec } from './copy'
import { Dais } from './Dais'
import { Label } from './Label'
import { usePalette } from './palette'
import { INK, SHADOW } from './theme'

/** A spiky star centred on the origin: [spikes] points, inner valleys at [valley] of the outer radius. */
export function starGeometry(w: number, h: number, spikes = 16, valley = 0.78): THREE.ShapeGeometry {
  const s = new THREE.Shape()
  for (let i = 0; i < spikes * 2; i++) {
    const a = (i / (spikes * 2)) * Math.PI * 2, k = i % 2 === 0 ? 1 : valley
    const x = Math.cos(a) * (w / 2) * k, y = Math.sin(a) * (h / 2) * k
    if (i === 0) s.moveTo(x, y); else s.lineTo(x, y)
  }
  s.closePath()
  return new THREE.ShapeGeometry(s)
}

function Star({ w, h, color, x = 0, y = 0, z = 0 }: { w: number; h: number; color: string; x?: number; y?: number; z?: number }) {
  const geo = useMemo(() => starGeometry(w, h), [w, h])
  useEffect(() => () => geo.dispose(), [geo])
  return <mesh geometry={geo} position={[x, y, z]}><meshBasicMaterial color={color} toneMapped={false} /></mesh>
}

/** One callout as a star-burst sticker: the words in the middle, a hard shadow behind. */
function Burst({ f }: { f: FlashSpec }) {
  const p = usePalette()
  const w = f.small ? 440 : 880, h = f.small ? 240 : 420
  const fill = resolve(p, f.fill, '#ffd23f'), ink = resolve(p, f.ink ?? 'var(--ink)', INK)
  return (
    <group rotation-z={0.07}>
      <Star w={w + 24} h={h + 24} color={SHADOW} x={14} y={-14} z={-3} />
      <Star w={w + 24} h={h + 24} color={INK} z={-2} />
      <Star w={w} h={h} color={fill} z={-1} />
      <Label px={f.small ? 72 : 112} kind="hero" color={ink} y={f.sub ? 30 : 0} maxWidth={w * 0.66}>{f.text}</Label>
      {f.sub && <Label px={30} kind="body" font="bodyBold" color={ink} y={f.small ? -50 : -64} maxWidth={w * 0.6}>{f.sub}</Label>}
    </group>
  )
}

/** The flash callout and the total banner, each on its own Dais so they can sit in front of and above the card. */
export function Callouts({ flash, banner }: { flash: FlashSpec | null; banner: string | null }) {
  return (
    <>
      <Dais panelKey={flash ? String(flash.id) : null} visible dist={8.4} offsetY={flash?.small ? 250 : 0}>
        {() => (flash ? <Burst f={flash} /> : null)}
      </Dais>
      <Dais panelKey={banner} visible dist={8.7} offsetY={380}>
        {(text) => <Label px={128} kind="hero" color="#ffffff" outline={INK} maxWidth={1500}>{text}</Label>}
      </Dais>
    </>
  )
}
```

- [ ] **Step 3: Wire them**

In `controller/src/tv/turf3d/TurfScene.tsx`: import `Callouts` and `type FlashSpec`; add `flash: FlashSpec | null` to the props; add `<Callouts flash={flash} banner={craft.banner} />` right after `<PanelHost .../>`.

In `controller/src/tv/turf3d/TurfStage3D.tsx`: add props `flash: FlashSpec | null` and `onReady?: () => void`; pass `flash={flash}` to `<TurfScene>`; make the readiness handler `() => { setPanelReady(true); onReady?.() }`; and hide the DOM banner text once 3D text is ready: render `{!panelReady && craft.banner}` inside `.turf-banner3d`.

In `controller/src/tv/TurfStage.tsx`, in `Turf`: add `const [ready3d, setReady3d] = useState(false)`; pass `flash={flash}` and `onReady={() => setReady3d(true)}` to `<TurfStage3D>`; and change the flash render to `<AnimatePresence>{flash && !(use3d && ready3d) && <FlashView key={flash.id} f={flash} />}</AnimatePresence>`.

- [ ] **Step 4: Type-check, build, and look**

Run: `cd /Users/jjahn/HoopDreams/controller && npx tsc -b && npx vitest run 2>&1 | grep -E "Tests " && npx vite build 2>&1 | grep -E "built|rror"`
Expected: no tsc output; tests pass; `✓ built in`.

Then look with `ui-look.cjs` at: `["diced-move",3400]` (the total banner, no card), `["rent",4200]`, `["bankrupt-fall",4200]`, `["card-moment",4200]` (callout and card together). The gallery injects beats about 0.7 s after mount and the 3D text needs about 3 s, so a callout can end before the text is ready (rent lasts 1.7 s); if so, add a per-fixture `delay` field to `TurfGallery.tsx`'s `BEATS` entries (default 700) read by the injection effect, and set it to 3500 for the fixtures you are capturing. Expected: a spiky sticker with the words fully inside the spikes, in the game's colours; the banner in big white outlined type near the top; on `card-moment` the callout sits in front of the card without either hiding the other's text.

- [ ] **Step 5: Commit**

```bash
cd /Users/jjahn/HoopDreams
git add controller/src
git commit -m "feat(turf3d): the total banner and the flash callouts as camera-locked 3D stickers"
```

---

### Task 4: Screenshots, checks, docs

**Files:**
- Modify: `controller/scripts/turf3d-shots.mjs`, `README.md`

- [ ] **Step 1: Capture the new panels and callouts**

In `controller/scripts/turf3d-shots.mjs`, add `['buy', 5000], ['auction', 5000], ['card', 5000]` to the panel shots (after `['deal', 5000]`), remove the earlier `['buy', 3500]` entry, and replace the earlier rent, bankrupt-fall and card-moment entries with `['rent', 4200], ['bankrupt-fall', 4200], ['card-moment', 4200]` (the callouts need the 3D text to have synced). Keep every other entry.

- [ ] **Step 2: Run it and read the frames**

Run: `cd /Users/jjahn/HoopDreams/controller && uptime && npx vite build 2>&1 | grep -E "built|rror" && OUT=$SP/final-hero node scripts/turf3d-shots.mjs 2>&1 | tail -32`
Expected: `ok` for every shot with no `OFF-ORIGIN`; the three fps lines with `balanced` at 50 or better and `p95ms` 25 or under. Read the new frames for legibility.

- [ ] **Step 3: Document**

In `README.md`, in the **In 3D** bullet, extend the panel sentence to include the buy, auction and card panels, the total banner and the flash callouts as 3D, and shorten the "still use the flat well" list to debt, trade, tally and team-up.

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

```bash
cd /Users/jjahn/HoopDreams
rm -rf controller/turf3d-shots
git add controller/scripts/turf3d-shots.mjs README.md
git commit -m "feat(turf3d): screenshot the hero panels and callouts, and document them"
```
