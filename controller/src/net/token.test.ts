import { describe, expect, it } from 'vitest'
import { TokenStore, type CookieJar, type KeyValue } from './token'

function memoryStorage(): KeyValue {
  const m = new Map<string, string>()
  return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => void m.set(k, v), removeItem: (k) => void m.delete(k) }
}
function memoryCookies(): CookieJar & { raw: string } {
  const jar = { raw: '', read: () => jar.raw, write: (c: string) => { jar.raw = c.split(';')[0] } }
  return jar
}

describe('TokenStore', () => {
  it('stores the session in localStorage', () => {
    const s = new TokenStore(memoryStorage(), memoryCookies())
    s.save({ room: 'KXQT', token: 't1', playerId: 'p1' })
    expect(s.load()).toEqual({ room: 'KXQT', token: 't1', playerId: 'p1' })
  })

  it('falls back to a cookie when localStorage is unavailable', () => {
    const cookies = memoryCookies()
    const s = new TokenStore(null, cookies)
    s.save({ room: 'KXQT', token: 't1', playerId: 'p1' })
    expect(new TokenStore(null, cookies).load()).toEqual({ room: 'KXQT', token: 't1', playerId: 'p1' })
  })

  it('falls back to a cookie when localStorage throws', () => {
    const throwing: KeyValue = { getItem: () => { throw new Error('denied') }, setItem: () => { throw new Error('denied') }, removeItem: () => {} }
    const cookies = memoryCookies()
    const s = new TokenStore(throwing, cookies)
    s.save({ room: 'ABCD', token: 't2', playerId: 'p2' })
    expect(s.load()?.token).toBe('t2')
  })

  it('clears both copies', () => {
    const cookies = memoryCookies()
    const s = new TokenStore(memoryStorage(), cookies)
    s.save({ room: 'ABCD', token: 't', playerId: 'p' })
    s.clear()
    expect(s.load()).toBeNull()
  })
})
