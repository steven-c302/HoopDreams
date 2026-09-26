#!/usr/bin/env node
// Rehearsal bots: join the party like phones do and play whatever is on their screen.
// Usage: node controller/scripts/bots.mjs [count=5] [http://127.0.0.1:8080]
const count = Number(process.argv[2] ?? 5)
const base = process.argv[3] ?? 'http://127.0.0.1:8080'
const names = ['Ava', 'Ben', 'Cleo', 'Dev', 'Eli', 'Fin', 'Gus', 'Hana', 'Ivy', 'Jay', 'Kai', 'Lu', 'Mo', 'Nia', 'Oz', 'Pip']
const emojis = ['🦊', '🐸', '🐙', '🦄', '🐼', '🐯', '🦉', '🐝', '🐧', '🦖', '🐨', '🍕', '🌮', '🎸', '🚀', '👾']
const colors = ['#FF7A00', '#22AA55', '#8E5CFF', '#FF4D8D', '#2EC4F1', '#FFD23F', '#3DDC97', '#FF5A5A']
const fakes = ['a very old goat', 'the moon', 'spaghetti', 'Belgium', 'four', 'a rubber duck', 'Nicolas Cage', 'soup', 'jazz', 'the year 1812']
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const pick = (a) => a[Math.floor(Math.random() * a.length)]

const tv = await fetch(`${base}/api/tv/session`).then((r) => r.json())
async function bot(i) {
  await sleep(i * 350)
  const r = await fetch(`${base}/api/join`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ room: tv.room, name: names[i % 16] + (i >= 16 ? i : ''), avatar: { emoji: emojis[i % 16], color: colors[i % 8] }, spectator: false }) })
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
    const key = `${round}:${screen.t}:${screen.kind ?? ''}:${(screen.hand ?? []).length}:${screen.actions?.length ?? ''}:${screen.acknowledged ?? ''}:${screen.value ?? ''}:${screen.selected ?? ''}`
    if (paused || key === lastKey) return
    lastKey = key
    await sleep(700 + Math.random() * 2500)
    if (screen.t === 'tutorial' && !screen.acknowledged) act(round, { kind: 'ack' })
    if (screen.t === 'text' && screen.value == null) act(round, { kind: screen.kind, text: pick(fakes) })
    if (screen.t === 'choice' && !screen.selected && screen.options.length) act(round, { kind: screen.kind, option: pick(screen.options).id })
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
}
for (let i = 0; i < count; i++) bot(i)
console.log(`${count} bots joining room ${tv.room}. Ctrl+C to send them home.`)
