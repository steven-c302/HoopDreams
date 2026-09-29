import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import type { PublicState } from "../party/contract.gen";

export function Reel({ state }: { state: PublicState }) {
  const items = state.reel;
  const [tick, setTick] = useState(0);
  const seconds = state.settings.reelItemSec;
  useEffect(() => {
    if (items.length <= 1) return;
    const id = setInterval(() => setTick((t) => t + 1), seconds * 1000);
    return () => clearInterval(id);
  }, [items.length, seconds]);

  if (items.length === 0) {
    return (
      <section className="reel reel--empty">
        <p className="pixel">📸 SHOT-CAM HIGHLIGHTS LAND HERE</p>
      </section>
    );
  }
  const item = items[tick % items.length];
  return (
    <section className="reel">
      <AnimatePresence mode="wait">
        <motion.div key={item.mediaId} className="reel__frame" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          {item.kind === "video" ? (
            <video src={item.url} poster={item.posterUrl ?? undefined} autoPlay muted loop playsInline />
          ) : (
            <img src={item.url} alt="" />
          )}
        </motion.div>
      </AnimatePresence>
      <span className="reel__label pixel">HIGHLIGHTS</span>
    </section>
  );
}
