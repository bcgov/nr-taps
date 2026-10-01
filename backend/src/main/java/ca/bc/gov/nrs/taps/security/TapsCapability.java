package ca.bc.gov.nrs.taps.security;

/**
 * What a TAPS user may do, independent of which FAM role granted it. Screens and endpoints check
 * capabilities, never role names, so the role catalogue can be collapsed or split later by editing
 * {@link TapsRole} alone.
 *
 * <p>Until ECAS and GAS are merged, capabilities follow the legacy functional areas of each app.
 * Most legacy rights also depend on the submission or worksheet status; those checks belong to the
 * business endpoints, not here.
 */
public enum TapsCapability {
  // ECAS: appraisal data submissions (ADS)
  /** Inbox, search, view and print submissions, audit history and comments, ADS reports. */
  ECAS_SUBMISSION_VIEW,
  /** Create and edit draft or clarification submissions: data entry, XML upload, attachments. */
  ECAS_SUBMISSION_EDIT,
  /** Submit to and recall from the Ministry; legacy ECAS_RPF and ECAS_BCTS_SUBMIT only. */
  ECAS_SUBMISSION_SUBMIT,
  /** BCTS timber-sale data entry; second-pass status transitions need separate workflow guards. */
  ECAS_BCTS_ENTRY,
  /** District review: receive, send to region, send with issue, district clarify and recall. */
  ECAS_DISTRICT_REVIEW,
  /** Region review: verify, region clarify and recall, return to district, scenarios. */
  ECAS_REGION_REVIEW,
  /** Edit PHARM risk assessments (coast and interior); Ministry only. */
  ECAS_RISK_ASSESSMENT,
  /** Override a submission's status, including Entered in Error; help desk only. */
  ECAS_STATUS_OVERRIDE,
  /** ECAS reference and system tables (legacy screens 80 to 90) and templates. */
  ECAS_REFERENCE_ADMIN,

  // GAS: appraisal worksheets and stumpage rates
  /** Appraisal search, appraised, historic and non-appraised worksheets, audit, admin tables. */
  GAS_APPRAISAL_VIEW,
  /** Edit and verify appraised and historic worksheets, issue rates, confirm, deliver notices. */
  GAS_APPRAISAL_EDIT,
  /** Create, edit and determine non-appraised worksheets and their rates. */
  GAS_NON_APPRAISED_EDIT,
  /** Quarterly update, NARU, NARC and mass reappraisal runs, and generated appraisal rates. */
  GAS_RATE_RUNS,
  /** The rate-input tables behind every calculation, including the CSV batch upload. */
  GAS_REFERENCE_ADMIN,
  /** Override a BCTS stumpage rate from the sale's upset and bonus. */
  GAS_BCTS_RATE_UPDATE,
  /** Rate and worksheet details search and its three reports; clients see their own only. */
  GAS_CLIENT_REPORTS,
  /** The ministry report set legacy GAS gave every district user. */
  GAS_MINISTRY_REPORTS,
  /** Headquarters reports: NARC rate calculation, stone data, non-appraised rate cost. */
  GAS_HQ_REPORTS,
  /** Branch run and adjustment reports. */
  GAS_BRANCH_REPORTS;

  /** The Spring Security authority string for {@code hasAuthority} checks. */
  public String authority() {
    return name();
  }
}
