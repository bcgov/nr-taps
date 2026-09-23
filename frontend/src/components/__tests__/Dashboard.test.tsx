import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, afterEach, vi } from 'vitest'
import Dashboard from '@/components/Dashboard'

const oidc = vi.hoisted(() => ({
  getOidcUser: vi.fn(),
  startLogin: vi.fn(),
  logout: vi.fn(),
  clearLogin: vi.fn(),
  isOidcConfigured: vi.fn(),
}))

vi.mock('@/service/oidc-service', () => oidc)

beforeEach(() => {
  vi.clearAllMocks()
  oidc.isOidcConfigured.mockReturnValue(true)
  oidc.getOidcUser.mockResolvedValue(null)
})

afterEach(() => vi.unstubAllGlobals())

test('offers both FAM providers without requiring a role', async () => {
  const user = userEvent.setup()
  render(<Dashboard />)

  await user.click(await screen.findByRole('button', { name: 'Sign in with IDIR' }))
  await user.click(screen.getByRole('button', { name: 'Sign in with Business BCeID' }))

  expect(oidc.startLogin).toHaveBeenCalledWith('idir')
  expect(oidc.startLogin).toHaveBeenCalledWith('business-bceid')
})

test('loads identity from the protected API using the FAM access token', async () => {
  oidc.getOidcUser.mockResolvedValue({ access_token: 'test-token' })
  const fetch = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ subject: 'user-123', name: 'TAPS User' }),
  })
  vi.stubGlobal('fetch', fetch)

  render(<Dashboard />)

  expect(await screen.findByText('Signed in as TAPS User.')).toBeInTheDocument()
  expect(fetch).toHaveBeenCalledWith('/api/me', {
    headers: { Authorization: 'Bearer test-token' },
  })
})

test('removes an invalid session when the API rejects it', async () => {
  oidc.getOidcUser.mockResolvedValue({ access_token: 'expired-token' })
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 401 }))

  render(<Dashboard />)

  expect(await screen.findByRole('button', { name: 'Sign in with IDIR' })).toBeInTheDocument()
  expect(oidc.clearLogin).toHaveBeenCalled()
})
