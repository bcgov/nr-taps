import type {
  AppraisalMethod,
  CoastReference,
  EcasInboxPage,
  EcasSearchFilters,
  EcasLookups,
  FtaLicenceInformation,
  GasAppraisalPage,
  GasAppraisedSummary,
  GasWorksheetSummary,
  InteriorReference,
  LicenceMarks,
  WorksheetKey,
} from '@/contracts/appraisal'
import { notifySessionExpired } from '@/context/auth/session-expiry'
import { clearLogin, getOidcUser } from './oidc-service'

export class ReadApiError extends Error {
  constructor(
    public readonly status: number,
    message?: string,
  ) {
    super(
      message ??
        (status === 401
          ? 'Your session has ended. Log in again.'
          : status === 403
            ? 'You do not have access to this information.'
            : status === 404
              ? 'No accessible record was found.'
              : status === 400
                ? 'Check your search values and try again.'
                : 'The read service is unavailable. Please try again.'),
    )
  }
}

export interface ReadApi {
  ecasLookups(signal: AbortSignal): Promise<EcasLookups>
  inbox(filters: EcasSearchFilters, page: number, signal: AbortSignal): Promise<EcasInboxPage>
  reference(
    method: AppraisalMethod,
    id: string,
    signal: AbortSignal,
  ): Promise<CoastReference | InteriorReference>
  relatedSummary(ecasId: string, signal: AbortSignal): Promise<GasAppraisedSummary>
  worksheets(
    licence: string,
    mark: string,
    page: number,
    signal: AbortSignal,
  ): Promise<GasAppraisalPage>
  worksheet(key: WorksheetKey, signal: AbortSignal): Promise<GasWorksheetSummary>
  marks(licence: string, signal: AbortSignal): Promise<LicenceMarks>
  licenceInformation(
    licence: string,
    mark: string,
    signal: AbortSignal,
  ): Promise<FtaLicenceInformation | null>
}

export async function readRequest<T>(
  path: string,
  signal: AbortSignal,
  body?: unknown,
): Promise<T> {
  const user = await getOidcUser()
  signal.throwIfAborted()
  if (!user) {
    notifySessionExpired('token-unavailable')
    throw new ReadApiError(401)
  }
  const response = await fetch(path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: {
      Authorization: `Bearer ${user.access_token}`,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    credentials: 'omit',
    signal,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  signal.throwIfAborted()
  if (!response.ok) {
    if (response.status === 401) {
      await clearLogin()
      notifySessionExpired('api-unauthorized')
    }
    // Do not surface arbitrary infrastructure/SQL error bodies in the page.
    throw new ReadApiError(response.status)
  }
  return response.json() as Promise<T>
}

const value = (text: string) => text.trim().toUpperCase() || null
const segment = (text: string) => encodeURIComponent(text)
const dates = (from?: string, to?: string) =>
  from || to ? { from: from || null, to: to || null } : undefined

export const readApi: ReadApi = {
  ecasLookups: (signal) => readRequest('/api/ecas/lookups', signal),
  inbox: (filters, page, signal) =>
    readRequest(`/api/ecas/inbox?page=${page}`, signal, {
      mode: filters.mode ?? 'ALL_SUBMISSIONS',
      ecasId: value(filters.ecasId),
      licence: value(filters.licence),
      timberMark: value(filters.timberMark),
      cuttingPermit: filters.cuttingPermit === undefined ? undefined : value(filters.cuttingPermit),
      clientNumber: filters.clientNumber === undefined ? undefined : value(filters.clientNumber),
      clientLocationCode:
        filters.clientLocationCode === undefined
          ? undefined
          : filters.clientLocationCode.trim() || null,
      orgUnitNumbers: filters.orgUnitNumbers,
      appraisalCategoryCode: filters.appraisalCategoryCode || undefined,
      reappraisalReasonCode: filters.reappraisalReasonCode || undefined,
      fileTypeCode: filters.fileTypeCode || undefined,
      managementUnitType:
        filters.managementUnitType === undefined ? undefined : value(filters.managementUnitType),
      managementUnitId:
        filters.managementUnitId === undefined ? undefined : value(filters.managementUnitId),
      workedOnByUserId: filters.workedOnByUserId?.trim().toUpperCase() || undefined,
      bctsFunded: filters.bctsFunded,
      certified: filters.certified,
      appraisalMethod: filters.appraisalMethod || undefined,
      statusCodes: filters.statusCodes,
      dateTypes: filters.dateTypes,
      dates: dates(filters.dateFrom, filters.dateTo),
      statusDates: dates(filters.statusDateFrom, filters.statusDateTo),
      sortBy: filters.sortBy,
      sortDirection: filters.sortDirection,
    }),
  reference: (method, id, signal) =>
    readRequest(`/api/ecas/references/${method}/${segment(id)}`, signal),
  relatedSummary: (id, signal) => readRequest(`/api/gas/appraised/by-ecas/${segment(id)}`, signal),
  worksheets: (licence, mark, page, signal) => {
    const query = new URLSearchParams({ page: String(page) })
    if (value(licence)) query.set('licence', value(licence)!)
    if (value(mark)) query.set('timberMark', value(mark)!)
    return readRequest(`/api/gas/worksheets?${query}`, signal)
  },
  worksheet: (key, signal) =>
    readRequest(`/api/gas/worksheets/${key.type}/${segment(key.worksheetId)}`, signal),
  marks: (licence, signal) =>
    readRequest(`/api/gas/licences/${segment(value(licence) ?? '')}/marks`, signal),
  licenceInformation: async (licence, mark, signal) => {
    if (!value(mark)) return null
    const query = new URLSearchParams({ timberMark: value(mark)! })
    if (value(licence)) query.set('licence', value(licence)!)
    try {
      return await readRequest<FtaLicenceInformation>(
        `/api/gas/licence-information?${query}`,
        signal,
      )
    } catch (error) {
      if (error instanceof ReadApiError && error.status === 404) return null
      throw error
    }
  },
}
