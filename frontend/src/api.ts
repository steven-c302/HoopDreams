import type { Draw, DrawResponse, Game } from "./types";

const BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8000";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  if (!res.ok) {
    throw new Error(`Request to ${path} failed: ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export function fetchGames(): Promise<Game[]> {
  return request<Game[]>("/api/games");
}

export function drawGame(exclude: number[]): Promise<DrawResponse> {
  return request<DrawResponse>("/api/draw", {
    method: "POST",
    body: JSON.stringify({ exclude }),
  });
}

export function fetchHistory(limit = 8): Promise<Draw[]> {
  return request<Draw[]>(`/api/history?limit=${limit}`);
}
