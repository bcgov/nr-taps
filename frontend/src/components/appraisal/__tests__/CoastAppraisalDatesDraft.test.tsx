import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import type { CoastReference } from '@/contracts/appraisal'
import { workflowFixture } from '@/local-synthetic/WorkflowPreview'
import CoastAppraisalDatesDraft from '../CoastAppraisalDatesDraft'

const source = (overrides: Partial<CoastReference['header']> = {}): CoastReference => ({
  ...workflowFixture.ecasCoastMultiMarkReference,
  header: { ...workflowFixture.ecasCoastMultiMarkReference.header, ...overrides },
})
afterEach(() => vi.unstubAllGlobals())

test('checks without saving, leaves stored data unchanged and resets locally', async () => {
  const user = userEvent.setup()
  const fetch = vi.fn()
  vi.stubGlobal('fetch', fetch)
  const reference = source({ effectiveDate: '2026-10-01', expiryDate: null })
  render(<CoastAppraisalDatesDraft reference={reference} />)
  const effective = screen.getByRole('textbox', { name: 'Appraisal effective date' })
  const expiry = screen.getByRole('textbox', { name: 'Appraisal expiry date' })
  expect(effective).toHaveValue('2026-10-01')
  await user.type(expiry, '2026-10-02')
  expect(screen.getByRole('status')).toHaveTextContent('Unsaved date changes')
  await user.click(screen.getByRole('button', { name: 'Check dates' }))
  expect(screen.getByRole('status')).toHaveTextContent(
    'Date fields pass the local checks. Changes are not saved.',
  )
  expect(reference.header.expiryDate).toBeNull()
  expect(fetch).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Reset draft' }))
  expect(expiry).toHaveValue('')
  expect(effective).toHaveFocus()
  expect(screen.getByRole('status')).toHaveTextContent('No date changes')
})

test('retains invalid input, explains dependent failures and focuses the first invalid field', async () => {
  const user = userEvent.setup()
  render(
    <CoastAppraisalDatesDraft
      reference={source({ appraisalCategoryCode: 'R', effectiveDate: null, expiryDate: null })}
    />,
  )
  const effective = screen.getByRole('textbox', { name: 'Appraisal effective date' })
  const expiry = screen.getByRole('textbox', { name: 'Appraisal expiry date' })
  await user.type(expiry, '2026-10-02')
  await user.click(screen.getByRole('button', { name: 'Check dates' }))
  expect(effective).toHaveFocus()
  expect(effective).toHaveAttribute('aria-invalid', 'true')
  expect(screen.getByText('An effective date is required for a reappraisal.')).toBeInTheDocument()
  expect(
    screen.getByText('Enter an effective date before entering an expiry date.'),
  ).toBeInTheDocument()
  await user.type(effective, '2026-02-30')
  await user.click(screen.getByRole('button', { name: 'Check dates' }))
  expect(effective).toHaveValue('2026-02-30')
  expect(screen.getByText('Enter a valid effective date in YYYY-MM-DD format.')).toBeInTheDocument()
  expect(document.querySelector('.flatpickr-calendar')).not.toBeInTheDocument()
})

test('rejects reversed expiry and clears the prior check on edit', async () => {
  const user = userEvent.setup()
  render(
    <CoastAppraisalDatesDraft
      reference={source({ effectiveDate: '2026-10-02', expiryDate: '2026-10-02' })}
    />,
  )
  const expiry = screen.getByRole('textbox', { name: 'Appraisal expiry date' })
  await user.click(screen.getByRole('button', { name: 'Check dates' }))
  fireEvent.change(expiry, { target: { value: '2026-10-01' } })
  expect(screen.getByRole('status')).toHaveTextContent('Unsaved date changes')
  await user.click(screen.getByRole('button', { name: 'Check dates' }))
  expect(expiry).toHaveFocus()
  expect(
    screen.getByText('The expiry date must be on or after the effective date.'),
  ).toBeInTheDocument()
})

test('new record or revision resets the draft and discards previous errors', async () => {
  const user = userEvent.setup()
  const { rerender } = render(
    <CoastAppraisalDatesDraft
      reference={source({ effectiveDate: '2026-10-02', revisionCount: 1 })}
    />,
  )
  fireEvent.change(screen.getByRole('textbox', { name: 'Appraisal effective date' }), {
    target: { value: '2001-01-01' },
  })
  await user.click(screen.getByRole('button', { name: 'Check dates' }))
  expect(
    screen.getByText('The Coast effective date must be on or after 2002-04-01.'),
  ).toBeInTheDocument()
  rerender(
    <CoastAppraisalDatesDraft
      reference={source({ ecasId: '999900000003', effectiveDate: '2026-11-01', revisionCount: 2 })}
    />,
  )
  expect(screen.getByRole('textbox', { name: 'Appraisal effective date' })).toHaveValue(
    '2026-11-01',
  )
  expect(
    screen.queryByText('The Coast effective date must be on or after 2002-04-01.'),
  ).not.toBeInTheDocument()
  fireEvent.change(screen.getByRole('textbox', { name: 'Appraisal effective date' }), {
    target: { value: '2026-12-01' },
  })
  rerender(
    <CoastAppraisalDatesDraft
      reference={source({ ecasId: '999900000003', effectiveDate: '2026-11-02', revisionCount: 3 })}
    />,
  )
  expect(screen.getByRole('textbox', { name: 'Appraisal effective date' })).toHaveValue(
    '2026-11-02',
  )
})
