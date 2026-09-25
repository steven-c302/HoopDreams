import type { Draw } from "../types";

interface HistoryStripProps {
  history: Draw[];
}

export function HistoryStrip({ history }: HistoryStripProps) {
  if (history.length === 0) return null;

  return (
    <div className="history">
      <h3 className="history__title">Recently drawn</h3>
      <div className="history__list">
        {history.map((draw) => (
          <div className={`history__chip history__chip--${draw.game.color}`} key={draw.id}>
            <span>{draw.game.emoji}</span>
            <span>{draw.game.name}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
