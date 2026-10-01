import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { renderRoute, screen, staffSession, userEvent } from '@/test-utils'

const oidc = vi.hoisted(() => ({
  AUTH_CALLBACK_PATH: '/authCallback',
  getOidcUser: vi.fn(),
  isOidcConfigured: vi.fn(),
  startLogin: vi.fn(),
  logout: vi.fn(),
  clearLogin: vi.fn(),
  completeLogin: vi.fn(),
}))
vi.mock('@/service/oidc-service', () => oidc)

const fetch = vi.fn()

function withCapabilities(capabilities: string[]) {
  fetch.mockResolvedValue({
    ok: true,
    json: async () => ({ ...staffSession, capabilities }),
  })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.stubGlobal('fetch', fetch)
  oidc.getOidcUser.mockResolvedValue({ access_token: 'test-token' })
  oidc.isOidcConfigured.mockReturnValue(true)
  withCapabilities(staffSession.capabilities)
})

afterEach(() => vi.unstubAllGlobals())

test('shows separate application entry points only when a server capability allows them', async () => {
  withCapabilities(['ECAS_SUBMISSION_VIEW'])

  await renderRoute()

  expect(await screen.findByRole('link', { name: 'ECAS' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'GAS' })).not.toBeInTheDocument()
})

test('opens the ECAS inbox and profile shells from its own navigation', async () => {
  const user = userEvent.setup()
  withCapabilities(['ECAS_SUBMISSION_VIEW'])
  const { router } = await renderRoute('/ecas')

  expect(await screen.findByRole('link', { name: 'Inbox Search' })).toHaveAttribute(
    'href',
    '/ecas/ECAS05',
  )
  expect(screen.getByRole('link', { name: 'Your profile' })).toHaveAttribute('href', '/ecas/ECAS88')
  expect(screen.queryByRole('link', { name: 'Appraisal Search' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('link', { name: 'Inbox Search' }))

  expect(await screen.findByRole('heading', { name: 'Inbox Search' })).toBeInTheDocument()
  expect(router.state.location.pathname).toBe('/ecas/ECAS05')
  expect(screen.getByRole('status')).toHaveTextContent('This page is being modernized')
})

test('does not expose ministry worksheet pages to a client-report-only session', async () => {
  withCapabilities(['GAS_CLIENT_REPORTS'])

  await renderRoute('/gas')

  expect(await screen.findByRole('heading', { name: 'GAS' })).toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent('Your GAS pages will appear here')
  expect(screen.queryByRole('link', { name: 'Appraisal Search' })).not.toBeInTheDocument()
  expect(
    screen.queryByRole('link', { name: 'Summary - Appraised Worksheet' }),
  ).not.toBeInTheDocument()
})

test.each([
  ['/ecas/ECAS05', 'Inbox Search'],
  ['/ecas/ECAS88', 'Your profile'],
  ['/gas/showAppraisalSearch', 'Appraisal Search'],
  ['/gas/showAppraisedSummary', 'Summary - Appraised Worksheet'],
  ['/gas/showNonAppraisedSummary', 'Summary - Non-Appraised Rates'],
])('opens the authorized shell at %s without fetching business data', async (path, title) => {
  await renderRoute(path)

  expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent('It is not available yet.')
  expect(fetch).toHaveBeenCalledOnce()
  expect(fetch.mock.calls[0][0]).toBe('/api/me')
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
})

test.each(['/ecas', '/ecas/ECAS05', '/gas', '/gas/showAppraisalSearch'])(
  'requires sign in on direct navigation to %s',
  async (path) => {
    oidc.getOidcUser.mockResolvedValue(null)

    await renderRoute(path)

    expect(await screen.findByRole('button', { name: 'Sign in with IDIR' })).toBeInTheDocument()
    expect(
      screen.queryByText('This page is being modernized. It is not available yet.'),
    ).not.toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()
  },
)

test.each(['/ecas', '/ecas/ECAS05', '/gas', '/gas/showAppraisalSearch'])(
  'denies no-role users on direct navigation to %s',
  async (path) => {
    fetch.mockResolvedValue({ ok: true, json: async () => ({ ...staffSession, roles: [] }) })

    await renderRoute(path)

    expect(await screen.findByText(/You do not have TAPS access yet/)).toBeInTheDocument()
    expect(
      screen.queryByText('This page is being modernized. It is not available yet.'),
    ).not.toBeInTheDocument()
  },
)

test.each([
  '/gas/showAppraisalSearch',
  '/gas/showAppraisedSummary',
  '/gas/showNonAppraisedSummary',
])('denies worksheet deep links without the appraisal view capability: %s', async (path) => {
  withCapabilities(['GAS_CLIENT_REPORTS'])

  await renderRoute(path)

  expect(await screen.findByRole('alert')).toHaveTextContent('You do not have access to this page.')
  expect(
    screen.queryByText('This page is being modernized. It is not available yet.'),
  ).not.toBeInTheDocument()
})

test('denies ECAS deep links when the session only grants GAS access', async () => {
  withCapabilities(['GAS_APPRAISAL_VIEW'])

  await renderRoute('/ecas/ECAS05')

  expect(await screen.findByRole('alert')).toHaveTextContent('You do not have access to this page.')
})

test('returns 404 for screens outside the small verified catalogue', async () => {
  await renderRoute('/ecas/showAppraisalSearch')

  expect(await screen.findByRole('heading', { name: '404' })).toBeInTheDocument()
})
