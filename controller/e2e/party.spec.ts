import { expect, test, type Browser, type Page } from '@playwright/test'

async function phone(browser: Browser, room: string, name: string): Promise<Page> {
  const ctx = await browser.newContext()
  const p = await ctx.newPage()
  await p.goto(`/j/${room}`)
  await p.getByLabel('Your name').fill(name)
  await p.getByRole('button', { name: 'Join the party' }).click()
  await expect(p.getByRole('heading', { name: /You're in/ })).toBeVisible()
  return p
}

async function hostPage(browser: Browser): Promise<{ host: Page; room: string }> {
  const host = await (await browser.newContext()).newPage()
  await host.goto('/host')
  await host.getByRole('textbox').fill('4242')
  await host.getByRole('button', { name: 'Unlock host controls' }).click()
  const chip = host.locator('.room-chip')
  await expect(chip).toHaveText(/Room [A-Z]{4}/)
  return { host, room: (await chip.textContent())!.replace('Room ', '').trim() }
}

/** The dev server is shared across tests: end any game and remove everyone an earlier test left behind. */
async function clearParty(host: Page) {
  host.on('dialog', (d) => d.accept())
  const end = host.getByRole('button', { name: 'End game' })
  if (await end.isVisible()) await end.click()
  await expect(host.getByRole('heading', { name: 'Start a game' })).toBeVisible()
  const rows = host.locator('.players li')
  while ((await rows.count()) > 0) {
    const n = await rows.count()
    await rows.first().getByRole('button', { name: 'Remove' }).click()
    await expect(rows).toHaveCount(n - 1)
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
  for (const [p, name] of [[phones[0], 'Quizzly Bears'], [phones[2], 'Smarty Pints']] as const) {
    await p.locator('textarea').fill(name)
    await p.getByRole('button', { name: 'Lock it in' }).click()
  }

  // The round card, then a four-way question with shape buttons on every phone.
  for (const p of phones) await expect(p.locator('.choices.shapes button')).toHaveCount(4, { timeout: 20_000 })
  await expect(phones[1].locator('.team-band')).toHaveText('Quizzly Bears')
  for (const p of phones) await p.locator('.choices.shapes button').first().click()
  for (const p of phones) await expect(p.getByRole('heading', { name: /Correct!|Nope/ })).toBeVisible()
  await host.getByRole('button', { name: 'End game' }).click()
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
