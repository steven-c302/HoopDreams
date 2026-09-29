import { expect, test, type Page } from '@playwright/test'

const tvUrl = (beat: string) => `/tv?gallery=themes&game=hottype&beat=${beat}`
const phoneUrl = (beat: string) => `${tvUrl(beat)}&view=phone`

async function centre(page: Page, i: number) {
  const b = (await page.locator(`.hunt-tile[data-i="${i}"]`).boundingBox())!
  return { x: b.x + b.width / 2, y: b.y + b.height / 2 }
}

/** Drags a finger across the given tiles (in order). Lifts at the end unless [lift] is false. */
async function swipe(page: Page, path: number[], lift = true, steps = 4) {
  const pts = await Promise.all(path.map((i) => centre(page, i)))
  await page.mouse.move(pts[0].x, pts[0].y)
  await page.mouse.down()
  for (const p of pts.slice(1)) await page.mouse.move(p.x, p.y, { steps })
  if (lift) await page.mouse.up()
}

test.describe('the phone board', () => {
  test('dragging across touching tiles spells the word, and lifting sends it', async ({ page }) => {
    await page.goto(phoneUrl('hunt'))
    await swipe(page, [0, 1, 5, 6, 9], false)
    await expect(page.locator('.hunt-preview')).toContainText('STONE')
    await expect(page.locator('.hunt-hint')).toContainText('5 LETTERS = 800')
    await page.mouse.up()
    await expect(page.getByText('Preview only · no action sent')).toBeVisible()
  })

  test('a fast diagonal covers the tiles in between', async ({ page }) => {
    await page.goto(phoneUrl('hunt'))
    await swipe(page, [0, 10], false, 1) // S, then C in one jump: S, O, C
    await expect(page.locator('.hunt-preview')).toContainText('SOC')
    await page.mouse.up()
  })

  test('backing up over the last tile erases it', async ({ page }) => {
    await page.goto(phoneUrl('hunt'))
    await swipe(page, [0, 1, 5, 1], false)
    await expect(page.locator('.hunt-preview')).toContainText('ST')
    await expect(page.locator('.hunt-preview')).not.toContainText('STO')
    await page.mouse.up()
  })

  test('a second finger is ignored while the first is down', async ({ page }) => {
    await page.goto(phoneUrl('hunt'))
    await swipe(page, [0], false)
    const other = await centre(page, 3)
    await page.evaluate(({ x, y }) => {
      document.querySelector('.hunt-board')!.dispatchEvent(new PointerEvent('pointerdown', { pointerId: 9, clientX: x, clientY: y, bubbles: true }))
    }, other)
    await expect(page.locator('.hunt-preview')).toHaveText('S')
    await page.mouse.up()
  })

  test('lifting under three letters sends nothing', async ({ page }) => {
    await page.goto(phoneUrl('hunt'))
    await swipe(page, [0, 1])
    await expect(page.locator('.hunt-hint')).toContainText('3 LETTERS MINIMUM')
    await expect(page.getByText('Preview only · no action sent')).toHaveCount(0)
  })

  test('the last ten seconds redden the press bar and frame only', async ({ page }) => {
    await page.goto(phoneUrl('last10'))
    await expect(page.locator('.hunt.hot')).toBeVisible()
    // Only the bar turns red (#c4432f). The board bed and the preview stay their normal colours: no red wash.
    await expect(page.locator('.hunt-bar i')).toHaveCSS('background-color', 'rgb(196, 67, 47)')
    await expect(page.locator('.hunt-bed')).toHaveCSS('background-color', 'rgb(42, 39, 35)')
    await expect(page.locator('.hunt-preview')).toHaveCSS('background-color', 'rgb(244, 236, 214)')
  })

  test('the reveal lists own words with unique stamps', async ({ page }) => {
    await page.goto(phoneUrl('reveal'))
    await expect(page.locator('.hunt-list li').first()).toContainText('STONE')
    await expect(page.locator('.hunt-list em')).toHaveText('ONLY YOU')
    await expect(page.locator('.hunt-list small').first()).toContainText('FOUND IT')
  })
})

test.describe('the TV', () => {
  test('never draws text under 28px', async ({ page }) => {
    for (const beat of ['ready', 'hunt', 'big', 'press', 'reveal', 'scores', 'empty', 'crowd', 'podium']) {
      await page.goto(tvUrl(beat))
      await expect(page.locator('.ht-stage')).toBeVisible()
      await page.waitForTimeout(beat === 'reveal' ? 8000 : 400)
      const small = await page.evaluate(() => {
        const out: string[] = []
        for (const el of document.querySelectorAll('.ht-stage *')) {
          const own = [...el.childNodes].some((c) => c.nodeType === 3 && c.textContent!.trim())
          if (own && parseFloat(getComputedStyle(el).fontSize) < 28) out.push(`${(el as HTMLElement).className}: ${el.textContent}`)
        }
        return out
      })
      expect(small, beat).toEqual([])
    }
  })

  test('the hunt shows no found word, and the burst shows a length only', async ({ page }) => {
    await page.goto(tvUrl('big'))
    const text = await page.locator('.ht-stage').innerText()
    expect(text).toMatch(/7 LETTERS/)
    expect(text).not.toMatch(/STONE|\bTON\b|\bTOE\b|\bONE\b/i)
  })

  test('eight players fit above the host bar and a long name is cut', async ({ page }) => {
    await page.goto(tvUrl('crowd'))
    await expect(page.locator('.ht-row')).toHaveCount(8)
    const last = (await page.locator('.ht-row').last().boundingBox())!
    const host = (await page.locator('.ht-host').boundingBox())!
    expect(last.y + last.height).toBeLessThanOrEqual(host.y)
  })

  test('the reveal stamps the words one at a time, longest last, then shows the one that got away', async ({ page }) => {
    await page.goto(tvUrl('reveal'))
    await expect(page.locator('.ht-word')).toHaveCount(4, { timeout: 12_000 })
    await expect(page.locator('.ht-word').last()).toHaveClass(/longest/)
    await expect(page.locator('.ht-word').last()).toContainText('STONE')
    await expect(page.getByText('The one that got away')).toBeVisible()
    await expect(page.locator('.ht-word.unique .ht-stamp').first()).toHaveText('ONLY YOU')
  })

  test('a round with no words still shows the one that got away, and nobody drinks', async ({ page }) => {
    await page.goto(tvUrl('empty'))
    await expect(page.getByText('NO WORDS THIS ROUND')).toBeVisible({ timeout: 6000 })
    await expect(page.getByText('The one that got away')).toBeVisible()
    await expect(page.locator('.ht-host')).toContainText('Nobody found a thing')
    await page.goto(tvUrl('scores'))
    await expect(page.locator('.ht-drink')).toContainText('Drink 2 sips')
  })

  test('reduced motion turns the stamp animation off', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto(tvUrl('reveal'))
    await expect(page.locator('.ht-stamp').first()).toBeVisible({ timeout: 10_000 })
    const name = await page.locator('.ht-stamp').first().evaluate((el) => getComputedStyle(el).animationName)
    expect(name).toBe('none')
  })
})
