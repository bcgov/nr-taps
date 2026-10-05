import { Search } from '@carbon/icons-react'
import { Button, Tile } from '@carbon/react'
import { useId, type ReactNode } from 'react'

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
  const id = useId()
  return (
    <Tile className="taps-search-filters">
      <form
        aria-labelledby={id}
        onSubmit={(event) => {
          event.preventDefault()
          if (!loading && !disabled) onSearch()
        }}
      >
        <h2 id={id}>{title}</h2>
        <div className="taps-filter-grid">{children}</div>
        <div className="taps-actions">
          <Button type="submit" size="md" renderIcon={Search} disabled={loading || disabled}>
            {loading ? 'Searching…' : 'Search'}
          </Button>
          {onReset && (
            <Button type="button" kind="secondary" size="md" onClick={onReset} disabled={loading}>
              Reset
            </Button>
          )}
        </div>
      </form>
    </Tile>
  )
}
