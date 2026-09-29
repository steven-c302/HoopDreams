import { expect, test } from '@playwright/test'
import { clearParty, hostPage } from './helpers'

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

test('water tonight is set at join, shows on the TV, and switches from the phone', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const p = await (await browser.newContext()).newPage()
  await p.goto(`/j/${room}`)
  await p.getByLabel('Your name').fill('Wes')
  const joinSwitch = p.locator('.water-row').getByRole('switch')
  await joinSwitch.click()
  await expect(joinSwitch).toHaveAttribute('aria-checked', 'true')
  await p.getByRole('button', { name: 'Join the party' }).click()
  await expect(p.getByRole('heading', { name: /You're in|You have the crown/ })).toBeVisible()

  const lobbySwitch = p.locator('.water-row').getByRole('switch')
  await expect(lobbySwitch).toHaveAttribute('aria-checked', 'true')
  const tv = await (await browser.newContext()).newPage()
  const water = () => tv.evaluate(async () => {
    const s = await fetch('/api/tv/session').then((r) => r.json())
    return new Promise<boolean | undefined>((resolve) => {
      const ws = new WebSocket(`${location.origin.replace('http', 'ws')}/ws?host=${s.hostToken}`)
      ws.onopen = () => ws.send(JSON.stringify({ t: 'hello', protocol: 1 }))
      ws.onmessage = (e) => { const m = JSON.parse(e.data); if (m.t === 'tv') { ws.close(); resolve(m.tv.players.find((x: { name: string }) => x.name === 'Wes')?.water) } }
    })
  })
  await tv.goto('/healthz')
  expect(await water()).toBe(true)
  await lobbySwitch.click()
  await expect(lobbySwitch).toHaveAttribute('aria-checked', 'false')
  expect(await water()).toBe(false)
})

test("the captain sets the timer length from their phone", async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)
  const p = await (await browser.newContext()).newPage()
  await p.goto(`/j/${room}`)
  await p.getByLabel('Your name').fill('Cap')
  await p.getByRole('button', { name: 'Join the party' }).click()
  await expect(p.getByRole('heading', { name: 'You have the crown' })).toBeVisible()
  const row = p.locator('.setting-row').filter({ hasText: 'Timers' })
  await expect(row.locator('b')).toHaveText('Normal')
  await p.getByRole('button', { name: 'More Timers' }).click()
  await expect(row.locator('b')).toHaveText('Relaxed')
  await p.getByRole('button', { name: 'More Timers' }).click()
  await expect(row.locator('b')).toHaveText('No rush')
  await p.getByRole('button', { name: 'Less Timers' }).click()
  await p.getByRole('button', { name: 'Less Timers' }).click()
  await expect(row.locator('b')).toHaveText('Normal')
})
