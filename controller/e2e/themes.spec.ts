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

const BEATS: Record<string, string[]> = {
  blackjack: ['bet', 'play', 'settle'],
  bluff: ['write', 'pick', 'reveal', 'scores'],
  writeitdown: ['teamup', 'intro', 'question', 'reveal', 'standings'],
}

for (const [game, beats] of Object.entries(BEATS)) {
  test(`${game}: every beat sits in its own room with a dial and no cartoon host`, async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    for (const beat of beats) {
      await page.goto(`/tv?gallery=themes&game=${game}&beat=${beat}`)
      await expect(page.locator(`.game-scene[data-game-theme='${game}']`), beat).toBeVisible()
      await expect(page.locator('.scene'), beat).toHaveCount(0)
      await expect(page.locator('.brainy'), beat).toHaveCount(0)
      await expect(page.locator('.timer:not(.game-dial)'), beat).toHaveCount(0)
    }
  })

  test(`${game}: phone screens fit 320px`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 740 })
    for (const beat of beats) {
      await page.goto(`/tv?gallery=themes&game=${game}&beat=${beat}&view=phone`)
      await expect(page.locator(`.play[data-game-theme='${game}']`), beat).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth), beat).toBeLessThanOrEqual(320)
    }
  })
}

/** Text colour differs from the background actually behind it (the element's own, or the nearest ancestor's). */
async function readable(el: Locator) {
  return el.evaluate((node) => {
    const text = getComputedStyle(node).color
    for (let n: Element | null = node; n; n = n.parentElement) {
      const bg = getComputedStyle(n).backgroundColor
      if (bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') return text !== bg
    }
    return true
  })
}

test('light cards in dark rooms keep dark text', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('/tv?gallery=themes&game=writeitdown&beat=question')
  expect(await readable(page.locator('.answered'))).toBe(true)
  await page.goto('/tv?gallery=themes&game=writeitdown&beat=reveal')
  for (const t of await page.locator('.write-text').all()) expect(await readable(t)).toBe(true)
  await page.goto('/tv?gallery=themes&game=blackjack&beat=settle')
  await expect(page.locator('.bj-call').first()).toBeVisible()
  for (const c of await page.locator('.bj-call').all()) expect(await readable(c)).toBe(true)
})

test('write it down on its own is a pub quiz; inside Brain Drain the round stays a cartoon', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('/tv?gallery=trivia&show=writeitdown&beat=write-reveal')
  await expect(page.locator(".game-scene[data-game-theme='writeitdown']")).toBeVisible()
  await expect(page.locator('.quizmaster')).toBeVisible()
  await page.goto('/tv?gallery=trivia&beat=write-reveal')
  await expect(page.locator('[data-game-theme]')).toHaveCount(0)
  await expect(page.locator('.brainy').first()).toBeVisible()
})
