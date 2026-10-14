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

test('keeps Layout available and the narrow drawer usable through Tab, close and reopen', async () => {
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
    const panel = screen.getByRole('complementary', { name: 'Reference details' })
    background.forEach((region) => expect(region.closest('[inert]')).toBeNull())
    expect(menu.closest('[inert]')).toBeNull()
    expect(panel.closest('[inert]')).toBeNull()
    expect(panel.closest('#main-content')).toBeNull()
    await waitFor(() =>
      expect(screen.getByRole('textbox', { name: 'Reference note' })).toHaveFocus(),
    )

    await user.tab()
    expect(within(panel).getByRole('button', { name: 'Last action' })).toHaveFocus()
    await user.tab()
    expect(panel).not.toContainElement(document.activeElement as HTMLElement)
    await user.click(within(panel).getByRole('button', { name: 'Close' }))

    expect(
      screen.queryByRole('complementary', { name: 'Reference details' }),
    ).not.toBeInTheDocument()
    background.forEach((region) => expect(region.closest('[inert]')).toBeNull())
    await waitFor(() => expect(launcher).toHaveFocus())
  }

  await user.click(launcher)
  const panel = screen.getByRole('complementary', { name: 'Reference details' })
  await user.click(menu)
  expect(menu).toHaveAttribute('aria-expanded', 'true')
  expect(screen.getByRole('main')).toHaveAttribute('inert')
  expect(panel.closest('[inert]')).toBeNull()
  await user.click(menu)
  expect(screen.getByRole('main')).not.toHaveAttribute('inert')
  await user.click(within(panel).getByRole('button', { name: 'Close' }))
  await waitFor(() => expect(launcher).toHaveFocus())
})

test('keeps content, theme and page focus when switching between beside-page and over-page layouts', async () => {
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

  act(() => setViewportWidth(1280))
  expect(screen.getByRole('complementary', { name: 'Reference details' })).toBe(panel)
  expect(main.closest('[inert]')).toBeNull()
  expect(panel.closest('[inert]')).toBeNull()
  expect(note).toHaveValue('Keep this reference')
  expect(note).toHaveFocus()

  act(() => setViewportWidth(1024))
  expect(screen.getByRole('complementary', { name: 'Reference details' })).toBe(panel)
  expect(main.closest('[inert]')).toBeNull()
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
  expect(launcher).toHaveFocus()
  expect(main.closest('[inert]')).toBeNull()
  expect(note).toHaveValue('Keep this reference')
  await user.click(note)
  await user.keyboard('{Escape}')
  await waitFor(() => expect(launcher).toHaveFocus())
})
