import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import type { AuditDetailPage, AuditEvent, AuditHistoryPage } from '@/contracts/audit'
import type { EcasAuditApi } from '@/service/audit-service'
import { ReadApiError } from '@/service/read-service'
import EcasAuditDetails from '../EcasAuditDetails'

const { reloadSession } = vi.hoisted(() => ({
  reloadSession: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/context/auth/AuthContext', () => ({ useAuth: () => ({ reloadSession }) }))

const event: AuditEvent = {
  eventId: '60001',
  userId: 'IDIR\\SYNTHETIC',
  eventDate: '2026-01-01T12:34:56',
  action: { code: 'UPD', description: 'Update' },
  sentToUserId: null,
  submittedFileId: '62001',
  fileName: 'submission.xml',
  commentPreview: 'Stored comment',
  hasMoreComment: true,
  commentsSuppressed: false,
}
const history: AuditHistoryPage = { ecasId: '1001', items: [event], total: 1, page: 0 }
const details: AuditDetailPage = {
  ecasId: '1001',
  event,
  comment: '<script>plain audit text</script>',
  commentTruncated: true,
  items: [
    {
      detailId: '61001',
      userId: null,
      eventDate: null,
      businessIdentifier: '1001',
      tableName: 'APPRAISAL_DATA_SUBMISSION',
      columnName: 'VOLUME',
      previousValue: '0',
      changedValue: null,
      previousValueTruncated: false,
      changedValueTruncated: false,
    },
  ],
  total: 1,
  page: 0,
}
const api = (): EcasAuditApi => ({
  history: vi.fn().mockResolvedValue(history),
  details: vi.fn().mockResolvedValue(details),
})
beforeEach(() => vi.clearAllMocks())

test('history and event details retain plain text, nulls, zero and bounded-comment notice', async () => {
  const reader = api()
  const { container } = render(<EcasAuditDetails ecasId="1001" api={reader} />)
  expect(await screen.findByText('submission.xml')).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'submission.xml' })).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'View event 60001' }))
  expect(await screen.findByText('<script>plain audit text</script>')).toBeInTheDocument()
  expect(container.querySelector('script')).toBeNull()
  expect(screen.getByText(/4,000-character display limit/)).toBeInTheDocument()
  const changes = within(screen.getByRole('table', { name: 'Audit field changes' }))
  expect(changes.getByRole('cell', { name: '0' })).toBeInTheDocument()
  expect(changes.getAllByRole('cell', { name: '—' })).toHaveLength(3)
  expect(reader.details).toHaveBeenCalledWith('1001', '60001', 0, expect.any(AbortSignal))
  expect(screen.getByRole('heading', { name: 'Event 60001' })).toHaveFocus()
  fireEvent.click(screen.getByRole('button', { name: 'Close event details' }))
  expect(screen.getByRole('button', { name: 'View event 60001' })).toHaveFocus()
})

test('import remarks stay suppressed in both views and absent changes are explicit', async () => {
  const reader = api()
  const imported = {
    ...event,
    commentsSuppressed: true,
    hasMoreComment: false,
    commentPreview: null,
  }
  vi.mocked(reader.history).mockResolvedValue({ ...history, items: [imported] })
  vi.mocked(reader.details).mockResolvedValue({
    ...details,
    event: imported,
    comment: null,
    commentTruncated: false,
    items: [],
    total: 0,
  })
  render(<EcasAuditDetails ecasId="1001" api={reader} />)
  expect(await screen.findByText('Not displayed for import events.')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'View event 60001' }))
  expect(await screen.findByText('No field changes on this page.')).toBeInTheDocument()
  expect(screen.getAllByText('Not displayed for import events.')).toHaveLength(2)
})

test('retry recovers safely and history pagination clears selected event details', async () => {
  const reader = api()
  vi.mocked(reader.history)
    .mockRejectedValueOnce(new Error('private database diagnostic'))
    .mockResolvedValue({ ...history, total: 101 })
  render(<EcasAuditDetails ecasId="1001" api={reader} />)
  expect(await screen.findByText('Audit history unavailable')).toBeInTheDocument()
  expect(screen.queryByText('private database diagnostic')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Retry audit history' }))
  fireEvent.click(await screen.findByRole('button', { name: 'View event 60001' }))
  await screen.findByText('<script>plain audit text</script>')
  fireEvent.click(screen.getAllByRole('button', { name: 'Next page' })[0])
  await waitFor(() =>
    expect(reader.history).toHaveBeenCalledWith('1001', 1, expect.any(AbortSignal)),
  )
  expect(screen.queryByRole('region', { name: 'Audit event 60001' })).not.toBeInTheDocument()
})

test('changing parent aborts and ignores a late response from the previous submission', async () => {
  const reader = api()
  let resolveFirst!: (value: AuditHistoryPage) => void
  vi.mocked(reader.history)
    .mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveFirst = resolve
        }),
    )
    .mockResolvedValue({ ecasId: '1002', items: [], total: 0, page: 0 })
  const view = render(<EcasAuditDetails ecasId="1001" api={reader} />)
  await waitFor(() => expect(reader.history).toHaveBeenCalledTimes(1))
  const firstSignal = vi.mocked(reader.history).mock.calls[0][2]
  view.rerender(<EcasAuditDetails ecasId="1002" api={reader} />)
  expect(await screen.findByText('No audit events on this page.')).toBeInTheDocument()
  expect(firstSignal.aborted).toBe(true)
  await act(async () => resolveFirst(history))
  expect(screen.queryByText('submission.xml')).not.toBeInTheDocument()
  expect(reader.details).not.toHaveBeenCalled()
})

test('session expiry refreshes the shared authentication state', async () => {
  const reader = api()
  vi.mocked(reader.history).mockRejectedValue(new ReadApiError(401))
  render(<EcasAuditDetails ecasId="1001" api={reader} />)
  await waitFor(() => expect(reloadSession).toHaveBeenCalledOnce())
})
