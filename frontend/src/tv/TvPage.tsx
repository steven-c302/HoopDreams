import { useEffect, useState } from "react";
import { setSoundEnabled, unlockAudio } from "../arcade/sound";
import { ArcadeSurface } from "../arcade/Surface";
import { announce, setVoiceEnabled } from "../arcade/voice";
import { usePartyState } from "../party/store";
import { Feed } from "./Feed";
import { JoinPanel } from "./JoinPanel";
import { Leaderboard } from "./Leaderboard";
import { Reel } from "./Reel";
import { Scoreboard } from "./Scoreboard";
import { Stage } from "./stage/StageOverlay";
import "./tv.css";

export default function TvPage() {
  const state = usePartyState();
  const [tippedOff, setTippedOff] = useState(false);
  const sound = state?.settings.sound ?? true;
  const voice = state?.settings.voice ?? true;
  useEffect(() => setSoundEnabled(sound), [sound]);
  useEffect(() => setVoiceEnabled(voice), [voice]);

  // Browsers only allow sound and speech after a click, so the host tips off once per page load.
  async function tipOff() {
    await unlockAudio();
    announce("Welcome to Hoop Dreams! Let's get it!");
    setTippedOff(true);
  }

  if (!state) {
    return (
      <ArcadeSurface>
        <div className="tv tv--loading display">Warming up…</div>
      </ArcadeSurface>
    );
  }
  return (
    <ArcadeSurface>
      <div className="tv">
        <Scoreboard state={state} />
        <Leaderboard state={state} />
        <aside className="tv__side">
          <Reel state={state} />
          <Feed state={state} />
        </aside>
        <footer className="tv__foot">
          <div className="tv__strips" />
          <JoinPanel join={state.join} />
        </footer>
        <div className="tv__games" />
        <Stage state={state} />
        {!tippedOff && (
          <button className="tipoff" onClick={() => void tipOff()}>
            <span className="display">🏀 TIP OFF</span>
            <span className="pixel">click once to turn on sound</span>
          </button>
        )}
      </div>
    </ArcadeSurface>
  );
}
