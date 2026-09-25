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
    vi.useRealTimers()
  })
})
