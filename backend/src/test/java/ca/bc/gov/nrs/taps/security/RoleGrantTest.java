package ca.bc.gov.nrs.taps.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoleGrantTest {
  private static final RecordScope DCC_IN_CARIBOO = new RecordScope("DCC", "RCB", "00001018");
  private static final RecordScope DKA_IN_THOMPSON = new RecordScope("DKA", "RTO", "00001018");

  @Test
  void regionGrantReachesItsDistrictsOnly() {
    RoleGrant cariboo = grant("TAPS_REGION_APPRAISER_REGION-CARIBOO");

    assertThat(cariboo.covers(DCC_IN_CARIBOO)).isTrue();
    assertThat(cariboo.covers(DKA_IN_THOMPSON)).isFalse();
  }

  @Test
  void districtGrantReachesItsDistrictOnly() {
    RoleGrant dcc = grant("TAPS_DISTRICT_APPRAISER_DISTRICT-DCC");

    assertThat(dcc.covers(DCC_IN_CARIBOO)).isTrue();
    assertThat(dcc.covers(DKA_IN_THOMPSON)).isFalse();
  }

  @Test
  void missingRecordScopeMatchesNoScopedGrant() {
    RecordScope unknown = new RecordScope(null, null, null);

    assertThat(grant("TAPS_DISTRICT_APPRAISER_DISTRICT-DCC").covers(unknown)).isFalse();
    assertThat(grant("TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00001018").covers(unknown)).isFalse();
    assertThat(grant("TAPS_VIEWER_DISTRICT-DCC").covers(unknown)).isFalse();
    assertThat(grant("TAPS_ADMIN").covers(unknown)).isTrue();
    assertThat(grant("TAPS_ADMIN").covers(null)).isFalse();
  }

  @Test
  void capabilityAndScopeMustComeFromTheSameGrant() {
    TapsUser user =
        new TapsUser(
            "IDIR\\JSMITH",
            "Jane Smith",
            null,
            IdentityProvider.IDIR,
            null,
            List.of(
                grant("TAPS_VIEWER_DISTRICT-DKA"), grant("TAPS_REGION_APPRAISER_REGION-CARIBOO")));

    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_VIEW, DKA_IN_THOMPSON)).isTrue();
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_EDIT, DKA_IN_THOMPSON)).isFalse();
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_EDIT, DCC_IN_CARIBOO)).isTrue();
  }

  @Test
  void everyRegionUsesADistinctCode() {
    assertThat(FamRegion.values()).hasSize(8);
    for (FamRegion region : FamRegion.values()) {
      assertThat(FamRegion.fromOrgUnitCode(region.orgUnitCode())).contains(region);
    }
  }

  @Test
  void aDistrictViewerCannotBorrowAClerksEditGrantFromAnotherRegion() {
    TapsUser user =
        new TapsUser(
            "IDIR\\JSMITH",
            "Jane Smith",
            null,
            IdentityProvider.IDIR,
            null,
            List.of(grant("TAPS_VIEWER_DISTRICT-DKA"), grant("TAPS_REGION_CLERK_REGION-CARIBOO")));

    assertThat(user.can(TapsCapability.GAS_NON_APPRAISED_EDIT, DCC_IN_CARIBOO)).isTrue();
    assertThat(user.can(TapsCapability.GAS_NON_APPRAISED_EDIT, DKA_IN_THOMPSON)).isFalse();
    assertThat(user.can(TapsCapability.GAS_APPRAISAL_EDIT, DCC_IN_CARIBOO)).isFalse();
    assertThat(user.can(TapsCapability.GAS_RATE_RUNS)).isFalse();
  }

  @Test
  void broadHeadquartersViewDoesNotExpandARegionalEditGrant() {
    TapsUser user =
        new TapsUser(
            "IDIR\\JSMITH",
            "Jane Smith",
            null,
            IdentityProvider.IDIR,
            null,
            List.of(grant("TAPS_HEADQUARTERS"), grant("TAPS_REGION_APPRAISER_REGION-CARIBOO")));

    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_VIEW, DKA_IN_THOMPSON)).isTrue();
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_EDIT, DKA_IN_THOMPSON)).isFalse();
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_EDIT, DCC_IN_CARIBOO)).isTrue();
    assertThat(TapsRole.TAPS_HEADQUARTERS.capabilities())
        .doesNotContain(
            TapsCapability.GAS_APPRAISAL_VIEW,
            TapsCapability.GAS_MINISTRY_REPORTS,
            TapsCapability.ECAS_RISK_ASSESSMENT);
  }

  @Test
  void clientEditAndSubmitGrantsStaySeparateForTheSameUser() {
    TapsUser user =
        new TapsUser(
            "BCEID\\ACME",
            "Acme Clerk",
            null,
            IdentityProvider.BCEID_BUSINESS,
            "Acme",
            List.of(
                grant("TAPS_LICENSEE_SUBMITTER_FOREST_CLIENT-00001018"),
                grant("TAPS_LICENSEE_FOREST_CLIENT-00147603")));

    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_EDIT, new RecordScope(null, null, "00147603")))
        .isTrue();
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_SUBMIT, new RecordScope(null, null, "00147603")))
        .isFalse();
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_SUBMIT, DCC_IN_CARIBOO)).isTrue();
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_VIEW, new RecordScope(null, null, "00099999")))
        .isFalse();
  }

  @Test
  void roleCatalogueDoesNotGiveAdministrativeUsersClientSubmissionOrBctsRateOverride() {
    assertThat(TapsRole.TAPS_ADMIN.capabilities())
        .doesNotContain(TapsCapability.ECAS_SUBMISSION_SUBMIT, TapsCapability.GAS_BCTS_RATE_UPDATE);
    assertThat(TapsRole.TAPS_BCTS.capabilities()).doesNotContain(TapsCapability.GAS_BCTS_RATE_UPDATE);
    assertThat(TapsRole.TAPS_BCTS_SUBMITTER.capabilities())
        .contains(TapsCapability.GAS_BCTS_RATE_UPDATE);
    assertThat(TapsRole.TAPS_REGION_APPRAISER.capabilities())
        .doesNotContain(TapsCapability.ECAS_STATUS_OVERRIDE);
  }

  private static RoleGrant grant(String roleName) {
    TapsRole role = TapsRole.fromCode(FamRoleName.parse(roleName).baseRole()).orElseThrow();
    IdentityProvider provider =
        role.identityProviders().contains(IdentityProvider.IDIR)
            ? IdentityProvider.IDIR
            : IdentityProvider.BCEID_BUSINESS;
    return RoleGrant.accept(FamRoleName.parse(roleName), provider).orElseThrow();
  }
}
