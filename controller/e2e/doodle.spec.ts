import { expect, test, type Locator, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

const gallery = (beat: string) => `/tv?gallery=themes&game=doodle&beat=${beat}`
const phoneView = (beat: string) => `${gallery(beat)}&view=phone`

/** How many pixels of a canvas have been drawn on. */
const inkPixels = (canvas: Locator) =>
  canvas.evaluate((c: HTMLCanvasElement) => {
    const d = c.getContext('2d')!.getImageData(0, 0, c.width, c.height).data
    let n = 0
    for (let i = 3; i < d.length; i += 4) if (d[i] > 0) n++
    return n
  })

async function scribble(page: Page, canvas: Locator) {
  const box = (await canvas.boundingBox())!
  await page.mouse.move(box.x + 30, box.y + 30)
  await page.mouse.down()
  for (let i = 1; i <= 20; i++) await page.mouse.move(box.x + 30 + i * 12, box.y + 30 + Math.sin(i / 2) * 40)
  await page.mouse.up()
}

test.describe('the drawing pad', () => {
  test('a stroke paints on the pad, and undo takes it back off', async ({ page }) => {
    await page.goto(phoneView('drawer'))
    const canvas = page.locator('.draw-canvas')
    await expect(canvas).toBeVisible()
    expect(await inkPixels(canvas)).toBe(0)
    await expect(page.getByRole('button', { name: 'Undo' })).toBeDisabled()
    await scribble(page, canvas)
    expect(await inkPixels(canvas)).toBeGreaterThan(500)
    await page.getByRole('button', { name: 'Undo' }).click()
    expect(await inkPixels(canvas)).toBe(0)
  })

  test('there are eight crayons and three brushes, all thumb-sized', async ({ page }) => {
    await page.goto(phoneView('drawer'))
    await expect(page.getByRole('radio', { name: /^(Brown|Red|Orange|Yellow|Green|Blue|Purple|Pink)$/ })).toHaveCount(8)
    await expect(page.getByRole('radio', { name: /^(Thin|Medium|Fat)$/ })).toHaveCount(3)
    for (const b of await page.locator('.crayon, .brush, .tool').all()) expect((await b.boundingBox())!.height).toBeGreaterThanOrEqual(44)
    await page.getByRole('radio', { name: 'Blue' }).click()
    await expect(page.getByRole('radio', { name: 'Blue' })).toHaveAttribute('aria-checked', 'true')
  })

  test('the word shows to the drawer', async ({ page }) => {
    await page.goto(phoneView('drawer'))
    await expect(page.locator('.draw-word b')).toHaveText('house')
  })
})

test.describe('the guess pad', () => {
  test('shows the blanks, clears the field after a guess, and never shows the word', async ({ page }) => {
    await page.goto(phoneView('draw'))
    await expect(page.locator('.blank-cell')).toHaveCount(5)
    await expect(page.locator('.blank-cell.shown')).toHaveText(['H', 'E'])
    const field = page.getByLabel('Your guess')
    await field.fill('barn')
    await page.getByRole('button', { name: 'Guess' }).click()
    await expect(field).toHaveValue('')
    await expect(page.getByText('house', { exact: true })).toHaveCount(0)
  })

  test('once you have it the pad says so and the field goes away', async ({ page }) => {
    await page.goto(phoneView('solved'))
    await expect(page.getByText('You got it!')).toBeVisible()
    await expect(page.getByText('+850')).toBeVisible()
    await expect(page.getByLabel('Your guess')).toHaveCount(0)
  })

  test('the word list shows how hard each word is', async ({ page }) => {
    await page.goto(phoneView('pickme'))
    await expect(page.locator('.choice-detail')).toHaveText(['Easy', 'Medium', 'Hard'])
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

  test('the draw scene has the easel with the picture, hint letters, wrong-guess bubbles and who has it', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('draw'))
    await expect(page.locator('.dd-blanks .blank-cell')).toHaveCount(5)
    await expect(page.locator('.dd-blanks .blank-cell.shown')).toHaveText(['H', 'E'])
    await expect(page.locator('.dd-bubble')).toHaveCount(5)
    await expect(page.locator('.dd-solver')).toHaveCount(2)
    await page.waitForTimeout(1200) // the bubbles spring in
    await fits(page.locator('.dd-frame'))
    for (const b of await page.locator('.dd-bubble').all()) await fits(b)
    expect(await inkPixels(page.locator('.dd-paper canvas'))).toBeGreaterThan(2000)
    await expect(page.getByText('HOUSE', { exact: true })).toHaveCount(0) // the word is not on the TV mid-draw
  })

  test('the pick scene shows three face-down cards and who is choosing', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('pick'))
    await expect(page.locator('.dd-card')).toHaveCount(3)
    await expect(page.getByText('is picking a word')).toBeVisible()
  })

  test('the reveal names the word, replays the drawing and lists who got it and who drinks', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('reveal'))
    await expect(page.locator('.dd-word b')).toHaveText('HOUSE')
    await expect(page.locator('.dd-results li')).toHaveCount(3)
    await expect(page.locator('.dd-drinks li')).toHaveCount(2)
    const canvas = page.locator('.dd-paper canvas')
    await page.waitForTimeout(4_500) // the time-lapse runs about 3 seconds
    const after = await inkPixels(canvas)
    expect(after).toBeGreaterThan(2000)
    await fits(page.locator('.dd-frame'))
  })

  test('the gallery hangs every drawing of the night', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('gallery'))
    const shots = page.locator('.dd-shot')
    await expect(shots).toHaveCount(5)
    await page.waitForTimeout(1500)
    for (const s of await shots.all()) await fits(s)
    await expect(page.locator('.dd-shot figcaption b').first()).toHaveText('house')
  })

  test('the podium comes before the gallery', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(gallery('podium'))
    await expect(page.locator('.podium-block')).toHaveCount(3)
    await expect(page.locator('.dd-shot')).toHaveCount(0)
  })
})

test('three phones play a turn of Doodle Dash: pick, draw live on the TV, guess, reveal', async ({ browser }) => {
  test.setTimeout(180_000)
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const phones = await Promise.all(['Ana', 'Bo', 'Cy'].map((n) => phone(browser, room, n)))
  await expect(host.locator('.players li')).toHaveCount(3)
  // A real TV page watches the turn, fed by live game state and the ink channel.
  const tv = await (await browser.newContext({ viewport: { width: 1280, height: 720 } })).newPage()
  const tvErrors: string[] = []
  tv.on('pageerror', (e) => tvErrors.push(e.message))
  await tv.goto('/tv')
  await host.getByRole('button', { name: /Doodle Dash/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()

  // Whoever is drawing gets the word list.
  let drawer!: Page
  await expect.poll(async () => {
    for (const p of phones) if (await p.getByRole('heading', { name: 'Pick a word to draw' }).isVisible()) { drawer = p; return true }
    return false
  }, { timeout: 30_000 }).toBe(true)
  const guessers = phones.filter((p) => p !== drawer)
  await drawer.locator('.choice', { hasText: 'Medium' }).click()
  await expect(drawer.locator('.draw-canvas')).toBeVisible()
  const word = ((await drawer.locator('.draw-word b').textContent()) ?? '').trim()
  expect(word.length).toBeGreaterThan(2)

  // The TV shows blanks, never the word, and the ink as it is drawn.
  await expect(tv.locator('.dd-blanks .blank-cell').first()).toBeVisible({ timeout: 20_000 })
  expect(await tv.locator('.dd-blanks .blank-cell').count()).toBe(word.replace(/ /g, '').length)
  await expect(tv.getByText(word, { exact: true })).toHaveCount(0)
  await scribble(drawer, drawer.locator('.draw-canvas'))
  await expect.poll(() => inkPixels(tv.locator('.dd-paper canvas')), { timeout: 10_000 }).toBeGreaterThan(500)

  // A wrong guess floats across the TV; the right one locks the phone.
  const first = guessers[0]
  await first.getByLabel('Your guess').fill('zzzzz')
  await first.getByRole('button', { name: 'Guess' }).click()
  await expect(tv.locator('.dd-bubble', { hasText: 'zzzzz' })).toBeVisible({ timeout: 10_000 })
  for (const [i, g] of guessers.entries()) {
    await g.getByLabel('Your guess').fill(word)
    await g.getByRole('button', { name: 'Guess' }).click()
    // The last guess ends the draw at once, so only the earlier guessers ever sit on the locked pad.
    if (i < guessers.length - 1) await expect(g.getByText('You got it!')).toBeVisible({ timeout: 10_000 })
  }

  // Everyone has it: the draw ends early and the TV reveals the word.
  await expect(tv.locator('.dd-word b')).toHaveText(word.toUpperCase(), { timeout: 20_000 })
  await expect(drawer.getByRole('heading', { name: /^It was/ })).toBeVisible({ timeout: 20_000 })
  expect(tvErrors).toEqual([])
})
