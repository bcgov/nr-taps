import {
  Button,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react'
import { useRef, useState } from 'react'
import DetailSidePanel from '@/components/DetailSidePanel'
import PageHeader from '@/components/PageHeader'
import SearchFilters from '@/components/SearchFilters'
import SearchResultsTableFrame from '@/components/SearchResultsTableFrame'

const sampleRows = [
  {
    label: 'Synthetic example A',
    state: 'Ready',
    notes: 'Synthetic row for checking table spacing and a detail drawer.',
  },
  {
    label: 'Synthetic example B',
    state: 'Review',
    notes: 'Synthetic row for checking a second preview state.',
  },
  {
    label: 'Synthetic example C',
    state: 'Ready',
    notes: 'Synthetic row for checking text wrapping and narrow-screen table overflow.',
  },
]

type SampleRow = (typeof sampleRows)[number]
type ResultsState = 'ready' | 'loading' | 'empty' | 'error'

export default function ControlsSample() {
  const [draft, setDraft] = useState({ label: '', state: '' })
  const [applied, setApplied] = useState(draft)
  const [resultsState, setResultsState] = useState<ResultsState>('ready')
  const [selectedRow, setSelectedRow] = useState<SampleRow | null>(null)
  const launcherRef = useRef<HTMLElement | null>(null)
  const rows =
    resultsState === 'empty'
      ? []
      : sampleRows.filter(
          (row) =>
            row.label.toLowerCase().includes(applied.label.trim().toLowerCase()) &&
            (!applied.state || row.state === applied.state),
        )

  return (
    <section
      className="taps-page taps-fullbleed-page taps-preview-samples"
      aria-label="Synthetic controls sample"
    >
      <PageHeader
        title="Synthetic reusable controls"
        subtitle="Presentation examples only. These rows do not represent TAPS business records."
        actions={
          <Button
            kind="tertiary"
            size="md"
            onClick={() => {
              setDraft({ label: '', state: '' })
              setApplied({ label: '', state: '' })
              setResultsState('ready')
            }}
          >
            Reset sample
          </Button>
        }
      />
      <SearchFilters
        title="Synthetic sample filters"
        onSearch={() => setApplied(draft)}
        onReset={() => {
          setDraft({ label: '', state: '' })
          setApplied({ label: '', state: '' })
        }}
        loading={resultsState === 'loading'}
      >
        <TextInput
          id="synthetic-label-filter"
          labelText="Example label contains"
          helperText="Apply draft filters with Search or Enter."
          value={draft.label}
          onChange={(event) => setDraft({ ...draft, label: event.target.value })}
        />
        <Select
          id="synthetic-state-filter"
          labelText="Preview state"
          value={draft.state}
          onChange={(event) => setDraft({ ...draft, state: event.target.value })}
        >
          <SelectItem value="" text="All synthetic states" />
          <SelectItem value="Ready" text="Synthetic Ready state" />
          <SelectItem value="Review" text="Synthetic Review state" />
        </Select>
      </SearchFilters>

      <div className="taps-preview-results-controls">
        <h2 id="synthetic-results-heading" tabIndex={-1}>
          Synthetic results
        </h2>
        <Select
          id="synthetic-results-state"
          labelText="Synthetic results display"
          value={resultsState}
          onChange={(event) => setResultsState(event.target.value as ResultsState)}
        >
          <SelectItem value="ready" text="Synthetic results" />
          <SelectItem value="loading" text="Synthetic loading state" />
          <SelectItem value="empty" text="Synthetic empty state" />
          <SelectItem value="error" text="Synthetic error state" />
        </Select>
      </div>
      <SearchResultsTableFrame
        ariaLabel="Synthetic sample results table"
        loading={resultsState === 'loading'}
        loadingDescription="Loading synthetic sample results…"
        totalItems={rows.length}
        columnCount={3}
        error={resultsState === 'error' ? 'Synthetic error state. No request was made.' : undefined}
        onRetry={() => setResultsState('ready')}
      >
        <Table
          size="md"
          useZebraStyles
          className="taps-preview-table"
          aria-label="Synthetic sample rows"
        >
          <TableHead>
            <TableRow>
              <TableHeader>Example label</TableHeader>
              <TableHeader>Preview state</TableHeader>
              <TableHeader>Notes</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.label}>
                <TableCell>
                  <button
                    type="button"
                    className="taps-link-button"
                    aria-label={`Open details for ${row.label}`}
                    onClick={(event) => {
                      launcherRef.current = event.currentTarget
                      setSelectedRow(row)
                    }}
                  >
                    {row.label}
                  </button>
                </TableCell>
                <TableCell>Synthetic {row.state}</TableCell>
                <TableCell>{row.notes}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </SearchResultsTableFrame>

      <DetailSidePanel
        open={selectedRow !== null}
        title={selectedRow?.label ?? 'Synthetic example details'}
        launcherRef={launcherRef}
        initialFocusSelector="#synthetic-detail-focus"
        fallbackFocusSelector="#synthetic-results-heading"
        onClose={() => setSelectedRow(null)}
      >
        <h2 id="synthetic-detail-focus" tabIndex={-1}>
          Synthetic example details
        </h2>
        <p>This drawer demonstrates presentation and focus behavior using a synthetic row.</p>
        {selectedRow && (
          <dl>
            <dt>Example label</dt>
            <dd>{selectedRow.label}</dd>
            <dt>Preview state</dt>
            <dd>Synthetic {selectedRow.state}</dd>
            <dt>Notes</dt>
            <dd>{selectedRow.notes}</dd>
          </dl>
        )}
        <Button kind="tertiary" size="md" onClick={() => setSelectedRow(null)}>
          Close synthetic details
        </Button>
      </DetailSidePanel>
    </section>
  )
}
