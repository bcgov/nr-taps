import { Button, TextInput } from '@carbon/react'
import { useRef, useState } from 'react'
import source from '../../../backend/src/test/resources/contracts/synthetic-workflow.json'
import type {
  CoastReference,
  EcasInboxItem,
  GasAppraisedSummary,
  GasAppraisalItem,
  GasAppraisalPage,
  GasSearchResult,
  InteriorReference,
  LicenceMarks,
} from '@/contracts/appraisal'
import PageHeader from '@/components/PageHeader'
import SearchFilters from '@/components/SearchFilters'
import DetailSidePanel from '@/components/DetailSidePanel'
import { EcasInboxResults, GasSearchResults } from '@/components/appraisal/AppraisalResults'
import GasSearchFilters, { type GasFilters } from '@/components/appraisal/GasSearchFilters'
import {
  CoastReferenceDetails,
  InteriorReferenceDetails,
  GasStoredSummary,
} from '@/components/appraisal/AppraisalDetails'

// Dev-only fixture, also used by the Java serialization tests.
export const workflowFixture = source as {
  synthetic: boolean
  ecasInboxItem: EcasInboxItem
  ecasInboxMultiMarkItems: EcasInboxItem[]
  ecasCoastMultiMarkReference: CoastReference
  ecasInteriorReference: InteriorReference
  gasLicenceMarks: LicenceMarks
  gasSearchPage: GasAppraisalPage
  gasSearchResult: GasSearchResult
  gasSearchResultWithoutAppraisals: GasSearchResult
  gasAppraisedSummary: GasAppraisedSummary
  gasMultiMarkAppraisedSummary: GasAppraisedSummary
}

const fixture = workflowFixture
const inboxItems = [...fixture.ecasInboxMultiMarkItems, fixture.ecasInboxItem]
const gasItems = [...fixture.gasSearchResult.appraisals.items, ...fixture.gasSearchPage.items]
const summaries = [fixture.gasMultiMarkAppraisedSummary, fixture.gasAppraisedSummary]
const emptyGasFilters = { licence: '', timberMark: '' }
const emptyEcasFilters = { ecasId: '', licence: '', timberMark: '' }
const normalized = (value: string) => value.trim().toUpperCase()
type Detail = { kind: 'ecas'; item: EcasInboxItem } | { kind: 'gas'; summary: GasAppraisedSummary }

export default function WorkflowPreview() {
  const [application, setApplication] = useState<'ecas' | 'gas'>('ecas')
  const [ecasDraft, setEcasDraft] = useState(emptyEcasFilters)
  const [ecasApplied, setEcasApplied] = useState(emptyEcasFilters)
  const [gasDraft, setGasDraft] = useState<GasFilters>(emptyGasFilters)
  const [gasApplied, setGasApplied] = useState<GasFilters>(emptyGasFilters)
  const [relatedEcasId, setRelatedEcasId] = useState<string | null>(null)
  const [detail, setDetail] = useState<Detail | null>(null)
  const launcherRef = useRef<HTMLElement | null>(null)

  const inbox = inboxItems.filter(
    (item) =>
      (!ecasApplied.ecasId || item.ecasId === normalized(ecasApplied.ecasId)) &&
      (!ecasApplied.licence || item.licence === normalized(ecasApplied.licence)) &&
      (!ecasApplied.timberMark || item.timberMark === normalized(ecasApplied.timberMark)),
  )
  const selectedLicence = normalized(gasDraft.licence)
  const licenceMarks =
    selectedLicence === fixture.gasLicenceMarks.licence
      ? fixture.gasLicenceMarks
      : selectedLicence === fixture.ecasInboxItem.licence
        ? { licence: selectedLicence, timberMarks: fixture.gasAppraisedSummary.timberMarks }
        : null
  // Records link by ECAS ID and worksheet key, not licence or mark.
  const relatedSummary = summaries.find((summary) => summary.ecasId === relatedEcasId)
  const gasRows = gasItems.filter(
    (item) =>
      (!gasApplied.licence || item.licence === normalized(gasApplied.licence)) &&
      (!gasApplied.timberMark || item.timberMark === normalized(gasApplied.timberMark)) &&
      (!relatedEcasId ||
        (item.key.type === relatedSummary?.key.type &&
          item.key.worksheetId === relatedSummary?.key.worksheetId)),
  )
  const licenceInformation =
    [fixture.gasSearchResult, fixture.gasSearchResultWithoutAppraisals]
      .map((result) => result.licenceInformation)
      .find(
        (info) =>
          info?.timberMark === normalized(gasApplied.timberMark) &&
          (!gasApplied.licence || info.licenceNumber === normalized(gasApplied.licence)),
      ) ?? null
  const result: GasSearchResult = {
    appraisals: { items: gasRows, total: gasRows.length, page: 0 },
    licenceInformation,
  }

  function openGas(item: GasAppraisalItem, launcher: HTMLButtonElement) {
    const summary = summaries.find(
      (entry) => entry.key.type === item.key.type && entry.key.worksheetId === item.key.worksheetId,
    )
    if (!summary) return
    launcherRef.current = launcher
    setDetail({ kind: 'gas', summary })
  }

  function showRelated(item: EcasInboxItem) {
    setRelatedEcasId(item.ecasId)
    setGasDraft(emptyGasFilters)
    setGasApplied(emptyGasFilters)
    setDetail(null)
    setApplication('gas')
  }

  const detailTitle =
    detail?.kind === 'ecas'
      ? `${detail.item.appraisalMethod === 'C' ? 'Coast' : 'Interior'} reference ${detail.item.ecasId}`
      : detail?.kind === 'gas'
        ? `Appraised worksheet ${detail.summary.key.worksheetId}`
        : 'Appraisal details'

  return (
    <section className="taps-page" aria-label="Synthetic ECAS and GAS workflow">
      <PageHeader
        title={application === 'ecas' ? 'Inbox Search' : 'Appraisal Search'}
        subtitle="Synthetic contract preview. Search, reference and stored-rate presentation only."
        actions={
          <Button
            kind="tertiary"
            onClick={() => {
              setDetail(null)
              setApplication(application === 'ecas' ? 'gas' : 'ecas')
            }}
          >
            {application === 'ecas' ? 'GAS appraisal search' : 'ECAS inbox search'}
          </Button>
        }
      />
      <p>
        This preview uses shared Java test fixtures. It performs no service or database requests. It
        covers selected filters; full legacy search and workflow functions remain in development.
      </p>
      {application === 'ecas' ? (
        <>
          <SearchFilters
            title="Inbox search filters"
            onSearch={() =>
              setEcasApplied({
                ecasId: normalized(ecasDraft.ecasId),
                licence: normalized(ecasDraft.licence),
                timberMark: normalized(ecasDraft.timberMark),
              })
            }
            onReset={() => {
              setEcasDraft(emptyEcasFilters)
              setEcasApplied(emptyEcasFilters)
            }}
          >
            <TextInput
              id="workflow-ecas-id"
              labelText="ECAS ID"
              value={ecasDraft.ecasId}
              onChange={(event) => setEcasDraft({ ...ecasDraft, ecasId: event.target.value })}
            />
            <TextInput
              id="workflow-ecas-licence"
              labelText="Licence"
              value={ecasDraft.licence}
              onChange={(event) => setEcasDraft({ ...ecasDraft, licence: event.target.value })}
            />
            <TextInput
              id="workflow-ecas-mark"
              labelText="Timber mark"
              value={ecasDraft.timberMark}
              onChange={(event) => setEcasDraft({ ...ecasDraft, timberMark: event.target.value })}
            />
          </SearchFilters>
          <h2 id="workflow-results" tabIndex={-1}>
            ECAS submissions
          </h2>
          <EcasInboxResults
            items={inbox}
            onOpen={(item, launcher) => {
              launcherRef.current = launcher
              setDetail({ kind: 'ecas', item })
            }}
          />
        </>
      ) : (
        <>
          <p>
            Synthetic licences: X99998 and X99999. Mark ZZ9996 demonstrates licence information with
            no worksheet results.
          </p>
          {relatedEcasId && (
            <div className="taps-actions">
              <p>Related to ECAS {relatedEcasId}</p>
              <Button kind="ghost" onClick={() => setRelatedEcasId(null)}>
                Show all worksheets
              </Button>
            </div>
          )}
          <GasSearchFilters
            draft={gasDraft}
            licenceMarks={licenceMarks}
            onChange={setGasDraft}
            onSearch={setGasApplied}
            onReset={() => {
              setGasDraft(emptyGasFilters)
              setGasApplied(emptyGasFilters)
              setRelatedEcasId(null)
            }}
          />
          <h2 id="workflow-results" tabIndex={-1}>
            GAS worksheets
          </h2>
          <GasSearchResults result={result} onOpen={openGas} />
        </>
      )}
      <DetailSidePanel
        open={detail !== null}
        title={detailTitle}
        launcherRef={launcherRef}
        initialFocusSelector="#workflow-detail-heading"
        fallbackFocusSelector="#workflow-results"
        onClose={() => setDetail(null)}
      >
        <h2 id="workflow-detail-heading" tabIndex={-1}>
          {detail?.kind === 'ecas' ? 'Submission details' : 'Stored worksheet details'}
        </h2>
        {detail?.kind === 'ecas' && (
          <>
            <p>
              Selected result: mark {detail.item.timberMark ?? '—'}, cutting permit{' '}
              {detail.item.cuttingPermit ?? '—'}.
            </p>
            {detail.item.appraisalMethod === 'C' ? (
              <CoastReferenceDetails reference={fixture.ecasCoastMultiMarkReference} />
            ) : (
              <InteriorReferenceDetails reference={fixture.ecasInteriorReference} />
            )}
            <Button onClick={() => showRelated(detail.item)}>View related GAS worksheets</Button>
          </>
        )}
        {detail?.kind === 'gas' && <GasStoredSummary summary={detail.summary} />}
      </DetailSidePanel>
    </section>
  )
}
