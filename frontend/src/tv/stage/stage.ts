import type { Moment } from "../../party/store";

export type ShotMoment = Extract<Moment, { type: "shot" }>;
export type StageKind = "shot" | "milestone" | "lead-change" | "waved-off" | "replay";

export interface StageItem {
  key: string;
  kind: StageKind;
  moments: Moment[];
  shownAt: number | null;
  until: number | null;
}

export interface StageState {
  current: StageItem | null;
  queue: StageItem[];
}

export interface StageTiming {
  comboWindowMs: number;
  comboMaxMs: number;
}

/** Replays end early via finish() when the clip ends; 16 s is only a safety cap. */
export const DURATION_MS: Record<StageKind, number> = {
  shot: 3200,
  milestone: 3500,
  "lead-change": 3200,
  "waved-off": 2600,
  replay: 16_000,
};

const MAX_QUEUE = 20;

export const emptyStage: StageState = { current: null, queue: [] };

export function isShot(m: Moment): m is ShotMoment {
  return m.type === "shot";
}

/** Game plugins render their own shots and moments; the stage only runs party moments. */
export function stageKind(m: Moment): StageKind | null {
  if (m.type === "game") return null;
  if (m.type === "shot" && m.source.startsWith("game:")) return null;
  return m.type;
}

function lastAt(item: StageItem): number {
  return item.moments[item.moments.length - 1].at;
}

export function enqueue(stage: StageState, m: Moment, now: number, timing: StageTiming): StageState {
  const kind = stageKind(m);
  if (kind === null) return stage;
  if (kind === "shot") {
    const cur = stage.current;
    if (
      cur?.kind === "shot" &&
      cur.shownAt !== null &&
      now - cur.shownAt < timing.comboMaxMs &&
      m.at - lastAt(cur) <= timing.comboWindowMs
    ) {
      const until = Math.min(cur.shownAt + timing.comboMaxMs, Math.max(cur.until ?? now, now + timing.comboWindowMs));
      return { ...stage, current: { ...cur, moments: [...cur.moments, m], until } };
    }
    const tail = stage.queue[stage.queue.length - 1];
    if (tail?.kind === "shot" && m.at - lastAt(tail) <= timing.comboWindowMs) {
      return { ...stage, queue: [...stage.queue.slice(0, -1), { ...tail, moments: [...tail.moments, m] }] };
    }
  }
  const item: StageItem = { key: m.id, kind, moments: [m], shownAt: null, until: null };
  return { ...stage, queue: [...stage.queue, item].slice(-MAX_QUEUE) };
}

export function advance(stage: StageState, now: number): StageState {
  const cur = stage.current;
  if (cur && cur.until !== null && now < cur.until) return stage;
  if (stage.queue.length === 0) return cur ? { current: null, queue: stage.queue } : stage;
  const [next, ...rest] = stage.queue;
  return { current: { ...next, shownAt: now, until: now + DURATION_MS[next.kind] }, queue: rest };
}

export function finish(stage: StageState, key: string, now: number): StageState {
  if (stage.current?.key !== key) return stage;
  return { ...stage, current: { ...stage.current, until: now } };
}
