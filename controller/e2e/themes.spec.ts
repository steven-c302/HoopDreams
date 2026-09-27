import { expect, test, type Locator } from '@playwright/test'

for (const game of ['turf', 'sprawl']) {
  test(`${game}: six players fit on TV and the paused dial is stable`, async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(`/tv?gallery=themes&game=${game}`)
    const cards = page.locator(game === 'turf' ? '.turf-card' : '.sp-seat')
    await expect(cards).toHaveCount(6)
    await expect(page.locator('.game-mark')).toBeVisible()
    await expect(page.locator('.game-dial span')).toHaveText('18')
    // Bounding boxes rather than toBeInViewport({ ratio: 1 }): the scaled stage gives sub-pixel ratios like 0.99999994.
    const fits = async (el: Locator) => {
      const b = await el.boundingBox()
      expect(b).not.toBeNull()
      expect(b!.x).toBeGreaterThanOrEqual(0)
      expect(b!.y).toBeGreaterThanOrEqual(0)
      expect(b!.x + b!.width).toBeLessThanOrEqual(1280.5)
      expect(b!.y + b!.height).toBeLessThanOrEqual(720.5)
    }
    for (const card of await cards.all()) await fits(card)
    await fits(page.locator(game === 'turf' ? '.turf-viewport' : '.sp-map'))
    await page.waitForTimeout(1100)
    await expect(page.locator('.game-dial span')).toHaveText('18')
  })

  test(`${game}: narrow phone tabs remain usable without horizontal overflow`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 740 })
    await page.goto(`/tv?gallery=themes&game=${game}&view=phone`)
    const nav = page.getByRole('navigation')
    await expect(nav).toBeVisible()
    const labels = game === 'turf' ? ['MY PLACES', 'TRADE', 'NOW'] : ['CARDS', 'TRADE', 'NOW']
    for (const label of labels) {
      const button = nav.getByRole('button', { name: label, exact: true })
      await button.click()
      const box = await button.boundingBox()
      expect(box?.height).toBeGreaterThanOrEqual(44)
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320)
    }
    // Player identity must survive the common surface/border overrides.
    const stripe = page.locator(game === 'turf' ? '.turf-band' : '.sp-band')
    const colors = await stripe.evaluate(el => {
      const s = getComputedStyle(el)
      return { identity: s.borderLeftColor, neutral: s.borderRightColor }
    })
    expect(colors.identity).not.toBe(colors.neutral)
  })
}

test('game themes do not change the trivia scene', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('/tv?gallery=trivia&beat=quick-question')
  await expect(page.locator('[data-game-theme]')).toHaveCount(0)
  await expect(page.locator('.scene')).toBeVisible()
})
