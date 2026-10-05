import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { SESSION_EXPIRED_EVENT } from '@/context/auth/session-expiry'
import { readApi, ReadApiError } from '../read-service'

const oidc = vi.hoisted(() => ({ getOidcUser: vi.fn(), clearLogin: vi.fn() }))
vi.mock('@/service/oidc-service', () => oidc)
const fetch = vi.fn()
const signal = () => new AbortController().signal
const expired = vi.fn()

beforeEach(() => {
  vi.resetAllMocks()
  vi.stubGlobal('fetch', fetch)
  window.addEventListener(SESSION_EXPIRED_EVENT, expired)
  oidc.getOidcUser.mockResolvedValue({ access_token: 'synthetic-token' })
  fetch.mockResolvedValue({ ok: true, json: async () => ({}) })
})
afterEach(() => {
  vi.unstubAllGlobals()
  window.removeEventListener(SESSION_EXPIRED_EVENT, expired)
})

test('uses the bearer token and normalized POST filters without cookie credentials', async () => {
  const abort = signal()
  await readApi.inbox({ ecasId: ' 123 ', licence: ' a00001 ', timberMark: ' ' }, 2, abort)
  expect(fetch).toHaveBeenCalledWith('/api/ecas/inbox?page=2', {
    method: 'POST',
    headers: { Authorization: 'Bearer synthetic-token', 'Content-Type': 'application/json' },
    credentials: 'omit',
    signal: abort,
    body: JSON.stringify({
      mode: 'ALL_SUBMISSIONS',
      ecasId: '123',
      licence: 'A00001',
      timberMark: null,
    }),
  })
})

test('loads effective choices and sends date, status and sort filters without timezone conversion', async () => {
  await readApi.ecasLookups(signal())
  expect(fetch).toHaveBeenLastCalledWith(
    '/api/ecas/lookups',
    expect.objectContaining({ method: 'GET' }),
  )
  await readApi.inbox(
    {
      ecasId: '',
      licence: '',
      timberMark: '',
      appraisalMethod: 'I',
      statusCodes: ['DFT', 'CON'],
      dateTypes: ['EFFCTV', 'EXPRY'],
      dateFrom: '2026-10-01',
      dateTo: '',
      statusDateFrom: '',
      statusDateTo: '2026-10-02',
      sortBy: 'EFFECTIVE_DATE',
      sortDirection: 'ASC',
    },
    3,
    signal(),
  )
  expect(JSON.parse(fetch.mock.calls.at(-1)![1].body)).toEqual({
    mode: 'ALL_SUBMISSIONS',
    ecasId: null,
    licence: null,
    timberMark: null,
    appraisalMethod: 'I',
    statusCodes: ['DFT', 'CON'],
    dateTypes: ['EFFCTV', 'EXPRY'],
    dates: { from: '2026-10-01', to: null },
    statusDates: { from: null, to: '2026-10-02' },
    sortBy: 'EFFECTIVE_DATE',
    sortDirection: 'ASC',
  })
})

test('preserves all additional filters, explicit false and legacy user/location case', async () => {
  await readApi.inbox(
    {
      ecasId: '',
      licence: ' a00001 ',
      timberMark: ' aa0001 ',
      cuttingPermit: ' a1 ',
      clientNumber: '12',
      clientLocationCode: 'a1',
      orgUnitNumbers: ['10', '20'],
      appraisalCategoryCode: 'R',
      reappraisalReasonCode: 'CHG',
      fileTypeCode: 'A01',
      managementUnitType: 'u',
      managementUnitId: '12',
      workedOnByUserId: ' IDIR\\MixedCase ',
      bctsFunded: false,
      certified: false,
    },
    0,
    signal(),
  )
  expect(JSON.parse(fetch.mock.calls.at(-1)![1].body)).toMatchObject({
    licence: 'A00001',
    timberMark: 'AA0001',
    cuttingPermit: 'A1',
    clientNumber: '12',
    clientLocationCode: 'a1',
    orgUnitNumbers: ['10', '20'],
    appraisalCategoryCode: 'R',
    reappraisalReasonCode: 'CHG',
    fileTypeCode: 'A01',
    managementUnitType: 'U',
    managementUnitId: '12',
    workedOnByUserId: 'IDIR\\MixedCase',
    bctsFunded: false,
    certified: false,
  })
})

test('keeps typed worksheet identities, reference methods and URL segments separate', async () => {
  await readApi.reference('I', '123/4', signal())
  await readApi.relatedSummary('123', signal())
  await readApi.worksheet({ type: 'HISTORIC', worksheetId: '321' }, signal())
  await readApi.marks(' a00001 ', signal())
  await readApi.worksheets('a00001', 'aa0001', 3, signal())
  expect(fetch.mock.calls.map(([url]) => url)).toEqual([
    '/api/ecas/references/I/123%2F4',
    '/api/gas/appraised/by-ecas/123',
    '/api/gas/worksheets/HISTORIC/321',
    '/api/gas/licences/A00001/marks',
    '/api/gas/worksheets?page=3&licence=A00001&timberMark=AA0001',
  ])
})

test('preserves rate decimal strings and nullable fields from JSON', async () => {
  const summary = { rates: [{ totalStumpageRate: '12.30', effectiveDate: null }] }
  fetch.mockResolvedValue({ ok: true, json: async () => summary })
  await expect(
    readApi.worksheet({ type: 'APPRAISED', worksheetId: '123' }, signal()),
  ).resolves.toEqual(summary)
})

test('does not fetch after cancellation or without an authenticated session', async () => {
  const abort = new AbortController()
  abort.abort()
  await expect(readApi.marks('A00001', abort.signal)).rejects.toHaveProperty('name', 'AbortError')
  expect(expired).not.toHaveBeenCalled()
  oidc.getOidcUser.mockResolvedValue(null)
  await expect(readApi.marks('A00001', signal())).rejects.toMatchObject({ status: 401 })
  expect(fetch).not.toHaveBeenCalled()
  expect(expired).toHaveBeenCalledOnce()
  expect(expired.mock.calls[0][0]).toMatchObject({ detail: { reason: 'token-unavailable' } })
})

test.each([400, 401, 403, 404, 503])(
  'maps HTTP %s to safe feedback and clears only expired sessions',
  async (status) => {
    fetch.mockResolvedValue({
      ok: false,
      status,
      text: async () => 'sensitive SQL infrastructure details',
    })
    await expect(readApi.marks('A00001', signal())).rejects.toEqual(new ReadApiError(status))
    expect(oidc.clearLogin).toHaveBeenCalledTimes(status === 401 ? 1 : 0)
    expect(expired.mock.calls.map(([event]) => event.detail.reason)).toEqual(
      status === 401 ? ['api-unauthorized'] : [],
    )
  },
)

test('treats missing FTA context independently and propagates real failures', async () => {
  await expect(readApi.licenceInformation('A00001', '', signal())).resolves.toBeNull()
  expect(fetch).not.toHaveBeenCalled()
  fetch.mockResolvedValue({ ok: false, status: 404 })
  await expect(readApi.licenceInformation('A00001', 'AA0001', signal())).resolves.toBeNull()
  fetch.mockResolvedValue({ ok: false, status: 503 })
  await expect(readApi.licenceInformation('A00001', 'AA0001', signal())).rejects.toMatchObject({
    status: 503,
  })
})

test('read errors keep status messages and accept a specific explanation', () => {
  expect(new ReadApiError(403).message).toBe('You do not have access to this information.')
  expect(new ReadApiError(400).message).toBe('Check your search values and try again.')
  expect(new ReadApiError(400, 'No appraisal method.').message).toBe('No appraisal method.')
})
