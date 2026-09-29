import { expect, test } from '@playwright/test'
import { clearParty, hostPage } from './helpers'

const COLORS = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF71', '#2F6BFF', '#9B5DE5']

// The dev server is shared across spec files: leave the party empty for the next test.
test.afterEach(async ({ browser }) => { await clearParty((await hostPage(browser)).host) })

for (const n of [4, 6, 13, 16]) {
  test(`lobby with ${n} players: the players stay clear of the game settings and the games fit on screen`, async ({ browser, request }) => {
    const { host, room } = await hostPage(browser)
    await clearParty(host)
    for (let i = 0; i < n; i++) {
      const avatar = { face: `p:${String(i % 16).padStart(2, '0')}`, color: COLORS[i % COLORS.length] }
      await request.post('/api/join', { data: { room, name: `Player${i + 1}`, avatar } })
    }
    const tv = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage()
    await tv.goto('/tv')
    await expect(tv.locator('.cast-card')).toHaveCount(n)
    await tv.waitForTimeout(1200) // the cards settle

    const m = await tv.evaluate(() => {
      const box = (s: string) => document.querySelector(s)!.getBoundingClientRect()
      const cast = document.querySelector('.cast') as HTMLElement
      const cards = [...document.querySelectorAll('.cast-card')].map((c) => c.getBoundingClientRect())
      return {
        castOverflow: cast.scrollHeight - cast.clientHeight,
        lastCardBottom: Math.max(...cards.map((c) => c.bottom)),
        controlsTop: box('.controls-row').top,
        pickerBottom: box('.picker').bottom,
        rightBottom: box('.lobby-right').bottom,
        games: document.querySelectorAll('.picker > *').length,
      }
    })
    expect(m.games).toBeGreaterThanOrEqual(7) // the shape this guards: more games than fit in two rows of three
    expect(m.castOverflow, 'the player cards need more room than the cast box has').toBeLessThanOrEqual(1)
    expect(m.lastCardBottom, 'the last player card runs into the settings row').toBeLessThanOrEqual(m.controlsTop)
    expect(m.pickerBottom, 'the game picker runs off the bottom').toBeLessThanOrEqual(m.rightBottom + 1)
  })
}
