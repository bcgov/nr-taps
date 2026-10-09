import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import type { GasAuditHistoryPage } from '@/contracts/gas-audit'
import type { GasAuditApi } from '@/service/gas-audit-service'
import { ReadApiError } from '@/service/read-service'
import GasAuditDetails from '../GasAuditDetails'

const { reloadSession } = vi.hoisted(() => ({ reloadSession: vi.fn(async () => {}) }))
vi.mock('@/context/auth/AuthContext', () => ({ useAuth: () => ({ reloadSession }) }))

const history: GasAuditHistoryPage = {
  key: { type: 'NON_APPRAISED', worksheetId: '999900000095' },
  items: [
    {
      eventId: 'R:999900000096:2',
      rateId: '999900000096',
      userId: 'IDIR\\SYNTHETIC',
      eventDate: '2026-10-01T12:34:56',
      attribute: '<b>Bonus bid</b>',
      value: '0.00',
      comment: '<script>Plain comment</script>',
    },
    {
      eventId: 'R:999900000097:3',
      rateId: '999900000097',
      userId: null,
      eventDate: '2026-10-02T10:00:00',
      attribute: 'Development levy',
      value: null,
      comment: null,
    },
  ],
  total: 2,
  page: 0,
  size: 10,
}
const api = (): GasAuditApi => ({ history: vi.fn().mockResolvedValue(history) })
beforeEach(() => vi.clearAllMocks())

test.each(['APPRAISED', 'NON_APPRAISED'] as const)(
  'loads %s only on expansion and shows server values, rate identity and markup as plain text',
  async (type) => {
    const user = userEvent.setup()
    const reader = api()
    const worksheetKey = { type, worksheetId: history.key.worksheetId }
    vi.mocked(reader.history).mockResolvedValue({ ...history, key: worksheetKey })
    const { container } = render(<GasAuditDetails worksheetKey={worksheetKey} api={reader} />)
    expect(reader.history).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'History' }))
    const table = within(await screen.findByRole('table', { name: 'Worksheet history' }))
    expect(table.getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      'Update user',
      'Date modified',
      'Attribute',
      'Value',
      'Comment',
    ])
    expect(table.getByText('<b>Bonus bid</b>')).toBeInTheDocument()
    expect(table.getByText('<script>Plain comment</script>')).toBeInTheDocument()
    expect(table.getByRole('cell', { name: '0.00' })).toBeInTheDocument()
    expect(table.getByText('Rate 999900000096')).toBeInTheDocument()
    expect(table.getByText('Rate 999900000097')).toBeInTheDocument()
    expect(table.getAllByRole('cell', { name: '—' })).toHaveLength(3)
    expect(container.querySelector('script, b')).toBeNull()
    expect(reader.history).toHaveBeenCalledWith(worksheetKey, 0, expect.any(AbortSignal))
  },
)

test('switching family with the same worksheet ID resets paging and ignores the prior page error', async () => {
  const user = userEvent.setup()
  const reader = api()
  const appraisedKey = { type: 'APPRAISED' as const, worksheetId: history.key.worksheetId }
  let rejectOld!: (error: Error) => void
  vi.mocked(reader.history)
    .mockResolvedValueOnce({ ...history, total: 12 })
    .mockImplementationOnce(
      () =>
        new Promise((_resolve, reject) => {
          rejectOld = reject
        }),
    )
    .mockResolvedValueOnce({ ...history, key: appraisedKey, items: [], total: 0 })
  const view = render(<GasAuditDetails worksheetKey={history.key} api={reader} />)
  await user.click(screen.getByRole('button', { name: 'History' }))
  await screen.findByRole('table', { name: 'Worksheet history' })
  await user.click(screen.getByRole('button', { name: 'Next page' }))
  await waitFor(() => expect(reader.history).toHaveBeenCalledTimes(2))
  const oldSignal = vi.mocked(reader.history).mock.calls[1][2]
  view.rerender(<GasAuditDetails worksheetKey={appraisedKey} api={reader} />)
  expect(oldSignal.aborted).toBe(true)
  expect(screen.getByRole('button', { name: 'History' })).toHaveAttribute('aria-expanded', 'false')
  await user.click(screen.getByRole('button', { name: 'History' }))
  await screen.findByText('No history events on this page.')
  expect(reader.history).toHaveBeenLastCalledWith(appraisedKey, 0, expect.any(AbortSignal))
  await act(async () => rejectOld(new ReadApiError(503)))
  expect(screen.queryByText('History unavailable')).not.toBeInTheDocument()
  expect(screen.queryByText('<script>Plain comment</script>')).not.toBeInTheDocument()
})

test('retry hides private errors and pagination requests the server page without keeping old rows', async () => {
  const user = userEvent.setup()
  const reader = api()
  vi.mocked(reader.history)
    .mockRejectedValueOnce(new Error('private database details'))
    .mockResolvedValueOnce({ ...history, total: 12 })
    .mockResolvedValueOnce({ ...history, items: [], total: 12, page: 1 })
  render(<GasAuditDetails worksheetKey={history.key} api={reader} />)
  await user.click(screen.getByRole('button', { name: 'History' }))
  await screen.findByText('History unavailable')
  expect(screen.queryByText('private database details')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Retry history' }))
  await screen.findByRole('table', { name: 'Worksheet history' })
  await user.click(screen.getByRole('button', { name: 'Next page' }))
  await screen.findByText('No history events on this page.')
  expect(reader.history).toHaveBeenLastCalledWith(history.key, 1, expect.any(AbortSignal))
  expect(screen.queryByText('<script>Plain comment</script>')).not.toBeInTheDocument()
})

test.each([
  { ...history, key: { type: 'NON_APPRAISED', worksheetId: '999900000999' } },
  { ...history, key: { type: 'APPRAISED', worksheetId: history.key.worksheetId } },
  { ...history, page: 1 },
  { ...history, size: 100 },
])('rejects a history response for another identity or page: %j', async (invalid) => {
  const reader = api()
  vi.mocked(reader.history).mockResolvedValue(invalid as unknown as GasAuditHistoryPage)
  render(<GasAuditDetails worksheetKey={history.key} api={reader} />)
  await userEvent.setup().click(screen.getByRole('button', { name: 'History' }))
  await screen.findByText('History unavailable')
  expect(screen.queryByText('<script>Plain comment</script>')).not.toBeInTheDocument()
  expect(screen.queryByRole('table', { name: 'Worksheet history' })).not.toBeInTheDocument()
})

test('collapsing history aborts the request and ignores late content', async () => {
  const user = userEvent.setup()
  const reader = api()
  let release!: (page: GasAuditHistoryPage) => void
  vi.mocked(reader.history).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        release = resolve
      }),
  )
  render(<GasAuditDetails worksheetKey={history.key} api={reader} />)
  await user.click(screen.getByRole('button', { name: 'History' }))
  await waitFor(() => expect(reader.history).toHaveBeenCalledOnce())
  const signal = vi.mocked(reader.history).mock.calls[0][2]
  await user.click(screen.getByRole('button', { name: 'History' }))
  expect(signal.aborted).toBe(true)
  await act(async () => release(history))
  expect(screen.queryByText('<script>Plain comment</script>')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'History' }))
  await screen.findByRole('table', { name: 'Worksheet history' })
  expect(reader.history).toHaveBeenCalledTimes(2)
})

test('changing worksheet collapses history and discards the previous request and page', async () => {
  const user = userEvent.setup()
  const reader = api()
  let release!: (page: GasAuditHistoryPage) => void
  vi.mocked(reader.history)
    .mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          release = resolve
        }),
    )
    .mockResolvedValue({
      ...history,
      key: { type: 'NON_APPRAISED', worksheetId: '999900000999' },
      items: [],
      total: 0,
    })
  const view = render(<GasAuditDetails worksheetKey={history.key} api={reader} />)
  await user.click(screen.getByRole('button', { name: 'History' }))
  await waitFor(() => expect(reader.history).toHaveBeenCalledOnce())
  const signal = vi.mocked(reader.history).mock.calls[0][2]
  view.rerender(
    <GasAuditDetails
      worksheetKey={{ type: 'NON_APPRAISED', worksheetId: '999900000999' }}
      api={reader}
    />,
  )
  expect(screen.getByRole('button', { name: 'History' })).toHaveAttribute('aria-expanded', 'false')
  expect(signal.aborted).toBe(true)
  await act(async () => release(history))
  await user.click(screen.getByRole('button', { name: 'History' }))
  await screen.findByText('No history events on this page.')
  expect(reader.history).toHaveBeenLastCalledWith(
    { type: 'NON_APPRAISED', worksheetId: '999900000999' },
    0,
    expect.any(AbortSignal),
  )
  expect(screen.queryByText('<script>Plain comment</script>')).not.toBeInTheDocument()
})

test('a rejected session refreshes authentication', async () => {
  const reader = api()
  vi.mocked(reader.history).mockRejectedValue(new ReadApiError(401))
  render(<GasAuditDetails worksheetKey={history.key} api={reader} />)
  await userEvent.setup().click(screen.getByRole('button', { name: 'History' }))
  await waitFor(() => expect(reloadSession).toHaveBeenCalledOnce())
})
