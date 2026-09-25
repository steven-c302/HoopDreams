import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { Avatar } from "../arcade/Avatar";
import { ArcadeSurface } from "../arcade/Surface";
import type { PlayerView, PublicState } from "../party/contract.gen";
import { uuid } from "../party/ids";
import type { OutboxResult } from "../party/outbox";
import { call } from "../party/socket";
import { useConnected, useNow, usePartyState } from "../party/store";
import { JoinFlow } from "./JoinFlow";
import { RosterGrid } from "./RosterGrid";
import { ShotCam } from "./ShotCam";
import { Toasts, type UndoToast } from "./Toasts";
import { useOutbox } from "./useOutbox";
import { usePlayer } from "./usePlayer";
import "./play.css";

export default function PlayPage() {
  const state = usePartyState();
  const connected = useConnected();
  const player = usePlayer(state);

  let body;
  if (!state) body = <p className="play__center display">Finding the party…</p>;
  else if (player.status === "joining") body = <JoinFlow state={state} onJoin={player.join} />;
  else if (player.me) body = <PlayScreen state={state} me={player.me} resumed={player.resumed} token={player.identity?.token ?? ""} />;
  else if (player.status === "resuming") body = <p className="play__center display">Checking in…</p>;
  else body = <Gone onRejoin={player.forget} />;

  return (
    <ArcadeSurface>
      {!connected && <div className="banner">Reconnecting…</div>}
      <div className="play">{body}</div>
    </ArcadeSurface>
  );
}

function Gone({ onRejoin }: { onRejoin: () => void }) {
  return (
    <div className="play__center">
      <p className="display">You're off the roster</p>
      <p>The host removed or merged your player.</p>
      <button className="btn" onClick={onRejoin}>
        JOIN AGAIN
      </button>
    </div>
  );
}

function PlayScreen({ state, me, resumed, token }: { state: PublicState; me: PlayerView; resumed: boolean; token: string }) {
  const now = useNow(1000);
  const [selected, setSelected] = useState<string[]>([]);
  const [locked, setLocked] = useState(false);
  const [undo, setUndo] = useState<UndoToast | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dismissed, setDismissed] = useState<string[]>([]);
  const names = useMemo(() => new Map(state.players.map((p) => [p.id, p.name])), [state.players]);
  const namesRef = useRef(names);
  useEffect(() => {
    namesRef.current = names;
  }, [names]);
  const undoMs = state.settings.undoWindowSec * 1000;

  const onResult = useCallback(
    (result: OutboxResult) => {
      if (result.item.event !== "shot:log") return;
      if (!result.ack.ok) {
        setError(result.ack.error);
        return;
      }
      const ids = result.item.payload.drinkerIds as string[];
      const label = ids.map((id) => namesRef.current.get(id) ?? "someone").join(", ");
      setUndo({ requestId: result.item.requestId, label, until: Date.now() + undoMs - 1000 });
    },
    [undoMs],
  );

  const { outbox, pending } = useOutbox(me.id, onResult);
  useEffect(() => {
    if (resumed) void outbox.resume();
    else outbox.pause();
  }, [outbox, resumed]);

  useEffect(() => {
    if (!error) return;
    const id = setTimeout(() => setError(null), 4000);
    return () => clearTimeout(id);
  }, [error]);

  const inFeed = new Set(state.feed.map((f) => f.requestId));
  const unsent = pending.filter((i) => !inFeed.has(i.requestId));
  const mine = unsent.filter((i) => i.event === "shot:log" && (i.payload.drinkerIds as string[]).includes(me.id)).length;

  async function log(drinkerIds: string[]) {
    if (locked) return;
    setLocked(true);
    setTimeout(() => setLocked(false), 2000);
    if ("vibrate" in navigator) navigator.vibrate(40);
    setSelected([]);
    await outbox.push("shot:log", { requestId: uuid(), drinkerIds });
  }

  async function doUndo() {
    if (!undo) return;
    const ack = await call("shot:undo", { requestId: undo.requestId });
    setUndo(null);
    if (!ack.ok) setError(ack.error);
  }

  async function doNotMe(shotId: string) {
    setDismissed((d) => [...d, shotId]);
    const ack = await call("shot:reject", { shotId });
    if (!ack.ok) setError(ack.error);
  }

  const notMeItem = state.feed.find(
    (f) =>
      f.drinkerId === me.id &&
      f.loggedById !== null &&
      f.loggedById !== me.id &&
      now - f.at < state.settings.notMeWindowSec * 1000 &&
      !dismissed.includes(f.shotId),
  );
  const recentShotId =
    state.feed.find((f) => (f.drinkerId === me.id || f.loggedById === me.id) && now - f.at < 120_000)?.shotId ?? null;
  const team = state.teams.find((t) => t.id === me.teamId);

  return (
    <>
      <header className="me" style={{ "--team": team?.color } as CSSProperties}>
        <Avatar avatar={me.avatar} size="56px" color={team?.color} fire={me.streak === "fire"} />
        <div className="me__who">
          <span className="display">{me.name}</span>
          <span className="me__team">{team?.name}</span>
        </div>
        <div className="me__score">
          <span className="display me__count">{me.count + mine}</span>
          <span className="pixel">#{me.rank}</span>
        </div>
      </header>
      {me.streak && <p className={`streak streak--${me.streak} pixel`}>{me.streak === "fire" ? "🔥 ON FIRE 🔥" : "HEATING UP"}</p>}

      <button className="shot-button display" disabled={locked} onClick={() => void log([me.id])}>
        <span className="shot-button__glass" aria-hidden>
          🥃
        </span>
        I TOOK ONE
      </button>
      {unsent.length > 0 && <p className="sending">sending {unsent.length}…</p>}

      <div className="play__extras">
        <ShotCam token={token} recentShotId={recentShotId} />
      </div>

      <section className="squad">
        <h2 className="pixel squad__title">LOG FOR THE SQUAD</h2>
        <RosterGrid
          players={state.players.filter((p) => p.id !== me.id)}
          teams={state.teams}
          selected={selected}
          onToggle={(id) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]))}
        />
      </section>

      <Standings state={state} me={me} />

      {selected.length > 0 && (
        <div className="log-bar">
          <button className="btn btn--ghost" onClick={() => setSelected([])}>
            CLEAR
          </button>
          <button className="btn log-bar__go" disabled={locked} onClick={() => void log(selected)}>
            LOG +1 ({selected.length})
          </button>
        </div>
      )}

      <Toasts
        undo={undo && Date.now() < undo.until ? undo : null}
        onUndo={() => void doUndo()}
        notMe={notMeItem ? { shotId: notMeItem.shotId, by: names.get(notMeItem.loggedById ?? "") ?? "Someone" } : null}
        onNotMe={(shotId) => void doNotMe(shotId)}
        onFair={(shotId) => setDismissed((d) => [...d, shotId])}
        error={error}
      />
    </>
  );
}

function Standings({ state, me }: { state: PublicState; me: PlayerView }) {
  const top = state.players.slice(0, 5);
  return (
    <section className="standings">
      <h2 className="pixel squad__title">STANDINGS</h2>
      <ol>
        {top.map((p) => (
          <li key={p.id} className={p.id === me.id ? "standings__me" : undefined}>
            <span className="standings__rank">{p.rank}</span>
            <span className="standings__name">
              {p.name}
              {p.streak === "fire" ? " 🔥" : ""}
            </span>
            <span className="display">{p.count}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}
