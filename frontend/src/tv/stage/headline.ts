import type { ShotMoment } from "./stage";

type Drinker = ShotMoment["drinkers"][number];

export function shotHeadline(shots: ShotMoment[], teamOf: (playerId: string) => string | undefined): string {
  const drinkers = shots.flatMap((s) => s.drinkers);
  if (drinkers.length <= 1) {
    const streak = drinkers[0]?.streak;
    return streak === "fire" ? "ON FIRE!" : streak === "heating" ? "HEATING UP!" : "BUCKET!";
  }
  if (drinkers.length === 2) return "DOUBLE!";
  const teams = new Set(drinkers.map((d) => teamOf(d.playerId)));
  return teams.size === 1 ? `TEAM SHOT ×${drinkers.length}` : `COMBO ×${drinkers.length}`;
}

/** One entry per player, with the count and streak from their newest shot. */
export function latestDrinkers(shots: ShotMoment[]): Drinker[] {
  const byPlayer = new Map<string, Drinker>();
  for (const s of shots) for (const d of s.drinkers) byPlayer.set(d.playerId, d);
  return [...byPlayer.values()];
}
