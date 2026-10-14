import { Button, InlineLoading, Select, SelectItem, TextInput } from '@carbon/react'
import type { LicenceMarks } from '@/contracts/appraisal'
import AppNotification from '../AppNotification'
import SearchFilters from '../SearchFilters'

export type GasFilters = { licence: string; timberMark: string }

export default function GasSearchFilters({
  draft,
  licenceMarks,
  onChange,
  onSearch,
  onReset,
  loading = false,
  lookupLoading = false,
  lookupError,
  onRetryLookup,
}: {
  draft: GasFilters
  licenceMarks: LicenceMarks | null
  onChange: (draft: GasFilters) => void
  onSearch: (filters: GasFilters) => void
  onReset: () => void
  loading?: boolean
  lookupLoading?: boolean
  lookupError?: string
  onRetryLookup?: () => void
}) {
  const licence = draft.licence.trim().toUpperCase()
  const hasLicenceMarks = licenceMarks?.licence === licence
  const marks = hasLicenceMarks ? licenceMarks.timberMarks : []
  return (
    <SearchFilters
      title="Appraisal search filters"
      onSearch={() =>
        onSearch({
          licence,
          timberMark: licence
            ? marks.includes(draft.timberMark)
              ? draft.timberMark
              : ''
            : draft.timberMark.trim().toUpperCase(),
        })
      }
      onReset={onReset}
      loading={loading}
      disabled={lookupLoading || Boolean(lookupError)}
    >
      <TextInput
        id="gas-licence"
        labelText="Licence"
        maxLength={10}
        value={draft.licence}
        onChange={(event) => onChange({ licence: event.target.value, timberMark: '' })}
      />
      <div className="taps-filter-field">
        {licence ? (
          <Select
            id="gas-timber-mark"
            labelText="Timber mark"
            value={marks.includes(draft.timberMark) ? draft.timberMark : ''}
            disabled={lookupLoading || Boolean(lookupError)}
            onChange={(event) => onChange({ ...draft, timberMark: event.target.value })}
          >
            <SelectItem value="" text="All marks for this licence" />
            {marks.map((mark) => (
              <SelectItem key={mark} value={mark} text={mark} />
            ))}
          </Select>
        ) : (
          <TextInput
            id="gas-timber-mark"
            labelText="Timber mark"
            maxLength={6}
            value={draft.timberMark}
            onChange={(event) => onChange({ ...draft, timberMark: event.target.value })}
          />
        )}
        {lookupLoading && <InlineLoading description="Loading timber marks…" />}
        {hasLicenceMarks && !lookupLoading && !lookupError && marks.length === 0 && (
          <p role="status">No timber marks returned for this licence.</p>
        )}
        {lookupError && (
          <>
            <AppNotification kind="error" title="Timber marks unavailable" subtitle={lookupError} />
            <Button type="button" kind="tertiary" size="md" onClick={onRetryLookup}>
              Retry timber marks
            </Button>
          </>
        )}
      </div>
    </SearchFilters>
  )
}
