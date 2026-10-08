package ca.bc.gov.nrs.taps.read.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class GasSearchPlanTest {
  // Statuses the GAS2 appraisal search leaves out.
  private static final List<String> LEGACY_EXCLUDED_STATUSES =
      List.of(
          "ACC", "APP", "CLR", "CPC", "DCL", "DFT", "EE", "FWD", "LFS", "GAS", "NAP", "NBS",
          "RCD", "RGN", "RTN", "SCN", "SEC", "SWI", "SUB");

  @Test
  void requiresGasCapabilityWithoutBorrowingWiderScopeFromOtherGrants() {
    var plan =
        GasSearchPlan.forUser(
            idir(
                "TAPS_HEADQUARTERS",
                "TAPS_VIEWER_DISTRICT-DYY",
                "TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ"),
            new GasAppraisal.Search(null, null, null));

    assertThat(plan.sql()).startsWith("((record_scope.ADMIN_DISTRICT_CODE = ?) AND ");
    assertThat(plan.sql()).doesNotContain("1 = 1", " OR ");
    assertThat(plan.parameters().getFirst()).isEqualTo("DZZ");
    assertThat(plan.parameters()).doesNotContain("DYY");
    assertThat(plan.parameters()).hasSize(20);
  }

  @Test
  void noGasGrantStillDeniesWhenRequestFiltersMatch() {
    var plan =
        GasSearchPlan.forUser(
            idir("TAPS_HEADQUARTERS", "TAPS_VIEWER_DISTRICT-DZZ"),
            new GasAppraisal.Search("a00001", "zz1234", 0));

    assertThat(plan.sql()).startsWith("((1 = 0) AND ");
    assertThat(plan.parameters()).doesNotContain("DZZ");
    assertThat(plan.parameters()).endsWith("A00001", "ZZ1234");
  }

  @Test
  void intersectsAllFiltersAfterParenthesizedAlternativeGrants() {
    var plan =
        GasSearchPlan.forUser(
            idir("TAPS_REGION_APPRAISER_REGION-CARIBOO", "TAPS_REGION_CLERK_REGION-SKEENA"),
            new GasAppraisal.Search("a00001", "zz1234", 0));

    assertThat(plan.sql())
        .startsWith(
            "((record_scope.ROLLUP_REGION_CODE = ? OR record_scope.ROLLUP_REGION_CODE = ?) AND ")
        .endsWith(" AND record_scope.LICENSE = ? AND record_scope.TIMBER_MARK = ?)");
    assertThat(plan.parameters()).startsWith("RCB", "RSK").endsWith("A00001", "ZZ1234");
    assertThat(plan.sql().chars().filter(character -> character == '?').count())
        .isEqualTo(plan.parameters().size());
  }

  @Test
  void preservesEverySourceStatusExclusionIncludingLicenseeFirstSubmission() {
    var plan = GasSearchPlan.forUser(idir("TAPS_ADMIN"), new GasAppraisal.Search(null, null, 0));

    assertThat(plan.parameters()).containsExactlyElementsOf(LEGACY_EXCLUDED_STATUSES);
    assertThat(plan.sql()).contains("record_scope.STATUS_CODE NOT IN (");
    assertThat(plan.sql()).doesNotContain("IS NULL", "COALESCE", "NVL");
    assertThat(plan.sql().chars().filter(character -> character == '?').count()).isEqualTo(19);
  }

  @Test
  void punctuationInExactMatchFiltersIsBoundRatherThanInterpolated() {
    var plan =
        GasSearchPlan.forUser(idir("TAPS_ADMIN"), new GasAppraisal.Search("a'--", "x_%'", 0));

    assertThat(plan.parameters()).endsWith("A'--", "X_%'");
    assertThat(plan.sql()).doesNotContain("A'--", "X_%'", "LIKE");
    assertThat(plan.sql()).endsWith("LICENSE = ? AND record_scope.TIMBER_MARK = ?)");
  }

  @Test
  void nullAndBlankFiltersHaveTheSameUnfilteredPlan() {
    var user = idir("TAPS_ADMIN");
    var absent = GasSearchPlan.forUser(user, new GasAppraisal.Search(null, null, null));
    var blank = GasSearchPlan.forUser(user, new GasAppraisal.Search("  ", "\t", 0));

    assertThat(blank.sql()).isEqualTo(absent.sql());
    assertThat(blank.parameters()).isEqualTo(absent.parameters());
    assertThat(blank.sql()).doesNotContain("record_scope.LICENSE", "record_scope.TIMBER_MARK");
    assertThat(blank.firstRow()).isEqualTo(1);
    assertThat(blank.lastRow()).isEqualTo(10);
  }

  @Test
  void eachOptionalFilterCanBeAppliedIndependently() {
    var user = idir("TAPS_ADMIN");
    var licence = GasSearchPlan.forUser(user, new GasAppraisal.Search("a00001", null, 0));
    var mark = GasSearchPlan.forUser(user, new GasAppraisal.Search(null, "zz1234", 0));

    assertThat(licence.sql()).endsWith(" AND record_scope.LICENSE = ?)");
    assertThat(licence.sql()).doesNotContain("record_scope.TIMBER_MARK");
    assertThat(licence.parameters()).endsWith("A00001");
    assertThat(mark.sql()).endsWith(" AND record_scope.TIMBER_MARK = ?)");
    assertThat(mark.sql()).doesNotContain("record_scope.LICENSE");
    assertThat(mark.parameters()).endsWith("ZZ1234");
  }

  @Test
  void singleImmutablePredicateAndBindingsRemainTheSameAcrossPages() {
    var user = idir("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ");
    var first = GasSearchPlan.forUser(user, new GasAppraisal.Search("a00001", "zz1234", 0));
    var next = GasSearchPlan.forUser(user, new GasAppraisal.Search("a00001", "zz1234", 1));

    assertThat(next.sql()).isEqualTo(first.sql());
    assertThat(next.parameters()).isEqualTo(first.parameters());
    assertThat(next.firstRow()).isEqualTo(11);
    assertThat(next.lastRow()).isEqualTo(20);
    assertThatThrownBy(() -> next.parameters().add("OTHER"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(next.sql()).doesNotContain("SELECT", "ORDER BY", "ROWNUM");
  }

  @Test
  void largestAcceptedPageCannotOverflowItsInclusiveBounds() {
    var plan =
        GasSearchPlan.forUser(
            idir("TAPS_ADMIN"), new GasAppraisal.Search(null, null, Integer.MAX_VALUE));

    assertThat(plan.firstRow()).isEqualTo(21_474_836_471L);
    assertThat(plan.lastRow()).isEqualTo(21_474_836_480L);
    assertThat(plan.lastRow() - plan.firstRow() + 1).isEqualTo(GasAppraisal.PAGE_SIZE);
  }

  private TapsUser idir(String... roles) {
    return new TapsUser(
        "synthetic",
        "Synthetic user",
        null,
        IdentityProvider.IDIR,
        null,
        Arrays.stream(roles)
            .map(role -> RoleGrant.accept(FamRoleName.parse(role), IdentityProvider.IDIR).orElseThrow())
            .toList());
  }
}
