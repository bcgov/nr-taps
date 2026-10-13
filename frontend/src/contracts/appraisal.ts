// JSON shapes of the backend read records.
export type CodeOption = { code: string | null; description: string | null }
export type AppraisalMethod = 'C' | 'I'

export type EcasInboxItem = {
  ecasId: string
  appraisalMethod: AppraisalMethod | null
  timberMark: string | null
  licence: string | null
  cuttingPermit: string | null
  status: CodeOption | null
  statusDate: string | null
  appraisalTypeDescription: string | null
  effectiveDate: string | null
  expiryDate: string | null
  licenseeSubmittedDate: string | null
  districtReceivedDate: string | null
  sentToRegionDate: string | null
  multipleTimberMarks: boolean | null
  clientNumber: string | null
  clientLocationCode: string | null
  clientName: string | null
  revisionCount: number | null
}

export type ReferenceHeader = {
  ecasId: string
  appraisalMethod: AppraisalMethod
  revisionCount: number | null
  licence: string | null
  cuttingPermit: string | null
  clientNumber: string | null
  clientLocationCode: string | null
  licenseeName: string | null
  status: CodeOption | null
  appraisalCategoryCode: string | null
  reappraisalReasonCode: string | null
  rateCalculationMethodCode: string | null
  coniferousStandRateEligibility: CodeOption | null
  deciduousStandRateEligibility: CodeOption | null
  effectiveDate: string | null
  expiryDate: string | null
  administrativeDistrict: CodeOption | null
  geographicDistrict: CodeOption | null
  fileType: CodeOption | null
  timberSupplyArea: CodeOption | null
  timberSupplyBlock: CodeOption | null
}

export type CoastReference = {
  header: ReferenceHeader
  primaryTimberMark: string | null
  timberMarks: {
    timberMark: string | null
    cruiseVolume: number | null
    primary: boolean | null
    revisionCount: number | null
  }[]
  referenceMark: string | null
  netCruiseVolume: number | null
  netMerchantableArea: number | null
  initialMerchantableArea: number | null
  pointOfAppraisalDistance: number | null
  majorCentreCode: string | null
  majorCentreDistance: number | null
}

export type InteriorReference = {
  header: ReferenceHeader
  timberMark: string | null
  timberMarkRevisionCount: number | null
  referenceMark: string | null
  pointOfAppraisal: CodeOption | null
  sellingPriceZoneCode: string | null
  comparativeCruise: boolean | null
  salvage: boolean | null
}

export type WorksheetKey = {
  type: 'APPRAISED' | 'NON_APPRAISED' | 'HISTORIC'
  worksheetId: string
}

export type GasAppraisalItem = {
  key: WorksheetKey
  licence: string | null
  timberMark: string | null
  effectiveDate: string | null
  expiryDate: string | null
  status: CodeOption | null
  referenceTypeCode: string | null
}

export type GasAppraisalPage = { items: GasAppraisalItem[]; total: number; page: number }
export type EcasInboxPage = { items: EcasInboxItem[]; total: number; page: number }
export type EcasDateType = 'EFFCTV' | 'EXPRY' | 'NTRY' | 'LTMD' | 'STTS' | 'FCED'
export type EcasSortField =
  | 'ECAS_ID'
  | 'TIMBER_MARK'
  | 'LICENCE'
  | 'CLIENT_NAME'
  | 'STATUS'
  | 'STATUS_CHANGE_DATE'
  | 'APPRAISAL_TYPE'
  | 'EFFECTIVE_DATE'
  | 'EXPIRY_DATE'
  | 'SUBMITTED_DATE'
  | 'DISTRICT_RECEIVED_DATE'
  | 'SENT_TO_REGION_DATE'
  | 'UPDATE_DATE'
export type EcasSearchFilters = {
  mode?: 'MY_TO_DO' | 'ALL_SUBMISSIONS'
  ecasId: string
  licence: string
  timberMark: string
  cuttingPermit?: string
  clientNumber?: string
  clientLocationCode?: string
  orgUnitNumbers?: string[]
  appraisalCategoryCode?: string
  reappraisalReasonCode?: string
  fileTypeCode?: string
  managementUnitType?: string
  managementUnitId?: string
  workedOnByUserId?: string
  bctsFunded?: boolean | null
  certified?: boolean | null
  appraisalMethod?: AppraisalMethod | ''
  statusCodes?: string[]
  dateTypes?: EcasDateType[]
  dateFrom?: string
  dateTo?: string
  statusDateFrom?: string
  statusDateTo?: string
  sortBy?: EcasSortField
  sortDirection?: 'ASC' | 'DESC'
}
export type EffectiveCode = {
  code: string
  description: string | null
  effectiveDate: string | null
  expiryDate: string | null
  updateTimestamp: string | null
}
export type EcasLookups = {
  appraisalMethods: CodeOption[]
  appraisalStatuses: (EffectiveCode & { active: boolean })[]
  appraisalCategories?: (CodeOption & { active: boolean })[]
  reappraisalReasons?: (CodeOption & { active: boolean })[]
  fileTypes?: (CodeOption & { active: boolean })[]
  organizations?: CodeOption[]
}
export type LicenceMarks = { licence: string; timberMarks: string[] }

export type FtaLicenceInformation = {
  clientNumber: string | null
  licenseeName: string | null
  licenceNumber: string | null
  cuttingPermit: string | null
  fileTypeCode: string | null
  timberMark: string | null
  forestRegion: string | null
  forestDistrict: string | null
  markExpiryDate: string | null
  markExtendDate: string | null
  ftaStatus: string | null
  markStatus: CodeOption | null
  cruiseBased: boolean | null
}

export type GasSearchResult = {
  appraisals: GasAppraisalPage
  licenceInformation: FtaLicenceInformation | null
}

export type GasAppraisedSummary = {
  key: { type: 'APPRAISED'; worksheetId: string }
  ecasId: string
  appraisalMethod: AppraisalMethod
  variant: 'CVP' | 'INTERIOR_MPS' | 'COAST_MPS_TOA_Y' | 'COAST_MPS_TOA_N'
  rateCalculationMethodCode: string
  toaEligible: boolean | null
  status: CodeOption | null
  effectiveDate: string | null
  expiryDate: string | null
  timberMarks: string[]
  primaryTimberMark: string | null
  referenceTypeCode: string | null
  ceaseAdjustmentDate: string | null
  rates: { rateId: string; effectiveDate: string; totalStumpageRate: string }[]
}

export type StoredNonAppraisedRate = {
  rateId: string
  scaleSpecies: CodeOption
  scaleProduct: CodeOption
  scaleGrade: CodeOption
  reserveStumpageRate: string
  bonusBidAmount: string | null
  developmentLevy: string | null
  silvicultureLevy: string | null
  upsetStumpageRate: string
  totalStumpageRate: string
}

export type GasHistoricSummary = {
  key: { type: 'HISTORIC'; worksheetId: string }
  licence: string | null
  timberMark: string | null
  appraisalMethod: AppraisalMethod
  variant: GasAppraisedSummary['variant']
  rateCalculationMethodCode: string
  tenureObligationAdjustment: boolean | null
  adjustQuarterly: boolean | null
  active: boolean | null
  policyVersion: string | null
  status: CodeOption | null
  effectiveDate: string | null
  expiryDate: string | null
  ceaseAdjustmentDate: string | null
  rates: GasAppraisedSummary['rates']
  nonAppraisedRates: StoredNonAppraisedRate[]
  historicSpecies: HistoricSpecies[]
  coastSpeciesGrades: HistoricCoastSpeciesGrade[]
}

export type GasNonAppraisedSummary = {
  key: { type: 'NON_APPRAISED'; worksheetId: string }
  licence: string | null
  timberMark: string | null
  appraisalMethod: AppraisalMethod
  status: CodeOption | null
  effectiveDate: string | null
  expiryDate: string | null
  referenceType: CodeOption | null
  sdmDeclarationAcceptanceDate: string | null
  timberSupplyBlock: CodeOption | null
  appraisalForestZone: CodeOption | null
  nonAppraisedRateType: CodeOption | null
  rateAdjustmentType: CodeOption | null
  rates: StoredNonAppraisedRate[]
  selectedRateAddons: SelectedRateAddon[]
}

export type SelectedRateAddon = {
  code: string
  description: string | null
  effectiveDate: string | null
  expiryDate: string | null
  updateTimestamp: string | null
}

export type HistoricSpecies = {
  speciesId: string
  scaleSpeciesCode: string
  sellingPriceZone: string | null
  speciesVolume: string
  lumberRecoveryFactor: string | null
  speciesDecayPercent: string | null
  speciesStudPercent: string | null
  speciesBurnPercent: string | null
}

export type HistoricCoastSpeciesGrade = {
  speciesGradeId: string
  scaleSpeciesCode: string
  scaleProductCode: string
  scaleGradeCode: string
  speciesGradePercent: string
}

export type GasWorksheetSummary = GasAppraisedSummary | GasHistoricSummary | GasNonAppraisedSummary

// Rows can repeat; don't deduplicate by record ID.
export function rowsWithKeys<T>(items: readonly T[], identity: (item: T) => unknown[]) {
  const occurrences = new Map<string, number>()
  return items.map((item) => {
    const base = JSON.stringify(identity(item))
    const occurrence = occurrences.get(base) ?? 0
    occurrences.set(base, occurrence + 1)
    return { item, key: `${base}:${occurrence}` }
  })
}

export const ecasRowIdentity = (item: EcasInboxItem) => [
  item.ecasId,
  item.timberMark,
  item.licence,
  item.cuttingPermit,
]
export const gasRowIdentity = (item: GasAppraisalItem) => [
  item.key.type,
  item.key.worksheetId,
  item.timberMark,
]
