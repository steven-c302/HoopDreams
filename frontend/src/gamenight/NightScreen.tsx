import { useState, type CSSProperties } from "react";
import { Link } from "react-router";
import { useConnected, useNow, usePartyState } from "../party/store";
import type { PublicState } from "../party/contract.gen";
import { JoinPanel } from "../tv/JoinPanel";
import { Drawing } from "./Drawing";
import { GAMES, useGame, RULES, type GameState, type Mode } from "./state";
import "./night.css";

export function GameArt({ mode }: { mode: Mode }) {
  return (
    <div className={`gn-art gn-art--${mode}`} aria-hidden="true">
      {mode === "trivia" ? (
        <>
          <i className="gn-orbit" />
          <span className="gn-question">?</span>
          <span className="gn-star">✦</span>
          <span className="gn-mini">Aha!</span>
        </>
      ) : mode === "draw" ? (
        <>
          <div className="gn-paper">
            <svg viewBox="0 0 180 110">
              <path
                d="M30 82 L40 42 L72 12 L103 42 L116 83 Z M48 56 Q68 33 90 58 M57 70 Q73 85 91 65 M109 35 L135 18 L145 38"
                fill="none"
                stroke="currentColor"
                strokeWidth="5"
                strokeLinecap="round"
              />
              <circle cx="61" cy="53" r="3" />
              <circle cx="85" cy="53" r="3" />
            </svg>
          </div>
          <span className="gn-pencil">✎</span>
          <span className="gn-star">✦</span>
        </>
      ) : (
        <>
          <span className="gn-bubble">“</span>
          <span className="gn-smile">☺</span>
          <span className="gn-star">✳</span>
        </>
      )}
    </div>
  );
}
export default function NightScreen() {
  const party = usePartyState();
  const game = useGame();
  const connected = useConnected();
  const [detail, setDetail] = useState<Mode | null>(null);
  const [fullError, setFullError] = useState("");
  const active = game && game.phase !== "lobby";
  return (
    <div className="gn-screen">
      <header className="gn-header">
        <Link className="gn-logo" to="/tv">
          <span>
            h<span className="gn-logo-dot">.</span>
          </span>{" "}
          HOOPDREAMS <small>THE LIVING ROOM LEAGUE</small>
        </Link>
        <nav>
          <span className={`gn-live ${connected ? "" : "gn-offline"}`}>
            {connected ? "PARTY IS LIVE" : "RECONNECTING"}
          </span>
          <button
            className="gn-textbutton"
            onClick={() => {
              void (
                document.fullscreenElement
                  ? document.exitFullscreen()
                  : document.documentElement.requestFullscreen()
              ).catch(() =>
                setFullError("Use your browser’s full-screen menu."),
              );
            }}
          >
            ⛶ Full screen
          </button>
          <Link to="/host">Host controls ↗</Link>
        </nav>
      </header>
      {fullError && <p role="status">{fullError}</p>}
      {!party || !game ? (
        <main className="gn-loading">
          <span className="gn-loader" />
          <h1>Getting the room together.</h1>
          <p>
            {connected
              ? "Setting the stage…"
              : "Connecting to the party server. Keep this screen open."}
          </p>
        </main>
      ) : (
        <>
          <main className="gn-main">
            <section className="gn-stage">
              {active ? (
                <RoundStage game={game} party={party} />
              ) : (
                <>
                  <div className="gn-hero">
                    <div>
                      <p className="gn-eyebrow">GOOD COMPANY. BAD GUESSES.</p>
                      <h1>
                        Big screen.
                        <br />
                        Bigger <em>energy.</em>
                      </h1>
                      <p>
                        Pick your people. Grab your phone.
                        <br />
                        Make a night of it.
                      </p>
                    </div>
                    <div className="gn-stamp">
                      <span>4 TEAMS</span>
                      <b>
                        ONE
                        <br />
                        ROOM.
                      </b>
                      <span>EVERYONE’S IN</span>
                    </div>
                  </div>
                  <div className="gn-section-label">
                    <h2>Tonight’s lineup</h2>
                    <span>
                      Phones are the controllers. You bring the chaos.
                    </span>
                  </div>
                  <div className="gn-games">
                    {GAMES.map((g) => (
                      <button
                        key={g.id}
                        className={`gn-card gn-card--${g.id}`}
                        onClick={() => setDetail(detail === g.id ? null : g.id)}
                        aria-expanded={detail === g.id}
                      >
                        <GameArt mode={g.id} />
                        <div className="gn-card-copy">
                          <span className="gn-eyebrow">{g.kicker}</span>
                          <h3>
                            {g.name}
                            <span>↗</span>
                          </h3>
                          <p>{g.description}</p>
                          <footer>
                            <span>{g.duration}</span>
                            <span>TEAM PLAY</span>
                          </footer>
                        </div>
                      </button>
                    ))}
                  </div>
                  {detail && (
                    <div className="gn-details">
                      <strong>
                        {GAMES.find((g) => g.id === detail)?.name}
                      </strong>
                      <p>{RULES[detail]}</p>
                      <Link to="/host">Open host controls to start →</Link>
                    </div>
                  )}
                </>
              )}
            </section>
            <aside className="gn-sidebar">
              <div className="gn-join-box">
                <p className="gn-eyebrow">YOUR PHONE = YOUR CONTROLLER</p>
                <h2>You’re invited.</h2>
                <JoinPanel join={party.join} />
                <p>Same Wi-Fi. No app. No account.</p>
                <div className="gn-steps">
                  <span>
                    <b>01</b> Scan the code
                  </span>
                  <span>
                    <b>02</b> Pick a name & team
                  </span>
                  <span>
                    <b>03</b> Look up. Game on.
                  </span>
                </div>
              </div>
              <div className="gn-room-count">
                <span className="gn-live-dot" />
                <strong>
                  {party.players.filter((p) => p.connected).length}
                </strong>
                <span>
                  in the room
                  <br />
                  <small>{party.players.length} on the roster</small>
                </span>
                <span className="gn-people">✦</span>
              </div>
              <div className="gn-shot-total">
                <span>TONIGHT’S SHOT TALLY</span>
                <b>
                  {party.total}
                  <small>logged</small>
                </b>
                <Link to="/tracker">Open the shot board ↗</Link>
              </div>
            </aside>
          </main>
          <TeamStrip party={party} game={game} />
          <footer className="gn-footer">
            <span>
              EST. TONIGHT <i>✳</i> MEMORIES IN THE MAKING
            </span>
            <span>
              {active
                ? "Host sets the pace · Points and shot tallies are separate"
                : "The host starts each game from their phone."}
            </span>
            <Link to="/hat">Feeling spontaneous? Draw from the hat ↗</Link>
          </footer>
        </>
      )}
    </div>
  );
}
export function Clock({ game }: { game: GameState }) {
  const now = useNow(200);
  const seconds = Math.max(
    0,
    Math.ceil(
      (game.paused ? game.remaining : (game.deadline ?? now) - now) / 1000,
    ),
  );
  return (
    <span
      className={`gn-clock ${seconds <= 10 && !game.paused ? "gn-clock--urgent" : ""}`}
    >
      {game.paused ? "Ⅱ PAUSED" : `${seconds}s`}
    </span>
  );
}
function RoundStage({
  game: g,
  party,
}: {
  game: GameState;
  party: PublicState;
}) {
  const mode = GAMES.find((m) => m.id === g.mode)!;
  const artist = party.players.find((p) => p.id === g.artistId);
  const teamName = (id: string) =>
    party.teams.find((t) => t.id === id)?.name ?? "Team";
  const reveal = g.phase === "reveal";
  const sorted = [...party.teams].sort(
    (a, b) => (g.scores[b.id] ?? 0) - (g.scores[a.id] ?? 0),
  );
  const best = g.scores[sorted[0]?.id] ?? 0;
  const winners = sorted.filter((t) => (g.scores[t.id] ?? 0) === best);
  return (
    <div className={`gn-round gn-round--${g.mode}`}>
      <div className="gn-round-head">
        <span className="gn-eyebrow">
          {mode.name} <b> / </b> ROUND {g.round} OF {g.rounds}
        </span>
        {g.deadline || g.paused ? (
          <Clock game={g} />
        ) : (
          <span className="gn-pill">
            {g.phase === "finished" ? "THAT’S A WRAP" : "THE REVEAL"}
          </span>
        )}
      </div>
      {g.phase === "finished" ? (
        <div className="gn-finale">
          <span>✳</span>
          <p className="gn-eyebrow">
            {winners.length > 1 ? "SHARING THE CROWN" : "YOUR NIGHT LEADER"}
          </p>
          <h1>{winners.map((t) => t.name).join(" + ")}</h1>
          <p>
            {best.toLocaleString()} points · Excellent company, questionable
            decisions.
          </p>
          <p>Head to host controls for the next game.</p>
        </div>
      ) : (
        <>
          <p className="gn-eyebrow gn-category">
            {g.mode === "trivia"
              ? g.category
              : g.mode === "draw"
                ? `${artist?.name ?? "Your artist"} is drawing · ${teamName(g.artistTeam ?? "")}`
                : g.phase === "vote"
                  ? "VOTE FOR YOUR FAVORITE ON YOUR PHONE"
                  : "ONE ANSWER PER TEAM. MAKE IT COUNT."}
          </p>
          <h1 className="gn-prompt">
            {g.mode === "draw"
              ? reveal
                ? `It was… ${g.prompt}!`
                : "What on earth is that?"
              : g.prompt}
          </h1>
          {g.mode === "trivia" && (
            <div className="gn-options">
              {g.options.map((answer, i) => (
                <div
                  key={answer}
                  className={`gn-option ${reveal && answer === g.correct ? "gn-option--correct" : ""} ${reveal && answer !== g.correct ? "gn-option--muted" : ""}`}
                >
                  <b>{"ABCD"[i]}</b>
                  <span>{answer}</span>
                  {reveal && answer === g.correct && <strong>✓</strong>}
                </div>
              ))}
            </div>
          )}
          {g.mode === "draw" && <Drawing strokes={g.strokes} />}
          {g.mode === "riff" && g.phase === "answer" && (
            <div className="gn-riff-wait">
              <span>“</span>
              <p>The group chat has been training you for this.</p>
              <b>
                {g.writtenTeams.length} /{" "}
                {new Set(Object.values(g.roster)).size} teams have served
              </b>
            </div>
          )}
          {g.mode === "riff" && g.phase === "vote" && (
            <div className="gn-options">
              {g.entries.map((e, i) => (
                <div className="gn-option" key={e.id}>
                  <b>{i + 1}</b>
                  <span>{e.text}</span>
                </div>
              ))}
            </div>
          )}
          {reveal && (
            <div className="gn-results">
              {g.results.map((r) => (
                <div key={r.teamId}>
                  <span>{teamName(r.teamId)}</span>
                  <strong>+{r.points.toLocaleString()}</strong>
                  <small>{r.answer}</small>
                </div>
              ))}
            </div>
          )}
          <div className="gn-round-foot">
            <span>
              {reveal
                ? "Take a victory lap. The host starts the next round."
                : g.mode === "trivia"
                  ? `${g.submitted.length} / ${Object.keys(g.roster).length} answers locked · Majority answer counts for your team`
                  : g.mode === "draw"
                    ? "Type your guesses on your phone · The artist’s team sits this one out"
                    : g.phase === "vote"
                      ? `${g.voted.length} votes in · You can’t vote for your own team`
                      : "Talk it out. One teammate submits."}
            </span>
            {g.mode === "trivia" && (
              <small>
                {g.source.startsWith("The Trivia") ? (
                  <a
                    href="https://the-trivia-api.com/"
                    target="_blank"
                    rel="noreferrer"
                  >
                    {g.source}
                  </a>
                ) : (
                  g.source
                )}
              </small>
            )}
          </div>
        </>
      )}
    </div>
  );
}
function TeamStrip({ party, game }: { party: PublicState; game: GameState }) {
  return (
    <section className="gn-teams" aria-label="Team standings">
      {party.teams.map((team, i) => (
        <div
          className="gn-team"
          key={team.id}
          style={{ "--team-color": team.color } as CSSProperties}
        >
          <span className="gn-team-number">0{i + 1}</span>
          <div className="gn-team-info">
            <h3>{team.name}</h3>
            <p>
              {team.size} {team.size === 1 ? "player" : "players"}{" "}
              <span>·</span> {team.total} shots logged
            </p>
            <div className="gn-avatars">
              {party.players
                .filter((p) => p.teamId === team.id)
                .slice(0, 8)
                .map((p) => (
                  <span key={p.id} title={p.name}>
                    {p.avatar.kind === "emoji" ? p.avatar.value : p.name[0]}
                  </span>
                ))}
              {team.size === 0 && <small>Your crew goes here</small>}
            </div>
          </div>
          <div className="gn-team-score">
            <strong>{(game.scores[team.id] ?? 0).toLocaleString()}</strong>
            <small>PTS</small>
          </div>
        </div>
      ))}
    </section>
  );
}
