import { RouterProvider } from '@tanstack/react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, render, renderRoute, screen, userEvent } from '@/test-utils'
import { isOidcConfigured } from '@/service/oidc-service'
import { createPreviewRouter } from '../LocalSyntheticPreview'

const fetch = vi.fn()
let previousConfig: Window['config']

beforeEach(() => {
  previousConfig = window.config
  window.config = {}
  vi.stubEnv('VITE_OIDC_ISSUER_URI', '')
  vi.stubEnv('VITE_OIDC_CLIENT_ID', '')
  fetch.mockReset().mockRejectedValue(new Error('Unexpected HTTP request'))
  vi.stubGlobal('fetch', fetch)
})

afterEach(() => {
  window.config = previousConfig
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
  localStorage.clear()
})

async function renderPreview() {
  const router = createPreviewRouter()
  render(<RouterProvider router={router} />)
  await act(async () => {
    await router.load()
  })
}

async function selectScenario(user: ReturnType<typeof userEvent.setup>, scenario: string) {
  const summary = screen.getByText('Local synthetic UI preview')
  if (!summary.closest('details')?.open) await user.click(summary)
  await user.selectOptions(screen.getByRole('combobox', { name: 'Synthetic session' }), scenario)
}

test('keeps the actual unconfigured login disabled when no preview override is supplied', async () => {
  expect(isOidcConfigured()).toBe(false)
  await renderRoute()

  expect(await screen.findByRole('button', { name: 'Log in with IDIR' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Log in with Business BCeID' })).toBeDisabled()
  expect(screen.getByText(/TAPS log in is not configured for this environment/)).toBeInTheDocument()
  const logo = screen.getByRole('img', { name: 'Government of British Columbia' })
  expect(logo).toHaveAttribute('viewBox', '70 70 710 188')
  expect(logo.querySelector('image')).toHaveAttribute(
    'href',
    expect.stringContaining('BCID_H_rgb_pos'),
  )
  expect(fetch).not.toHaveBeenCalled()
})

test.each([
  { provider: 'IDIR', scenario: 'staff' },
  { provider: 'Business BCeID', scenario: 'ecas' },
])(
  'uses the fake $provider login without HTTP or OIDC configuration',
  async ({ provider, scenario }) => {
    const user = userEvent.setup()
    expect(isOidcConfigured()).toBe(false)
    await renderPreview()
    await selectScenario(user, 'signed-out')
    const login = screen.getByRole('button', { name: `Log in with ${provider}` })

    expect(login).toBeEnabled()
    await user.click(login)

    expect(await screen.findByRole('heading', { level: 1, name: 'Home' })).toBeInTheDocument()
    await user.click(screen.getByText('Local synthetic UI preview'))
    expect(screen.getByRole('combobox', { name: 'Synthetic session' })).toHaveValue(scenario)
    expect(fetch).not.toHaveBeenCalled()
  },
)

test('keeps controls available when switching from signed-out to the expired login scenario', async () => {
  const user = userEvent.setup()
  await renderPreview()
  await selectScenario(user, 'signed-out')
  expect(screen.queryByText("You've been logged out")).not.toBeInTheDocument()

  await selectScenario(user, 'signed-out-expired')

  expect(screen.getByText("You've been logged out")).toBeInTheDocument()
  expect(screen.getAllByText('Local synthetic UI preview')).toHaveLength(1)
  expect(
    screen.getByText('Local synthetic UI preview').closest('.taps-preview-landing-controls'),
  ).not.toBeNull()
  await selectScenario(user, 'no-role')
  expect(await screen.findByText(/You do not have TAPS access yet/)).toBeInTheDocument()
  expect(fetch).not.toHaveBeenCalled()
})
