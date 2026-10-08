import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  InlineLoading,
} from '@carbon/react'
import {
  ecasRowIdentity,
  gasRowIdentity,
  rowsWithKeys,
  type CodeOption,
  type EcasInboxItem,
  type GasAppraisalItem,
  type GasSearchResult,
} from '@/contracts/appraisal'
import SearchResultsTableFrame from '../SearchResultsTableFrame'
import AppNotification from '../AppNotification'
import './appraisal.scss'

export const displayCode = (value: CodeOption | null) => value?.description ?? value?.code ?? '—'

export function EcasInboxResults({
  items,
  onOpen,
  totalItems = items.length,
  loading = false,
  error,
  onRetry,
}: {
  items: EcasInboxItem[]
  onOpen: (item: EcasInboxItem, launcher: HTMLButtonElement) => void
  totalItems?: number
  loading?: boolean
  error?: string
  onRetry?: () => void
}) {
  return (
    <SearchResultsTableFrame
      ariaLabel="ECAS submissions"
      totalItems={totalItems}
      columnCount={8}
      loading={loading}
      error={error}
      onRetry={onRetry}
    >
      <Table
        useZebraStyles
        size="md"
        aria-label="ECAS submissions"
        className="taps-appraisal-table"
      >
        <TableHead>
          <TableRow>
            {[
              'ECAS ID',
              'Method',
              'Timber mark',
              'Licence',
              'Cutting permit',
              'Client',
              'Status',
              'Effective date',
            ].map((label) => (
              <TableHeader key={label}>{label}</TableHeader>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rowsWithKeys(items, ecasRowIdentity).map(({ item, key }) => (
            <TableRow key={key}>
              <TableCell>
                <Button
                  kind="ghost"
                  size="sm"
                  aria-label={`Open ECAS ${item.ecasId}, mark ${item.timberMark ?? 'unknown'}, permit ${item.cuttingPermit ?? 'unknown'}`}
                  onClick={(event) => onOpen(item, event.currentTarget)}
                >
                  {item.ecasId}
                </Button>
              </TableCell>
              <TableCell>
                {item.appraisalMethod === 'C'
                  ? 'Coast'
                  : item.appraisalMethod === 'I'
                    ? 'Interior'
                    : '—'}
              </TableCell>
              <TableCell>{item.timberMark ?? '—'}</TableCell>
              <TableCell>{item.licence ?? '—'}</TableCell>
              <TableCell>{item.cuttingPermit ?? '—'}</TableCell>
              <TableCell>{item.clientName ?? item.clientNumber ?? '—'}</TableCell>
              <TableCell>{displayCode(item.status)}</TableCell>
              <TableCell>{item.effectiveDate ?? '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </SearchResultsTableFrame>
  )
}

export function GasSearchResults({
  result,
  onOpen,
  allFamilies = false,
  loading = false,
  error,
  onRetry,
  contextLoading = false,
  contextError,
  onRetryContext,
}: {
  result: GasSearchResult
  onOpen: (item: GasAppraisalItem, launcher: HTMLButtonElement) => void
  allFamilies?: boolean
  loading?: boolean
  error?: string
  onRetry?: () => void
  contextLoading?: boolean
  contextError?: string
  onRetryContext?: () => void
}) {
  const info = result.licenceInformation
  return (
    <>
      <section aria-label="Licence information" className="taps-licence-information">
        <h2>Licence information</h2>
        {contextLoading ? (
          <InlineLoading description="Loading licence information…" />
        ) : contextError ? (
          <>
            <AppNotification
              kind="error"
              title="Unable to load licence information"
              subtitle={contextError}
            />
            <Button kind="tertiary" onClick={onRetryContext}>
              Retry licence information
            </Button>
          </>
        ) : info ? (
          <dl className="taps-appraisal-fields">
            {(
              [
                ['Client number', info.clientNumber],
                ['Licensee', info.licenseeName],
                ['Licence', info.licenceNumber],
                ['Cutting permit', info.cuttingPermit],
                ['File type', info.fileTypeCode],
                ['Timber mark', info.timberMark],
                ['Forest region', info.forestRegion],
                ['Forest district', info.forestDistrict],
                ['Mark expiry date', info.markExpiryDate],
                ['Mark extension date', info.markExtendDate],
                ['Licence status', info.ftaStatus],
              ] as const
            ).map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value ?? '—'}</dd>
              </div>
            ))}
          </dl>
        ) : (
          <p>No licence information is available for this search.</p>
        )}
      </section>
      <SearchResultsTableFrame
        ariaLabel="GAS worksheets"
        totalItems={result.appraisals.total}
        columnCount={7}
        loading={loading}
        error={error}
        onRetry={onRetry}
      >
        <Table
          useZebraStyles
          size="md"
          aria-label="GAS worksheets"
          className="taps-appraisal-table"
        >
          <TableHead>
            <TableRow>
              {[
                'Worksheet',
                'Type',
                'Timber mark',
                'Licence',
                'Status',
                'Effective date',
                'Expiry date',
              ].map((label) => (
                <TableHeader key={label}>{label}</TableHeader>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {rowsWithKeys(result.appraisals.items, gasRowIdentity).map(({ item, key }) => (
              <TableRow key={key}>
                <TableCell>
                  {allFamilies || item.key.type === 'APPRAISED' ? (
                    <Button
                      kind="ghost"
                      size="sm"
                      aria-label={`Open ${item.key.type} worksheet ${item.key.worksheetId}, mark ${item.timberMark ?? 'unknown'}`}
                      onClick={(event) => onOpen(item, event.currentTarget)}
                    >
                      {item.key.worksheetId}
                    </Button>
                  ) : (
                    item.key.worksheetId
                  )}
                </TableCell>
                <TableCell>
                  {
                    {
                      APPRAISED: 'Appraised',
                      NON_APPRAISED: 'Non-appraised',
                      HISTORIC: 'Historic',
                    }[item.key.type]
                  }
                </TableCell>
                <TableCell>{item.timberMark ?? '—'}</TableCell>
                <TableCell>{item.licence ?? '—'}</TableCell>
                <TableCell>{displayCode(item.status)}</TableCell>
                <TableCell>{item.effectiveDate ?? '—'}</TableCell>
                <TableCell>{item.expiryDate ?? '—'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </SearchResultsTableFrame>
    </>
  )
}
