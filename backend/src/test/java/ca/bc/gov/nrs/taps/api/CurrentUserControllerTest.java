package ca.bc.gov.nrs.taps.api;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {"management.endpoint.health.probes.enabled=true", "taps.auth.client-id=taps"})
@AutoConfigureMockMvc
class CurrentUserControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void healthIsPublicButApiRequiresAuthentication() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void currentUserReportsGrantsAndCapabilities() throws Exception {
    signIn(
        Map.of(
            "identity_provider", "bceidbusiness",
            "bceid_username", "acme-clerk",
            "display_name", "Acme Clerk",
            "bceid_business_name", "Acme Forest Products",
            "client_roles", List.of("TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00001018")));

    mvc.perform(get("/api/me").header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("BCEID\\ACME-CLERK"))
        .andExpect(jsonPath("$.displayName").value("Acme Clerk"))
        .andExpect(jsonPath("$.identityProvider").value("BCEID_BUSINESS"))
        .andExpect(jsonPath("$.businessName").value("Acme Forest Products"))
        .andExpect(jsonPath("$.roles[0].role").value("TAPS_LICENSEE_VIEWER"))
        .andExpect(jsonPath("$.roles[0].scopes[0].type").value("FOREST_CLIENT"))
        .andExpect(jsonPath("$.roles[0].scopes[0].value").value("00001018"))
        .andExpect(jsonPath("$.capabilities", contains("ECAS_SUBMISSION_VIEW", "GAS_CLIENT_REPORTS")))
        .andExpect(jsonPath("$.forestClients", contains("00001018")));
  }

  @Test
  void signedInUserWithoutTapsRolesStillGetsASession() throws Exception {
    signIn(Map.of("identity_provider", "azureidir", "idir_username", "jsmith"));

    mvc.perform(get("/api/me").header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("IDIR\\JSMITH"))
        .andExpect(jsonPath("$.roles").isEmpty())
        .andExpect(jsonPath("$.capabilities").isEmpty());
  }

  @Test
  void malformedIdentityClaimsReturnUnauthorizedInsteadOfAServerError() throws Exception {
    for (Map<String, Object> claims :
        List.of(
            Map.<String, Object>of(
                "identity_provider", List.of("azureidir"), "idir_username", "jsmith"),
            Map.<String, Object>of(
                "identity_provider", Map.of("name", "azureidir"), "idir_username", "jsmith"),
            Map.<String, Object>of(
                "identity_provider", "azureidir", "idir_username", Map.of("name", "jsmith")),
            Map.<String, Object>of(
                "identity_provider", "azureidir", "idir_user_guid", List.of("user-guid")))) {
      signIn(claims);

      mvc.perform(get("/api/me").header("Authorization", "Bearer token"))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void malformedRolesCannotPromoteASignedInUser() throws Exception {
    signIn(
        Map.of(
            "identity_provider", "azureidir",
            "idir_username", "jsmith",
            "client_roles", "TAPS_ADMIN",
            "resource_access", Map.of("taps", Map.of("roles", List.of("TAPS_ADMIN")))));

    mvc.perform(get("/api/me").header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles").isEmpty())
        .andExpect(jsonPath("$.capabilities").isEmpty());
  }

  @Test
  void otherApiPathsAndMethodsRequireAnExplicitFuturePolicy() throws Exception {
    signIn(
        Map.of(
            "identity_provider", "azureidir",
            "idir_username", "jsmith",
            "client_roles", List.of("TAPS_ADMIN")));

    mvc.perform(get("/api/future").header("Authorization", "Bearer token"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/me").header("Authorization", "Bearer token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void aTokenWithoutExpirationCannotCreateAnApplicationSession() throws Exception {
    when(jwtDecoder.decode("token"))
        .thenReturn(
            Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123")
                .claim("azp", "taps")
                .claim("typ", "Bearer")
                .claim("identity_provider", "azureidir")
                .claim("idir_username", "jsmith")
                .claim("client_roles", List.of("TAPS_ADMIN"))
                .build());

    mvc.perform(get("/api/me").header("Authorization", "Bearer token"))
        .andExpect(status().isUnauthorized());
  }

  private void signIn(Map<String, Object> claims) {
    Instant now = Instant.now();
    when(jwtDecoder.decode("token"))
        .thenReturn(
            Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123")
                .claim("azp", "taps")
                .claim("typ", "Bearer")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claims(values -> values.putAll(claims))
                .build());
  }
}
