import { Fragment, type CSSProperties } from "react";
import { LedDigits } from "../arcade/LedDigits";
import type { PublicState } from "../party/contract.gen";
import { useNow } from "../party/store";
import { formatClock } from "../party/time";

export function Scoreboard({ state }: { state: PublicState }) {
  const now = useNow(1000);
  const middle = Math.ceil(state.teams.length / 2);
  return (
    <header className="scoreboard">
      <div className="scoreboard__logo display">
        HOOP
        <br />
        DREAMS
      </div>
      <div className="scoreboard__teams">
        {state.teams.map((t, i) => (
          <Fragment key={t.id}>
            {i === middle && <Clock now={now} startedAt={state.startedAt} total={state.total} />}
            <div className="scoreboard__team" style={{ "--team": t.color } as CSSProperties}>
              <span className="scoreboard__name display">{t.name}</span>
              <LedDigits value={t.total} digits={3} size="5.2vw" />
            </div>
          </Fragment>
        ))}
      </div>
    </header>
  );
}

function Clock({ now, startedAt, total }: { now: number; startedAt: number; total: number }) {
  return (
    <div className="scoreboard__clock">
      <LedDigits value={formatClock(now - startedAt)} size="2.6vw" color="var(--bad)" />
      <span className="pixel">TOTAL {total}</span>
    </div>
  );
}
