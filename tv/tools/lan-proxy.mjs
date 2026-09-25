#!/usr/bin/env node
// Forwards phone traffic on the Mac's Wi-Fi address to the Android emulator (via `adb forward`).
// Plain TCP piping, so HTTP and WebSocket upgrades both pass through untouched.
// Usage: node lan-proxy.mjs --listen 0.0.0.0:8080 --target 127.0.0.1:18080
import net from 'node:net'

const arg = (name, fallback) => {
  const i = process.argv.indexOf(`--${name}`)
  return i > 0 ? process.argv[i + 1] : fallback
}
const [lh, lp] = arg('listen', '0.0.0.0:8080').split(':')
const [th, tp] = arg('target', '127.0.0.1:18080').split(':')

const server = net.createServer((client) => {
  const upstream = net.connect(Number(tp), th)
  client.pipe(upstream).pipe(client)
  const done = () => { client.destroy(); upstream.destroy() }
  client.on('error', done)
  upstream.on('error', done)
})
server.on('error', (e) => { console.error(`lan-proxy: ${e.message}`); process.exit(1) })
server.listen(Number(lp), lh, () => console.log(`lan-proxy: ${lh}:${lp} -> ${th}:${tp}`))
