import type { GasAuditHistoryPage, GasAuditWorksheetKey } from '@/contracts/gas-audit'
import { readRequest, ReadApiError } from './read-service'

export interface GasAuditApi {
  history(
    key: GasAuditWorksheetKey,
    page: number,
    signal: AbortSignal,
  ): Promise<GasAuditHistoryPage>
}

export const gasAuditApi: GasAuditApi = {
  history: (key, page, signal) => {
    if (key.type !== 'APPRAISED' && key.type !== 'NON_APPRAISED')
      return Promise.reject(new ReadApiError(400))
    return readRequest(
      `/api/gas/worksheets/${key.type}/${encodeURIComponent(key.worksheetId)}/history?page=${page}`,
      signal,
    )
  },
}
