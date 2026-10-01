import type { RoleGrant } from '@/service/session-service'

// Mirrors TapsCapability in the backend. The backend decides what a user holds; these names only
// shape navigation, and every endpoint checks again.
export const Capability = {
  EcasSubmissionView: 'ECAS_SUBMISSION_VIEW',
  EcasSubmissionEdit: 'ECAS_SUBMISSION_EDIT',
  EcasSubmissionSubmit: 'ECAS_SUBMISSION_SUBMIT',
  EcasBctsEntry: 'ECAS_BCTS_ENTRY',
  EcasDistrictReview: 'ECAS_DISTRICT_REVIEW',
  EcasRegionReview: 'ECAS_REGION_REVIEW',
  EcasRiskAssessment: 'ECAS_RISK_ASSESSMENT',
  EcasStatusOverride: 'ECAS_STATUS_OVERRIDE',
  EcasReferenceAdmin: 'ECAS_REFERENCE_ADMIN',
  GasAppraisalView: 'GAS_APPRAISAL_VIEW',
  GasAppraisalEdit: 'GAS_APPRAISAL_EDIT',
  GasNonAppraisedEdit: 'GAS_NON_APPRAISED_EDIT',
  GasRateRuns: 'GAS_RATE_RUNS',
  GasReferenceAdmin: 'GAS_REFERENCE_ADMIN',
  GasBctsRateUpdate: 'GAS_BCTS_RATE_UPDATE',
  GasClientReports: 'GAS_CLIENT_REPORTS',
  GasMinistryReports: 'GAS_MINISTRY_REPORTS',
  GasHqReports: 'GAS_HQ_REPORTS',
  GasBranchReports: 'GAS_BRANCH_REPORTS',
} as const

export type Capability = (typeof Capability)[keyof typeof Capability]

const ROLE_LABELS: Record<string, string> = {
  TAPS_ADMIN: 'Administrator',
  TAPS_HEADQUARTERS: 'Headquarters',
  TAPS_VIEWER: 'Ministry viewer',
  TAPS_REGION_APPRAISER: 'Region appraiser',
  TAPS_REGION_CLERK: 'Region clerk',
  TAPS_DISTRICT_APPRAISER: 'District appraiser',
  TAPS_BCTS: 'BC Timber Sales',
  TAPS_BCTS_SUBMITTER: 'BC Timber Sales submitter',
  TAPS_LICENSEE: 'Licensee',
  TAPS_LICENSEE_SUBMITTER: 'Licensee submitter (RPF/RFT)',
  TAPS_LICENSEE_VIEWER: 'Licensee viewer',
}

const SCOPE_LABELS: Record<string, string> = {
  DISTRICT: 'district',
  REGION: 'region',
  FOREST_CLIENT: 'client',
}

export function describeGrant(grant: RoleGrant): string {
  const role = ROLE_LABELS[grant.role] ?? grant.role
  const scopes = grant.scopes.map((scope) => `${SCOPE_LABELS[scope.type]} ${scope.value}`)
  return scopes.length ? `${role} (${scopes.join(', ')})` : role
}
