import { describe, expect, it } from "vitest";
import { ago, formatClock } from "./time";

describe("formatClock", () => {
  it("shows m:ss under an hour and h:mm:ss after", () => {
    expect(formatClock(0)).toBe("0:00");
    expect(formatClock(83_000)).toBe("1:23");
    expect(formatClock(3_723_000)).toBe("1:02:03");
    expect(formatClock(-5)).toBe("0:00");
  });
});

describe("ago", () => {
  it("rounds down to now, minutes or hours", () => {
    expect(ago(59_000)).toBe("now");
    expect(ago(125_000)).toBe("2m");
    expect(ago(7_300_000)).toBe("2h");
  });
});
