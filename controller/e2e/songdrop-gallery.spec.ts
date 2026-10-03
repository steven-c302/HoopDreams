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

test('the lobby shows the era key and the YouTube song check for Song Drop', async ({ page, request }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('/tv?yt=fake')
  await page.mouse.click(640, 360) // dismiss the GO LIVE gate
  await expect(page.locator('.cast-card, .cast-empty').first()).toBeVisible()
  const games = await request.get('/api/games').then((r) => r.json())
  const at = games.findIndex((g: { id: string }) => g.id === 'songdrop')
  expect(at).toBeGreaterThanOrEqual(0)
  expect(games[at].probe).toBeTruthy()
  // Focus Song Drop on the TV with the arrow keys (the lobby reads them from the window).
  // One press at a time: the focus lives on the server, so a second press before the first lands would read a stale focus.
  for (let i = 1; i <= at; i++) {
    await page.keyboard.press('ArrowRight')
    await expect(page.locator('.cover.focused')).toHaveClass(new RegExp(`cover-${games[i].id}`))
  }
  await expect(page.getByText('Era')).toBeVisible()
  await expect(page.getByText('Songs')).toBeVisible()
  await expect(page.locator('.stepper', { hasText: 'YouTube' })).toContainText('OK', { timeout: 8_000 })
})

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
