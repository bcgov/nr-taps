import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import type { ReactNode } from 'react'
import { AuthContext, type AuthContextValue } from '@/context/auth/AuthContext'
import { staffSession } from '@/test-utils'
import { setViewportWidth } from '@/test-setup'
import { SyntheticEcasInboxPage, SyntheticGasSearchPage } from '../AsyncReadPreview'

afterEach(() => vi.restoreAllMocks())

function mount(children: ReactNode) {
  setViewportWidth(1440)
  const fetch = vi
    .spyOn(globalThis, 'fetch')
    .mockRejectedValue(new Error('Unexpected HTTP request'))
  const auth: AuthContextValue = {
    state: { kind: 'signed-in', session: staffSession },
    can: (capability) => staffSession.capabilities.includes(capability),
    reloadSession: vi.fn(async () => {}),
    login: vi.fn(async () => {}),
    logout: vi.fn(async () => {}),
  }
  render(
    <AuthContext value={auth}>
      <main id="main-content">{children}</main>
    </AuthContext>,
  )
  return fetch
}

test('synthetic ECAS search and related drawer sections never request HTTP data', async () => {
  const user = userEvent.setup()
  const fetch = mount(<SyntheticEcasInboxPage />)
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await user.click(await screen.findByRole('button', { name: /Open ECAS .*mark ZZ9997/ }))
  await screen.findByRole('heading', { name: 'Coast reference' })
  await user.click(screen.getByRole('button', { name: 'Audit history' }))
  await screen.findByText('No audit events on this page.')
  await user.click(screen.getByRole('button', { name: 'Attachment inventory' }))
  await screen.findByText('No accessible individual documents on this page.')
  await user.click(screen.getByRole('button', { name: 'View related GAS worksheet' }))
  await screen.findByRole('table', { name: 'Stored stumpage rates' })
  await user.click(screen.getByRole('button', { name: 'History' }))
  await screen.findByRole('table', { name: 'Worksheet history' })
  expect(fetch).not.toHaveBeenCalled()
})

test('synthetic GAS search includes FTA and history without HTTP requests', async () => {
  const user = userEvent.setup()
  const fetch = mount(<SyntheticGasSearchPage />)
  await user.type(screen.getByRole('textbox', { name: 'Timber mark' }), 'ZZ9995')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await user.click(
    await screen.findByRole('button', { name: /Open NON_APPRAISED worksheet .*mark ZZ9995/ }),
  )
  await screen.findByRole('table', { name: 'Non-appraised rates' })
  await screen.findByText('Synthetic issued mark')
  await user.click(screen.getByRole('button', { name: 'History' }))
  await screen.findByRole('table', { name: 'Worksheet history' })
  expect(fetch).not.toHaveBeenCalled()
})
