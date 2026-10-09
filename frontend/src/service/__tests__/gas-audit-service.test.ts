import { beforeEach, expect, test, vi } from 'vitest'
import { gasAuditApi } from '../gas-audit-service'
import type { GasAuditWorksheetKey } from '@/contracts/gas-audit'
import type * as ReadService from '../read-service'

const { readRequest } = vi.hoisted(() => ({ readRequest: vi.fn() }))
vi.mock('../read-service', async (importOriginal) => ({
  ...(await importOriginal<typeof ReadService>()),
  readRequest,
}))
beforeEach(() => vi.clearAllMocks())

test.each(['APPRAISED', 'NON_APPRAISED'] as const)(
  'requests typed %s history with the encoded ID, page and signal',
  async (type) => {
    const signal = new AbortController().signal
    await gasAuditApi.history({ type, worksheetId: '123/4' }, 2, signal)
    expect(readRequest).toHaveBeenCalledWith(
      `/api/gas/worksheets/${type}/123%2F4/history?page=2`,
      signal,
    )
  },
)

test.each(['HISTORIC', 'UNKNOWN', '../ecas'])(
  'does not request unsupported history family %s',
  async (type) => {
    await expect(
      gasAuditApi.history(
        { type, worksheetId: '123' } as GasAuditWorksheetKey,
        0,
        new AbortController().signal,
      ),
    ).rejects.toMatchObject({ status: 400 })
    expect(readRequest).not.toHaveBeenCalled()
  },
)
