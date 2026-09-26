import { useEffect, useRef, useState } from "react";
import type { PlayerView } from "../party/contract.gen";
import { call } from "../party/socket";
import { useConnected } from "../party/store";
import { Drawing } from "./Drawing";
import { Clock } from "./NightScreen";
import {
  GAMES,
  RULES,
  syncGame,
  useGame,
  type GameState,
  type Stroke,
} from "./state";
import "./night.css";
export function GameController({
  me,
  resumed,
}: {
  me: PlayerView;
  resumed: boolean;
}) {
  const game = useGame();
  if (!game)
    return <div className="gn-controller">Connecting to the games…</div>;
  return (
    <Controller
      key={`${game.roundId}-${game.phase}`}
      game={game}
      me={me}
      resumed={resumed}
    />
  );
}
function Controller({
  game: g,
  me,
  resumed,
}: {
  game: GameState;
  me: PlayerView;
  resumed: boolean;
}) {
  const connected = useConnected();
  const [secret, setSecret] = useState<string | null>(null);
  const [ownEntry, setOwnEntry] = useState<string | null>(null);
  const [value, setValue] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const drawingQueue = useRef(Promise.resolve());
  useEffect(() => {
    let live = true;
    if (resumed)
      void syncGame().then((ack) => {
        if (live && ack.ok) {
          setSecret(ack.secret);
          setOwnEntry(ack.ownEntry);
        }
      });
    return () => {
      live = false;
    };
  }, [resumed, g.roundId, connected]);
  const mode = GAMES.find((m) => m.id === g.mode);
  const locked = !connected || !resumed || g.paused || busy;
  async function send(action: string, extra: Record<string, unknown> = {}) {
    setBusy(true);
    setMessage("");
    const ack = await call<{ correct?: boolean }>("game:play", {
      roundId: g.roundId,
      action,
      value,
      ...extra,
    });
    setBusy(false);
    if (!ack.ok) {
      setMessage(ack.error);
      return;
    }
    if (action === "guess") {
      setMessage(
        ack.correct
          ? "You got it! Points for your team."
          : "Not quite. Keep guessing!",
      );
      setValue("");
    } else if (action === "write")
      setMessage("Served! Your team’s answer is in.");
    else if (action === "answer" || action === "vote")
      setMessage("Locked in. Look up at the TV.");
  }
  function stroke(s: Stroke) {
    drawingQueue.current = drawingQueue.current.then(async () => {
      const ack = await call("game:play", {
        roundId: g.roundId,
        action: "stroke",
        ...s,
      });
      if (!ack.ok) setMessage(ack.error);
    });
  }
  if (g.phase === "lobby")
    return (
      <section className="gn-controller gn-controller-lobby">
        <span className="gn-eyebrow">YOU’RE IN. LOOK UP.</span>
        <h2>Your couch is the arena.</h2>
        <p>
          The host will start a game. Your controller appears here
          automatically.
        </p>
        <div className="gn-phone-lineup">
          {GAMES.map((m) => (
            <span key={m.id}>
              {m.icon} {m.name}
            </span>
          ))}
        </div>
      </section>
    );
  if (g.phase === "finished")
    return (
      <section className="gn-controller">
        <span className="gn-eyebrow">THAT’S A WRAP</span>
        <h2>What a room.</h2>
        <p>
          Your team has {(g.scores[me.teamId] ?? 0).toLocaleString()} points.
          The host can start another game.
        </p>
      </section>
    );
  if (g.phase === "reveal")
    return (
      <section className="gn-controller">
        <span className="gn-eyebrow">THE REVEAL</span>
        <h2>
          {g.mode === "trivia"
            ? g.correct
            : g.mode === "draw"
              ? g.prompt
              : "The room has spoken."}
        </h2>
        <p>{g.results.find((r) => r.teamId === me.teamId)?.answer}</p>
        <strong className="gn-earned">
          +{g.results.find((r) => r.teamId === me.teamId)?.points ?? 0} points
        </strong>
        <p>Look up! The host starts the next round.</p>
      </section>
    );
  const participating = !!g.roster[me.id];
  const answered = g.submitted.includes(me.id);
  const voted = g.voted.includes(me.id);
  return (
    <section className="gn-controller">
      <header>
        <span className="gn-eyebrow">
          {mode?.name} · {g.round}/{g.rounds}
        </span>
        <Clock game={g} />
      </header>
      {!participating ? (
        <>
          <h2>You’re on deck.</h2>
          <p>You joined during a round. You’ll play in the next one.</p>
        </>
      ) : (
        <>
          {g.paused && (
            <p className="gn-notice">
              Intermission. Your host will resume the timer.
            </p>
          )}
          {g.mode === "trivia" && (
            <>
              <h2>{g.prompt}</h2>
              {answered ? (
                <div className="gn-locked">
                  ✓ Answer locked<p>Let your team cook. Eyes on the TV.</p>
                </div>
              ) : (
                <div className="gn-phone-options">
                  {g.options.map((answer, i) => (
                    <button
                      key={answer}
                      disabled={locked}
                      onClick={() => void send("answer", { value: answer })}
                    >
                      <b>{"ABCD"[i]}</b>
                      {answer}
                    </button>
                  ))}
                </div>
              )}
              <p className="gn-hint">
                Discuss together. Everyone taps. Team majority counts.
              </p>
            </>
          )}
          {g.mode === "draw" &&
            (g.artistId === me.id ? (
              <>
                <span className="gn-eyebrow">FOR YOUR EYES ONLY</span>
                <h2>{secret ?? "Getting your secret word…"}</h2>
                <p>Draw it. No letters, numbers, or talking!</p>
                <Drawing
                  strokes={g.strokes}
                  onStroke={stroke}
                  disabled={locked}
                />
                <button
                  className="gn-button gn-button--quiet"
                  disabled={locked}
                  onClick={() => void send("clear")}
                >
                  Clear canvas
                </button>
              </>
            ) : g.artistTeam === me.teamId ? (
              <>
                <h2>Your team is drawing.</h2>
                <p>
                  Cheer your artist on—keep the answer to yourself. Other teams
                  guess this round.
                </p>
              </>
            ) : (
              <>
                <h2>Any bright ideas?</h2>
                <Drawing strokes={g.strokes} />
                {answered ? (
                  <div className="gn-locked">✓ You got it!</div>
                ) : (
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      void send("guess");
                    }}
                  >
                    <label htmlFor="guess">What’s the drawing?</label>
                    <input
                      id="guess"
                      autoComplete="off"
                      maxLength={120}
                      value={value}
                      onChange={(e) => setValue(e.target.value)}
                      placeholder="Type your guess…"
                    />
                    <button
                      className="gn-button"
                      disabled={locked || !value.trim()}
                    >
                      Guess →
                    </button>
                  </form>
                )}
              </>
            ))}
          {g.mode === "riff" && (
            <>
              <h2>{g.prompt}</h2>
              {g.phase === "answer" ? (
                g.writtenTeams.includes(me.teamId) ? (
                  <div className="gn-locked">
                    ✓ Your team has served
                    <p>Save the explanation. Let the joke land.</p>
                  </div>
                ) : (
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      void send("write");
                    }}
                  >
                    <label htmlFor="riff">
                      Talk it out. One teammate submits.
                    </label>
                    <textarea
                      id="riff"
                      maxLength={120}
                      value={value}
                      onChange={(e) => setValue(e.target.value)}
                      placeholder="Your team’s finest work…"
                    />
                    <small>{value.length}/120</small>
                    <button
                      className="gn-button"
                      disabled={locked || !value.trim()}
                    >
                      Serve it →
                    </button>
                  </form>
                )
              ) : voted ? (
                <div className="gn-locked">✓ Vote locked</div>
              ) : (
                <div className="gn-phone-options">
                  {g.entries.map((e) => (
                    <button
                      key={e.id}
                      disabled={locked || e.id === ownEntry}
                      onClick={() => void send("vote", { value: e.id })}
                    >
                      {e.text}
                      {e.id === ownEntry && <small> · Your team</small>}
                    </button>
                  ))}
                  <p>You can’t vote for your own team. Team majority counts.</p>
                </div>
              )}
            </>
          )}
          <details className="gn-rules">
            <summary>How to play</summary>
            <p>{g.mode && RULES[g.mode]}</p>
          </details>
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
