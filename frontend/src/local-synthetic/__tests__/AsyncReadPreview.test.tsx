import { render, screen, within } from '@testing-library/react'
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
  const history = within(screen.getByRole('region', { name: 'Submission audit history' }))
  await history.findByRole('table', { name: 'Audit events' })
  await user.click(history.getByRole('button', { name: 'View event SYNTHETIC-EVENT-1' }))
  const event = within(screen.getByRole('region', { name: 'Audit event SYNTHETIC-EVENT-1' }))
  await event.findByRole('table', { name: 'Audit field changes' })
  expect(event.getByText(`SYNTHETIC_PREVIOUS_${'A'.repeat(200)}`)).toBeInTheDocument()
  expect(event.getByText(`SYNTHETIC_CHANGED_${'B'.repeat(200)}`)).toBeInTheDocument()
  await user.click(event.getByRole('button', { name: 'Next page' }))
  await event.findByText('SYNTHETIC_FIELD_101')
  expect(event.queryByText('SYNTHETIC_FIELD_1')).not.toBeInTheDocument()
  await user.click(event.getByRole('button', { name: 'Close event details' }))
  await user.click(history.getByRole('button', { name: 'Next page' }))
  await history.findByRole('button', { name: 'View event SYNTHETIC-EVENT-101' })
  expect(
    history.queryByRole('button', { name: 'View event SYNTHETIC-EVENT-1' }),
  ).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Attachment inventory' }))
  const attachments = within(screen.getByRole('region', { name: 'Attachment inventory' }))
  await attachments.findByRole('table', { name: 'Attachment information' })
  expect(attachments.getByText(`SYNTHETIC_${'LONGFILENAME'.repeat(18)}.pdf`)).toBeInTheDocument()
  await user.click(attachments.getByRole('button', { name: 'Next page' }))
  await attachments.findByText('SYNTHETIC-document-51.pdf')
  expect(attachments.queryByText('SYNTHETIC-document-2.pdf')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'View related GAS worksheet' }))
  await screen.findByRole('table', { name: 'Stored stumpage rates' })
  await user.click(screen.getByRole('button', { name: 'History' }))
  await screen.findByRole('table', { name: 'Worksheet history' })
  expect(fetch).not.toHaveBeenCalled()
}, 15000)

test('synthetic GAS search includes FTA and history without HTTP requests', async () => {
  const user = userEvent.setup()
  const fetch = mount(<SyntheticGasSearchPage />)
  await user.type(screen.getByRole('textbox', { name: 'Timber mark' }), 'ZZ9995')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await user.click(
    await screen.findByRole('button', { name: /Open Non-appraised worksheet .*mark ZZ9995/ }),
  )
  await screen.findByRole('table', { name: 'Non-appraised rates' })
  await screen.findByText('Synthetic issued mark')
  const addons = within(screen.getByRole('table', { name: 'Selected rate add-ons' }))
  expect(addons.getByRole('cell', { name: 'SYNTHETIC-ADDON' })).toBeInTheDocument()
  expect(
    addons.getByRole('cell', { name: 'Synthetic stored rate add-on selection' }),
  ).toBeInTheDocument()
  expect(addons.getByRole('cell', { name: '2026-10-01T09:00:00' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'History' }))
  await screen.findByRole('table', { name: 'Worksheet history' })
  expect(fetch).not.toHaveBeenCalled()
})

test('synthetic GAS historic search reaches every stored detail table without HTTP', async () => {
  const user = userEvent.setup()
  const fetch = mount(<SyntheticGasSearchPage />)
  await user.type(screen.getByRole('textbox', { name: 'Timber mark' }), 'ZZ9995')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await user.click(
    await screen.findByRole('button', { name: /Open Historic worksheet 999900000098/ }),
  )

  await screen.findByRole('table', { name: 'Historic stored rates' })
  expect(screen.queryByText('Variant')).not.toBeInTheDocument()
  expect(screen.queryByText('COAST_MPS_TOA_Y')).not.toBeInTheDocument()
  expect(screen.getByText('SYNTHETIC-2025')).toBeInTheDocument()
  expect(screen.getByRole('table', { name: 'Non-appraised rates' })).toBeInTheDocument()
  const species = within(screen.getByRole('table', { name: 'Historic species' }))
  expect(species.getByRole('cell', { name: '123.4500' })).toBeInTheDocument()
  const grades = within(screen.getByRole('table', { name: 'Coast species grades' }))
  expect(grades.getByRole('cell', { name: '100.00' })).toBeInTheDocument()
  await screen.findByText('Synthetic issued mark')
  expect(fetch).not.toHaveBeenCalled()
})
