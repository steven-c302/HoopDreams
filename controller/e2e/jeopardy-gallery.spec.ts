import { expect, test, type Locator } from '@playwright/test'

const gallery = (beat: string) => `/tv?gallery=themes&game=jeopardy&beat=${beat}`
const phoneView = (beat: string) => `${gallery(beat)}&view=phone`

/** Perceived brightness (0-1) of an element's text colour, read back through a canvas so any colour notation works. */
const brightness = (el: Locator) => el.evaluate((node) => {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 1
  const g = canvas.getContext('2d')!
  g.fillStyle = getComputedStyle(node).color
  g.fillRect(0, 0, 1, 1)
  const [r, gr, b] = g.getImageData(0, 0, 1, 1).data
  return (0.2126 * r + 0.7152 * gr + 0.0722 * b) / 255
})

test.describe('the phone board', () => {
  test('tap a square, then confirm: nothing is picked by a single tap', async ({ page }) => {
    await page.goto(phoneView('board'))
    await expect(page.locator('.jb-head')).toHaveCount(5)
    await expect(page.locator('.jb-cell')).toHaveCount(25)
    await expect(page.getByRole('button', { name: /^Pick / })).toHaveCount(0)
    await page.getByRole('button', { name: 'Sports for 400' }).click()
    const confirm = page.getByRole('button', { name: 'Pick Sports for $400' })
    await expect(confirm).toBeVisible()
    await confirm.click()
    await expect(page.getByRole('status')).toContainText('Preview only')
  })

  test('squares already played cannot be chosen', async ({ page }) => {
    await page.goto(phoneView('board'))
    await expect(page.locator('.jb-cell.used')).toHaveCount(2)
    for (const c of await page.locator('.jb-cell.used').all()) await expect(c).toBeDisabled()
  })

  test('someone who does not hold the board can look but not pick', async ({ page }) => {
    await page.goto(phoneView('boardwait'))
    await expect(page.getByText('Ben is picking')).toBeVisible()
    for (const c of await page.locator('.jb-cell').all()) await expect(c).toBeDisabled()
  })

  test('the captain sees who they are picking for', async ({ page }) => {
    await page.goto(phoneView('boardcaptain'))
    await page.getByRole('button', { name: 'Music for 600' }).click()
    await expect(page.getByRole('button', { name: 'Pick Music for $600 (for Ben)' })).toBeVisible()
  })

  test('the board fits a phone with thumb-sized squares and readable text', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto(phoneView('boardwait'))
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(375)
    for (const c of (await page.locator('.jb-cell').all()).slice(0, 5)) expect((await c.boundingBox())!.height).toBeGreaterThanOrEqual(44)
    expect(await brightness(page.locator('.muted, .jb-note').first()), 'note text is too dark on the dark stage').toBeGreaterThan(0.5)
  })
})

test.describe('the BUZZ button', () => {
  test('waits while the clue is read, then lights up', async ({ page }) => {
    await page.goto(phoneView('reading'))
    await expect(page.locator('.jz-buzz')).toContainText('WAIT')
    await page.goto(phoneView('open'))
    const buzz = page.locator('.jz-buzz')
    await expect(buzz).toContainText('BUZZ!')
    await expect(buzz).toBeEnabled()
    await buzz.click({ force: true }) // the open button pulses, so it is never "stable"
    await expect(page.getByRole('status')).toContainText('Preview only')
  })

  test('a locked-out phone opens itself when the lockout ends and the window is open', async ({ page }) => {
    await page.goto(phoneView('lockedlive'))
    await expect(page.locator('.jz-buzz')).toContainText('TOO EARLY')
    await expect(page.locator('.jz-buzz')).toContainText('BUZZ!', { timeout: 3_000 })
  })

  test('someone who lost the race or missed the clue cannot buzz', async ({ page }) => {
    await page.goto(phoneView('beaten'))
    await expect(page.locator('.jz-buzz')).toContainText('TOO SLOW')
    await expect(page.locator('.jz-buzz')).toBeDisabled()
    await page.goto(phoneView('tried'))
    await expect(page.locator('.jz-buzz')).toBeDisabled()
  })

  test('the buzz button is big', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto(phoneView('open'))
    const box = (await page.locator('.jz-buzz').boundingBox())!
    expect(box.height).toBeGreaterThan(300)
    expect(box.width).toBeGreaterThan(300)
  })
})

test('wagers and answers use the keypad and the text box already on the phone', async ({ page }) => {
  await page.goto(phoneView('wager'))
  await expect(page.getByText('How much do you wager?')).toBeVisible()
  await expect(page.locator('.numpad')).toBeVisible()
  await page.goto(phoneView('answer'))
  await expect(page.getByRole('textbox')).toBeVisible()
})
