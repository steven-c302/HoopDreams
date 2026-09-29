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

export type TriviaDifficulty = "easy" | "medium" | "hard";

export interface TriviaAnswer {
  text: string;
  correct: boolean;
}

export interface TriviaQuestion {
  category: string;
  difficulty: TriviaDifficulty;
  question: string;
  answers: TriviaAnswer[];
}
