import { expect, test, vi } from 'vitest'
import { attachmentApi } from '../attachment-service'
import { readRequest } from '../read-service'

vi.mock('../read-service', () => ({ readRequest: vi.fn().mockResolvedValue({}) }))

test('encodes the ECAS segment and passes page and cancellation to the shared authenticated reader', async () => {
  const signal = new AbortController().signal
  await attachmentApi.inventory('1001/other', 2, signal)
  expect(readRequest).toHaveBeenCalledWith('/api/ecas/1001%2Fother/attachments?page=2', signal)
})
