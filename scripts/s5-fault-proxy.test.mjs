import test from 'node:test'
import assert from 'node:assert/strict'
import net from 'node:net'
import { startFaultProxy } from './s5-fault-proxy.mjs'

test('transparent loopback proxy preserves bytes, refuses unauthorized control, cuts and restores existing connections', async () => {
  const upstream = net.createServer(s => s.pipe(s))
  await new Promise(r => upstream.listen(0, '127.0.0.1', r))
  const token = 'synthetic-local-test-control-token'
  const events = []
  const proxy = await startFaultProxy({ token, targets: { mysql: { host: '127.0.0.1', port: upstream.address().port } }, onEvent: e => events.push(e) })
  const url = `http://127.0.0.1:${proxy.controlPort}`
  const send = () => new Promise((resolve, reject) => {
    const s = net.createConnection({ host: '127.0.0.1', port: proxy.snapshot().mysql.port })
    s.setTimeout(2000, () => s.destroy(Error('timeout')))
    s.on('error', reject); s.once('connect', () => s.write('binary\x00payload'))
    s.once('data', data => { s.destroy(); resolve(data.toString()) })
  })
  try {
    assert.equal(await send(), 'binary\x00payload')
    assert.equal((await fetch(url + '/mysql/cut', { method: 'POST' })).status, 403)
    assert.equal(proxy.snapshot().mysql.enabled, true)
    const control = action => fetch(url + '/mysql/' + action, { method: 'POST', headers: { authorization: `Bearer ${token}` } })
    const held = net.createConnection({ host: '127.0.0.1', port: proxy.snapshot().mysql.port })
    held.on('error', () => {})
    await new Promise(r => held.once('connect', r))
    const closed = new Promise(r => held.once('close', r))
    assert.equal((await control('cut')).status, 200); await closed
    assert.equal(proxy.snapshot().mysql.enabled, false)
    const denied = net.createConnection({ host: '127.0.0.1', port: proxy.snapshot().mysql.port }); denied.on('error', () => {})
    await new Promise(r => denied.once('close', r))
    assert.equal((await control('restore')).status, 200)
    assert.equal(await send(), 'binary\x00payload')
    assert.deepEqual(events.map(e => e.action), ['cut', 'restore'])
  } finally { await proxy.close(); await new Promise(r => upstream.close(r)) }
})

test('proxy refuses non-loopback upstream and weak control token', async () => {
  await assert.rejects(startFaultProxy({ token: 'short', targets: {} }), /token/)
  await assert.rejects(startFaultProxy({ token: 'synthetic-test-control-token', targets: { mysql: { host: 'example.com', port: 3306 } } }), /loopback/)
})
