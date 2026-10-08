// Test-only OIDC issuer for the local rehearsal. Don't deploy it.
import { createServer } from 'node:http'
import { createHash, generateKeyPairSync, randomBytes, sign } from 'node:crypto'
import { pathToFileURL } from 'node:url'

const random = () => randomBytes(24).toString('base64url')
const encoded = (value) => Buffer.from(JSON.stringify(value)).toString('base64url')
const personas = {
  admin: { label: 'Provincial administrator', role: 'TAPS_ADMIN' },
  cariboo: { label: 'Cariboo appraiser', role: 'TAPS_REGION_APPRAISER_REGION-CARIBOO' },
  omineca: { label: 'Omineca appraiser', role: 'TAPS_REGION_APPRAISER_REGION-OMINECA' },
  licensee: { label: 'Licensee viewer', role: 'TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00000001', provider: 'bceidbusiness' },
  no_role: { label: 'Signed in without a TAPS role' },
  expired: { label: 'Expired access token (expected sign-in failure)', role: 'TAPS_ADMIN', expired: true },
}

export function createLocalReadIssuer({ issuer, clientId = 'taps-local-read', clock = Date.now }) {
  const location = new URL(issuer)
  if (location.protocol !== 'http:' || location.pathname !== '/oidc' ||
      !['localhost', 'taps.localhost', '127.0.0.1'].includes(location.hostname)) {
    throw new Error('The synthetic issuer requires a local HTTP /oidc URL')
  }
  const origin = location.origin
  const redirectUri = `${origin}/authCallback`
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
  const kid = random()
  const jwk = { ...publicKey.export({ format: 'jwk' }), kid, use: 'sig', alg: 'RS256' }
  const pending = new Map()
  const codes = new Map()
  const refreshTokens = new Map()
  const jwt = (claims) => {
    const input = `${encoded({ alg: 'RS256', typ: 'JWT', kid })}.${encoded(claims)}`
    return `${input}.${sign('RSA-SHA256', Buffer.from(input), privateKey).toString('base64url')}`
  }
  const response = (res, status, body, type = 'application/json') => {
    res.writeHead(status, { 'Content-Type': type, 'Cache-Control': 'no-store' })
    res.end(type === 'application/json' ? JSON.stringify(body) : body)
  }
  const tokens = ({ persona, nonce, authTime }) => {
    const now = Math.floor(clock() / 1000)
    const authenticatedAt = authTime ?? now
    const identity = personas[persona]
    const common = { iss: issuer, sub: `synthetic-${persona}`, aud: clientId, iat: now, exp: now + 300 }
    const refresh = random()
    refreshTokens.set(refresh, { persona, nonce, authTime: authenticatedAt, expires: clock() + 3600000 })
    return {
      token_type: 'Bearer', expires_in: 300, scope: 'openid profile email', refresh_token: refresh,
      access_token: jwt({ ...common, exp: identity.expired ? now - 120 : now + 300,
        azp: clientId, typ: 'Bearer', identity_provider: identity.provider ?? 'azureidir',
        idir_username: persona, bceid_username: persona, display_name: `Synthetic ${identity.label}`,
        client_roles: identity.role ? [identity.role] : [] }),
      id_token: jwt({ ...common, nonce, auth_time: authenticatedAt, sid: `synthetic-${persona}`,
        name: `Synthetic ${identity.label}` }),
    }
  }
  async function body(req) {
    let text = ''
    for await (const chunk of req) {
      text += chunk
      if (text.length > 16384) throw new Error('body too large')
    }
    return new URLSearchParams(text)
  }
  const server = createServer(async (req, res) => {
    try {
      const url = new URL(req.url, issuer)
      const path = url.pathname
      if (req.method === 'GET' && path === '/oidc/.well-known/openid-configuration') {
        return response(res, 200, {
          issuer, authorization_endpoint: `${issuer}/authorize`, token_endpoint: `${issuer}/token`,
          jwks_uri: `${issuer}/protocol/openid-connect/certs`, end_session_endpoint: `${issuer}/logout`,
          response_types_supported: ['code'], subject_types_supported: ['public'],
          id_token_signing_alg_values_supported: ['RS256'], token_endpoint_auth_methods_supported: ['none'],
          code_challenge_methods_supported: ['S256'], scopes_supported: ['openid', 'profile', 'email'],
        })
      }
      if (req.method === 'GET' && path === '/oidc/protocol/openid-connect/certs') return response(res, 200, { keys: [jwk] })
      if (req.method === 'GET' && path === '/oidc/authorize') {
        const params = url.searchParams
        if (params.get('client_id') !== clientId || params.get('redirect_uri') !== redirectUri ||
            params.get('response_type') !== 'code' || params.get('code_challenge_method') !== 'S256' ||
            !/^[A-Za-z0-9_-]{43}$/.test(params.get('code_challenge') ?? '') || !params.get('state')) {
          return response(res, 400, { error: 'invalid_request' })
        }
        const id = random()
        pending.set(id, { challenge: params.get('code_challenge'), state: params.get('state'),
          nonce: params.get('nonce') ?? undefined, expires: clock() + 300000 })
        return response(res, 200, `<!doctype html><html lang="en"><meta charset="utf-8"><title>TAPS local test sign-in</title>
          <h1>TAPS local test sign-in</h1><p>Disposable synthetic accounts only. No real credentials.</p>
          <form method="post" action="/oidc/authorize"><input type="hidden" name="request" value="${id}">
          <label for="persona">Test persona</label><select name="persona" id="persona">${Object.entries(personas)
            .map(([key, value]) => `<option value="${key}">${value.label}</option>`).join('')}</select>
          <button type="submit">Sign in to local TAPS</button></form></html>`, 'text/html; charset=utf-8')
      }
      if (req.method === 'POST' && path === '/oidc/authorize') {
        const params = await body(req)
        const id = params.get('request')
        const authorization = pending.get(id)
        pending.delete(id)
        if (!authorization || authorization.expires < clock() || !personas[params.get('persona')]) {
          return response(res, 400, { error: 'invalid_request' })
        }
        const code = random()
        codes.set(code, { ...authorization, persona: params.get('persona'), expires: clock() + 60000 })
        const target = new URL(redirectUri)
        target.searchParams.set('code', code)
        target.searchParams.set('state', authorization.state)
        res.writeHead(303, { Location: target.toString(), 'Cache-Control': 'no-store' })
        return res.end()
      }
      if (req.method === 'POST' && path === '/oidc/token') {
        const params = await body(req)
        if (params.get('client_id') !== clientId) return response(res, 400, { error: 'invalid_client' })
        if (params.get('grant_type') === 'authorization_code') {
          const code = params.get('code')
          const authorization = codes.get(code)
          codes.delete(code)
          const verifier = params.get('code_verifier') ?? ''
          const challenge = createHash('sha256').update(verifier).digest('base64url')
          if (!authorization || authorization.expires < clock() || !/^[A-Za-z0-9._~-]{43,128}$/.test(verifier) || params.get('redirect_uri') !== redirectUri || challenge !== authorization.challenge) {
            return response(res, 400, { error: 'invalid_grant' })
          }
          return response(res, 200, tokens(authorization))
        }
        if (params.get('grant_type') === 'refresh_token') {
          const refresh = params.get('refresh_token')
          const authorization = refreshTokens.get(refresh)
          refreshTokens.delete(refresh)
          if (!authorization || authorization.expires < clock()) return response(res, 400, { error: 'invalid_grant' })
          return response(res, 200, tokens(authorization))
        }
        return response(res, 400, { error: 'unsupported_grant_type' })
      }
      if (req.method === 'GET' && (path === '/oidc/logout' || path === '/oidc/protocol/openid-connect/logout')) {
        const target = url.searchParams.get('post_logout_redirect_uri')
        if (target !== origin && target !== `${origin}/`) return response(res, 400, { error: 'invalid_request' })
        refreshTokens.clear()
        res.writeHead(303, { Location: target, 'Cache-Control': 'no-store' })
        return res.end()
      }
      response(res, 404, { error: 'not_found' })
    } catch {
      response(res, 400, { error: 'invalid_request' })
    }
  })
  return server
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const issuer = process.env.LOCAL_READ_ISSUER ?? 'http://taps.localhost:13000/oidc'
  const server = createLocalReadIssuer({ issuer })
  // No host port is published; this listener belongs only to the disposable Docker network.
  server.listen(18281, '0.0.0.0', () => console.log('Synthetic OIDC issuer ready'))
  process.on('SIGTERM', () => server.close(() => process.exit(0)))
}
