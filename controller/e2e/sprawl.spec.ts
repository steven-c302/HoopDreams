import { expect, test, type Locator, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

const NAMES = ['Wes', 'Xan', 'Yui']
const placing = (p: Page) => p.getByText(/^Place a (settlement|road)$/)
const rollButton = (p: Page) => p.locator('.sp-roll')

/** The phone that has to act, by whatever [locate] finds on it. */
async function whoever(phones: Page[], locate: (p: Page) => Locator, timeout = 45_000): Promise<Page> {
  let found: Page | undefined
  await expect.poll(async () => {
    for (const p of phones) if (await locate(p).isVisible()) { found = p; return true }
    return false
  }, { timeout }).toBe(true)
  return found!
}

test('three phones play Sprawl: setup on the mini-map, roll, build a road, end turn', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)

  const phones = await Promise.all(NAMES.map((n) => phone(browser, room, n)))
  await expect(host.locator('.players li')).toHaveCount(3)
  await host.getByRole('button', { name: /Sprawl/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()

  // Setup: 3 players × (settlement + road) × 2 rounds, each picked on the phone's map and confirmed.
  for (let pick = 0; pick < 12; pick++) {
    const me = await whoever(phones, placing, 20_000)
    await me.locator('.sp-spot').first().click()
    await me.getByRole('button', { name: /SETTLE HERE|BUILD ROAD HERE/ }).click()
    await expect(me.locator('.sp-spot.on')).toHaveCount(0)
  }

  // First turn: roll, deal with a 7 if it comes up, build a road if affordable, end the turn.
  const first = await whoever(phones, rollButton)
  await rollButton(first).click()
  const end = first.getByRole('button', { name: 'END TURN' })
  await expect.poll(async () => {
    if (await end.isVisible()) return 'main'
    if (await first.getByText('Move The Landlord').isVisible()) {
      await first.locator('.sp-spot').first().click()
      await first.getByRole('button', { name: 'MOVE HERE' }).click().catch(() => {})
    }
    const victim = first.locator('.sp-key.victim').first()
    if (await victim.isVisible()) await victim.click().catch(() => {})
    return 'waiting'
  }, { timeout: 45_000 }).toBe('main')

  const road = first.locator('.sp-build.ok').filter({ hasText: 'ROAD' })
  if (await road.isVisible()) {
    await road.click()
    await first.locator('.sp-spot').first().click()
    await first.getByRole('button', { name: 'BUILD ROAD' }).click()
    await expect(end).toBeVisible()
  }
  await end.click()

  // The dice move on to the next phone.
  const next = await whoever(phones.filter((p) => p !== first), rollButton)
  await expect(rollButton(next)).toBeVisible()
})
