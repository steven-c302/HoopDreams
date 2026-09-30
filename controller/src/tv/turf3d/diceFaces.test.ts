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
