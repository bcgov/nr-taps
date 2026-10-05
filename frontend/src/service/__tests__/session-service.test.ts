import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { SESSION_EXPIRED_EVENT } from '@/context/auth/session-expiry'
import { fetchSession, SessionUnavailableError } from '@/service/session-service'

const oidc = vi.hoisted(() => ({ getOidcUser: vi.fn(), clearLogin: vi.fn() }))
vi.mock('@/service/oidc-service', () => oidc)

const fetch = vi.fn()

beforeEach(() => {
  vi.resetAllMocks()
  vi.stubGlobal('fetch', fetch)
  oidc.getOidcUser.mockResolvedValue({ access_token: 'test-token' })
})

afterEach(() => vi.unstubAllGlobals())

test('does not call the API when no usable OIDC session exists', async () => {
  oidc.getOidcUser.mockResolvedValue(null)

  await expect(fetchSession()).resolves.toBeNull()

  expect(fetch).not.toHaveBeenCalled()
})

test('uses the access token and returns server-decided roles and capabilities', async () => {
  const session = {
    userId: 'staff-1',
    roles: [{ role: 'TAPS_VIEWER', scopes: [{ type: 'DISTRICT', value: 'DCR' }] }],
    capabilities: ['ECAS_SUBMISSION_VIEW'],
  }
  fetch.mockResolvedValue({ ok: true, json: async () => session })

  await expect(fetchSession()).resolves.toEqual(session)
  expect(fetch).toHaveBeenCalledWith('/api/me', {
    headers: { Authorization: 'Bearer test-token' },
  })
})

test('clears and reports a session the API rejects with 401', async () => {
  const expired = vi.fn()
  window.addEventListener(SESSION_EXPIRED_EVENT, expired)
  fetch.mockResolvedValue({ status: 401 })

  await expect(fetchSession()).resolves.toBeNull()
  expect(oidc.clearLogin).toHaveBeenCalledOnce()
  expect(expired).toHaveBeenCalledOnce()
  expect(expired.mock.calls[0][0]).toMatchObject({ detail: { reason: 'api-unauthorized' } })
  window.removeEventListener(SESSION_EXPIRED_EVENT, expired)
})

test.each([403, 500, 503])('keeps the stored session after an HTTP %s error', async (status) => {
  fetch.mockResolvedValue({ status, ok: false })

  await expect(fetchSession()).rejects.toBeInstanceOf(SessionUnavailableError)
  expect(oidc.clearLogin).not.toHaveBeenCalled()
})

test('keeps the stored session after a transport error', async () => {
  const error = new Error('Network unavailable')
  fetch.mockRejectedValue(error)

  await expect(fetchSession()).rejects.toBe(error)
  expect(oidc.clearLogin).not.toHaveBeenCalled()
})

test('keeps the stored session and does not call the API when renewal temporarily fails', async () => {
  const error = new Error('SSO temporarily unavailable')
  oidc.getOidcUser.mockRejectedValue(error)

  await expect(fetchSession()).rejects.toBe(error)
  expect(fetch).not.toHaveBeenCalled()
  expect(oidc.clearLogin).not.toHaveBeenCalled()
})
