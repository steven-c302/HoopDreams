import { expect, test, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

async function nameTeams(namers: (readonly [Page, string])[]) {
  for (const [p, name] of namers) {
    const naming = p.getByRole('heading', { name: 'Name your team' })
    await expect.poll(async () => {
      if (await naming.isVisible()) return 'name it'
      if (await p.getByRole('heading', { name: /You.re on/ }).isVisible() || !(await p.locator('.choices.teams').count())) return 'named'
      return 'waiting'
    }, { timeout: 20_000 }).not.toBe('waiting')
    if (!(await naming.isVisible())) continue
    await p.locator('textarea').fill(name)
    await p.getByRole('button', { name: 'Lock it in' }).click()
  }
}

test('four phones play the Final Wager: wager, type the answer, watch the reveal', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const phones = await Promise.all(['Ana', 'Bo', 'Cal', 'Di'].map((n) => phone(browser, room, n)))
  const [ana, , cal] = phones
  const tv = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage()
  await tv.goto('/tv')

  await host.getByRole('button', { name: /Brain Drain/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()
  for (const [i, p] of phones.entries()) await p.locator('.choices.teams button').nth(i < 2 ? 0 : 1).click()
  await nameTeams([[ana, 'Quizzly Bears'], [cal, 'Smarty Pints']])

  // Skip the five rounds (nobody scores, so both teams are level) until the finale's category slam shows on the TV.
  for (let i = 0; i < 300 && !(await tv.getByText('THE FINAL WAGER').count()); i++) {
    expect(await tv.getByText(/win Brain Drain/).count(), 'the show reached the podium without a finale').toBe(0)
    await host.getByRole('button', { name: 'Skip phase' }).click()
    await host.waitForTimeout(500) // let the TV catch up, or the next click skips right past the finale's first phase
  }
  await expect(tv.getByText('THE FINAL WAGER')).toBeVisible()
  await host.getByRole('button', { name: 'Skip phase' }).click() // category → wager

  // Everyone is level, so everyone is an underdog and sees ALL IN. The leader rules are covered by the engine tests.
  for (const p of phones) await expect(p.locator('.choices button', { hasText: 'ALL IN' })).toBeVisible()
  await expect(ana.locator('.choices button', { hasText: 'Play it safe' })).toBeVisible()
  await expect(tv.locator('.bet-in')).toHaveCount(0)
  await ana.locator('.choices button', { hasText: '50%' }).click()
  await expect(tv.locator('.bet-in')).toHaveCount(1) // one team's wager is in; what they picked stays hidden
  await cal.locator('.choices button', { hasText: 'ALL IN' }).click()

  // Both teams are in, so the question opens at once; every phone gets a typed-answer box and the TV shows the question.
  for (const p of phones) await expect(p.locator('textarea')).toBeVisible({ timeout: 5_000 })
  await expect(tv.locator('.q-bubble')).not.toBeEmpty()
  for (const [i, p] of phones.entries()) {
    await p.locator('textarea').fill(i < 2 ? 'Paris' : 'Rome')
    await p.getByRole('button', { name: 'Lock it in' }).click()
  }

  // The reveal plays on the TV: last place first, then the ladder; every phone shows its result.
  await expect(tv.locator('.final-reveal')).toBeVisible({ timeout: 5_000 })
  await expect(tv.locator('.ladder-row')).toHaveCount(2)
  // The phones stay neutral until the podium: a result on a phone now would spoil the reveal on the TV.
  for (const p of phones) { await expect(p.getByRole('heading', { name: 'Eyes on the TV' })).toBeVisible(); await expect(p.getByText(/Correct!|Wrong\./)).toHaveCount(0) }
  await expect(tv.locator('.final-card')).toBeVisible()

  // Skipping the reveal lands the scores and moves on to the podium.
  await host.getByRole('button', { name: 'Skip phase' }).click()
  await expect(tv.getByText(/Quizzly Bears|Smarty Pints/).first()).toBeVisible()
  await host.getByRole('button', { name: 'End game' }).click()
})
