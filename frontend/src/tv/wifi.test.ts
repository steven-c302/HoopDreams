import { describe, expect, it } from "vitest";
import { wifiQr } from "./wifi";

describe("wifiQr", () => {
  it("builds a WPA join string and escapes special characters", () => {
    expect(wifiQr("Hoop;House", 'pa:ss"1')).toBe('WIFI:T:WPA;S:Hoop\\;House;P:pa\\:ss\\"1;;');
  });

  it("uses nopass for open networks", () => {
    expect(wifiQr("Open Court", null)).toBe("WIFI:T:nopass;S:Open Court;;");
  });
});
