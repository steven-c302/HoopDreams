// Builds tv/engine/src/main/resources/packs/songdrop-core.json from seed.txt using yt-dlp (needs `brew install yt-dlp`).
// For every "artist | title | year | genre" line it searches YouTube, prefers an auto-generated "- Topic" upload whose title
// matches (no intro, often embeddable), checks the video with YouTube's oEmbed, and starts the clip at the "most replayed"
// peak when YouTube publishes one (else 40 s). overrides.json can pin a videoId or startSec for any song id.
// Usage: node controller/scripts/songs/build-deck.mjs
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const out = join(here, '../../../tv/engine/src/main/resources/packs/songdrop-core.json')
const overrides = JSON.parse(readFileSync(join(here, 'overrides.json'), 'utf8'))
const norm = (s) => s.toLowerCase().normalize('NFKD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, ' ').trim()
const slug = (s) => norm(s).replace(/ /g, '-')

function search(artist, title) {
  const q = `ytsearch4:${artist} ${title} topic`
  const raw = execFileSync('yt-dlp', ['--no-warnings', '--skip-download', '--dump-single-json', q], { maxBuffer: 1 << 27 }).toString()
  return JSON.parse(raw).entries ?? []
}

function pick(entries, artist, title) {
  const t = norm(title), a = norm(artist)
  const ok = entries.filter((e) => e.duration > 60 && e.duration < 600 && norm(e.title ?? '').includes(t.split(' ')[0]) && (norm(e.channel ?? e.uploader ?? '') + ' ' + norm(e.title ?? '')).includes(a.split(' ')[0]))
  return ok.find((e) => /topic$/i.test(e.channel ?? e.uploader ?? '')) ?? ok[0]
}

function hook(entry) {
  const peaks = (entry.heatmap ?? []).filter((h) => h.start_time >= 10 && h.end_time <= entry.duration - 15)
  if (!peaks.length) return 40
  return Math.floor(peaks.reduce((best, h) => (h.value > best.value ? h : best)).start_time)
}

async function embeddable(id) {
  const r = await fetch(`https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${id}&format=json`)
  return r.status === 200
}

const items = []
const skipped = []
for (const line of readFileSync(join(here, 'seed.txt'), 'utf8').split('\n').filter((l) => l.trim())) {
  const [artist, title, year, genre] = line.split('|').map((s) => s.trim())
  const id = slug(`${artist} ${title}`)
  const fix = overrides[id] ?? {}
  try {
    let videoId = fix.videoId, startSec = fix.startSec
    if (!videoId) {
      const entry = pick(search(artist, title), artist, title)
      if (!entry) { skipped.push(`${id}: no matching video`); continue }
      videoId = entry.id
      startSec ??= hook(entry)
    }
    if (!(await embeddable(videoId))) { skipped.push(`${id}: oEmbed refused ${videoId}`); continue }
    items.push({ id, title, artist, year: Number(year), genre, videoId, startSec: startSec ?? 40 })
    console.log(`ok  ${id} ${videoId} @${startSec ?? 40}s`)
  } catch (e) { skipped.push(`${id}: ${e.message.split('\n')[0]}`) }
}
writeFileSync(out, JSON.stringify({ packId: 'songdrop-core', title: 'Song Drop core', game: 'songdrop', version: 1, items }, null, 2) + '\n')
console.log(`\n${items.length} songs written, ${skipped.length} skipped:\n${skipped.join('\n')}`)
