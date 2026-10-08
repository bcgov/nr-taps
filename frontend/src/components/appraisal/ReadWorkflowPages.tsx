import { Button, InlineLoading, Pagination } from '@carbon/react'
import { useCallback, useEffect, useRef, useState } from 'react'
import type {
  AppraisalMethod,
  CoastReference,
  EcasInboxItem,
  EcasSearchFilters,
  GasAppraisalItem,
  GasWorksheetSummary,
  InteriorReference,
} from '@/contracts/appraisal'
import { useAuth } from '@/context/auth/AuthContext'
import { Capability } from '@/context/auth/capabilities'
import { readApi, ReadApiError, type ReadApi } from '@/service/read-service'
import PageHeader from '../PageHeader'
import DetailSidePanel from '../DetailSidePanel'
import AppNotification from '../AppNotification'
import { EcasInboxResults, GasSearchResults } from './AppraisalResults'
import { CoastReferenceDetails, InteriorReferenceDetails } from './AppraisalDetails'
import OtherWorksheetDetails from './OtherWorksheetDetails'
import GasSearchFilters, { type GasFilters } from './GasSearchFilters'
import useReadResource, { useLoadedTotal } from './useReadResource'
import EcasSearchFiltersForm from './EcasSearchFilters'
import useSessionFailure from './useReadSessionFailure'
import EcasReferenceSections, { type EcasRelatedApis } from './EcasReferenceSections'

type Selection =
  | { kind: 'reference'; id: string; method: AppraisalMethod | null }
  | { kind: 'worksheet'; item: GasAppraisalItem }
  | { kind: 'related'; id: string; reference: Extract<Selection, { kind: 'reference' }> }
type Detail =
  | { kind: 'coast'; reference: CoastReference }
  | { kind: 'interior'; reference: InteriorReference }
  | { kind: 'worksheet'; summary: GasWorksheetSummary }

function ReadDetailPanel({
  selection,
  setSelection,
  launcherRef,
  api,
  relatedApis,
}: {
  selection: Selection | null
  setSelection: (selection: Selection | null) => void
  launcherRef: React.RefObject<HTMLElement | null>
  api: ReadApi
  relatedApis?: EcasRelatedApis
}) {
  const { can } = useAuth()
  const load = useCallback(
    async (signal: AbortSignal): Promise<Detail> => {
      if (!selection) throw new ReadApiError(404)
      if (selection.kind === 'reference') {
        if (!selection.method)
          throw new ReadApiError(
            400,
            'This submission has no appraisal method recorded, so its reference cannot be shown.',
          )
        const reference = await api.reference(selection.method, selection.id, signal)
        if (
          reference.header.ecasId !== selection.id ||
          reference.header.appraisalMethod !== selection.method
        )
          throw new ReadApiError(503)
        return 'timberMarks' in reference
          ? { kind: 'coast', reference }
          : { kind: 'interior', reference }
      }
      if (selection.kind === 'related') {
        const summary = await api.relatedSummary(selection.id, signal)
        if (summary.ecasId !== selection.id) throw new ReadApiError(503)
        return { kind: 'worksheet', summary }
      }
      const summary = await api.worksheet(selection.item.key, signal)
      if (
        summary.key.type !== selection.item.key.type ||
        summary.key.worksheetId !== selection.item.key.worksheetId
      )
        throw new ReadApiError(503)
      return { kind: 'worksheet', summary }
    },
    [api, selection],
  )
  const detail = useReadResource(selection ? load : null)
  useSessionFailure(detail.error)
  useEffect(() => {
    if (!selection) return
    const frame = requestAnimationFrame(() =>
      document.getElementById('read-detail-heading')?.focus(),
    )
    return () => cancelAnimationFrame(frame)
  }, [selection, detail.value])
  const title =
    selection?.kind === 'reference'
      ? `ECAS reference ${selection.id}`
      : selection?.kind === 'related'
        ? `Worksheet for ECAS ${selection.id}`
        : selection
          ? `Worksheet ${selection.item.key.worksheetId}`
          : 'Details'
  return (
    <DetailSidePanel
      open={selection !== null}
      title={title}
      launcherRef={launcherRef}
      initialFocusSelector="#read-detail-heading"
      fallbackFocusSelector="#read-results-heading"
      onClose={() => setSelection(null)}
    >
      <h2 id="read-detail-heading" tabIndex={-1}>
        Read details
      </h2>
      {detail.loading && <InlineLoading description="Loading details…" />}
      {detail.error && (
        <>
          <AppNotification
            kind="error"
            title="Unable to load details"
            subtitle={detail.error.message}
          />
          <Button kind="tertiary" onClick={detail.retry}>
            Retry details
          </Button>
        </>
      )}
      {detail.value?.kind === 'coast' && (
        <CoastReferenceDetails reference={detail.value.reference} />
      )}
      {detail.value?.kind === 'interior' && (
        <InteriorReferenceDetails reference={detail.value.reference} />
      )}
      {detail.value?.kind === 'worksheet' && (
        <OtherWorksheetDetails summary={detail.value.summary} />
      )}
      {(detail.value?.kind === 'coast' || detail.value?.kind === 'interior') && (
        <EcasReferenceSections
          key={detail.value.reference.header.ecasId}
          reference={detail.value.reference}
          apis={relatedApis}
        />
      )}
      {selection?.kind === 'reference' && detail.value && can(Capability.GasAppraisalView) && (
        <Button
          onClick={() => setSelection({ kind: 'related', id: selection.id, reference: selection })}
        >
          View related GAS worksheet
        </Button>
      )}
      {selection?.kind === 'related' && (
        <Button kind="tertiary" onClick={() => setSelection(selection.reference)}>
          Back to ECAS reference
        </Button>
      )}
    </DetailSidePanel>
  )
}

const emptyEcas: EcasSearchFilters = {
  ecasId: '',
  licence: '',
  timberMark: '',
  appraisalMethod: '',
  statusCodes: [],
  dateTypes: [],
  dateFrom: '',
  dateTo: '',
  statusDateFrom: '',
  statusDateTo: '',
  sortBy: 'ECAS_ID',
  sortDirection: 'DESC',
}

export function EcasInboxReadPage({
  api = readApi,
  relatedApis,
}: {
  api?: ReadApi
  relatedApis?: EcasRelatedApis
}) {
  const [draft, setDraft] = useState(emptyEcas)
  const [query, setQuery] = useState<{ filters: EcasSearchFilters; page: number } | null>(null)
  const [selection, setSelection] = useState<Selection | null>(null)
  const launcherRef = useRef<HTMLElement | null>(null)
  const loadLookups = useCallback((signal: AbortSignal) => api.ecasLookups(signal), [api])
  const lookups = useReadResource(loadLookups)
  useSessionFailure(lookups.error)
  const load = useCallback(
    (signal: AbortSignal) => api.inbox(query!.filters, query!.page, signal),
    [api, query],
  )
  const results = useReadResource(query ? load : null)
  const total = useLoadedTotal(results.value?.total, query?.filters)
  useSessionFailure(results.error)
  const openReference = (item: EcasInboxItem, launcher: HTMLButtonElement) => {
    launcherRef.current = launcher
    setSelection({ kind: 'reference', id: item.ecasId, method: item.appraisalMethod })
  }
  return (
    <section className="taps-page" aria-label="ECAS inbox search">
      <PageHeader
        title="Inbox Search"
        subtitle="Search all appraisal data submissions available to you."
      />
      <EcasSearchFiltersForm
        draft={draft}
        onChange={setDraft}
        lookups={lookups.value}
        lookupLoading={lookups.loading}
        lookupError={lookups.error?.message}
        onRetryLookups={lookups.retry}
        loading={results.loading}
        onSearch={() => {
          setSelection(null)
          setQuery({ filters: { ...draft }, page: 0 })
        }}
        onReset={() => {
          setSelection(null)
          setDraft(emptyEcas)
          setQuery(null)
        }}
      />
      <h2 id="read-results-heading" tabIndex={-1}>
        Submissions
      </h2>
      {!query ? (
        <p>Enter any filters, then select Search.</p>
      ) : (
        <>
          <EcasInboxResults
            items={results.value?.items ?? []}
            totalItems={results.value?.total}
            loading={results.loading}
            error={results.error?.message}
            onRetry={results.retry}
            onOpen={openReference}
          />
          {total !== undefined && !results.error && (
            <Pagination
              page={query.page + 1}
              pageSize={100}
              pageSizes={[100]}
              totalItems={total}
              onChange={({ page }) => {
                setSelection(null)
                setQuery({ filters: query.filters, page: page - 1 })
              }}
            />
          )}
        </>
      )}
      <ReadDetailPanel
        selection={selection}
        setSelection={setSelection}
        launcherRef={launcherRef}
        api={api}
        relatedApis={relatedApis}
      />
    </section>
  )
}

const emptyGas: GasFilters = { licence: '', timberMark: '' }

export function GasSearchReadPage({ api = readApi }: { api?: ReadApi }) {
  const [draft, setDraft] = useState(emptyGas)
  const [lookupLicence, setLookupLicence] = useState('')
  const [query, setQuery] = useState<{ filters: GasFilters; page: number } | null>(null)
  const [selection, setSelection] = useState<Selection | null>(null)
  const launcherRef = useRef<HTMLElement | null>(null)
  const licence = draft.licence.trim().toUpperCase()
  useEffect(() => {
    const timer = setTimeout(() => setLookupLicence(licence), 300)
    return () => clearTimeout(timer)
  }, [licence])
  const loadMarks = useCallback(
    (signal: AbortSignal) => api.marks(lookupLicence, signal),
    [api, lookupLicence],
  )
  const marks = useReadResource(lookupLicence ? loadMarks : null)
  const loadRows = useCallback(
    (signal: AbortSignal) =>
      api.worksheets(query!.filters.licence, query!.filters.timberMark, query!.page, signal),
    [api, query],
  )
  const filters = query?.filters
  const loadContext = useCallback(
    (signal: AbortSignal) => api.licenceInformation(filters!.licence, filters!.timberMark, signal),
    [api, filters],
  )
  const results = useReadResource(query ? loadRows : null)
  const total = useLoadedTotal(results.value?.total, query?.filters)
  const context = useReadResource(query?.filters.timberMark ? loadContext : null)
  useSessionFailure(marks.error)
  useSessionFailure(results.error)
  useSessionFailure(context.error)
  const openWorksheet = (item: GasAppraisalItem, launcher: HTMLButtonElement) => {
    launcherRef.current = launcher
    setSelection({ kind: 'worksheet', item })
  }
  return (
    <section className="taps-page" aria-label="GAS appraisal search">
      <PageHeader title="Appraisal Search" subtitle="Find appraisal worksheets and stored rates." />
      <GasSearchFilters
        draft={draft}
        onChange={setDraft}
        licenceMarks={marks.value ?? null}
        loading={results.loading}
        lookupLoading={licence !== lookupLicence || marks.loading}
        lookupError={licence === lookupLicence ? marks.error?.message : undefined}
        onRetryLookup={marks.retry}
        onSearch={(filters) => {
          setSelection(null)
          setQuery({ filters, page: 0 })
        }}
        onReset={() => {
          setSelection(null)
          setDraft(emptyGas)
          setQuery(null)
        }}
      />
      <h2 id="read-results-heading" tabIndex={-1}>
        Worksheets
      </h2>
      {!query ? (
        <p>Enter any filters, then select Search.</p>
      ) : (
        <>
          <GasSearchResults
            allFamilies
            result={{
              appraisals: results.value ?? { items: [], total: 0, page: query.page },
              licenceInformation: context.value ?? null,
            }}
            loading={results.loading}
            error={results.error?.message}
            onRetry={results.retry}
            onOpen={openWorksheet}
            contextLoading={context.loading}
            contextError={context.error?.message}
            onRetryContext={context.retry}
          />
          {total !== undefined && !results.error && (
            <Pagination
              page={query.page + 1}
              pageSize={10}
              pageSizes={[10]}
              totalItems={total}
              onChange={({ page }) => {
                setSelection(null)
                setQuery({ filters: query.filters, page: page - 1 })
              }}
            />
          )}
        </>
      )}
      <ReadDetailPanel
        selection={selection}
        setSelection={setSelection}
        launcherRef={launcherRef}
        api={api}
      />
    </section>
  )
}
