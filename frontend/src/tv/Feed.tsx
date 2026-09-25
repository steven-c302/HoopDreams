import type { PublicState } from "../party/contract.gen";
import { useNow } from "../party/store";
import { ago } from "../party/time";

export function Feed({ state }: { state: PublicState }) {
  const now = useNow(5000);
  const names = new Map(state.players.map((p) => [p.id, p.name]));
  const name = (id: string) => names.get(id) ?? "?";
  return (
    <section className="feed">
      <h2 className="pixel feed__title">PLAY-BY-PLAY</h2>
      <ul>
        {state.feed.slice(0, 7).map((f) => (
          <li key={f.shotId} className="feed__item">
            <span className="feed__who">
              {f.source.startsWith("game:") ? "🎯 " : ""}
              {f.loggedById && f.loggedById !== f.drinkerId ? `${name(f.loggedById)} → ${name(f.drinkerId)}` : name(f.drinkerId)}
            </span>
            <span className="feed__plus display">+1</span>
            <span className="feed__ago">{ago(now - f.at)}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
