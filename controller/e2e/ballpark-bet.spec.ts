import { expect, test, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

/** Names each team from its first phone, if the phone asks (trivia remembers team names between shows in one party). */
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

/** Brain Drain deals its five rounds in a random order, so skip phases from the host until a Ballpark question shows up. */
async function skipToBallparkQuestion(host: Page, phones: Page[]) {
  for (let i = 0; i < 80; i++) {
    if (await phones[0].locator('.numkey').count()) return
    await host.getByRole('button', { name: 'Skip phase' }).click()
    await host.waitForTimeout(250)
  }
  throw new Error('never reached a Ballpark question')
}

async function guess(p: Page, digits: string) {
  for (const d of digits) await p.getByRole('button', { name: d, exact: true }).click()
  await p.getByRole('button', { name: 'Send guess' }).click()
}

test('four phones play a Ballpark question through the bet to the reveal', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const phones = await Promise.all(['Ana', 'Bo', 'Cal', 'Di'].map((n) => phone(browser, room, n)))
  const [ana, bo, cal, di] = phones
  // The TV page for the same party, so we can see what the room sees.
  const tv = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage()
  await tv.goto('/tv')

  await host.getByRole('button', { name: /Brain Drain/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()
  for (const [i, p] of phones.entries()) await p.locator('.choices.teams button').nth(i < 2 ? 0 : 1).click()
  await nameTeams([[ana, 'Quizzly Bears'], [cal, 'Smarty Pints']])

  await skipToBallparkQuestion(host, phones)

  // Both teams guess a different number, so there are two flags to bet on. Ana and Bo agree; Cal and Di agree.
  await guess(ana, '7'); await guess(bo, '7')
  await guess(cal, '99'); await guess(di, '99')

  // The bet phase: every phone is offered both guesses (with odds) and a way to skip.
  for (const p of phones) await expect(p.getByRole('heading', { name: "Who's closest? Back a guess" })).toBeVisible()
  await expect(ana.locator('.choices.teams button')).toHaveCount(3)
  await expect(ana.locator('.choices.teams button', { hasText: 'Quizzly Bears' })).toContainText('guess 7 · pays 1×')
  await expect(ana.locator('.choices.teams button', { hasText: 'Smarty Pints' })).toContainText('guess 99 · pays 1×')
  await expect(ana.getByRole('button', { name: 'Skip betting' })).toBeVisible()

  // The TV shows the guesses with odds tags and no answer yet.
  await expect(tv.locator('.numberline.bet .nl-team')).toHaveCount(2)
  await expect(tv.locator('.numberline.bet .odds').first()).toHaveText('×1')
  await expect(tv.locator('.nl-truth')).toHaveCount(0)

  // Ana backs her own team's guess; the stake screen offers house money only, since the teams are on nothing.
  await ana.locator('.choices.teams button', { hasText: 'Quizzly Bears' }).click()
  await expect(ana.getByRole('heading', { name: 'How much on Quizzly Bears? Pays 1×' })).toBeVisible()
  await expect(ana.locator('.choices.teams button')).toHaveText([/250 pts/, /Change guess/])
  // Changing her mind goes back to the guess list; she picks again.
  await ana.getByRole('button', { name: 'Change guess' }).click()
  await expect(ana.getByRole('heading', { name: "Who's closest? Back a guess" })).toBeVisible()
  await ana.locator('.choices.teams button', { hasText: 'Quizzly Bears' }).click()
  await ana.getByRole('button', { name: /250 pts/ }).click()
  await expect(ana.getByRole('heading', { name: 'Backing Quizzly Bears for 250. Tap to change' })).toBeVisible()

  // Bo backs the same guess; a teammate sees who has backed what.
  await bo.locator('.choices.teams button', { hasText: 'Quizzly Bears' }).click()
  await bo.getByRole('button', { name: /250 pts/ }).click()
  await expect(tv.locator('.bet-in')).toHaveCount(1) // one team has a bet in; who backed what stays hidden

  // Cal and Di skip. That was the last decision: the phase ends at once instead of running out the 15 s clock.
  await cal.getByRole('button', { name: 'Skip betting' }).click()
  await expect(tv.locator('.nl-truth')).toHaveCount(0)
  await di.getByRole('button', { name: 'Skip betting' }).click()

  // The reveal: the answer drops on the TV, and every phone shows its result. The backed team's phones mention the bet.
  await expect(tv.locator('.nl-truth')).toHaveCount(1, { timeout: 5_000 })
  for (const p of phones) await expect(p.getByRole('heading', { name: /Closest!|Off by|Bullseye!|No guess/ })).toBeVisible({ timeout: 5_000 })
  await expect(ana.getByText(/bet (\+\d+|lost|-\d+)/)).toBeVisible()
  await expect(bo.getByText(/bet (\+\d+|lost|-\d+)/)).toBeVisible()
  await expect(cal.getByText(/bet /)).toHaveCount(0) // a team that skipped has no bet
  await expect(tv.locator('.bet-chip')).toHaveCount(1) // one chip: Quizzly Bears' bet, on their own flag

  await host.getByRole('button', { name: 'End game' }).click()
})
