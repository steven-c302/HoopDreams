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
