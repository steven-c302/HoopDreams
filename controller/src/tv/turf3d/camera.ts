// controller/src/tv/turf3d/camera.ts
export type Shot = 'wide' | 'dice' | 'follow' | 'close'
export type V3 = [number, number, number]
export interface Pose { pos: V3; look: V3 }

const WIDE: V3 = [0, 15.4, 16.9] // backed off 10% so the board clears both rails by 24px at 1920x1080

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
