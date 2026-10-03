import { expect, test } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

test('four phones play a Song Drop song: the TV reports playing, each taps a different answer, exactly one is right', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)

  // The TV page plays with a fake YouTube (no network); its "playing" report is what starts the guess clock.
  const tv = await (await browser.newContext()).newPage()
  await tv.setViewportSize({ width: 1280, height: 720 })
  await tv.goto('/tv?yt=fake')
  await tv.mouse.click(640, 360) // dismiss the GO LIVE gate
  await expect(tv.locator('.cast-card, .cast-empty').first()).toBeVisible()

  const phones = await Promise.all(['Ava', 'Ben', 'Cy', 'Dee'].map((n) => phone(browser, room, n)))
  await host.getByRole('button', { name: /Song Drop/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()

  // The TV loads the clip, reports it playing, and only then do the phones get the question.
  for (const p of phones) await expect(p.getByRole('heading', { name: /Name that song/ })).toBeVisible({ timeout: 20_000 })
  await expect(tv.locator('.sd-cover')).toContainText('NAME THAT SONG')

  // Four players, four different letters: exactly one of them is right.
  for (const [i, p] of phones.entries()) await p.locator('.choices.shapes button').nth(i).click()

  await expect(tv.locator('.sd-reveal')).toBeVisible({ timeout: 10_000 })
  await expect(tv.locator('.sd-solver')).toHaveCount(1)
  await expect(tv.locator('.sd-title')).not.toBeEmpty()
  // The reveal is on the phones too: the song's title is the heading.
  const title = (await tv.locator('.sd-title').innerText()).trim()
  for (const p of phones) await expect(p.getByRole('heading', { name: title })).toBeVisible()
})
