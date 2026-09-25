import { describe, expect, it } from 'vitest'
import { Outbox } from './outbox'
import { backoffMs } from './backoff'

const act = (id: string, round = 1) => ({ t: 'action' as const, id, round, payload: { kind: 'tap' } })

describe('Outbox', () => {
  it('keeps unacknowledged actions for resend, in order', () => {
    const o = new Outbox()
    o.add(act('a', 1)); o.add(act('b', 2)); o.add(act('c', 3))
    o.settle('b')
    expect(o.pending().map((m) => m.id)).toEqual(['a', 'c'])
  })

  it('drops an action once rejected so a stale action never loops', () => {
    const o = new Outbox()
    o.add(act('a', 3))
    o.settle('a')
    expect(o.pending()).toEqual([])
    o.settle('a')
    expect(o.pending()).toEqual([])
  })

  it('replaces an older action of the same kind in the same round', () => {
    const o = new Outbox()
    o.add({ t: 'action', id: 'a', round: 2, payload: { kind: 'write', text: 'one' } })
    o.add({ t: 'action', id: 'b', round: 2, payload: { kind: 'write', text: 'two' } })
    expect(o.pending().map((m) => m.id)).toEqual(['b'])
  })
})

describe('backoff', () => {
  it('grows exponentially and caps at five seconds', () => {
    expect([0, 1, 2, 3, 4, 5, 10].map(backoffMs)).toEqual([250, 500, 1000, 2000, 4000, 5000, 5000])
  })
})
