import { useTheme as useCarbonTheme } from '@carbon/react'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { useTheme } from '../ThemeContext'
import ThemeProvider from '../ThemeProvider'

function ThemeExample() {
  const { theme, toggleTheme } = useTheme()
  const { theme: carbonTheme } = useCarbonTheme()
  return (
    <>
      <p>Current theme: {theme}</p>
      <p>Carbon theme: {carbonTheme}</p>
      <button onClick={toggleTheme}>Toggle theme</button>
    </>
  )
}

beforeEach(() => {
  localStorage.clear()
  document.documentElement.removeAttribute('data-carbon-theme')
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  localStorage.clear()
  document.documentElement.removeAttribute('data-carbon-theme')
})

test('defaults to white and persists a theme toggle across remounts', async () => {
  const user = userEvent.setup()
  const { unmount } = render(
    <ThemeProvider>
      <ThemeExample />
    </ThemeProvider>,
  )
  expect(screen.getByText('Current theme: white')).toBeInTheDocument()
  expect(document.documentElement).toHaveAttribute('data-carbon-theme', 'white')

  await user.click(screen.getByRole('button', { name: 'Toggle theme' }))

  expect(screen.getByText('Current theme: g100')).toBeInTheDocument()
  expect(localStorage.getItem('taps.ui.theme')).toBe('g100')
  expect(document.documentElement).toHaveAttribute('data-carbon-theme', 'g100')
  expect(screen.getByText('Carbon theme: g100')).toBeInTheDocument()
  unmount()
  render(
    <ThemeProvider>
      <ThemeExample />
    </ThemeProvider>,
  )
  expect(screen.getByText('Current theme: g100')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Toggle theme' }))
  expect(localStorage.getItem('taps.ui.theme')).toBe('white')
  expect(document.documentElement).toHaveAttribute('data-carbon-theme', 'white')
})

test('rejects unsupported persisted themes and uses the white fallback', () => {
  localStorage.setItem('taps.ui.theme', 'sepia')
  render(
    <ThemeProvider>
      <ThemeExample />
    </ThemeProvider>,
  )

  expect(screen.getByText('Current theme: white')).toBeInTheDocument()
  expect(document.documentElement).toHaveAttribute('data-carbon-theme', 'white')
  expect(localStorage.getItem('taps.ui.theme')).toBe('white')
})

test('keeps theme switching usable when browser preference storage is unavailable', async () => {
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
    throw new DOMException('Storage is unavailable', 'SecurityError')
  })
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
    throw new DOMException('Storage is unavailable', 'SecurityError')
  })
  const user = userEvent.setup()
  render(
    <ThemeProvider>
      <ThemeExample />
    </ThemeProvider>,
  )

  expect(screen.getByText('Current theme: white')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Toggle theme' }))
  expect(screen.getByText('Current theme: g100')).toBeInTheDocument()
  expect(document.documentElement).toHaveAttribute('data-carbon-theme', 'g100')
})

test.each([null, 'g90'])('restores the previous root theme %s when unmounted', (previous) => {
  if (previous !== null) document.documentElement.setAttribute('data-carbon-theme', previous)
  localStorage.setItem('taps.ui.theme', 'g100')
  const { unmount } = render(
    <ThemeProvider>
      <ThemeExample />
    </ThemeProvider>,
  )
  expect(document.documentElement).toHaveAttribute('data-carbon-theme', 'g100')

  unmount()

  if (previous === null) expect(document.documentElement).not.toHaveAttribute('data-carbon-theme')
  else expect(document.documentElement).toHaveAttribute('data-carbon-theme', previous)
})
