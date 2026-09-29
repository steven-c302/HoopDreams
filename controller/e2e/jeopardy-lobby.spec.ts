import { expect, test } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

test('the lobby lets the TV and the captain choose a Short or Full show, with no Rounds setting', async ({ browser }) => {
  test.setTimeout(90_000)
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const captain = await phone(browser, room, 'Ana')
  await phone(browser, room, 'Bo')
  const tv = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage()
  await tv.goto('/tv')
  await tv.mouse.click(960, 540) // dismiss the GO LIVE gate

  await captain.getByRole('radio', { name: /Answer & Question/ }).click()
  const controls = tv.locator('.controls-row')
  await expect(controls).toContainText('Show')
  await expect(controls).toContainText('SHORT')
  await expect(controls).not.toContainText('Rounds')

  await tv.keyboard.press('s')
  await expect(controls).toContainText('FULL')
  await expect(captain.locator('.setting-row', { hasText: 'Show' })).toContainText('Full')

  await captain.getByRole('button', { name: 'Less Show' }).click()
  await expect(controls).toContainText('SHORT')
  await captain.getByRole('button', { name: 'More Show' }).click()
  await expect(controls).toContainText('FULL')
  await expect(captain.locator('.setting-row', { hasText: 'Drink calls' }).first()).toBeVisible()
})
