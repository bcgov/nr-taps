package ca.bc.gov.nrs.taps.security;

import static ca.bc.gov.nrs.taps.security.TapsCapability.ECAS_BCTS_ENTRY;
import static ca.bc.gov.nrs.taps.security.TapsCapability.ECAS_DISTRICT_REVIEW;
import static ca.bc.gov.nrs.taps.security.TapsCapability.ECAS_REFERENCE_ADMIN;
import static ca.bc.gov.nrs.taps.security.TapsCapability.ECAS_REGION_REVIEW;
import static ca.bc.gov.nrs.taps.security.TapsCapability.ECAS_RISK_ASSESSMENT;
import static ca.bc.gov.nrs.taps.security.TapsCapability.ECAS_STATUS_OVERRIDE;
import static ca.bc.gov.nrs.taps.security.TapsCapability.ECAS_SUBMISSION_EDIT;
import static ca.bc.gov.nrs.taps.security.TapsCapability.ECAS_SUBMISSION_SUBMIT;
import static ca.bc.gov.nrs.taps.security.TapsCapability.ECAS_SUBMISSION_VIEW;
import static ca.bc.gov.nrs.taps.security.TapsCapability.GAS_APPRAISAL_EDIT;
import static ca.bc.gov.nrs.taps.security.TapsCapability.GAS_APPRAISAL_VIEW;
import static ca.bc.gov.nrs.taps.security.TapsCapability.GAS_BCTS_RATE_UPDATE;
import static ca.bc.gov.nrs.taps.security.TapsCapability.GAS_BRANCH_REPORTS;
import static ca.bc.gov.nrs.taps.security.TapsCapability.GAS_CLIENT_REPORTS;
import static ca.bc.gov.nrs.taps.security.TapsCapability.GAS_HQ_REPORTS;
import static ca.bc.gov.nrs.taps.security.TapsCapability.GAS_MINISTRY_REPORTS;
import static ca.bc.gov.nrs.taps.security.TapsCapability.GAS_NON_APPRAISED_EDIT;
import static ca.bc.gov.nrs.taps.security.TapsCapability.GAS_RATE_RUNS;
import static ca.bc.gov.nrs.taps.security.TapsCapability.GAS_REFERENCE_ADMIN;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The FAM roles TAPS accepts and what each grants. This table is the only place a role name is
 * interpreted.
 *
 * <p>The archived WebADE design bundles ECAS and GAS roles (for example the ECAS_REGION profile
 * includes GAS REGION, CLERICAL and DISTRICT). These proposed combined roles use those profiles as
 * a starting point, with conservative limits where the archive layers differ. Confirm the catalogue
 * with the business before provisioning it, because renaming a FAM role orphans every grant made
 * under it.
 *
 * <p>Each role in this initial catalogue has one fixed scope type. A grant must carry exactly that
 * scope: an unscoped grant of a scoped role, or a scoped grant of an unscoped role, is ignored.
 */
public enum TapsRole {
  // IDIR: Ministry staff
  // Legacy ECAS_ADMINISTRATOR bundled GAS BRANCH_ADMIN, but never GAS BCTS_UPDATE.
  // Province-wide admin/HQ scope is a proposal, not proven legacy ECAS provisioning.
  // Ordinary client submit is excluded; the special Administrator/BUP flow is deferred.
  TAPS_ADMIN(
      IdentityProvider.IDIR,
      null,
      EnumSet.of(
          ECAS_SUBMISSION_VIEW,
          ECAS_SUBMISSION_EDIT,
          ECAS_BCTS_ENTRY,
          ECAS_DISTRICT_REVIEW,
          ECAS_REGION_REVIEW,
          ECAS_RISK_ASSESSMENT,
          ECAS_STATUS_OVERRIDE,
          ECAS_REFERENCE_ADMIN,
          GAS_APPRAISAL_VIEW,
          GAS_APPRAISAL_EDIT,
          GAS_NON_APPRAISED_EDIT,
          GAS_RATE_RUNS,
          GAS_REFERENCE_ADMIN,
          GAS_CLIENT_REPORTS,
          GAS_MINISTRY_REPORTS,
          GAS_HQ_REPORTS,
          GAS_BRANCH_REPORTS)),
  TAPS_HEADQUARTERS(
      IdentityProvider.IDIR,
      null,
      EnumSet.of(ECAS_SUBMISSION_VIEW, GAS_CLIENT_REPORTS, GAS_HQ_REPORTS)),
  TAPS_VIEWER(
      IdentityProvider.IDIR,
      FamRoleName.DISTRICT,
      EnumSet.of(ECAS_SUBMISSION_VIEW, GAS_CLIENT_REPORTS)),
  TAPS_REGION_APPRAISER(
      IdentityProvider.IDIR,
      FamRoleName.REGION,
      EnumSet.of(
          ECAS_SUBMISSION_VIEW,
          ECAS_SUBMISSION_EDIT,
          ECAS_REGION_REVIEW,
          ECAS_RISK_ASSESSMENT,
          GAS_APPRAISAL_VIEW,
          GAS_APPRAISAL_EDIT,
          GAS_NON_APPRAISED_EDIT,
          GAS_CLIENT_REPORTS,
          GAS_MINISTRY_REPORTS)),
  TAPS_REGION_CLERK(
      IdentityProvider.IDIR,
      FamRoleName.REGION,
      EnumSet.of(
          ECAS_SUBMISSION_VIEW,
          GAS_APPRAISAL_VIEW,
          GAS_NON_APPRAISED_EDIT,
          GAS_CLIENT_REPORTS,
          GAS_MINISTRY_REPORTS)),
  TAPS_DISTRICT_APPRAISER(
      IdentityProvider.IDIR,
      FamRoleName.DISTRICT,
      EnumSet.of(
          ECAS_SUBMISSION_VIEW,
          ECAS_DISTRICT_REVIEW,
          ECAS_RISK_ASSESSMENT,
          GAS_APPRAISAL_VIEW,
          GAS_CLIENT_REPORTS,
          GAS_MINISTRY_REPORTS)),
  // BC Timber Sales staff use IDIR; authorized consultants use Business BCeID.
  TAPS_BCTS(
      Set.of(IdentityProvider.IDIR, IdentityProvider.BCEID_BUSINESS),
      FamRoleName.FOREST_CLIENT,
      EnumSet.of(ECAS_SUBMISSION_VIEW, ECAS_SUBMISSION_EDIT, ECAS_BCTS_ENTRY, GAS_CLIENT_REPORTS)),
  TAPS_BCTS_SUBMITTER(
      Set.of(IdentityProvider.IDIR, IdentityProvider.BCEID_BUSINESS),
      FamRoleName.FOREST_CLIENT,
      EnumSet.of(
          ECAS_SUBMISSION_VIEW,
          ECAS_SUBMISSION_EDIT,
          ECAS_SUBMISSION_SUBMIT,
          ECAS_BCTS_ENTRY,
          GAS_BCTS_RATE_UPDATE,
          GAS_CLIENT_REPORTS)),

  // Business BCeID: licensee staff, RPFs and RFTs, each for one forest client per grant
  TAPS_LICENSEE(
      IdentityProvider.BCEID_BUSINESS,
      FamRoleName.FOREST_CLIENT,
      EnumSet.of(ECAS_SUBMISSION_VIEW, ECAS_SUBMISSION_EDIT, GAS_CLIENT_REPORTS)),
  TAPS_LICENSEE_SUBMITTER(
      IdentityProvider.BCEID_BUSINESS,
      FamRoleName.FOREST_CLIENT,
      EnumSet.of(
          ECAS_SUBMISSION_VIEW, ECAS_SUBMISSION_EDIT, ECAS_SUBMISSION_SUBMIT, GAS_CLIENT_REPORTS)),
  TAPS_LICENSEE_VIEWER(
      IdentityProvider.BCEID_BUSINESS,
      FamRoleName.FOREST_CLIENT,
      EnumSet.of(ECAS_SUBMISSION_VIEW, GAS_CLIENT_REPORTS));

  private final Set<IdentityProvider> identityProviders;
  private final String scopeType;
  private final Set<TapsCapability> capabilities;

  TapsRole(
      IdentityProvider identityProvider, String scopeType, EnumSet<TapsCapability> capabilities) {
    this(Set.of(identityProvider), scopeType, capabilities);
  }

  TapsRole(
      Set<IdentityProvider> identityProviders,
      String scopeType,
      EnumSet<TapsCapability> capabilities) {
    this.identityProviders = Set.copyOf(identityProviders);
    this.scopeType = scopeType;
    this.capabilities = Set.copyOf(capabilities);
  }

  public static Optional<TapsRole> fromCode(String code) {
    return Arrays.stream(values()).filter(role -> role.name().equals(code)).findFirst();
  }

  public Set<IdentityProvider> identityProviders() {
    return identityProviders;
  }

  /** {@link FamRoleName#DISTRICT}, {@link FamRoleName#REGION}, {@link FamRoleName#FOREST_CLIENT} or null. */
  public String scopeType() {
    return scopeType;
  }

  public Set<TapsCapability> capabilities() {
    return capabilities;
  }
}
