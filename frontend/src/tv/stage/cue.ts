import { LINES, pick } from "../../arcade/lines";
import { sfx } from "../../arcade/sound";
import { announce } from "../../arcade/voice";
import type { PublicState } from "../../party/contract.gen";
import { latestDrinkers } from "./headline";
import { isShot, type StageItem } from "./stage";

/** Sound plus announcer line for a stage item as it takes the screen. */
export function cue(item: StageItem, state: PublicState): void {
  const name = (id: string | null | undefined) => state.players.find((p) => p.id === id)?.name ?? "Somebody";
  const m = item.moments[0];
  switch (m.type) {
    case "shot": {
      const drinkers = latestDrinkers(item.moments.filter(isShot));
      const lead = drinkers[0];
      if (drinkers.length > 1) {
        sfx.bucket();
        announce(pick(LINES.group, { n: drinkers.length }));
      } else if (lead?.streak === "fire") {
        sfx.fire();
        announce(pick(LINES.fire, { name: name(lead.playerId) }));
      } else {
        sfx.swish();
        announce(pick(lead?.streak === "heating" ? LINES.heating : LINES.shot, { name: name(lead?.playerId) }));
      }
      return;
    }
    case "milestone":
      sfx.horn();
      if (m.scope === "first") announce(pick(LINES.first, { name: name(m.playerId) }));
      else if (m.scope === "player") announce(pick(LINES.playerMilestone, { name: name(m.playerId), value: m.value }));
      else announce(pick(m.value === 100 ? LINES.century : LINES.partyMilestone, { value: m.value }));
      return;
    case "lead-change":
      sfx.horn();
      announce(pick(LINES.lead, { team: state.teams.find((t) => t.id === m.teamId)?.name ?? "The visitors" }));
      return;
    case "waved-off":
      sfx.whistle();
      announce(pick(LINES.wavedOff));
      return;
    case "replay":
      sfx.bucket();
      announce(pick(LINES.replay));
      return;
    default:
      return;
  }
}
