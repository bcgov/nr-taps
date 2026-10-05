import { render, screen, within } from '@testing-library/react'
import { expect, test } from 'vitest'
import type { GasHistoricSummary, GasNonAppraisedSummary } from '@/contracts/appraisal'
import OtherWorksheetDetails from '../OtherWorksheetDetails'

const rate = {
  rateId: '999900000099',
  scaleSpeciesCode: 'FI',
  scaleProductCode: '01',
  scaleGradeCode: 'Z',
  reserveStumpageRate: '0.00',
  bonusBidAmount: '12.30',
  developmentLevy: null,
  silvicultureLevy: '0.10',
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
  render(<OtherWorksheetDetails summary={summary} />)
  expect(screen.getByText('Historic')).toBeInTheDocument()
  expect(screen.getByText('Yes')).toBeInTheDocument()
  expect(screen.getByText('No')).toBeInTheDocument()
  expect(
    within(screen.getByRole('table', { name: 'Historic stored rates' })).getByRole('cell', {
      name: '99.90',
    }),
  ).toBeInTheDocument()
  const components = within(screen.getByRole('table', { name: 'Stored rate components' }))
  expect(components.getByRole('cell', { name: '0.00' })).toBeInTheDocument()
  expect(components.getByRole('cell', { name: '12.30' })).toBeInTheDocument()
  expect(components.getByRole('cell', { name: '—' })).toBeInTheDocument()
  const species = within(screen.getByRole('table', { name: 'Historic species' }))
  expect(species.getByRole('cell', { name: '123.4500' })).toBeInTheDocument()
  expect(species.getAllByRole('row')).toHaveLength(3)
  expect(species.getAllByRole('cell', { name: '—' })).toHaveLength(5)
  const grades = within(screen.getByRole('table', { name: 'Coast species grades' }))
  expect(grades.getByRole('cell', { name: '02' })).toBeInTheDocument()
  expect(grades.getByRole('cell', { name: '40.50' })).toBeInTheDocument()
  expect(screen.queryByRole('table', { name: 'Selected rate add-ons' })).not.toBeInTheDocument()
})

test('non-appraised summary uses its own reference, classification and component fields', () => {
  const summary: GasNonAppraisedSummary = {
    ...common,
    key: { type: 'NON_APPRAISED', worksheetId: '999900000090' },
    referenceTypeCode: 'REF',
    sdmDeclarationAcceptanceDate: '2026-10-01',
    tsbNumberCode: 'TSB',
    appraisalForestZoneCode: 'ZONE',
    nonAppraisedRateTypeCode: 'TYPE',
    rateAdjustmentTypeCode: 'ADJ',
    rates: [rate],
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
  render(<OtherWorksheetDetails summary={summary} />)
  expect(screen.getByText('Non-appraised')).toBeInTheDocument()
  expect(screen.getByText('REF')).toBeInTheDocument()
  expect(screen.getByText('ADJ')).toBeInTheDocument()
  expect(screen.getByText('2026-10-01')).toBeInTheDocument()
  expect(screen.queryByRole('table', { name: 'Historic stored rates' })).not.toBeInTheDocument()
  expect(screen.getByRole('cell', { name: '12.30' })).toBeInTheDocument()
  const addons = within(screen.getByRole('table', { name: 'Selected rate add-ons' }))
  expect(addons.getByRole('cell', { name: 'EXPIRED - Historic selection' })).toBeInTheDocument()
  expect(addons.getByRole('cell', { name: '2000-01-02T12:34:56' })).toBeInTheDocument()
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  expect(screen.queryByRole('table', { name: 'Historic species' })).not.toBeInTheDocument()
})

test('an empty selected-add-on list is explicit and does not imply a calculated zero cost', () => {
  render(
    <OtherWorksheetDetails
      summary={{
        ...common,
        key: { type: 'NON_APPRAISED', worksheetId: '42' },
        referenceTypeCode: null,
        sdmDeclarationAcceptanceDate: null,
        tsbNumberCode: null,
        appraisalForestZoneCode: null,
        nonAppraisedRateTypeCode: null,
        rateAdjustmentTypeCode: null,
        rates: [],
        selectedRateAddons: [],
      }}
    />,
  )
  expect(screen.getByText('No selected rate add-ons recorded.')).toBeInTheDocument()
  expect(screen.queryByRole('table', { name: 'Selected rate add-ons' })).not.toBeInTheDocument()
  expect(screen.queryByText('0.00')).not.toBeInTheDocument()
})
