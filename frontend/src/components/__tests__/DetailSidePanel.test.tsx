import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useRef, useState, type CSSProperties } from 'react'
import { expect, test, vi } from 'vitest'
import { setViewportWidth } from '@/test-setup'
import DetailSidePanel from '../DetailSidePanel'

function PanelExample({
  onClose = () => {},
  removeLauncher = false,
  contentStyle,
}: {
  onClose?: () => void
  removeLauncher?: boolean
  contentStyle?: CSSProperties
}) {
  const [open, setOpen] = useState(false)
  const [launcherRemoved, setLauncherRemoved] = useState(false)
  const launcherRef = useRef<HTMLButtonElement>(null)
  return (
    <>
      <main id="main-content" tabIndex={-1} style={contentStyle}>
        {!launcherRemoved && (
          <button
            ref={launcherRef}
            onClick={() => {
              if (removeLauncher) setLauncherRemoved(true)
              setOpen(true)
            }}
          >
            View details
          </button>
        )}
        <DetailSidePanel
          open={open}
          title="Reference details"
          onClose={() => {
            onClose()
            setOpen(false)
          }}
          launcherRef={launcherRef}
          initialFocusSelector="#detail-action"
        >
          <button id="detail-action">Primary action</button>
          <button>Last action</button>
        </DetailSidePanel>
      </main>
    </>
  )
}

test.each([
  { width: 1440, mode: 'slide-in' },
  { width: 768, mode: 'overlay' },
])(
  'focuses the requested control and returns focus after Escape in $mode mode',
  async ({ width, mode }) => {
    setViewportWidth(width)
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<PanelExample onClose={onClose} />)
    const launcher = screen.getByRole('button', { name: 'View details' })

    await user.click(launcher)
    const role = mode === 'slide-in' ? 'complementary' : 'dialog'
    const panel = screen.getByRole(role, { name: 'Reference details' })
    if (mode === 'slide-in') expect(panel).toHaveClass('c4p--side-panel--slide-in')
    else expect(panel).toHaveClass('c4p--side-panel--has-overlay')
    await waitFor(() =>
      expect(within(panel).getByRole('button', { name: 'Primary action' })).toHaveFocus(),
    )

    await user.keyboard('{Escape}')

    expect(screen.queryByRole(role, { name: 'Reference details' })).not.toBeInTheDocument()
    expect(onClose).toHaveBeenCalledOnce()
    await waitFor(() => expect(launcher).toHaveFocus())
  },
)

test.each([1440, 768])(
  'returns focus when the IBM close button is clicked at %ipx',
  async (width) => {
    setViewportWidth(width)
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<PanelExample onClose={onClose} />)
    const launcher = screen.getByRole('button', { name: 'View details' })
    await user.click(launcher)
    const role = width >= 1312 ? 'complementary' : 'dialog'
    const panel = screen.getByRole(role, { name: 'Reference details' })

    await user.click(within(panel).getByRole('button', { name: 'Close' }))

    expect(onClose).toHaveBeenCalledOnce()
    expect(screen.queryByRole(role)).not.toBeInTheDocument()
    await waitFor(() => expect(launcher).toHaveFocus())
  },
)

test('contains Tab and Shift+Tab focus within the IBM overlay panel', async () => {
  setViewportWidth(768)
  const user = userEvent.setup()
  render(<PanelExample />)
  await user.click(screen.getByRole('button', { name: 'View details' }))
  const panel = screen.getByRole('dialog', { name: 'Reference details' })
  const close = within(panel).getByRole('button', { name: 'Close' })
  const primaryAction = within(panel).getByRole('button', { name: 'Primary action' })
  const lastAction = within(panel).getByRole('button', { name: 'Last action' })
  await waitFor(() => expect(primaryAction).toHaveFocus())

  await user.tab()
  expect(lastAction).toHaveFocus()

  await user.tab()
  expect(close).toHaveFocus()
  await user.tab({ shift: true })
  expect(lastAction).toHaveFocus()
})

test.each([1440, 768])(
  'returns focus to main content if its launcher was removed at %ipx',
  async (width) => {
    setViewportWidth(width)
    const user = userEvent.setup()
    render(<PanelExample removeLauncher />)
    await user.click(screen.getByRole('button', { name: 'View details' }))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Primary action' })).toHaveFocus(),
    )
    await user.keyboard('{Escape}')

    await waitFor(() => expect(screen.getByRole('main')).toHaveFocus())
  },
)

const originalStyle: CSSProperties = {
  marginInlineEnd: '2rem',
  inlineSize: 'calc(100% - 2rem)',
  transition: 'opacity 100ms linear',
}

test('restores existing content sizing after closing and reopening a slide-in panel', async () => {
  setViewportWidth(1440)
  const user = userEvent.setup()
  render(<PanelExample contentStyle={originalStyle} />)
  const main = screen.getByRole('main')

  for (let cycle = 0; cycle < 2; cycle++) {
    await user.click(screen.getByRole('button', { name: 'View details' }))
    expect(main.style.marginInlineEnd).toBe('30rem')
    expect(main.style.inlineSize).toBe('auto')
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Primary action' })).toHaveFocus(),
    )
    await user.keyboard('{Escape}')

    expect(main.style.marginInlineEnd).toBe(originalStyle.marginInlineEnd)
    expect(main.style.inlineSize).toBe(originalStyle.inlineSize)
    expect(main.style.transition).toBe(originalStyle.transition)
  }
})

test('restores content sizing when switching from slide-in to overlay while open', async () => {
  setViewportWidth(1440)
  const user = userEvent.setup()
  render(<PanelExample contentStyle={originalStyle} />)
  await user.click(screen.getByRole('button', { name: 'View details' }))
  const main = screen.getByRole('main')
  expect(main.style.marginInlineEnd).toBe('30rem')

  act(() => setViewportWidth(768))

  expect(screen.getByRole('dialog')).toHaveClass('c4p--side-panel--has-overlay')
  expect(screen.getByRole('dialog')).not.toHaveClass('c4p--side-panel--slide-in')
  expect(main.style.marginInlineEnd).toBe(originalStyle.marginInlineEnd)
  expect(main.style.inlineSize).toBe(originalStyle.inlineSize)
  expect(main.style.transition).toBe(originalStyle.transition)

  act(() => setViewportWidth(1440))
  expect(screen.getByRole('complementary')).toHaveClass('c4p--side-panel--slide-in')
  expect(main.style.marginInlineEnd).toBe('30rem')
  await user.click(screen.getByRole('button', { name: 'Close' }))
  expect(main.style.marginInlineEnd).toBe(originalStyle.marginInlineEnd)
  expect(main.style.inlineSize).toBe(originalStyle.inlineSize)
  expect(main.style.transition).toBe(originalStyle.transition)
})

test('removes IBM sizing overrides when an open panel unmounts', async () => {
  setViewportWidth(1440)
  const user = userEvent.setup()
  const { unmount } = render(<PanelExample />)
  await user.click(screen.getByRole('button', { name: 'View details' }))
  const main = screen.getByRole('main')
  expect(main.style.marginInlineEnd).toBe('30rem')

  unmount()

  expect(main.style.marginInlineEnd).toBe('')
  expect(main.style.inlineSize).toBe('')
  expect(main.style.transition).toBe('')
})

test.each(['close', 'unmount', 'resize'])(
  'restores background inert values after modal %s',
  async (action) => {
    setViewportWidth(768)
    const user = userEvent.setup()
    const alreadyInert = document.createElement('div')
    alreadyInert.setAttribute('inert', 'existing')
    document.body.append(alreadyInert)
    const { container, unmount } = render(<PanelExample />)
    try {
      await user.click(screen.getByRole('button', { name: 'View details' }))
      const panel = screen.getByRole('dialog', { name: 'Reference details' })
      expect(container).toHaveAttribute('inert')
      expect(panel.closest('[inert]')).toBeNull()

      if (action === 'close') await user.click(within(panel).getByRole('button', { name: 'Close' }))
      else if (action === 'unmount') unmount()
      else act(() => setViewportWidth(1312))

      expect(container).not.toHaveAttribute('inert')
      expect(alreadyInert).toHaveAttribute('inert', 'existing')
    } finally {
      unmount()
      alreadyInert.remove()
    }
  },
)
