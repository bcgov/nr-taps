import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { User } from 'oidc-client-ts'

const oidc = vi.hoisted(() => ({
  getUser: vi.fn<() => Promise<User | null>>(),
  signinSilent: vi.fn<() => Promise<User | null>>(),
  removeUser: vi.fn<() => Promise<void>>(),
  signinRedirect: vi.fn<() => Promise<void>>(),
  signinRedirectCallback: vi.fn<() => Promise<User>>(),
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
    signinRedirect = oidc.signinRedirect
    signinRedirectCallback = oidc.signinRedirectCallback
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

test('shares one callback completion when React mounts the callback more than once', async () => {
  const callback = Promise.withResolvers<User>()
  oidc.signinRedirectCallback.mockReturnValue(callback.promise)
  const { completeLogin } = await import('@/service/oidc-service')

  const first = completeLogin()
  const second = completeLogin()
  callback.resolve(renewedUser)

  expect(await Promise.all([first, second])).toEqual([renewedUser, renewedUser])
  expect(oidc.signinRedirectCallback).toHaveBeenCalledOnce()
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

test('discards credentials written by a refresh that finishes after logout', async () => {
  let storedUser: User | null = expiringUser
  const refresh = Promise.withResolvers<User | null>()
  oidc.getUser.mockImplementation(async () => storedUser)
  oidc.removeUser.mockImplementation(async () => {
    storedUser = null
  })
  // oidc-client-ts writes the refreshed user before resolving signinSilent.
  oidc.signinSilent.mockImplementation(async () => {
    storedUser = await refresh.promise
    return storedUser
  })
  const { getOidcUser, logout } = await import('@/service/oidc-service')
  const pending = getOidcUser()
  await vi.waitFor(() => expect(oidc.signinSilent).toHaveBeenCalledOnce())

  await logout(vi.fn())
  refresh.resolve(renewedUser)

  await expect(pending).resolves.toBeNull()
  expect(storedUser).toBeNull()
  await expect(getOidcUser()).resolves.toBeNull()
})

test('does not renew an old storage read after logout', async () => {
  const storage = Promise.withResolvers<User | null>()
  oidc.getUser.mockReturnValueOnce(storage.promise).mockResolvedValueOnce(expiringUser)
  const { getOidcUser, logout } = await import('@/service/oidc-service')
  const pending = getOidcUser()

  await logout(vi.fn())
  storage.resolve(expiringUser)

  await expect(pending).resolves.toBeNull()
  expect(oidc.signinSilent).not.toHaveBeenCalled()
})

test('discards a callback that finishes after local session clearing', async () => {
  let storedUser: User | null = null
  const callback = Promise.withResolvers<User>()
  oidc.signinRedirectCallback.mockImplementation(async () => {
    storedUser = await callback.promise
    return storedUser
  })
  oidc.removeUser.mockImplementation(async () => {
    storedUser = null
  })
  const { completeLogin, clearLogin } = await import('@/service/oidc-service')
  const pending = completeLogin()
  const rejected = expect(pending).rejects.toThrow('session ended')

  await clearLogin()
  callback.resolve(renewedUser)

  await rejected
  expect(storedUser).toBeNull()
})

test('clears a session that expires without a refresh token', async () => {
  oidc.getUser.mockResolvedValue({ ...expiringUser, refresh_token: undefined } as User)
  const { getOidcUser } = await import('@/service/oidc-service')

  await expect(getOidcUser()).resolves.toBeNull()
  expect(oidc.removeUser).toHaveBeenCalledOnce()
  expect(oidc.signinSilent).not.toHaveBeenCalled()
})

test('shares sign out across simultaneous callers', async () => {
  const navigate = vi.fn<(url: string) => void>()
  const { logout } = await import('@/service/oidc-service')

  await Promise.all([logout(navigate), logout(navigate)])

  expect(oidc.removeUser).toHaveBeenCalledOnce()
  expect(navigate).toHaveBeenCalledOnce()
})

test('waits for an old renewal to be discarded before starting another login', async () => {
  const refresh = Promise.withResolvers<User | null>()
  oidc.signinSilent.mockReturnValue(refresh.promise)
  const { getOidcUser, logout, startLogin } = await import('@/service/oidc-service')
  const pending = getOidcUser()
  await vi.waitFor(() => expect(oidc.signinSilent).toHaveBeenCalledOnce())
  await logout(vi.fn())

  const login = startLogin('business-bceid')
  expect(oidc.signinRedirect).not.toHaveBeenCalled()
  refresh.resolve(renewedUser)
  await Promise.all([pending, login])

  expect(oidc.removeUser.mock.invocationCallOrder.at(-1)).toBeLessThan(
    oidc.signinRedirect.mock.invocationCallOrder[0],
  )
  expect(oidc.signinRedirect).toHaveBeenCalledWith({
    extraQueryParams: { kc_idp_hint: 'bceidbusiness' },
  })
})
