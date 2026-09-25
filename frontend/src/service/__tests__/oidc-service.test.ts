import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { User } from 'oidc-client-ts'

const oidc = vi.hoisted(() => ({
  getUser: vi.fn<() => Promise<User | null>>(),
  signinSilent: vi.fn<() => Promise<User | null>>(),
  removeUser: vi.fn<() => Promise<void>>(),
  ErrorResponse: class extends Error {
    error: string
    constructor(error: string) {
      super(error)
      this.error = error
    }
  },
}))

vi.mock('oidc-client-ts', () => ({
  ErrorResponse: oidc.ErrorResponse,
  UserManager: class {
    getUser = oidc.getUser
    signinSilent = oidc.signinSilent
    removeUser = oidc.removeUser
  },
  WebStorageStateStore: class {},
}))

const expiringUser = {
  access_token: 'test-access-token',
  refresh_token: 'test-refresh-token',
  expired: false,
  expires_in: 30,
} as User
const renewedUser = { ...expiringUser, access_token: 'renewed-test-token', expires_in: 300 } as User

beforeEach(() => {
  vi.resetModules()
  vi.resetAllMocks()
  window.config = {
    VITE_OIDC_ISSUER_URI: 'https://example.invalid/realms/taps',
    VITE_OIDC_CLIENT_ID: 'taps-test',
  }
  oidc.getUser.mockResolvedValue(expiringUser)
})

afterEach(() => {
  delete window.config
})

test('shares one refresh across concurrent storage reads and an in-flight caller', async () => {
  const refresh = Promise.withResolvers<User | null>()
  oidc.signinSilent.mockReturnValue(refresh.promise)
  const { getOidcUser } = await import('@/service/oidc-service')

  const first = getOidcUser()
  const second = getOidcUser()
  await vi.waitFor(() => expect(oidc.signinSilent).toHaveBeenCalled())
  const third = getOidcUser()
  refresh.resolve(renewedUser)

  expect(await Promise.all([first, second, third])).toEqual([renewedUser, renewedUser, renewedUser])
  expect(oidc.getUser).toHaveBeenCalledTimes(2)
  expect(oidc.signinSilent).toHaveBeenCalledTimes(1)
})

test('signs out when SSO rejects the refresh token', async () => {
  oidc.signinSilent.mockRejectedValue(new oidc.ErrorResponse('invalid_grant'))
  const { getOidcUser } = await import('@/service/oidc-service')

  expect(await Promise.all([getOidcUser(), getOidcUser()])).toEqual([null, null])
  expect(oidc.signinSilent).toHaveBeenCalledTimes(1)
  expect(oidc.removeUser).toHaveBeenCalledTimes(1)
})

test('keeps the session when SSO reports a temporary error', async () => {
  const error = new oidc.ErrorResponse('server_error')
  oidc.signinSilent.mockRejectedValueOnce(error).mockResolvedValueOnce(renewedUser)
  const { getOidcUser } = await import('@/service/oidc-service')

  await expect(getOidcUser()).rejects.toBe(error)
  expect(oidc.removeUser).not.toHaveBeenCalled()
  await expect(getOidcUser()).resolves.toBe(renewedUser)
})

test('shares a failed refresh and allows a subsequent attempt', async () => {
  const error = new Error('Session renewal failed')
  oidc.signinSilent.mockRejectedValueOnce(error).mockResolvedValueOnce(renewedUser)
  const { getOidcUser } = await import('@/service/oidc-service')

  expect(await Promise.allSettled([getOidcUser(), getOidcUser()])).toEqual([
    { status: 'rejected', reason: error },
    { status: 'rejected', reason: error },
  ])
  expect(oidc.signinSilent).toHaveBeenCalledTimes(1)
  expect(oidc.removeUser).not.toHaveBeenCalled()

  await expect(getOidcUser()).resolves.toBe(renewedUser)
  expect(oidc.signinSilent).toHaveBeenCalledTimes(2)
})

test('uses a valid access token without refreshing it', async () => {
  oidc.getUser.mockResolvedValue(renewedUser)
  const { getOidcUser } = await import('@/service/oidc-service')

  await expect(getOidcUser()).resolves.toBe(renewedUser)
  expect(oidc.signinSilent).not.toHaveBeenCalled()
})

test('logs off SiteMinder before ending the Keycloak session', async () => {
  window.config = {
    ...window.config,
    VITE_OIDC_SITEMINDER_LOGOUT_URL: 'https://logontest7.gov.bc.ca/clp-cgi/logoff.cgi',
  }
  oidc.getUser.mockResolvedValue({ ...renewedUser, id_token: 'test-id-token' } as User)
  const navigate = vi.fn<(url: string) => void>()
  const { logout } = await import('@/service/oidc-service')

  await logout(navigate)

  expect(oidc.removeUser.mock.invocationCallOrder[0]).toBeLessThan(
    navigate.mock.invocationCallOrder[0],
  )
  const siteminder = new URL(navigate.mock.calls[0][0])
  expect(`${siteminder.origin}${siteminder.pathname}`).toBe(
    'https://logontest7.gov.bc.ca/clp-cgi/logoff.cgi',
  )
  expect(siteminder.searchParams.get('retnow')).toBe('1')
  const keycloak = new URL(siteminder.searchParams.get('returl') ?? '')
  expect(`${keycloak.origin}${keycloak.pathname}`).toBe(
    'https://example.invalid/realms/taps/protocol/openid-connect/logout',
  )
  expect(Object.fromEntries(keycloak.searchParams)).toEqual({
    client_id: 'taps-test',
    post_logout_redirect_uri: window.location.origin,
    id_token_hint: 'test-id-token',
  })
})

test('ends only the Keycloak session when SiteMinder logoff is not configured', async () => {
  const navigate = vi.fn<(url: string) => void>()
  const { logout } = await import('@/service/oidc-service')

  await logout(navigate)

  const keycloak = new URL(navigate.mock.calls[0][0])
  expect(`${keycloak.origin}${keycloak.pathname}`).toBe(
    'https://example.invalid/realms/taps/protocol/openid-connect/logout',
  )
  expect(keycloak.searchParams.get('client_id')).toBe('taps-test')
  expect(oidc.removeUser).toHaveBeenCalled()
})
