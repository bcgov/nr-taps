import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { afterEach, expect, test, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '@/context/auth/AuthContext'
import { Capability } from '@/context/auth/capabilities'
import { ReadApiError } from '@/service/read-service'
import { staffSession } from '@/test-utils'
import { setViewportWidth } from '@/test-setup'
import ReadFeedbackPreview, { createFeedbackReadApi } from '../ReadFeedbackPreview'
import { workflowFixture } from '../WorkflowPreview'

afterEach(() => vi.restoreAllMocks())

function mount() {
  setViewportWidth(1440)
  const fetch = vi
    .spyOn(globalThis, 'fetch')
    .mockRejectedValue(new Error('Unexpected HTTP request'))
  const auth: AuthContextValue = {
    state: { kind: 'signed-in', session: staffSession },
    can: (capability) => Object.values(Capability).includes(capability),
    reloadSession: vi.fn(async () => {}),
    login: vi.fn(async () => {}),
    logout: vi.fn(async () => {}),
  }
  render(
    <StrictMode>
      <AuthContext value={auth}>
        <main id="main-content">
          <ReadFeedbackPreview />
        </main>
      </AuthContext>
    </StrictMode>,
  )
  return fetch
}

test('an aborted initial request does not consume the one visible failure', async () => {
  const api = createFeedbackReadApi('choices')
  const cancelled = new AbortController()
  const pending = api.ecasLookups(cancelled.signal)
  cancelled.abort()
  await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
  await expect(api.ecasLookups(new AbortController().signal)).rejects.toBeInstanceOf(ReadApiError)
  await expect(api.ecasLookups(new AbortController().signal)).resolves.toHaveProperty(
    'appraisalStatuses',
  )
})

test('search choices fail visibly in StrictMode and retry succeeds without HTTP', async () => {
  const user = userEvent.setup()
  const fetch = mount()
  await user.selectOptions(screen.getByLabelText('Synthetic read scenario'), 'choices')
  await screen.findByText('Search choices unavailable')
  await user.click(screen.getByRole('button', { name: 'Retry search choices' }))
  await waitFor(() => expect(screen.getByRole('combobox', { name: /^Statuses/ })).toBeEnabled())
  expect(screen.queryByText('Search choices unavailable')).not.toBeInTheDocument()
  expect(fetch).not.toHaveBeenCalled()
})

test('search-result failure retries and changing selectors clears the prior results', async () => {
  const user = userEvent.setup()
  const fetch = mount()
  await user.selectOptions(screen.getByLabelText('Synthetic read scenario'), 'results')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await screen.findByRole('heading', { name: 'Submission search unavailable' })
  await user.click(screen.getByRole('button', { name: 'Try again' }))
  await screen.findByRole('button', { name: /Open ECAS .*mark ZZ9997/ })
  await user.selectOptions(screen.getByLabelText('Synthetic read scenario'), 'normal')
  expect(screen.queryByRole('button', { name: /Open ECAS .*mark ZZ9997/ })).not.toBeInTheDocument()
  expect(fetch).not.toHaveBeenCalled()
})

test('record details fail once and recover through the real drawer retry', async () => {
  const user = userEvent.setup()
  const fetch = mount()
  await user.selectOptions(screen.getByLabelText('Synthetic read scenario'), 'details')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await user.click(await screen.findByRole('button', { name: /Open ECAS .*mark ZZ9997/ }))
  await screen.findByText('Unable to load details')
  await user.click(screen.getByRole('button', { name: 'Retry details' }))
  await screen.findByRole('heading', { name: 'Coast reference' })
  expect(fetch).not.toHaveBeenCalled()
})

test('GAS FTA failure recovers independently from the worksheet result list', async () => {
  const user = userEvent.setup()
  const fetch = mount()
  await user.selectOptions(screen.getByLabelText('Synthetic application'), 'gas')
  await user.selectOptions(screen.getByLabelText('Synthetic read scenario'), 'fta')
  await user.type(screen.getByRole('textbox', { name: 'Timber mark' }), 'ZZ9996')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await screen.findByRole('button', { name: 'Retry licence information' })
  await user.click(screen.getByRole('button', { name: 'Retry licence information' }))
  await screen.findByText('Synthetic active licence')
  expect(fetch).not.toHaveBeenCalled()
})

test('reappraisal preset shows the real required draft without changing the source fixture', async () => {
  const user = userEvent.setup()
  const fetch = mount()
  const sourceHeader = { ...workflowFixture.ecasCoastMultiMarkReference.header }
  await user.selectOptions(screen.getByLabelText('Synthetic read scenario'), 'reappraisal')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await user.click(await screen.findByRole('button', { name: /Open ECAS .*mark ZZ9997/ }))
  await screen.findByRole('heading', { name: 'Coast reference' })
  await user.click(screen.getByRole('button', { name: 'Appraisal dates draft' }))
  const form = within(screen.getByRole('form', { name: 'Coast appraisal dates draft' }))
  expect(form.getByRole('textbox', { name: 'Appraisal effective date' })).toHaveValue('')
  expect(form.getByRole('textbox', { name: 'Appraisal effective date' })).toHaveAttribute(
    'aria-required',
    'true',
  )
  await user.click(form.getByRole('button', { name: 'Check dates' }))
  expect(form.getByText('An effective date is required for a reappraisal.')).toBeInTheDocument()
  expect(workflowFixture.ecasCoastMultiMarkReference.header).toEqual(sourceHeader)
  expect(fetch).not.toHaveBeenCalled()
})
