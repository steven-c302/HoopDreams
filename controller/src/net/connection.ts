import { encodeClient, parseServerMsg, PROTOCOL_VERSION, type ActionPayload, type ClientMsg, type HostCommand, type ServerMsg } from '../protocol'
import { backoffMs } from './backoff'
import { Outbox } from './outbox'

export interface SocketLike {
  onopen: (() => void) | null
  onmessage: ((e: { data: string }) => void) | null
  onclose: (() => void) | null
  send(data: string): void
  close(): void
}

export type Status = 'connecting' | 'online' | 'offline' | 'closed'

export interface Handlers {
  onMessage(m: ServerMsg): void
  onStatus(s: Status): void
}

const PING_MS = 3_000
const SILENCE_MS = 10_000

let counter = 0
const newId = () => `${Date.now().toString(36)}-${(counter++).toString(36)}-${Math.random().toString(36).slice(2, 7)}`

/** A self-healing socket: reconnects with backoff, pings, and resends unacknowledged actions. */
export class Connection {
  private sock: SocketLike | null = null
  private attempt = 0
  private stopped = false
  private lastHeard = 0
  private pingTimer: ReturnType<typeof setInterval> | null = null
  private retryTimer: ReturnType<typeof setTimeout> | null = null
  private outbox = new Outbox()

  constructor(private url: string, private factory: (url: string) => SocketLike, private h: Handlers) {}

  start() {
    this.stopped = false
    this.open()
  }

  stop() {
    this.stopped = true
    this.clearTimers()
    this.sock?.close()
    this.h.onStatus('closed')
  }

  act(round: number, payload: ActionPayload): string {
    const m: ClientMsg = { t: 'action', id: newId(), round, payload }
    this.outbox.add(m)
    this.send(m)
    return m.id
  }

  host(cmd: HostCommand): string {
    const m: ClientMsg = { t: 'host', id: newId(), cmd }
    this.outbox.add(m)
    this.send(m)
    return m.id
  }

  private open() {
    this.h.onStatus('connecting')
    const s = this.factory(this.url)
    this.sock = s
    s.onopen = () => {
      this.attempt = 0
      this.lastHeard = Date.now()
      this.h.onStatus('online')
      this.send({ t: 'hello', protocol: PROTOCOL_VERSION })
      for (const m of this.outbox.pending()) this.send(m)
      this.pingTimer = setInterval(() => this.heartbeat(), PING_MS)
    }
    s.onmessage = (e) => {
      this.lastHeard = Date.now()
      const m = parseServerMsg(e.data)
      if (!m) return
      if (m.t === 'ack' || m.t === 'reject') this.outbox.settle(m.id)
      if (m.t === 'bye') this.stopped = true
      this.h.onMessage(m)
    }
    s.onclose = () => {
      if (this.sock !== s) return
      this.clearTimers()
      this.sock = null
      if (this.stopped) { this.h.onStatus('closed'); return }
      this.h.onStatus('offline')
      this.retryTimer = setTimeout(() => this.open(), backoffMs(this.attempt++))
    }
  }

  private heartbeat() {
    if (Date.now() - this.lastHeard > SILENCE_MS) {
      this.sock?.close()
      return
    }
    this.send({ t: 'ping' })
  }

  private send(m: ClientMsg) {
    try { this.sock?.send(encodeClient(m)) } catch { /* socket not open yet; outbox resends on open */ }
  }

  private clearTimers() {
    if (this.pingTimer) clearInterval(this.pingTimer)
    if (this.retryTimer) clearTimeout(this.retryTimer)
    this.pingTimer = null
    this.retryTimer = null
  }
}

/** Builds a same-origin ws:// URL and a factory backed by the browser's WebSocket. */
export function browserSocketUrl(query: string): string {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${location.host}/ws?${query}`
}

export const browserSocket = (url: string): SocketLike => {
  const ws = new WebSocket(url)
  const s: SocketLike = {
    onopen: null, onmessage: null, onclose: null,
    send: (d) => { if (ws.readyState === WebSocket.OPEN) ws.send(d); else throw new Error('not open') },
    close: () => ws.close(),
  }
  ws.onopen = () => s.onopen?.()
  ws.onmessage = (e) => s.onmessage?.({ data: String(e.data) })
  ws.onclose = () => s.onclose?.()
  ws.onerror = () => ws.close()
  return s
}
