import { describe, expect, it } from "vitest";
import { LINES, fill, pick } from "./lines";

describe("announcer lines", () => {
  it("fills known placeholders and leaves unknown ones", () => {
    expect(fill("{name} hits {value}! {nope}", { name: "Jess", value: 10 })).toBe("Jess hits 10! {nope}");
  });

  it("picks with the given random source", () => {
    expect(pick(["a {name}", "b {name}"], { name: "Sam" }, () => 0.99)).toBe("b Sam");
    expect(pick(["a {name}", "b {name}"], { name: "Sam" }, () => 0)).toBe("a Sam");
  });

  it("has a line for every stage moment", () => {
    for (const key of ["shot", "heating", "fire", "group", "first", "playerMilestone", "partyMilestone", "century", "lead", "wavedOff", "replay"] as const) {
      expect(LINES[key].length).toBeGreaterThan(0);
    }
  });
});
