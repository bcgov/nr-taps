package ca.bc.gov.nrs.taps.security;

/** Capabilities also require record-scope and workflow-state checks. */
public enum TapsCapability {
  ECAS_SUBMISSION_VIEW,
  ECAS_SUBMISSION_EDIT,
  ECAS_SUBMISSION_SUBMIT,
  ECAS_BCTS_ENTRY,
  ECAS_DISTRICT_REVIEW,
  ECAS_REGION_REVIEW,
  ECAS_RISK_ASSESSMENT,
  ECAS_STATUS_OVERRIDE,
  ECAS_REFERENCE_ADMIN,

  GAS_APPRAISAL_VIEW,
  GAS_APPRAISAL_EDIT,
  GAS_NON_APPRAISED_EDIT,
  /** Quarterly updates, NARU, NARC and mass reappraisals. */
  GAS_RATE_RUNS,
  GAS_REFERENCE_ADMIN,
  /** Override using the sale's upset and bonus. */
  GAS_BCTS_RATE_UPDATE,
  GAS_CLIENT_REPORTS,
  GAS_MINISTRY_REPORTS,
  GAS_HQ_REPORTS,
  GAS_BRANCH_REPORTS;

  public String authority() {
    return name();
  }
}
