import { describe, expect, it } from "vitest";
import { uuid } from "./ids";

describe("uuid", () => {
  it("makes distinct RFC 4122 v4 ids without crypto.randomUUID", () => {
    const ids = new Set(Array.from({ length: 200 }, uuid));
    expect(ids.size).toBe(200);
    for (const id of ids) expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });
});
