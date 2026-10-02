#!/usr/bin/env node
// Renders every beat of a design gallery (BRAIN DRAIN by default, or Home Turf) at 1920×1080, for review and the README.
// Usage: node controller/scripts/shots.mjs [out=controller/test-results/shots] [http://127.0.0.1:8080] [beat,beat,…] [gallery=trivia|turf]
import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const out = process.argv[2] ?? 'controller/test-results/shots'
const base = process.argv[3] ?? 'http://127.0.0.1:8080'
const gallery = process.argv[5] ?? 'trivia'
const BEATS = {
  trivia: ['teamup', 'intro', 'quick-question', 'live-question', 'quick-reveal', 'ballpark-question', 'ballpark-reveal', 'ballpark-bet', 'ballpark-bet-reveal', 'sides-question',
    'sides-reveal', 'victim', 'steal', 'standings', 'write-question', 'write-reveal', 'gauntlet-question', 'gauntlet-reveal', 'podium', 'awards'],
  turf: ['teamup', 'pieces', 'deal', 'roll', 'move', 'buy', 'auction', 'card', 'trade', 'debt', 'manage', 'jail', 'rent', 'home-turf',
    'last-lap', 'bankrupt', 'tally', 'podium'],
}
const beats = BEATS[gallery]
mkdirSync(out, { recursive: true })
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } })
for (const beat of ((process.argv[4] && process.argv[4] !== 'all' ? process.argv[4].split(',') : beats))) {
  await page.goto(`${base}/tv?gallery=${gallery}&beat=${beat}`)
  await page.waitForTimeout(3200)
  await page.screenshot({ path: `${out}/${beat}.png` })
  console.log(`${out}/${beat}.png`)
}
await browser.close()
