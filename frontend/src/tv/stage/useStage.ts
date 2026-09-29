import { useCallback, useEffect, useRef, useState } from "react";
import { onMoment } from "../../party/store";
import { advance, emptyStage, enqueue, finish, type StageItem, type StageState, type StageTiming } from "./stage";

interface StageCallbacks {
  onShow: (item: StageItem) => void;
  onMerge: (item: StageItem) => void;
}

export function useStage(timing: StageTiming, callbacks: StageCallbacks) {
  const [stage, setStage] = useState<StageState>(emptyStage);
  const timingRef = useRef(timing);
  const callbacksRef = useRef(callbacks);
  useEffect(() => {
    timingRef.current = timing;
    callbacksRef.current = callbacks;
  });

  useEffect(
    () =>
      onMoment((m) =>
        setStage((s) => {
          const now = Date.now();
          return advance(enqueue(s, m, now, timingRef.current), now);
        }),
      ),
    [],
  );
  useEffect(() => {
    const id = setInterval(() => setStage((s) => advance(s, Date.now())), 200);
    return () => clearInterval(id);
  }, []);

  // Cue sound and voice outside the reducer: once per new item, plus a swish per merged shot.
  const current = stage.current;
  const seen = useRef<{ key: string | null; size: number }>({ key: null, size: 0 });
  useEffect(() => {
    const previous = seen.current;
    seen.current = { key: current?.key ?? null, size: current?.moments.length ?? 0 };
    if (!current) return;
    if (current.key !== previous.key) callbacksRef.current.onShow(current);
    else if (current.moments.length > previous.size) callbacksRef.current.onMerge(current);
  }, [current]);

  const done = useCallback((key: string) => setStage((s) => advance(finish(s, key, Date.now()), Date.now())), []);
  return { current, done };
}
