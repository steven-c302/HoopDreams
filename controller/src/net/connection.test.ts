import { describe, expect, it, vi } from 'vitest'
import { Connection, type SocketLike } from './connection'
import type { ServerMsg } from '../protocol'

class FakeSocket implements SocketLike {
  static all: FakeSocket[] = []
  sent: string[] = []
  onopen: (() => void) | null = null
  onmessage: ((e: { data: string }) => void) | null = null
  onclose: (() => void) | null = null
  constructor(public url: string) { FakeSocket.all.push(this) }
  send(d: string) { this.sent.push(d) }
  close() { this.onclose?.() }
  open() { this.onopen?.() }
  recv(m: ServerMsg) { this.onmessage?.({ data: JSON.stringify(m) }) }
  sentOf(t: string) { return this.sent.map((s) => JSON.parse(s)).filter((m) => m.t === t) }
}

function setup() {
  FakeSocket.all = []
  vi.useFakeTimers()
  const events: string[] = []
  const c = new Connection('ws://tv/ws?token=x', (u) => new FakeSocket(u), {
    onMessage: (m) => events.push(m.t),
    onStatus: (s) => events.push(`status:${s}`),
  })
  c.start()
  return { c, events, sock: () => FakeSocket.all[FakeSocket.all.length - 1] }
}

describe('Connection', () => {
  it('ignores delayed events after stop and after restarting with a new socket', () => {
    const { c, sock, events } = setup()
    const old = sock()
    c.stop()
    old.open()
    old.recv({ t: 'bye', reason: 'KICKED' })
    expect(old.sent).toEqual([])
    expect(events).not.toContain('bye')
    c.start()
    const current = sock()
    current.open()
    old.open()
    old.recv({ t: 'bye', reason: 'KICKED' })
    vi.advanceTimersByTime(3_000)
    expect(current.sentOf('ping')).toHaveLength(1)
    expect(events).not.toContain('bye')
    c.stop()
    vi.useRealTimers()
  })

  it('starting twice keeps one socket and one heartbeat', () => {
    const { c, sock } = setup()
    c.start()
    expect(FakeSocket.all).toHaveLength(1)
    sock().open()
    vi.advanceTimersByTime(3_000)
    expect(sock().sentOf('ping')).toHaveLength(1)
    c.stop()
    vi.useRealTimers()
  })

  it('says hello, then resends unacknowledged actions after a reconnect', () => {
    const { c, sock } = setup()
    sock().open()
    expect(sock().sentOf('hello')).toHaveLength(1)
    c.act(2, { kind: 'write', text: 'hi' })
    const id = sock().sentOf('action')[0].id
    sock().close()
    vi.advanceTimersByTime(250)
    expect(FakeSocket.all).toHaveLength(2)
    sock().open()
    expect(sock().sentOf('action').map((a) => a.id)).toEqual([id])
    sock().recv({ t: 'ack', id })
    sock().close(); vi.advanceTimersByTime(250); sock().open()
    expect(sock().sentOf('action')).toEqual([])
    vi.useRealTimers()
  })

  it('sends ink without keeping it for a resend', () => {
    const { c, sock } = setup()
    sock().open()
    c.ink(4, [{ t: 'clear' }])
    expect(sock().sentOf('ink')).toEqual([{ t: 'ink', round: 4, ops: [{ t: 'clear' }] }])
    sock().close(); vi.advanceTimersByTime(250); sock().open()
    expect(sock().sentOf('ink')).toEqual([])
    vi.useRealTimers()
  })

  it('does not resend an action the server rejected as stale', () => {
    const { c, sock } = setup()
    sock().open()
    c.act(1, { kind: 'pick', option: 'o1' })
    const id = sock().sentOf('action')[0].id
    sock().recv({ t: 'reject', id, code: 'STALE' })
    sock().close(); vi.advanceTimersByTime(250); sock().open()
    expect(sock().sentOf('action')).toEqual([])
    vi.useRealTimers()
  })

  it('pings every three seconds and reconnects when the server goes quiet', () => {
    const { sock } = setup()
    sock().open()
    vi.advanceTimersByTime(3_000)
    expect(sock().sentOf('ping').length).toBeGreaterThanOrEqual(1)
    vi.advanceTimersByTime(10_000)
    expect(FakeSocket.all.length).toBeGreaterThanOrEqual(2)
    vi.useRealTimers()
  })

  it('stops for good on bye', () => {
    const { sock, events } = setup()
    sock().open()
    sock().recv({ t: 'bye', reason: 'KICKED' })
    vi.advanceTimersByTime(30_000)
    expect(FakeSocket.all).toHaveLength(1)
    expect(events).toContain('bye')
    expect(events).toContain('status:closed')
    expect(sock().sentOf('ping')).toEqual([])
    vi.useRealTimers()
  })
})
