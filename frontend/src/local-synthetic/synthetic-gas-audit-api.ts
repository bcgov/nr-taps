import type { GasAuditEvent } from '@/contracts/gas-audit'
import type { GasAuditApi } from '@/service/gas-audit-service'
import { ReadApiError } from '@/service/read-service'
import { nonAppraisedSample } from './non-appraised-sample'

// Fictional preselected event pages only. This preview does not derive differences or authorize records.
const events: GasAuditEvent[] = Array.from({ length: 12 }, (_, index) => ({
  eventId:
    index === 0
      ? `W:${nonAppraisedSample.key.worksheetId}:1`
      : `R:${nonAppraisedSample.rates[index % 2].rateId}:${index + 1}`,
  rateId: index === 0 ? null : nonAppraisedSample.rates[index % 2].rateId,
  userId: index === 0 ? null : 'IDIR\\SYNTHETIC',
  eventDate: `2026-10-${String(index + 1).padStart(2, '0')}T09:00:00`,
  attribute: index === 0 ? 'Status' : index % 2 === 0 ? 'Development levy' : 'Bonus bid',
  value: index === 0 ? 'Unconfirmed' : index === 2 ? null : '0.00',
  comment: index === 1 ? '<script>Fictional comment as text</script>' : null,
}))

export const syntheticGasAuditApi: GasAuditApi = {
  history: async (worksheetId, page, signal) => {
    await new Promise<void>((resolve) => setTimeout(resolve, 200))
    signal.throwIfAborted()
    if (worksheetId !== nonAppraisedSample.key.worksheetId) throw new ReadApiError(404)
    return {
      key: nonAppraisedSample.key,
      items: events.slice(page * 10, (page + 1) * 10),
      total: events.length,
      page,
      size: 10,
    }
  },
}
