import { expect, test } from '@playwright/test'

for (const beat of ['load', 'stage', 'reveal', 'podium', 'dead']) {
  test(`song drop ${beat}: fits the TV with 16 players and keeps text TV-sized`, async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(`/tv?gallery=songdrop&beat=${beat}&n=16`)
    await expect(page.locator('.sd')).toBeVisible()
    await page.waitForTimeout(1500) // entrance animations
    const m = await page.evaluate(() => {
      const stage = document.querySelector('.tv-stage')!.getBoundingClientRect()
      const sd = document.querySelector('.sd')!
      const bad = [...sd.querySelectorAll('*')].filter((e) => {
        if (!e.closest('.sd-deck, .sd-podium, .sd-dead')) return false
        const hasText = [...e.childNodes].some((c) => c.nodeType === 3 && c.textContent!.trim())
        return hasText && parseFloat(getComputedStyle(e).fontSize) < 28
      }).map((e) => e.className)
      // The deck is parked off-screen (class "away") when no song is playing; that is deliberate.
      const off = [...sd.querySelectorAll('.sd-deck:not(.away), .sd-podium, .sd-dead')].filter((e) => {
        const r = e.getBoundingClientRect()
        return r.width > 0 && (r.left < stage.left - 1 || r.right > stage.right + 1 || r.bottom > stage.bottom + 1 || r.top < stage.top - 1)
      }).map((e) => e.className)
      return { bad, off }
    })
    expect(m.bad, 'text under 28px').toEqual([])
    expect(m.off, 'panels outside the stage').toEqual([])
  })
}

test('song drop: the cover hides the video, V shows it, and the player is never display:none', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('/tv?gallery=songdrop&beat=stage')
  await expect(page.locator('.sd-cover')).toBeVisible()
  const display = await page.locator('.sd-player').evaluate((e) => getComputedStyle(e).display)
  expect(display).not.toBe('none')
  await page.keyboard.press('v')
  await expect(page.locator('.sd-cover')).toHaveCount(0)
  await page.keyboard.press('v')
  await expect(page.locator('.sd-cover')).toBeVisible()
})
