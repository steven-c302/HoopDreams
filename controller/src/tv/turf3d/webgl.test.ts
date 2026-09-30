// controller/src/tv/turf3d/webgl.test.ts
import { describe, expect, it } from 'vitest'
import { hasWebGL2, wants3d } from './webgl'

describe('webgl decision', () => {
  it('detects WebGL2 through a canvas factory', () => {
    expect(hasWebGL2(() => ({ getContext: (id) => (id === 'webgl2' ? {} : null) }))).toBe(true)
    expect(hasWebGL2(() => ({ getContext: () => null }))).toBe(false)
    expect(hasWebGL2(() => { throw new Error('no canvas') })).toBe(false)
  })
  it('uses 3D only when supported and not forced to 2D', () => {
    expect(wants3d('', true)).toBe(true)
    expect(wants3d('?board=2d', true)).toBe(false)
    expect(wants3d('', false)).toBe(false)
    expect(wants3d('?board=3d', false)).toBe(false)
  })
})
