import { expect, test, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

/**
 * Names each team from its first phone, if the phone asks. Trivia games remember teams (names included) for the next
 * show in the same party, so a team may already be named and go straight to "You're on …".
 */
async function nameTeams(namers: (readonly [Page, string])[]) {
  for (const [p, name] of namers) {
    // Named already, the phone shows "You're on …" or, once every team is confirmed, has moved on to the show.
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

test('three phones and a host play a round of Bluff Battle', async ({ browser }) => {
  const { host, room } = await hostPage(browser)

  const phones = await Promise.all(['Ava', 'Ben', 'Cy'].map((n) => phone(browser, room, n)))
  await expect(host.locator('.players li')).toHaveCount(3)

  await host.getByRole('combobox').selectOption('3')
  await host.getByRole('button', { name: /Bluff Battle/ }).click()

  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()

  for (const [i, p] of phones.entries()) {
    await p.getByPlaceholder('Type here').fill(`totally fake answer ${i}`)
    await p.getByRole('button', { name: 'Lock it in' }).click()
  }

  for (const p of phones) {
    await expect(p.getByRole('heading', { name: 'Which one is the truth?' })).toBeVisible()
    await p.locator('.choices button').first().click()
  }

  for (const p of phones) await expect(p.getByRole('heading', { name: 'Eyes on the TV' })).toBeVisible()
  await host.getByRole('button', { name: 'Skip phase' }).click()
  for (const p of phones) await expect(p.getByRole('heading', { name: 'Round 1 scores' })).toBeVisible()
})

test('four phones team up and answer a Brain Drain question', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const phones = await Promise.all(['Ana', 'Bo', 'Cal', 'Di'].map((n) => phone(browser, room, n)))
  await host.getByRole('button', { name: /Brain Drain/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()

  // Two teams for four players: Ana and Bo on the first, Cal and Di on the second; the first of each names it.
  for (const [i, p] of phones.entries()) await p.locator('.choices.teams button').nth(i < 2 ? 0 : 1).click()
  await nameTeams([[phones[0], 'Quizzly Bears'], [phones[2], 'Smarty Pints']])

  // The round card, then the first question. Rounds come in a random order, so answer whichever one it is.
  await expect(phones[1].locator('.team-band')).toHaveText('Quizzly Bears')
  for (const p of phones) {
    await expect(p.locator('.choices.shapes button, .choices.sides button, .numkey, textarea').first()).toBeVisible({ timeout: 20_000 })
    if (await p.locator('textarea').count()) {
      await p.locator('textarea').fill('Paris')
      await p.getByRole('button', { name: 'Lock it in' }).click()
    } else if (await p.locator('.numkey').count()) {
      await p.getByRole('button', { name: '7', exact: true }).click()
      await p.getByRole('button', { name: 'Send guess' }).click()
    } else {
      await p.locator('.choices.shapes button, .choices.sides button').first().click()
    }
  }
  for (const p of phones) await expect(p.getByRole('heading', { name: /Correct!|Nope|Closest!|Off by|Bullseye!/ })).toBeVisible()
  await host.getByRole('button', { name: 'End game' }).click()
})

test('Write It Down plays on its own: type the answer, no options', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const phones = await Promise.all(['Eve', 'Fay', 'Gil', 'Hal'].map((n) => phone(browser, room, n)))
  await host.getByRole('button', { name: /Write It Down/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()
  for (const [i, p] of phones.entries()) await p.locator('.choices.teams button').nth(i < 2 ? 0 : 1).click()
  await nameTeams([[phones[0], 'Pens Out'], [phones[2], 'Scribblers']])
  // Every question is typed: a text box, never answer buttons.
  for (const p of phones) {
    await expect(p.locator('textarea')).toBeVisible({ timeout: 20_000 })
    await expect(p.locator('.choices button')).toHaveCount(0)
    await p.locator('textarea').fill('Paris')
    await p.getByRole('button', { name: 'Lock it in' }).click()
  }
  for (const p of phones) await expect(p.getByRole('heading', { name: /Correct!|Nope/ })).toBeVisible()
  await host.getByRole('button', { name: 'End game' }).click()
})

test('the first phone to join runs the show from the couch', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const captain = await phone(browser, room, 'Cap')
  const guest = await phone(browser, room, 'Gus')
  await expect(captain.getByRole('heading', { name: 'You have the crown' })).toBeVisible()
  await expect(guest.getByRole('heading', { name: /You're in/ })).toBeVisible()
  await expect(guest.getByText('Cap has the crown and picks the game')).toBeVisible()

  await captain.getByRole('radio', { name: /Brain Drain/ }).click()
  await captain.getByRole('button', { name: 'More Questions per round' }).click()
  await captain.getByRole('button', { name: /Everybody's in! Start Brain Drain/ }).click()
  for (const p of [captain, guest]) await expect(p.getByRole('button', { name: 'Ready!' })).toBeVisible()

  // Team Up: the captain can deal everyone evenly; the guest never gets that button.
  for (const p of [captain, guest]) await p.getByRole('button', { name: 'Ready!' }).click()
  await captain.locator('.choices.teams button').first().click()
  await expect(guest.getByRole('button', { name: /Shuffle evenly/ })).toHaveCount(0)
  await captain.getByRole('button', { name: /Shuffle evenly/ }).click()
  // The guest never picked a team; after the shuffle they're each on a different one.
  const band = async (p: Page) => ((await p.locator('.team-band').count()) ? (await p.locator('.team-band').textContent()) ?? '' : '')
  await expect.poll(async () => { const [a, b] = [await band(captain), await band(guest)]; return a !== '' && b !== '' && a !== b }).toBe(true)

  // Mid-game the crown opens the show controls; the guest never sees them.
  await expect(guest.getByRole('button', { name: 'Captain controls' })).toHaveCount(0)
  await captain.getByRole('button', { name: 'Captain controls' }).click()
  await expect(captain.getByRole('button', { name: 'Shuffle teams evenly' })).toBeVisible()
  await captain.getByRole('button', { name: 'End game' }).click()
  await captain.getByRole('button', { name: 'Really end the game?' }).click()
  await expect(captain.getByRole('heading', { name: 'You have the crown' })).toBeVisible()
})

test('a refreshed phone rejoins as the same player', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  const p = await phone(browser, room, 'Dee')
  await p.reload()
  await expect(p.getByRole('heading', { name: /You're in/ })).toBeVisible()
  await expect(p.locator('.me')).toContainText('Dee')
  await expect(host.locator('.players li', { hasText: 'Dee' })).toHaveCount(1)
})

test('a spectator can take a free seat between games', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  // The dev server is shared across tests; finish any game an earlier test left running.
  host.on('dialog', (d) => d.accept())
  const end = host.getByRole('button', { name: 'End game' })
  if (await end.isVisible()) await end.click()
  await expect(host.getByRole('heading', { name: 'Start a game' })).toBeVisible()
  const p = await (await browser.newContext()).newPage()
  await p.goto(`/j/${room}`)
  await p.getByLabel('Your name').fill('Watcher')
  await p.getByRole('button', { name: 'Just watch' }).click()
  await expect(host.locator('.players li', { hasText: 'Watcher (watching)' })).toHaveCount(1)
  await p.getByRole('button', { name: 'Join as a player' }).click()
  await expect(host.locator('.players li', { hasText: 'Watcher' })).not.toContainText('watching')
  await expect(p.getByRole('button', { name: 'Join as a player' })).toHaveCount(0)
})

test('host sees why a game could not start', async ({ browser }) => {
  const { host } = await hostPage(browser)
  host.on('dialog', (d) => d.accept())
  const end = host.getByRole('button', { name: 'End game' })
  if (await end.isVisible()) await end.click()
  const rows = host.locator('.players li')
  while ((await rows.count()) > 0) {
    const n = await rows.count()
    await rows.first().getByRole('button', { name: 'Remove' }).click()
    await expect(rows).toHaveCount(n - 1)
  }
  await host.getByRole('button', { name: /Bluff Battle/ }).click()
  const toast = host.getByRole('status')
  await expect(toast).toHaveText('Need more players connected.')
  await host.waitForTimeout(1_000)
  await expect(toast).toBeVisible()
})
