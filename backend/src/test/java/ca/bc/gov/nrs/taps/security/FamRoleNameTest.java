package ca.bc.gov.nrs.taps.security;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.taps.security.FamRoleName.Scope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Cases follow CssRoleNamingTest in nr-fam, the grammar's reference implementation.
class FamRoleNameTest {
  @ParameterizedTest
  @ValueSource(strings = {"TAPS_ADMIN", "SOME-ROLE"})
  void unscopedNamesAreTheirOwnBaseRole(String roleName) {
    FamRoleName parsed = FamRoleName.parse(roleName);

    assertThat(parsed.baseRole()).isEqualTo(roleName);
    assertThat(parsed.scopes()).isEmpty();
  }

  @Test
  void forestClientScopeKeepsItsTwoWordType() {
    FamRoleName parsed = FamRoleName.parse("TAPS_LICENSEE_FOREST_CLIENT-00001018");

    assertThat(parsed.baseRole()).isEqualTo("TAPS_LICENSEE");
    assertThat(parsed.scopes()).containsExactly(new Scope("FOREST_CLIENT", "00001018"));
    assertThat(parsed.scope(FamRoleName.FOREST_CLIENT)).contains("00001018");
  }

  @Test
  void regionValueKeepsItsUnderscore() {
    FamRoleName parsed = FamRoleName.parse("TAPS_REGION_APPRAISER_REGION-KOOTENAY_BOUNDARY");

    assertThat(parsed.baseRole()).isEqualTo("TAPS_REGION_APPRAISER");
    assertThat(parsed.scopes()).containsExactly(new Scope("REGION", "KOOTENAY_BOUNDARY"));
  }

  @Test
  void compoundScopesReadBackInWrittenOrder() {
    FamRoleName parsed =
        FamRoleName.parse("FOM_SUBMITTER_DISTRICT-DCC_REGION-CARIBOO_FOREST_CLIENT-00001012");

    assertThat(parsed.baseRole()).isEqualTo("FOM_SUBMITTER");
    assertThat(parsed.scopes())
        .containsExactly(
            new Scope("DISTRICT", "DCC"),
            new Scope("REGION", "CARIBOO"),
            new Scope("FOREST_CLIENT", "00001012"));
  }

  @Test
  void hyphenatedBaseRoleSurvivesScopeParsing() {
    FamRoleName parsed = FamRoleName.parse("SOME-ROLE_DISTRICT-DCC");

    assertThat(parsed.baseRole()).isEqualTo("SOME-ROLE");
    assertThat(parsed.scopes()).containsExactly(new Scope("DISTRICT", "DCC"));
  }

  @Test
  void famBookkeepingRolesAreSidecars() {
    assertThat(FamRoleName.isSidecar("FAM:EXPIRES:2026-09-30:TAPS_LICENSEE_FOREST_CLIENT-00001018"))
        .isTrue();
    assertThat(FamRoleName.isSidecar("TAPS_LICENSEE_FOREST_CLIENT-00001018")).isFalse();
    assertThat(FamRoleName.isSidecar(null)).isFalse();
  }
}
