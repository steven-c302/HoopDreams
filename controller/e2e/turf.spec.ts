import { expect, test, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

const rollButton = (p: Page) => p.locator('.turf-roll')
const endButton = (p: Page) => p.getByRole('button', { name: 'END TURN' })

/** The phone whose ROLL button shows up first: whoever's turn it is. */
async function roller(phones: Page[], timeout = 45_000): Promise<Page> {
  let found: Page | undefined
  await expect.poll(async () => {
    for (const p of phones) if (await rollButton(p).isVisible()) { found = p; return true }
    return false
  }, { timeout }).toBe(true)
  return found!
}

/** Plays the current phone's turn up to its build/trade/end step: rolls (doubles too) and buys whatever it lands on. */
async function toManage(p: Page): Promise<boolean> {
  for (let i = 0; i < 40; i++) {
    if (await endButton(p).isVisible()) return true
    if (await rollButton(p).isVisible()) await rollButton(p).click()
    const buy = p.getByRole('button', { name: /^BUY \$/ })
    if (await buy.isVisible()) await buy.click()
    // Sent to Timeout by a card or a third double: the turn is over without a build step.
    if (await p.getByRole('heading', { name: /is rolling|is moving|is in Timeout|has the dice/ }).isVisible()) return false
    await p.waitForTimeout(500)
  }
  return false
}

test('three phones play Home Turf: pieces, roll, buy, trade, accept, end turn', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)

  const phones = await Promise.all(['Tia', 'Uma', 'Vic'].map((n) => phone(browser, room, n)))
  await expect(host.locator('.players li')).toHaveCount(3)
  await host.getByRole('button', { name: /Home Turf/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()

  // Grab your piece: first tap wins, so a phone may lose a race and just tap the next one.
  for (const p of phones) {
    const grab = p.getByRole('heading', { name: 'Grab your piece!' })
    await expect(grab).toBeVisible({ timeout: 20_000 })
    await expect.poll(async () => {
      if (!(await grab.isVisible())) return 'done'
      await p.locator('.turf-pieces button').first().click().catch(() => {})
      return 'grabbing'
    }, { timeout: 15_000 }).toBe('done')
  }

  // First turn: roll, buy, and at the build step offer a partner $10 for one of their starter places.
  let me = await roller(phones)
  let managed = await toManage(me)
  for (let tries = 0; !managed && tries < 3; tries++) { me = await roller(phones); managed = await toManage(me) }
  expect(managed).toBe(true)

  await me.locator('.turf-tabs').getByRole('button', { name: /^TRADE/ }).click()
  await me.locator('.turf-partners button').first().click()
  const partnerName = (await me.getByRole('heading', { name: /^Deal with/ }).textContent())!.replace('Deal with ', '').trim()
  const theirs = me.locator('.turf-trade section').nth(1).locator('.chips button')
  await theirs.first().click()
  await me.locator('.turf-trade section').first().getByRole('button', { name: '+10' }).click()
  await me.getByRole('button', { name: 'SEND OFFER' }).click()

  // The partner's phone jumps to the offer; they take it.
  const partner = phones[['Tia', 'Uma', 'Vic'].indexOf(partnerName)]
  await expect(partner.getByRole('heading', { name: /wants to trade/ })).toBeVisible({ timeout: 10_000 })
  await partner.getByRole('button', { name: 'ACCEPT' }).click()

  // The turn resumes at the build step; end it and the dice move to the next phone.
  await expect(endButton(me)).toBeVisible({ timeout: 10_000 })
  await endButton(me).click()
  const next = await roller(phones.filter((p) => p !== me))
  await expect(rollButton(next)).toBeVisible()
})
