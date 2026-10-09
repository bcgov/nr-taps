import type { GasAuditHistoryPage } from '@/contracts/gas-audit'
import { readRequest } from './read-service'

export interface GasAuditApi {
  history(worksheetId: string, page: number, signal: AbortSignal): Promise<GasAuditHistoryPage>
}

export const gasAuditApi: GasAuditApi = {
  history: (id, page, signal) =>
    readRequest(
      `/api/gas/worksheets/NON_APPRAISED/${encodeURIComponent(id)}/history?page=${page}`,
      signal,
    ),
}
