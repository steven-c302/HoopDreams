import { useState } from "react";
import type { HostState, PublicState } from "../party/contract.gen";
import type { Run } from "./actions";

export function ShotsPanel({ state, hostState, run }: { state: PublicState; hostState: HostState | null; run: Run }) {
  const [adding, setAdding] = useState<string[]>([]);
  const names = new Map(state.players.map((p) => [p.id, p.name]));
  const name = (id: string) => names.get(id) ?? "(removed)";
  const toggle = (id: string) => setAdding((a) => (a.includes(id) ? a.filter((x) => x !== id) : [...a, id]));
  return (
    <section className="panel">
      <h2 className="pixel">ADD A SHOT</h2>
      <div className="chips">
        {state.players.map((p) => (
          <button key={p.id} className={`chip${adding.includes(p.id) ? " chip--on" : ""}`} onClick={() => toggle(p.id)}>
            {p.avatar.kind === "emoji" ? p.avatar.value : "📷"} {p.name}
          </button>
        ))}
      </div>
      <button
        className="btn"
        disabled={adding.length === 0}
        onClick={async () => {
          if (await run("shot.add", { drinkerIds: adding })) setAdding([]);
        }}
      >
        ADD +1 ({adding.length})
      </button>

      <h2 className="pixel">SHOT LOG</h2>
      <table className="table">
        <thead>
          <tr>
            <th>When</th>
            <th>Drinker</th>
            <th>Logged by</th>
            <th>Source</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {(hostState?.shots ?? []).map((s) => (
            <tr key={s.id} className={s.voidedAt ? "table__void" : undefined}>
              <td>{new Date(s.at).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}</td>
              <td>{name(s.drinkerId)}</td>
              <td>{s.loggedById ? name(s.loggedById) : "host"}</td>
              <td>{s.source}</td>
              <td>
                {s.voidedAt ? (
                  <span className="table__tag">{s.voidReason}</span>
                ) : (
                  <button className="btn btn--danger btn--small" onClick={() => void run("shot.void", { shotId: s.id })}>
                    VOID
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
