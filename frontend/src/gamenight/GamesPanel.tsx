import { useState } from "react";
import { call } from "../party/socket";
import { usePartyState } from "../party/store";
import { GAMES, RULES, useGame, type Mode } from "./state";
import { Clock, GameArt } from "./NightScreen";
import "./night.css";
export function GamesPanel() {
  const game = useGame();
  const party = usePartyState();
  const [mode, setMode] = useState<Mode>("trivia");
  const [source, setSource] = useState("house");
  const [rounds, setRounds] = useState(8);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [confirmEnd, setConfirmEnd] = useState(false);
  if (!game) return <p>Connecting to games…</p>;
  async function run(action: string) {
    setBusy(true);
    setMessage("");
    const ack = await call(
      "game:host",
      { action, mode, source, rounds, revision: game!.revision },
      action === "refresh" ? 15000 : 5000,
    );
    setBusy(false);
    setMessage(
      ack.ok
        ? action === "refresh"
          ? "Fresh questions loaded. Ready for tonight."
          : ""
        : ack.error,
    );
    setConfirmEnd(false);
  }
  const live = game.phase !== "lobby" && game.phase !== "finished";
  return (
    <section className="gn-host">
      <header>
        <p className="gn-eyebrow">YOU SET THE PACE</p>
        <h1>Run the room.</h1>
        <a href="/tv" target="_blank" rel="noreferrer">
          Open TV screen ↗
        </a>
      </header>
      {party && party.teams.length < 4 && (
        <div className="gn-notice">
          Planning four teams of four?{" "}
          <button
            className="gn-button gn-button--quiet"
            disabled={busy}
            onClick={() => void run("fourTeams")}
          >
            Set up four teams
          </button>
        </div>
      )}
      {live ? (
        <div className="gn-host-live">
          <h2>
            {GAMES.find((m) => m.id === game.mode)?.name} · Round {game.round}/
            {game.rounds}
          </h2>
          <p>
            {game.phase === "reveal"
              ? "Answers revealed. Give the room a moment."
              : game.phase === "vote"
                ? "Voting is open."
                : "The round is live."}
          </p>
          {(game.deadline || game.paused) && <Clock game={game} />}
          <div className="gn-host-actions">
            {game.phase === "reveal" ? (
              <button
                className="gn-button"
                disabled={busy}
                onClick={() => void run("next")}
              >
                {game.round === game.rounds
                  ? "Show final standings"
                  : "Next round →"}
              </button>
            ) : (
              <>
                <button
                  className="gn-button"
                  disabled={busy}
                  onClick={() => void run(game.paused ? "resume" : "pause")}
                >
                  {game.paused ? "Resume round" : "Pause for a breather"}
                </button>
                <button
                  className="gn-button gn-button--quiet"
                  disabled={busy}
                  onClick={() => void run("reveal")}
                >
                  {game.mode === "riff" && game.phase === "answer"
                    ? "Close writing & start vote"
                    : "Reveal answers"}
                </button>
              </>
            )}
            <button
              className="gn-textbutton"
              disabled={busy}
              onClick={() => setConfirmEnd(true)}
            >
              End game
            </button>
          </div>
          {confirmEnd && (
            <div className="gn-notice">
              Return to the lobby? Completed round points stay; this round ends
              without scoring.
              <button
                className="gn-button"
                onClick={() => void run("lobby")}
                disabled={busy}
              >
                Return to lobby
              </button>
              <button
                className="gn-textbutton"
                onClick={() => setConfirmEnd(false)}
              >
                Keep playing
              </button>
            </div>
          )}
        </div>
      ) : (
        <>
          <div className="gn-host-picker">
            {GAMES.map((m) => (
              <button
                className={mode === m.id ? "selected" : ""}
                key={m.id}
                onClick={() => {
                  setMode(m.id);
                  setRounds(m.id === "riff" ? 4 : 8);
                }}
              >
                <GameArt mode={m.id} />
                <b>{m.name}</b>
              </button>
            ))}
          </div>
          <p>{RULES[mode]}</p>
          <div className="gn-host-settings">
            <label>
              Rounds
              <select
                value={rounds}
                onChange={(e) => setRounds(Number(e.target.value))}
              >
                {[4, 8, 12, 16].map((n) => (
                  <option key={n} value={n}>
                    {n} rounds
                  </option>
                ))}
              </select>
            </label>
            {mode === "trivia" && (
              <label>
                Question pack
                <select
                  value={source}
                  onChange={(e) => setSource(e.target.value)}
                >
                  <option value="house">House pack · offline</option>
                  <option value="online" disabled={!game.bankCount}>
                    Fresh trivia · {game.bankCount} loaded
                  </option>
                </select>
              </label>
            )}
          </div>
          {mode === "trivia" && (
            <div className="gn-pack">
              <p>
                <strong>Fresh questions, zero signup.</strong>
                <br />
                Load a batch before playing. Easy & medium questions, with
                repeats filtered within this night.
              </p>
              <button
                className="gn-button gn-button--quiet"
                disabled={busy}
                onClick={() => void run("refresh")}
              >
                {busy ? "Working…" : "Load fresh questions ↻"}
              </button>
              <small>
                Questions by{" "}
                <a
                  href="https://the-trivia-api.com/"
                  target="_blank"
                  rel="noreferrer"
                >
                  The Trivia API
                </a>{" "}
                · CC BY-NC 4.0
              </small>
            </div>
          )}
          <button
            className="gn-button gn-start"
            disabled={busy}
            onClick={() => void run("start")}
          >
            {busy
              ? "Getting ready…"
              : `Let’s play ${GAMES.find((m) => m.id === mode)?.name} →`}
          </button>
          <p className="gn-hint">
            At least 2 teams need players. For your night: 4 teams, 4 people
            each. Game points carry across games; a new night resets them.
          </p>
        </>
      )}
      {message && (
        <p className="gn-notice" role="status">
          {message}
        </p>
      )}
    </section>
  );
}
