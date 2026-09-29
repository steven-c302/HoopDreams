import { describe, expect, it } from "vitest";
import { latestDrinkers, shotHeadline } from "./headline";
import type { ShotMoment } from "./stage";

function shot(drinkers: [string, number, "heating" | "fire" | null][]): ShotMoment {
  return {
    type: "shot", id: drinkers.map((d) => d[0]).join(), at: 0, requestId: "r", source: "manual", loggedById: null,
    drinkers: drinkers.map(([playerId, count, streak]) => ({ playerId, count, streak })),
  };
}

const teams: Record<string, string> = { jess: "home", alex: "home", kim: "home", sam: "away" };
const teamOf = (id: string) => teams[id];

describe("shotHeadline", () => {
  it("uses the streak for a single shot", () => {
    expect(shotHeadline([shot([["jess", 1, null]])], teamOf)).toBe("BUCKET!");
    expect(shotHeadline([shot([["jess", 2, "heating"]])], teamOf)).toBe("HEATING UP!");
    expect(shotHeadline([shot([["jess", 3, "fire"]])], teamOf)).toBe("ON FIRE!");
  });

  it("calls two shots a double and bigger groups team shots or combos", () => {
    expect(shotHeadline([shot([["jess", 1, null]]), shot([["sam", 1, null]])], teamOf)).toBe("DOUBLE!");
    expect(shotHeadline([shot([["jess", 1, null], ["alex", 1, null], ["kim", 1, null]])], teamOf)).toBe("TEAM SHOT ×3");
    expect(shotHeadline([shot([["jess", 1, null], ["sam", 1, null], ["kim", 1, null]])], teamOf)).toBe("COMBO ×3");
  });
});

describe("latestDrinkers", () => {
  it("keeps one entry per player with their newest count", () => {
    const merged = latestDrinkers([shot([["jess", 1, null]]), shot([["jess", 2, "heating"], ["sam", 1, null]])]);
    expect(merged).toEqual([
      { playerId: "jess", count: 2, streak: "heating" },
      { playerId: "sam", count: 1, streak: null },
    ]);
  });
});
