import type { HotTypeTv } from './types'

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`
const longestOf = (lengths: number[]) => Math.max(0, ...lengths)

/**
 * The HOST bar's line. During play it uses only what the rail already shows (counts and word lengths), so it can never
 * give a word away or say a word is unique.
 */
export function hostLine(g: HotTypeTv, name: (id: string) => string = (id) => id): string {
  switch (g.phase) {
    case 'ready': return 'Same board for everyone. Fingers ready.'
    case 'press': return 'Pencils down. Nothing more gets stamped.'
    case 'hunt': return huntLine(g)
    case 'reveal': return revealLine(g, name)
    case 'scores': return scoresLine(g)
    default: return ''
  }
}

const joinNames = (names: string[]) => (names.length <= 1 ? names.join('') : `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`)

function revealLine(g: HotTypeTv, name: (id: string) => string): string {
  const longest = g.page.find((w) => w.longest)
  if (!longest) return g.missed ? `Nobody found a thing. ${g.missed.word.toUpperCase()} was right there.` : 'Nobody found a thing.'
  const uniques = g.page.filter((w) => w.bonus > 0).length
  const tail = uniques === 0 ? 'Everyone found the same words.'
    : uniques === 1 ? 'One stamped word was found by one player only.'
    : `${uniques} stamped words were found by one player only.`
  return `${joinNames(longest.finders.map(name))} found ${longest.word.toUpperCase()}. ${longest.word.length} letters. ${tail}`
}

function scoresLine(g: HotTypeTv): string {
  const top = [...g.deltas].sort((a, b) => b.total - a.total)[0]
  return top && top.total > 0 ? `${top.name} takes the round with ${top.total.toLocaleString()}.` : 'Nobody scored. Tough crowd.'
}

function huntLine(g: HotTypeTv): string {
  const byLength = [...g.rail].sort((a, b) => longestOf(b.lengths) - longestOf(a.lengths))
  const top = byLength[0]
  const topLen = top ? longestOf(top.lengths) : 0
  const tied = g.rail.filter((r) => longestOf(r.lengths) === topLen).length
  if (top && topLen >= 7 && tied === 1) return `${top.name} has the longest word so far: ${topLen} letters.`

  const [first, second] = [...g.rail].sort((a, b) => b.count - a.count)
  if (first && first.count >= 3 && first.count - (second?.count ?? 0) >= 2) {
    return `${first.name} is pulling ahead with ${plural(first.count, 'word')}.`
  }
  if (g.wordsFound === 0) return 'Nobody has a word yet. The board is right there.'
  return `${plural(g.wordsFound, 'word')} found so far. Plenty left.`
}
