package ca.bc.gov.nrs.taps.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class TapsAuthenticationConverterTest {
  private final TapsAuthenticationConverter converter = new TapsAuthenticationConverter("taps");

  @Test
  void idirUserKeepsTheLegacyAuditNameAndScopedGrants() {
    TapsUser user =
        converter.toUser(
            idir(
                List.of(
                    "TAPS_REGION_APPRAISER_REGION-KOOTENAY_BOUNDARY",
                    "TAPS_VIEWER_DISTRICT-DCC",
                    "FAM:EXPIRES:2026-12-31:TAPS_VIEWER")));

    assertThat(user.userId()).isEqualTo("IDIR\\JSMITH");
    assertThat(user.legacyAccount()).isEqualTo("IDIR\\JSMITH");
    assertThat(user.ecasMyToDoAvailable()).isTrue();
    assertThat(user.displayName()).isEqualTo("Smith, Jane");
    assertThat(user.identityProvider()).isEqualTo(IdentityProvider.IDIR);
    assertThat(user.grants())
        .containsExactly(
            new RoleGrant(
                TapsRole.TAPS_REGION_APPRAISER,
                new FamRoleName.Scope("REGION", "KOOTENAY_BOUNDARY")),
            new RoleGrant(TapsRole.TAPS_VIEWER, new FamRoleName.Scope("DISTRICT", "DCC")));
    assertThat(user.can(TapsCapability.GAS_APPRAISAL_EDIT)).isTrue();
    assertThat(user.can(TapsCapability.GAS_REFERENCE_ADMIN)).isFalse();
  }

  @Test
  void businessBceidUserActsForEachGrantedClient() {
    Jwt token =
        token(
            Map.of(
                "identity_provider", "bceidbusiness",
                "bceid_username", "acme-clerk",
                "bceid_business_name", "Acme Forest Products",
                "client_roles",
                    List.of(
                        "TAPS_LICENSEE_SUBMITTER_FOREST_CLIENT-00001018",
                        "TAPS_LICENSEE_FOREST_CLIENT-00147603")));

    TapsUser user = converter.toUser(token);

    assertThat(user.userId()).isEqualTo("BCEID\\ACME-CLERK");
    assertThat(user.legacyAccount()).isEqualTo("BCEID\\ACME-CLERK");
    assertThat(user.businessName()).isEqualTo("Acme Forest Products");
    assertThat(user.forestClients()).containsExactly("00001018", "00147603");
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_SUBMIT, client("00001018"))).isTrue();
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_SUBMIT, client("00147603"))).isFalse();
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_VIEW, client("00099999"))).isFalse();
  }

  @Test
  void rolesForTheOtherSignInTypeGrantNothing() {
    // A BCeID account must not gain Ministry access, nor an IDIR account act as a licensee.
    assertThat(converter.toUser(bceid(List.of("TAPS_ADMIN"))).grants()).isEmpty();
    assertThat(converter.toUser(idir(List.of("TAPS_LICENSEE_FOREST_CLIENT-00001018"))).grants())
        .isEmpty();
  }

  @Test
  void bctsConsultantsUseBusinessBceidWithClientScopedAccess() {
    TapsUser user =
        converter.toUser(
            bceid(
                List.of(
                    "TAPS_BCTS_SUBMITTER_FOREST_CLIENT-00001018",
                    "TAPS_ADMIN",
                    "TAPS_REGION_APPRAISER_REGION-CARIBOO")));

    assertThat(user.grants())
        .containsExactly(
            new RoleGrant(
                TapsRole.TAPS_BCTS_SUBMITTER,
                new FamRoleName.Scope("FOREST_CLIENT", "00001018")));
    assertThat(user.can(TapsCapability.ECAS_BCTS_ENTRY, client("00001018"))).isTrue();
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_SUBMIT, client("00001018"))).isTrue();
    assertThat(user.can(TapsCapability.GAS_BCTS_RATE_UPDATE, client("00001018"))).isTrue();
    assertThat(user.can(TapsCapability.ECAS_SUBMISSION_SUBMIT, client("00147603"))).isFalse();
    assertThat(user.can(TapsCapability.GAS_BCTS_RATE_UPDATE, client("00147603"))).isFalse();
    assertThat(user.can(TapsCapability.ECAS_REGION_REVIEW)).isFalse();
    assertThat(user.can(TapsCapability.GAS_REFERENCE_ADMIN)).isFalse();
  }

  @Test
  void bctsEntryAndSubmitterRolesAcceptStaffAndConsultantsWithoutChangingTheirCapabilities() {
    for (IdentityProvider provider : IdentityProvider.values()) {
      List<String> roles = List.of("TAPS_BCTS_FOREST_CLIENT-00001018");
      TapsUser entry = converter.toUser(provider == IdentityProvider.IDIR ? idir(roles) : bceid(roles));

      assertThat(entry.can(TapsCapability.ECAS_BCTS_ENTRY, client("00001018"))).isTrue();
      assertThat(entry.can(TapsCapability.ECAS_BCTS_ENTRY, client("00147603"))).isFalse();
      assertThat(entry.can(TapsCapability.ECAS_SUBMISSION_SUBMIT)).isFalse();
      assertThat(entry.can(TapsCapability.GAS_BCTS_RATE_UPDATE)).isFalse();

      List<String> submittingRoles = List.of("TAPS_BCTS_SUBMITTER_FOREST_CLIENT-00001018");
      TapsUser submitter =
          converter.toUser(
              provider == IdentityProvider.IDIR ? idir(submittingRoles) : bceid(submittingRoles));
      assertThat(submitter.can(TapsCapability.ECAS_SUBMISSION_SUBMIT, client("00001018"))).isTrue();
      assertThat(submitter.can(TapsCapability.ECAS_SUBMISSION_SUBMIT, client("00147603"))).isFalse();
      assertThat(submitter.can(TapsCapability.ECAS_REGION_REVIEW)).isFalse();
    }
  }

  @Test
  void malformedOrMisspelledGrantsAreIgnored() {
    TapsUser user =
        converter.toUser(
            idir(
                List.of(
                    "TAPS_REGION_APPRAISER", // a scoped role without its scope
                    "TAPS_ADMIN_REGION-CARIBOO", // an unscoped role with a scope
                    "TAPS_REGION_APPRAISER_REGION-NORTHERN_INTERIOR", // retired region
                    "TAPS_REGION_APPRAISER_REGION-cariboo", // scope values are exact
                    "TAPS_VIEWER", // Ministry viewers need an org scope too
                    "TAPS_VIEWER_DISTRICT-DCC_REGION-CARIBOO", // compound scopes not enabled
                    "TAPS_DISTRICT_APPRAISER_REGION-CARIBOO", // wrong scope type
                    "TAPS_BCTS_FOREST_CLIENT-132184", // client number not 8 digits
                    "TAPS_BCTS_FOREST_CLIENT-00001018 ", // whitespace changes the grant
                    "taps_admin", // a different, lower-case role
                    "FAM_ADMIN", // FAM management is not TAPS application access
                    "ECAS_AGENT", // legacy service roles are not interactive grants
                    "TAPS_UNKNOWN")));

    assertThat(user.grants()).isEmpty();
    assertThat(user.capabilities()).isEmpty();
  }

  @Test
  void readsResourceAccessWhenClientRolesAreAbsent() {
    Jwt token =
        token(
            Map.of(
                "identity_provider", "azureidir",
                "idir_username", "jsmith",
                "resource_access",
                    Map.of(
                        "taps", Map.of("roles", List.of("TAPS_ADMIN")),
                        "other-app", Map.of("roles", List.of("TAPS_HEADQUARTERS")))));

    assertThat(converter.toUser(token).grants())
        .containsExactly(new RoleGrant(TapsRole.TAPS_ADMIN, null));
  }

  @Test
  void authoritiesAreCapabilitiesNotRoleNames() {
    List<String> authorities =
        converter.convert(idir(List.of("TAPS_DISTRICT_APPRAISER_DISTRICT-DCC"))).getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

    assertThat(authorities)
        .contains("ECAS_DISTRICT_REVIEW", "GAS_APPRAISAL_VIEW")
        .doesNotContain("TAPS_DISTRICT_APPRAISER", "TAPS_DISTRICT_APPRAISER_DISTRICT-DCC");
  }

  @Test
  void fallsBackToTheUpperCasedGuidWithoutAUsername() {
    Jwt token = token(Map.of("identity_provider", "idir", "idir_user_guid", "0a1b2c3d"));

    TapsUser user = converter.toUser(token);
    assertThat(user.userId()).isEqualTo("IDIR\\0A1B2C3D");
    assertThat(user.legacyAccount()).isNull();
  }

  @Test
  void onlyTheSignedProviderUsernameCanSupplyTheLegacyAssignmentAccount() {
    for (Object username : List.of(" ", 123, List.of("jsmith"))) {
      TapsUser user = converter.toUser(token(Map.of(
          "identity_provider", "azureidir", "idir_user_guid", "guid-only",
          "idir_username", username, "bceid_username", "another-provider",
          "display_name", "IDIR\\DISPLAY", "preferred_username", "IDIR\\PREFERRED",
          "client_roles", List.of("TAPS_HEADQUARTERS"))));
      assertThat(user.userId()).isEqualTo("IDIR\\GUID-ONLY");
      assertThat(user.legacyAccount()).isNull();
      assertThat(user.ecasMyToDoAvailable()).isFalse();
      assertThat(user.can(TapsCapability.ECAS_SUBMISSION_VIEW)).isTrue();
    }
    TapsUser user = converter.toUser(token(Map.of(
        "identity_provider", "bceidbusiness", "bceid_username", " acme-clerk ",
        "bceid_user_guid", "different-guid", "idir_username", "another-provider",
        "client_roles", List.of("TAPS_BCTS_FOREST_CLIENT-00001018"))));
    assertThat(user.legacyAccount()).isEqualTo("BCEID\\ACME-CLERK");
    assertThat(user.ecasMyToDoAvailable()).isTrue();
  }

  @Test
  void guidOnlyUsersCanUseQueuesThatDoNotRequireAssignment() {
    for (String role : List.of("TAPS_ADMIN", "TAPS_VIEWER_DISTRICT-DCC", "TAPS_REGION_CLERK_REGION-CARIBOO")) {
      TapsUser user = converter.toUser(token(Map.of("identity_provider", "idir",
          "idir_user_guid", "guid-only", "client_roles", List.of(role))));
      assertThat(user.legacyAccount()).isNull();
      assertThat(user.ecasMyToDoAvailable()).isTrue();
    }
    TapsUser withoutRoles = converter.toUser(token(Map.of("identity_provider", "idir", "idir_user_guid", "guid-only")));
    assertThat(withoutRoles.ecasMyToDoAvailable()).isFalse();
  }

  @Test
  void tokenWithoutAUserIdentityIsUnauthorized() {
    assertThatThrownBy(() -> converter.toUser(token(Map.of("identity_provider", "azureidir"))))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  @Test
  void otherClientsAndNonUserTokensCannotReachTheConverter() {
    for (Map<String, Object> claims :
        List.of(
            Map.<String, Object>of("azp", "other-app"),
            Map.<String, Object>of("azp", "TAPS"),
            Map.<String, Object>of("typ", "ID"),
            Map.<String, Object>of("identity_provider", "AzureIDIR"),
            Map.<String, Object>of("identity_provider", " azureidir "),
            Map.<String, Object>of("identity_provider", "bceidbasic"),
            Map.<String, Object>of("identity_provider", "bceidboth"),
            Map.<String, Object>of("identity_provider", "service-account"))) {
      assertThatThrownBy(() -> converter.toUser(token(withIdirClaims(claims))))
          .as(claims.toString())
          .isInstanceOf(InvalidBearerTokenException.class);
    }
  }

  @Test
  void nonStringIdentityClaimsAreNotCoercedIntoAUser() {
    for (Object malformed : List.of(123, List.of("jsmith"), Map.of("name", "jsmith"))) {
      assertThatThrownBy(
              () ->
                  converter.toUser(
                      token(Map.of("identity_provider", "azureidir", "idir_username", malformed))))
          .isInstanceOf(InvalidBearerTokenException.class);
    }
  }

  @Test
  void malformedOptionalClaimsAndRoleEntriesAreIgnored() {
    TapsUser user =
        converter.toUser(
            token(
                withIdirClaims(
                    Map.of(
                        "display_name", Map.of("name", "Jane"),
                        "email", 123,
                        "client_roles",
                            List.of(
                                "TAPS_VIEWER_DISTRICT-DCC",
                                "TAPS_VIEWER_DISTRICT-DCC",
                                "FAM:LABEL:TAPS_ADMIN:Admin",
                                "FAM:DESC:TAPS_ADMIN:Admin",
                                "FAM:EXPIRES:2026-12-31:TAPS_ADMIN",
                                "HAS_FOREST_CLIENT",
                                123,
                                Map.of("role", "TAPS_ADMIN"))))));

    assertThat(user.displayName()).isEqualTo("IDIR\\JSMITH");
    assertThat(user.email()).isNull();
    assertThat(user.grants())
        .containsExactly(
            new RoleGrant(TapsRole.TAPS_VIEWER, new FamRoleName.Scope("DISTRICT", "DCC")));
  }

  @Test
  void explicitCssRolesTakePrecedenceOverResourceAccessEvenWhenEmptyOrMalformed() {
    for (Object cssRoles : List.of(List.of(), "TAPS_ADMIN", Map.of("role", "TAPS_ADMIN"))) {
      TapsUser user =
          converter.toUser(
              token(
                  withIdirClaims(
                      Map.of(
                          "client_roles", cssRoles,
                          "resource_access",
                              Map.of("taps", Map.of("roles", List.of("TAPS_ADMIN")))))));

      assertThat(user.grants()).isEmpty();
    }
  }

  @Test
  void rolesFromOtherClientsRealmRolesAndMalformedResourceAccessGrantNothing() {
    for (Map<String, Object> roleClaims :
        List.of(
            Map.<String, Object>of(
                "resource_access", Map.of("other-app", Map.of("roles", List.of("TAPS_ADMIN")))),
            Map.<String, Object>of(
                "resource_access", Map.of("TAPS", Map.of("roles", List.of("TAPS_ADMIN")))),
            Map.<String, Object>of(
                "resource_access", Map.of("taps", Map.of("roles", "TAPS_ADMIN"))),
            Map.<String, Object>of("resource_access", List.of("TAPS_ADMIN")),
            Map.<String, Object>of("realm_access", Map.of("roles", List.of("TAPS_ADMIN"))))) {
      assertThat(converter.toUser(token(withIdirClaims(roleClaims))).grants()).isEmpty();
    }
  }

  private static Map<String, Object> withIdirClaims(Map<String, Object> claims) {
    Map<String, Object> values = new HashMap<>();
    values.put("identity_provider", "azureidir");
    values.put("idir_username", "jsmith");
    values.putAll(claims);
    return values;
  }

  private static RecordScope client(String clientNumber) {
    return new RecordScope(null, null, clientNumber);
  }

  private static Jwt idir(List<String> roles) {
    return token(
        Map.of(
            "identity_provider", "azureidir",
            "idir_username", "jsmith",
            "display_name", "Smith, Jane",
            "client_roles", roles));
  }

  private static Jwt bceid(List<String> roles) {
    return token(
        Map.of(
            "identity_provider", "bceidbusiness",
            "bceid_username", "acme-clerk",
            "client_roles", roles));
  }

  private static Jwt token(Map<String, Object> claims) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("user-123")
        .claim("azp", "taps")
        .claim("typ", "Bearer")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .claims(values -> values.putAll(claims))
        .build();
  }
}
