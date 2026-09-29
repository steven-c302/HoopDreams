import { expect, test } from '@playwright/test'
import { clearParty, hostPage } from './helpers'

test('a phone joins with a photo and the host sees it', async ({ browser }) => {
  const { host, room } = await hostPage(browser)
  await clearParty(host)

  const p = await (await browser.newContext()).newPage()
  await p.goto(`/j/${room}`)
  await p.getByLabel('Your name').fill('Pic')
  await p.getByRole('tab', { name: 'Photo' }).click()
  // A tall "camera shot" made in the browser: the phone crops it square and shrinks it before uploading.
  const shot = await p.evaluate(() => {
    const c = document.createElement('canvas')
    c.width = 900; c.height = 1600
    const g = c.getContext('2d')!
    g.fillStyle = '#2f6bff'; g.fillRect(0, 0, 900, 1600)
    g.fillStyle = '#ffd23f'; g.beginPath(); g.arc(450, 800, 300, 0, Math.PI * 2); g.fill()
    return c.toDataURL('image/jpeg', 0.95).split(',')[1]
  })
  await p.getByLabel('Choose a photo').setInputFiles({ name: 'me.jpg', mimeType: 'image/jpeg', buffer: Buffer.from(shot, 'base64') })

  const preview = p.locator('.photo-pick image')
  await expect(preview).toHaveAttribute('href', /^\/api\/avatar\/[0-9a-f]{16}\.jpg$/)
  const href = (await preview.getAttribute('href'))!
  const photo = await p.request.get(href)
  expect(photo.status()).toBe(200)
  expect(photo.headers()['content-type']).toBe('image/jpeg')
  expect((await photo.body()).length).toBeLessThanOrEqual(96 * 1024)

  await p.getByRole('button', { name: 'Join the party' }).click()
  await expect(p.getByRole('heading', { name: /You're in|You have the crown/ })).toBeVisible()
  // The host's player list draws the same photo, from the same address.
  await expect(host.locator('.players li').filter({ hasText: 'Pic' }).locator('image')).toHaveAttribute('href', href)
})
