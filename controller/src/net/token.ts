export interface KeyValue { getItem(k: string): string | null; setItem(k: string, v: string): void; removeItem(k: string): void }
export interface CookieJar { read(): string; write(cookie: string): void }
export interface Session { room: string; token: string; playerId: string }

const KEY = 'partyos.session'

/** Remembers this phone's identity: localStorage first, a same-origin cookie as the fallback. */
export class TokenStore {
  constructor(private storage: KeyValue | null, private cookies: CookieJar) {}

  save(s: Session) {
    const v = JSON.stringify(s)
    try { this.storage?.setItem(KEY, v) } catch { /* private mode or blocked storage */ }
    this.cookies.write(`${KEY}=${encodeURIComponent(v)}; Max-Age=86400; Path=/; SameSite=Strict`)
  }

  load(): Session | null {
    let v: string | null = null
    try { v = this.storage?.getItem(KEY) ?? null } catch { v = null }
    if (!v) {
      const hit = this.cookies.read().split('; ').find((c) => c.startsWith(`${KEY}=`))
      v = hit ? decodeURIComponent(hit.slice(KEY.length + 1)) : null
    }
    if (!v) return null
    try {
      const s = JSON.parse(v) as Session
      return s && typeof s.token === 'string' && typeof s.room === 'string' ? s : null
    } catch { return null }
  }

  clear() {
    try { this.storage?.removeItem(KEY) } catch { /* ignore */ }
    this.cookies.write(`${KEY}=; Max-Age=0; Path=/`)
  }
}

export function browserTokenStore(): TokenStore {
  let storage: KeyValue | null = null
  try { storage = window.localStorage } catch { storage = null }
  return new TokenStore(storage, { read: () => document.cookie, write: (c) => { document.cookie = c } })
}
