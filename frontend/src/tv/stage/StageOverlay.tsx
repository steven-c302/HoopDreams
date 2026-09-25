import { AnimatePresence, motion } from "motion/react";
import { useCallback, useEffect, useRef, type CSSProperties } from "react";
import { Avatar } from "../../arcade/Avatar";
import { LedDigits } from "../../arcade/LedDigits";
import { sfx } from "../../arcade/sound";
import type { PublicState } from "../../party/contract.gen";
import { cue } from "./cue";
import { latestDrinkers, shotHeadline } from "./headline";
import { isShot, type StageItem } from "./stage";
import { useStage } from "./useStage";

export function Stage({ state }: { state: PublicState }) {
  const stateRef = useRef(state);
  useEffect(() => {
    stateRef.current = state;
  });
  const onShow = useCallback((item: StageItem) => cue(item, stateRef.current), []);
  const onMerge = useCallback(() => sfx.swish(), []);
  const { current, done } = useStage(
    { comboWindowMs: state.settings.comboWindowMs, comboMaxMs: state.settings.comboMaxMs },
    { onShow, onMerge },
  );
  const currentKey = current?.key;
  const finishCurrent = useCallback(() => {
    if (currentKey) done(currentKey);
  }, [currentKey, done]);
  return (
    <AnimatePresence>
      {current && (
        <motion.div
          key={current.key}
          className="takeover"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
        >
          <Takeover item={current} state={state} onDone={finishCurrent} />
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function Headline({ children, color }: { children: string; color?: string }) {
  return (
    <motion.h1
      className="takeover__headline display"
      style={{ color }}
      initial={{ scale: 3, rotate: -12, opacity: 0 }}
      animate={{ scale: 1, rotate: -4, opacity: 1 }}
      transition={{ type: "spring", stiffness: 420, damping: 18 }}
    >
      {children}
    </motion.h1>
  );
}

function Takeover({ item, state, onDone }: { item: StageItem; state: PublicState; onDone: () => void }) {
  const players = new Map(state.players.map((p) => [p.id, p]));
  const teams = new Map(state.teams.map((t) => [t.id, t]));
  const m = item.moments[0];

  // Photo replays hold for 5 s; video replays end when the clip does (the stage's 16 s cap is a backstop).
  const isPhotoReplay = m.type === "replay" && m.item.kind === "photo";
  useEffect(() => {
    if (!isPhotoReplay) return;
    const id = setTimeout(onDone, 5000);
    return () => clearTimeout(id);
  }, [isPhotoReplay, onDone]);

  if (m.type === "shot") {
    const shots = item.moments.filter(isShot);
    const drinkers = latestDrinkers(shots).filter((d) => players.has(d.playerId));
    const drinkerIds = new Set(drinkers.map((d) => d.playerId));
    const loggers = [...new Set(shots.map((s) => s.loggedById).filter((id): id is string => !!id && !drinkerIds.has(id)))];
    const big = drinkers.length <= 3;
    return (
      <>
        <Headline>{shotHeadline(shots, (id) => players.get(id)?.teamId)}</Headline>
        <div className="takeover__people">
          {drinkers.map((d, i) => {
            const p = players.get(d.playerId)!;
            const color = teams.get(p.teamId)?.color;
            return (
              <motion.div
                key={d.playerId}
                className="takeover__person"
                style={{ "--team": color } as CSSProperties}
                initial={{ y: 80, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ delay: 0.08 + i * 0.06 }}
              >
                <Avatar avatar={p.avatar} size={big ? "13vw" : "7vw"} color={color} fire={d.streak === "fire"} />
                <span className="takeover__name display">{p.name}</span>
                <LedDigits value={d.count} size={big ? "5vw" : "3vw"} />
              </motion.div>
            );
          })}
        </div>
        {loggers.length > 0 && <p className="takeover__by">logged by {loggers.map((id) => players.get(id)?.name ?? "?").join(", ")}</p>}
      </>
    );
  }

  if (m.type === "milestone") {
    const p = m.playerId ? players.get(m.playerId) : undefined;
    const title =
      m.scope === "first"
        ? "FIRST BUCKET"
        : m.scope === "player"
          ? `${p?.name ?? "?"} HITS ${m.value}`
          : m.value === 100
            ? "CENTURY CLUB"
            : `${m.value} SHOTS TONIGHT`;
    return (
      <>
        <Headline color="var(--fire)">{title}</Headline>
        {p && <Avatar avatar={p.avatar} size="14vw" color={teams.get(p.teamId)?.color} fire />}
        {m.scope === "first" && p && <p className="takeover__sub display">{p.name}</p>}
      </>
    );
  }

  if (m.type === "lead-change") {
    const team = teams.get(m.teamId);
    return (
      <div className="takeover__lead" style={{ "--team": team?.color } as CSSProperties}>
        <Headline>LEAD CHANGE!</Headline>
        <p className="takeover__sub display">{team?.name ?? "?"} TAKES THE LEAD</p>
      </div>
    );
  }

  if (m.type === "waved-off") {
    const p = players.get(m.playerId);
    const why = m.reason === "not_me" ? "overturned on review" : m.reason === "host" ? "ref's call" : "waved off";
    return (
      <>
        <Headline color="var(--bad)">NO GOOD!</Headline>
        <p className="takeover__sub display">
          {p?.name ?? "?"} · {why}
        </p>
      </>
    );
  }

  if (m.type === "replay") {
    const by = players.get(m.item.playerId);
    return (
      <div className="replay">
        <p className="replay__label pixel">INSTANT REPLAY</p>
        {m.item.kind === "video" ? (
          <video className="replay__media" src={m.item.url} autoPlay playsInline onEnded={onDone} onError={onDone} />
        ) : (
          <img className="replay__media" src={m.item.url} alt="" onError={onDone} />
        )}
        {by && <p className="replay__by display">📸 {by.name}</p>}
      </div>
    );
  }
  return null;
}
