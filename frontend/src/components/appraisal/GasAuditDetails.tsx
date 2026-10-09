import { useCallback, useState } from 'react'
import {
  Accordion,
  AccordionItem,
  Button,
  InlineLoading,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react'
import { gasAuditApi, type GasAuditApi } from '@/service/gas-audit-service'
import { ReadApiError } from '@/service/read-service'
import AppNotification from '../AppNotification'
import TableFrame from '../TableFrame'
import useReadResource, { useLoadedTotal } from './useReadResource'
import useReadSessionFailure from './useReadSessionFailure'
import type { GasAuditWorksheetKey } from '@/contracts/gas-audit'

export default function GasAuditDetails({
  worksheetKey,
  api = gasAuditApi,
}: {
  worksheetKey: GasAuditWorksheetKey
  api?: GasAuditApi
}) {
  return (
    <HistoryAccordion
      key={`${worksheetKey.type}:${worksheetKey.worksheetId}`}
      worksheetKey={worksheetKey}
      api={api}
    />
  )
}

function HistoryAccordion({
  worksheetKey,
  api,
}: {
  worksheetKey: GasAuditWorksheetKey
  api: GasAuditApi
}) {
  const [open, setOpen] = useState(false)
  return (
    <Accordion className="taps-reference-sections">
      <AccordionItem title="History" onHeadingClick={({ isOpen }) => setOpen(isOpen)}>
        {open && <HistoryContents worksheetKey={worksheetKey} api={api} />}
      </AccordionItem>
    </Accordion>
  )
}

function HistoryContents({
  worksheetKey,
  api,
}: {
  worksheetKey: GasAuditWorksheetKey
  api: GasAuditApi
}) {
  const { type, worksheetId } = worksheetKey
  const [page, setPage] = useState(0)
  const history = useReadResource(
    useCallback(
      async (signal: AbortSignal) => {
        const result = await api.history({ type, worksheetId }, page, signal)
        if (
          result.key.type !== type ||
          result.key.worksheetId !== worksheetId ||
          result.page !== page ||
          result.size !== 10
        )
          throw new ReadApiError(503)
        return result
      },
      [api, type, worksheetId, page],
    ),
  )
  const total = useLoadedTotal(history.value?.total)
  useReadSessionFailure(history.error)
  return (
    <section
      aria-label={
        type === 'APPRAISED' ? 'Appraised worksheet history' : 'Non-appraised worksheet history'
      }
    >
      {history.loading && <InlineLoading description="Loading history…" />}
      {history.error && (
        <>
          <AppNotification
            kind="error"
            title="History unavailable"
            subtitle={history.error.message}
          />
          <Button kind="tertiary" size="md" onClick={history.retry}>
            Retry history
          </Button>
        </>
      )}
      {history.value &&
        (history.value.items.length === 0 ? (
          <p>No history events on this page.</p>
        ) : (
          <TableFrame ariaLabel="Worksheet history">
            <Table useZebraStyles size="md" aria-label="Worksheet history">
              <TableHead>
                <TableRow>
                  {['Update user', 'Date modified', 'Attribute', 'Value', 'Comment'].map(
                    (label) => (
                      <TableHeader key={label}>{label}</TableHeader>
                    ),
                  )}
                </TableRow>
              </TableHead>
              <TableBody>
                {history.value.items.map((event) => (
                  <TableRow key={event.eventId}>
                    <TableCell>{event.userId ?? '—'}</TableCell>
                    <TableCell>{event.eventDate}</TableCell>
                    <TableCell>
                      <div>{event.attribute}</div>
                      {event.rateId !== null && (
                        <div>
                          <small>Rate {event.rateId}</small>
                        </div>
                      )}
                    </TableCell>
                    <TableCell>{event.value ?? '—'}</TableCell>
                    <TableCell style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                      {event.comment ?? '—'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableFrame>
        ))}
      {total !== undefined && !history.error && (
        <Pagination
          totalItems={total}
          page={page + 1}
          pageSize={10}
          pageSizes={[10]}
          onChange={({ page: next }) => setPage(next - 1)}
        />
      )}
    </section>
  )
}
