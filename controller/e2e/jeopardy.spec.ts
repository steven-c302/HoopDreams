import { expect, test, type Locator, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'
import pack from '../../tv/engine/src/main/resources/packs/jeopardy-core.json' with { type: 'json' }

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

/**
 * Perceived brightness (0-1) of an element's text colour. Browsers report colours in whatever notation the CSS used
 * (`oklch(...)` here), so the colour is painted onto a canvas and read back as plain sRGB.
 */
const brightness = (el: Locator) => el.evaluate((node) => {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 1
  const g = canvas.getContext('2d')!
  g.fillStyle = getComputedStyle(node).color
  g.fillRect(0, 0, 1, 1)
  const [r, gr, b] = g.getImageData(0, 0, 1, 1).data
  return (0.2126 * r + 0.7152 * gr + 0.0722 * b) / 255
})

async function ringIn(page: Page) {
  const button = page.getByRole('button', { name: 'BUZZ!', exact: true })
  await expect(button).toBeVisible({ timeout: 15_000 })
  await expect(button).toBeEnabled()
  // This button pulses continuously; waiting for a stable bounding box would miss the buzz window.
  await button.click({ force: true })
}

test('Answer & Question: pick from the TV, answer in the show\'s form, see who scored, all readable', async ({ browser }) => {
  test.setTimeout(120_000)
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const [ana, bo, cy] = await Promise.all(['Ana', 'Bo', 'Cy'].map((n) => phone(browser, room, n)))
  const tv = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage()
  const errors: string[] = []
  tv.on('pageerror', (e) => errors.push(e.message))
  await tv.goto('/tv')
  await tv.mouse.click(960, 540) // dismiss the GO LIVE gate
  await host.getByRole('button', { name: /Answer & Question/ }).click()
  for (const p of [ana, bo, cy]) await p.getByRole('button', { name: 'Ready!' }).click()

  // The board: the how-to-pick hint sits straight on the dark TV, so it must be light.
  const hint = tv.getByText('Arrow keys and Enter work here too.')
  await expect(hint).toBeVisible({ timeout: 20_000 })
  expect(await brightness(hint), 'the pick hint is too dark to read on the dark TV').toBeGreaterThan(0.6)

  // Categories are shuffled. The cheapest row has no Daily Doubles, so choose its first clue.
  const category = (await tv.locator('.jeo-head').first().textContent())!.trim()
  const clue = pack.categories.find((c) => c.name === category)!.clues[0]
  await tv.keyboard.press('Enter')
  await expect(tv.locator('.jeo-clue')).toHaveText(clue.clue)
  // A wrong answer loses the clue's value, and opens the buzzer again for the remaining players.
  await ringIn(cy)
  await cy.getByRole('textbox').fill('not the answer')
  await cy.getByRole('button', { name: /Lock it in/ }).click()
  await expect(cy.getByRole('button', { name: 'MISSED', exact: true })).toBeVisible()
  await ringIn(ana)
  await ana.getByRole('textbox').fill(`What is ${clue.answer}?`)
  await ana.getByRole('button', { name: /Lock it in/ }).click()

  // The first correct answer ends the clue; players who never rang in score nothing.
  await expect(tv.getByText('Ana +200')).toBeVisible()
  await expect(tv.getByText('Cy -200')).toBeVisible()
  await expect(tv.getByText('Bo +200')).toHaveCount(0)
  await expect(bo.getByRole('heading', { name: 'Eyes on the TV' })).toBeVisible()
  const gotIt = tv.getByText('Got it!', { exact: true })
  await expect(gotIt).toBeVisible()
  expect(await brightness(gotIt), 'the GOT IT label is too dark to read on the dark TV').toBeGreaterThan(0.6)
  expect(errors).toEqual([])
})
