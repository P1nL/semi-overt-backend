import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve, join } from 'node:path'
import { pathToFileURL } from 'node:url'
import vm from 'node:vm'

// Validate retained REAL gateway responses through the actual demo frontend's adapters.
// This is not a browser acceptance replacement and does not contact production.
const [runArg, frontendArg = 'D:/works/semi-overt-frontend'] = process.argv.slice(2)
assert.ok(runArg, 'Usage: node scripts/s4-frontend-contract.mjs <S4 acceptance run> [demo frontend]')
const run = resolve(runArg)
const frontend = resolve(frontendArg)
assert.match(frontend.replaceAll('\\', '/'), /\/semi-overt-frontend$/i, 'Only isolated demo frontend is allowed')
const ts = (await import(pathToFileURL(join(frontend, 'node_modules/typescript/lib/typescript.js')).href)).default
class BusinessError extends Error {}
async function load(relative, dependencies) {
  const source = await readFile(join(frontend, relative), 'utf8')
  const exports = {}
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  vm.runInNewContext(code, { exports, require: name => {
    assert.ok(name in dependencies, `Undeclared runtime dependency ${name}`)
    return dependencies[name]
  }, Date, Number, String, Object, Error })
  return exports
}
const contract = await load('src/shared/api/contract.ts', {
  '@/shared/types/api': { ApiBusinessError: BusinessError },
  '@/shared/utils/dateTime': { normalizeBackendDateTime: value => value ?? null },
})
const adapters = await load('src/shared/api/adapters.ts', {
  '@/shared/types/api': {},
  '@/shared/utils/asset': { resolveAssetUrl: value => value ?? null },
  '@/shared/utils/dateTime': { normalizeBackendDateTime: value => value ?? null },
  './contract': contract,
})
const json = async name => JSON.parse((await readFile(join(run, name), 'utf8')).replace(/^\uFEFF/, ''))
const p1 = await json('package1-wire.json')
const publicProfile = adapters.normalizeUserProfileResp(p1.public)
const owner = adapters.normalizeUserProfileResp(p1.owner)
assert.equal(publicProfile.profile.id, 101)
assert.equal('email' in publicProfile.profile, false)
assert.equal(publicProfile.stats.draft, 0)
assert.equal(publicProfile.writingCalendar.length, 1)
assert.ok(publicProfile.list.every(article => article.status === 'APPROVED'))
assert.equal(owner.stats.draft, 1)
assert.equal(owner.writingCalendar.length, 2)
assert.equal(owner.list.length, p1.owner.list.length, 'No silent adapter dropping')
const home = adapters.normalizeHomeResp(p1.home)
assert.equal(home.sections.length, 3)
assert.equal(home.hero.secondary.length, 10)
assert.equal(home.hero.primary.id, p1.home.hero.primary.id)
assert.ok(home.sections.every(section => section.list.every(article => article.status === 'APPROVED')))
let p2
try { p2 = await json('package2-wire.json') } catch (error) { if (error.code !== 'ENOENT') throw error }
for (const result of p2 ? [p2.indexed.data, p2.fallback.data] : []) {
  const search = adapters.normalizeSearchResp(result)
  assert.equal(search.total, 3)
  assert.equal(search.list.length, 3)
  assert.equal(search.list[0].id, 1001)
  assert.ok(search.list.every(article => article.status === 'APPROVED'))
}
console.log(JSON.stringify({ passed: true, run, frontend, scope: ['actual profile/calendar adapters', 'actual home adapter', ...(p2 ? ['actual search adapter'] : [])], browserAcceptance: false }))
