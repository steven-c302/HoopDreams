import { describe, expect, it } from 'vitest'
import serverFixtures from './protocol/fixtures/server-messages.json'
import clientFixtures from './protocol/fixtures/client-messages.json'
import { encodeClient, parseServerMsg, type ClientMsg } from './protocol'

describe('protocol fixtures shared with the Kotlin server', () => {
  it('parses every server message sample', () => {
    for (const raw of serverFixtures) {
      const msg = parseServerMsg(JSON.stringify(raw))
      expect(msg, JSON.stringify(raw).slice(0, 80)).not.toBeNull()
      expect(msg!.t).toBe(raw.t)
    }
  })

  it('covers every screen kind the phone can render', () => {
    const kinds = serverFixtures.flatMap((m) => ('view' in m ? [(m.view as { screen: { t: string } }).screen.t] : []))
    expect(new Set(kinds)).toEqual(new Set(['waiting', 'tutorial', 'text', 'choice', 'scores', 'number', 'multi', 'turf', 'sprawl', 'secret', 'board', 'buzzer']))
  })

  it('encodes client messages exactly as the server expects', () => {
    const ours: ClientMsg[] = [
      { t: 'hello', protocol: 1 },
      { t: 'action', id: 'a-1', round: 3, payload: { kind: 'write', text: 'stars' } },
      { t: 'action', id: 'a-2', round: 4, payload: { kind: 'pick', option: 'o2' } },
      { t: 'action', id: 'a-3', round: 1, payload: { kind: 'ack' } },
      { t: 'host', id: 'h-1', cmd: { t: 'start', gameId: 'bluff', rounds: 5, options: {} } },
      { t: 'host', id: 'h-8', cmd: { t: 'start', gameId: 'trivia', rounds: 5, options: { teams: 4, drinks: 1 } } },
      { t: 'host', id: 'h-9', cmd: { t: 'setOption', key: 'game', value: 1 } },
      { t: 'host', id: 'h-10', cmd: { t: 'makeCaptain', playerId: 'p-al' } },
      { t: 'host', id: 'h-11', cmd: { t: 'gameAction', action: 'shuffle' } },
      { t: 'action', id: 'a-4', round: 9, payload: { kind: 'guess', value: 206 } },
      { t: 'action', id: 'a-5', round: 10, payload: { kind: 'multi', picks: ['a'], lock: true } },
      { t: 'host', id: 'h-2', cmd: { t: 'pause' } },
      { t: 'host', id: 'h-3', cmd: { t: 'resume' } },
      { t: 'host', id: 'h-4', cmd: { t: 'skip' } },
      { t: 'host', id: 'h-5', cmd: { t: 'end' } },
      { t: 'host', id: 'h-6', cmd: { t: 'kick', playerId: 'p-al' } },
      { t: 'host', id: 'h-7', cmd: { t: 'setRounds', rounds: 6 } },
      { t: 'host', id: 'h-12', cmd: { t: 'setOption', key: 'turfMode', value: 2 } },
      { t: 'host', id: 'h-13', cmd: { t: 'setOption', key: 'minutes', value: 45 } },
      { t: 'action', id: 'a-6', round: 21, payload: { kind: 'bid', auction: 3, amount: 30 } },
      { t: 'action', id: 'a-7', round: 21, payload: { kind: 'build', target: 1 } },
      { t: 'action', id: 'a-8', round: 20, payload: { kind: 'trade', to: 1, give: ['1'], get: ['5'], giveCash: 50, getCash: 0 } },
      { t: 'host', id: 'h-14', cmd: { t: 'setOption', key: 'vp', value: 10 } },
      { t: 'action', id: 'a-9', round: 30, payload: { kind: 'build', what: 'road', target: 12 } },
      { t: 'action', id: 'a-10', round: 30, payload: { kind: 'trade', to: -2, give: [1, 0, 0, 0, 0], get: [0, 0, 0, 1, 0] } },
      { t: 'action', id: 'a-11', round: 2, payload: { kind: 'seen' } },
      { t: 'action', id: 'a-12', round: 3, payload: { kind: 'clue', text: 'cheesy' } },
      { t: 'action', id: 'a-13', round: 5, payload: { kind: 'vote', option: 'p-al' } },
      { t: 'action', id: 'a-14', round: 4, payload: { kind: 'pick', cell: 'food-200' } },
      { t: 'action', id: 'a-15', round: 6, payload: { kind: 'buzz' } },
      { t: 'action', id: 'a-16', round: 8, payload: { kind: 'wager', value: 300 } },
      { t: 'host', id: 'h-15', cmd: { t: 'setOption', key: 'show', value: 1 } },
      { t: 'host', id: 'h-16', cmd: { t: 'gameAction', action: 'pick:food-200' } },
      { t: 'ping' },
    ]
    expect(ours.map((m) => JSON.parse(encodeClient(m)))).toEqual(clientFixtures)
  })

  it('rejects malformed input instead of throwing', () => {
    expect(parseServerMsg('{nope')).toBeNull()
    expect(parseServerMsg('{"t":"mystery"}')).toBeNull()
    expect(parseServerMsg('{"t":"view","seq":1}')).toBeNull()
  })
})
