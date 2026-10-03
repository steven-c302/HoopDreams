import { expect, test } from '@playwright/test'

// Brain Drain with six teams: every team's name and row stays whole and on the 1920×1080 stage.
for (const beat of ['ballpark-bet', 'victim', 'steal', 'standings', 'final-reveal-end']) {
  test(`brain drain, six teams: ${beat} shows every team and nothing is cut off`, async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 })
    await page.goto(`/tv?gallery=trivia&beat=${beat}&teams=6`)
    await page.waitForTimeout(6000) // let the pop-ins settle

    const result = await page.evaluate(() => {
      const stage = document.querySelector('.tv-stage')!.getBoundingClientRect()
      const inside = (el: Element) => { const r = el.getBoundingClientRect(); return r.left >= stage.left - 2 && r.right <= stage.right + 2 && r.top >= stage.top - 2 && r.bottom <= stage.bottom + 2 }
      const cut = (el: Element) => el.scrollWidth > el.clientWidth + 1 || el.scrollHeight > el.clientHeight + 4
      return {
        stripNames: [...document.querySelectorAll('.team-strip .team-name')].map((e) => ({ text: e.textContent, cut: cut(e) })),
        offStage: [...document.querySelectorAll('.team-badge, .bar-row, .ladder-row, .nl-team, .trivia-foot')].filter((e) => !inside(e)).map((e) => String(e.className)),
        targets: document.querySelectorAll('.targets .team-badge').length,
        ladderCovered: (() => {
          const hit = (a: DOMRect, b: DOMRect) => a.bottom > b.top && a.top < b.bottom && a.right > b.left && a.left < b.right
          const over = [document.querySelector('.drink-call'), document.querySelector('.team-strip')].filter(Boolean).map((e) => e!.getBoundingClientRect())
          return [...document.querySelectorAll('.ladder-row')].filter((row) => over.some((o) => hit(row.getBoundingClientRect(), o))).length
        })(),
        drinkOverStrip: (() => {
          const d = document.querySelector('.drink-call')?.getBoundingClientRect(); const s = document.querySelector('.team-strip')?.getBoundingClientRect()
          return !!d && !!s && d.bottom > s.top && d.top < s.bottom && d.right > s.left && d.left < s.right
        })(),
      }
    })

    expect(result.offStage).toEqual([])
    expect(result.drinkOverStrip).toBe(false)
    expect(result.ladderCovered).toBe(0)
    if (beat !== 'victim' && beat !== 'standings') {
      expect(result.stripNames.map((n) => n.text)).toHaveLength(6)
      expect(result.stripNames.filter((n) => n.cut)).toEqual([])
    }
    if (beat === 'victim') expect(result.targets).toBe(5)
  })
}
