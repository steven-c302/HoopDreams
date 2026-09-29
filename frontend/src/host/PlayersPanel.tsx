import { useState } from "react";
import { Avatar } from "../arcade/Avatar";
import type { PlayerView, PublicState } from "../party/contract.gen";
import type { Run } from "./actions";

export function PlayersPanel({ state, run }: { state: PublicState; run: Run }) {
  if (state.players.length === 0) return <section className="panel">Nobody has joined yet.</section>;
  return (
    <section className="panel">
      <h2 className="pixel">PLAYERS ({state.players.length})</h2>
      <ul className="rows">
        {state.players.map((p) => (
          <PlayerRow key={`${p.id}-${p.name}`} player={p} state={state} run={run} />
        ))}
      </ul>
    </section>
  );
}

function PlayerRow({ player, state, run }: { player: PlayerView; state: PublicState; run: Run }) {
  const [name, setName] = useState(player.name);
  const [mergeInto, setMergeInto] = useState("");
  const others = state.players.filter((p) => p.id !== player.id);
  const color = state.teams.find((t) => t.id === player.teamId)?.color;
  return (
    <li className="row">
      <Avatar avatar={player.avatar} size="40px" color={color} />
      <input
        className="input input--small"
        aria-label={`Name for ${player.name}`}
        value={name}
        maxLength={20}
        onChange={(e) => setName(e.target.value)}
        onBlur={() => {
          if (name.trim() && name.trim() !== player.name) void run("player.edit", { playerId: player.id, name: name.trim() });
        }}
      />
      <select
        className="input input--small"
        aria-label={`Team for ${player.name}`}
        value={player.teamId}
        onChange={(e) => void run("player.edit", { playerId: player.id, teamId: e.target.value })}
      >
        {state.teams.map((t) => (
          <option key={t.id} value={t.id}>
            {t.name}
          </option>
        ))}
      </select>
      <span className="row__count display">{player.count}</span>
      <select className="input input--small" aria-label={`Merge ${player.name} into`} value={mergeInto} onChange={(e) => setMergeInto(e.target.value)}>
        <option value="">merge into…</option>
        {others.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name}
          </option>
        ))}
      </select>
      <button
        className="btn btn--ghost btn--small"
        disabled={!mergeInto}
        onClick={() => {
          const into = others.find((p) => p.id === mergeInto)?.name;
          if (confirm(`Merge ${player.name} into ${into}? Their shots move over.`)) void run("player.merge", { fromId: player.id, intoId: mergeInto });
        }}
      >
        MERGE
      </button>
      <button
        className="btn btn--danger btn--small"
        onClick={() => {
          if (confirm(`Remove ${player.name}? Their shots stop counting.`)) void run("player.remove", { playerId: player.id });
        }}
      >
        REMOVE
      </button>
    </li>
  );
}
