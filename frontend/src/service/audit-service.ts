import type { AuditDetailPage, AuditHistoryPage } from '@/contracts/audit'
import { readRequest } from './read-service'

export interface EcasAuditApi {
  history(ecasId: string, page: number, signal: AbortSignal): Promise<AuditHistoryPage>
  details(
    ecasId: string,
    eventId: string,
    page: number,
    signal: AbortSignal,
  ): Promise<AuditDetailPage>
}

export const ecasAuditApi: EcasAuditApi = {
  history: (id, page, signal) =>
    readRequest(`/api/ecas/audit/${encodeURIComponent(id)}?page=${page}`, signal),
  details: (id, eventId, page, signal) =>
    readRequest(
      `/api/ecas/audit/${encodeURIComponent(id)}/events/${encodeURIComponent(eventId)}?page=${page}`,
      signal,
    ),
}
