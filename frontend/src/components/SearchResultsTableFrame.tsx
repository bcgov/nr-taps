import { Button, DataTableSkeleton, InlineLoading } from '@carbon/react'
import type { ReactNode } from 'react'
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
  errorTitle = 'Search unavailable',
  onRetry,
  pagination,
}: {
  children: ReactNode
  ariaLabel?: string
  loading?: boolean
  loadingDescription?: string
  totalItems?: number
  columnCount?: number
  error?: string
  errorTitle?: string
  onRetry?: () => void
  pagination?: ReactNode
}) {
  return (
    <section className="taps-results" aria-label="Search results">
      {error && !loading ? (
        <EmptyState
          role="alert"
          title={errorTitle}
          description={error}
          action={
            onRetry && (
              <Button size="md" onClick={onRetry}>
                Try again
              </Button>
            )
          }
        />
      ) : (
        <div className="taps-results-frame">
          <div className="taps-results-toolbar">
            {loading && <InlineLoading description={loadingDescription} />}
            {/* Stays mounted so each new count is announced. */}
            <p className="taps-results-count" role="status">
              {!loading && totalItems !== undefined
                ? `${new Intl.NumberFormat('en-CA').format(totalItems)} ${totalItems === 1 ? 'result' : 'results'} found`
                : null}
            </p>
          </div>
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
              <EmptyState
                title="No results found"
                description="No records match your search criteria. Try adjusting your filters and searching again."
              />
            ) : (
              children
            )}
          </TableFrame>
          {/* Stays mounted while the next page loads, so focus stays on its controls. */}
          {loading || totalItems !== 0 ? pagination : null}
        </div>
      )}
    </section>
  )
}
