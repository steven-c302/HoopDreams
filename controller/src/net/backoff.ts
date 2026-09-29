/** Reconnect delay: 250 ms doubling per attempt, capped at 5 s. */
export const backoffMs = (attempt: number): number => Math.min(5_000, 250 * 2 ** attempt)
