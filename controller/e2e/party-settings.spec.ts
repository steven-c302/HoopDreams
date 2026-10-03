import { expect, test } from '@playwright/test'
import { clearParty, hostPage } from './helpers'

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

test("the captain sets the timer length from their phone", async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const p = await (await browser.newContext()).newPage()
  await p.goto(`/j/${room}`)
  await p.getByLabel('Your name').fill('Cap')
  await p.getByRole('button', { name: 'Join the party' }).click()
  await expect(p.getByRole('heading', { name: 'You have the crown' })).toBeVisible()
  const row = p.locator('.setting-row').filter({ hasText: 'Timers' })
  await expect(row.locator('b')).toHaveText('Normal')
  await p.getByRole('button', { name: 'More Timers' }).click()
  await expect(row.locator('b')).toHaveText('Relaxed')
  await p.getByRole('button', { name: 'More Timers' }).click()
  await expect(row.locator('b')).toHaveText('No rush')
  await p.getByRole('button', { name: 'Less Timers' }).click()
  await p.getByRole('button', { name: 'Less Timers' }).click()
  await expect(row.locator('b')).toHaveText('Normal')
})
