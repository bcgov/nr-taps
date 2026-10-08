import { ReadApiError, type ReadApi } from '@/service/read-service'
import { workflowFixture as fixture } from './WorkflowPreview'

const inbox = [...fixture.ecasInboxMultiMarkItems, fixture.ecasInboxItem]
const worksheets = [...fixture.gasSearchResult.appraisals.items, ...fixture.gasSearchPage.items]
const summaries = [fixture.gasMultiMarkAppraisedSummary, fixture.gasAppraisedSummary]
const normalized = (value: string) => value.trim().toUpperCase()
async function delayed<T>(signal: AbortSignal, value: T): Promise<T> {
  await new Promise<void>((resolve) => setTimeout(resolve, 200))
  signal.throwIfAborted()
  return value
}

// Local synthetic preview and tests only; the app uses the HTTP readApi.
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
