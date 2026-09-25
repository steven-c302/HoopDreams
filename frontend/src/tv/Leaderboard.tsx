import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState, type CSSProperties } from "react";
import { Avatar } from "../arcade/Avatar";
import { LedDigits } from "../arcade/LedDigits";
import type { PublicState } from "../party/contract.gen";

const PAGE = 10;

export function Leaderboard({ state }: { state: PublicState }) {
  const pages = Math.max(1, Math.ceil(state.players.length / PAGE));
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (pages <= 1) return;
    const id = setInterval(() => setTick((t) => t + 1), 8000);
    return () => clearInterval(id);
  }, [pages]);
  const page = tick % pages;
  const teams = new Map(state.teams.map((t) => [t.id, t]));

  if (state.players.length === 0) {
    return (
      <main className="board board--empty">
        <p className="display">Scan the code to get in the game →</p>
      </main>
    );
  }
  return (
    <main className="board">
      <h2 className="board__title pixel">
        LEADERBOARD{pages > 1 ? ` · ${page + 1}/${pages}` : ""}
      </h2>
      <ol className="board__list">
        <AnimatePresence initial={false}>
          {state.players.slice(page * PAGE, page * PAGE + PAGE).map((p) => {
            const team = teams.get(p.teamId);
            return (
              <motion.li
                key={p.id}
                layout
                initial={{ opacity: 0, x: -40 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0 }}
                className={`board__row${p.streak ? ` board__row--${p.streak}` : ""}${p.connected ? "" : " board__row--away"}`}
                style={{ "--team": team?.color } as CSSProperties}
              >
                <span className="board__rank display">{p.rank}</span>
                <Avatar avatar={p.avatar} size="3.4vw" color={team?.color} fire={p.streak === "fire"} />
                <span className="board__name display">
                  {p.name}
                  {p.streak === "fire" && " 🔥"}
                </span>
                {p.streak === "heating" && <span className="board__tag pixel">HEATING UP</span>}
                <LedDigits value={p.count} digits={2} size="2.8vw" />
              </motion.li>
            );
          })}
        </AnimatePresence>
      </ol>
    </main>
  );
}
