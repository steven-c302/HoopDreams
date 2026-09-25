import type { Game } from "../types";

interface GameCardProps {
  game: Game;
  justReshuffled: boolean;
}

export function GameCard({ game, justReshuffled }: GameCardProps) {
  return (
    <div className={`game-card game-card--${game.color}`} key={game.id}>
      {justReshuffled && (
        <div className="game-card__banner">🔄 Hat refilled — every game's back in!</div>
      )}
      <div className="game-card__emoji">{game.emoji}</div>
      <h2 className="game-card__name">{game.name}</h2>
      <p className="game-card__description">{game.description}</p>
    </div>
  );
}
