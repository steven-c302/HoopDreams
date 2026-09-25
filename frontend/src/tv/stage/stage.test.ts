import { describe, expect, it } from "vitest";
import type { Moment } from "../../party/store";
import { advance, emptyStage, enqueue, finish, type StageState } from "./stage";

const timing = { comboWindowMs: 4000, comboMaxMs: 8000 };

function shot(id: string, at: number, source = "manual"): Moment {
  return { type: "shot", id, at, requestId: id, source, loggedById: null, drinkers: [{ playerId: `p-${id}`, count: 1, streak: null }] };
}

function milestone(id: string, at: number): Moment {
  return { type: "milestone", id, at, scope: "party", playerId: null, value: 25 };
}

function play(moments: [Moment, number][]): StageState {
  return moments.reduce((s, [m, now]) => advance(enqueue(s, m, now, timing), now), emptyStage);
}

describe("stage", () => {
  it("shows the first moment right away with its duration", () => {
    const s = play([[shot("a", 1000), 1000]]);
    expect(s.current?.key).toBe("a");
    expect(s.current?.until).toBe(1000 + 3200);
  });

  it("merges shots arriving during a shot takeover into a combo", () => {
    const s = play([
      [shot("a", 1000), 1000],
      [shot("b", 2000), 2000],
      [shot("c", 3500), 3500],
    ]);
    expect(s.current?.moments.map((m) => m.id)).toEqual(["a", "b", "c"]);
    expect(s.current?.until).toBe(3500 + 4000);
    expect(s.queue).toEqual([]);
  });

  it("caps a combo at comboMaxMs after it appeared", () => {
    let s = play([[shot("a", 0), 0]]);
    for (const t of [3000, 6000]) s = advance(enqueue(s, shot(`s${t}`, t), t, timing), t);
    expect(s.current?.until).toBe(8000);
    s = advance(enqueue(s, shot("late", 8500), 8500, timing), 8500);
    expect(s.current?.key).toBe("late");
  });

  it("queues other moments first-in first-out", () => {
    let s = play([
      [milestone("m1", 0), 0],
      [milestone("m2", 100), 100],
    ]);
    expect(s.current?.key).toBe("m1");
    s = advance(s, 3499);
    expect(s.current?.key).toBe("m1");
    s = advance(s, 3500);
    expect(s.current?.key).toBe("m2");
    s = advance(s, 7000);
    expect(s.current).toBeNull();
  });

  it("merges shots that queue up behind another moment", () => {
    const s = play([
      [milestone("m", 0), 0],
      [shot("a", 100), 100],
      [shot("b", 900), 900],
    ]);
    expect(s.queue).toHaveLength(1);
    expect(s.queue[0].moments.map((m) => m.id)).toEqual(["a", "b"]);
  });

  it("leaves game shots and game moments to the game overlay", () => {
    const game: Moment = { type: "game", id: "g", at: 0, gameId: "challenges", kind: "violation", data: {} };
    expect(play([[shot("a", 0, "game:challenges"), 0], [game, 0]])).toEqual(emptyStage);
  });

  it("finish ends the current item early", () => {
    let s = play([[milestone("m1", 0), 0], [milestone("m2", 0), 0]]);
    s = advance(finish(s, "m1", 500), 500);
    expect(s.current?.key).toBe("m2");
    expect(finish(s, "nope", 600)).toBe(s);
  });
});
