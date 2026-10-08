import { Search } from '@carbon/icons-react'
import { Button } from '@carbon/react'
import type { ReactNode } from 'react'

export default function SearchFilters({
  title = 'Search filters',
  children,
  onSearch,
  onReset,
  loading = false,
  disabled = false,
}: {
  title?: string
  children: ReactNode
  onSearch: () => void
  onReset?: () => void
  loading?: boolean
  disabled?: boolean
}) {
  return (
    <section className="taps-search-filters">
      <form
        aria-label={title}
        onSubmit={(event) => {
          event.preventDefault()
          if (!loading && !disabled) onSearch()
        }}
      >
        <div className="taps-filter-grid">{children}</div>
        <div className="taps-actions">
          {onReset && (
            <Button type="button" kind="tertiary" size="md" onClick={onReset} disabled={loading}>
              Clear all
            </Button>
          )}
          <Button type="submit" size="md" renderIcon={Search} disabled={loading || disabled}>
            {loading ? 'Searching…' : 'Search'}
          </Button>
        </div>
      </form>
    </section>
  )
}
