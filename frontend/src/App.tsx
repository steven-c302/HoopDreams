import { useEffect, useState } from "react";
import { drawGame, fetchGames, fetchHistory } from "./api";
import { GameCard } from "./components/GameCard";
import { Hat } from "./components/Hat";
import { HistoryStrip } from "./components/HistoryStrip";
import { TriviaGame } from "./components/TriviaGame";
import type { Draw, Game } from "./types";

const PLAYABLE_GAMES = new Set(["Trivia"]);

function App() {
  const [games, setGames] = useState<Game[]>([]);
  const [drawnIds, setDrawnIds] = useState<number[]>([]);
  const [current, setCurrent] = useState<{ game: Game; reshuffled: boolean } | null>(null);
  const [history, setHistory] = useState<Draw[]>([]);
  const [isDrawing, setIsDrawing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [mode, setMode] = useState<"hat" | "trivia">("hat");

  useEffect(() => {
    Promise.all([fetchGames(), fetchHistory()])
      .then(([gamesRes, historyRes]) => {
        setGames(gamesRes);
        setHistory(historyRes);
      })
      .catch(() => setError("Couldn't reach the HoopDreams server. Is the backend running?"))
      .finally(() => setLoaded(true));
  }, []);

  const remaining = Math.max(games.length - drawnIds.length, 0);

  async function handleDraw() {
    setError(null);
    setIsDrawing(true);
    try {
      const result = await drawGame(drawnIds);
      setTimeout(() => {
        setCurrent(result);
        setDrawnIds((prev) => (result.reshuffled ? [result.game.id] : [...prev, result.game.id]));
        setIsDrawing(false);
        fetchHistory().then(setHistory).catch(() => {});
      }, 900);
    } catch {
      setError("The draw didn't go through — try again in a sec.");
      setIsDrawing(false);
    }
  }

  function handleReset() {
    setDrawnIds([]);
    setCurrent(null);
  }

  return (
    <div className="page">
      <header className="header">
        <p className="header__eyebrow">🏀 HoopDreams</p>
        <h1 className="header__title">Game &amp; Drinking Night</h1>
        <p className="header__subtitle">Pull a game out of the hat and play it live!</p>
      </header>

      <main className="content">
        {mode === "trivia" ? (
          <TriviaGame onExit={() => setMode("hat")} />
        ) : (
          <>
            <Hat onDraw={handleDraw} isDrawing={isDrawing} disabled={!loaded || games.length === 0} />

            {loaded && games.length > 0 && (
              <p className="remaining">
                {remaining === games.length
                  ? `${games.length} game${games.length === 1 ? "" : "s"} in the hat`
                  : `${remaining} of ${games.length} games left in the hat`}
              </p>
            )}

            {error && <p className="error">{error}</p>}

            {current && (
              <div className="reveal">
                <GameCard
                  game={current.game}
                  justReshuffled={current.reshuffled}
                  onPlay={PLAYABLE_GAMES.has(current.game.name) ? () => setMode("trivia") : undefined}
                />
              </div>
            )}

            {drawnIds.length > 0 && (
              <button className="reset-button" onClick={handleReset}>
                🔄 Reset hat
              </button>
            )}

            <HistoryStrip history={history} />
          </>
        )}
      </main>
    </div>
  );
}

export default App;
