/* Generated from backend/app/party/contract.py by `npm run gen:types`. Do not edit. */

export interface HoopContract {
  [k: string]: unknown;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "AvatarIn".
 */
export interface AvatarIn {
  kind: "emoji" | "photo";
  value: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "AvatarView".
 */
export interface AvatarView {
  kind: "emoji" | "photo";
  value: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "FeedItem".
 */
export interface FeedItem {
  at: number;
  drinkerId: string;
  loggedById: string | null;
  requestId: string;
  shotId: string;
  source: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "FireRule".
 */
export interface FireRule {
  coolMin: number;
  count: number;
  windowMin: number;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "GameActionIn".
 */
export interface GameActionIn {
  action: string;
  gameId: string;
  payload?: {
    [k: string]: unknown;
  };
  requestId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "GameEnableIn".
 */
export interface GameEnableIn {
  enabled: boolean;
  gameId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "GameMoment".
 */
export interface GameMoment {
  at: number;
  data: {
    [k: string]: unknown;
  };
  gameId: string;
  id: string;
  kind: string;
  type: "game";
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "HostActionIn".
 */
export interface HostActionIn {
  action: string;
  payload?: {
    [k: string]: unknown;
  };
  requestId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "HostAuthIn".
 */
export interface HostAuthIn {
  pin: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "HostShot".
 */
export interface HostShot {
  at: number;
  drinkerId: string;
  id: string;
  loggedById: string | null;
  requestId: string;
  source: string;
  voidReason: string | null;
  voidedAt: number | null;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "HostState".
 */
export interface HostState {
  shots: HostShot[];
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "JoinIn".
 */
export interface JoinIn {
  avatar: AvatarIn;
  name: string;
  requestId: string;
  teamId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "JoinInfo".
 */
export interface JoinInfo {
  lanUrl: string;
  tunnelUrl: string | null;
  wifiPassword: string | null;
  wifiSsid: string | null;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "JoinOut".
 */
export interface JoinOut {
  playerId: string;
  token: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "LeadChangeMoment".
 */
export interface LeadChangeMoment {
  at: number;
  id: string;
  teamId: string;
  type: "lead-change";
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "MediaOut".
 */
export interface MediaOut {
  mediaId: string;
  status: "processing" | "ready" | "failed";
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "MilestoneMoment".
 */
export interface MilestoneMoment {
  at: number;
  id: string;
  playerId: string | null;
  scope: "first" | "player" | "party";
  type: "milestone";
  value: number;
}
/**
 * Exists so the Moment union gets a named TypeScript type.
 *
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "MomentEnvelope".
 */
export interface MomentEnvelope {
  moment: ShotMoment | MilestoneMoment | LeadChangeMoment | WavedOffMoment | ReplayMoment | GameMoment;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ShotMoment".
 */
export interface ShotMoment {
  at: number;
  drinkers: ShotDrinker[];
  id: string;
  loggedById: string | null;
  requestId: string;
  source: string;
  type: "shot";
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ShotDrinker".
 */
export interface ShotDrinker {
  count: number;
  playerId: string;
  streak: ("heating" | "fire") | null;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "WavedOffMoment".
 */
export interface WavedOffMoment {
  at: number;
  id: string;
  playerId: string;
  reason: "undo" | "not_me" | "host";
  shotId: string;
  type: "waved-off";
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ReplayMoment".
 */
export interface ReplayMoment {
  at: number;
  id: string;
  item: ReelItem;
  type: "replay";
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ReelItem".
 */
export interface ReelItem {
  at: number;
  kind: "photo" | "video";
  mediaId: string;
  playerId: string;
  posterUrl: string | null;
  shotId: string | null;
  url: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "NightNewIn".
 */
export interface NightNewIn {
  name?: string | null;
}
/**
 * Host-tunable party behaviour. Stored per night in the settings table under key "party".
 *
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "PartySettings-Input".
 */
export interface PartySettingsInput {
  comboMaxMs?: number;
  comboWindowMs?: number;
  heatingUp?: StreakRule;
  notMeWindowSec?: number;
  onFire?: FireRule1;
  partyMilestoneEvery?: number;
  partyMilestones?: number[];
  playerMilestoneEvery?: number;
  reelItemSec?: number;
  shotLogPerMin?: number;
  sound?: boolean;
  undoWindowSec?: number;
  voice?: boolean;
}
export interface StreakRule {
  count: number;
  windowMin: number;
}
export interface FireRule1 {
  coolMin: number;
  count: number;
  windowMin: number;
}
/**
 * Host-tunable party behaviour. Stored per night in the settings table under key "party".
 *
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "PartySettings-Output".
 */
export interface PartySettingsOutput {
  comboMaxMs: number;
  comboWindowMs: number;
  heatingUp: StreakRule1;
  notMeWindowSec: number;
  onFire: FireRule2;
  partyMilestoneEvery: number;
  partyMilestones: number[];
  playerMilestoneEvery: number;
  reelItemSec: number;
  shotLogPerMin: number;
  sound: boolean;
  undoWindowSec: number;
  voice: boolean;
}
export interface StreakRule1 {
  count: number;
  windowMin: number;
}
export interface FireRule2 {
  coolMin: number;
  count: number;
  windowMin: number;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "PlayerEditIn".
 */
export interface PlayerEditIn {
  name?: string | null;
  playerId: string;
  teamId?: string | null;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "PlayerMergeIn".
 */
export interface PlayerMergeIn {
  fromId: string;
  intoId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "PlayerRemoveIn".
 */
export interface PlayerRemoveIn {
  playerId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "PlayerUpdateIn".
 */
export interface PlayerUpdateIn {
  avatar?: AvatarIn | null;
  name?: string | null;
  teamId?: string | null;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "PlayerView".
 */
export interface PlayerView {
  avatar: AvatarView;
  connected: boolean;
  count: number;
  id: string;
  lastShotAt: number | null;
  name: string;
  rank: number;
  streak: ("heating" | "fire") | null;
  teamId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "PublicState".
 */
export interface PublicState {
  feed: FeedItem[];
  games: {
    [k: string]: unknown;
  };
  gamesEnabled: {
    [k: string]: boolean;
  };
  join: JoinInfo;
  nightId: string;
  nightName: string;
  now: number;
  players: PlayerView[];
  reel: ReelItem[];
  settings: PartySettingsOutput;
  startedAt: number;
  teams: TeamView[];
  total: number;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "TeamView".
 */
export interface TeamView {
  color: string;
  id: string;
  name: string;
  size: number;
  total: number;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ResumeIn".
 */
export interface ResumeIn {
  token: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ResumeOut".
 */
export interface ResumeOut {
  playerId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "SettingsIn".
 */
export interface SettingsIn {
  settings: PartySettingsInput;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ShotAddIn".
 */
export interface ShotAddIn {
  /**
   * @minItems 1
   * @maxItems 60
   */
  drinkerIds: [string, ...string[]];
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ShotLogIn".
 */
export interface ShotLogIn {
  /**
   * @minItems 1
   * @maxItems 60
   */
  drinkerIds: [string, ...string[]];
  requestId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ShotLogOut".
 */
export interface ShotLogOut {
  shotIds: string[];
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ShotRejectIn".
 */
export interface ShotRejectIn {
  shotId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ShotUndoIn".
 */
export interface ShotUndoIn {
  requestId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "ShotVoidIn".
 */
export interface ShotVoidIn {
  shotId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "StreakRule".
 */
export interface StreakRule2 {
  count: number;
  windowMin: number;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "TeamRemoveIn".
 */
export interface TeamRemoveIn {
  teamId: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "TeamUpsertIn".
 */
export interface TeamUpsertIn {
  color: string;
  id?: string | null;
  name: string;
}
/**
 * This interface was referenced by `HoopContract`'s JSON-Schema
 * via the `definition` "WifiIn".
 */
export interface WifiIn {
  password?: string;
  ssid?: string;
}
