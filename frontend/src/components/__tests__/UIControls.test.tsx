import { TextInput } from '@carbon/react'
import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { expect, test, vi } from 'vitest'
import { notifyResize } from '@/test-setup'
import PageHeader from '../PageHeader'
import SearchFilters from '../SearchFilters'
import SearchResultsTableFrame from '../SearchResultsTableFrame'
import TableFrame from '../TableFrame'

function FilterExample({
  onSearch,
  onReset,
}: {
  onSearch: (value: string) => void
  onReset: () => void
}) {
  const [draft, setDraft] = useState('')
  return (
    <SearchFilters
      onSearch={() => onSearch(draft)}
      onReset={() => {
        setDraft('')
        onReset()
      }}
    >
      <TextInput
        id="reference-filter"
        labelText="Reference"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
      />
    </SearchFilters>
  )
}

test('keeps caller-owned filters as drafts until Search or Enter, including repeated searches', async () => {
  const user = userEvent.setup()
  const onSearch = vi.fn()
  render(<FilterExample onSearch={onSearch} onReset={vi.fn()} />)

  const input = screen.getByRole('textbox', { name: 'Reference' })
  await user.type(input, 'Draft reference')
  expect(onSearch).not.toHaveBeenCalled()

  await user.click(screen.getByRole('button', { name: 'Search' }))
  expect(onSearch).toHaveBeenNthCalledWith(1, 'Draft reference')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  expect(onSearch).toHaveBeenNthCalledWith(2, 'Draft reference')

  await user.clear(input)
  await user.type(input, 'New reference{Enter}')
  expect(onSearch).toHaveBeenNthCalledWith(3, 'New reference')
  expect(onSearch).toHaveBeenCalledTimes(3)
})

test('lets the caller reset filters without submitting a search', async () => {
  const user = userEvent.setup()
  const onSearch = vi.fn()
  const onReset = vi.fn()
  render(<FilterExample onSearch={onSearch} onReset={onReset} />)

  await user.type(screen.getByRole('textbox', { name: 'Reference' }), 'Draft reference')
  await user.click(screen.getByRole('button', { name: 'Reset' }))

  expect(screen.getByRole('textbox', { name: 'Reference' })).toHaveValue('')
  expect(onReset).toHaveBeenCalledOnce()
  expect(onSearch).not.toHaveBeenCalled()
})

test.each([
  { loading: true, disabled: false, label: 'Searching…', resetDisabled: true },
  { loading: false, disabled: true, label: 'Search', resetDisabled: false },
])('blocks search submission when loading=$loading and disabled=$disabled', async (state) => {
  const user = userEvent.setup()
  const onSearch = vi.fn()
  render(
    <SearchFilters
      onSearch={onSearch}
      onReset={vi.fn()}
      loading={state.loading}
      disabled={state.disabled}
    >
      <TextInput id="reference-filter" labelText="Reference" />
    </SearchFilters>,
  )

  expect(screen.getByRole('button', { name: state.label })).toBeDisabled()
  if (state.resetDisabled) expect(screen.getByRole('button', { name: 'Reset' })).toBeDisabled()
  else expect(screen.getByRole('button', { name: 'Reset' })).toBeEnabled()

  await user.type(screen.getByRole('textbox', { name: 'Reference' }), 'Draft{Enter}')
  fireEvent.submit(screen.getByRole('form', { name: 'Search filters' }))
  expect(onSearch).not.toHaveBeenCalled()
})

test('makes a table overflow region keyboard focusable only while horizontal overflow exists', async () => {
  const user = userEvent.setup()
  render(
    <>
      <TableFrame ariaLabel="Appraisal results">
        <table>
          <tbody>
            <tr>
              <td>Reference</td>
            </tr>
          </tbody>
        </table>
      </TableFrame>
      <button>Continue</button>
    </>,
  )
  const frame = screen.getByRole('region', { name: 'Appraisal results' })
  let scrollWidth = 300
  Object.defineProperties(frame, {
    clientWidth: { configurable: true, value: 300 },
    scrollWidth: { configurable: true, get: () => scrollWidth },
  })
  act(() => notifyResize(frame))
  expect(frame).not.toHaveAttribute('tabindex')
  await user.tab()
  expect(screen.getByRole('button', { name: 'Continue' })).toHaveFocus()

  scrollWidth = 650
  act(() => notifyResize(screen.getByRole('table')))
  expect(frame).toHaveAttribute('tabindex', '0')
  await user.tab({ shift: true })
  expect(frame).toHaveFocus()

  scrollWidth = 300
  fireEvent(window, new Event('resize'))
  expect(frame).not.toHaveAttribute('tabindex')
})

test('stops monitoring table sizes when its overflow region unmounts', () => {
  const { unmount } = render(
    <TableFrame ariaLabel="Appraisal results">
      <table />
    </TableFrame>,
  )
  const frame = screen.getByRole('region', { name: 'Appraisal results' })
  unmount()
  Object.defineProperties(frame, {
    clientWidth: { configurable: true, value: 100 },
    scrollWidth: { configurable: true, value: 500 },
  })

  act(() => notifyResize(frame))
  fireEvent(window, new Event('resize'))
  expect(frame).not.toHaveAttribute('tabindex')
})

const resultTable = (
  <table>
    <tbody>
      <tr>
        <td>Saved reference</td>
      </tr>
    </tbody>
  </table>
)

test('replaces stale table contents with a busy skeleton and then an empty state', () => {
  const { rerender } = render(
    <SearchResultsTableFrame totalItems={1}>{resultTable}</SearchResultsTableFrame>,
  )
  expect(screen.getByRole('status')).toHaveTextContent('1 result found')
  expect(screen.getByRole('cell', { name: 'Saved reference' })).toBeInTheDocument()

  rerender(
    <SearchResultsTableFrame loading totalItems={1} loadingDescription="Refreshing results…">
      {resultTable}
    </SearchResultsTableFrame>,
  )
  expect(screen.getByRole('region', { name: 'Search results table' })).toHaveAttribute(
    'aria-busy',
    'true',
  )
  expect(screen.getByText('Refreshing results…')).toBeInTheDocument()
  expect(screen.queryByRole('cell', { name: 'Saved reference' })).not.toBeInTheDocument()
  expect(screen.queryByText('1 result found')).not.toBeInTheDocument()

  rerender(<SearchResultsTableFrame totalItems={0}>{resultTable}</SearchResultsTableFrame>)
  expect(screen.getByRole('status')).toHaveTextContent('0 results found')
  expect(screen.getByRole('heading', { name: 'No results' })).toBeInTheDocument()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  expect(screen.getByRole('region', { name: 'Search results table' })).toHaveAttribute(
    'aria-busy',
    'false',
  )
})

test('replaces results with an accessible error and lets the caller retry', async () => {
  const user = userEvent.setup()
  const onRetry = vi.fn()
  const { rerender } = render(
    <SearchResultsTableFrame totalItems={2}>{resultTable}</SearchResultsTableFrame>,
  )
  rerender(
    <SearchResultsTableFrame error="The service is unavailable." onRetry={onRetry} totalItems={2}>
      {resultTable}
    </SearchResultsTableFrame>,
  )

  expect(screen.getByRole('alert')).toHaveTextContent('Unable to load results')
  expect(screen.getByRole('alert')).toHaveTextContent('The service is unavailable.')
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  expect(screen.queryByRole('region', { name: 'Search results table' })).not.toBeInTheDocument()
  expect(screen.queryByText('2 results found')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Try again' }))
  expect(onRetry).toHaveBeenCalledOnce()
})

test('shows a loading state while retrying after an earlier error', () => {
  render(
    <SearchResultsTableFrame loading error="An earlier failure" totalItems={1}>
      {resultTable}
    </SearchResultsTableFrame>,
  )

  expect(screen.getByText('Loading search results…')).toBeInTheDocument()
  expect(screen.queryByText('An earlier failure')).not.toBeInTheDocument()
  expect(screen.getByRole('region', { name: 'Search results table' })).toHaveAttribute(
    'aria-busy',
    'true',
  )
  expect(screen.queryByRole('cell', { name: 'Saved reference' })).not.toBeInTheDocument()
})

test('provides one page H1 with separately labelled actions and a back link', () => {
  render(
    <PageHeader
      title="Inbox Search"
      subtitle="Find your submissions."
      backLink={<a href="/ecas">Back to ECAS</a>}
      actions={<button>Help</button>}
    />,
  )

  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Inbox Search')
  expect(screen.getByText('Find your submissions.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Back to ECAS' })).toHaveAttribute('href', '/ecas')
  expect(screen.getByRole('group', { name: 'Page actions' })).toContainElement(
    screen.getByRole('button', { name: 'Help' }),
  )
})
