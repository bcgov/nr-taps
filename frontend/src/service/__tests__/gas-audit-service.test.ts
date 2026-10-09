import { expect, test, vi } from 'vitest'
import { gasAuditApi } from '../gas-audit-service'

const { readRequest } = vi.hoisted(() => ({ readRequest: vi.fn() }))
vi.mock('../read-service', () => ({ readRequest }))

test('requests the typed non-appraised history path with a bound page and abort signal', async () => {
  const signal = new AbortController().signal
  await gasAuditApi.history('123/4', 2, signal)
  expect(readRequest).toHaveBeenCalledWith(
    '/api/gas/worksheets/NON_APPRAISED/123%2F4/history?page=2',
    signal,
  )
})
