import type { CSSProperties } from "react";
import { Avatar } from "../arcade/Avatar";
import type { PlayerView, TeamView } from "../party/contract.gen";

interface RosterGridProps {
  players: PlayerView[];
  teams: TeamView[];
  selected: string[];
  onToggle: (playerId: string) => void;
}

export function RosterGrid({ players, teams, selected, onToggle }: RosterGridProps) {
  const color = new Map(teams.map((t) => [t.id, t.color]));
  const sorted = [...players].sort((a, b) => a.name.localeCompare(b.name));
  if (sorted.length === 0) return <p className="squad__empty">Nobody else is here yet. Get them to scan the TV.</p>;
  return (
    <ul className="roster">
      {sorted.map((p) => {
        const on = selected.includes(p.id);
        return (
          <li key={p.id}>
            <button
              className={`roster__item${on ? " roster__item--on" : ""}`}
              aria-pressed={on}
              onClick={() => onToggle(p.id)}
              style={{ "--team": color.get(p.teamId) } as CSSProperties}
            >
              <Avatar avatar={p.avatar} size="52px" color={color.get(p.teamId)} />
              <span className="roster__name">{p.name}</span>
              {on && <span className="roster__check">✓</span>}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
