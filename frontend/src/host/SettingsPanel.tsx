import { useState } from "react";
import type { PublicState } from "../party/contract.gen";
import type { Run } from "./actions";
import { NumberField } from "./NumberField";

type Settings = PublicState["settings"];

export function SettingsPanel({ state, run }: { state: PublicState; run: Run }) {
  const [draft, setDraft] = useState<Settings>(state.settings);
  const [ssid, setSsid] = useState(state.join.wifiSsid ?? "");
  const [password, setPassword] = useState(state.join.wifiPassword ?? "");
  const [nightName, setNightName] = useState("");
  const set = <K extends keyof Settings>(key: K, value: Settings[K]) => setDraft((d) => ({ ...d, [key]: value }));

  return (
    <section className="panel">
      <h2 className="pixel">SOUND</h2>
      <label className="toggle">
        <input type="checkbox" checked={draft.sound} onChange={(e) => set("sound", e.target.checked)} /> Sound effects on the TV
      </label>
      <label className="toggle">
        <input type="checkbox" checked={draft.voice} onChange={(e) => set("voice", e.target.checked)} /> Announcer voice
      </label>

      <h2 className="pixel">HYPE RULES</h2>
      <div className="fields">
        <NumberField label="Heating up: shots" value={draft.heatingUp.count} onChange={(n) => set("heatingUp", { ...draft.heatingUp, count: n })} />
        <NumberField label="…within minutes" value={draft.heatingUp.windowMin} onChange={(n) => set("heatingUp", { ...draft.heatingUp, windowMin: n })} />
        <NumberField label="On fire: shots" value={draft.onFire.count} onChange={(n) => set("onFire", { ...draft.onFire, count: n })} />
        <NumberField label="…within minutes" value={draft.onFire.windowMin} onChange={(n) => set("onFire", { ...draft.onFire, windowMin: n })} />
        <NumberField label="Fire goes out after (min)" value={draft.onFire.coolMin} onChange={(n) => set("onFire", { ...draft.onFire, coolMin: n })} />
        <NumberField label="Player milestone every" value={draft.playerMilestoneEvery} onChange={(n) => set("playerMilestoneEvery", n)} />
        <NumberField label="Undo window (s)" value={draft.undoWindowSec} onChange={(n) => set("undoWindowSec", n)} />
        <NumberField label="NOT ME window (s)" value={draft.notMeWindowSec} onChange={(n) => set("notMeWindowSec", n)} />
        <NumberField label="Max logs per player per minute" value={draft.shotLogPerMin} onChange={(n) => set("shotLogPerMin", n)} />
      </div>
      <button className="btn" onClick={() => void run("settings.update", { settings: draft })}>
        SAVE SETTINGS
      </button>

      <h2 className="pixel">WI-FI ON THE TV</h2>
      <p className="panel__hint">Shown as a QR code guests can scan to join the network.</p>
      <div className="fields">
        <label className="field">
          <span>Network name</span>
          <input className="input input--small" value={ssid} onChange={(e) => setSsid(e.target.value)} />
        </label>
        <label className="field">
          <span>Password</span>
          <input className="input input--small" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
      </div>
      <button className="btn" onClick={() => void run("wifi.set", { ssid, password })}>
        SAVE WI-FI
      </button>

      <h2 className="pixel">LINKS</h2>
      <p className="panel__hint">
        Phones: {state.join.lanUrl}
        {state.join.tunnelUrl ? ` · Tunnel: ${state.join.tunnelUrl}/play` : " · Tunnel off (start party.py with --tunnel)"}
      </p>

      <h2 className="pixel">NEW NIGHT</h2>
      <p className="panel__hint">Archives tonight's players and shots. Everyone re-joins. Teams and settings carry over.</p>
      <div className="row">
        <input className="input input--small" placeholder="Name (optional)" value={nightName} onChange={(e) => setNightName(e.target.value)} />
        <button
          className="btn btn--danger"
          onClick={() => {
            if (confirm("Start a new night? Everyone will need to re-join.")) void run("night.new", nightName.trim() ? { name: nightName.trim() } : {});
          }}
        >
          START NEW NIGHT
        </button>
      </div>
    </section>
  );
}
