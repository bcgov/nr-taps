import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, renderRoute, screen, staffSession, userEvent } from '@/test-utils'

const oidc = vi.hoisted(() => ({
  AUTH_CALLBACK_PATH: '/authCallback',
  getOidcUser: vi.fn(),
  completeLogin: vi.fn(),
  startLogin: vi.fn(),
  logout: vi.fn(),
  clearLogin: vi.fn(),
  isOidcConfigured: vi.fn(),
}))
vi.mock('@/service/oidc-service', () => oidc)

const fetch = vi.fn()
const destinationKey = 'taps.login-destination'

beforeEach(() => {
  vi.resetAllMocks()
  window.sessionStorage.clear()
  vi.stubGlobal('fetch', fetch)
  oidc.isOidcConfigured.mockReturnValue(true)
  oidc.getOidcUser.mockResolvedValue({ access_token: 'callback-token' })
  fetch.mockResolvedValue({ ok: true, json: async () => staffSession })
})

afterEach(() => vi.unstubAllGlobals())

test('waits for the login callback before loading and synchronizing the shared session', async () => {
  const callback = Promise.withResolvers<void>()
  oidc.completeLogin.mockReturnValue(callback.promise)
  const { router } = await renderRoute('/authCallback')

  expect(await screen.findByRole('status')).toHaveTextContent('Completing sign in')
  expect(oidc.getOidcUser).not.toHaveBeenCalled()
  expect(fetch).not.toHaveBeenCalled()
  await act(async () => callback.resolve())

  expect(await screen.findByText('Signed in as TAPS User.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'ECAS' })).toBeInTheDocument()
  expect(router.state.location.pathname).toBe('/')
  expect(fetch).toHaveBeenCalledOnce()
  expect(fetch).toHaveBeenCalledWith('/api/me', {
    headers: { Authorization: 'Bearer callback-token' },
  })
})

test('returns a signed-out deep link to the same page after sign in', async () => {
  const user = userEvent.setup()
  const deepLink = '/ecas/ECAS05?ecasId=1001#results'
  oidc.getOidcUser.mockResolvedValue(null)
  oidc.startLogin.mockReturnValue(new Promise(() => {}))
  const signedOut = await renderRoute(deepLink)

  await user.click(await screen.findByRole('button', { name: 'Sign in with Business BCeID' }))
  expect(oidc.startLogin).toHaveBeenCalledWith('business-bceid')
  expect(window.sessionStorage.getItem(destinationKey)).toBe(deepLink)
  signedOut.unmount()

  oidc.getOidcUser.mockResolvedValue({ access_token: 'callback-token' })
  oidc.completeLogin.mockResolvedValue(undefined)
  const { router } = await renderRoute('/authCallback?code=synthetic&state=synthetic')

  expect(await screen.findByRole('heading', { name: 'Inbox Search' })).toBeInTheDocument()
  expect(router.state.location.href).toBe(deepLink)
  expect(router.history.length).toBe(1)
  expect(window.sessionStorage.getItem(destinationKey)).toBeNull()
})

test.each(['//example.com/ecas', '/\\example.com/ecas', 'https://example.com/ecas'])(
  'goes home instead of the unsafe stored destination %s',
  async (destination) => {
    window.sessionStorage.setItem(destinationKey, destination)
    oidc.completeLogin.mockResolvedValue(undefined)
    const { router } = await renderRoute('/authCallback')

    expect(await screen.findByText('Signed in as TAPS User.')).toBeInTheDocument()
    expect(router.state.location.href).toBe('/')
    expect(window.sessionStorage.getItem(destinationKey)).toBeNull()
  },
)

test('can return home and sign in again after a callback error', async () => {
  const user = userEvent.setup()
  oidc.completeLogin.mockRejectedValue(new Error('Invalid callback'))
  oidc.getOidcUser.mockResolvedValue(null)
  window.sessionStorage.setItem(destinationKey, '/ecas')
  const { router } = await renderRoute('/authCallback')

  expect(await screen.findByRole('alert')).toHaveTextContent('Sign in could not be completed')
  expect(fetch).not.toHaveBeenCalled()
  expect(window.sessionStorage.getItem(destinationKey)).toBeNull()
  await user.click(screen.getByRole('link', { name: 'Return to home' }))

  expect(await screen.findByRole('button', { name: 'Sign in with IDIR' })).toBeInTheDocument()
  expect(router.state.location.pathname).toBe('/')
})

test('keeps the completed login available for retry when the session API is temporarily unavailable', async () => {
  const user = userEvent.setup()
  oidc.completeLogin.mockResolvedValue(undefined)
  fetch
    .mockResolvedValueOnce({ ok: false, status: 503 })
    .mockResolvedValueOnce({ ok: true, json: async () => staffSession })

  await renderRoute('/authCallback')

  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load your session')
  expect(oidc.clearLogin).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByRole('link', { name: 'ECAS' })).toBeInTheDocument()
})
