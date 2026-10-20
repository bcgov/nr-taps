import { Dropdown, Modal } from '@carbon/react'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { expect, test, vi } from 'vitest'
import { setViewportWidth } from '@/test-setup'
import DetailSidePanel from '../DetailSidePanel'

function PanelExample({
  onClose = () => {},
  removeLauncher = false,
  contentStyle,
  children,
}: {
  onClose?: () => void
  removeLauncher?: boolean
  contentStyle?: CSSProperties
  children?: ReactNode
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
        <button>Page action</button>
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
          {children}
        </DetailSidePanel>
      </main>
    </>
  )
}

test.each([
  { width: 1440, besidePage: true },
  { width: 1280, besidePage: false },
  { width: 1024, besidePage: false },
  { width: 375, besidePage: false },
])(
  'keeps the page available and returns focus after Escape at $width px',
  async ({ width, besidePage }) => {
    setViewportWidth(width)
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<PanelExample onClose={onClose} />)
    const launcher = screen.getByRole('button', { name: 'View details' })

    await user.click(launcher)
    const panel = screen.getByRole('complementary', { name: 'Reference details' })
    expect(panel).toHaveClass('c4p--side-panel--slide-in')
    expect(panel.classList.contains('taps-detail-panel--over-page')).toBe(!besidePage)
    expect(panel).not.toHaveAttribute('aria-modal')
    expect(document.querySelector('.c4p--side-panel__overlay')).toBeNull()
    expect(screen.getByRole('main').closest('[inert]')).toBeNull()
    expect(screen.getByRole('main').style.marginInlineEnd).toBe(besidePage ? '30rem' : '')
    await waitFor(() =>
      expect(within(panel).getByRole('button', { name: 'Primary action' })).toHaveFocus(),
    )

    await user.keyboard('{Escape}')

    expect(
      screen.queryByRole('complementary', { name: 'Reference details' }),
    ).not.toBeInTheDocument()
    expect(onClose).toHaveBeenCalledOnce()
    await waitFor(() => expect(launcher).toHaveFocus())
  },
)

test.each([1440, 1280, 1024, 375])(
  'returns focus when the IBM close button is clicked at %ipx',
  async (width) => {
    setViewportWidth(width)
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<PanelExample onClose={onClose} />)
    const launcher = screen.getByRole('button', { name: 'View details' })
    await user.click(launcher)
    const panel = screen.getByRole('complementary', { name: 'Reference details' })

    await user.click(within(panel).getByRole('button', { name: 'Close' }))

    expect(onClose).toHaveBeenCalledOnce()
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
    await waitFor(() => expect(launcher).toHaveFocus())
  },
)

test('focuses the initial target in the preview Strict Mode', async () => {
  setViewportWidth(1280)
  render(
    <StrictMode>
      <main id="main-content">
        <DetailSidePanel
          open
          title="Reference details"
          onClose={() => {}}
          launcherRef={{ current: null }}
          initialFocusSelector="#strict-detail-action"
        >
          <button id="strict-detail-action">Primary action</button>
        </DetailSidePanel>
      </main>
    </StrictMode>,
  )
  await waitFor(() => expect(screen.getByRole('button', { name: 'Primary action' })).toHaveFocus())
})

test.each([1440, 1280, 1024, 375])('lets focus leave the panel at %ipx', async (width) => {
  setViewportWidth(width)
  const user = userEvent.setup()
  render(<PanelExample />)
  await user.click(screen.getByRole('button', { name: 'View details' }))
  const panel = screen.getByRole('complementary', { name: 'Reference details' })
  const primaryAction = within(panel).getByRole('button', { name: 'Primary action' })
  const lastAction = within(panel).getByRole('button', { name: 'Last action' })
  await waitFor(() => expect(primaryAction).toHaveFocus())

  await user.tab()
  expect(lastAction).toHaveFocus()

  await user.tab()
  expect(panel).not.toContainElement(document.activeElement as HTMLElement)
  within(panel).getByRole('button', { name: 'Close' }).focus()
  await user.tab({ shift: true })
  expect(panel).not.toContainElement(document.activeElement as HTMLElement)
  const pageAction = screen.getByRole('button', { name: 'Page action' })
  await user.click(pageAction)
  expect(pageAction).toHaveFocus()
})

test.each([1440, 1280, 1024, 375])(
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

test('restores content sizing when moving over the page and back while open', async () => {
  setViewportWidth(1440)
  const user = userEvent.setup()
  render(<PanelExample contentStyle={originalStyle} />)
  await user.click(screen.getByRole('button', { name: 'View details' }))
  const main = screen.getByRole('main')
  expect(main.style.marginInlineEnd).toBe('30rem')

  act(() => setViewportWidth(1280))

  expect(screen.getByRole('complementary')).toHaveClass('c4p--side-panel--slide-in')
  expect(screen.getByRole('complementary')).toHaveClass('taps-detail-panel--over-page')
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
  'leaves existing background inert values unchanged through %s',
  async (action) => {
    setViewportWidth(768)
    const user = userEvent.setup()
    const alreadyInert = document.createElement('div')
    alreadyInert.setAttribute('inert', 'existing')
    document.body.append(alreadyInert)
    const { container, unmount } = render(<PanelExample />)
    try {
      await user.click(screen.getByRole('button', { name: 'View details' }))
      const panel = screen.getByRole('complementary', { name: 'Reference details' })
      expect(container).not.toHaveAttribute('inert')
      expect(panel.closest('[inert]')).toBeNull()
      expect(alreadyInert).toHaveAttribute('inert', 'existing')

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

test('leaves Escape on the page to the page', async () => {
  setViewportWidth(1024)
  const user = userEvent.setup()
  const onClose = vi.fn()
  render(<PanelExample onClose={onClose} />)
  await user.click(screen.getByRole('button', { name: 'View details' }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Primary action' })).toHaveFocus())
  const pageAction = screen.getByRole('button', { name: 'Page action' })
  pageAction.focus()
  await user.keyboard('{Escape}')
  expect(onClose).not.toHaveBeenCalled()
  expect(screen.getByRole('complementary', { name: 'Reference details' })).toBeInTheDocument()
})

test('Escape dismisses an open dropdown before closing the panel from its collapsed trigger', async () => {
  setViewportWidth(1280)
  const user = userEvent.setup()
  const onClose = vi.fn()
  render(
    <PanelExample onClose={onClose}>
      <Dropdown id="detail-type" titleText="Detail type" label="" items={['Staff', 'Public']} />
    </PanelExample>,
  )
  await user.click(screen.getByRole('button', { name: 'View details' }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Primary action' })).toHaveFocus())
  const dropdown = screen.getByRole('combobox', { name: 'Detail type' })
  await user.click(dropdown)
  expect(dropdown).toHaveAttribute('aria-expanded', 'true')
  fireEvent.keyDown(dropdown, { key: 'Escape', code: 'Escape', keyCode: 27, which: 27 })
  expect(dropdown).toHaveAttribute('aria-expanded', 'false')
  expect(onClose).not.toHaveBeenCalled()
  fireEvent.keyDown(dropdown, { key: 'Escape', code: 'Escape', keyCode: 27, which: 27 })
  expect(onClose).toHaveBeenCalledOnce()
})

test('leaves Escape to a dialog above the panel', async () => {
  setViewportWidth(1024)
  const user = userEvent.setup()
  const onClose = vi.fn()
  const onDialogClose = vi.fn()
  render(
    <PanelExample onClose={onClose}>
      <Modal open modalHeading="Confirm action" passiveModal onRequestClose={onDialogClose}>
        <button>Keep reviewing</button>
      </Modal>
    </PanelExample>,
  )
  await user.click(screen.getByRole('button', { name: 'View details' }))
  fireEvent.keyDown(screen.getByRole('button', { name: 'Keep reviewing' }), {
    key: 'Escape',
    code: 'Escape',
    keyCode: 27,
    which: 27,
  })
  expect(onDialogClose).toHaveBeenCalledOnce()
  expect(onClose).not.toHaveBeenCalled()
})
