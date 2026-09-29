import { expect, test } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

test('the captain sheet hides what is behind it: no sharp slivers of the screen poke out around the sheet', async ({ browser }) => {
  test.setTimeout(90_000)
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  // One after the other: the first phone to join holds the crown, so the order has to be certain.
  const captain = await phone(browser, room, 'Ana')
  const other = await phone(browser, room, 'Bo')
  await host.getByRole('button', { name: /Brain Drain/ }).click()
  for (const p of [captain, other]) await p.getByRole('button', { name: 'Ready!' }).click()

  // The team-picking screen puts big coloured buttons right where the sheet's top edge lands, the case that showed slivers.
  await expect(captain.getByRole('heading', { name: 'Pick a team' })).toBeVisible({ timeout: 30_000 })
  await captain.locator('.crown-fab').click()
  const scrim = captain.locator('.sheet-scrim')
  await expect(scrim).toBeVisible()
  await captain.waitForTimeout(500) // the sheet slides in

  const scrimStyle = await scrim.evaluate((el) => {
    const cs = getComputedStyle(el)
    return { blur: cs.backdropFilter || (cs as unknown as Record<string, string>).webkitBackdropFilter || '', bg: cs.backgroundColor }
  })
  expect(scrimStyle.blur, 'content behind the sheet must be blurred, not left sharp').toMatch(/blur\(/)

  // What is actually visible just above the sheet must be smooth: no hard dark-on-light edges from the screen below.
  const sheetTop = (await captain.locator('.sheet').boundingBox())!.y
  const png = await captain.screenshot({ clip: { x: 0, y: Math.max(0, sheetTop - 40), width: 390, height: 36 } })
  const sharpest = await captain.evaluate(async (b64) => {
    const img = new Image()
    img.src = `data:image/png;base64,${b64}`
    await img.decode()
    const c = document.createElement('canvas')
    c.width = img.width; c.height = img.height
    const g = c.getContext('2d')!
    g.drawImage(img, 0, 0)
    const d = g.getImageData(0, 0, c.width, c.height).data
    let worst = 0
    for (let y = 0; y < c.height; y++) for (let x = 1; x < c.width; x++) {
      const i = (y * c.width + x) * 4, j = i - 4
      worst = Math.max(worst, Math.abs(d[i] - d[j]) + Math.abs(d[i + 1] - d[j + 1]) + Math.abs(d[i + 2] - d[j + 2]))
    }
    return worst
  }, png.toString('base64'))
  expect(sharpest, 'a hard edge from the screen behind the sheet is showing above it').toBeLessThan(120)
})
