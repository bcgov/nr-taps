import {
  createMemoryHistory,
  createRootRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { useRef, useState } from 'react'
import { beforeEach, expect, test } from 'vitest'
import { AuthContext } from '@/context/auth/AuthContext'
import ThemeProvider from '@/context/theme/ThemeProvider'
import { setViewportWidth } from '@/test-setup'
import { act, render, screen, staffSession, userEvent, waitFor, within } from '@/test-utils'
import DetailSidePanel from '../DetailSidePanel'
import Layout from '../Layout'

function DetailPage() {
  const [open, setOpen] = useState(false)
  const launcherRef = useRef<HTMLButtonElement>(null)
  return (
    <>
      <button ref={launcherRef} onClick={() => setOpen(true)}>
        View reference
      </button>
      <DetailSidePanel
        open={open}
        title="Reference details"
        onClose={() => setOpen(false)}
        launcherRef={launcherRef}
        initialFocusSelector="#reference-note"
      >
        <label htmlFor="reference-note">Reference note</label>
        <input id="reference-note" />
        <button>Last action</button>
      </DetailSidePanel>
    </>
  )
}

async function renderLayout() {
  const routeTree = createRootRoute({
    component: () => (
      <ThemeProvider>
        <AuthContext
          value={{
            state: { kind: 'signed-in', session: staffSession },
            can: (capability) => staffSession.capabilities.includes(capability),
            reloadSession: async () => {},
            login: async () => {},
            logout: async () => {},
          }}
        >
          <Layout>
            <DetailPage />
          </Layout>
        </AuthContext>
      </ThemeProvider>
    ),
  })
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: ['/'] }),
  })
  const result = render(<RouterProvider router={router} />)
  await act(async () => router.load())
  return result
}

beforeEach(() => {
  localStorage.clear()
})

test('isolates every Layout region and keeps the mobile drawer usable through Tab, Escape and reopen', async () => {
  setViewportWidth(375)
  const user = userEvent.setup()
  await renderLayout()
  const launcher = screen.getByRole('button', { name: 'View reference' })
  const menu = screen.getByRole('button', { name: 'Open menu' })
  const background = [
    screen.getByRole('banner'),
    screen.getByRole('navigation', { name: 'Side navigation' }),
    screen.getByRole('main'),
  ]

  for (let cycle = 0; cycle < 2; cycle++) {
    await user.click(launcher)
    const panel = screen.getByRole('dialog', { name: 'Reference details' })
    background.forEach((region) => expect(region.closest('[inert]')).not.toBeNull())
    expect(menu.closest('[inert]')).not.toBeNull()
    expect(panel.closest('[inert]')).toBeNull()
    expect(panel.closest('#main-content')).toBeNull()
    await waitFor(() =>
      expect(screen.getByRole('textbox', { name: 'Reference note' })).toHaveFocus(),
    )

    await user.tab()
    expect(within(panel).getByRole('button', { name: 'Last action' })).toHaveFocus()
    await user.tab()
    expect(within(panel).getByRole('button', { name: 'Close' })).toHaveFocus()
    await user.tab({ shift: true })
    expect(within(panel).getByRole('button', { name: 'Last action' })).toHaveFocus()
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: 'Reference details' })).not.toBeInTheDocument()
    background.forEach((region) => expect(region.closest('[inert]')).toBeNull())
    await waitFor(() => expect(launcher).toHaveFocus())
  }

  await user.click(menu)
  expect(menu).toHaveAttribute('aria-expanded', 'true')
  expect(screen.getByRole('main')).toHaveAttribute('inert')
})

test('keeps drawer content and dark theme while switching between modal and desktop modes', async () => {
  localStorage.setItem('taps.ui.theme', 'g100')
  setViewportWidth(1440)
  const user = userEvent.setup()
  await renderLayout()
  const launcher = screen.getByRole('button', { name: 'View reference' })
  const main = screen.getByRole('main')
  await user.click(launcher)
  const panel = screen.getByRole('complementary', { name: 'Reference details' })
  const note = within(panel).getByRole('textbox', { name: 'Reference note' })
  await user.type(note, 'Keep this reference')
  expect(main.closest('[inert]')).toBeNull()
  expect(panel.closest('.taps-detail-panel-host')).toHaveClass('cds--g100')

  act(() => setViewportWidth(1311))
  expect(screen.getByRole('dialog', { name: 'Reference details' })).toBe(panel)
  expect(main.closest('[inert]')).not.toBeNull()
  expect(panel.closest('[inert]')).toBeNull()
  expect(note).toHaveValue('Keep this reference')
  expect(note).toHaveFocus()

  act(() => setViewportWidth(1312))
  expect(screen.getByRole('complementary', { name: 'Reference details' })).toBe(panel)
  expect(main.closest('[inert]')).toBeNull()
  expect(note).toHaveValue('Keep this reference')
  expect(note).toHaveFocus()

  act(() => launcher.focus())
  expect(launcher).toHaveFocus()
  act(() => setViewportWidth(375))
  await waitFor(() => expect(note).toHaveFocus())
  expect(main.closest('[inert]')).not.toBeNull()
  await user.keyboard('{Escape}')
  await waitFor(() => expect(launcher).toHaveFocus())
})
