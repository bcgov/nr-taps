import type { GasAppraisalItem, GasNonAppraisedSummary } from '@/contracts/appraisal'

// Fictional local preview values only; no shared-database records or policy calculations.
export const nonAppraisedSample: GasNonAppraisedSummary = {
  key: { type: 'NON_APPRAISED', worksheetId: '999900000095' },
  licence: 'X99995',
  timberMark: 'ZZ9995',
  appraisalMethod: 'I',
  status: { code: 'SYN', description: 'Synthetic stored worksheet' },
  effectiveDate: '2026-10-01',
  expiryDate: '2027-09-30',
  referenceType: { code: 'SYN', description: 'Synthetic reference type' },
  sdmDeclarationAcceptanceDate: null,
  tsbNumberCode: 'SYN',
  appraisalForestZone: { code: 'Z', description: 'Synthetic forest zone' },
  nonAppraisedRateType: { code: 'S', description: 'Synthetic stored rate type' },
  rateAdjustmentType: { code: 'N', description: 'Synthetic adjustment type' },
  rates: [
    {
      rateId: '999900000096',
      scaleSpecies: { code: 'FI', description: 'Synthetic fir' },
      scaleProduct: { code: ' ', description: 'Logs' },
      scaleGrade: { code: ' ', description: 'Ungraded' },
      reserveStumpageRate: '12.30',
      silvicultureLevy: '0.00',
      developmentLevy: null,
      upsetStumpageRate: '12.30',
      bonusBidAmount: '2.40',
      totalStumpageRate: '14.70',
    },
    {
      rateId: '999900000097',
      scaleSpecies: { code: 'UNKNOWN', description: null },
      scaleProduct: { code: '02', description: 'Synthetic product' },
      scaleGrade: { code: 'B', description: null },
      reserveStumpageRate: '0.00',
      silvicultureLevy: null,
      developmentLevy: '0.00',
      upsetStumpageRate: '0.00',
      bonusBidAmount: null,
      totalStumpageRate: '0.00',
    },
  ],
  selectedRateAddons: [],
}

export const nonAppraisedSampleItem: GasAppraisalItem = {
  key: nonAppraisedSample.key,
  licence: nonAppraisedSample.licence,
  timberMark: nonAppraisedSample.timberMark,
  status: nonAppraisedSample.status,
  effectiveDate: nonAppraisedSample.effectiveDate,
  expiryDate: nonAppraisedSample.expiryDate,
  referenceTypeCode: nonAppraisedSample.referenceType?.code ?? null,
}
