import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { expect, test, vi } from 'vitest'
import type { EcasSearchFilters as Filters, EcasLookups } from '@/contracts/appraisal'
import EcasSearchFilters from '../EcasSearchFilters'
import { isValidIsoDate, parseIsoDate } from '../../iso-date'

const empty: Filters = { ecasId: '', licence: '', timberMark: '' }
const code = (code: string, description: string) => ({
  code,
  description,
  effectiveDate: null,
  expiryDate: null,
  updateTimestamp: null,
  active: true,
})
const lookups: EcasLookups = {
  appraisalMethods: [code('C', 'Coast'), code('I', 'Interior')],
  appraisalStatuses: [
    code('DFT', 'Draft'),
    code('CON', 'Confirmed'),
    code('EE', 'Entered in error'),
  ],
}
function Form({
  onSearch,
  initial = empty,
}: {
  onSearch: (filters: Filters) => void
  initial?: Filters
}) {
  const [draft, setDraft] = useState(initial)
  return (
    <EcasSearchFilters
      draft={draft}
      onChange={setDraft}
      onSearch={() => onSearch(draft)}
      onReset={() => setDraft(empty)}
      loading={false}
      lookups={lookups}
      lookupLoading={false}
      onRetryLookups={() => {}}
    />
  )
}

test('uses code-backed method/status selections and exact sorting with calendar-only ranges', async () => {
  const user = userEvent.setup()
  const submit = vi.fn()
  render(<Form onSearch={submit} />)
  await user.selectOptions(screen.getByLabelText('Appraisal method'), 'I')
  await user.click(screen.getByRole('combobox', { name: /^Statuses/ }))
  await user.click(screen.getByText('DFT — Draft', { exact: true }))
  await user.click(screen.getByRole('combobox', { name: /^Dates to match/ }))
  await user.click(screen.getByText('Created date', { exact: true }))
  fireEvent.change(screen.getByLabelText('From date'), { target: { value: '2026-10-01' } })
  fireEvent.change(screen.getByLabelText('To date'), { target: { value: '2026-10-02' } })
  await user.selectOptions(screen.getByLabelText('Sort by'), 'EFFECTIVE_DATE')
  await user.selectOptions(screen.getByLabelText('Sort direction'), 'ASC')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  expect(submit).toHaveBeenCalledWith(
    expect.objectContaining({
      appraisalMethod: 'I',
      statusCodes: ['DFT'],
      dateTypes: ['NTRY'],
      dateFrom: '2026-10-01',
      dateTo: '2026-10-02',
      sortBy: 'EFFECTIVE_DATE',
      sortDirection: 'ASC',
    }),
  )
})

test.each([
  [{ dateFrom: '2026-10-01' }, 'date field'],
  [{ dateTypes: ['EFFCTV'], dateFrom: '2026-02-30' }, 'valid YYYY-MM-DD'],
  [{ dateTypes: ['EFFCTV'], dateFrom: '2026-10-02', dateTo: '2026-10-01' }, 'on or after'],
  [{ dateTypes: ['FCED'], dateFrom: '2026-10-01' }, 'requires both'],
  [{ statusDateFrom: '2026-10-01' }, 'Choose statuses'],
  [{ statusCodes: ['EE'], statusDateFrom: '2026-10-01' }, 'Choose statuses'],
  [{ ecasId: 'abc' }, 'positive ECAS ID'],
  [{ ecasId: '000' }, 'positive ECAS ID'],
  [{ licence: 'A1-2' }, 'letters and numbers'],
] as [Partial<Filters>, string][])(
  'blocks an invalid combination without issuing a search: %j',
  (partial, error) => {
    const submit = vi.fn()
    render(<Form onSearch={submit} initial={{ ...empty, ...partial }} />)
    expect(screen.getByRole('button', { name: 'Search' })).toBeDisabled()
    expect(screen.getAllByText(new RegExp(error)).length).toBeGreaterThan(0)
    expect(submit).not.toHaveBeenCalled()
  },
)

test('reset clears invalid typed dates and selected filters', async () => {
  const user = userEvent.setup()
  render(
    <Form
      onSearch={vi.fn()}
      initial={{
        ...empty,
        appraisalMethod: 'I',
        dateTypes: ['EFFCTV'],
        dateFrom: '2026-02-30',
        statusCodes: ['DFT'],
        sortBy: 'STATUS',
      }}
    />,
  )
  await user.click(screen.getByRole('button', { name: 'Reset' }))
  expect(screen.getByLabelText('From date')).toHaveValue('')
  expect(screen.getByLabelText('Appraisal method')).toHaveValue('')
  expect(screen.getByLabelText('Sort by')).toHaveValue('ECAS_ID')
  expect(screen.getByRole('button', { name: 'Search' })).toBeEnabled()
})

test('lookup loading/error is distinct from an empty successful list and retains base filters on retry', async () => {
  const retry = vi.fn()
  const props = {
    draft: { ...empty, licence: 'A00001' },
    onChange: vi.fn(),
    onSearch: vi.fn(),
    onReset: vi.fn(),
    loading: false,
    onRetryLookups: retry,
  }
  const { rerender } = render(<EcasSearchFilters {...props} lookupLoading />)
  expect(screen.getByText('Loading search choices…')).toBeInTheDocument()
  expect(screen.getByLabelText('Appraisal method')).toBeEnabled()
  expect(screen.getByRole('combobox', { name: /^Statuses/ })).toBeDisabled()
  rerender(
    <EcasSearchFilters
      {...props}
      lookupLoading={false}
      lookupError="The read service is unavailable."
    />,
  )
  await userEvent.setup().click(screen.getByRole('button', { name: 'Retry search choices' }))
  expect(retry).toHaveBeenCalledOnce()
  expect(screen.getByLabelText('Licence')).toHaveValue('A00001')
  rerender(
    <EcasSearchFilters
      {...props}
      lookupLoading={false}
      lookups={{ appraisalMethods: [], appraisalStatuses: [] }}
    />,
  )
  await waitFor(() => expect(screen.getByLabelText('Appraisal method')).toBeEnabled())
  expect(screen.queryByText('Search choices unavailable')).not.toBeInTheDocument()
})

test('calendar validation rejects rollover dates and retains four-digit years without UTC conversion', () => {
  for (const value of ['', '2024-02-29', '0001-01-01', '0099-12-31', '9999-12-31'])
    expect(isValidIsoDate(value)).toBe(true)
  for (const value of [
    '2026-02-29',
    '1900-02-29',
    '2026-04-31',
    '0000-01-01',
    '2026-1-01',
    '2026-01-00',
  ])
    expect(isValidIsoDate(value)).toBe(false)
  const parsed = parseIsoDate('0099-01-01') as Date
  expect(parsed.getFullYear()).toBe(99)
  expect(parsed.getMonth()).toBe(0)
  expect(parsed.getDate()).toBe(1)
})

test('inactive historical statuses remain selectable and carry their original code', async () => {
  const onChange = vi.fn()
  render(
    <EcasSearchFilters
      draft={empty}
      onChange={onChange}
      onSearch={vi.fn()}
      onReset={vi.fn()}
      loading={false}
      lookupLoading={false}
      onRetryLookups={vi.fn()}
      lookups={{
        ...lookups,
        appraisalStatuses: [{ ...code('OLD', 'Historic status'), active: false }],
      }}
    />,
  )
  await userEvent.setup().click(screen.getByRole('combobox', { name: /^Statuses/ }))
  await userEvent
    .setup()
    .click(screen.getByText('OLD — Historic status (inactive)', { exact: true }))
  expect(onChange).toHaveBeenCalledWith({ ...empty, statusCodes: ['OLD'] })
})
