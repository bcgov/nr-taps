package ca.bc.gov.nrs.taps.read.oracle;

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

class EcasReadPredicateTest {
  @Test
  void noAcceptedGrantDenies() {
    assertThat(EcasReadPredicate.forUser(user(IdentityProvider.IDIR)).sql()).isEqualTo("(1 = 0)");
    var malformed = new TapsUser("synthetic", "Synthetic", null, IdentityProvider.IDIR, null,
        List.of(new RoleGrant(TapsRole.TAPS_VIEWER, null)));
    assertThat(EcasReadPredicate.forUser(malformed).sql()).isEqualTo("(1 = 0)");
  }

  @Test
  void clientScenarioRestrictionStaysWithItsOwnClientScope() {
    var predicate = EcasReadPredicate.forUser(user(IdentityProvider.IDIR,
        "TAPS_BCTS_FOREST_CLIENT-00000001", "TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ"));
    assertThat(predicate.sql()).isEqualTo(
        "(((record_scope.CLIENT_NUMBER = ?) AND record_scope.STATUS_CODE <> ?)"
            + " OR (record_scope.ADMIN_DISTRICT_CODE = ?))");
    assertThat(predicate.parameters()).containsExactly("00000001", "SCN", "DZZ");
  }

  @Test
  void viewerDraftRestrictionDoesNotBorrowAnAppraisersWiderRegion() {
    var predicate = EcasReadPredicate.forUser(user(IdentityProvider.IDIR,
        "TAPS_VIEWER_DISTRICT-DZZ", "TAPS_REGION_APPRAISER_REGION-CARIBOO"));
    assertThat(predicate.sql()).isEqualTo(
        "(((record_scope.ADMIN_DISTRICT_CODE = ?) AND record_scope.STATUS_CODE <> ?)"
            + " OR (record_scope.ROLLUP_REGION_CODE = ?))");
    assertThat(predicate.parameters()).containsExactly("DZZ", "DFT", "RCB");
  }

  @Test
  void allClientRolesHideScenariosWithoutHidingDrafts() {
    var predicate = EcasReadPredicate.forUser(user(IdentityProvider.BCEID_BUSINESS,
        "TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00000001",
        "TAPS_LICENSEE_SUBMITTER_FOREST_CLIENT-00000002"));
    assertThat(predicate.parameters()).containsExactly("00000001", "SCN", "00000002", "SCN");
    assertThat(predicate.parameters()).doesNotContain("DFT");
  }

  @Test
  void validProvincialReadRemainsProvincial() {
    var predicate = EcasReadPredicate.forUser(user(IdentityProvider.IDIR,
        "TAPS_VIEWER_DISTRICT-DZZ", "TAPS_ADMIN"));
    assertThat(predicate.sql()).isEqualTo("(1 = 1)");
    assertThat(predicate.parameters()).isEmpty();
  }

  @Test
  void scopeAndStatusAreBoundAndImmutable() {
    var predicate = EcasReadPredicate.forUser(user(IdentityProvider.IDIR,
        "TAPS_VIEWER_DISTRICT-DZZ"));
    assertThat(predicate.sql()).doesNotContain("DZZ", "DFT");
    assertThatThrownBy(() -> predicate.parameters().add("SCN"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private TapsUser user(IdentityProvider provider, String... roles) {
    return new TapsUser("synthetic", "Synthetic user", null, provider, null,
        Arrays.stream(roles)
            .map(role -> RoleGrant.accept(FamRoleName.parse(role), provider).orElseThrow())
            .toList());
  }
}
