import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import type { ReactNode } from 'react'
import { AuthContext, type AuthContextValue } from '@/context/auth/AuthContext'
import { staffSession } from '@/test-utils'
import { setViewportWidth } from '@/test-setup'
import { syntheticReadApi } from '@/local-synthetic/synthetic-read-api'
import { workflowFixture as fixture } from '@/local-synthetic/WorkflowPreview'
import { nonAppraisedSample } from '@/local-synthetic/non-appraised-sample'
import { syntheticGasAuditApi } from '@/local-synthetic/synthetic-gas-audit-api'
import { ReadApiError } from '@/service/read-service'
import type { EcasInboxPage } from '@/contracts/appraisal'
import { EcasInboxReadPage, GasSearchReadPage } from '../ReadWorkflowPages'

function mount(
  children: ReactNode,
  capabilities = staffSession.capabilities,
  ecasMyToDoAvailable?: boolean,
) {
  setViewportWidth(1440)
  const auth: AuthContextValue = {
    state: { kind: 'signed-in', session: { ...staffSession, ecasMyToDoAvailable } },
    can: (capability) => capabilities.includes(capability),
    reloadSession: vi.fn(async () => {}),
    login: vi.fn(async () => {}),
    logout: vi.fn(async () => {}),
  }
  render(
    <AuthContext value={auth}>
      <main id="main-content">{children}</main>
    </AuthContext>,
  )
  return auth
}

test('submits the selected mode only on Search and clears results when the mode changes', async () => {
  const user = userEvent.setup()
  const api = { ...syntheticReadApi, inbox: vi.fn(syntheticReadApi.inbox) }
  mount(<EcasInboxReadPage api={api} />, undefined, true)
  const mode = screen.getByRole('combobox', { name: 'Search mode' })
  expect(mode).toHaveValue('ALL_SUBMISSIONS')
  expect(api.inbox).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await screen.findByRole('table', { name: 'ECAS submissions' })
  await user.selectOptions(mode, 'MY_TO_DO')
  expect(screen.queryByRole('table', { name: 'ECAS submissions' })).not.toBeInTheDocument()
  expect(api.inbox).toHaveBeenCalledOnce()
  await user.click(screen.getByRole('button', { name: 'Search' }))
  expect(await screen.findByRole('heading', { name: 'No results' })).toBeInTheDocument()
  expect(api.inbox).toHaveBeenLastCalledWith(
    expect.objectContaining({ mode: 'MY_TO_DO' }),
    0,
    expect.any(AbortSignal),
  )
})

test.each([undefined, false])(
  'does not offer My to do when availability is %s',
  async (available) => {
    const user = userEvent.setup()
    const api = { ...syntheticReadApi, inbox: vi.fn(syntheticReadApi.inbox) }
    mount(<EcasInboxReadPage api={api} />, undefined, available)
    expect(screen.queryByRole('combobox', { name: 'Search mode' })).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'My to do list' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() => expect(api.inbox).toHaveBeenCalledOnce())
    expect(api.inbox).toHaveBeenCalledWith(
      expect.objectContaining({ mode: 'ALL_SUBMISSIONS' }),
      0,
      expect.any(AbortSignal),
    )
  },
)

test('Clear all resets mode, filters, results and the selected reference without another request', async () => {
  const user = userEvent.setup()
  const api = { ...syntheticReadApi, inbox: vi.fn(syntheticReadApi.inbox) }
  mount(<EcasInboxReadPage api={api} />, undefined, true)
  await user.selectOptions(screen.getByRole('combobox', { name: 'Search mode' }), 'MY_TO_DO')
  await user.type(screen.getByRole('textbox', { name: 'ECAS ID' }), '999900000002')
  await user.selectOptions(screen.getByRole('combobox', { name: 'Appraisal method' }), 'C')
  await user.selectOptions(screen.getByRole('combobox', { name: 'Sort by' }), 'LICENCE')
  expect(screen.getByText(/takes precedence over method, licence/)).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await user.click(await screen.findByRole('button', { name: /Open ECAS .*mark ZZ9997/ }))
  await screen.findByRole('heading', { name: 'Coast reference' })
  await user.click(screen.getByRole('button', { name: 'Clear all' }))
  expect(screen.getByRole('combobox', { name: 'Search mode' })).toHaveValue('ALL_SUBMISSIONS')
  expect(screen.getByRole('textbox', { name: 'ECAS ID' })).toHaveValue('')
  expect(screen.getByRole('combobox', { name: 'Appraisal method' })).toHaveValue('')
  expect(screen.getByRole('combobox', { name: 'Sort by' })).toHaveValue('ECAS_ID')
  expect(screen.queryByRole('table', { name: 'ECAS submissions' })).not.toBeInTheDocument()
  await waitFor(() =>
    expect(screen.queryByRole('heading', { name: 'Coast reference' })).not.toBeInTheDocument(),
  )
  expect(api.inbox).toHaveBeenCalledOnce()
})

test('aborts an old mode request and ignores its late rows after a new mode search', async () => {
  const user = userEvent.setup()
  let releaseOld: (page: EcasInboxPage) => void = () => {}
  let oldSignal: AbortSignal | undefined
  const api = {
    ...syntheticReadApi,
    inbox: vi.fn((...args: Parameters<typeof syntheticReadApi.inbox>) => {
      if (args[0].mode === 'ALL_SUBMISSIONS') {
        oldSignal = args[2]
        return new Promise<EcasInboxPage>((resolve) => (releaseOld = resolve))
      }
      return syntheticReadApi.inbox(...args)
    }),
  }
  mount(<EcasInboxReadPage api={api} />, undefined, true)
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await waitFor(() => expect(api.inbox).toHaveBeenCalledOnce())
  await user.selectOptions(screen.getByRole('combobox', { name: 'Search mode' }), 'MY_TO_DO')
  expect(oldSignal?.aborted).toBe(true)
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await screen.findByRole('heading', { name: 'No results' })
  await act(async () => releaseOld({ items: [fixture.ecasInboxItem], total: 250, page: 0 }))
  expect(screen.getByRole('heading', { name: 'No results' })).toBeInTheDocument()
  expect(screen.queryByRole('table', { name: 'ECAS submissions' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Next page' })).not.toBeInTheDocument()
})

test('searches, opens the selected ECAS reference and follows its immutable ID to a GAS summary', async () => {
  const user = userEvent.setup()
  const audit = { history: vi.fn(syntheticGasAuditApi.history) }
  const api = {
    ...syntheticReadApi,
    inbox: vi.fn(syntheticReadApi.inbox),
    relatedSummary: vi.fn(syntheticReadApi.relatedSummary),
    licenceInformation: vi.fn(syntheticReadApi.licenceInformation),
  }
  mount(<EcasInboxReadPage api={api} gasAuditApi={audit} />)
  expect(api.inbox).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Search' }))
  const launcher = await screen.findByRole('button', { name: /Open ECAS .*mark ZZ9997/ })
  expect(
    within(screen.getByRole('table', { name: 'ECAS submissions' })).getAllByRole('row'),
  ).toHaveLength(5)
  await user.click(launcher)
  expect(await screen.findByRole('heading', { name: 'Coast reference' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'View related GAS worksheet' }))
  expect(await screen.findByRole('cell', { name: '12.30' })).toBeInTheDocument()
  expect(api.relatedSummary).toHaveBeenCalledWith('999900000002', expect.any(AbortSignal))
  expect(screen.getByText('COAST_MPS_TOA_N')).toBeInTheDocument()
  await waitFor(() =>
    expect(api.licenceInformation).toHaveBeenCalledWith(
      '',
      fixture.gasMultiMarkAppraisedSummary.primaryTimberMark,
      expect.any(AbortSignal),
    ),
  )
  expect(
    within(screen.getByRole('region', { name: 'Worksheet FTA information' })).getByText(
      fixture.gasMultiMarkAppraisedSummary.primaryTimberMark!,
    ),
  ).toBeInTheDocument()
  expect(audit.history).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'History' }))
  const history = within(await screen.findByRole('region', { name: 'Appraised worksheet history' }))
  await history.findByText('<script>Fictional appraised comment as text</script>')
  expect(history.getByText('2026-10-12T10:00:00')).toBeInTheDocument()
  expect(history.getByText('Discount Percent')).toBeInTheDocument()
  expect(history.getByRole('cell', { name: '10.0' })).toBeInTheDocument()
  await user.click(history.getByRole('button', { name: 'Next page' }))
  await history.findByText('2026-10-02T10:00:00')
  expect(audit.history).toHaveBeenLastCalledWith(
    fixture.gasMultiMarkAppraisedSummary.key,
    1,
    expect.any(AbortSignal),
  )
  const lastSignal = audit.history.mock.calls.at(-1)![2]
  await user.click(screen.getByRole('button', { name: 'Close' }))
  await waitFor(() => expect(launcher).toHaveFocus())
  expect(lastSignal.aborted).toBe(true)
})

test('summary FTA uses its primary mark instead of the selected or first listed search mark', async () => {
  const user = userEvent.setup()
  const api = {
    ...syntheticReadApi,
    worksheet: vi.fn().mockResolvedValue({
      ...fixture.gasMultiMarkAppraisedSummary,
      primaryTimberMark: 'ZZ9997',
      timberMarks: ['ZZ9998', 'ZZ9997'],
    }),
    licenceInformation: vi.fn(async (_licence: string, timberMark: string) => ({
      ...fixture.gasSearchResult.licenceInformation!,
      timberMark,
      markStatus: { code: timberMark, description: `Mark ${timberMark}` },
      cruiseBased: false,
    })),
  }
  mount(<GasSearchReadPage api={api} />)
  await user.type(screen.getByRole('textbox', { name: 'Timber mark' }), 'ZZ9998')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await user.click(
    await screen.findByRole('button', { name: /Open APPRAISED worksheet .*mark ZZ9998/ }),
  )
  const context = within(await screen.findByRole('region', { name: 'Worksheet FTA information' }))
  expect(await context.findByText('Mark ZZ9997')).toBeInTheDocument()
  expect(context.queryByText('Mark ZZ9998')).not.toBeInTheDocument()
  expect(api.licenceInformation).toHaveBeenCalledWith('', 'ZZ9997', expect.any(AbortSignal))
  expect(api.worksheet).toHaveBeenCalledOnce()
})

test('never offers the related GAS action without its capability', async () => {
  const user = userEvent.setup()
  mount(<EcasInboxReadPage api={syntheticReadApi} />, ['ECAS_SUBMISSION_VIEW'])
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await user.click(await screen.findByRole('button', { name: /Open ECAS .*mark ZZ9997/ }))
  await screen.findByRole('heading', { name: 'Coast reference' })
  expect(
    screen.queryByRole('button', { name: 'View related GAS worksheet' }),
  ).not.toBeInTheDocument()
})

test('preserves the licence chooser and FTA context when worksheets are empty', async () => {
  const user = userEvent.setup()
  const api = { ...syntheticReadApi, worksheets: vi.fn(syntheticReadApi.worksheets) }
  mount(<GasSearchReadPage api={api} />)
  await user.type(screen.getByRole('textbox', { name: 'Licence' }), 'X99998')
  await screen.findByRole('option', { name: 'ZZ9996' })
  await user.selectOptions(screen.getByRole('combobox', { name: 'Timber mark' }), 'ZZ9996')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  expect(await screen.findByRole('heading', { name: 'No results' })).toBeInTheDocument()
  expect(
    within(screen.getByRole('region', { name: 'Licence information' })).getByText('ZZ9996'),
  ).toBeInTheDocument()
  expect(api.worksheets).toHaveBeenCalledWith('X99998', 'ZZ9996', 0, expect.any(AbortSignal))
  await user.clear(screen.getByRole('textbox', { name: 'Licence' }))
  expect(screen.getByRole('textbox', { name: 'Timber mark' })).toHaveValue('')
  expect(screen.queryByRole('option', { name: 'ZZ9996' })).not.toBeInTheDocument()
})

test('opens the fictional non-appraised preview worksheet with labelled server rate totals', async () => {
  const user = userEvent.setup()
  const audit = { history: vi.fn(syntheticGasAuditApi.history) }
  mount(<GasSearchReadPage api={syntheticReadApi} gasAuditApi={audit} />)
  await user.type(screen.getByRole('textbox', { name: 'Licence' }), nonAppraisedSample.licence!)
  await screen.findByRole('option', { name: nonAppraisedSample.timberMark! })
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await user.click(
    await screen.findByRole('button', {
      name: `Open NON_APPRAISED worksheet ${nonAppraisedSample.key.worksheetId}, mark ${nonAppraisedSample.timberMark}`,
    }),
  )
  const rates = within(await screen.findByRole('table', { name: 'Non-appraised rates' }))
  expect(rates.getByRole('cell', { name: 'Logs' })).toBeInTheDocument()
  expect(rates.getByRole('cell', { name: 'Ungraded' })).toBeInTheDocument()
  expect(rates.getByRole('cell', { name: '14.70' })).toBeInTheDocument()
  expect(rates.getByRole('cell', { name: 'UNKNOWN' })).toBeInTheDocument()
  expect(screen.getByText('Synthetic forest zone')).toBeInTheDocument()
  expect(audit.history).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'History' }))
  const history = within(
    await screen.findByRole('region', { name: 'Non-appraised worksheet history' }),
  )
  await history.findByText('<script>Fictional comment as text</script>')
  expect(history.getByText('2026-10-12T09:00:00')).toBeInTheDocument()
  expect(
    within(history.getByRole('table', { name: 'Worksheet history' })).getAllByRole('row'),
  ).toHaveLength(11)
  await user.click(history.getByRole('button', { name: 'Next page' }))
  await history.findByText('2026-10-02T09:00:00')
  expect(
    within(history.getByRole('table', { name: 'Worksheet history' })).getAllByRole('row'),
  ).toHaveLength(3)
  expect(audit.history).toHaveBeenLastCalledWith(nonAppraisedSample.key, 1, expect.any(AbortSignal))
  expect(history.queryByText('<script>Fictional comment as text</script>')).not.toBeInTheDocument()
})

test('retains worksheet rows when the separate FTA request fails and retries only that request', async () => {
  const user = userEvent.setup()
  const api = {
    ...syntheticReadApi,
    worksheets: vi.fn(syntheticReadApi.worksheets),
    licenceInformation: vi
      .fn()
      .mockRejectedValueOnce(new ReadApiError(503))
      .mockResolvedValue(fixture.gasSearchResult.licenceInformation),
  }
  mount(<GasSearchReadPage api={api} />)
  await user.type(screen.getByRole('textbox', { name: 'Timber mark' }), 'ZZ9997')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await screen.findByRole('table', { name: 'GAS worksheets' })
  await user.click(screen.getByRole('button', { name: 'Retry licence information' }))
  await waitFor(() => expect(api.licenceInformation).toHaveBeenCalledTimes(2))
  expect(api.worksheets).toHaveBeenCalledOnce()
})

test('refreshes the session after 401 and presents a safe denial with retry for 403', async () => {
  const user = userEvent.setup()
  const api = {
    ...syntheticReadApi,
    inbox: vi
      .fn()
      .mockRejectedValueOnce(new ReadApiError(401))
      .mockRejectedValueOnce(new ReadApiError(403)),
  }
  const auth = mount(<EcasInboxReadPage api={api} />)
  await user.click(screen.getByRole('button', { name: 'Search' }))
  await waitFor(() => expect(auth.reloadSession).toHaveBeenCalledOnce())
  await user.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByText('You do not have access to this information.')).toBeInTheDocument()
})

test('keeps pagination and keyboard focus in place while the next page loads', async () => {
  const user = userEvent.setup()
  let releasePage: () => void = () => {}
  const api = {
    ...syntheticReadApi,
    inbox: vi.fn(async (...args: Parameters<typeof syntheticReadApi.inbox>) => {
      const page = await syntheticReadApi.inbox(...args)
      if (args[1] > 0) await new Promise<void>((resolve) => (releasePage = resolve))
      return { ...page, page: args[1], total: 250 }
    }),
  }
  mount(<EcasInboxReadPage api={api} />)
  await user.click(screen.getByRole('button', { name: 'Search' }))
  const next = await screen.findByRole('button', { name: 'Next page' })
  await user.click(next)
  await waitFor(() => expect(api.inbox).toHaveBeenCalledTimes(2))
  expect(screen.getByRole('button', { name: 'Next page' })).toHaveFocus()
  releasePage()
  expect(await screen.findByText(/101.200 of 250 items/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Next page' })).toHaveFocus()
})
