package ca.bc.gov.nrs.taps.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ca.bc.gov.nrs.taps.read.EcasAudit;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasAudit;
import ca.bc.gov.nrs.taps.security.SecurityConfiguration;
import ca.bc.gov.nrs.taps.security.TapsAuthenticationConverter;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuditController.class, properties = {"taps.oracle.enabled=true", "taps.auth.client-id=taps"})
@Import({SecurityConfiguration.class, TapsAuthenticationConverter.class})
class AuditControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean OracleEcasAudit audit;
  @MockitoBean JwtDecoder decoder;

  @Test
  void anonymousNoGrantAndWritesNeverReachAuditReaders() throws Exception {
    for (String path : List.of("/api/ecas/audit/1001", "/api/ecas/audit/1001/events/60001")) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      signIn(List.of());
      mvc.perform(get(path).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
      signIn(List.of("TAPS_ADMIN"));
      mvc.perform(post(path).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
    }
    verifyNoInteractions(audit);
  }

  @Test
  void invalidIdsAndPageProduce400BeforeTheReader() throws Exception {
    signIn(List.of("TAPS_ADMIN"));
    for (String path : List.of("/api/ecas/audit/nope", "/api/ecas/audit/1001?page=-1",
        "/api/ecas/audit/1001/events/0", "/api/ecas/audit/1001/events/60001?page=bad")) {
      mvc.perform(get(path).header("Authorization", "Bearer token"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    verifyNoInteractions(audit);
  }

  @Test
  void parentAndEventArePassedWithTheSameAuthenticatedGrant() throws Exception {
    signIn(List.of("TAPS_REGION_APPRAISER_REGION-CARIBOO"));
    when(audit.history(any(), eq("1001"), eq(1))).thenReturn(Optional.of(new EcasAudit.HistoryPage("1001", List.of(), 150, 1)));
    when(audit.details(any(), eq("1001"), eq("60001"), eq(2))).thenReturn(Optional.empty());
    mvc.perform(get("/api/ecas/audit/001001?page=1").header("Authorization", "Bearer token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(150));
    mvc.perform(get("/api/ecas/audit/1001/events/60001?page=2").header("Authorization", "Bearer token"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    var user = ArgumentCaptor.forClass(TapsUser.class);
    verify(audit).details(user.capture(), eq("1001"), eq("60001"), eq(2));
    assertThat(user.getValue().grants().getFirst().scope().value()).isEqualTo("CARIBOO");
  }

  @Test
  void databaseErrorsNeverExposeStoredCommentsOrConnectionDetails() throws Exception {
    signIn(List.of("TAPS_ADMIN"));
    when(audit.history(any(), anyString(), anyInt())).thenThrow(new DataAccessResourceFailureException("private audit comment or connection"));
    String body = mvc.perform(get("/api/ecas/audit/1001").header("Authorization", "Bearer token"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("READ_UNAVAILABLE"))
        .andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain("private", "comment", "connection");
  }

  private void signIn(List<String> roles) {
    Instant now = Instant.now();
    when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "RS256")
        .subject("synthetic").issuedAt(now).expiresAt(now.plusSeconds(300)).claim("azp", "taps")
        .claim("typ", "Bearer").claim("identity_provider", "azureidir")
        .claim("idir_username", "synthetic").claim("client_roles", roles).build());
  }
}
