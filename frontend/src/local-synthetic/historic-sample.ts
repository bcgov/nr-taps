import type { GasAppraisalItem, GasHistoricSummary } from '@/contracts/appraisal'
import { nonAppraisedSample } from './non-appraised-sample'

// Fictional local preview values only; no shared-database records or policy calculations.
export const historicSample: GasHistoricSummary = {
  key: { type: 'HISTORIC', worksheetId: '999900000098' },
  licence: nonAppraisedSample.licence,
  timberMark: nonAppraisedSample.timberMark,
  appraisalMethod: 'C',
  variant: 'COAST_MPS_TOA_Y',
  rateCalculationMethodCode: 'MPS',
  tenureObligationAdjustment: true,
  adjustQuarterly: false,
  active: false,
  policyVersion: 'SYNTHETIC-2025',
  status: { code: 'SYN', description: 'Synthetic historic worksheet' },
  effectiveDate: '2025-10-01',
  expiryDate: '2026-09-30',
  ceaseAdjustmentDate: '2026-09-30',
  rates: [
    {
      rateId: '999900000099',
      effectiveDate: '2025-10-01',
      totalStumpageRate: nonAppraisedSample.rates[0].totalStumpageRate,
    },
  ],
  nonAppraisedRates: nonAppraisedSample.rates,
  historicSpecies: [
    {
      speciesId: '999900000100',
      scaleSpeciesCode: 'SYNTHETIC',
      sellingPriceZone: 'SYN',
      speciesVolume: '123.4500',
      lumberRecoveryFactor: '300.00',
      speciesDecayPercent: null,
      speciesStudPercent: '0.00',
      speciesBurnPercent: '12.00',
    },
  ],
  coastSpeciesGrades: [
    {
      speciesGradeId: '999900000101',
      scaleSpeciesCode: 'SYNTHETIC',
      scaleProductCode: 'SYN',
      scaleGradeCode: 'S',
      speciesGradePercent: '100.00',
    },
  ],
}

export const historicSampleItem: GasAppraisalItem = {
  key: historicSample.key,
  licence: historicSample.licence,
  timberMark: historicSample.timberMark,
  status: historicSample.status,
  effectiveDate: historicSample.effectiveDate,
  expiryDate: historicSample.expiryDate,
  referenceTypeCode: 'SYN',
}
