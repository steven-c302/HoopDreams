import { expect, test, type Browser, type Page } from '@playwright/test'

async function phone(browser: Browser, room: string, name: string): Promise<Page> {
  const ctx = await browser.newContext()
  const p = await ctx.newPage()
  await p.goto(`/j/${room}`)
  await p.getByPlaceholder('What should the TV call you?').fill(name)
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

test('three phones and a host play a round of Bluff Battle', async ({ browser }) => {
  const { host, room } = await hostPage(browser)

  const phones = await Promise.all(['Ava', 'Ben', 'Cy'].map((n) => phone(browser, room, n)))
  await expect(host.locator('.players li')).toHaveCount(3)

  await host.getByRole('combobox').selectOption('3')
  await host.getByRole('button', { name: /Bluff Battle/ }).click()

  for (const p of phones) await p.getByRole('button', { name: 'Got it!' }).click()

  for (const [i, p] of phones.entries()) {
    await p.getByPlaceholder('Type your answer').fill(`totally fake answer ${i}`)
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

test('a refreshed phone rejoins as the same player', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  const p = await phone(browser, room, 'Dee')
  await p.reload()
  await expect(p.getByRole('heading', { name: /You're in/ })).toBeVisible()
  await expect(p.locator('.me')).toContainText('Dee')
  await expect(host.locator('.players li', { hasText: 'Dee' })).toHaveCount(1)
})
