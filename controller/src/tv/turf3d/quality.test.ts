// controller/src/tv/turf3d/quality.test.ts
import { describe, expect, it } from 'vitest'
import { DEFAULT_QUALITY, parseQuality, usesAO, usesComposer, usesRealGlass } from './quality'

describe('quality', () => {
  it('reads ?quality= and falls back to the default', () => {
    expect(parseQuality('?quality=high')).toBe('high')
    expect(parseQuality('?x=1&quality=low')).toBe('low')
    expect(parseQuality('?quality=ultra')).toBe(DEFAULT_QUALITY)
    expect(parseQuality('')).toBe(DEFAULT_QUALITY)
  })
  it('drops effects in the order the spec says: ambient occlusion first, then real glass and the composer', () => {
    expect([usesAO('high'), usesAO('balanced'), usesAO('low')]).toEqual([true, false, false])
    expect([usesRealGlass('high'), usesRealGlass('balanced'), usesRealGlass('low')]).toEqual([true, true, false])
    expect([usesComposer('high'), usesComposer('balanced'), usesComposer('low')]).toEqual([true, true, false])
  })
})
