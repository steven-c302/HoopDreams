import { expect, test } from '@playwright/test'

// The recap card from fixture data: no party server needed beyond the one that serves the build.
for (const n of [4, 12, 16]) {
  test(`night in review fits the TV with ${n} players and keeps text TV-sized`, async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(`/tv?gallery=night&n=${n}`)
    await expect(page.getByRole('heading', { name: 'NIGHT IN REVIEW' })).toBeVisible()
    await expect(page.getByLabel('Night leaderboard').getByRole('listitem').first()).toContainText('Amanda')
    await expect(page.getByText('NIGHT CHAMP')).toBeVisible()
    await expect(page.getByLabel('Moments').getByRole('listitem')).toHaveCount(3)
    // Let the pop-in animations finish, then nothing may be clipped by the card or spill past the 1920×1080 stage.
    await page.waitForFunction(() => [...document.querySelectorAll('.night-row')].every((e) => getComputedStyle(e.parentElement!).opacity === '1'), null, { timeout: 15_000 })
    const sizes = await page.evaluate(() => {
      const body = document.querySelector('.night-body')!
      const panel = document.querySelector('.night-panel')!.getBoundingClientRect()
      const stage = document.querySelector('.tv-stage')!.getBoundingClientRect()
      const smallest = Math.min(...[...document.querySelectorAll('.night-panel *')]
        .filter((e) => [...e.childNodes].some((c) => c.nodeType === 3 && c.textContent!.trim()))
        .map((e) => parseFloat(getComputedStyle(e).fontSize)))
      return { clipped: body.scrollHeight - body.clientHeight, panelFits: panel.top >= stage.top - 1 && panel.bottom <= stage.bottom + 1, smallest }
    })
    expect(sizes.clipped).toBeLessThanOrEqual(0)
    expect(sizes.panelFits).toBe(true)
    expect(sizes.smallest).toBeGreaterThanOrEqual(28)
  })
}

test('night in review shows a nudge instead of empty awards, and still lists moments', async ({ page }) => {
  await page.goto('/tv?gallery=night&n=6&awards=0')
  await expect(page.getByText('Play another game and the awards start landing.')).toBeVisible()
  await expect(page.getByLabel('Moments').getByRole('listitem')).toHaveCount(3)
})
