import { useState } from "react";
import type { PublicState, TeamView } from "../party/contract.gen";
import type { Run } from "./actions";

export function TeamsPanel({ state, run }: { state: PublicState; run: Run }) {
  return (
    <section className="panel">
      <h2 className="pixel">TEAMS</h2>
      <ul className="rows">
        {state.teams.map((t) => (
          <TeamRow key={`${t.id}-${t.name}-${t.color}`} team={t} canRemove={state.teams.length > 2 && t.size === 0} run={run} />
        ))}
      </ul>
      {state.teams.length < 4 && <NewTeam run={run} />}
    </section>
  );
}

function TeamRow({ team, canRemove, run }: { team: TeamView; canRemove: boolean; run: Run }) {
  const [name, setName] = useState(team.name);
  const [color, setColor] = useState(team.color.toLowerCase());
  const dirty = name !== team.name || color !== team.color.toLowerCase();
  return (
    <li className="row">
      <input type="color" aria-label={`${team.name} colour`} value={color} onChange={(e) => setColor(e.target.value)} />
      <input className="input input--small" aria-label={`${team.name} name`} value={name} maxLength={16} onChange={(e) => setName(e.target.value)} />
      <span className="row__meta">
        {team.size} players · {team.total} shots
      </span>
      <button className="btn btn--small" disabled={!dirty || !name.trim()} onClick={() => void run("team.upsert", { id: team.id, name: name.trim(), color })}>
        SAVE
      </button>
      {canRemove && (
        <button className="btn btn--danger btn--small" onClick={() => void run("team.remove", { teamId: team.id })}>
          REMOVE
        </button>
      )}
    </li>
  );
}

function NewTeam({ run }: { run: Run }) {
  const [name, setName] = useState("");
  const [color, setColor] = useState("#35e07f");
  return (
    <form
      className="row"
      onSubmit={async (e) => {
        e.preventDefault();
        if (await run("team.upsert", { name: name.trim(), color })) setName("");
      }}
    >
      <input type="color" aria-label="New team colour" value={color} onChange={(e) => setColor(e.target.value)} />
      <input className="input input--small" placeholder="New team name" value={name} maxLength={16} onChange={(e) => setName(e.target.value)} />
      <button className="btn btn--small" disabled={!name.trim()}>
        ADD TEAM
      </button>
    </form>
  );
}
