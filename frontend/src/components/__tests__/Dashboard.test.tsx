import { beforeEach, afterEach, vi } from 'vitest'
import { renderRoute, screen, staffSession, userEvent } from '@/test-utils'

const oidc = vi.hoisted(() => ({
  AUTH_CALLBACK_PATH: '/authCallback',
  getOidcUser: vi.fn(),
  startLogin: vi.fn(),
  completeLogin: vi.fn(),
  logout: vi.fn(),
  clearLogin: vi.fn(),
  isOidcConfigured: vi.fn(),
}))

vi.mock('@/service/oidc-service', () => oidc)

beforeEach(() => {
  vi.resetAllMocks()
  oidc.isOidcConfigured.mockReturnValue(true)
  oidc.getOidcUser.mockResolvedValue(null)
})

afterEach(() => vi.unstubAllGlobals())

test.each(['idir', 'business-bceid'] as const)(
  'offers the %s FAM provider without requiring a role',
  async (provider) => {
    const user = userEvent.setup()
    await renderRoute()

    const name = provider === 'idir' ? 'Sign in with IDIR' : 'Sign in with Business BCeID'
    await user.click(await screen.findByRole('button', { name }))

    expect(oidc.startLogin).toHaveBeenCalledWith(provider)
  },
)

test('loads identity and access from the protected API using the FAM access token', async () => {
  oidc.getOidcUser.mockResolvedValue({ access_token: 'test-token' })
  const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => staffSession })
  vi.stubGlobal('fetch', fetch)

  await renderRoute()

  expect(await screen.findByText('Signed in as TAPS User.')).toBeInTheDocument()
  expect(screen.getByText('District appraiser (district DCR)')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'ECAS' })).toHaveAttribute('href', '/ecas')
  expect(screen.getByRole('link', { name: 'GAS' })).toHaveAttribute('href', '/gas')
  expect(fetch).toHaveBeenCalledWith('/api/me', {
    headers: { Authorization: 'Bearer test-token' },
  })
})

test('removes an invalid session when the API rejects it', async () => {
  oidc.getOidcUser.mockResolvedValue({ access_token: 'expired-token' })
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 401 }))

  await renderRoute()

  expect(await screen.findByRole('button', { name: 'Sign in with IDIR' })).toBeInTheDocument()
  expect(oidc.clearLogin).toHaveBeenCalledOnce()
})

test('offers retry and local sign out after a transient service error', async () => {
  const user = userEvent.setup()
  oidc.getOidcUser.mockResolvedValue({ access_token: 'test-token' })
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce({ status: 503, ok: false })
      .mockResolvedValueOnce({ ok: true, json: async () => staffSession }),
  )

  await renderRoute()

  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load your session')
  expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  expect(oidc.clearLogin).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByRole('link', { name: 'ECAS' })).toBeInTheDocument()
})

test('denies a signed-in user with no roles even if capabilities are present in the response', async () => {
  oidc.getOidcUser.mockResolvedValue({ access_token: 'test-token' })
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ...staffSession, roles: [] }) }),
  )

  await renderRoute()

  expect(await screen.findByText(/You do not have TAPS access yet/)).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'ECAS' })).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'GAS' })).not.toBeInTheDocument()
})

test('explains when local sign in is not configured', async () => {
  oidc.isOidcConfigured.mockReturnValue(false)

  await renderRoute()

  expect(
    await screen.findByText('Sign in is not configured for this environment.'),
  ).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Sign in with IDIR' })).not.toBeInTheDocument()
})
