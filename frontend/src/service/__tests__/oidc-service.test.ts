import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { User } from 'oidc-client-ts'

const oidc = vi.hoisted(() => ({
  getUser: vi.fn<() => Promise<User | null>>(),
  signinSilent: vi.fn<() => Promise<User | null>>(),
}))

vi.mock('oidc-client-ts', () => ({
  UserManager: class {
    getUser = oidc.getUser
    signinSilent = oidc.signinSilent
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

test('shares a failed refresh and allows a subsequent attempt', async () => {
  const error = new Error('Session renewal failed')
  oidc.signinSilent.mockRejectedValueOnce(error).mockResolvedValueOnce(renewedUser)
  const { getOidcUser } = await import('@/service/oidc-service')

  expect(await Promise.allSettled([getOidcUser(), getOidcUser()])).toEqual([
    { status: 'rejected', reason: error },
    { status: 'rejected', reason: error },
  ])
  expect(oidc.signinSilent).toHaveBeenCalledTimes(1)

  await expect(getOidcUser()).resolves.toBe(renewedUser)
  expect(oidc.signinSilent).toHaveBeenCalledTimes(2)
})

test('uses a valid access token without refreshing it', async () => {
  oidc.getUser.mockResolvedValue(renewedUser)
  const { getOidcUser } = await import('@/service/oidc-service')

  await expect(getOidcUser()).resolves.toBe(renewedUser)
  expect(oidc.signinSilent).not.toHaveBeenCalled()
})
