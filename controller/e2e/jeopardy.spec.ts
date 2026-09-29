import { expect, test, type Locator } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

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
  const hint = tv.getByText('Arrow keys to move, Enter to pick a clue.')
  await expect(hint).toBeVisible()
  expect(await brightness(hint), 'the pick hint is too dark to read on the dark TV').toBeGreaterThan(0.6)

  await tv.keyboard.press('Enter') // the first clue: Food & Drink for $200, answer "Guacamole"
  await expect(tv.getByText('Food & Drink · $200')).toBeVisible()
  await ana.getByRole('textbox').fill('What is guacamole?')
  await ana.getByRole('button', { name: /Lock it in/ }).click()
  await bo.getByRole('textbox').fill('guacamole')
  await bo.getByRole('button', { name: /Lock it in/ }).click()
  await cy.getByRole('textbox').fill('nachos')
  await cy.getByRole('button', { name: /Lock it in/ }).click()

  // Everyone answered, so the reveal comes up by itself.
  await expect(tv.getByText('Ana +200')).toBeVisible()
  await expect(tv.getByText('Bo +200')).toBeVisible()
  await expect(tv.getByText('Cy +200')).toHaveCount(0)
  const gotIt = tv.getByText('GOT IT', { exact: true })
  await expect(gotIt).toBeVisible()
  expect(await brightness(gotIt), 'the GOT IT label is too dark to read on the dark TV').toBeGreaterThan(0.6)
  expect(errors).toEqual([])
})
