import { expect, type Browser, type Page } from '@playwright/test'

export async function phone(browser: Browser, room: string, name: string): Promise<Page> {
  const ctx = await browser.newContext()
  const p = await ctx.newPage()
  await p.goto(`/j/${room}`)
  await p.getByLabel('Your name').fill(name)
  await p.getByRole('button', { name: 'Join the party' }).click()
  // The first phone to join holds the crown and lands on the captain's panel instead.
  await expect(p.getByRole('heading', { name: /You're in|You have the crown/ })).toBeVisible()
  return p
}

export async function hostPage(browser: Browser): Promise<{ host: Page; room: string }> {
  const host = await (await browser.newContext()).newPage()
  await host.goto('/host')
  await host.getByRole('textbox').fill('4242')
  await host.getByRole('button', { name: 'Unlock host controls' }).click()
  const chip = host.locator('.room-chip')
  await expect(chip).toHaveText(/Room [A-Z]{4}/, { timeout: 20_000 }) // a long run can leave the first connect slow
  return { host, room: (await chip.textContent())!.replace('Room ', '').trim() }
}

/** The dev server is shared across tests: end any game and remove everyone an earlier test left behind. */
export async function clearParty(host: Page) {
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
