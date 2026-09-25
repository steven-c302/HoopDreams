/** Announcer voice using the Mac's built-in speech synthesis (local voices, works offline). */
let enabled = true;
let voice: SpeechSynthesisVoice | null = null;
const PREFERRED = ["Daniel", "Ralph", "Fred", "Alex", "Samantha"];

export function setVoiceEnabled(on: boolean): void {
  enabled = on;
  if (!on && "speechSynthesis" in window) speechSynthesis.cancel();
}

function pickVoice(): SpeechSynthesisVoice | null {
  const english = speechSynthesis.getVoices().filter((v) => v.lang.startsWith("en"));
  const local = english.filter((v) => v.localService);
  for (const name of PREFERRED) {
    const found = local.find((v) => v.name.startsWith(name));
    if (found) return found;
  }
  return local[0] ?? english[0] ?? null;
}

/** Newest call wins: a backlog of stale announcements is worse than a skipped one. */
export function announce(text: string): void {
  if (!enabled || !("speechSynthesis" in window)) return;
  voice ??= pickVoice();
  if (speechSynthesis.speaking || speechSynthesis.pending) speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  if (voice) utterance.voice = voice;
  utterance.rate = 1.08;
  utterance.pitch = 0.9;
  speechSynthesis.speak(utterance);
}
