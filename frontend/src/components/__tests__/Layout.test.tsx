import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, renderRoute, screen, staffSession, userEvent, waitFor, within } from '@/test-utils'
import { setViewportWidth } from '@/test-setup'
import { notifySessionExpired } from '@/context/auth/session-expiry'

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
const collapsedPreference = 'taps.ui.sideNavCollapsed'

beforeEach(() => {
  vi.resetAllMocks()
  localStorage.clear()
  vi.stubGlobal('fetch', fetch)
  oidc.getOidcUser.mockResolvedValue({ access_token: 'test-token' })
  oidc.isOidcConfigured.mockReturnValue(true)
  fetch.mockResolvedValue({ ok: true, json: async () => staffSession })
})

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

test.each([
  { path: '/ecas/ECAS05', application: 'ECAS', title: 'Inbox Search', otherApplication: 'GAS' },
  {
    path: '/gas/showAppraisalSearch',
    application: 'GAS',
    title: 'Appraisal Search',
    otherApplication: 'ECAS',
  },
])('expands the active application and marks its page on deep link $path', async (entry) => {
  await renderRoute(entry.path)
  await screen.findByRole('heading', { name: entry.title })
  const navigation = within(screen.getByRole('navigation', { name: 'Side navigation' }))

  expect(navigation.getByRole('button', { name: entry.application })).toHaveAttribute(
    'aria-expanded',
    'true',
  )
  expect(navigation.getByRole('button', { name: entry.otherApplication })).toHaveAttribute(
    'aria-expanded',
    'false',
  )
  expect(navigation.getByRole('link', { name: entry.title })).toHaveAttribute(
    'aria-current',
    'page',
  )
  expect(navigation.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
  expect(
    navigation.getByRole('link', { name: `${entry.application} overview` }),
  ).not.toHaveAttribute('aria-current')
  expect(
    screen
      .getByRole('navigation', { name: 'Side navigation' })
      .querySelectorAll('a[aria-current="page"]'),
  ).toHaveLength(1)
  expect(fetch).toHaveBeenCalledOnce()
})

test('updates active navigation and opens the destination group when moving between applications', async () => {
  const user = userEvent.setup()
  const { router } = await renderRoute('/ecas/ECAS05')
  await screen.findByRole('heading', { name: 'Inbox Search' })
  const navigation = within(screen.getByRole('navigation', { name: 'Side navigation' }))
  await user.click(navigation.getByRole('button', { name: 'GAS' }))
  await user.click(navigation.getByRole('link', { name: 'Appraisal Search' }))

  await screen.findByRole('heading', { name: 'Appraisal Search' })
  expect(router.state.location.pathname).toBe('/gas/showAppraisalSearch')
  expect(navigation.getByRole('button', { name: 'GAS' })).toHaveAttribute('aria-expanded', 'true')
  expect(navigation.getByRole('button', { name: 'ECAS' })).toHaveAttribute('aria-expanded', 'false')
  expect(navigation.getByRole('link', { name: 'Appraisal Search' })).toHaveAttribute(
    'aria-current',
    'page',
  )
})

test.each([false, true])(
  'gates application groups and page links by capability with desktop collapsed=%s',
  async (collapsed) => {
    localStorage.setItem(collapsedPreference, String(collapsed))
    fetch.mockResolvedValue({
      ok: true,
      json: async () => ({ ...staffSession, capabilities: ['GAS_CLIENT_REPORTS'] }),
    })
    const user = userEvent.setup()
    await renderRoute()
    const navigation = within(await screen.findByRole('navigation', { name: 'Side navigation' }))

    expect(navigation.queryByRole('button', { name: 'ECAS' })).not.toBeInTheDocument()
    expect(navigation.getByRole('button', { name: 'GAS' })).toHaveAttribute(
      'aria-expanded',
      'false',
    )
    expect(
      navigation.queryByRole('link', { name: 'Appraisal Search', hidden: true }),
    ).not.toBeInTheDocument()
    expect(
      navigation.queryByRole('link', { name: 'Inbox Search', hidden: true }),
    ).not.toBeInTheDocument()
    await user.click(navigation.getByRole('button', { name: 'GAS' }))

    expect(navigation.getByRole('link', { name: 'GAS overview' })).toHaveAttribute('href', '/gas')
    expect(navigation.queryByRole('link', { name: 'Appraisal Search' })).not.toBeInTheDocument()
    expect(
      navigation.queryByRole('link', { name: 'Summary - Appraised Worksheet' }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Close menu' })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
  },
)

test.each(['signed-out', 'no-role'])(
  'omits application navigation for a %s session on a protected deep link',
  async (kind) => {
    localStorage.setItem(collapsedPreference, 'true')
    if (kind === 'signed-out') oidc.getOidcUser.mockResolvedValue(null)
    else fetch.mockResolvedValue({ ok: true, json: async () => ({ ...staffSession, roles: [] }) })
    await renderRoute('/gas/showAppraisalSearch')
    if (kind === 'signed-out') await screen.findByRole('button', { name: 'Log in with IDIR' })
    else await screen.findByText(/You do not have TAPS access yet/)

    expect(screen.queryByRole('navigation', { name: 'Side navigation' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Open menu' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Close menu' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'ECAS' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'GAS' })).not.toBeInTheDocument()
  },
)

test('persists desktop collapse and exposes the active application in the icon rail', async () => {
  const user = userEvent.setup()
  const { unmount } = await renderRoute('/ecas/ECAS05')
  await screen.findByRole('heading', { name: 'Inbox Search' })
  await user.click(screen.getByRole('button', { name: 'Close menu' }))

  expect(localStorage.getItem(collapsedPreference)).toBe('true')
  const navigation = screen.getByRole('navigation', { name: 'Side navigation' })
  expect(navigation).toHaveClass('is-collapsed')
  expect(within(navigation).getByRole('button', { name: 'ECAS' })).toHaveAttribute(
    'aria-current',
    'true',
  )
  expect(within(navigation).getByRole('button', { name: 'ECAS' })).toHaveAttribute(
    'aria-description',
    'Contains current page: Inbox Search',
  )
  expect(within(navigation).queryByRole('link', { name: 'Inbox Search' })).not.toBeInTheDocument()

  unmount()
  await renderRoute('/ecas/ECAS05')
  await screen.findByRole('heading', { name: 'Inbox Search' })
  expect(screen.getByRole('button', { name: 'Open menu' })).toHaveAttribute(
    'aria-expanded',
    'false',
  )
  await user.click(
    within(screen.getByRole('navigation', { name: 'Side navigation' })).getByRole('button', {
      name: 'ECAS',
    }),
  )

  expect(localStorage.getItem(collapsedPreference)).toBe('false')
  expect(
    within(screen.getByRole('navigation', { name: 'Side navigation' })).getByRole('link', {
      name: 'Inbox Search',
    }),
  ).toHaveAttribute('aria-current', 'page')
})

test.each([false, true])(
  'keeps mobile open and close independent from the persisted desktop collapsed=%s preference',
  async (collapsed) => {
    setViewportWidth(1440)
    localStorage.setItem(collapsedPreference, String(collapsed))
    const user = userEvent.setup()
    const { unmount } = await renderRoute()
    await screen.findByRole('navigation', { name: 'Side navigation' })
    expect(
      screen.getByRole('button', { name: collapsed ? 'Open menu' : 'Close menu' }),
    ).toHaveAttribute('aria-expanded', String(!collapsed))

    act(() => setViewportWidth(375))
    await user.click(screen.getByRole('button', { name: 'Open menu' }))
    expect(screen.getByRole('button', { name: 'Close navigation' })).toBeInTheDocument()
    expect(localStorage.getItem(collapsedPreference)).toBe(String(collapsed))
    await user.click(screen.getByRole('button', { name: 'Close navigation' }))
    expect(screen.getByRole('button', { name: 'Open menu' })).toHaveAttribute(
      'aria-expanded',
      'false',
    )
    expect(localStorage.getItem(collapsedPreference)).toBe(String(collapsed))

    act(() => setViewportWidth(1440))
    expect(
      screen.getByRole('button', { name: collapsed ? 'Open menu' : 'Close menu' }),
    ).toHaveAttribute('aria-expanded', String(!collapsed))
    unmount()
    await renderRoute()
    await screen.findByRole('navigation', { name: 'Side navigation' })
    expect(
      screen.getByRole('button', { name: collapsed ? 'Open menu' : 'Close menu' }),
    ).toHaveAttribute('aria-expanded', String(!collapsed))
  },
)

test('focuses mobile navigation, makes main content inert, and returns focus after Escape', async () => {
  setViewportWidth(375)
  const user = userEvent.setup()
  await renderRoute('/ecas/ECAS05')
  await screen.findByRole('heading', { name: 'Inbox Search' })
  const main = screen.getByRole('main')
  const menu = screen.getByRole('button', { name: 'Open menu' })
  await user.click(menu)
  const navigation = screen.getByRole('navigation', { name: 'Side navigation' })
  await waitFor(() => expect(within(navigation).getByRole('link', { name: 'Home' })).toHaveFocus())

  expect(main).toHaveAttribute('inert')
  expect(menu).toHaveAttribute('aria-expanded', 'true')
  await user.keyboard('{Escape}')

  expect(menu).toHaveFocus()
  expect(menu).toHaveAttribute('aria-expanded', 'false')
  expect(main).not.toHaveAttribute('inert')
  expect(navigation).toHaveClass('is-collapsed')
  expect(screen.queryByRole('button', { name: 'Close navigation' })).not.toBeInTheDocument()
})

test('closes mobile navigation and focuses the destination page after following a link', async () => {
  setViewportWidth(375)
  const user = userEvent.setup()
  const { router } = await renderRoute('/ecas')
  await screen.findByRole('heading', { name: 'ECAS' })
  await user.click(screen.getByRole('button', { name: 'Open menu' }))
  const navigation = screen.getByRole('navigation', { name: 'Side navigation' })
  await waitFor(() => expect(within(navigation).getByRole('link', { name: 'Home' })).toHaveFocus())
  await user.click(within(navigation).getByRole('link', { name: 'Inbox Search' }))

  await screen.findByRole('heading', { name: 'Inbox Search' })
  expect(router.state.location.pathname).toBe('/ecas/ECAS05')
  expect(screen.getByRole('button', { name: 'Open menu' })).toHaveAttribute(
    'aria-expanded',
    'false',
  )
  expect(screen.getByRole('main')).not.toHaveAttribute('inert')
  await waitFor(() => expect(screen.getByRole('main')).toHaveFocus())
  expect(screen.queryByRole('button', { name: 'Close navigation' })).not.toBeInTheDocument()
})

test('closes an expanded application group with Escape and returns focus to its control', async () => {
  const user = userEvent.setup()
  await renderRoute('/ecas/ECAS05')
  await screen.findByRole('heading', { name: 'Inbox Search' })
  const navigation = within(screen.getByRole('navigation', { name: 'Side navigation' }))
  const application = navigation.getByRole('button', { name: 'ECAS' })
  act(() => navigation.getByRole('link', { name: 'Inbox Search' }).focus())
  await user.keyboard('{Escape}')

  expect(application).toHaveFocus()
  expect(application).toHaveAttribute('aria-expanded', 'false')
  expect(application).toHaveAttribute('aria-current', 'true')
  expect(navigation.queryByRole('link', { name: 'Inbox Search' })).not.toBeInTheDocument()
})

test('provides the Dark theme switch with mouse and keyboard operation', async () => {
  const user = userEvent.setup()
  await renderRoute()
  await screen.findByRole('navigation', { name: 'Side navigation' })
  const themeSwitch = screen.getByRole('switch', { name: 'Dark theme' })

  expect(themeSwitch).not.toBeChecked()
  expect(document.documentElement).toHaveAttribute('data-carbon-theme', 'white')
  await user.click(themeSwitch)

  expect(themeSwitch).toBeChecked()
  expect(document.documentElement).toHaveAttribute('data-carbon-theme', 'g100')
  expect(localStorage.getItem('taps.ui.theme')).toBe('g100')
  await user.keyboard(' ')

  expect(themeSwitch).not.toBeChecked()
  expect(document.documentElement).toHaveAttribute('data-carbon-theme', 'white')
  expect(localStorage.getItem('taps.ui.theme')).toBe('white')
})

test('shows the login page without the shell and keeps the saved theme', async () => {
  localStorage.setItem('taps.ui.theme', 'g100')
  oidc.getOidcUser.mockResolvedValue(null)
  await renderRoute('/ecas/ECAS05')
  await screen.findByRole('button', { name: 'Log in with IDIR' })

  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('TAPS')
  expect(screen.getByText('Timber Appraisal and Pricing System')).toBeInTheDocument()
  expect(screen.getByRole('img', { name: 'Government of British Columbia' })).toHaveAttribute(
    'src',
    expect.stringContaining('gov-bc-logo-horiz'),
  )
  expect(screen.getByRole('button', { name: 'Log in with Business BCeID' })).toBeEnabled()
  expect(screen.queryByRole('banner')).not.toBeInTheDocument()
  expect(screen.queryByRole('switch', { name: 'Dark theme' })).not.toBeInTheDocument()
  expect(document.documentElement).toHaveAttribute('data-carbon-theme', 'g100')
})

test.each([1440, 375])(
  'opens a nonmodal profile with current identity and grants and returns focus after Escape at %ipx',
  async (width) => {
    setViewportWidth(width)
    const user = userEvent.setup()
    await renderRoute()
    const toggle = await screen.findByRole('button', { name: 'Open profile panel' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('dialog', { name: 'My profile' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument()
    if (width === 375) await user.click(screen.getByRole('button', { name: 'Open menu' }))
    await user.click(toggle)
    const profile = screen.getByRole('dialog', { name: 'My profile' })

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(profile).toHaveAttribute('aria-modal', 'false')
    expect(profile).toHaveTextContent(staffSession.displayName)
    expect(profile).toHaveTextContent('District appraiser (district DCR)')
    expect(screen.getByRole('main')).not.toHaveAttribute('inert')
    expect(screen.queryByRole('button', { name: 'Close navigation' })).not.toBeInTheDocument()
    await waitFor(() =>
      expect(within(profile).getByRole('button', { name: 'Close profile panel' })).toHaveFocus(),
    )
    act(() => within(profile).getByRole('button', { name: 'Log out' }).focus())
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: 'My profile' })).not.toBeInTheDocument()
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await waitFor(() => expect(toggle).toHaveFocus())
  },
)

test('closes the profile from its own close control and restores avatar focus', async () => {
  const user = userEvent.setup()
  await renderRoute()
  const toggle = await screen.findByRole('button', { name: 'Open profile panel' })
  await user.click(toggle)
  const profile = screen.getByRole('dialog', { name: 'My profile' })

  await user.click(within(profile).getByRole('button', { name: 'Close profile panel' }))

  expect(screen.queryByRole('dialog', { name: 'My profile' })).not.toBeInTheDocument()
  expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await waitFor(() => expect(toggle).toHaveFocus())
})

test('retains profile and log out access for a signed-in session with no TAPS roles', async () => {
  fetch.mockResolvedValue({ ok: true, json: async () => ({ ...staffSession, roles: [] }) })
  const user = userEvent.setup()
  await renderRoute()
  await screen.findByText(/You do not have TAPS access yet/)
  await user.click(screen.getByRole('button', { name: 'Open profile panel' }))
  const profile = screen.getByRole('dialog', { name: 'My profile' })

  expect(profile).toHaveTextContent(staffSession.displayName)
  expect(profile).not.toHaveTextContent('District appraiser')
  expect(within(profile).getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  expect(screen.queryByRole('navigation', { name: 'Side navigation' })).not.toBeInTheDocument()
})

test('shows the current Business BCeID organization and scoped role in the profile', async () => {
  fetch.mockResolvedValue({
    ok: true,
    json: async () => ({
      ...staffSession,
      displayName: 'BCeID User',
      identityProvider: 'BCEID_BUSINESS',
      businessName: 'Example Company',
      email: null,
      roles: [
        {
          role: 'TAPS_LICENSEE_VIEWER',
          scopes: [{ type: 'FOREST_CLIENT', value: '00123456' }],
        },
      ],
      forestClients: ['00123456'],
    }),
  })
  const user = userEvent.setup()
  await renderRoute()
  await user.click(await screen.findByRole('button', { name: 'Open profile panel' }))
  const profile = screen.getByRole('dialog', { name: 'My profile' })

  expect(profile).toHaveTextContent('BCeID User')
  expect(profile).toHaveTextContent('Example Company')
  expect(profile).toHaveTextContent('Licensee viewer (client 00123456)')
  expect(profile).not.toHaveTextContent('District appraiser')
})

test('dismisses the nonmodal profile when a header control outside it is used', async () => {
  const user = userEvent.setup()
  await renderRoute()
  const toggle = await screen.findByRole('button', { name: 'Open profile panel' })
  await user.click(toggle)
  const themeSwitch = screen.getByRole('switch', { name: 'Dark theme' })

  await user.click(themeSwitch)

  expect(screen.queryByRole('dialog', { name: 'My profile' })).not.toBeInTheDocument()
  expect(toggle).toHaveAttribute('aria-expanded', 'false')
  expect(themeSwitch).toBeChecked()
  expect(themeSwitch).toHaveFocus()
})

test('returns to the login page with a notice when the session expires', async () => {
  await renderRoute('/ecas/ECAS05')
  await screen.findByRole('heading', { name: 'Inbox Search' })

  act(() => notifySessionExpired('api-unauthorized'))

  expect(await screen.findByRole('button', { name: 'Log in with IDIR' })).toBeInTheDocument()
  expect(screen.getByText("You've been logged out")).toBeInTheDocument()
  expect(screen.queryByRole('navigation', { name: 'Side navigation' })).not.toBeInTheDocument()
})

test('logs out from the profile and returns to the login page', async () => {
  oidc.logout.mockResolvedValue(undefined)
  const user = userEvent.setup()
  await renderRoute()
  await user.click(await screen.findByRole('button', { name: 'Open profile panel' }))
  const profile = screen.getByRole('dialog', { name: 'My profile' })

  await user.click(within(profile).getByRole('button', { name: 'Log out' }))

  expect(oidc.logout).toHaveBeenCalledOnce()
  expect(await screen.findByRole('button', { name: 'Log in with IDIR' })).toBeInTheDocument()
  expect(screen.queryByRole('dialog', { name: 'My profile' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Open profile panel' })).not.toBeInTheDocument()
  expect(screen.queryByRole('navigation', { name: 'Side navigation' })).not.toBeInTheDocument()
  expect(screen.queryByText("You've been logged out")).not.toBeInTheDocument()
})
