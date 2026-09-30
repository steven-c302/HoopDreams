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
