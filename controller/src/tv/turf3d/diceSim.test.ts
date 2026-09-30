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
