#!/usr/bin/env node
// Generates Party OS's score and sound effects with ElevenLabs, masters them with ffmpeg, and writes the manifest
// the TV's audio engine loads (controller/public/assets/audio/manifest.json).
//
//   ELEVENLABS_API_KEY=... node controller/scripts/audio/generate.mjs            # everything that's missing
//   node controller/scripts/audio/generate.mjs --only heist,correct --force       # re-roll specific cues
//   node controller/scripts/audio/generate.mjs --master-only                      # re-master raw takes, rebuild manifest
//
// Raw takes are kept in scripts/audio/raw (git-ignored) so re-mastering never costs credits.
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const cues = JSON.parse(readFileSync(join(here, 'cues.json'), 'utf8'))
const RAW = join(here, 'raw')
const OUT = join(here, '../../public/assets/audio')
const API = 'https://api.elevenlabs.io/v1'
const args = process.argv.slice(2)
const only = args.includes('--only') ? new Set(args[args.indexOf('--only') + 1].split(',')) : null
const force = args.includes('--force')
const masterOnly = args.includes('--master-only')
// The key comes from the environment, or from scripts/audio/.env (git-ignored): ELEVENLABS_API_KEY=...
const dotenv = join(here, '.env')
const key = process.env.ELEVENLABS_API_KEY
  ?? (existsSync(dotenv) ? readFileSync(dotenv, 'utf8').match(/^\s*ELEVENLABS_API_KEY\s*=\s*["']?([^"'\s]+)/m)?.[1] : undefined)
const LOOP_XFADE = 2 // seconds of the tail crossfaded into the head so loops are seamless

for (const d of [RAW, join(OUT, 'music'), join(OUT, 'sfx')]) mkdirSync(d, { recursive: true })
if (!masterOnly && !key) {
  console.error('Set ELEVENLABS_API_KEY (ElevenLabs → Developers → API keys) in your environment or in controller/scripts/audio/.env,\nor pass --master-only to rebuild from raw takes.')
  process.exit(1)
}

async function post(path, body) {
  const r = await fetch(`${API}${path}?output_format=mp3_44100_128`, {
    method: 'POST', headers: { 'xi-api-key': key, 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  })
  if (!r.ok) throw new Error(`${path} ${r.status}: ${(await r.text()).slice(0, 300)}`)
  return Buffer.from(await r.arrayBuffer())
}

const want = (id) => !only || only.has(id)
const ffmpeg = (a) => execFileSync('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y', ...a])
const duration = (f) => Number(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', f]).toString().trim())

/** Music: trim silence, normalise to -20 LUFS, then fold the last seconds over the first so the loop has no seam. */
function masterMusic(raw, out) {
  const tmp = out.replace(/\.mp3$/, '.tmp.wav')
  ffmpeg(['-i', raw, '-af', 'silenceremove=start_periods=1:start_threshold=-45dB,areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse,loudnorm=I=-20:TP=-2:LRA=11', '-ar', '44100', tmp])
  const len = duration(tmp)
  const x = Math.min(LOOP_XFADE, len / 6)
  ffmpeg(['-i', tmp, '-filter_complex',
    `[0]atrim=0:${x},asetpts=N/SR/TB[head];[0]atrim=${x}:${len - x},asetpts=N/SR/TB[mid];[0]atrim=${len - x}:${len},asetpts=N/SR/TB[tail];` +
    `[tail][head]acrossfade=d=${x}:c1=tri:c2=tri[seam];[mid][seam]concat=n=2:v=0:a=1,asetpts=N/SR/TB[out]`,
    '-map', '[out]', '-codec:a', 'libmp3lame', '-b:a', '160k', out])
  execFileSync('rm', ['-f', tmp])
}

/** Effects: trim leading silence, normalise to -16 LUFS with -3 dBTP peaks, mono, short fade out. */
function masterSfx(raw, out) {
  ffmpeg(['-i', raw, '-af', 'silenceremove=start_periods=1:start_threshold=-50dB,loudnorm=I=-16:TP=-3:LRA=7,areverse,afade=t=in:d=0.03,areverse',
    '-ac', '1', '-ar', '44100', '-codec:a', 'libmp3lame', '-b:a', '128k', out])
}

/** Runs one cue; a failed cue is reported and skipped so the rest of the score still renders. */
async function attempt(label, fn) {
  try { await fn() } catch (e) { failures.push(label); console.log(`FAILED ${label}: ${e.message}`) }
}
const failures = []

async function run() {
  let spent = 0
  for (const m of cues.music) {
    if (!want(m.id)) continue
    const raw = join(RAW, `music-${m.id}.mp3`)
    if (!masterOnly && (force || !existsSync(raw))) {
      await attempt(`music ${m.id}`, async () => {
        process.stdout.write(`music ${m.id} (${m.seconds}s)… `)
        writeFileSync(raw, await post('/music', { prompt: `${cues.style} ${m.prompt}`, music_length_ms: m.seconds * 1000, force_instrumental: true }))
        spent += m.seconds; console.log('ok')
      })
    }
    if (existsSync(raw)) await attempt(`master ${m.id}`, async () => masterMusic(raw, join(OUT, 'music', `${m.id}.mp3`)))
  }
  for (const s of cues.sfx) {
    if (!want(s.id)) continue
    for (let v = 1; v <= (s.variants ?? 1); v++) {
      const raw = join(RAW, `sfx-${s.id}-${v}.mp3`)
      if (!masterOnly && (force || !existsSync(raw))) {
        await attempt(`sfx ${s.id} #${v}`, async () => {
          process.stdout.write(`sfx ${s.id} #${v}… `)
          // The API accepts 0.5–30 s; very short cues are trimmed by mastering anyway.
          const seconds = Math.min(30, Math.max(0.5, s.seconds))
          writeFileSync(raw, await post('/sound-generation', { text: `${s.prompt}. Cartoon game show sound, clean, no music.`, duration_seconds: seconds, prompt_influence: 0.6 }))
          console.log('ok')
        })
      }
      if (existsSync(raw)) await attempt(`master ${s.id} #${v}`, async () => masterSfx(raw, join(OUT, 'sfx', `${s.id}-${v}.mp3`)))
    }
  }
  if (spent) console.log(`Generated ${Math.round(spent / 6) / 10} min of music.`)
  if (failures.length) console.log(`${failures.length} cue(s) failed; run again to retry just those: ${failures.join(', ')}`)
}

function writeManifest() {
  const sfx = {}
  for (const f of readdirSync(join(OUT, 'sfx')).filter((f) => f.endsWith('.mp3')).sort()) {
    const id = f.replace(/-\d+\.mp3$/, '')
    ;(sfx[id] ??= []).push(`sfx/${f}`)
  }
  const music = {}
  for (const f of readdirSync(join(OUT, 'music')).filter((f) => f.endsWith('.mp3')).sort()) music[f.replace(/\.mp3$/, '')] = { file: `music/${f}`, gain: 0.9 }
  // Phases that share a bed.
  if (music.standings) music.scores = music.standings
  if (music.lobby && !music.off) delete music.off
  writeFileSync(join(OUT, 'manifest.json'), JSON.stringify({ sfx, music }, null, 1) + '\n')
  console.log(`manifest: ${Object.keys(music).length} music beds, ${Object.keys(sfx).length} effects`)
}

// The manifest is always rewritten, even after failures, so whatever did render starts playing.
run().catch((e) => console.error(e.message)).finally(() => { writeManifest(); if (failures.length) process.exitCode = 1 })
