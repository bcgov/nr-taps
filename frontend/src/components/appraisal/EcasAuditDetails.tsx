import { useCallback, useEffect, useRef, useState } from 'react'
import {
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
import { ecasAuditApi, type EcasAuditApi } from '@/service/audit-service'
import AppNotification from '../AppNotification'
import TableFrame from '../TableFrame'
import { displayCode } from './AppraisalResults'
import useReadResource, { useLoadedTotal } from './useReadResource'
import useReadSessionFailure from './useReadSessionFailure'

export default function EcasAuditDetails({
  ecasId,
  api = ecasAuditApi,
}: {
  ecasId: string
  api?: EcasAuditApi
}) {
  // Remount per submission so the selected event and in-flight requests reset.
  return <AuditContents key={ecasId} ecasId={ecasId} api={api} />
}

function AuditContents({ ecasId, api }: { ecasId: string; api: EcasAuditApi }) {
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<{ id: string; page: number } | null>(null)
  const selectedHeadingRef = useRef<HTMLHeadingElement>(null)
  const selectedTriggerRef = useRef<HTMLButtonElement | null>(null)
  const history = useReadResource(
    useCallback((signal: AbortSignal) => api.history(ecasId, page, signal), [api, ecasId, page]),
  )
  const loadDetails = useCallback(
    (signal: AbortSignal) => api.details(ecasId, selected!.id, selected!.page, signal),
    [api, ecasId, selected],
  )
  const detail = useReadResource(selected ? loadDetails : null)
  const historyTotal = useLoadedTotal(history.value?.total)
  const detailTotal = useLoadedTotal(detail.value?.total, selected?.id)
  useReadSessionFailure(history.error)
  useReadSessionFailure(detail.error)
  useEffect(() => {
    if (selected?.id) selectedHeadingRef.current?.focus()
  }, [selected?.id])

  return (
    <section aria-label="Submission audit history">
      <h3>Audit history</h3>
      {history.loading && <InlineLoading description="Loading audit history…" />}
      {history.error && (
        <>
          <AppNotification
            kind="error"
            title="Audit history unavailable"
            subtitle={history.error.message}
          />
          <Button kind="tertiary" onClick={history.retry}>
            Retry audit history
          </Button>
        </>
      )}
      {history.value && (
        <>
          {history.value.items.length === 0 ? (
            <p>No audit events on this page.</p>
          ) : (
            <TableFrame ariaLabel="Audit events">
              <Table useZebraStyles size="md" aria-label="Audit events">
                <TableHead>
                  <TableRow>
                    {[
                      'Date and time',
                      'User',
                      'Event',
                      'Sent to',
                      'File',
                      'Comments',
                      'Details',
                    ].map((label) => (
                      <TableHeader key={label}>{label}</TableHeader>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {history.value.items.map((event) => (
                    <TableRow key={event.eventId}>
                      <TableCell>{event.eventDate ?? '—'}</TableCell>
                      <TableCell>{event.userId ?? '—'}</TableCell>
                      <TableCell>{displayCode(event.action)}</TableCell>
                      <TableCell>{event.sentToUserId ?? '—'}</TableCell>
                      <TableCell>{event.fileName ?? '—'}</TableCell>
                      <TableCell>
                        {event.commentsSuppressed ? (
                          'Not displayed for import events.'
                        ) : (
                          <>
                            {event.commentPreview ?? '—'}
                            {event.hasMoreComment ? '…' : ''}
                          </>
                        )}
                      </TableCell>
                      <TableCell>
                        <Button
                          size="sm"
                          kind="ghost"
                          onClick={(click) => {
                            selectedTriggerRef.current = click.currentTarget
                            setSelected({ id: event.eventId, page: 0 })
                          }}
                        >
                          View event {event.eventId}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableFrame>
          )}
        </>
      )}
      {historyTotal !== undefined && !history.error && (
        <Pagination
          totalItems={historyTotal}
          page={page + 1}
          pageSize={100}
          pageSizes={[100]}
          onChange={({ page: next }) => {
            setPage(next - 1)
            setSelected(null)
          }}
        />
      )}
      {selected && (
        <section aria-label={`Audit event ${selected.id}`}>
          <h3 ref={selectedHeadingRef} tabIndex={-1}>
            Event {selected.id}
          </h3>
          <Button
            kind="ghost"
            size="sm"
            onClick={() => {
              setSelected(null)
              selectedTriggerRef.current?.focus()
            }}
          >
            Close event details
          </Button>
          {detail.loading && <InlineLoading description="Loading event details…" />}
          {detail.error && (
            <>
              <AppNotification
                kind="error"
                title="Event details unavailable"
                subtitle={detail.error.message}
              />
              <Button kind="tertiary" onClick={detail.retry}>
                Retry event details
              </Button>
            </>
          )}
          {detail.value && (
            <>
              <h4>Comment</h4>
              <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                {detail.value.event.commentsSuppressed
                  ? 'Not displayed for import events.'
                  : (detail.value.comment ?? 'No comment recorded.')}
              </p>
              {detail.value.commentTruncated && (
                <p>This comment continues beyond the 4,000-character display limit.</p>
              )}
              <h4>Field changes</h4>
              {detail.value.items.length === 0 ? (
                <p>No field changes on this page.</p>
              ) : (
                <TableFrame ariaLabel="Audit field changes">
                  <Table useZebraStyles size="md" aria-label="Audit field changes">
                    <TableHead>
                      <TableRow>
                        {[
                          'User',
                          'Date and time',
                          'Data location',
                          'Identifier',
                          'Field',
                          'Previous value',
                          'Changed value',
                        ].map((label) => (
                          <TableHeader key={label}>{label}</TableHeader>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {detail.value.items.map((change) => (
                        <TableRow key={change.detailId}>
                          <TableCell>{change.userId ?? '—'}</TableCell>
                          <TableCell>{change.eventDate ?? '—'}</TableCell>
                          <TableCell>{change.tableName ?? '—'}</TableCell>
                          <TableCell>{change.businessIdentifier ?? '—'}</TableCell>
                          <TableCell>{change.columnName ?? '—'}</TableCell>
                          <TableCell>
                            {change.previousValue ?? '—'}
                            {change.previousValueTruncated && ' (truncated)'}
                          </TableCell>
                          <TableCell>
                            {change.changedValue ?? '—'}
                            {change.changedValueTruncated && ' (truncated)'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableFrame>
              )}
            </>
          )}
          {detailTotal !== undefined && !detail.error && (
            <Pagination
              totalItems={detailTotal}
              page={selected.page + 1}
              pageSize={100}
              pageSizes={[100]}
              onChange={({ page: next }) => setSelected({ id: selected.id, page: next - 1 })}
            />
          )}
        </section>
      )}
    </section>
  )
}
