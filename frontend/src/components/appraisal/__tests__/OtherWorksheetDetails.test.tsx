import { render, screen, within } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import type { CodeOption, GasHistoricSummary, GasNonAppraisedSummary } from '@/contracts/appraisal'
import { workflowFixture } from '@/local-synthetic/WorkflowPreview'
import OtherWorksheetDetails from '../OtherWorksheetDetails'
import { syntheticFtaApi } from '@/local-synthetic/synthetic-fta-api'

vi.mock('@/context/auth/AuthContext', () => ({
  useAuth: () => ({ reloadSession: async () => {} }),
}))

const rate = {
  rateId: '999900000099',
  scaleSpecies: { code: 'FI', description: 'Fir' },
  scaleProduct: { code: ' ', description: 'Logs' },
  scaleGrade: { code: ' ', description: 'Ungraded' },
  reserveStumpageRate: '0.00',
  bonusBidAmount: '12.30',
  developmentLevy: null,
  silvicultureLevy: '0.10',
  upsetStumpageRate: '18.07',
  totalStumpageRate: '30.37',
}
const common = {
  licence: 'X99999',
  timberMark: 'ZZ9999',
  appraisalMethod: 'I' as const,
  status: null,
  effectiveDate: null,
  expiryDate: null,
}

test('historic summary retains flags, exact stored rates and separate rate components', () => {
  const summary: GasHistoricSummary = {
    ...common,
    key: { type: 'HISTORIC', worksheetId: '999900000090' },
    variant: 'INTERIOR_MPS',
    rateCalculationMethodCode: 'MPS',
    tenureObligationAdjustment: true,
    adjustQuarterly: false,
    active: null,
    policyVersion: '2026.1',
    ceaseAdjustmentDate: '2026-10-01',
    rates: [{ rateId: '999900000098', effectiveDate: '2026-10-01', totalStumpageRate: '99.90' }],
    nonAppraisedRates: [rate],
    historicSpecies: [
      {
        speciesId: '1',
        scaleSpeciesCode: 'FI',
        sellingPriceZone: null,
        speciesVolume: '123.4500',
        lumberRecoveryFactor: '300',
        speciesDecayPercent: null,
        speciesStudPercent: '0',
        speciesBurnPercent: '12',
      },
      {
        speciesId: '2',
        scaleSpeciesCode: 'HE',
        sellingPriceZone: '02',
        speciesVolume: '0',
        lumberRecoveryFactor: null,
        speciesDecayPercent: '5',
        speciesStudPercent: null,
        speciesBurnPercent: null,
      },
    ],
    coastSpeciesGrades: [
      {
        speciesGradeId: '3',
        scaleSpeciesCode: 'FI',
        scaleProductCode: '02',
        scaleGradeCode: 'B',
        speciesGradePercent: '40.50',
      },
    ],
  }
  render(<OtherWorksheetDetails summary={summary} api={syntheticFtaApi} />)
  expect(screen.getByText('Historic')).toBeInTheDocument()
  expect(screen.getByText('Yes')).toBeInTheDocument()
  expect(screen.getByText('No')).toBeInTheDocument()
  expect(
    within(screen.getByRole('table', { name: 'Historic stored rates' })).getByRole('cell', {
      name: '99.90',
    }),
  ).toBeInTheDocument()
  const components = within(screen.getByRole('table', { name: 'Non-appraised rates' }))
  expect(components.getByRole('cell', { name: '0.00' })).toBeInTheDocument()
  expect(components.getByRole('cell', { name: '12.30' })).toBeInTheDocument()
  expect(components.getByRole('cell', { name: '—' })).toBeInTheDocument()
  expect(components.getByRole('cell', { name: 'Logs' })).toBeInTheDocument()
  expect(components.getByRole('cell', { name: 'Ungraded' })).toBeInTheDocument()
  expect(components.getByRole('cell', { name: '18.07' })).toBeInTheDocument()
  expect(components.getByRole('cell', { name: '30.37' })).toBeInTheDocument()
  const species = within(screen.getByRole('table', { name: 'Historic species' }))
  expect(species.getByRole('cell', { name: '123.4500' })).toBeInTheDocument()
  expect(species.getAllByRole('row')).toHaveLength(3)
  expect(species.getAllByRole('cell', { name: '—' })).toHaveLength(5)
  const grades = within(screen.getByRole('table', { name: 'Coast species grades' }))
  expect(grades.getByRole('cell', { name: '02' })).toBeInTheDocument()
  expect(grades.getByRole('cell', { name: '40.50' })).toBeInTheDocument()
  expect(screen.queryByRole('table', { name: 'Selected rate add-ons' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'History' })).not.toBeInTheDocument()
})

test('non-appraised summary uses its own reference, classification and component fields', () => {
  const summary: GasNonAppraisedSummary = {
    ...common,
    key: { type: 'NON_APPRAISED', worksheetId: '999900000090' },
    referenceType: { code: 'REF', description: 'Reference label' },
    sdmDeclarationAcceptanceDate: '2026-10-01',
    timberSupplyBlock: { code: 'TSB', description: 'TSB - Synthetic block' },
    appraisalForestZone: { code: 'ZONE', description: null },
    nonAppraisedRateType: null,
    rateAdjustmentType: { code: 'ADJ', description: 'Adjustment label' },
    rates: [
      rate,
      {
        ...rate,
        rateId: '999900000100',
        scaleSpecies: { code: 'OTHER', description: null },
        scaleProduct: { code: '02', description: null },
        scaleGrade: { code: 'Z', description: null },
        reserveStumpageRate: '0.00',
        silvicultureLevy: null,
        developmentLevy: '0.00',
        upsetStumpageRate: '0.00',
        bonusBidAmount: '0.00',
        totalStumpageRate: '0.00',
      },
    ],
    selectedRateAddons: [
      {
        code: 'EXPIRED',
        description: 'EXPIRED - Historic selection',
        effectiveDate: '2000-01-01T00:00:00',
        expiryDate: '2001-01-01T00:00:00',
        updateTimestamp: '2000-01-02T12:34:56',
      },
    ],
  }
  render(<OtherWorksheetDetails summary={summary} api={syntheticFtaApi} />)
  expect(screen.getByText('Non-appraised')).toBeInTheDocument()
  expect(screen.getByText('Reference label')).toBeInTheDocument()
  expect(screen.getByText('ZONE')).toBeInTheDocument()
  expect(within(screen.getByText('Rate type').parentElement!).getByText('—')).toBeInTheDocument()
  expect(screen.getByText('Adjustment label')).toBeInTheDocument()
  expect(screen.getByText('2026-10-01')).toBeInTheDocument()
  expect(screen.queryByRole('table', { name: 'Historic stored rates' })).not.toBeInTheDocument()
  expect(screen.getByRole('cell', { name: '12.30' })).toBeInTheDocument()
  const components = within(screen.getByRole('table', { name: 'Non-appraised rates' }))
  expect(components.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
    'Species',
    'Product',
    'Grade',
    'Reserve rate',
    'Silviculture levy',
    'Development levy',
    'Upset rate',
    'Bonus bid',
    'Total rate',
  ])
  const rows = components.getAllByRole('row').slice(1)
  expect(
    rows.map((row) =>
      within(row)
        .getAllByRole('cell')
        .map((cell) => cell.textContent),
    ),
  ).toEqual([
    ['Fir', 'Logs', 'Ungraded', '0.00', '0.10', '—', '18.07', '12.30', '30.37'],
    ['OTHER', '02', 'Z', '0.00', '—', '0.00', '0.00', '0.00', '0.00'],
  ])
  expect(components.queryByRole('cell', { name: rate.rateId })).not.toBeInTheDocument()
  const addons = within(screen.getByRole('table', { name: 'Selected rate add-ons' }))
  expect(addons.getByRole('cell', { name: 'EXPIRED - Historic selection' })).toBeInTheDocument()
  expect(addons.getByRole('cell', { name: '2000-01-02T12:34:56' })).toBeInTheDocument()
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  expect(screen.queryByRole('table', { name: 'Historic species' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'History' })).toHaveAttribute('aria-expanded', 'false')
})

test('an empty selected-add-on list is explicit and does not imply a calculated zero cost', () => {
  render(
    <OtherWorksheetDetails
      api={syntheticFtaApi}
      summary={{
        ...common,
        key: { type: 'NON_APPRAISED', worksheetId: '42' },
        referenceType: null,
        sdmDeclarationAcceptanceDate: null,
        timberSupplyBlock: null,
        appraisalForestZone: null,
        nonAppraisedRateType: null,
        rateAdjustmentType: null,
        rates: [],
        selectedRateAddons: [],
      }}
    />,
  )
  expect(screen.getByText('No selected rate add-ons recorded.')).toBeInTheDocument()
  expect(screen.queryByRole('table', { name: 'Selected rate add-ons' })).not.toBeInTheDocument()
  expect(screen.queryByText('0.00')).not.toBeInTheDocument()
})

test.each([
  ['C', { code: 'SYN', description: 'SYN - Synthetic block' }, 'SYN - Synthetic block'],
  ['C', { code: 'SYN', description: null }, 'SYN'],
  ['C', null, '—'],
  ['I', { code: 'SYN', description: 'SYN - Synthetic block' }, null],
] as ['C' | 'I', CodeOption | null, string | null][])(
  'timber supply block respects method %s and stored label availability: %j',
  (appraisalMethod, timberSupplyBlock, expected) => {
    render(
      <OtherWorksheetDetails
        api={syntheticFtaApi}
        summary={{
          ...common,
          appraisalMethod,
          key: { type: 'NON_APPRAISED', worksheetId: '999900000090' },
          referenceType: null,
          sdmDeclarationAcceptanceDate: null,
          timberSupplyBlock,
          appraisalForestZone: null,
          nonAppraisedRateType: null,
          rateAdjustmentType: null,
          rates: [],
          selectedRateAddons: [],
        }}
      />,
    )
    if (expected === null) {
      expect(screen.queryByText('Timber supply block')).not.toBeInTheDocument()
      expect(screen.queryByText('SYN - Synthetic block')).not.toBeInTheDocument()
    } else {
      expect(
        within(screen.getByText('Timber supply block').parentElement!).getByText(expected),
      ).toBeInTheDocument()
    }
  },
)

test('appraised summary does not offer non-appraised history', () => {
  render(
    <OtherWorksheetDetails summary={workflowFixture.gasAppraisedSummary} api={syntheticFtaApi} />,
  )
  expect(screen.queryByRole('button', { name: 'History' })).not.toBeInTheDocument()
})
