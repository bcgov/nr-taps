import { render, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { expect, test, vi } from 'vitest'
import { setViewportWidth } from '@/test-setup'
import {
  ecasRowIdentity,
  gasRowIdentity,
  rowsWithKeys,
  type GasAppraisalItem,
} from '@/contracts/appraisal'
import WorkflowPreview, { workflowFixture as fixture } from '@/local-synthetic/WorkflowPreview'
import { EcasInboxResults, GasSearchResults } from '../AppraisalResults'
import GasSearchFilters, { type GasFilters } from '../GasSearchFilters'

const { reloadSession } = vi.hoisted(() => ({ reloadSession: vi.fn(async () => {}) }))
vi.mock('@/context/auth/AuthContext', () => ({ useAuth: () => ({ reloadSession }) }))

test('retains ECAS marks and permits sharing the same submission and opens the selected row', async () => {
  const user = userEvent.setup()
  const onOpen = vi.fn()
  render(<EcasInboxResults items={fixture.ecasInboxMultiMarkItems} onOpen={onOpen} />)
  const table = screen.getByRole('table', { name: 'ECAS submissions' })
  expect(within(table).getAllByRole('row')).toHaveLength(4)
  expect(within(table).getAllByRole('cell', { name: 'ZZ9998' })).toHaveLength(2)
  expect(within(table).getByRole('cell', { name: 'ZZ9997' })).toBeInTheDocument()
  const launcher = within(table).getByRole('button', { name: /mark ZZ9998, permit 003/ })
  await user.click(launcher)
  expect(onOpen).toHaveBeenCalledWith(
    expect.objectContaining({ ecasId: '999900000002', timberMark: 'ZZ9998', cuttingPermit: '003' }),
    launcher,
  )
})

test('keeps GAS marks and worksheet families distinct and preserves rows across reordering', () => {
  const appraised = fixture.gasSearchResult.appraisals.items
  const historic: GasAppraisalItem = {
    ...appraised[0],
    key: { ...appraised[0].key, type: 'HISTORIC' },
  }
  const items = [...appraised, historic]
  const result = { ...fixture.gasSearchResult, appraisals: { items, total: 3, page: 0 } }
  const { rerender } = render(<GasSearchResults result={result} onOpen={vi.fn()} />)
  expect(
    within(screen.getByRole('table', { name: 'GAS worksheets' })).getAllByRole('row'),
  ).toHaveLength(4)
  expect(screen.getByRole('cell', { name: 'Historic' })).toBeInTheDocument()
  rerender(
    <GasSearchResults
      result={{ ...result, appraisals: { ...result.appraisals, items: [...items].reverse() } }}
      onOpen={vi.fn()}
    />,
  )
  expect(
    screen.getByRole('button', { name: /APPRAISED worksheet .*mark ZZ9997/ }),
  ).toBeInTheDocument()
  expect(
    screen.getByRole('button', { name: /APPRAISED worksheet .*mark ZZ9998/ }),
  ).toBeInTheDocument()
  expect(screen.getAllByRole('cell', { name: 'ZZ9998' })).toHaveLength(2)
})

test('retains identical projected rows without duplicate React keys or record-ID deduplication', () => {
  const item = fixture.ecasInboxMultiMarkItems[0]
  const rows = rowsWithKeys([item, item, ...fixture.ecasInboxMultiMarkItems], ecasRowIdentity)
  expect(rows).toHaveLength(5)
  expect(new Set(rows.map((row) => row.key)).size).toBe(5)
  expect(
    rowsWithKeys(fixture.gasSearchResult.appraisals.items, gasRowIdentity).map((row) => row.key),
  ).toHaveLength(2)
  render(<EcasInboxResults items={rows.map((row) => row.item)} onOpen={vi.fn()} />)
  expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(6)
})

test('shows all FTA fields independently when no worksheet rows exist', () => {
  render(<GasSearchResults result={fixture.gasSearchResultWithoutAppraisals} onOpen={vi.fn()} />)
  const info = screen.getByRole('region', { name: 'Licence information' })
  expect(within(info).getByText('ZZ9996', { exact: true })).toBeInTheDocument()
  expect(within(info).getAllByRole('term')).toHaveLength(11)
  expect(screen.getByRole('status')).toHaveTextContent('0 results found')
  expect(screen.getByRole('heading', { name: 'No results' })).toBeInTheDocument()
})

test('preserves aggregated permits, nullable licence values and rows without FTA data', () => {
  const { rerender } = render(
    <GasSearchResults result={fixture.gasSearchResult} onOpen={vi.fn()} />,
  )
  expect(
    within(screen.getByRole('region', { name: 'Licence information' })).getByText('002,003', {
      exact: true,
    }),
  ).toBeInTheDocument()
  rerender(
    <GasSearchResults
      result={{ ...fixture.gasSearchResult, licenceInformation: null }}
      onOpen={vi.fn()}
    />,
  )
  expect(
    screen.getByText('No licence information is available for this search.'),
  ).toBeInTheDocument()
  expect(
    within(screen.getByRole('table', { name: 'GAS worksheets' })).getAllByRole('row'),
  ).toHaveLength(3)
})

function FilterExample({ onSearch }: { onSearch: (filters: GasFilters) => void }) {
  const [draft, setDraft] = useState({ licence: 'X99998', timberMark: 'ZZ9998' })
  return (
    <GasSearchFilters
      draft={draft}
      licenceMarks={fixture.gasLicenceMarks}
      onChange={setDraft}
      onSearch={onSearch}
      onReset={() => setDraft({ licence: '', timberMark: '' })}
    />
  )
}

test('licence changes clear the selected mark and cannot retain the previous licence choices', async () => {
  const user = userEvent.setup()
  const onSearch = vi.fn()
  render(<FilterExample onSearch={onSearch} />)
  expect(screen.getByRole('combobox', { name: 'Timber mark' })).toHaveValue('ZZ9998')
  await user.clear(screen.getByRole('textbox', { name: 'Licence' }))
  expect(screen.getByRole('textbox', { name: 'Timber mark' })).toHaveValue('')
  await user.type(screen.getByRole('textbox', { name: 'Licence' }), 'X99999')
  expect(screen.getByRole('combobox', { name: 'Timber mark' })).toHaveValue('')
  expect(screen.queryByRole('option', { name: 'ZZ9998' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Search' }))
  expect(onSearch).toHaveBeenCalledWith({ licence: 'X99999', timberMark: '' })
})

test('submits the displayed selection when a licence lookup no longer contains the selected mark', async () => {
  const user = userEvent.setup()
  const onSearch = vi.fn()
  render(
    <GasSearchFilters
      draft={{ licence: 'X99998', timberMark: 'ZZ9998' }}
      licenceMarks={{ licence: 'X99998', timberMarks: [] }}
      onChange={vi.fn()}
      onSearch={onSearch}
      onReset={vi.fn()}
    />,
  )
  expect(screen.getByRole('combobox', { name: 'Timber mark' })).toHaveValue('')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  expect(onSearch).toHaveBeenCalledWith({ licence: 'X99998', timberMark: '' })
})

function renderWorkflow() {
  setViewportWidth(1440)
  return render(
    <main id="main-content" tabIndex={-1}>
      <WorkflowPreview />
    </main>,
  )
}

test('connects the Coast reference to both related GAS mark rows and exact stored rates', async () => {
  const user = userEvent.setup()
  renderWorkflow()
  await user.click(screen.getByRole('button', { name: /Open ECAS .*mark ZZ9997/ }))
  const reference = screen.getByRole('complementary', { name: 'Coast reference 999900000002' })
  expect(within(reference).getByRole('heading', { name: 'Coast reference' })).toBeInTheDocument()
  expect(within(reference).getByText(/ZZ9997 — volume/)).toBeInTheDocument()
  await user.click(within(reference).getByRole('button', { name: 'View related GAS worksheets' }))
  expect(screen.getByText('Related to ECAS 999900000002')).toBeInTheDocument()
  const table = screen.getByRole('table', { name: 'GAS worksheets' })
  expect(within(table).getAllByRole('row')).toHaveLength(3)
  const launcher = within(table).getByRole('button', { name: /mark ZZ9997/ })
  await user.click(launcher)
  const summary = screen.getByRole('complementary', { name: 'Appraised worksheet 999900000011' })
  expect(within(summary).getByText('COAST_MPS_TOA_N')).toBeInTheDocument()
  expect(within(summary).getByRole('cell', { name: '12.30' })).toBeInTheDocument()
  await user.click(within(summary).getByRole('button', { name: 'Close' }))
  await waitFor(() => expect(launcher).toHaveFocus())
})

test('keeps Interior reference fields separate and links its own worksheet', async () => {
  const user = userEvent.setup()
  renderWorkflow()
  await user.click(screen.getByRole('button', { name: /Open ECAS 999900000001/ }))
  const reference = screen.getByRole('complementary', { name: 'Interior reference 999900000001' })
  expect(within(reference).getByText('Comparative cruise')).toBeInTheDocument()
  expect(within(reference).queryByText('Net cruise volume')).not.toBeInTheDocument()
  await user.click(within(reference).getByRole('button', { name: 'View related GAS worksheets' }))
  expect(
    screen.getByRole('button', { name: /APPRAISED worksheet 999900000010/ }),
  ).toBeInTheDocument()
  expect(
    screen.queryByRole('button', { name: /APPRAISED worksheet 999900000011/ }),
  ).not.toBeInTheDocument()
})

test('applies the selected licence mark on Search and retains FTA context with zero worksheets', async () => {
  const user = userEvent.setup()
  renderWorkflow()
  await user.click(screen.getByRole('button', { name: 'GAS appraisal search' }))
  await user.type(screen.getByRole('textbox', { name: 'Licence' }), 'X99998')
  await user.selectOptions(screen.getByRole('combobox', { name: 'Timber mark' }), 'ZZ9996')
  expect(screen.getByRole('table', { name: 'GAS worksheets' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Search' }))
  expect(screen.queryByRole('table', { name: 'GAS worksheets' })).not.toBeInTheDocument()
  expect(
    within(screen.getByRole('region', { name: 'Licence information' })).getByText('ZZ9996'),
  ).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Clear all' }))
  expect(
    within(screen.getByRole('table', { name: 'GAS worksheets' })).getAllByRole('row'),
  ).toHaveLength(4)
})

test('treats whitespace-only ECAS and GAS filters as no filter, matching Java normalization', async () => {
  const user = userEvent.setup()
  renderWorkflow()
  await user.type(screen.getByRole('textbox', { name: 'ECAS ID' }), '  ')
  await user.type(screen.getByRole('textbox', { name: 'Licence' }), '  ')
  await user.type(screen.getByRole('textbox', { name: 'Timber mark' }), '  {Enter}')
  expect(
    within(screen.getByRole('table', { name: 'ECAS submissions' })).getAllByRole('row'),
  ).toHaveLength(5)
  await user.click(screen.getByRole('button', { name: 'GAS appraisal search' }))
  await user.type(screen.getByRole('textbox', { name: 'Licence' }), '  ')
  await user.type(screen.getByRole('textbox', { name: 'Timber mark' }), '  {Enter}')
  expect(
    within(screen.getByRole('table', { name: 'GAS worksheets' })).getAllByRole('row'),
  ).toHaveLength(4)
})
