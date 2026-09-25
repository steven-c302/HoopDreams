export type GameColor = "sky" | "pink" | "lavender" | "mint" | "peach";

export interface Game {
  id: number;
  name: string;
  emoji: string;
  description: string;
  color: GameColor;
}

export interface DrawResponse {
  game: Game;
  reshuffled: boolean;
}

export interface Draw {
  id: number;
  drawn_at: string;
  game: Game;
}
