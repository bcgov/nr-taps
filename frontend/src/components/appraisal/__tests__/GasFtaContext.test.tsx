import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import type { FtaLicenceInformation, GasHistoricSummary } from '@/contracts/appraisal'
import type { ReadApi } from '@/service/read-service'
import { ReadApiError } from '@/service/read-service'
import { workflowFixture } from '@/local-synthetic/WorkflowPreview'
import { nonAppraisedSample } from '@/local-synthetic/non-appraised-sample'
import GasFtaContext from '../GasFtaContext'
import { GasStoredSummary } from '../AppraisalDetails'
import OtherWorksheetDetails from '../OtherWorksheetDetails'

const { reloadSession } = vi.hoisted(() => ({ reloadSession: vi.fn(async () => {}) }))
vi.mock('@/context/auth/AuthContext', () => ({ useAuth: () => ({ reloadSession }) }))

const info: FtaLicenceInformation = {
  clientNumber: null,
  licenseeName: null,
  licenceNumber: null,
  cuttingPermit: null,
  fileTypeCode: null,
  timberMark: 'ZZ9997',
  forestRegion: null,
  forestDistrict: null,
  markExpiryDate: null,
  markExtendDate: null,
  ftaStatus: 'Licence status must not replace mark status',
  markStatus: { code: 'I', description: '<script>Issued as text</script>' },
  cruiseBased: null,
}
const api = (): Pick<ReadApi, 'licenceInformation'> => ({
  licenceInformation: vi.fn().mockResolvedValue(info),
})
beforeEach(() => vi.clearAllMocks())

test.each([
  [true, 'Yes'],
  [false, 'No'],
  [null, '—'],
] as const)(
  'renders mark status as text and distinguishes cruise indicator %s',
  async (cruiseBased, expected) => {
    const reader = api()
    vi.mocked(reader.licenceInformation).mockResolvedValue({ ...info, cruiseBased })
    const { container } = render(<GasFtaContext timberMark="ZZ9997" api={reader} />)
    await screen.findByText('<script>Issued as text</script>')
    expect(container.querySelector('script')).toBeNull()
    expect(screen.queryByText(info.ftaStatus!)).not.toBeInTheDocument()
    expect(
      within(screen.getByText('Cruise based').parentElement!).getByText(expected),
    ).toBeInTheDocument()
    expect(reader.licenceInformation).toHaveBeenCalledWith('', 'ZZ9997', expect.any(AbortSignal))
  },
)

test('uses the stored appraised primary mark and does not fall back to the first mark when it is absent', async () => {
  const reader = api()
  const summary = {
    ...workflowFixture.gasMultiMarkAppraisedSummary,
    primaryTimberMark: 'ZZ9997',
    timberMarks: ['ZZ9998', 'ZZ9997'],
  }
  const view = render(<GasStoredSummary summary={summary} api={reader} />)
  await waitFor(() => expect(reader.licenceInformation).toHaveBeenCalledOnce())
  expect(reader.licenceInformation).toHaveBeenCalledWith('', 'ZZ9997', expect.any(AbortSignal))
  view.rerender(<GasStoredSummary summary={{ ...summary, primaryTimberMark: null }} api={reader} />)
  expect(
    screen.getByText('No primary timber mark is recorded for this worksheet.'),
  ).toBeInTheDocument()
  expect(reader.licenceInformation).toHaveBeenCalledOnce()
  expect(screen.getByRole('cell', { name: '12.30' })).toBeInTheDocument()
})

test.each(['NON_APPRAISED', 'HISTORIC'] as const)(
  'uses the stored mark for %s summaries',
  async (type) => {
    const reader = api()
    vi.mocked(reader.licenceInformation).mockResolvedValue({ ...info, timberMark: 'ZZ9995' })
    const historic: GasHistoricSummary = {
      key: { type: 'HISTORIC', worksheetId: '999900000094' },
      licence: null,
      timberMark: 'ZZ9995',
      appraisalMethod: 'I',
      variant: 'INTERIOR_MPS',
      rateCalculationMethodCode: 'MPS',
      tenureObligationAdjustment: null,
      adjustQuarterly: null,
      active: true,
      policyVersion: null,
      status: null,
      effectiveDate: null,
      expiryDate: null,
      ceaseAdjustmentDate: null,
      rates: [],
      nonAppraisedRates: [],
      historicSpecies: [],
      coastSpeciesGrades: [],
    }
    render(
      <OtherWorksheetDetails
        summary={type === 'HISTORIC' ? historic : nonAppraisedSample}
        api={reader}
      />,
    )
    await screen.findByText('<script>Issued as text</script>')
    expect(reader.licenceInformation).toHaveBeenCalledWith('', 'ZZ9995', expect.any(AbortSignal))
  },
)

test.each([null, ' '])('does not request FTA when the stored mark is %j', async (timberMark) => {
  const reader = api()
  render(<GasFtaContext timberMark={timberMark} api={reader} />)
  expect(
    screen.getByText('No primary timber mark is recorded for this worksheet.'),
  ).toBeInTheDocument()
  expect(reader.licenceInformation).not.toHaveBeenCalled()
  expect(within(screen.getByText('Cruise based').parentElement!).getByText('—')).toBeInTheDocument()
})

test('missing or failed FTA retains stored rates and retries only the context request', async () => {
  const reader = api()
  vi.mocked(reader.licenceInformation)
    .mockResolvedValueOnce(null)
    .mockRejectedValueOnce(new ReadApiError(503))
    .mockResolvedValueOnce(info)
  const summary = { ...workflowFixture.gasMultiMarkAppraisedSummary, primaryTimberMark: 'ZZ9997' }
  const view = render(<GasStoredSummary summary={summary} api={reader} />)
  await screen.findByText('No FTA information is available for this timber mark.')
  expect(screen.getByRole('cell', { name: '12.30' })).toBeInTheDocument()
  view.rerender(
    <GasStoredSummary
      summary={{ ...summary, key: { ...summary.key, worksheetId: '999900000099' } }}
      api={reader}
    />,
  )
  await screen.findByText('FTA information unavailable')
  expect(screen.getByRole('cell', { name: '12.30' })).toBeInTheDocument()
  await userEvent.setup().click(screen.getByRole('button', { name: 'Retry FTA information' }))
  await screen.findByText('<script>Issued as text</script>')
  expect(reader.licenceInformation).toHaveBeenCalledTimes(3)
})

test('rejects FTA metadata for a different returned mark', async () => {
  const reader = api()
  vi.mocked(reader.licenceInformation).mockResolvedValue({ ...info, timberMark: 'ZZ9999' })
  render(<GasFtaContext timberMark="ZZ9997" api={reader} />)
  await screen.findByText('FTA information unavailable')
  expect(screen.queryByText('<script>Issued as text</script>')).not.toBeInTheDocument()
})

test('changing the mark aborts and ignores the old response', async () => {
  const reader = api()
  let release!: (value: FtaLicenceInformation) => void
  vi.mocked(reader.licenceInformation)
    .mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          release = resolve
        }),
    )
    .mockResolvedValue({
      ...info,
      timberMark: 'ZZ9995',
      markStatus: { code: 'N', description: 'Current mark' },
    })
  const view = render(<GasFtaContext timberMark="ZZ9997" api={reader} />)
  await waitFor(() => expect(reader.licenceInformation).toHaveBeenCalledOnce())
  const signal = vi.mocked(reader.licenceInformation).mock.calls[0][2]
  view.rerender(<GasFtaContext timberMark="ZZ9995" api={reader} />)
  await screen.findByText('Current mark')
  expect(signal.aborted).toBe(true)
  await act(async () => release(info))
  expect(screen.queryByText('<script>Issued as text</script>')).not.toBeInTheDocument()
})

test('a new worksheet key reloads its context even with the same primary mark and unmount aborts it', async () => {
  const reader = api()
  vi.mocked(reader.licenceInformation).mockImplementation(() => new Promise(() => {}))
  const summary = { ...workflowFixture.gasMultiMarkAppraisedSummary, primaryTimberMark: 'ZZ9997' }
  const view = render(<GasStoredSummary summary={summary} api={reader} />)
  await waitFor(() => expect(reader.licenceInformation).toHaveBeenCalledOnce())
  const first = vi.mocked(reader.licenceInformation).mock.calls[0][2]
  view.rerender(
    <GasStoredSummary
      summary={{ ...summary, key: { ...summary.key, worksheetId: '999900000099' } }}
      api={reader}
    />,
  )
  await waitFor(() => expect(reader.licenceInformation).toHaveBeenCalledTimes(2))
  expect(first.aborted).toBe(true)
  const second = vi.mocked(reader.licenceInformation).mock.calls[1][2]
  view.unmount()
  expect(second.aborted).toBe(true)
})

test.each([401, 403])('handles HTTP %s locally while preserving rates', async (status) => {
  const reader = api()
  vi.mocked(reader.licenceInformation).mockRejectedValue(new ReadApiError(status))
  render(
    <GasStoredSummary
      summary={{ ...workflowFixture.gasMultiMarkAppraisedSummary, primaryTimberMark: 'ZZ9997' }}
      api={reader}
    />,
  )
  await screen.findByText('FTA information unavailable')
  expect(screen.getByRole('cell', { name: '12.30' })).toBeInTheDocument()
  await waitFor(() => expect(reloadSession).toHaveBeenCalledTimes(status === 401 ? 1 : 0))
})
