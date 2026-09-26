#!/usr/bin/env node
// Renders every BRAIN DRAIN beat from the design gallery at 1920×1080, for review and the README.
// Usage: node controller/scripts/shots.mjs [out=controller/test-results/shots] [http://127.0.0.1:8080]
import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const out = process.argv[2] ?? 'controller/test-results/shots'
const base = process.argv[3] ?? 'http://127.0.0.1:8080'
const beats = ['teamup', 'intro', 'quick-question', 'quick-reveal', 'ballpark-question', 'ballpark-reveal', 'sides-question', 'sides-reveal',
  'victim', 'steal', 'standings', 'gauntlet-question', 'gauntlet-reveal', 'podium']
mkdirSync(out, { recursive: true })
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } })
for (const beat of (process.argv[4]?.split(',') ?? beats)) {
  await page.goto(`${base}/tv?gallery=trivia&beat=${beat}`)
  await page.waitForTimeout(3200)
  await page.screenshot({ path: `${out}/${beat}.png` })
  console.log(`${out}/${beat}.png`)
}
await browser.close()
