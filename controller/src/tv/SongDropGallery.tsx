import '../theme/tokens.css'
import './tv.css'
import { GameScene } from '../theme/GameScene'
import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { SongDropStage } from './SongDropStage'
import type { SongDropTv } from './types'

const NAMES = ['Amanda', 'Steven', 'John', 'Sunhye', 'Alex', 'Anna', 'Charlie', 'Daniel', 'Daniel K', 'Ethan', 'Junha', 'Kaishun', 'Izzy', 'Maximilian-Longname', 'Wes', 'Zoe']
const COLORS = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF55', '#7FD3FF', '#2F6BFF', '#8B4DFF', '#FF6FB5']

/** /tv?gallery=songdrop&beat=load|stage|reveal|podium|dead[&n=16]: each beat from fixture data at 1920×1080, no player, no server. */
export function SongDropGallery() {
  const q = new URLSearchParams(location.search)
  const n = Math.min(16, Math.max(2, Number(q.get('n') ?? 8) || 8))
  const beat = (q.get('beat') ?? 'stage') as SongDropTv['phase']
  const players: PlayerSummary[] = NAMES.slice(0, n).map((name, i) => ({
    id: `p${i}`, name, role: 'PLAYER', connected: true, avatar: { face: `p:${String(i).padStart(2, '0')}`, color: COLORS[i % COLORS.length] },
  }))
  const scores: ScoreRow[] = players.map((p, i) => ({ id: p.id, name: p.name, avatar: p.avatar, score: Math.max(0, 9000 - i * 450) }))
  const card = { title: 'Never Gonna Give You Up', artist: 'Rick Astley', year: 1987, videoId: 'dQw4w9WgXcQ' }
  const g: SongDropTv = {
    t: 'songdrop', phase: beat, song: 3, totalSongs: 8, finalSong: false, clipSeq: 7, videoId: 'dQw4w9WgXcQ', startSec: 43,
    stage: 1, stages: 4, clipMs: 4000, answered: Math.min(n, 5), expected: n,
    solvers: players.slice(0, 2).map((p, i) => ({ id: p.id, name: p.name, points: beat === 'reveal' ? 1000 - i * 150 : undefined })),
    lockedOut: players.slice(2, Math.min(n, 5)).map((p) => p.id),
    card: beat === 'reveal' ? card : undefined,
    drinks: beat === 'reveal' ? players.slice(5).map((p) => ({ id: p.id, name: p.name, sips: 1, text: 'Missed it. Drink 1 sip' })) : [],
    gallery: beat === 'podium' ? Array.from({ length: 8 }, (_, i) => ({ ...card, title: `Song number ${i + 1}` })) : [],
  }
  const stage: StageInfo = { gameId: 'songdrop', title: 'Song Drop', phaseSeq: 3, paused: false, game: g as unknown as StageInfo['game'] }
  return (
    <div className="tv-root">
      <div className="tv-stage" style={{ transform: `scale(${Math.min(innerWidth / 1920, innerHeight / 1080)}) translate(-50%, -50%)` }}>
        <GameScene game="songdrop"><SongDropStage stage={stage} players={players} scores={scores} clock={{ deadline: null, frozen: null }} cmd={() => undefined} still /></GameScene>
      </div>
    </div>
  )
}
