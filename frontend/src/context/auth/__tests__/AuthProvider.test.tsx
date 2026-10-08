import { beforeEach, expect, test, vi } from 'vitest'
import AuthProvider from '@/context/auth/AuthProvider'
import { useAuth } from '@/context/auth/AuthContext'
import { Capability } from '@/context/auth/capabilities'
import { notifySessionExpired } from '@/context/auth/session-expiry'
import type { Session } from '@/service/session-service'
import { act, render, screen, staffSession, userEvent, waitFor } from '@/test-utils'

const service = vi.hoisted(() => ({
  fetchSession: vi.fn(),
  logout: vi.fn(),
  clearLogin: vi.fn(),
  startLogin: vi.fn(),
}))
vi.mock('@/service/session-service', () => ({ fetchSession: service.fetchSession }))
vi.mock('@/service/oidc-service', () => ({
  AUTH_CALLBACK_PATH: '/authCallback',
  logout: service.logout,
  clearLogin: service.clearLogin,
  startLogin: service.startLogin,
}))

function Probe() {
  const { state, can, reloadSession, logout, login } = useAuth()
  return (
    <>
      <p>{state.kind}</p>
      <p>{can(Capability.EcasSubmissionView) ? 'ECAS access' : 'No ECAS access'}</p>
      {state.kind === 'error' && <p role="alert">{state.message}</p>}
      <button onClick={() => void reloadSession()}>Reload</button>
      <button onClick={() => void logout()}>Sign out</button>
      <button onClick={() => void login('idir', '/ecas/ECAS05?ecasId=1001')}>Sign in</button>
    </>
  )
}

const destinationKey = 'taps.login-destination'

beforeEach(() => {
  vi.resetAllMocks()
  window.sessionStorage.clear()
  service.fetchSession.mockResolvedValue(staffSession)
})

test('loads the server session and gates by its capabilities', async () => {
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

  expect(await screen.findByText('signed-in')).toBeInTheDocument()
  expect(screen.getByText('ECAS access')).toBeInTheDocument()
})

test('defers stored-session reads until the callback has completed', async () => {
  const view = render(
    <AuthProvider deferSessionLoad>
      <Probe />
    </AuthProvider>,
  )

  expect(service.fetchSession).not.toHaveBeenCalled()
  expect(screen.getByText('loading')).toBeInTheDocument()
  view.rerender(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )
  expect(await screen.findByText('signed-in')).toBeInTheDocument()
  expect(service.fetchSession).toHaveBeenCalledOnce()
})

test('does not grant capability access without a valid application role', async () => {
  service.fetchSession.mockResolvedValue({ ...staffSession, roles: [] })
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

  await screen.findByText('signed-in')
  expect(screen.getByText('No ECAS access')).toBeInTheDocument()
})

test('allows retry after a temporary session failure', async () => {
  const user = userEvent.setup()
  service.fetchSession
    .mockRejectedValueOnce(new Error('Unavailable'))
    .mockResolvedValueOnce(staffSession)
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load your session')
  expect(screen.getByText('No ECAS access')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Reload' }))
  expect(await screen.findByText('ECAS access')).toBeInTheDocument()
})

test('revokes access after successful sign out', async () => {
  const user = userEvent.setup()
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

  await screen.findByText('ECAS access')
  await user.click(screen.getByRole('button', { name: 'Sign out' }))

  expect(screen.getByText('signed-out')).toBeInTheDocument()
  expect(screen.getByText('No ECAS access')).toBeInTheDocument()
  expect(service.logout).toHaveBeenCalledOnce()
  expect(window.sessionStorage.getItem(destinationKey)).toBeNull()
})

test('clears locally when SSO sign out fails', async () => {
  const user = userEvent.setup()
  service.logout.mockRejectedValue(new Error('SSO unavailable'))
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

  await screen.findByText('ECAS access')
  await user.click(screen.getByRole('button', { name: 'Sign out' }))

  await waitFor(() => expect(service.clearLogin).toHaveBeenCalledOnce())
  expect(screen.getByText('signed-out')).toBeInTheDocument()
})

test('keeps access revoked and reports a failed local sign out', async () => {
  const user = userEvent.setup()
  service.logout.mockRejectedValue(new Error('SSO unavailable'))
  service.clearLogin.mockRejectedValue(new Error('Storage unavailable'))
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

  await screen.findByText('ECAS access')
  await user.click(screen.getByRole('button', { name: 'Sign out' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to log out')
  expect(screen.getByText('No ECAS access')).toBeInTheDocument()
})

test('a pending session request cannot restore access after sign out', async () => {
  const user = userEvent.setup()
  const pending = Promise.withResolvers<Session>()
  service.fetchSession.mockReturnValue(pending.promise)
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

  await user.click(screen.getByRole('button', { name: 'Sign out' }))
  await act(async () => pending.resolve(staffSession))

  expect(screen.getByText('signed-out')).toBeInTheDocument()
  expect(screen.getByText('No ECAS access')).toBeInTheDocument()
})

test('reports a sign-in start failure without granting access', async () => {
  const user = userEvent.setup()
  service.fetchSession.mockResolvedValue(null)
  service.startLogin.mockRejectedValue(new Error('Unavailable'))
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

  await screen.findByText('signed-out')
  await user.click(screen.getByRole('button', { name: 'Sign in' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to start log in')
  expect(screen.getByText('No ECAS access')).toBeInTheDocument()
  expect(window.sessionStorage.getItem(destinationKey)).toBeNull()
})

test('keeps the requested page for the sign-in callback', async () => {
  const user = userEvent.setup()
  service.fetchSession.mockResolvedValue(null)
  service.startLogin.mockReturnValue(new Promise(() => {}))
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

  await screen.findByText('signed-out')
  await user.click(screen.getByRole('button', { name: 'Sign in' }))

  expect(service.startLogin).toHaveBeenCalledWith('idir')
  expect(window.sessionStorage.getItem(destinationKey)).toBe('/ecas/ECAS05?ecasId=1001')
})

test.each(['api-unauthorized', 'token-unavailable'] as const)(
  'revokes access without another session request when a service reports %s',
  async (reason) => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await screen.findByText('ECAS access')
    window.sessionStorage.setItem(destinationKey, '/gas')

    act(() => notifySessionExpired(reason))

    expect(screen.getByText('signed-out')).toBeInTheDocument()
    expect(screen.getByText('No ECAS access')).toBeInTheDocument()
    expect(service.fetchSession).toHaveBeenCalledOnce()
    expect(window.sessionStorage.getItem(destinationKey)).toBeNull()
  },
)

test('a pending session request cannot restore access after the session expires', async () => {
  const pending = Promise.withResolvers<Session>()
  service.fetchSession.mockReturnValue(pending.promise)
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

  act(() => notifySessionExpired('api-unauthorized'))
  await act(async () => pending.resolve(staffSession))

  expect(screen.getByText('signed-out')).toBeInTheDocument()
  expect(screen.getByText('No ECAS access')).toBeInTheDocument()
})

test('stops listening for session expiry after unmount', async () => {
  const view = render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )
  await screen.findByText('ECAS access')
  view.unmount()
  window.sessionStorage.setItem(destinationKey, '/gas')

  notifySessionExpired('api-unauthorized')

  expect(window.sessionStorage.getItem(destinationKey)).toBe('/gas')
})
