import { expect, test } from '@playwright/test'
import { clearParty, hostPage } from './helpers'

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

test('a watcher taps a cheer and it stamps on the TV with their name; the pad rests between taps', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)

  const tv = await (await browser.newContext()).newPage()
  await tv.setViewportSize({ width: 1280, height: 720 })
  await tv.goto('/tv')
  await tv.mouse.click(960, 540) // dismiss the GO LIVE gate
  await expect(tv.locator('.cast-card, .cast-empty').first()).toBeVisible() // the TV has its state, so it has seen the quiet room

  const watcher = await (await browser.newContext()).newPage()
  await watcher.goto(`/j/${room}`)
  await watcher.getByLabel('Your name').fill('Wes')
  await watcher.getByRole('button', { name: 'Just watch' }).click()
  const pad = watcher.getByRole('group', { name: 'Cheer for the room' })
  await expect(pad).toBeVisible()

  await pad.getByRole('button', { name: 'WOW!' }).click()
  const stamp = tv.locator('.cheer-stamp')
  await expect(stamp).toHaveCount(1)
  await expect(stamp).toContainText('WOW!')
  await expect(stamp).toContainText('Wes')
  // The phone rests so one finger can't flood the room.
  await expect(pad.getByRole('button', { name: 'YES!' })).toBeDisabled()
  await expect(pad.getByRole('button', { name: 'YES!' })).toBeEnabled({ timeout: 4_000 })
  await pad.getByRole('button', { name: 'YES!' }).click()
  await expect(stamp).toHaveCount(2)
  // Stamps come down on their own.
  await expect(stamp).toHaveCount(0, { timeout: 8_000 })
})

test('a player who is playing does not get the cheer pad', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const p = await (await browser.newContext()).newPage()
  await p.goto(`/j/${room}`)
  await p.getByLabel('Your name').fill('Pat')
  await p.getByRole('button', { name: 'Join the party' }).click()
  await expect(p.getByRole('heading', { name: /You're in|You have the crown/ })).toBeVisible()
  await expect(p.getByRole('group', { name: 'Cheer for the room' })).toHaveCount(0)
})
