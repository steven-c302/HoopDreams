import { useState } from "react";
import type { JoinOut, PublicState } from "../party/contract.gen";
import { uuid } from "../party/ids";
import type { Ack } from "../party/socket";
import type { JoinInput } from "./usePlayer";

const EMOJIS = ["🏀", "🔥", "😎", "🐐", "👑", "🚀", "🦄", "🎯", "🌶️", "🍋", "🐻", "🦈", "👽", "🤠", "💎", "🧃"];

interface JoinFlowProps {
  state: PublicState;
  onJoin: (input: JoinInput) => Promise<Ack<JoinOut>>;
}

export function JoinFlow({ state, onJoin }: JoinFlowProps) {
  const [step, setStep] = useState<"name" | "avatar" | "team">("name");
  const [name, setName] = useState("");
  const [emoji, setEmoji] = useState(EMOJIS[0]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Stable across retries: if an ack is lost, retrying returns the same player instead of a twin.
  const [requestId] = useState(uuid);

  async function pickTeam(teamId: string) {
    setBusy(true);
    setError(null);
    const ack = await onJoin({ requestId, name: name.trim(), emoji, teamId });
    setBusy(false);
    if (!ack.ok) {
      setError(ack.error);
      if (ack.error.includes("taken")) setStep("name");
    }
  }

  return (
    <div className="join-flow">
      <h1 className="display join-flow__title">
        HOOP
        <br />
        DREAMS
      </h1>
      {step === "name" && (
        <form
          className="join-flow__step"
          onSubmit={(e) => {
            e.preventDefault();
            if (name.trim()) setStep("avatar");
          }}
        >
          <label className="pixel" htmlFor="join-name">
            WHO'S CHECKING IN?
          </label>
          <input
            id="join-name"
            className="input"
            placeholder="Your name"
            autoComplete="nickname"
            maxLength={20}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <button className="btn" disabled={!name.trim()}>
            NEXT
          </button>
        </form>
      )}
      {step === "avatar" && (
        <div className="join-flow__step">
          <p className="pixel">PICK YOUR JERSEY</p>
          <div className="emoji-grid">
            {EMOJIS.map((e) => (
              <button key={e} className={`emoji-grid__item${e === emoji ? " emoji-grid__item--on" : ""}`} onClick={() => setEmoji(e)}>
                {e}
              </button>
            ))}
          </div>
          <div className="join-flow__extras" />
          <button className="btn" onClick={() => setStep("team")}>
            NEXT
          </button>
          <button className="btn btn--ghost" onClick={() => setStep("name")}>
            BACK
          </button>
        </div>
      )}
      {step === "team" && (
        <div className="join-flow__step">
          <p className="pixel">PICK YOUR TEAM</p>
          {state.teams.map((t) => (
            <button key={t.id} className="team-pick display" style={{ background: t.color }} disabled={busy} onClick={() => void pickTeam(t.id)}>
              {t.name}
              <span className="team-pick__size">
                {t.size} {t.size === 1 ? "player" : "players"}
              </span>
            </button>
          ))}
          <button className="btn btn--ghost" onClick={() => setStep("avatar")}>
            BACK
          </button>
        </div>
      )}
      {error && (
        <p className="join-flow__error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
