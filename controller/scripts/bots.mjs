#!/usr/bin/env node
// Rehearsal bots: join the party like phones do and play whatever is on their screen.
// Usage: node controller/scripts/bots.mjs [count=5] [http://127.0.0.1:8080]
const count = Number(process.argv[2] ?? 5)
const base = process.argv[3] ?? 'http://127.0.0.1:8080'
const names = ['Ava', 'Ben', 'Cleo', 'Dev', 'Eli', 'Fin', 'Gus', 'Hana', 'Ivy', 'Jay', 'Kai', 'Lu', 'Mo', 'Nia', 'Oz', 'Pip']
const faces = Array.from({ length: 16 }, (_, i) => `p:${String(i).padStart(2, '0')}`)
const colors = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF55', '#7FD3FF', '#2F6BFF', '#8B4DFF', '#FF6FB5']
const teamNames = ['The Quizzards', 'Smarty Pints', 'Brain Freeze', 'Trivia Newton John', 'Les Quizerables', 'Sip Happens']
const fakes = ['a very old goat', 'the moon', 'spaghetti', 'Belgium', 'four', 'a rubber duck', 'Nicolas Cage', 'soup', 'jazz', 'the year 1812']
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const pick = (a) => a[Math.floor(Math.random() * a.length)]

const tv = await fetch(`${base}/api/tv/session`).then((r) => r.json())
async function bot(i) {
  await sleep(i * 350)
  const r = await fetch(`${base}/api/join`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ room: tv.room, name: names[i % 16] + (i >= 16 ? i : ''), avatar: { face: faces[i % 16], color: colors[i % 8] }, spectator: false }) })
  const { token } = await r.json()
  if (!token) return console.log('join failed', await r.text())
  const ws = new WebSocket(`${base.replace('http', 'ws')}/ws?token=${token}`)
  let n = 0
  let lastKey = ''
  const act = (round, payload) => ws.send(JSON.stringify({ t: 'action', id: `${i}-${n++}`, round, payload }))
  ws.onopen = () => { ws.send(JSON.stringify({ t: 'hello', protocol: 1 })); setInterval(() => ws.send(JSON.stringify({ t: 'ping' })), 3000) }
  ws.onmessage = async (e) => {
    const m = JSON.parse(e.data)
    if (m.t !== 'view') return
    const { screen, round, paused } = m.view
    if (screen.t === 'turf') return turf(screen, round)
    const key = `${round}:${screen.t}:${screen.kind ?? ''}:${(screen.hand ?? []).length}:${screen.actions?.length ?? ''}:${screen.acknowledged ?? ''}:${screen.value ?? ''}:${screen.selected ?? ''}:${screen.locked ?? ''}`
    if (paused || key === lastKey) return
    lastKey = key
    await sleep(700 + Math.random() * 2500)
    if (screen.t === 'tutorial' && !screen.acknowledged) act(round, { kind: 'ack' })
    if (screen.t === 'text' && screen.value == null) act(round, { kind: screen.kind, text: screen.kind === 'teamName' ? pick(teamNames) : pick(fakes) })
    if (screen.t === 'choice' && screen.options.length) {
      // Teammates tend to follow whoever voted first, like people do.
      const votes = Object.entries(screen.votes ?? {}).sort((a, b) => b[1].length - a[1].length)
      const follow = votes.length && Math.random() < 0.6 ? votes[0][0] : null
      if (screen.kind === 'team' && screen.selected) { if (screen.prompt.startsWith('Still')) act(round, { kind: 'team', option: screen.selected }) }
      else if (!screen.selected) act(round, { kind: screen.kind, option: follow ?? pick(screen.options).id })
    }
    if (screen.t === 'number' && screen.value == null) {
      const guesses = (screen.guesses ?? []).map((g) => g.value)
      const base = guesses.length ? guesses[0] : 10 ** (1 + Math.floor(Math.random() * 3))
      act(round, { kind: screen.kind, value: Math.max(1, Math.round(base * (0.6 + Math.random() * 0.8))) })
    }
    if (screen.t === 'multi' && !screen.locked) {
      const open = screen.options.filter((o) => !(screen.eliminated ?? []).includes(o.id))
      act(round, { kind: screen.kind, picks: open.filter(() => Math.random() < 0.6).map((o) => o.id), lock: true })
    }
    if (screen.t === 'cards' && screen.actions.length) {
      if (screen.kind === 'bet') act(round, { kind: 'bet', option: pick(screen.actions).id })
      else {
        const t = screen.total ?? 0
        const has = (id) => screen.actions.some((a) => a.id === id)
        const choice = has('double') && (t === 10 || t === 11) ? 'double' : !has('stand') || t < 16 ? 'hit' : t < 18 && Math.random() < 0.25 ? 'hit' : 'stand'
        act(round, { kind: 'move', option: choice })
      }
    }
  }

  // Home Turf keeps one round across auction bids and trades, so the act-once key is the prompt itself.
  let turfKey = ''
  async function turf(screen, round) {
    const p = screen.prompt, me = screen.me, pad = screen.auction
    const key = `${round}:${p.kind}:${p.title}:${pad?.top ?? ''}:${pad?.leading ?? ''}:${screen.trade?.id ?? ''}:${me?.cash ?? ''}:${screen.deeds.length}`
    if (key === turfKey) return
    turfKey = key
    await sleep(600 + Math.random() * 1800)
    const has = (id) => p.actions.some((a) => a.id === id)
    const cash = me?.cash ?? 0
    switch (p.kind) {
      case 'pieces': if (screen.pieces.length) act(round, { kind: 'piece', option: pick(screen.pieces).id }); break
      case 'roll': act(round, { kind: 'roll' }); break
      case 'jail': act(round, { kind: 'jail', option: has('card') ? 'card' : cash >= 400 ? 'pay' : 'roll' }); break
      case 'buy': act(round, { kind: 'buy', option: has('buy') && cash - p.amount >= 150 ? 'buy' : 'pass' }); break
      case 'bid': {
        // Raise $10-50 up to 1.5x the printed price while keeping $50 back.
        if (!pad?.canBid || pad.leading) break
        const amount = pad.top + 10 * (1 + Math.floor(Math.random() * 5))
        if (amount <= Math.min(pad.maxBid, pad.price * 1.5, cash - 50)) act(round, { kind: 'bid', auction: pad.auction, amount })
        break
      }
      case 'bus': case 'triples': act(round, { kind: 'choose', option: p.actions[0].id }); break
      case 'manage': {
        const build = screen.deeds.find((d) => d.build != null && cash - d.build >= 250)
        const payOff = screen.deeds.find((d) => d.unmortgage != null && cash - d.unmortgage >= 400)
        if (build) act(round, { kind: 'build', target: build.space })
        else if (payOff) act(round, { kind: 'unmortgage', target: payOff.space })
        else act(round, { kind: 'end' })
        break
      }
      case 'debt': {
        // Sell buildings, then mortgage singles before set members, then pay; bankrupt only when there's nothing left.
        const sell = screen.deeds.find((d) => d.sell != null)
        const mortgage = screen.deeds.filter((d) => d.mortgage != null).sort((a, b) => Number(a.set) - Number(b.set))[0]
        if (has('pay')) act(round, { kind: 'pay' })
        else if (sell) act(round, { kind: 'sell', target: sell.space })
        else if (mortgage) act(round, { kind: 'mortgage', target: mortgage.space })
        else act(round, { kind: 'bankrupt' })
        break
      }
      case 'trade':
        if (screen.trade) act(round, { kind: 'tradeReply', trade: screen.trade.id, option: Math.random() < 0.5 ? 'accept' : 'reject' })
        break
    }
  }
}
for (let i = 0; i < count; i++) bot(i)
console.log(`${count} bots joining room ${tv.room}. Ctrl+C to send them home.`)
