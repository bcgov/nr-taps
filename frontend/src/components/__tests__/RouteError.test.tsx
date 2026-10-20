import type { ReactNode } from 'react'
import { expect, test, vi } from 'vitest'
import { render, renderRoute, screen, userEvent } from '@/test-utils'
import RouteError from '../RouteError'

const internalMessage = 'Internal route failure that must not appear in the page'

vi.mock('@/components/Layout', () => ({
  default: () => {
    throw new Error('Internal route failure that must not appear in the page')
  },
}))

vi.mock('@/context/auth/AuthProvider', () => ({
  default: ({ children }: { children: ReactNode }) => children,
}))

test('replaces a failed route with accessible recovery and keeps internal errors out of the page', async () => {
  const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
  const consoleWarn = vi.spyOn(console, 'warn').mockImplementation(() => {})
  localStorage.setItem('taps.ui.theme', 'g100')
  try {
    await renderRoute()

    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(
      'Page could not be loaded',
    )
    expect(screen.getAllByRole('heading')).toHaveLength(1)
    expect(
      screen.getByRole('alert', { name: 'Page could not be loaded' }),
    ).toHaveAccessibleDescription('TAPS could not load this page. Reload and try again.')
    expect(screen.getByRole('button', { name: 'Reload application' })).toBeEnabled()
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content')
    expect(screen.queryByText(internalMessage)).not.toBeInTheDocument()
    expect(document.documentElement).toHaveAttribute('data-carbon-theme', 'g100')
  } finally {
    consoleError.mockRestore()
    consoleWarn.mockRestore()
    localStorage.clear()
  }
})

test('runs the recovery action when Reload application is clicked', async () => {
  const onReload = vi.fn()
  const user = userEvent.setup()
  render(<RouteError onReload={onReload} />)

  await user.click(screen.getByRole('button', { name: 'Reload application' }))

  expect(onReload).toHaveBeenCalledOnce()
})
