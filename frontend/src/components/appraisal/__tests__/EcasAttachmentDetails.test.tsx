import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import type { EcasAttachmentPage } from '@/contracts/attachments'
import EcasAttachmentDetails from '../EcasAttachmentDetails'
import { ReadApiError } from '@/service/read-service'

const auth = vi.hoisted(() => ({ reloadSession: vi.fn() }))
vi.mock('@/context/auth/AuthContext', () => ({ useAuth: () => auth }))
beforeEach(() => vi.clearAllMocks())

const page: EcasAttachmentPage = {
  ecasId: '1001',
  appraisalMethod: 'C',
  total: 1,
  page: 0,
  size: 50,
  items: [
    {
      documentId: '7001',
      documentType: { code: 'DOC', description: 'Document' },
      fileName: 'sample.pdf',
      description: '<script>literal text</script>',
      transmissionTypeCode: 'E',
      revisionCount: 2,
      createdAt: '2026-01-02T13:45:56',
      updatedAt: null,
    },
  ],
}

test('shows permitted metadata as text, original timestamps, and the explicit ZIP/binary boundary', async () => {
  render(
    <EcasAttachmentDetails ecasId="1001" api={{ inventory: vi.fn().mockResolvedValue(page) }} />,
  )
  expect(await screen.findByRole('table', { name: 'Attachment information' })).toBeInTheDocument()
  expect(screen.getByRole('cell', { name: 'sample.pdf' })).toBeInTheDocument()
  expect(screen.getByText('<script>literal text</script>')).toBeInTheDocument()
  expect(screen.getByText('2026-01-02T13:45:56')).toBeInTheDocument()
  expect(screen.getByRole('cell', { name: '—' })).toBeInTheDocument()
  expect(screen.getByText(/ZIP contents/)).toBeInTheDocument()
  expect(screen.queryByRole('link')).not.toBeInTheDocument()
  expect(
    screen.queryByRole('button', { name: /download|upload|open file/i }),
  ).not.toBeInTheDocument()
})

test('does not imply hidden documents exist when the permitted inventory is empty', async () => {
  render(
    <EcasAttachmentDetails
      ecasId="1001"
      api={{ inventory: vi.fn().mockResolvedValue({ ...page, items: [], total: 0 }) }}
    />,
  )
  expect(await screen.findByRole('status')).toHaveTextContent(
    'No accessible individual documents on this page.',
  )
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

test('discards an old submission response immediately and ignores its late completion', async () => {
  let resolveOld!: (value: EcasAttachmentPage) => void
  const inventory = vi
    .fn()
    .mockImplementationOnce(
      () =>
        new Promise<EcasAttachmentPage>((resolve) => {
          resolveOld = resolve
        }),
    )
    .mockResolvedValue({ ...page, ecasId: '1002', items: [], total: 0 })
  const api = { inventory }
  const { rerender } = render(<EcasAttachmentDetails ecasId="1001" api={api} />)
  await act(async () => {})
  rerender(<EcasAttachmentDetails ecasId="1002" api={api} />)
  expect(
    await screen.findByText('No accessible individual documents on this page.'),
  ).toBeInTheDocument()
  await act(async () => resolveOld(page))
  expect(screen.queryByText('sample.pdf')).not.toBeInTheDocument()
  expect(inventory.mock.calls[0][2].aborted).toBe(true)
})

test('uses safe retry errors and refreshes the session on 401', async () => {
  const inventory = vi.fn().mockRejectedValueOnce(new ReadApiError(503)).mockResolvedValue(page)
  const { unmount } = render(<EcasAttachmentDetails ecasId="1001" api={{ inventory }} />)
  await userEvent.click(await screen.findByRole('button', { name: 'Retry attachments' }))
  expect(await screen.findByText('sample.pdf')).toBeInTheDocument()
  unmount()
  render(
    <EcasAttachmentDetails
      ecasId="1001"
      api={{ inventory: vi.fn().mockRejectedValue(new ReadApiError(401)) }}
    />,
  )
  expect(await screen.findByText('Your session has ended. Log in again.')).toBeInTheDocument()
  await waitFor(() => expect(auth.reloadSession).toHaveBeenCalledOnce())
})
