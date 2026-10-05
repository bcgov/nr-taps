import { Button, DataTableSkeleton, InlineLoading } from '@carbon/react'
import type { ReactNode } from 'react'
import AppNotification from './AppNotification'
import EmptyState from './EmptyState'
import TableFrame from './TableFrame'

export default function SearchResultsTableFrame({
  children,
  ariaLabel = 'Search results table',
  loading = false,
  loadingDescription = 'Loading search results…',
  totalItems,
  columnCount = 3,
  error,
  onRetry,
}: {
  children: ReactNode
  ariaLabel?: string
  loading?: boolean
  loadingDescription?: string
  totalItems?: number
  columnCount?: number
  error?: string
  onRetry?: () => void
}) {
  return (
    <section className="taps-results" aria-label="Search results">
      {loading ? (
        <InlineLoading role="status" aria-live="polite" description={loadingDescription} />
      ) : error ? (
        <>
          <AppNotification kind="error" title="Unable to load results" subtitle={error} />
          {onRetry && <Button onClick={onRetry}>Try again</Button>}
        </>
      ) : totalItems !== undefined ? (
        <p role="status">
          {new Intl.NumberFormat('en-CA').format(totalItems)}{' '}
          {totalItems === 1 ? 'result' : 'results'} found
        </p>
      ) : null}
      {!error || loading ? (
        <TableFrame ariaLabel={ariaLabel} busy={loading}>
          {loading ? (
            <DataTableSkeleton
              aria-label={loadingDescription}
              columnCount={columnCount}
              rowCount={5}
              showHeader={false}
              showToolbar={false}
            />
          ) : totalItems === 0 ? (
            <EmptyState title="No results" description="Try changing your search filters." />
          ) : (
            children
          )}
        </TableFrame>
      ) : null}
    </section>
  )
}
