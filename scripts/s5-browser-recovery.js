async (page) => {
  const check = (pass, message) => { if (!pass) throw Error(message) }
  check(page.url().startsWith('http://127.0.0.1:15173/'), 'Only local demo allowed')
  const results = []
  const runtimeState = () => page.evaluate(async () => {
    const runtime = await import('/src/shared/api/authRuntime.ts')
    return { tokenPresent: Boolean(runtime.getAccessToken()), pendingLogout: runtime.hasPendingLogout() }
  })
  check((await runtimeState()).tokenPresent, 'A real UI login must precede fault injection')
  for (const fault of ['network', 429, 503]) {
    let refreshCalls = 0, originalCalls = 0
    await page.route('**/api/v1/users/me', async route => {
      originalCalls++
      await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 401, message: 'Synthetic expired access token', data: null }) })
    })
    await page.route('**/api/v1/auth/refresh', async route => {
      refreshCalls++
      // Make all three concurrent callers observe the same pending refresh.
      await page.waitForTimeout(150)
      if (fault === 'network') return route.abort('connectionrefused')
      await route.fulfill({ status: fault, contentType: 'application/json', headers: { 'Retry-After': '1' }, body: JSON.stringify({ code: fault, message: 'Synthetic transient refresh failure', data: null }) })
    })
    try {
      const returned = await page.evaluate(async () => {
        const { http } = await import('/src/shared/api/http.ts')
        const attempts = await Promise.allSettled([http.get('/users/me'), http.get('/users/me'), http.get('/users/me')])
        return attempts.map(attempt => ({ status: attempt.status, code: attempt.status === 'rejected' ? attempt.reason?.code : null }))
      })
      const after = await runtimeState()
      check(returned.every(x => x.status === 'rejected'), `${fault}: requests must fail, not fake success`)
      check(refreshCalls === 1 && originalCalls === 3, `${fault}: no request storm (${refreshCalls}/${originalCalls})`)
      check(after.tokenPresent && !after.pendingLogout, `${fault}: transient error cleared the real session`)
      results.push({ fault, injection: 'browser protocol fault; not provider outage', originalCalls, refreshCalls, preservedSession: true })
    } finally {
      await page.unroute('**/api/v1/users/me'); await page.unroute('**/api/v1/auth/refresh')
    }
    const restored = await page.evaluate(async () => {
      const runtime = await import('/src/shared/api/authRuntime.ts')
      await runtime.refreshAccessToken()
      const { http } = await import('/src/shared/api/http.ts')
      const me = await http.get('/users/me')
      return { authenticated: Boolean(runtime.getAccessToken()), code: me.data.code, userId: me.data.data.id ?? me.data.data.userId }
    })
    check(restored.authenticated && restored.code === 200 && restored.userId === 101, `${fault}: real backend recovery failed`)
    results[results.length - 1].realBackendRecovered = true
  }
  const peer = await page.context().newPage()
  try {
    await peer.goto('http://127.0.0.1:15173/')
    await peer.waitForFunction(async () => Boolean((await import('/src/shared/api/authRuntime.ts')).getAccessToken()))
    const refresh = p => p.evaluate(async () => {
      const runtime = await import('/src/shared/api/authRuntime.ts'); const session = await runtime.refreshAccessToken()
      return Boolean(session.token)
    })
    const refreshed = await Promise.all([refresh(page), refresh(peer)])
    check(refreshed.every(Boolean), 'Two real tabs must serialize rotating cookie through Web Locks')
    results.push({ scenario: 'two-real-tabs', bothRefreshed: true, server: 'real auth instances, shared cookies' })
  } finally { await peer.close() }
  // Definitive refresh denial must still reach the actual app unauthorized handler.
  await page.route('**/api/v1/users/me', r => r.fulfill({ status: 401, contentType: 'application/json', body: '{"code":401,"message":"Synthetic invalid session","data":null}' }))
  await page.route('**/api/v1/auth/refresh', r => r.fulfill({ status: 401, contentType: 'application/json', body: '{"code":401,"message":"Synthetic revoked refresh","data":null}' }))
  try {
    await page.evaluate(async () => { const { http } = await import('/src/shared/api/http.ts'); try { await http.get('/users/me') } catch {} })
    const after = await runtimeState(); check(!after.tokenPresent, 'Definitive 401 must invalidate memory session')
    results.push({ scenario: 'definitive-401', memorySessionCleared: true })
  } finally { await page.unroute('**/api/v1/users/me'); await page.unroute('**/api/v1/auth/refresh') }
  return { passed: true, scope: 'S5 recovery browser only', results }
}
