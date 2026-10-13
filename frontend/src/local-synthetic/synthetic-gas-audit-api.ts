import type { GasAuditEvent } from '@/contracts/gas-audit'
import type { GasAuditApi } from '@/service/gas-audit-service'
import { ReadApiError } from '@/service/read-service'
import { nonAppraisedSample } from './non-appraised-sample'
import source from '../../../backend/src/test/resources/contracts/synthetic-workflow.json'

// Fictional preselected event pages only. This preview does not derive differences or authorize records.
const events: GasAuditEvent[] = Array.from({ length: 12 }, (_, index) => ({
  eventId:
    index === 0
      ? `W:${nonAppraisedSample.key.worksheetId}:1`
      : `R:${nonAppraisedSample.rates[index % 2].rateId}:${index + 1}`,
  rateId: index === 0 ? null : nonAppraisedSample.rates[index % 2].rateId,
  userId: index === 0 ? null : 'IDIR\\SYNTHETIC',
  eventDate: `2026-10-${String(12 - index).padStart(2, '0')}T09:00:00`,
  attribute: index === 0 ? 'Status' : index % 2 === 0 ? 'Development levy' : 'Bonus bid',
  value: index === 0 ? 'Unconfirmed' : index === 2 ? null : '0.00',
  comment: index === 1 ? '<script>Fictional comment as text</script>' : null,
}))

export const syntheticGasAuditApi: GasAuditApi = {
  history: async (key, page, signal) => {
    await new Promise<void>((resolve) => setTimeout(resolve, 200))
    signal.throwIfAborted()
    const appraised = [source.gasAppraisedSummary, source.gasMultiMarkAppraisedSummary].find(
      (summary) => summary.key.worksheetId === key.worksheetId,
    )
    const selectedEvents =
      key.type === 'NON_APPRAISED' && key.worksheetId === nonAppraisedSample.key.worksheetId
        ? events
        : key.type === 'APPRAISED' && appraised
          ? Array.from({ length: 12 }, (_, index): GasAuditEvent => ({
              eventId:
                index === 0
                  ? `W:${key.worksheetId}:1`
                  : `R:${appraised.rates[0].rateId}:${index + 1}`,
              rateId: index === 0 ? null : appraised.rates[0].rateId,
              userId: 'IDIR\\SYNTHETIC',
              eventDate: `2026-10-${String(12 - index).padStart(2, '0')}T10:00:00`,
              attribute: index === 0 ? 'Discount Percent' : 'Total stumpage rate',
              value: index === 0 ? '10.0' : appraised.rates[0].totalStumpageRate,
              comment: index === 1 ? '<script>Fictional appraised comment as text</script>' : null,
            }))
          : null
    if (!selectedEvents) throw new ReadApiError(404)
    return {
      key,
      items: selectedEvents.slice(page * 10, (page + 1) * 10),
      total: selectedEvents.length,
      page,
      size: 10,
    }
  },
}
