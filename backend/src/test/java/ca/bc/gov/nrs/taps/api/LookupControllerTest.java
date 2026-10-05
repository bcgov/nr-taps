package ca.bc.gov.nrs.taps.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ca.bc.gov.nrs.taps.read.EffectiveCode;
import ca.bc.gov.nrs.taps.read.EcasStatusCode;
import ca.bc.gov.nrs.taps.read.oracle.OracleCodeLists;
import ca.bc.gov.nrs.taps.security.SecurityConfiguration;
import ca.bc.gov.nrs.taps.security.TapsAuthenticationConverter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LookupController.class, properties = {"taps.oracle.enabled=true", "taps.auth.client-id=taps"})
@Import({SecurityConfiguration.class, TapsAuthenticationConverter.class})
class LookupControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean OracleCodeLists codes;
  @MockitoBean ca.bc.gov.nrs.taps.read.oracle.OracleEcasOrganizations organizations;
  @MockitoBean JwtDecoder decoder;

  @Test
  void lookupsRequireTheirOwnCapabilityAndRejectWritesBeforeQuerying() throws Exception {
    for (String path : List.of("/api/ecas/lookups", "/api/gas/lookups")) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      signIn(List.of());
      mvc.perform(get(path).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
      signIn(List.of("TAPS_ADMIN"));
      mvc.perform(post(path).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
    }
    verifyNoInteractions(codes);
  }

  @Test
  void industryEcasGrantCannotReadGasLookup() throws Exception {
    signIn(List.of("TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00001018"), "bceidbusiness");
    when(codes.appraisalMethods()).thenReturn(List.of());
    when(codes.ecasAppraisalStatuses()).thenReturn(List.of());
    mvc.perform(get("/api/ecas/lookups").header("Authorization", "Bearer token")).andExpect(status().isOk());
    mvc.perform(get("/api/gas/lookups").header("Authorization", "Bearer token"))
        .andExpect(status().isForbidden());
    verify(codes, never()).rateAdjustmentTypes();
  }

  @Test
  void effectiveMetadataNullsAndOrderArePreservedWithoutTimezoneConversion() throws Exception {
    signIn(List.of("TAPS_ADMIN"));
    var code = new EffectiveCode("C", "Coast", LocalDateTime.of(2000, 1, 1, 12, 30),
        LocalDateTime.of(9999, 12, 31, 23, 59), null);
    when(codes.appraisalMethods()).thenReturn(List.of(code));
    when(codes.ecasAppraisalStatuses()).thenReturn(List.of());
    when(codes.rateAdjustmentTypes()).thenReturn(List.of(code));
    when(codes.ecasAppraisalStatuses()).thenReturn(List.of(new EcasStatusCode("OLD", "Historic status",
        code.effectiveDate(), code.expiryDate(), null, false)));
    mvc.perform(get("/api/ecas/lookups").header("Authorization", "Bearer token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.appraisalMethods[0].code").value("C"))
        .andExpect(jsonPath("$.appraisalStatuses[0].effectiveDate").value("2000-01-01T12:30:00"))
        .andExpect(jsonPath("$.appraisalStatuses[0].updateTimestamp").isEmpty())
        .andExpect(jsonPath("$.appraisalStatuses[0].active").value(false));
    mvc.perform(get("/api/gas/lookups").header("Authorization", "Bearer token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.rateAdjustmentTypes[0].code").value("C"));
  }

  @Test
  void failureReturnsSafeUnavailableAndCanRecover() throws Exception {
    signIn(List.of("TAPS_ADMIN"));
    when(codes.ecasAppraisalStatuses()).thenThrow(new DataAccessResourceFailureException("sensitive connection details"))
        .thenReturn(List.of());
    String body = mvc.perform(get("/api/ecas/lookups").header("Authorization", "Bearer token"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("READ_UNAVAILABLE"))
        .andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain("sensitive", "connection details");
    mvc.perform(get("/api/ecas/lookups").header("Authorization", "Bearer token")).andExpect(status().isOk());
  }

  private void signIn(List<String> roles) { signIn(roles, "azureidir"); }
  private void signIn(List<String> roles, String provider) {
    Instant now = Instant.now();
    when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "RS256")
        .subject("synthetic").issuedAt(now).expiresAt(now.plusSeconds(300)).claim("azp", "taps")
        .claim("typ", "Bearer").claim("identity_provider", provider).claim("idir_username", "synthetic")
        .claim("bceid_username", "synthetic").claim("client_roles", roles).build());
  }
}
