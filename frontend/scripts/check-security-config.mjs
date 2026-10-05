import assert from 'node:assert/strict'
import { spawn, execFileSync } from 'node:child_process'
import { once } from 'node:events'
import { mkdtemp, mkdir, readFile, writeFile, rm } from 'node:fs/promises'
import { createServer } from 'node:http'
import { tmpdir } from 'node:os'
import { resolve, join } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'
import { fileURLToPath } from 'node:url'

// Exercise the deployed Caddy/Coraza configuration with synthetic requests only.
// Supply the binary built by frontend/Dockerfile (or an equivalent native build).
const caddy = process.env.CADDY_BIN || 'caddy'
const frontend = resolve(fileURLToPath(new URL('..', import.meta.url)))
const temp = await mkdtemp(join(tmpdir(), 'taps-security-'))
const srv = join(temp, 'srv')
let caddyProcess
let logs = ''
const requests = []
const credentialMarkers = {
  cookie: 'wava-cookie-marker',
  authorization: 'wava-authorization-marker',
  proxyAuthorization: 'wava-proxy-authorization-marker',
  setCookie: 'wava-response-cookie-marker',
  apiKey: 'wava-api-key-marker',
  location: 'wava-redirect-code-marker',
}
const backend = createServer((request, response) => {
  requests.push({ url: request.url, headers: request.headers })
  if (request.url.startsWith('/api/disconnect')) {
    request.socket.destroy()
    return
  }
  const status = Number(new URL(request.url, 'http://localhost').searchParams.get('status') || 200)
  response.writeHead(status, {
    'Content-Type': 'application/pdf',
    'Content-Disposition': 'attachment; filename="fixture.pdf"',
    Server: 'synthetic-upstream',
    'Referrer-Policy': 'same-origin',
    'Set-Cookie': `SYNTHETIC=${credentialMarkers.setCookie}; HttpOnly; SameSite=Lax`,
    Location: `/login?code=${credentialMarkers.location}`,
  })
  response.end('%PDF-1.4\nsynthetic report fixture\n%%EOF')
})

const listen = async (server) => {
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')
  return server.address().port
}

try {
  console.log(execFileSync(caddy, ['version'], { encoding: 'utf8' }).trim())
  const modules = execFileSync(caddy, ['list-modules', '--versions'], { encoding: 'utf8' })
  assert.match(modules, /http\.handlers\.waf v2\.5\.0/)
  const backendPort = await listen(backend)
  const reserved = [createServer(), createServer(), createServer()]
  const [appPort, healthPort, adminPort] = await Promise.all(reserved.map(listen))
  await Promise.all(reserved.map((server) => new Promise((done) => server.close(done))))
  await mkdir(join(srv, 'assets'), { recursive: true })
  await mkdir(join(temp, 'coraza'))
  await writeFile(join(srv, 'index.html'), '<!doctype html><title>TAPS security fixture</title>')
  await writeFile(join(srv, 'assets', 'fixture-123.js'), 'export const fixture = true')
  await writeFile(join(srv, 'config.js'), 'window.config = {}')
  const coraza = (await readFile(join(frontend, 'coraza.conf'), 'utf8')).replaceAll(
    '/tmp/coraza/',
    join(temp, 'coraza') + '/',
  )
  // Exercise Coraza's ERROR callback as well as the production rules' warning callbacks.
  // Severity must never determine whether the original URI can bypass log filtering.
  const criticalProbeRule = `
SecRule REQUEST_URI "@beginsWith /waf-critical-probe" "id:990001,phase:1,deny,status:403,log,severity:CRITICAL,msg:'Synthetic critical WAF rule'"
`
  await writeFile(join(temp, 'coraza.conf'), coraza + criticalProbeRule)
  const config = (await readFile(join(frontend, 'Caddyfile'), 'utf8'))
    .replace('\tmetrics', '\tdefault_bind 127.0.0.1\n\tmetrics')
    .replace('127.0.0.1:3003', `127.0.0.1:${adminPort}`)
    .replace(':3000 {', `http://127.0.0.1:${appPort} {`)
    .replace(':3001 {', `http://127.0.0.1:${healthPort} {`)
    .replace('/etc/caddy/coraza.conf', join(temp, 'coraza.conf'))
    .replace('root * /srv', `root * ${srv}`)
  const configPath = join(temp, 'Caddyfile')
  await writeFile(configPath, config)
  const env = {
    ...process.env,
    LOG_LEVEL: 'INFO',
    BACKEND_URL: `http://127.0.0.1:${backendPort}`,
    XDG_DATA_HOME: join(temp, 'data'),
    XDG_CONFIG_HOME: join(temp, 'config'),
  }
  execFileSync(caddy, ['validate', '--config', configPath], { env, stdio: 'pipe' })
  caddyProcess = spawn(caddy, ['run', '--config', configPath], {
    env,
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  caddyProcess.stdout.on('data', (data) => {
    logs += data
  })
  caddyProcess.stderr.on('data', (data) => {
    logs += data
  })
  const origin = `http://127.0.0.1:${appPort}`
  let ready = false
  for (let attempt = 0; attempt < 100; attempt++) {
    if (caddyProcess.exitCode !== null) throw new Error(logs)
    try {
      ready = (await fetch(`http://127.0.0.1:${healthPort}/health`)).ok
      if (ready) break
    } catch {
      /* Wait only for the local process to bind its sockets. */
    }
    await delay(50)
  }
  assert.ok(ready, 'Caddy did not become ready')

  const check = async (path, status, options = {}) => {
    const response = await fetch(origin + path, options)
    assert.equal(response.status, status, path)
    assert.equal(response.headers.get('server'), null, path)
    assert.equal(response.headers.get('referrer-policy'), 'no-referrer', path)
    assert.equal(response.headers.get('cross-origin-embedder-policy'), 'require-corp', path)
    assert.equal(response.headers.get('cross-origin-opener-policy'), 'same-origin', path)
    assert.equal(response.headers.get('x-frame-options'), 'SAMEORIGIN', path)
    assert.match(response.headers.get('content-security-policy'), /frame-ancestors 'self'/, path)
    assert.match(response.headers.get('content-security-policy'), /script-src 'self';/, path)
    assert.match(
      response.headers.get('content-security-policy'),
      /connect-src 'self' https:\/\/loginproxy\.gov\.bc\.ca https:\/\/\*\.loginproxy\.gov\.bc\.ca;/,
      path,
    )
    assert.equal(response.headers.get('x-content-type-options'), 'nosniff', path)
    await response.arrayBuffer()
    return response
  }

  await check('/ecas', 200)
  await check('/ecas/ECAS05', 200)
  await check('/config.js', 200)
  const asset = await check('/assets/fixture-123.js', 200)
  assert.match(asset.headers.get('cache-control'), /immutable/)
  const missingAsset = await check('/assets/missing.js', 404)
  assert.equal(missingAsset.headers.get('cache-control'), 'no-store')
  for (const path of ['/.git/', '/.ssh/', '/backup/', '/config/', '/logs/', '/tmp/']) {
    await check(path, 403)
  }
  for (const status of [200, 401, 403, 422, 500]) {
    const response = await check(`/api/fixture?status=${status}`, status)
    assert.match(response.headers.get('content-disposition'), /attachment/)
  }
  await check('/api/fixture?filter=' + encodeURIComponent("O'Connor Forestry & Co."), 200)
  await check('/api/fixture', 200, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ comment: '<p>Appraisal note</p>', volume: 123.45 }),
  })
  for (const payload of ['<script>alert(1)</script>', '../private-file', '\u0000']) {
    await check('/api/fixture?filter=' + encodeURIComponent(payload), 403)
  }

  const markers = [
    'wava-code-marker',
    'wava-state-marker',
    'wava-business-marker',
    'wava-csrf-marker',
    ...Object.values(credentialMarkers),
  ]
  const query = `?code=${markers[0]}&state=${markers[1]}&clientNumber=${markers[2]}`
  const headers = {
    Referer: origin + '/' + query,
    'X-XSRF-TOKEN': markers[3],
    Cookie: `SYNTHETIC=${credentialMarkers.cookie}`,
    Authorization: `Bearer ${credentialMarkers.authorization}`,
    'Proxy-Authorization': `Bearer ${credentialMarkers.proxyAuthorization}`,
    'X-Api-Key': credentialMarkers.apiKey,
  }
  await check('/' + query, 200, { headers })
  const credentialResponse = await check('/api/fixture' + query, 200, { headers })
  assert.ok(
    credentialResponse.headers.get('set-cookie')?.includes(credentialMarkers.setCookie),
    'Log filtering must preserve response cookies sent to the client',
  )
  assert.equal(
    credentialResponse.headers.get('location'),
    `/login?code=${credentialMarkers.location}`,
    'Log filtering must preserve redirect locations sent to the client',
  )
  await check('/.git/' + query, 403, { headers })
  await check('/waf-critical-probe' + query, 403, { headers })
  await check('/api/disconnect' + query, 502, { headers })
  await check('/api/fixture' + query + '&filter=' + encodeURIComponent("' OR 1=1-- "), 403, {
    headers,
  })
  assert.ok(
    requests.some((request) => request.url === '/api/fixture' + query),
    'Log filtering must preserve query parameters sent to the backend',
  )
  assert.ok(
    requests.some((request) => request.headers['x-xsrf-token'] === markers[3]),
    'Log filtering must preserve the CSRF header sent to the backend',
  )
  for (const header of ['Cookie', 'Authorization', 'X-Api-Key']) {
    assert.ok(
      requests.some((request) => request.headers[header.toLowerCase()] === headers[header]),
      `Log filtering must preserve the ${header} header sent to the backend`,
    )
  }
  assert.match(
    logs,
    /--[a-zA-Z0-9]+-K--/,
    'WAF denials must still produce matched-rule audit events',
  )
  assert.match(logs, /id:1004/, 'WAF audit events must retain rule IDs')
  assert.match(logs, /id:990001/, 'Critical WAF events must retain safe audit diagnostics')
  assert.match(
    logs,
    /Access to sensitive path blocked/,
    'WAF audit events must retain static messages',
  )

  await delay(100)
  const auditSections = [...logs.matchAll(/--[a-zA-Z0-9]+-([A-Z])--\n/g)].map((match) => match[1])
  assert.deepEqual([...new Set(auditSections)].sort(), ['A', 'K', 'Z'])
  const auditRecords = [
    ...logs.matchAll(/--([a-zA-Z0-9]+)-A--\n([^\n]*)\n--\1-K--\n[\s\S]*?--\1-Z--/g),
  ]
  assert.equal(auditRecords.length, auditSections.filter((section) => section === 'A').length)
  for (const [, , header] of auditRecords) {
    assert.match(
      header,
      /^\[[^\]\r\n]+\] [a-zA-Z0-9]+ [\da-fA-F.:]+ \d+ [\da-fA-F.:]* \d+$/,
      'Native audit part A must contain only timestamp, transaction ID and connection metadata',
    )
  }
  for (const marker of markers)
    assert.ok(!logs.includes(marker), `Sensitive marker in logs: ${marker}`)
  assert.doesNotMatch(
    logs,
    /"(?:headers|resp_headers)"\s*:/,
    'Header maps must be omitted, including custom credentials and redirect locations',
  )
  assert.doesNotMatch(logs, /http\.handlers\.waf/, 'Raw WAF messages must not reach any logger')
  for (const status of [200, 403, 502]) {
    assert.match(
      logs,
      new RegExp(`http\\.log\\.access[^\\n]+"status":\\s*${status}\\b`),
      `Access diagnostics must retain HTTP ${status} without the raw WAF logger`,
    )
  }
  console.log('PASS: headers, WAF denials, SPA/assets, proxy statuses/downloads, and log redaction')
} catch (error) {
  // Requests contain synthetic test values only; keep failures diagnosable.
  console.error(logs)
  throw error
} finally {
  if (caddyProcess && caddyProcess.exitCode === null) {
    caddyProcess.kill('SIGTERM')
    await once(caddyProcess, 'exit')
  }
  backend.closeAllConnections()
  await new Promise((done) => backend.close(done))
  await rm(temp, { recursive: true, force: true })
}
