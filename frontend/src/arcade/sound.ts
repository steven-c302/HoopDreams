/** Arcade sound effects synthesised with Web Audio: no files, works offline. */
let ctx: AudioContext | null = null;
let enabled = true;

export function setSoundEnabled(on: boolean): void {
  enabled = on;
}

/** Call from a click handler (browser autoplay rules). Resolves true once audio can play. */
export async function unlockAudio(): Promise<boolean> {
  ctx ??= new AudioContext();
  try {
    await Promise.race([ctx.resume(), new Promise((resolve) => setTimeout(resolve, 300))]);
  } catch {
    // resume() rejects when the context is closed; report "not running" below.
  }
  return ctx.state === "running";
}

function audio(): AudioContext | null {
  return enabled && ctx && ctx.state === "running" ? ctx : null;
}

function tone(freq: number, start: number, dur: number, type: OscillatorType = "square", gain = 0.2, slideTo?: number): void {
  const a = audio();
  if (!a) return;
  const t = a.currentTime + start;
  const osc = a.createOscillator();
  const env = a.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t);
  if (slideTo) osc.frequency.exponentialRampToValueAtTime(slideTo, t + dur);
  env.gain.setValueAtTime(gain, t);
  env.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  osc.connect(env).connect(a.destination);
  osc.start(t);
  osc.stop(t + dur + 0.05);
}

function noise(start: number, dur: number, gain = 0.3, freq = 1200): void {
  const a = audio();
  if (!a) return;
  const t = a.currentTime + start;
  const buffer = a.createBuffer(1, Math.ceil(a.sampleRate * dur), a.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1;
  const src = a.createBufferSource();
  src.buffer = buffer;
  const filter = a.createBiquadFilter();
  filter.type = "bandpass";
  filter.frequency.value = freq;
  const env = a.createGain();
  env.gain.setValueAtTime(gain, t);
  env.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  src.connect(filter).connect(env).connect(a.destination);
  src.start(t);
}

export const sfx = {
  swish() {
    noise(0, 0.35, 0.35, 2600);
    tone(900, 0.05, 0.15, "sine", 0.06, 1800);
  },
  bucket() {
    tone(523, 0, 0.1);
    tone(659, 0.1, 0.1);
    tone(784, 0.2, 0.3);
  },
  horn() {
    tone(233, 0, 0.9, "sawtooth", 0.18);
    tone(294, 0, 0.9, "sawtooth", 0.12);
    tone(349, 0, 0.9, "sawtooth", 0.1);
  },
  buzzer() {
    tone(98, 0, 1.3, "square", 0.25);
    tone(104, 0, 1.3, "square", 0.2);
  },
  fire() {
    noise(0, 0.9, 0.3, 500);
    tone(180, 0, 0.9, "sawtooth", 0.12, 900);
  },
  whistle() {
    tone(2900, 0, 0.22, "sine", 0.18);
    tone(2900, 0.3, 0.5, "sine", 0.18);
  },
  tick() {
    tone(1400, 0, 0.05, "square", 0.08);
  },
  spin() {
    for (let i = 0; i < 18; i++) tone(600 + i * 25, i * i * 0.012, 0.04, "square", 0.06);
  },
};
