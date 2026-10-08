package ca.bc.gov.nrs.taps.read.oracle;

import static ca.bc.gov.nrs.taps.security.TapsCapability.ECAS_SUBMISSION_EDIT;
import static ca.bc.gov.nrs.taps.security.TapsCapability.ECAS_SUBMISSION_VIEW;
import static ca.bc.gov.nrs.taps.security.TapsCapability.GAS_APPRAISAL_VIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsRole;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadScopePredicateTest {
  @Test
  void noGrantsOrNoRequiredCapabilityDenyAllRows() {
    assertThat(ReadScopePredicate.forCapability(idir(), GAS_APPRAISAL_VIEW).sql())
        .isEqualTo("(1 = 0)");
    var viewer = ReadScopePredicate.forCapability(idir("TAPS_VIEWER_DISTRICT-DZZ"), GAS_APPRAISAL_VIEW);
    assertThat(viewer.sql()).isEqualTo("(1 = 0)");
    assertThat(viewer.parameters()).isEmpty();
  }

  @Test
  void widerViewerScopeCannotBeBorrowedByAnAppraiserGrant() {
    var user = idir("TAPS_VIEWER_DISTRICT-DZZ", "TAPS_REGION_APPRAISER_REGION-CARIBOO");
    var predicate = ReadScopePredicate.forCapability(user, ECAS_SUBMISSION_EDIT);

    assertThat(predicate.sql()).isEqualTo("(record_scope.ROLLUP_REGION_CODE = ?)");
    assertThat(predicate.parameters()).containsExactly("RCB");
    assertThat(predicate.sql()).doesNotContain("DZZ", "RCB", "CLIENT_NUMBER");
  }

  @Test
  void provincialGrantWithoutTheCapabilityCannotWidenAnotherGrant() {
    var user = idir("TAPS_HEADQUARTERS", "TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ");
    var predicate = ReadScopePredicate.forCapability(user, GAS_APPRAISAL_VIEW);

    assertThat(predicate.sql()).isEqualTo("(record_scope.ADMIN_DISTRICT_CODE = ?)");
    assertThat(predicate.parameters()).containsExactly("DZZ");
  }

  @Test
  void matchingGrantsAreOrAlternativesInsideOneFilterIntersection() {
    var user =
        idir(
            "TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ",
            "TAPS_REGION_APPRAISER_REGION-CARIBOO",
            "TAPS_REGION_CLERK_REGION-SKEENA");
    var predicate = ReadScopePredicate.forCapability(user, GAS_APPRAISAL_VIEW);

    assertThat(predicate.sql())
        .isEqualTo(
            "(record_scope.ADMIN_DISTRICT_CODE = ? OR record_scope.ROLLUP_REGION_CODE = ?"
                + " OR record_scope.ROLLUP_REGION_CODE = ?)");
    assertThat(predicate.parameters()).containsExactly("DZZ", "RCB", "RSK");
    assertThat(predicate.sql() + " AND LICENSE = ?")
        .endsWith("ROLLUP_REGION_CODE = ?) AND LICENSE = ?");
  }

  @Test
  void clientGrantsPreserveLeadingZeroesAndCannotOpenGasAppraisals() {
    var user = bceid("TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00000001", "TAPS_LICENSEE_FOREST_CLIENT-00000002");
    var predicate = ReadScopePredicate.forCapability(user, ECAS_SUBMISSION_VIEW);

    assertThat(predicate.sql())
        .isEqualTo("(record_scope.CLIENT_NUMBER = ? OR record_scope.CLIENT_NUMBER = ?)");
    assertThat(predicate.parameters()).containsExactly("00000001", "00000002");
    assertThat(ReadScopePredicate.forCapability(user, GAS_APPRAISAL_VIEW).sql()).isEqualTo("(1 = 0)");
  }

  @Test
  void existingProvincialCapabilityDoesNotNeedScopeParameters() {
    var predicate =
        ReadScopePredicate.forCapability(
            idir("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ", "TAPS_ADMIN"), GAS_APPRAISAL_VIEW);
    assertThat(predicate.sql()).isEqualTo("(1 = 1)");
    assertThat(predicate.parameters()).isEmpty();
  }

  @Test
  void malformedScopedGrantAndWrongProviderCannotBecomeProvincial() {
    var missingScope = new RoleGrant(TapsRole.TAPS_DISTRICT_APPRAISER, null);
    var injectedScope =
        new RoleGrant(
            TapsRole.TAPS_DISTRICT_APPRAISER,
            new FamRoleName.Scope(FamRoleName.DISTRICT, "DZZ' OR 1=1 --"));
    var user = new TapsUser("synthetic", "Synthetic user", null, IdentityProvider.IDIR, null, List.of(missingScope, injectedScope));
    assertThat(ReadScopePredicate.forCapability(user, GAS_APPRAISAL_VIEW).sql()).isEqualTo("(1 = 0)");
    var wrongProvider = new TapsUser("synthetic", "Synthetic user", null, IdentityProvider.BCEID_BUSINESS, null, List.of(new RoleGrant(TapsRole.TAPS_ADMIN, null)));
    assertThat(ReadScopePredicate.forCapability(wrongProvider, GAS_APPRAISAL_VIEW).sql()).isEqualTo("(1 = 0)");
  }

  @Test
  void scopeValuesAreBoundAndCannotBeChangedAfterCompilation() {
    var predicate = ReadScopePredicate.forCapability(idir("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ"), GAS_APPRAISAL_VIEW);
    assertThat(predicate.sql()).doesNotContain("DZZ");
    assertThatThrownBy(() -> predicate.parameters().add("DYY")).isInstanceOf(UnsupportedOperationException.class);
  }

  private TapsUser idir(String... roles) {
    return user(IdentityProvider.IDIR, roles);
  }

  private TapsUser bceid(String... roles) {
    return user(IdentityProvider.BCEID_BUSINESS, roles);
  }

  private TapsUser user(IdentityProvider provider, String... roles) {
    return new TapsUser(
        "synthetic", "Synthetic user", null, provider, null,
        Arrays.stream(roles)
            .map(role -> RoleGrant.accept(FamRoleName.parse(role), provider).orElseThrow())
            .toList());
  }
}
