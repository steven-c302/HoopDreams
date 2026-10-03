import { expect, test, type Browser, type Page } from '@playwright/test'
import { clearParty, hostPage, phone } from './helpers'

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

/** The options the TV is listing right now, read straight off the host socket. */
async function tvOptions(browser: Browser): Promise<string[]> {
  const tv = await (await browser.newContext()).newPage()
  await tv.goto('/healthz')
  const options = await tv.evaluate(async () => {
    const s = await fetch('/api/tv/session').then((r) => r.json())
    return new Promise<string[]>((resolve) => {
      const ws = new WebSocket(`${location.origin.replace('http', 'ws')}/ws?host=${s.hostToken}`)
      ws.onopen = () => ws.send(JSON.stringify({ t: 'hello', protocol: 1 }))
      ws.onmessage = (e) => { const m = JSON.parse(e.data); if (m.t === 'tv') { ws.close(); resolve(m.tv.stage?.game?.options ?? []) } }
    })
  })
  await tv.context().close()
  return options
}

async function toPick(host: Page, phones: Page[]) {
  await host.getByRole('combobox').selectOption('3')
  await host.getByRole('button', { name: /Bluff Battle/ }).click()
  for (const p of phones) await p.getByRole('button', { name: 'Ready!' }).click()
  for (const [i, p] of phones.entries()) {
    await p.getByPlaceholder('Type here').fill(`totally fake answer ${i}`)
    await p.getByRole('button', { name: 'Lock it in' }).click()
  }
  for (const p of phones) await expect(p.getByRole('heading', { name: 'Which one is the truth?' })).toBeVisible()
}

test('the captain hides an option from their phone: it is cut for everyone and shows as a placeholder on the TV', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const phones = await Promise.all(['Ava', 'Ben', 'Cy'].map((n) => phone(browser, room, n)))
  await toPick(host, phones)
  // The phones join at the same time, so find the one holding the crown.
  const flags = await Promise.all(phones.map((p) => p.getByRole('button', { name: 'Captain controls' }).count()))
  const captain = phones[flags.indexOf(1)]
  const other = phones[flags.indexOf(0)]

  const choices = captain.locator('.choices button')
  const before = await choices.count()
  const target = (await choices.first().innerText()).trim()
  await captain.getByRole('button', { name: 'Captain controls' }).click()
  const veto = captain.getByRole('dialog', { name: 'Captain controls' })
  await expect(veto.getByText('Hide an option from the room')).toBeVisible()
  await veto.getByRole('button', { name: target, exact: true }).click()
  await veto.getByRole('button', { name: `Tap again to hide “${target}”` }).click()

  await expect(choices).toHaveCount(before - 1)
  await expect(captain.locator('.choices button', { hasText: target })).toHaveCount(0)
  await expect(other.locator('.choices button', { hasText: target })).toHaveCount(0)
  await expect.poll(async () => (await tvOptions(browser)).filter((o) => o === 'Hidden by the host').length).toBe(1)
  expect(await tvOptions(browser)).not.toContain(target)
})

test('the host overlay path: Esc lists the options with a HIDE button during picking', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const phones = await Promise.all(['Ava', 'Ben', 'Cy'].map((n) => phone(browser, room, n)))
  await toPick(host, phones)
  const tv = await (await browser.newContext()).newPage()
  await tv.setViewportSize({ width: 1280, height: 720 })
  await tv.goto('/tv')
  await tv.mouse.click(960, 540) // dismiss the GO LIVE gate
  await expect(tv.locator('.play-card').first()).toBeVisible() // the TV has its state, so the host overlay can open
  await tv.keyboard.press('Escape')
  await expect(tv.getByRole('heading', { name: 'HIDE AN OPTION' })).toBeVisible()
  const hides = tv.getByRole('button', { name: 'HIDE', exact: true })
  const n = await hides.count()
  expect(n).toBeGreaterThanOrEqual(3)
  await hides.first().click()
  await expect(hides).toHaveCount(n - 1)
})
