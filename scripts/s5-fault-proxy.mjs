import net from 'node:net'
import http from 'node:http'
import { readFile, writeFile } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'

export async function startFaultProxy({ targets, token, onEvent = () => {} }) {
  if (!token || token.length < 24) throw Error('Control token required')
  const states = new Map()
  for (const [name, target] of Object.entries(targets)) {
    if (target.host !== '127.0.0.1' || !Number.isInteger(target.port)) throw Error('Only explicit loopback upstreams allowed')
    const state = { enabled: true, sockets: new Set(), accepted: 0, refused: 0, target }
    const server = net.createServer(client => {
      state.accepted++
      if (!state.enabled) { state.refused++; client.destroy(); return }
      const upstream = net.createConnection(target)
      state.sockets.add(client); state.sockets.add(upstream)
      for (const [socket, peer] of [[client, upstream], [upstream, client]]) {
        socket.on('error', () => { socket.destroy(); peer.destroy() })
        socket.on('close', () => { state.sockets.delete(socket); peer.destroy() })
      }
      client.pipe(upstream); upstream.pipe(client)
    })
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve) })
    state.server = server; state.port = server.address().port; states.set(name, state)
  }
  const snapshot = () => Object.fromEntries([...states].map(([name, s]) => [name, { port: s.port, enabled: s.enabled, connections: s.sockets.size / 2, accepted: s.accepted, refused: s.refused }]))
  const control = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'application/json')
    if (req.headers.authorization !== `Bearer ${token}`) { res.writeHead(403); res.end('{}'); return }
    if (req.method === 'GET' && req.url === '/state') { res.end(JSON.stringify(snapshot())); return }
    const match = /^\/(mysql|rabbit)\/(cut|restore)$/.exec(req.url ?? '')
    if (req.method !== 'POST' || !match || !states.has(match[1])) { res.writeHead(404); res.end('{}'); return }
    const state = states.get(match[1]); state.enabled = match[2] === 'restore'
    if (!state.enabled) for (const socket of state.sockets) socket.destroy()
    onEvent({ at: new Date().toISOString(), target: match[1], action: match[2] })
    res.end(JSON.stringify(snapshot()))
  })
  await new Promise((resolve, reject) => { control.once('error', reject); control.listen(0, '127.0.0.1', resolve) })
  return { controlPort: control.address().port, snapshot, async close() {
    const closing = [...states.values()].map(s => { for (const socket of s.sockets) socket.destroy(); return new Promise(r => s.server.close(r)) })
    closing.push(new Promise(r => control.close(r))); await Promise.all(closing)
  } }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [configFile, stateFile] = process.argv.slice(2)
  const config = JSON.parse((await readFile(configFile, 'utf8')).replace(/^\uFEFF/, ''))
  const proxy = await startFaultProxy({ ...config, onEvent: e => console.log(JSON.stringify(e)) })
  await writeFile(stateFile, JSON.stringify({ pid: process.pid, controlPort: proxy.controlPort, targets: proxy.snapshot() }))
  process.on('SIGTERM', async () => { await proxy.close(); process.exit(0) })
}
