// controller/scripts/turf3d-shots.mjs
// Screenshots every Home Turf 3D gallery beat at 1920x1080 and measures the frame rate over a rolled move.
// Usage (with `npx vite preview --port 4173` running, and nothing else heavy on the Mac):
//   node scripts/turf3d-shots.mjs            -> shots in ./turf3d-shots and one fps line per quality level
//   EXTRA='&quality=high' node scripts/turf3d-shots.mjs
import { mkdirSync } from 'node:fs'
import { chromium } from '@playwright/test'

const base = process.env.BASE ?? 'http://127.0.0.1:4173'
const out = process.env.OUT ?? 'turf3d-shots'
const extra = process.env.EXTRA ?? ''
mkdirSync(out, { recursive: true })

const browser = await chromium.launch({ args: ['--use-angle=metal', '--enable-gpu', '--ignore-gpu-blocklist'] })
const open = async (beat, query = '') => {
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } })
  page.errors = []
  page.offOrigin = []
  page.on('request', (r) => { const u = r.url(); if (!u.startsWith(base) && !u.startsWith('data:') && !u.startsWith('blob:')) page.offOrigin.push(u) })
  page.on('pageerror', (e) => page.errors.push(String(e)))
  page.on('console', (m) => m.type() === 'error' && page.errors.push(m.text()))
  await page.goto(`${base}/tv?gallery=turf&beat=${beat}${query}`)
  return page
}

// [beat, ms after the page opens]. The gallery injects each beat about 0.7 s after the stage mounts, so effects
// start near 1.2 s; the times below catch each one in the middle of playing. The 3D cards need about 3 s for their
// text to lay out (the flat well shows until then), so the panel shots wait 5 s.
const shots = [
  ['lineup', 5000], ['roll', 5000], ['doubles-roll', 5000], ['manage', 5000], ['jail', 5000], ['choose', 5000], ['pieces', 5000], ['deal', 5000],
  ['diced-move', 2300], ['diced-move', 3400], ['doubles-move', 3400],
  ['rent', 1900], ['tax', 1900], ['payday-pass', 2400], ['card-moment', 2100], ['bankrupt-fall', 2000],
  ['jail-walk', 4600], ['build', 1500], ['buy', 3500],
]
for (const [beat, ms] of shots) {
  const page = await open(beat, extra)
  await page.waitForTimeout(ms)
  await page.screenshot({ path: `${out}/${beat}-${ms}.png` })
  console.log(`${beat}@${ms}`.padEnd(20), page.errors.length ? `ERRORS ${page.errors.join(' | ')}` : 'ok', page.offOrigin.length ? `OFF-ORIGIN ${page.offOrigin.join(' ')}` : '')
  await page.close()
}

for (const quality of ['high', 'balanced', 'low']) {
  const page = await open('diced-move', `&quality=${quality}`)
  await page.evaluate(() => {
    window.__ft = []
    let last = performance.now()
    const tick = () => { const n = performance.now(); window.__ft.push(n - last); last = n; requestAnimationFrame(tick) }
    requestAnimationFrame(tick)
  })
  await page.waitForTimeout(9000) // the fixture injects the roll at 0.7 s; a 9-space Theatre move takes about 6.5 s
  const r = await page.evaluate(() => {
    const a = window.__ft.slice(5).sort((x, y) => x - y)
    const avg = a.reduce((s, v) => s + v, 0) / a.length
    return { fps: Math.round(1000 / avg), p95ms: Math.round(a[Math.floor(a.length * 0.95)]), worstMs: Math.round(a[a.length - 1]) }
  })
  console.log(`fps ${quality.padEnd(8)}`, JSON.stringify(r), page.errors.length ? `ERRORS ${page.errors.join(' | ')}` : 'ok')
  await page.close()
}
await browser.close()
