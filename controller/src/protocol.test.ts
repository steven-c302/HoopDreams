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
    expect(new Set(kinds)).toEqual(new Set(['waiting', 'tutorial', 'text', 'choice', 'scores']))
  })

  it('encodes client messages exactly as the server expects', () => {
    const ours: ClientMsg[] = [
      { t: 'hello', protocol: 1 },
      { t: 'action', id: 'a-1', round: 3, payload: { kind: 'write', text: 'stars' } },
      { t: 'action', id: 'a-2', round: 4, payload: { kind: 'pick', option: 'o2' } },
      { t: 'action', id: 'a-3', round: 1, payload: { kind: 'ack' } },
      { t: 'host', id: 'h-1', cmd: { t: 'start', gameId: 'bluff', rounds: 5 } },
      { t: 'host', id: 'h-2', cmd: { t: 'pause' } },
      { t: 'host', id: 'h-3', cmd: { t: 'resume' } },
      { t: 'host', id: 'h-4', cmd: { t: 'skip' } },
      { t: 'host', id: 'h-5', cmd: { t: 'end' } },
      { t: 'host', id: 'h-6', cmd: { t: 'kick', playerId: 'p-al' } },
      { t: 'host', id: 'h-7', cmd: { t: 'setRounds', rounds: 6 } },
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
