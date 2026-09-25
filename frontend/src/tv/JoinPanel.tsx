import QRCode from "qrcode";
import { useEffect, useState } from "react";
import type { JoinInfo } from "../party/contract.gen";
import { wifiQr } from "./wifi";

interface Code {
  label: string;
  sub: string;
  text: string;
}

export function JoinPanel({ join }: { join: JoinInfo }) {
  const codes: Code[] = [];
  if (join.wifiSsid) codes.push({ label: "1 · JOIN WI-FI", sub: join.wifiSsid, text: wifiQr(join.wifiSsid, join.wifiPassword) });
  codes.push({ label: join.wifiSsid ? "2 · SCAN TO PLAY" : "SCAN TO PLAY", sub: join.lanUrl.replace(/^https?:\/\//, ""), text: join.lanUrl });
  if (join.tunnelUrl) codes.push({ label: "NOT ON WI-FI?", sub: "cell data works here", text: `${join.tunnelUrl}/play` });
  return (
    <div className="join">
      {codes.map((c) => (
        <QrCard key={c.label} {...c} />
      ))}
    </div>
  );
}

function QrCard({ label, sub, text }: Code) {
  const [src, setSrc] = useState("");
  useEffect(() => {
    let live = true;
    void QRCode.toDataURL(text, { margin: 1, width: 320, color: { dark: "#06070dff", light: "#f7f3eaff" } }).then((url) => {
      if (live) setSrc(url);
    });
    return () => {
      live = false;
    };
  }, [text]);
  return (
    <figure className="qr">
      {src && <img src={src} alt={`QR code for ${text}`} />}
      <figcaption>
        <span className="pixel">{label}</span>
        <span className="qr__sub">{sub}</span>
      </figcaption>
    </figure>
  );
}
