import { readFileSync } from 'node:fs'
import { expect, test, type Locator, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

/** Perceived brightness (0-1) of an element's text colour, read through a canvas because Chrome reports `oklch()`. */
const brightness = (el: Locator) => el.evaluate((node) => {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 1
  const g = canvas.getContext('2d')!
  g.fillStyle = getComputedStyle(node).color
  g.fillRect(0, 0, 1, 1)
  const [r, gr, b] = g.getImageData(0, 0, 1, 1).data
  return (0.2126 * r + 0.7152 * gr + 0.0722 * b) / 255
})

/** Every clue in the shipped pack, so the test can type the real answer for whatever the board serves up. */
const pack = JSON.parse(readFileSync('../tv/engine/src/main/resources/packs/jeopardy-core.json', 'utf8')) as {
  categories: { name: string; clues: { id: string; clue: string; answer: string }[] }[]
}
const answerFor = (clue: string) => pack.categories.flatMap((c) => c.clues).find((c) => c.clue === clue)!.answer

/** Pick the first square on the current board from this phone. */
async function pickFirst(page: Page) {
  const cell = page.locator('.jb-cell:not([disabled])').first()
  await expect(cell).toBeVisible({ timeout: 20_000 }) // the intro plays first
  await cell.click()
  await page.getByRole('button', { name: /^Pick / }).click()
}

test('Answer & Question: the captain picks from the phone, a wrong buzz costs points, a right one wins the board', async ({ browser }) => {
  test.setTimeout(150_000)
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const ana = await phone(browser, room, 'Ana') // the first phone in holds the crown
  const bo = await phone(browser, room, 'Bo')
  const cy = await phone(browser, room, 'Cy')
  const tv = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage()
  const errors: string[] = []
  tv.on('pageerror', (e) => errors.push(e.message))
  await tv.goto('/tv')
  await tv.mouse.click(960, 540) // dismiss the GO LIVE gate
  await host.getByRole('button', { name: /Answer & Question/ }).click() // the host page starts the game, as the party spec does
  for (const p of [ana, bo, cy]) await p.getByRole('button', { name: 'Ready!' }).click()

  // The board is on the TV; only Ana's phone can pick, and the TV says so in a readable colour.
  await expect(tv.locator('.jeo-head')).toHaveCount(5)
  await expect(tv.locator('.jeo-cell')).toHaveCount(25)
  const hint = tv.locator('.jeo-note').first()
  await expect(hint).toContainText('Ana has the board', { timeout: 20_000 })
  expect(await brightness(hint), 'the pick hint is too dark on the stage').toBeGreaterThan(0.6)
  await expect(bo.locator('.jb-cell:not([disabled])')).toHaveCount(0)

  await pickFirst(ana)
  const clue = tv.locator('.jeo-clue')
  await expect(clue).toBeVisible()
  const text = (await clue.textContent())!.trim()
  const answer = answerFor(text)

  // Buzzing before the reading is over locks that phone out; Cy waits.
  await expect(cy.locator('.jz-buzz')).toContainText('WAIT')
  await bo.locator('.jz-buzz').click({ force: true }) // pulsing button is never "stable"
  await expect(bo.locator('.jz-buzz')).toContainText('TOO EARLY')

  // Then the buzzers open. Cy buzzes and answers wrong.
  await expect(cy.locator('.jz-buzz')).toContainText('BUZZ!', { timeout: 15_000 })
  await cy.locator('.jz-buzz').click({ force: true }) // pulsing button is never "stable"
  await cy.getByRole('textbox').fill('a completely wrong answer')
  await cy.getByRole('button', { name: /Lock it in/ }).click()

  // A wrong answer costs the value and reopens the buzzers to everyone who has not tried.
  await expect(tv.locator('.jeo-chip', { hasText: 'Cy' })).toContainText('-$', { timeout: 10_000 })
  await expect(ana.locator('.jz-buzz')).toContainText('BUZZ!', { timeout: 10_000 })
  await expect(cy.locator('.jz-buzz')).toContainText('MISSED')
  await ana.locator('.jz-buzz').click({ force: true }) // pulsing button is never "stable"
  await ana.getByRole('textbox').fill(`What is ${answer}?`)
  await ana.getByRole('button', { name: /Lock it in/ }).click()

  // The right answer, given in the show's form, is revealed with who scored and lost.
  await expect(tv.locator('.jeo-answer')).toContainText(answer, { timeout: 10_000 })
  await expect(tv.locator('.jeo-delta', { hasText: 'Ana' })).toContainText('+')
  await expect(tv.locator('.jeo-delta', { hasText: 'Cy' })).toContainText('-')
  const gotIt = tv.getByText('Got it!', { exact: true })
  await expect(gotIt).toBeVisible()
  expect(await brightness(gotIt), 'the verdict is too dark on the stage').toBeGreaterThan(0.6)

  // Ana answered right, so she keeps the board and picks again.
  await expect(tv.locator('.jeo-cell.used')).toHaveCount(1, { timeout: 15_000 })
  await expect(tv.locator('.jeo-chip.holds')).toContainText('Ana')
  await expect(ana.locator('.jb-cell:not([disabled])').first()).toBeVisible()
  expect(errors).toEqual([])
})

test('Answer & Question: the captain can pick when someone else has the board', async ({ browser }) => {
  test.setTimeout(150_000)
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const ana = await phone(browser, room, 'Ana')
  const bo = await phone(browser, room, 'Bo')
  const cy = await phone(browser, room, 'Cy')
  const tv = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage()
  await tv.goto('/tv')
  await tv.mouse.click(960, 540)
  await host.getByRole('button', { name: /Answer & Question/ }).click() // the host page starts the game, as the party spec does
  for (const p of [ana, bo, cy]) await p.getByRole('button', { name: 'Ready!' }).click()

  // Ana holds the board first. Bo buzzes in and answers right, so Bo takes the board.
  await pickFirst(ana)
  const text = (await tv.locator('.jeo-clue').textContent())!.trim()
  await expect(bo.locator('.jz-buzz')).toContainText('BUZZ!', { timeout: 15_000 })
  await bo.locator('.jz-buzz').click({ force: true }) // pulsing button is never "stable"
  await bo.getByRole('textbox').fill(answerFor(text))
  await bo.getByRole('button', { name: /Lock it in/ }).click()
  await expect(tv.locator('.jeo-chip.holds')).toContainText('Bo', { timeout: 15_000 })

  // Bo picks on Bo's own phone, but Ana, the captain, can also pick "for Bo" and the TV moves on either way.
  await expect(bo.locator('.jb-cell:not([disabled])').first()).toBeVisible({ timeout: 15_000 }) // after the reveal
  const cell = ana.locator('.jb-cell:not([disabled])').first()
  await expect(cell).toBeVisible()
  await cell.click()
  await expect(ana.getByRole('button', { name: /\(for Bo\)/ })).toBeVisible()
  await ana.getByRole('button', { name: /^Pick / }).click()
  await expect(tv.locator('.jeo-clue')).toBeVisible()
  await expect(bo.locator('.jz-buzz')).toBeVisible() // the board is off screen while a clue is up
})
