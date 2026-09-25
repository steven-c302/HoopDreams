/** Contents of a "join this Wi-Fi" QR code (the format phone cameras understand). */
export function wifiQr(ssid: string, password: string | null): string {
  const esc = (s: string) => s.replace(/([\\;,:"])/g, "\\$1");
  return password ? `WIFI:T:WPA;S:${esc(ssid)};P:${esc(password)};;` : `WIFI:T:nopass;S:${esc(ssid)};;`;
}
