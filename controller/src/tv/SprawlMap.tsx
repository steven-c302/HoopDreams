import { useMemo, useRef, type CSSProperties, type PointerEvent } from 'react'
import type { SprawlMap as MapData } from '../protocol'
import { CityShape, LandlordShape, ResourceGlyph, SettlementShape, TERRAIN_FILL } from './SprawlArt'
import './sprawl-map.css'

const INK = 'var(--ink)'
/** Board units: a hex is 2 × 87 wide and 200 tall (the engine's SprawlGeometry). */
const CORNERS: [number, number][] = [[0, -100], [87, -50], [87, 50], [0, 100], [-87, 50], [-87, -50]]
const MARGIN = 110
/** How far from a spot a tap still picks it (board units): fat fingers, small phones. */
const REACH = { vertex: 70, edge: 60, hex: 115 } as const

export type SpotKind = 'vertex' | 'edge' | 'hex'

interface Props {
  map: MapData
  robber: number
  vOwner: number[]
  vLevel: number[]
  eOwner: number[]
  colors: string[]
  /** Legal spots to glow, and what they are. */
  spots?: number[]
  spotKind?: SpotKind
  selected?: number | null
  onPick?(spot: number): void
  /** The TV mirrors what the active phone is eyeing. */
  peek?: number
  peekKind?: SpotKind
  /** Hexes that just paid out. */
  hot?: number[]
  /** Place names on the hexes (the TV); phones skip them. */
  names?: boolean
  className?: string
  style?: CSSProperties
}

/** The island's drawing area in board units (the SVG viewBox). */
export function mapBox(map: MapData) {
  const xs = map.vertices.map((v) => v[0]), ys = map.vertices.map((v) => v[1])
  const x = Math.min(...xs) - MARGIN, y = Math.min(...ys) - MARGIN
  return { x, y, w: Math.max(...xs) + MARGIN - x, h: Math.max(...ys) + MARGIN - y }
}

/** Where a spot sits in board units: a hex's centre, a corner, or the middle of a side. */
export function spotPoint(map: MapData, kind: SpotKind, id: number): [number, number] {
  if (kind === 'hex') return [map.hexes[id].x, map.hexes[id].y]
  if (kind === 'vertex') return map.vertices[id]
  const [a, b] = map.edges[id]
  return [(map.vertices[a][0] + map.vertices[b][0]) / 2, (map.vertices[a][1] + map.vertices[b][1]) / 2]
}

/** Splits a place name onto at most two balanced lines. */
function twoLines(name: string): string[] {
  const words = name.split(' ')
  if (words.length < 2 || name.length <= 11) return [name]
  let best = 1, diff = Infinity
  for (let i = 1; i < words.length; i++) {
    const d = Math.abs(words.slice(0, i).join(' ').length - words.slice(i).join(' ').length)
    if (d < diff) { diff = d; best = i }
  }
  return [words.slice(0, best).join(' '), words.slice(best).join(' ')]
}

const pips = (n: number) => (n === 0 ? 0 : 6 - Math.abs(7 - n))

/** The island as an SVG: terrain, numbers, harbours, roads, buildings, The Landlord, and glowing spots to tap. */
export function SprawlMap({ map, robber, vOwner, vLevel, eOwner, colors, spots = [], spotKind, selected, onPick, peek = -1, peekKind, hot = [], names = false, className = '', style }: Props) {
  const svg = useRef<SVGSVGElement>(null)
  const down = useRef<{ x: number; y: number } | null>(null)
  const box = useMemo(() => mapBox(map), [map])
  const spotAt = (kind: SpotKind, id: number) => spotPoint(map, kind, id)

  const pick = (e: PointerEvent<SVGSVGElement>) => {
    if (!onPick || !spotKind || !spots.length || !svg.current) return
    // The end of a drag (scrolling the zoomed map) isn't a tap.
    const from = down.current
    down.current = null
    if (from && Math.hypot(e.clientX - from.x, e.clientY - from.y) > 12) return
    const ctm = svg.current.getScreenCTM()
    if (!ctm) return
    const p = new DOMPoint(e.clientX, e.clientY).matrixTransform(ctm.inverse())
    let best = -1, bestD = Infinity
    for (const id of spots) {
      const [x, y] = spotAt(spotKind, id)
      const d = Math.hypot(x - p.x, y - p.y)
      if (d < bestD) { bestD = d; best = id }
    }
    if (best >= 0 && bestD <= REACH[spotKind]) onPick(best)
  }

  const hexPoints = (x: number, y: number) => CORNERS.map(([dx, dy]) => `${x + dx},${y + dy}`).join(' ')
  const hotSet = new Set(hot)

  return (
    <svg ref={svg} className={`sp-map ${onPick ? 'interactive' : ''} ${className}`} style={style} viewBox={`${box.x} ${box.y} ${box.w} ${box.h}`} onPointerDown={(e) => { down.current = { x: e.clientX, y: e.clientY } }} onPointerUp={pick} role="img" aria-label="The island">
      {/* the island's shadow */}
      <g transform="translate(10 12)">{map.hexes.map((h, i) => <polygon key={i} points={hexPoints(h.x, h.y)} fill={INK} />)}</g>

      {map.harbours.map((hb, i) => {
        const [a, b] = map.edges[hb.edge]
        const [ax, ay] = map.vertices[a], [bx, by] = map.vertices[b]
        const mx = (ax + bx) / 2, my = (ay + by) / 2
        const len = Math.hypot(mx, my) || 1
        const px = mx + (mx / len) * 70, py = my + (my / len) * 70
        return (
          <g key={`h${i}`} className="sp-harbour">
            <path d={`M${ax} ${ay} L${px} ${py} L${bx} ${by}`} fill="none" stroke="var(--sp-jetty)" strokeWidth="12" strokeLinecap="round" strokeLinejoin="round" />
            <circle cx={px} cy={py} r="34" fill="var(--paper)" stroke={INK} strokeWidth="5" />
            {hb.kind < 0
              ? <text x={px} y={py + 11} textAnchor="middle" className="sp-ratio">3:1</text>
              : <>
                <g transform={`translate(${px - 19} ${py - 30}) scale(.38)`}><ResourceGlyph res={hb.kind} /></g>
                <text x={px} y={py + 26} textAnchor="middle" className="sp-ratio small">2:1</text>
              </>}
          </g>
        )
      })}

      {map.hexes.map((h, i) => {
        // With names (the TV) the number moves up so the name gets the hex's wide middle; phones centre the number.
        const numY = names ? -14 : 14
        return (
          <g key={i} className={`sp-hex ${hotSet.has(i) ? 'hot' : ''}`}>
            <polygon points={hexPoints(h.x, h.y)} fill={TERRAIN_FILL[h.terrain]} stroke={INK} strokeWidth="6" strokeLinejoin="round" />
            {h.terrain < 5 && <g transform={names ? `translate(${h.x - 24} ${h.y - 92}) scale(.48)` : `translate(${h.x - 30} ${h.y - 80}) scale(.6)`} opacity=".92"><ResourceGlyph res={h.terrain} /></g>}
            {h.number > 0 && (
              <g transform={`translate(${h.x} ${h.y + numY})`}>
                <circle r="30" fill="var(--paper)" stroke={INK} strokeWidth="5" />
                <text y="10" textAnchor="middle" className={`sp-num ${h.number === 6 || h.number === 8 ? 'red' : ''}`}>{h.number}</text>
                <g>{Array.from({ length: pips(h.number) }, (_, k) => <circle key={k} cx={(k - (pips(h.number) - 1) / 2) * 7} cy="20" r="2.6" fill={h.number === 6 || h.number === 8 ? 'var(--tomato)' : INK} />)}</g>
              </g>
            )}
          </g>
        )
      })}

      {/* Names go above every hex so a neighbour never covers them, sized to fit the hex's width. */}
      {names && map.hexes.map((h, i) => {
        const lines = twoLines(h.name)
        const size = Math.min(22, 150 / (Math.max(...lines.map((l) => l.length)) * 0.6))
        const first = lines.length > 1 ? 36 : 46
        return (
          <text key={`n${i}`} x={h.x} y={h.y + first} textAnchor="middle" className="sp-place" style={{ fontSize: size }}>
            {lines.map((line, k) => <tspan key={k} x={h.x} dy={k === 0 ? 0 : size + 1}>{line}</tspan>)}
          </text>
        )
      })}

      {eOwner.map((o, e) => {
        if (o < 0) return null
        const [a, b] = map.edges[e]
        const [ax, ay] = map.vertices[a], [bx, by] = map.vertices[b]
        const sx = ax + (bx - ax) * 0.16, sy = ay + (by - ay) * 0.16, ex = bx + (ax - bx) * 0.16, ey = by + (ay - by) * 0.16
        return (
          <g key={`e${e}`} className="sp-road">
            <path d={`M${sx} ${sy} L${ex} ${ey}`} stroke={INK} strokeWidth="24" strokeLinecap="round" />
            <path d={`M${sx} ${sy} L${ex} ${ey}`} stroke={colors[o] ?? 'var(--paper)'} strokeWidth="13" strokeLinecap="round" />
          </g>
        )
      })}

      {vOwner.map((o, v) => {
        if (o < 0) return null
        const [x, y] = map.vertices[v]
        return <g key={`v${v}`} transform={`translate(${x} ${y})`} className="sp-building">{vLevel[v] === 2 ? <CityShape color={colors[o]} scale={0.95} /> : <SettlementShape color={colors[o]} scale={0.95} />}</g>
      })}

      {/* The Landlord stands on the number: that hex doesn't pay while he's there. */}
      {robber >= 0 && map.hexes[robber] && (
        <g transform={`translate(${map.hexes[robber].x} ${map.hexes[robber].y + (names ? -22 : 6)})`}><LandlordShape scale={names ? 0.6 : 0.62} /></g>
      )}

      {spotKind && spots.map((id) => {
        const [x, y] = spotAt(spotKind, id)
        const on = id === selected
        if (spotKind === 'hex') return <circle key={`s${id}`} cx={x} cy={y} r={on ? 74 : 60} className={`sp-spot hex ${on ? 'on' : ''}`} />
        return <circle key={`s${id}`} cx={x} cy={y} r={on ? 30 : spotKind === 'edge' ? 17 : 21} className={`sp-spot ${on ? 'on' : ''}`} />
      })}

      {peekKind && peek >= 0 && (() => {
        const [x, y] = spotAt(peekKind, peek)
        return <circle cx={x} cy={y} r={peekKind === 'hex' ? 80 : 38} className="sp-peek" />
      })()}
    </svg>
  )
}
