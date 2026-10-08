import { Button } from '@carbon/react'
import { useState } from 'react'
import { EcasInboxReadPage, GasSearchReadPage } from '@/components/appraisal/ReadWorkflowPages'
import { ReadApiError, type ReadApi } from '@/service/read-service'
import { workflowFixture as fixture } from './WorkflowPreview'
import type { EcasRelatedApis } from '@/components/appraisal/EcasReferenceSections'

const inbox = [...fixture.ecasInboxMultiMarkItems, fixture.ecasInboxItem]
const worksheets = [...fixture.gasSearchResult.appraisals.items, ...fixture.gasSearchPage.items]
const summaries = [fixture.gasMultiMarkAppraisedSummary, fixture.gasAppraisedSummary]
const normalized = (value: string) => value.trim().toUpperCase()
const syntheticRelatedApis: EcasRelatedApis = {
  audit: {
    history: async (ecasId, page) => ({ ecasId, page, total: 0, items: [] }),
    details: async () => {
      throw new ReadApiError(404)
    },
  },
  attachments: {
    inventory: async (ecasId, page) => ({
      ecasId,
      page,
      total: 0,
      items: [],
      size: 50,
      appraisalMethod: ecasId === fixture.ecasInboxItem.ecasId ? 'I' : 'C',
    }),
  },
}
async function delayed<T>(signal: AbortSignal, value: T): Promise<T> {
  await new Promise<void>((resolve) => setTimeout(resolve, 200))
  signal.throwIfAborted()
  return value
}

// Dev and test only; the app uses the HTTP readApi.
export const syntheticReadApi: ReadApi = {
  ecasLookups: async (signal) =>
    delayed(signal, {
      appraisalMethods: [
        { code: 'C', description: 'Coast' },
        { code: 'I', description: 'Interior' },
      ],
      appraisalStatuses: [
        {
          code: fixture.ecasInboxItem.status?.code ?? 'CON',
          description: 'Synthetic status label',
          effectiveDate: null,
          expiryDate: null,
          updateTimestamp: null,
          active: true,
        },
      ],
    }),
  inbox: async (filters, page, signal) => {
    const items = inbox.filter(
      (item) =>
        (!normalized(filters.ecasId) || item.ecasId === normalized(filters.ecasId)) &&
        (!normalized(filters.licence) || item.licence === normalized(filters.licence)) &&
        (!normalized(filters.timberMark) || item.timberMark === normalized(filters.timberMark)) &&
        (!filters.appraisalMethod || item.appraisalMethod === filters.appraisalMethod) &&
        (!filters.statusCodes?.length || filters.statusCodes.includes(item.status?.code ?? '')),
    )
    return delayed(signal, { items, total: items.length, page })
  },
  reference: async (method, id, signal) => {
    const reference =
      method === 'C' ? fixture.ecasCoastMultiMarkReference : fixture.ecasInteriorReference
    if (reference.header.ecasId !== id) throw new ReadApiError(404)
    return delayed(signal, reference)
  },
  relatedSummary: async (id, signal) => {
    const summary = summaries.find((entry) => entry.ecasId === id)
    if (!summary) throw new ReadApiError(404)
    return delayed(signal, summary)
  },
  worksheets: async (licence, mark, page, signal) => {
    const items = worksheets.filter(
      (item) => (!licence || item.licence === licence) && (!mark || item.timberMark === mark),
    )
    return delayed(signal, { items, total: items.length, page })
  },
  worksheet: async (key, signal) => {
    const summary = summaries.find(
      (entry) => entry.key.type === key.type && entry.key.worksheetId === key.worksheetId,
    )
    if (!summary) throw new ReadApiError(404)
    return delayed(signal, summary)
  },
  marks: async (licence, signal) =>
    delayed(
      signal,
      licence === fixture.gasLicenceMarks.licence
        ? fixture.gasLicenceMarks
        : { licence, timberMarks: [] },
    ),
  licenceInformation: async (licence, mark, signal) =>
    delayed(
      signal,
      [fixture.gasSearchResult, fixture.gasSearchResultWithoutAppraisals]
        .map((result) => result.licenceInformation)
        .find(
          (info) => info?.timberMark === mark && (!licence || info.licenceNumber === licence),
        ) ?? null,
    ),
}

export default function AsyncReadPreview() {
  const [module, setModule] = useState<'ecas' | 'gas'>('ecas')
  return (
    <>
      <p>
        Synthetic asynchronous read preview. Uses the production page components with test fixtures;
        no database or sign-in requests.
      </p>
      <Button kind="tertiary" onClick={() => setModule(module === 'ecas' ? 'gas' : 'ecas')}>
        {module === 'ecas' ? 'GAS appraisal search' : 'ECAS inbox search'}
      </Button>
      {module === 'ecas' ? (
        <EcasInboxReadPage api={syntheticReadApi} relatedApis={syntheticRelatedApis} />
      ) : (
        <GasSearchReadPage api={syntheticReadApi} />
      )}
    </>
  )
}
