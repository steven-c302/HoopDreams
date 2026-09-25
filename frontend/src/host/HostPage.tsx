import { useEffect, useState, type FormEvent } from "react";
import { ArcadeSurface } from "../arcade/Surface";
import { call } from "../party/socket";
import { useConnected, useHostState, usePartyState } from "../party/store";
import { hostAction, savePin, savedPin } from "./actions";
import { PlayersPanel } from "./PlayersPanel";
import { SettingsPanel } from "./SettingsPanel";
import { ShotsPanel } from "./ShotsPanel";
import { TeamsPanel } from "./TeamsPanel";
import "./host.css";

type Tab = "shots" | "players" | "teams" | "settings";
const TABS: [Tab, string][] = [
  ["shots", "SHOTS"],
  ["players", "PLAYERS"],
  ["teams", "TEAMS"],
  ["settings", "SETTINGS"],
];

export default function HostPage() {
  const state = usePartyState();
  const hostState = useHostState();
  const connected = useConnected();
  const [authed, setAuthed] = useState(false);
  const [tab, setTab] = useState<Tab>("shots");
  const [message, setMessage] = useState<string | null>(null);

  // Re-authenticate on every (re)connect with the PIN remembered for this tab.
  useEffect(() => {
    if (!connected) {
      setAuthed(false);
      return;
    }
    const pin = savedPin();
    if (!pin) return;
    void call("host:auth", { pin }).then((ack) => {
      if (ack.ok) setAuthed(true);
      else savePin(null);
    });
  }, [connected]);

  async function run(action: string, payload?: Record<string, unknown>): Promise<boolean> {
    const ack = await hostAction(action, payload);
    setMessage(ack.ok ? null : ack.error);
    return ack.ok;
  }

  let body;
  if (!state) body = <p className="host__center display">Connecting…</p>;
  else if (!authed) body = <PinGate onAuthed={() => setAuthed(true)} />;
  else
    body = (
      <div className="host">
        <header className="host__head">
          <h1 className="display">HOST · {state.nightName}</h1>
          <nav className="host__tabs">
            {TABS.map(([id, label]) => (
              <button key={id} className={`host__tab${tab === id ? " host__tab--on" : ""}`} onClick={() => setTab(id)}>
                {label}
              </button>
            ))}
          </nav>
        </header>
        {message && (
          <p className="host__error" role="alert">
            {message}
          </p>
        )}
        {tab === "shots" && <ShotsPanel state={state} hostState={hostState} run={run} />}
        {tab === "players" && <PlayersPanel state={state} run={run} />}
        {tab === "teams" && <TeamsPanel state={state} run={run} />}
        {tab === "settings" && <SettingsPanel state={state} run={run} />}
      </div>
    );

  return (
    <ArcadeSurface>
      {!connected && <div className="banner">Reconnecting…</div>}
      {body}
    </ArcadeSurface>
  );
}

function PinGate({ onAuthed }: { onAuthed: () => void }) {
  const [pin, setPin] = useState("");
  const [error, setError] = useState<string | null>(null);
  async function submit(e: FormEvent) {
    e.preventDefault();
    const ack = await call("host:auth", { pin });
    if (ack.ok) {
      savePin(pin);
      onAuthed();
    } else setError(ack.error);
  }
  return (
    <form className="host__center host__pin" onSubmit={(e) => void submit(e)}>
      <h1 className="display">HOST PANEL</h1>
      <label className="pixel" htmlFor="host-pin">
        PIN FROM THE MAC TERMINAL
      </label>
      <input id="host-pin" className="input" inputMode="numeric" autoComplete="off" placeholder="PIN" value={pin} onChange={(e) => setPin(e.target.value)} />
      <button className="btn" disabled={!pin}>
        ENTER
      </button>
      {error && (
        <p className="host__error" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}
