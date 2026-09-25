import { useCallback, useEffect, useState } from "react";
import type { JoinIn, JoinOut, PublicState, ResumeOut } from "../party/contract.gen";
import { loadIdentity, saveIdentity, type Identity } from "../party/identity";
import { call, type Ack } from "../party/socket";
import { useConnected } from "../party/store";

export interface JoinInput {
  requestId: string;
  name: string;
  emoji: string;
  teamId: string;
}

export type PlayerStatus = "joining" | "resuming" | "ready";

/** Who this phone is. Re-identifies on every (re)connect and forgets itself when the night changes. */
export function usePlayer(state: PublicState | null) {
  const connected = useConnected();
  const [identity, setIdentity] = useState<Identity | null>(loadIdentity);
  const [resumed, setResumed] = useState(false);

  const forget = useCallback(() => {
    saveIdentity(null);
    setIdentity(null);
    setResumed(false);
  }, []);

  useEffect(() => {
    if (!connected || !identity) {
      setResumed(false);
      return;
    }
    let live = true;
    void call<ResumeOut>("player:resume", { token: identity.token }).then((ack) => {
      if (!live) return;
      if (ack.ok) setResumed(true);
      else if (!ack.retryable) forget();
    });
    return () => {
      live = false;
    };
  }, [connected, identity, forget]);

  const nightId = state?.nightId;
  useEffect(() => {
    if (nightId && identity && nightId !== identity.nightId) forget();
  }, [nightId, identity, forget]);

  const join = useCallback(
    async (input: JoinInput): Promise<Ack<JoinOut>> => {
      if (!nightId) return { ok: false, error: "Still connecting — try again" };
      const payload: JoinIn = {
        requestId: input.requestId,
        name: input.name,
        avatar: { kind: "emoji", value: input.emoji },
        teamId: input.teamId,
      };
      const ack = await call<JoinOut>("player:join", payload);
      if (ack.ok) {
        const next = { nightId, playerId: ack.playerId, token: ack.token };
        saveIdentity(next);
        setIdentity(next);
        setResumed(true);
      }
      return ack;
    },
    [nightId],
  );

  const me = identity ? (state?.players.find((p) => p.id === identity.playerId) ?? null) : null;
  const status: PlayerStatus = !identity ? "joining" : resumed ? "ready" : "resuming";
  return { status, identity, me, resumed, join, forget };
}
