// The show's sound: every effect and the house band are synthesized with Web Audio, so there are no
// files to load and every cue can change pitch with the moment (each join or bluff lands a step higher).
// Buses: sfx and music feed master. Character sits in 300Hz–5kHz because TV speakers have no bass.

let ctx: AudioContext | null = null
let master: GainNode, sfxBus: GainNode, musicBus: GainNode
let noiseBuf: AudioBuffer

export interface Mix { on: boolean; music: number; sfx: number }
const KEY = 'partyos.tv.mix'
export function loadMix(): Mix {
  try { return { on: true, music: 0.5, sfx: 0.9, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') } } catch { return { on: true, music: 0.5, sfx: 0.9 } }
}
let mix = loadMix()

export function setMix(m: Mix) {
  mix = m
  try { localStorage.setItem(KEY, JSON.stringify(m)) } catch { /* private mode */ }
  if (!ctx) return
  const t = ctx.currentTime
  master.gain.setTargetAtTime(m.on ? 0.9 : 0, t, 0.05)
  musicBus.gain.setTargetAtTime(m.music * 0.55, t, 0.05)
  sfxBus.gain.setTargetAtTime(m.sfx, t, 0.05)
}

/** Creates/resumes the context. Must run inside a user gesture unless Chrome was launched with autoplay allowed. */
export function unlockAudio(): boolean {
  if (!ctx) {
    ctx = new AudioContext({ latencyHint: 'interactive' })
    const comp = ctx.createDynamicsCompressor()
    comp.threshold.value = -14; comp.ratio.value = 4; comp.attack.value = 0.004; comp.release.value = 0.2
    comp.connect(ctx.destination)
    master = ctx.createGain(); master.connect(comp)
    sfxBus = ctx.createGain(); sfxBus.connect(master)
    musicBus = ctx.createGain(); musicBus.connect(master)
    noiseBuf = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate)
    const d = noiseBuf.getChannelData(0)
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1
    setMix(mix)
    startScheduler()
  }
  if (ctx.state !== 'running') void ctx.resume()
  return ctx.state === 'running'
}
export const audioRunning = () => ctx?.state === 'running'

const midi = (m: number) => 440 * Math.pow(2, (m - 69) / 12)
const jitter = (amount = 0.04) => 1 + (Math.random() * 2 - 1) * amount

// ---------- voices ----------

interface ToneOpts { type?: OscillatorType; gain?: number; attack?: number; release?: number; slideTo?: number; bus?: GainNode; lp?: number; detune?: number }
function tone(freq: number, t: number, dur: number, o: ToneOpts = {}) {
  if (!ctx) return
  const osc = ctx.createOscillator()
  osc.type = o.type ?? 'sine'
  osc.frequency.setValueAtTime(freq, t)
  if (o.detune) osc.detune.value = o.detune
  if (o.slideTo) osc.frequency.exponentialRampToValueAtTime(o.slideTo, t + dur)
  const g = ctx.createGain()
  const peak = o.gain ?? 0.2
  const a = o.attack ?? 0.005
  g.gain.setValueAtTime(0.0001, t)
  g.gain.exponentialRampToValueAtTime(peak, t + a)
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur + (o.release ?? 0.05))
  let out: AudioNode = g
  osc.connect(g)
  if (o.lp) {
    const f = ctx.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = o.lp
    g.connect(f); out = f
  }
  out.connect(o.bus ?? sfxBus)
  osc.start(t); osc.stop(t + dur + (o.release ?? 0.05) + 0.02)
}

interface NoiseOpts { gain?: number; type?: BiquadFilterType; freq?: number; q?: number; sweepTo?: number; attack?: number; bus?: GainNode }
function noise(t: number, dur: number, o: NoiseOpts = {}) {
  if (!ctx) return
  const src = ctx.createBufferSource()
  src.buffer = noiseBuf
  const f = ctx.createBiquadFilter()
  f.type = o.type ?? 'bandpass'; f.frequency.setValueAtTime(o.freq ?? 2000, t); f.Q.value = o.q ?? 1
  if (o.sweepTo) f.frequency.exponentialRampToValueAtTime(o.sweepTo, t + dur)
  const g = ctx.createGain()
  g.gain.setValueAtTime(0.0001, t)
  g.gain.exponentialRampToValueAtTime(o.gain ?? 0.2, t + (o.attack ?? 0.003))
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur)
  src.connect(f); f.connect(g); g.connect(o.bus ?? sfxBus)
  src.start(t, Math.random()); src.stop(t + dur + 0.02)
}

/** Drawbar organ: stacked sine partials, the lounge band's signature. */
function organ(m: number, t: number, dur: number, gain = 0.05, bus = musicBus) {
  const f = midi(m);
  [[1, 1], [2, 0.55], [3, 0.35], [4, 0.2], [0.5, 0.4]].forEach(([mul, g]) => tone(f * mul, t, dur, { gain: gain * g, attack: 0.008, release: 0.06, bus }))
}
function brass(ms: number[], t: number, dur: number, gain = 0.07, bus = sfxBus) {
  ms.forEach((m) => {
    tone(midi(m), t, dur, { type: 'sawtooth', gain, attack: 0.02, release: 0.12, lp: 2600, bus, detune: -6 })
    tone(midi(m), t, dur, { type: 'sawtooth', gain: gain * 0.7, attack: 0.02, release: 0.12, lp: 2600, bus, detune: 7 })
  })
}
const kick = (t: number, g = 0.35, bus = musicBus) => tone(120, t, 0.18, { gain: g, slideTo: 45, bus })
const rim = (t: number, g = 0.12, bus = musicBus) => { noise(t, 0.05, { freq: 1900, q: 3, gain: g, bus }); tone(480, t, 0.03, { gain: g * 0.6, bus }) }
const hat = (t: number, g = 0.03, bus = musicBus) => noise(t, 0.04, { type: 'highpass', freq: 7000, gain: g, bus })
const clap = (t: number, g = 0.14, bus = musicBus) => { [0, 0.012, 0.024].forEach((d) => noise(t + d, 0.06, { freq: 1300, q: 1.2, gain: g, bus })) }
const pluck = (m: number, t: number, g = 0.08, bus = musicBus) => tone(midi(m), t, 0.16, { type: 'triangle', gain: g, bus, release: 0.04 })
const block = (m: number, t: number, g = 0.12, bus = musicBus) => { tone(midi(m), t, 0.05, { gain: g, bus }); noise(t, 0.02, { freq: midi(m) * 2, q: 8, gain: g * 0.5, bus }) }

// ---------- effects ----------

const penta = [0, 2, 4, 7, 9]
const scaleStep = (root: number, step: number) => root + penta[step % 5] + 12 * Math.floor(step / 5)
const T = () => (ctx ? ctx.currentTime + 0.01 : 0)

export const sfx = {
  /** Lightest tier: moving focus. */
  focus() { tone(1700 * jitter(), T(), 0.03, { gain: 0.05 }) },
  /** Middle tier: choosing something. */
  select() { const t = T(); tone(220, t, 0.12, { type: 'triangle', gain: 0.25, slideTo: 110 }); noise(t, 0.03, { freq: 3000, gain: 0.08 }) },
  back() { noise(T(), 0.18, { freq: 2500, sweepTo: 500, q: 2, gain: 0.12 }) },
  /** A contestant joins: a xylophone note one step higher each time. */
  join(step: number) {
    const t = T(); const f = midi(scaleStep(72, step % 12))
    tone(f, t, 0.35, { gain: 0.22 }); tone(f * 4, t, 0.08, { gain: 0.06 }); tone(f * 2.76, t, 0.12, { gain: 0.05 })
  },
  leave() { tone(midi(64), T(), 0.25, { type: 'triangle', gain: 0.12, slideTo: midi(57) }) },
  /** A bluff or pick is in: a poker-chip clink that climbs with progress. */
  chip(step: number) {
    const t = T(); const f = 2000 * Math.pow(2, Math.min(step, 16) / 16) * jitter(0.02)
    tone(f, t, 0.06, { gain: 0.12 }); tone(f * 1.48, t + 0.035, 0.07, { gain: 0.09 }); noise(t, 0.02, { type: 'highpass', freq: 5000, gain: 0.08 })
  },
  /** One card dealt onto the table. */
  deal(step: number) { const t = T(); noise(t, 0.07, { freq: 2600 + step * 180, q: 1.5, sweepTo: 5000, gain: 0.2 }); tone(300 + step * 20, t, 0.03, { gain: 0.06 }) },
  ding() { const t = T(); tone(1318, t, 0.5, { gain: 0.18 }); tone(1318 * 2.01, t, 0.2, { gain: 0.05 }) },
  allIn() { const t = T(); [0, 0.14].forEach((d, i) => { tone(i ? 1760 : 1318, t + d, 0.55, { gain: 0.2 }); tone((i ? 1760 : 1318) * 3, t + d, 0.1, { gain: 0.04 }) }) },
  buzzer() { const t = T(); tone(110, t, 0.7, { type: 'square', gain: 0.1, lp: 1400 }); tone(117, t, 0.7, { type: 'square', gain: 0.1, lp: 1400 }) },
  /** Last five seconds: woodblock tick-tock that rises at 3-2-1. */
  tick(secondsLeft: number) { block(secondsLeft <= 3 ? 84 + (3 - secondsLeft) * 2 : secondsLeft % 2 ? 79 : 76, T(), 0.2, sfxBus) },
  /** Round-start band hit. */
  roundStart(final = false) {
    const t = T(); const root = final ? 62 : 60
    brass([root, root + 4, root + 7, root + 11], t, 0.35, 0.06); brass([root + 12, root + 16], t + 0.18, 0.5, 0.05)
    kick(t, 0.4, sfxBus); noise(t, 0.6, { type: 'highpass', freq: 5000, gain: 0.1 })
  },
  /** The show opens: a two-part fanfare. */
  showOpen() {
    const t = T()
    ;[[60, 64, 67], [62, 65, 69], [64, 67, 72]].forEach((ch, i) => brass(ch, t + i * 0.16, 0.14, 0.05))
    brass([65, 69, 72, 77], t + 0.52, 0.8, 0.06); kick(t + 0.52, 0.4, sfxBus); noise(t + 0.52, 1.2, { type: 'highpass', freq: 4500, gain: 0.12 })
  },
  drumroll(sec = 1.6) {
    const t = T(); const n = Math.floor(sec * 28)
    for (let i = 0; i < n; i++) noise(t + i / 28, 0.05, { freq: 900, q: 0.7, gain: 0.03 + 0.1 * (i / n) })
  },
  crash() { noise(T(), 1.6, { type: 'highpass', freq: 3500, gain: 0.22 }) },
  /** Wah-wah trombone for a fake that fooled people. */
  womp() {
    const t = T()
    ;[[63, 0], [62, 0.28], [61, 0.56]].forEach(([m, d]) => tone(midi(m), t + d, 0.24, { type: 'sawtooth', gain: 0.1, lp: 900, attack: 0.03 }))
    tone(midi(60), t + 0.84, 0.7, { type: 'sawtooth', gain: 0.1, lp: 700, attack: 0.03, slideTo: midi(58) })
  },
  /** Studio audience "ooooh": detuned low voices sliding up, then down. Bigger crowd = more fooled. */
  ooh(size: number) {
    if (!ctx) return
    const t = T(); const voices = Math.min(3 + size * 2, 12)
    for (let i = 0; i < voices; i++) {
      const f = 190 * jitter(0.18); const o = ctx.createOscillator(); o.type = 'sawtooth'
      o.frequency.setValueAtTime(f, t); o.frequency.linearRampToValueAtTime(f * 1.35, t + 0.45); o.frequency.linearRampToValueAtTime(f * 1.05, t + 1.3)
      const bp = ctx.createBiquadFilter(); bp.type = 'bandpass'; bp.frequency.value = 520; bp.Q.value = 3
      const g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.035, t + 0.25); g.gain.exponentialRampToValueAtTime(0.0001, t + 1.4)
      o.connect(bp); bp.connect(g); g.connect(sfxBus); o.start(t + Math.random() * 0.08); o.stop(t + 1.5)
    }
  },
  laugh() { const t = T(); for (let i = 0; i < 7; i++) noise(t + i * 0.13 + Math.random() * 0.03, 0.1, { freq: 700 + Math.random() * 400, q: 4, gain: 0.07 }) },
  applause(sec = 2.8) {
    const t = T(); const n = Math.floor(sec * 70)
    for (let i = 0; i < n; i++) {
      const at = t + Math.random() * sec
      const fade = 1 - Math.max(0, (at - t) - sec * 0.6) / (sec * 0.4)
      noise(at, 0.03, { freq: 1100 + Math.random() * 1400, q: 1.5, gain: 0.05 * fade })
    }
  },
  fanfare() {
    const t = T()
    ;[[67, 0], [72, 0.13], [76, 0.26]].forEach(([m, d]) => brass([m], t + d, 0.12, 0.06))
    brass([72, 76, 79, 84], t + 0.4, 0.9, 0.055); kick(t + 0.4, 0.4, sfxBus); noise(t + 0.4, 1.4, { type: 'highpass', freq: 4000, gain: 0.14 })
  },
  countTick(step: number) { tone(1200 + step * 25, T(), 0.025, { type: 'square', gain: 0.025, lp: 4000 }) },
  whoosh() { noise(T(), 0.35, { freq: 400, sweepTo: 4000, q: 1.2, gain: 0.14, attack: 0.1 }) },
  pop() { const t = T(); tone(500, t, 0.08, { gain: 0.2, slideTo: 1200 }); noise(t, 0.05, { freq: 2500, gain: 0.1 }) },
  stamp() { const t = T(); kick(t, 0.3, sfxBus); noise(t, 0.08, { freq: 900, q: 0.8, gain: 0.25 }) },
  scratch() { const t = T(); noise(t, 0.12, { freq: 800, sweepTo: 3500, q: 4, gain: 0.25 }); noise(t + 0.12, 0.16, { freq: 3500, sweepTo: 600, q: 4, gain: 0.25 }) },
  // ---- casino table ----
  /** Clay chips set down: a quick double clack, pitched by stack size. */
  chipClack(step = 0) {
    const t = T(); const f = 3200 * Math.pow(2, Math.min(step, 12) / 24)
    ;[0, 0.045].forEach((d, i) => { noise(t + d, 0.025, { freq: f * (i ? 1.12 : 1) * jitter(0.03), q: 6, gain: 0.22 }); tone(f / 3, t + d, 0.02, { gain: 0.05 }) })
  },
  /** One card slid off the shoe. */
  cardFlick(step = 0) { const t = T(); noise(t, 0.06, { freq: 3800 + (step % 6) * 150, q: 2, sweepTo: 7000, gain: 0.16 }); noise(t + 0.05, 0.03, { freq: 900, q: 1, gain: 0.08 }) },
  /** The dealer turns the hole card. */
  cardFlip() { const t = T(); noise(t, 0.09, { freq: 2200, sweepTo: 5500, q: 1.5, gain: 0.2 }); kick(t + 0.07, 0.16, sfxBus) },
  jackpot() {
    const t = T()
    ;[72, 76, 79, 84, 88, 91, 96].forEach((m, i) => { tone(midi(m), t + i * 0.07, 0.4, { gain: 0.13 }); tone(midi(m) * 2.76, t + i * 0.07, 0.1, { gain: 0.04 }) })
    for (let i = 0; i < 26; i++) noise(t + 0.3 + Math.random() * 1.4, 0.05, { type: 'highpass', freq: 6000 + Math.random() * 3000, gain: 0.07 })
    brass([72, 76, 79], t + 0.5, 0.7, 0.05)
  },
  bust() {
    const t = T(); noise(t, 0.45, { type: 'highpass', freq: 2500, gain: 0.3 })
    tone(330, t, 0.5, { type: 'square', gain: 0.07, slideTo: 90, lp: 1600 })
  },
  /** Funky little brass lick for winning hands. */
  winSting() { const t = T(); [[65, 0], [69, 0.09], [72, 0.18], [75, 0.27], [76, 0.34]].forEach(([m, d]) => brass([m], t + d, d === 0.34 ? 0.5 : 0.08, 0.06)); kick(t + 0.34, 0.3, sfxBus) },
  /** Everyone lost: a sad guitar slide down. */
  loseSting() {
    const t = T()
    ;[[64, 0], [63, 0.3], [62, 0.6]].forEach(([m, d]) => tone(midi(m) / 2, t + d, 0.26, { type: 'sawtooth', gain: 0.1, lp: 1300, attack: 0.01 }))
    tone(midi(61) / 2, t + 0.9, 1.1, { type: 'sawtooth', gain: 0.1, lp: 1000, slideTo: midi(56) / 2 })
  },
  /** Chips pushed across the felt to a winner. */
  payout(n = 8) { const t = T(); for (let i = 0; i < n; i++) noise(t + i * 0.04 + Math.random() * 0.015, 0.025, { freq: 3000 + Math.random() * 1200, q: 5, gain: 0.12 }) },
  resume() { const t = T(); [0, 0.08, 0.16].forEach((d, i) => tone(midi(72 + penta[i + 2]), t + d, 0.15, { type: 'triangle', gain: 0.14 })) },
}

// ---------- the house band ----------

export type Mode = 'off' | 'lobby' | 'think' | 'reveal' | 'scores' | 'casino'
let want: Mode = 'off'
let playing: Mode = 'off'
let hurry = false
let step = 0
let nextAt = 0

export function setMusic(m: Mode) { want = m }
export function setHurry(h: boolean) { hurry = h }

const BPM: Record<Mode, number> = { off: 100, lobby: 108, think: 96, reveal: 84, scores: 114, casino: 124 }

function startScheduler() {
  nextAt = ctx!.currentTime + 0.1
  setInterval(() => {
    if (!ctx || ctx.state !== 'running') return
    if (nextAt < ctx.currentTime - 0.2) nextAt = ctx.currentTime + 0.05 // tab was asleep
    while (nextAt < ctx.currentTime + 0.15) {
      if (step % 16 === 0 && want !== playing) { playing = want; step = 0 }
      const bpm = BPM[playing] * (playing === 'think' && hurry ? 1.22 : 1)
      const dur = 60 / bpm / 4
      const swing = playing === 'lobby' && step % 4 === 2 ? dur * 0.33 : 0
      playStep(playing, step, nextAt + swing, dur)
      nextAt += dur
      step = (step + 1) % 64
    }
  }, 25)
}

// Lobby: F major ii-V-ish lounge vamp. Four bars, walking bass in quarters.
const LOBBY_BASS = [[41, 45, 48, 40], [38, 41, 45, 42], [43, 46, 50, 47], [36, 40, 43, 40]]
const LOBBY_CHORD = [[57, 60, 64], [57, 60, 65], [58, 62, 65], [58, 64, 67]]
// Think: D minor, sneaky pizzicato with a ticking clock.
const THINK_BASS = [38, 38, 34, 33]
const THINK_MOTIF = [74, -1, -1, 77, -1, 76, -1, 74, 72, -1, 74, -1, 69, -1, -1, -1]
// Scores: F strut.
const STRUT_BASS = [[41, 41, 44, 45], [46, 46, 45, 43]]
// Casino: lounge bossa over Fmaj7 · Em7 · Dm7 · G7 with a vibraphone.
const BOSSA_ROOT = [41, 40, 38, 43]
const VIBES = [[69, 72, 76, 79], [67, 71, 74, 76], [65, 69, 72, 76], [67, 71, 74, 77]]
const vibe = (m: number, t: number, g = 0.045) => { tone(midi(m), t, 0.5, { gain: g, bus: musicBus, release: 0.3 }); tone(midi(m) * 4, t, 0.05, { gain: g * 0.25, bus: musicBus }) }

function playStep(mode: Mode, s: number, t: number, dur: number) {
  const bar = Math.floor(s / 16), beat = s % 16
  switch (mode) {
    case 'lobby': {
      if (beat % 4 === 0) tone(midi(LOBBY_BASS[bar][beat / 4]), t, dur * 3.2, { type: 'triangle', gain: 0.22, bus: musicBus, lp: 900 })
      if (beat === 0 || beat === 8) kick(t, 0.2)
      if (beat === 4 || beat === 12) rim(t, 0.09)
      if (beat % 2 === 0) hat(t, beat % 4 === 2 ? 0.035 : 0.02)
      if (beat === 2 || beat === 10 || (bar === 3 && beat === 14)) LOBBY_CHORD[bar].forEach((m) => organ(m, t, dur * 1.6, 0.035))
      if (bar === 3 && beat === 12) brass([65, 69], t, dur * 1.5, 0.03, musicBus)
      break
    }
    case 'think': {
      const b = THINK_BASS[bar]
      if ([0, 3, 6, 8, 11, 14].includes(beat)) pluck(b + (beat === 6 || beat === 14 ? 7 : 0) - 12 + 12, t, 0.14)
      if (beat % 4 === 0) block(beat % 8 === 0 ? 81 : 76, t, 0.06) // the clock
      const m = THINK_MOTIF[beat]
      if (bar % 2 === 1 && m > 0) pluck(m, t, 0.05)
      if (hurry) hat(t, 0.02); else if (beat % 2 === 0) hat(t, 0.012)
      break
    }
    case 'reveal': {
      if (beat === 0) { tone(midi(38), t, dur * 15, { type: 'sawtooth', gain: 0.05, lp: 380, attack: 0.3, bus: musicBus }); tone(midi(45), t, dur * 15, { type: 'sawtooth', gain: 0.035, lp: 380, attack: 0.3, bus: musicBus }) }
      if (beat === 0 || beat === 8) tone(90, t, 0.35, { gain: 0.22, slideTo: 60, bus: musicBus })
      if (beat === 14) noise(t, 0.08, { freq: 900, gain: 0.03, bus: musicBus })
      break
    }
    case 'scores': {
      const bb = STRUT_BASS[bar % 2]
      if ([0, 3, 6, 10].includes(beat)) tone(midi(bb[[0, 3, 6, 10].indexOf(beat)]), t, dur * 1.4, { type: 'triangle', gain: 0.22, bus: musicBus, lp: 1100 })
      if (beat === 0 || beat === 7 || beat === 10) kick(t, 0.22)
      if (beat === 4 || beat === 12) clap(t, 0.08)
      hat(t, beat % 2 ? 0.012 : 0.025)
      if (beat === 0 && bar % 2 === 0) brass([65, 69, 72], t, dur * 1.2, 0.028, musicBus)
      if (beat === 14 && bar % 2 === 1) brass([67, 70, 74], t, dur * 1.2, 0.028, musicBus)
      break
    }
    case 'casino': {
      const root = BOSSA_ROOT[bar]
      if (beat === 0 || beat === 6) tone(midi(root), t, dur * 3, { type: 'triangle', gain: 0.2, bus: musicBus, lp: 800 })
      if (beat === 8 || beat === 14) tone(midi(root + 7), t, dur * 2, { type: 'triangle', gain: 0.16, bus: musicBus, lp: 800 })
      if ([0, 3, 6, 10, 12].includes(beat)) rim(t, 0.05)
      if (beat % 2 === 0) noise(t, 0.05, { type: 'highpass', freq: 8000, gain: 0.018, bus: musicBus })
      if (beat === 0 || beat === 8) kick(t, 0.14)
      if (beat % 4 === 2) vibe(VIBES[bar][(beat / 4 + bar) % 4 | 0], t)
      if (beat === 12 && bar % 2 === 1) VIBES[bar].slice(0, 3).forEach((m, i) => vibe(m, t + i * dur * 0.5, 0.03))
      break
    }
    default:
  }
}
