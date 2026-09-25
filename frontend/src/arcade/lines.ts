export const LINES = {
  shot: [
    "{name}… from downtown!",
    "{name}, nothing but net!",
    "Boomshakalaka! {name}!",
    "{name} with the jam!",
    "Is it the shoes? {name}!",
    "Razzle dazzle, {name}!",
    "{name} for three!",
    "Count it! {name}!",
  ],
  heating: ["{name} is heating up!", "{name} is getting warm!"],
  fire: ["{name} is on fire!", "{name} can't miss!", "Somebody stop {name}!"],
  group: ["{n} shots down! Team effort!", "Combo! {n} at once!", "Everybody eats! {n} shots!"],
  first: ["And we're underway! First bucket, {name}!"],
  playerMilestone: ["{name} hits {value}!", "That's {value} for {name}!"],
  partyMilestone: ["{value} shots tonight! What a game!"],
  century: ["Welcome to the century club!"],
  lead: ["{team} takes the lead!", "Lead change! {team} in front!"],
  wavedOff: ["No good! Waved off!", "Overturned on review!", "The ref says no!"],
  replay: ["Let's go to the replay!", "Instant replay!"],
} as const;

export function fill(line: string, vars: Record<string, string | number>): string {
  return line.replace(/\{(\w+)\}/g, (match, key: string) => (key in vars ? String(vars[key]) : match));
}

export function pick(lines: readonly string[], vars: Record<string, string | number> = {}, random = Math.random): string {
  return fill(lines[Math.min(lines.length - 1, Math.floor(random() * lines.length))], vars);
}
