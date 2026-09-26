import { Card } from './Card'
import { Chip, Led } from './Casino'
import { SuitSprite } from './Suits'

/** Design review sheet at /tv?gallery: every card, the back, chips and readouts. */
export function Gallery() {
  return (
    <div style={{ position: 'fixed', inset: 0, overflow: 'auto', background: '#0f5c4a', padding: 24 }}>
      <SuitSprite />
      {[0, 1, 2, 3].map((suit) => (
        <div key={suit} style={{ display: 'flex', gap: 10, marginBottom: 14 }}>
          {Array.from({ length: 13 }, (_, i) => <Card key={i} card={{ rank: i + 1, suit }} width={110} animate={false} />)}
        </div>
      ))}
      <div style={{ display: 'flex', gap: 18, alignItems: 'center' }}>
        <Card card={{ rank: 0, suit: 0 }} width={110} animate={false} />
        {[100, 250, 500, 1000].map((v) => <Chip key={v} value={v} size={90} />)}
        <Led value={21} tone="gold" size={60} /><Led value={17} size={60} /><Led value={24} tone="red" size={60} />
      </div>
    </div>
  )
}
