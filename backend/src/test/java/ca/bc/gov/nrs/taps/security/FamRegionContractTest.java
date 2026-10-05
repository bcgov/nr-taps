package ca.bc.gov.nrs.taps.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Grant names follow nr-fam's region role naming. */
class FamRegionContractTest {
  @Test
  void supportedNamesMatchTheFamRegionCatalogue() {
    assertThat(Arrays.stream(FamRegion.values()).map(Enum::name))
        .containsExactlyInAnyOrder("NORTHEAST", "OMINECA", "SKEENA", "CARIBOO",
            "KOOTENAY_BOUNDARY", "THOMPSON_OKANAGAN", "SOUTH_COAST", "WEST_COAST");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NORTHEAST", "OMINECA", "SKEENA", "CARIBOO",
      "KOOTENAY_BOUNDARY", "THOMPSON_OKANAGAN", "SOUTH_COAST", "WEST_COAST"})
  void consumesFamRegionSuffixWithoutRequiringDistrictGrants(String regionCode) {
    var grant = RoleGrant.accept(
        FamRoleName.parse("TAPS_REGION_APPRAISER_REGION-" + regionCode), IdentityProvider.IDIR)
        .orElseThrow();
    assertThat(grant.scope()).isEqualTo(new FamRoleName.Scope(FamRoleName.REGION, regionCode));
    assertThat(grant.covers(new RecordScope("DZZ",
        FamRegion.fromScopeValue(regionCode).orElseThrow().orgUnitCode(), null))).isTrue();
    assertThat(grant.covers(new RecordScope("DZZ", null, null))).isFalse();
  }

  @Test
  void markerAndUnscopedCompositeDoNotGrantRegionalAccess() {
    assertThat(RoleGrant.accept(FamRoleName.parse("HAS_REGION_ROLE"), IdentityProvider.IDIR))
        .isEmpty();
    assertThat(RoleGrant.accept(FamRoleName.parse("TAPS_REGION_APPRAISER"), IdentityProvider.IDIR))
        .isEmpty();
  }
}
