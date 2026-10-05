import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createHash, createPublicKey, randomBytes, verify } from 'node:crypto'
import { createLocalReadIssuer } from './local-read-issuer.mjs'

const issuer = 'http://taps.localhost:13000/oidc'
const clientId = 'taps-local-read'
const redirectUri = 'http://taps.localhost:13000/authCallback'

async function fixture(context, options = {}) {
  const server = createLocalReadIssuer({ issuer, ...options })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  context.after(() => new Promise((resolve) => server.close(resolve)))
  const base = `http://127.0.0.1:${server.address().port}`
  const request = (path, init) => fetch(base + path, { redirect: 'manual', ...init })
  const post = (path, fields) => request(path, { method: 'POST', body: new URLSearchParams(fields) })
  const authorize = async (persona = 'cariboo') => {
    const verifier = randomBytes(32).toString('base64url')
    const query = new URLSearchParams({ client_id: clientId, redirect_uri: redirectUri,
      response_type: 'code', state: 'synthetic-state', nonce: 'synthetic-nonce',
      code_challenge_method: 'S256', code_challenge: createHash('sha256').update(verifier).digest('base64url') })
    const authorization = await request(`/oidc/authorize?${query}`)
    assert.equal(authorization.status, 200)
    const html = await authorization.text()
    const requestId = html.match(/name="request" value="([A-Za-z0-9_-]+)"/)[1]
    const result = await post('/oidc/authorize', { request: requestId, persona })
    assert.equal(result.status, 303)
    const target = new URL(result.headers.get('location'))
    assert.equal(target.origin + target.pathname, redirectUri)
    assert.equal(target.searchParams.get('state'), 'synthetic-state')
    return { verifier, code: target.searchParams.get('code') }
  }
  const exchange = ({ verifier, code }) => post('/oidc/token', { grant_type: 'authorization_code',
    client_id: clientId, redirect_uri: redirectUri, code, code_verifier: verifier })
  return { request, post, authorize, exchange }
}

function claims(token) { return JSON.parse(Buffer.from(token.split('.')[1], 'base64url')) }

test('discovery, PKCE code flow, ephemeral signature, role claims and refresh rotation', async (context) => {
  const api = await fixture(context)
  const metadata = await (await api.request('/oidc/.well-known/openid-configuration')).json()
  assert.equal(metadata.issuer, issuer)
  assert.deepEqual(metadata.code_challenge_methods_supported, ['S256'])
  const exchange = await api.authorize()
  const result = await api.exchange(exchange)
  assert.equal(result.status, 200)
  const tokens = await result.json()
  const { keys } = await (await api.request('/oidc/protocol/openid-connect/certs')).json()
  const parts = tokens.access_token.split('.')
  assert.equal(verify('RSA-SHA256', Buffer.from(parts.slice(0, 2).join('.')),
    createPublicKey({ key: keys[0], format: 'jwk' }), Buffer.from(parts[2], 'base64url')), true)
  assert.equal(keys[0].d, undefined)
  assert.equal(claims(tokens.id_token).nonce, 'synthetic-nonce')
  assert.deepEqual(claims(tokens.access_token).client_roles, ['TAPS_REGION_APPRAISER_REGION-CARIBOO'])
  assert.equal(claims(tokens.access_token).azp, clientId)
  assert.equal((await api.exchange(exchange)).status, 400)
  const refresh = { client_id: clientId, grant_type: 'refresh_token', refresh_token: tokens.refresh_token }
  assert.equal((await api.post('/oidc/token', refresh)).status, 200)
  assert.equal((await api.post('/oidc/token', refresh)).status, 400)
})

test('wrong redirect, missing PKCE and incorrect verifier are rejected', async (context) => {
  const api = await fixture(context)
  assert.equal((await api.request('/oidc/authorize?client_id=taps-local-read')).status, 400)
  const exchange = await api.authorize()
  assert.equal((await api.exchange({ ...exchange, verifier: 'wrong-verifier' })).status, 400)
  assert.equal((await api.exchange(exchange)).status, 400)
})

test('expired access fixture is genuinely expired and logout restricts its destination', async (context) => {
  const api = await fixture(context)
  const result = await api.exchange(await api.authorize('expired'))
  const tokens = await result.json()
  assert.ok(claims(tokens.access_token).exp < Math.floor(Date.now() / 1000) - 60)
  assert.ok(claims(tokens.id_token).exp > Math.floor(Date.now() / 1000))
  assert.equal((await api.request('/oidc/logout?post_logout_redirect_uri=https://example.invalid')).status, 400)
  const logout = await api.request('/oidc/logout?post_logout_redirect_uri=http%3A%2F%2Ftaps.localhost%3A13000')
  assert.equal(logout.status, 303)
  assert.equal((await api.post('/oidc/token', { grant_type: 'refresh_token', client_id: clientId,
    refresh_token: tokens.refresh_token })).status, 400)
})

test('synthetic issuer refuses nonlocal issuer identities', () => {
  assert.throws(() => createLocalReadIssuer({ issuer: 'https://loginproxy.gov.bc.ca/oidc' }), /local HTTP/)
})


test('refresh advances token lifetime while retaining original ID token session claims', async (context) => {
  let time = Date.now()
  const api = await fixture(context, { clock: () => time })
  const original = await (await api.exchange(await api.authorize())).json()
  const initial = claims(original.id_token)
  time += 120000
  const response = await api.post('/oidc/token', { grant_type: 'refresh_token', client_id: clientId,
    refresh_token: original.refresh_token })
  assert.equal(response.status, 200)
  const renewed = claims((await response.json()).id_token)
  for (const claim of ['sub', 'auth_time', 'azp', 'nonce', 'sid', 'iss', 'aud']) {
    assert.equal(renewed[claim], initial[claim], `refresh changed ${claim}`)
  }
  assert.equal(renewed.iat, initial.iat + 120)
  assert.equal(renewed.exp, initial.exp + 120)
})
