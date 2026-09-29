import { expect, test, type Locator, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

const gallery = (beat: string) => `/tv?gallery=themes&game=imposter&beat=${beat}`
const phoneView = (beat: string) => `${gallery(beat)}&view=phone`

async function hold(page: Page, card: Locator) {
  const box = (await card.boundingBox())!
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.waitForTimeout(150) // let Chromium register the hover, or a move straight off sends no pointerleave
}

test.describe('the secret card', () => {
  test('the word is not on the page until the card is held, and goes again on release', async ({ page }) => {
    await page.goto(phoneView('role'))
    const card = page.getByRole('button', { name: 'Your secret card. Hold to peek.' })
    await expect(page.getByText('PIZZA')).toHaveCount(0)
    await hold(page, card)
    await expect(page.getByText('PIZZA')).toBeVisible()
    await page.mouse.up()
    await expect(page.getByText('PIZZA')).toHaveCount(0)
  })

  test('sliding off the card or losing focus drops the word while the finger is still down', async ({ page }) => {
    await page.goto(phoneView('role'))
    const card = page.locator('.secret-card')
    await hold(page, card)
    await expect(page.getByText('PIZZA')).toBeVisible()
    await page.mouse.move(2, 2)
    await expect(page.getByText('PIZZA')).toHaveCount(0)

    await hold(page, card)
    await expect(page.getByText('PIZZA')).toBeVisible()
    await page.evaluate(() => window.dispatchEvent(new Event('blur')))
    await expect(page.getByText('PIZZA')).toHaveCount(0)
    await page.mouse.up()
  })

  test('crew and imposter cards are the same markup face-down, and differ only while held', async ({ page }) => {
    const faceDown = async (beat: string) => {
      await page.goto(phoneView(beat))
      return page.locator('.secret-card').evaluate((el) => el.outerHTML)
    }
    expect(await faceDown('role')).toBe(await faceDown('imposter'))
    await page.goto(phoneView('imposter'))
    await hold(page, page.locator('.secret-card'))
    await expect(page.locator('.secret-card b')).toHaveText('IMPOSTER')
    await page.mouse.up()
  })

  test('in the clue phase the card shrinks to a chip above the clue field', async ({ page }) => {
    await page.goto(phoneView('clue'))
    await expect(page.locator('.secret-card.chip')).toBeVisible()
    const field = page.getByRole('textbox')
    await field.fill('cheesy')
    await page.getByRole('button', { name: 'Lock it in' }).click()
    await expect(page.getByRole('status')).toContainText('Preview only')
  })

  test('the vote list is thumb-sized', async ({ page }) => {
    await page.goto(phoneView('vote'))
    const picks = page.locator('.choices.faces .face-pick')
    await expect(picks).toHaveCount(5)
    for (const p of await picks.all()) expect((await p.boundingBox())!.height).toBeGreaterThanOrEqual(44)
  })
})

test.describe('the TV stage', () => {
  const fits = async (el: Locator) => {
    const b = (await el.boundingBox())!
    expect(b.x).toBeGreaterThanOrEqual(0)
    expect(b.y).toBeGreaterThanOrEqual(0)
    expect(b.x + b.width).toBeLessThanOrEqual(1280.5)
    expect(b.y + b.height).toBeLessThanOrEqual(720.5)
  }

  test('the clue wall fits six players', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('discuss'))
    const placards = page.locator('.imp-placard')
    await expect(placards).toHaveCount(6)
    await page.waitForTimeout(1200) // the placards deal in
    for (const p of await placards.all()) await fits(p)
    await expect(page.getByText('delivery')).toBeVisible()
  })

  test('the result names the imposter, the drinks, and keeps the word back for the guess', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('result'))
    await expect(page.getByText('CAUGHT!', { exact: true })).toBeVisible()
    await expect(page.locator('.imp-drinks li')).toHaveCount(2)
    await expect(page.locator('.stamp', { hasText: 'IMPOSTER' })).toHaveCount(1) // not the header, which also says IMPOSTER
    await expect(page.getByText('The word was')).toHaveCount(0)
  })
})

test('four phones play a round of Imposter: look, clue, discuss, vote, result', async ({ browser }) => {
  test.setTimeout(180_000)
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const phones = await Promise.all(['Ana', 'Bo', 'Cy', 'Di'].map((n) => phone(browser, room, n)))
  await expect(host.locator('.players li')).toHaveCount(4)
  // A real TV page watches the whole round, fed by live game state rather than the gallery.
  const tv = await (await browser.newContext({ viewport: { width: 1280, height: 720 } })).newPage()
  const tvErrors: string[] = []
  tv.on('pageerror', (e) => tvErrors.push(e.message))
  await tv.goto('/tv')
  await host.getByRole('button', { name: /Imposter/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()

  // Look: each phone holds its card, then says it has seen it. The last "Got it" moves the room on.
  for (const p of phones) {
    await hold(p, p.locator('.secret-card'))
    await expect(p.locator('.secret-card b')).toBeVisible()
    await p.mouse.up()
    await p.getByRole('button', { name: 'Got it' }).click()
  }

  // Clue: everyone types one word (never the secret word, which no phone types).
  for (const [i, p] of phones.entries()) {
    await p.getByRole('textbox').fill(`hint${i}`)
    await p.getByRole('button', { name: 'Lock it in' }).click()
  }

  // The TV shows the clue wall: one placard per player, each with the clue they typed.
  await expect(tv.locator('.imp-placard')).toHaveCount(4, { timeout: 30_000 })
  for (const i of [0, 1, 2, 3]) await expect(tv.locator('.imp-clue', { hasText: `hint${i}` })).toBeVisible()

  // Discuss runs its 60 s, then everyone votes for the first face.
  for (const p of phones) await expect(p.getByRole('heading', { name: 'Who is the imposter?' })).toBeVisible({ timeout: 90_000 })
  for (const p of phones) await p.locator('.face-pick').first().click()

  for (const p of phones) {
    await expect(p.getByRole('heading', { name: /You fooled them|You were caught|You found the imposter|Wrong suspect/ })).toBeVisible({ timeout: 20_000 })
  }

  // The TV names the outcome, and nothing threw while it rendered live state.
  await expect(tv.getByText(/^(CAUGHT!|WRONG SUSPECT!|NOBODY GOT ACCUSED)$/)).toBeVisible({ timeout: 20_000 })
  expect(tvErrors).toEqual([])
})
