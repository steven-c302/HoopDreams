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

/** Greedy word wrap: as many words per line as fit [maxW] at [size]. */
function wrapWords(measure: (text: string, size: number) => number, words: string[], size: number, maxW: number): string[] {
  const lines: string[] = []
  let cur = ''
  for (const w of words) {
    const next = cur ? `${cur} ${w}` : w
    if (cur && measure(next, size) > maxW) { lines.push(cur); cur = w } else cur = next
  }
  if (cur) lines.push(cur)
  return lines
}

/**
 * Picks the largest name size, wrapping onto as many lines as needed, at which every line fits [maxW] and the lines
 * (plus a price line when [hasSub]) fit [maxH]. Never goes below [min], and never drops a word.
 */
export function layoutName(measure: (text: string, size: number) => number, text: string, maxW: number, maxH: number, start: number, min: number, hasSub: boolean): { size: number; lines: string[] } {
  const words = text.split(' ')
  for (let size = start; ; size -= 2) {
    const lines = wrapWords(measure, words, size, maxW)
    const used = lines.length * (size + 4) + (hasSub ? Math.round(size * 0.72) + 4 : 0)
    const widest = Math.max(...lines.map((l) => measure(l, size)))
    if ((widest <= maxW && used <= maxH) || size <= min) return { size: Math.max(size, min), lines }
  }
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
    const price = space.kind === 'tax' ? space.tax : space.price
    const sub = SUBTITLE[space.kind] ?? (price > 0 ? `$${price}` : '')
    const { size, lines } = layoutName(measure, space.label.toUpperCase(), box.w - 14, box.h - 10, corner ? 66 : 56, 24, !!sub)
    g.fillStyle = look.text; g.font = `${size}px ${look.nameFont}, sans-serif`
    const line = size + 4
    const total = lines.length + (sub ? 0.8 : 0)
    let y = box.y + box.h / 2 - ((total - 1) * line) / 2
    for (const w of lines) { g.fillText(w, box.x + box.w / 2, y); y += line }
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
