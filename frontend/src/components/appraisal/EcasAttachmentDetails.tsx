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
import { useCallback, useState } from 'react'
import { attachmentApi, type AttachmentApi } from '@/service/attachment-service'
import AppNotification from '../AppNotification'
import TableFrame from '../TableFrame'
import useReadResource, { useLoadedTotal } from './useReadResource'
import useReadSessionFailure from './useReadSessionFailure'

const transmission = (code: string | null) =>
  code === 'E'
    ? 'Electronic'
    : code === 'P'
      ? 'Paper copy to follow'
      : code === 'N'
        ? 'Not applicable'
        : (code ?? '—')

function AttachmentInventory({ ecasId, api }: { ecasId: string; api: AttachmentApi }) {
  const [page, setPage] = useState(0)
  const load = useCallback(
    (signal: AbortSignal) => api.inventory(ecasId, page, signal),
    [api, ecasId, page],
  )
  const result = useReadResource(load)
  const total = useLoadedTotal(result.value?.total)
  useReadSessionFailure(result.error)
  return (
    <section aria-label="Attachment inventory">
      <h3>Attachments</h3>
      <p>
        Details recorded for individual documents. ZIP contents and opening, downloading or
        uploading files aren't available yet.
      </p>
      {result.loading && <InlineLoading description="Loading attachment information…" />}
      {result.error && (
        <>
          <AppNotification
            kind="error"
            title="Attachment information unavailable"
            subtitle={result.error.message}
          />
          <Button kind="tertiary" onClick={result.retry}>
            Retry attachments
          </Button>
        </>
      )}
      {result.value && (
        <>
          {result.value.items.length === 0 ? (
            <p role="status">No accessible individual documents on this page.</p>
          ) : (
            <TableFrame ariaLabel="Attachment information rows">
              <Table useZebraStyles size="md" aria-label="Attachment information">
                <TableHead>
                  <TableRow>
                    {[
                      'File name',
                      'Document type',
                      'Description',
                      'Delivery',
                      'Revision',
                      'Created',
                      'Updated',
                    ].map((label) => (
                      <TableHeader key={label}>{label}</TableHeader>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {result.value.items.map((item) => (
                    <TableRow key={item.documentId}>
                      <TableCell>{item.fileName ?? '—'}</TableCell>
                      <TableCell>
                        {item.documentType.description
                          ? `${item.documentType.code} — ${item.documentType.description}`
                          : item.documentType.code}
                      </TableCell>
                      <TableCell>{item.description ?? '—'}</TableCell>
                      <TableCell>{transmission(item.transmissionTypeCode)}</TableCell>
                      <TableCell>{item.revisionCount ?? '—'}</TableCell>
                      <TableCell>{item.createdAt ?? '—'}</TableCell>
                      <TableCell>{item.updatedAt ?? '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableFrame>
          )}
        </>
      )}
      {total !== undefined && !result.error && (
        <Pagination
          page={page + 1}
          pageSize={50}
          pageSizes={[50]}
          totalItems={total}
          onChange={({ page }) => setPage(page - 1)}
        />
      )}
    </section>
  )
}

export default function EcasAttachmentDetails({
  ecasId,
  api = attachmentApi,
}: {
  ecasId: string
  api?: AttachmentApi
}) {
  // Remount per submission so paging and in-flight requests reset.
  return <AttachmentInventory key={ecasId} ecasId={ecasId} api={api} />
}
