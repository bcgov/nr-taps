import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { expect, test, vi } from 'vitest'
import type { EcasSearchFilters, EcasLookups } from '@/contracts/appraisal'
import EcasSearchFiltersForm from '../EcasSearchFilters'
import { additionalFilterErrors } from '../ecas-filter-validation'

const empty: EcasSearchFilters = { ecasId: '', licence: '', timberMark: '' }
const lookups: EcasLookups = {
  appraisalMethods: [],
  appraisalStatuses: [],
  organizations: [{ code: '10', description: 'DCA — District A' }],
  appraisalCategories: [{ code: 'OLD', description: 'Historical category', active: false }],
  reappraisalReasons: [{ code: 'CHG', description: 'Change', active: true }],
  fileTypes: [{ code: 'A01', description: 'Licence', active: true }],
}

function Form({
  initial = empty,
  onSearch,
}: {
  initial?: EcasSearchFilters
  onSearch: (draft: EcasSearchFilters) => void
}) {
  const [draft, setDraft] = useState(initial)
  return (
    <EcasSearchFiltersForm
      draft={draft}
      onChange={setDraft}
      onSearch={() => onSearch(draft)}
      onReset={() => setDraft(empty)}
      loading={false}
      lookups={lookups}
      lookupLoading={false}
      onRetryLookups={vi.fn()}
    />
  )
}

test('additional controls keep numeric client, alphanumeric location, false flags and inactive codes', async () => {
  const user = userEvent.setup(),
    search = vi.fn()
  render(<Form onSearch={search} />)
  await user.click(screen.getByRole('button', { name: 'Additional filters' }))
  await user.type(screen.getByLabelText('Licence'), 'A00001')
  await user.type(screen.getByLabelText('Cutting permit'), '001')
  await user.type(screen.getByLabelText('Client number'), '12')
  await user.type(screen.getByLabelText('Client location'), 'a1')
  await user.selectOptions(screen.getByLabelText('Appraisal type'), 'OLD')
  await user.selectOptions(screen.getByLabelText('Reappraisal reason'), 'CHG')
  await user.selectOptions(screen.getByLabelText('File type'), 'A01')
  await user.selectOptions(screen.getByLabelText('BCTS funded'), 'N')
  await user.selectOptions(screen.getByLabelText('Certification statement'), 'N')
  await user.type(screen.getByLabelText('Management unit type'), 'U')
  await user.type(screen.getByLabelText('Management unit ID'), '12')
  await user.type(screen.getByLabelText('Worked on by user ID'), 'IDIR\\MixedCase')
  await user.click(screen.getByRole('button', { name: 'Search' }))
  expect(search).toHaveBeenCalledWith(
    expect.objectContaining({
      cuttingPermit: '001',
      clientNumber: '12',
      clientLocationCode: 'a1',
      appraisalCategoryCode: 'OLD',
      reappraisalReasonCode: 'CHG',
      fileTypeCode: 'A01',
      bctsFunded: false,
      certified: false,
      managementUnitType: 'U',
      managementUnitId: '12',
      workedOnByUserId: 'IDIR\\MixedCase',
    }),
  )
})

test('dependent values clear when changing their parent and reset restores all filters', async () => {
  const user = userEvent.setup(),
    search = vi.fn()
  render(
    <Form
      initial={{
        ...empty,
        clientNumber: '1',
        clientLocationCode: '01',
        managementUnitType: 'U',
        managementUnitId: '12',
      }}
      onSearch={search}
    />,
  )
  await user.click(screen.getByRole('button', { name: 'Additional filters' }))
  await user.clear(screen.getByLabelText('Client number'))
  expect(screen.getByLabelText('Client location')).toHaveValue('')
  await user.clear(screen.getByLabelText('Management unit type'))
  expect(screen.getByLabelText('Management unit ID')).toHaveValue('')
  await user.click(screen.getByRole('button', { name: 'Clear all' }))
  await user.click(screen.getByRole('button', { name: 'Search' }))
  expect(search).toHaveBeenCalledWith(empty)
})

test('selected organizations are submitted as filters without changing available grants', async () => {
  const user = userEvent.setup(),
    search = vi.fn()
  render(<Form onSearch={search} />)
  await user.click(screen.getByRole('button', { name: 'Additional filters' }))
  await user.click(screen.getByRole('combobox', { name: /^Organization units/ }))
  await user.click(screen.getByText('DCA — District A', { exact: true }))
  await user.click(screen.getByRole('button', { name: 'Search' }))
  expect(search).toHaveBeenCalledWith(expect.objectContaining({ orgUnitNumbers: ['10'] }))
})

test.each([
  [{ cuttingPermit: '001' }, 'cuttingPermit'],
  [{ managementUnitId: '12' }, 'managementUnitId'],
  [{ clientLocationCode: '01' }, 'clientLocationCode'],
  [{ clientNumber: 'ABC' }, 'clientNumber'],
  [{ managementUnitType: ';' }, 'managementUnitType'],
] as [Partial<EcasSearchFilters>, keyof EcasSearchFilters][])(
  'enforces dependent filter validation: %j',
  (values, field) => {
    expect(additionalFilterErrors({ ...empty, ...values })[field]).toBeTruthy()
  },
)
