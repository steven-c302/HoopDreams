import { useSyncExternalStore } from "react";
import { call, getSocket } from "../party/socket";
export type Mode = "trivia" | "draw" | "riff";
export interface Stroke {
  points: [number, number][];
  color: string;
}
export interface GameState {
  nightId: string;
  mode: Mode | null;
  phase: "lobby" | "answer" | "vote" | "reveal" | "finished";
  revision: number;
  scores: Record<string, number>;
  round: number;
  rounds: number;
  roundId: string;
  deadline: number | null;
  paused: boolean;
  remaining: number;
  prompt: string;
  category: string;
  options: string[];
  correct: string | null;
  artistId: string | null;
  artistTeam: string | null;
  strokes: Stroke[];
  submitted: string[];
  writtenTeams: string[];
  voted: string[];
  entries: { id: string; text: string; teamId: string | null }[];
  results: { teamId: string; answer: string; points: number }[];
  source: string;
  bankCount: number;
  roster: Record<string, string>;
}
let state: GameState | null = null;
const listeners = new Set<() => void>();
let wired = false;
function receive(next: GameState) {
  state = next;
  listeners.forEach((fn) => fn());
}
export async function syncGame() {
  const ack = await call<{
    state: GameState;
    secret: string | null;
    ownEntry: string | null;
  }>("game:sync", {});
  if (ack.ok) receive(ack.state);
  return ack;
}
function subscribe(listener: () => void) {
  if (!wired) {
    wired = true;
    const socket = getSocket();
    socket.on("game:state", receive);
    socket.on("connect", () => void syncGame());
    if (socket.connected) void syncGame();
  }
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
export function useGame() {
  return useSyncExternalStore(subscribe, () => state);
}
export const GAMES: {
  id: Mode;
  name: string;
  kicker: string;
  description: string;
  duration: string;
  icon: string;
}[] = [
  {
    id: "trivia",
    name: "Know It All",
    kicker: "THE CROWD PLEASER",
    description: "Big opinions. Four answers. One very smug team.",
    duration: "8 rounds · ~8 min",
    icon: "?",
  },
  {
    id: "draw",
    name: "Draw a Blank",
    kicker: "QUESTIONABLE ART",
    description: "Draw on your phone. Let the room lose its mind.",
    duration: "8 rounds · ~12 min",
    icon: "✎",
  },
  {
    id: "riff",
    name: "Room Service",
    kicker: "NO WRONG ANSWERS",
    description: "Serve your funniest answer. The room picks a favorite.",
    duration: "4 rounds · ~7 min",
    icon: "✳",
  },
];

export const RULES: Record<Mode, string> = {
  trivia:
    "30 seconds. Talk it out, then everyone locks an answer on their phone. Your team’s majority answer wins 1,000 points. Ties use the first answer received. No speed bonus—make your case.",
  draw: "One artist gets a secret word on their phone and draws for 75 seconds. Other teams type guesses. Each team that gets it earns 1,000 points; the artist’s team earns 500 per correct team. Artists rotate.",
  riff: "You have 60 seconds to dream up a funny answer together. One person submits for your team, then everyone votes for another team’s answer. Each team’s majority vote gives 500 points. Ties use the first vote received.",
};
