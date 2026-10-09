package ca.bc.gov.nrs.taps.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.read.GasAudit;
import ca.bc.gov.nrs.taps.read.oracle.OracleGasAudit;
import ca.bc.gov.nrs.taps.security.SecurityConfiguration;
import ca.bc.gov.nrs.taps.security.TapsAuthenticationConverter;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = GasAuditController.class, properties = "taps.auth.client-id=taps")
@ActiveProfiles("oracle")
@Import({SecurityConfiguration.class, TapsAuthenticationConverter.class})
class GasAuditControllerTest {
  private static final String URL = "/api/gas/worksheets/NON_APPRAISED/123/history";
  @Autowired MockMvc mvc;
  @MockitoBean OracleGasAudit audit;
  @MockitoBean JwtDecoder decoder;

  @Test
  void anonymousNonGasRolesAndWritesNeverReachReader() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    signIn("TAPS_HEADQUARTERS");
    mvc.perform(get(URL).header("Authorization", "Bearer token"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    signIn("TAPS_ADMIN");
    mvc.perform(post(URL).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
    for (String family : List.of("HISTORIC", "UNKNOWN")) {
      mvc.perform(get("/api/gas/worksheets/" + family + "/123/history").header("Authorization", "Bearer token"))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(audit);
  }

  @Test
  void invalidIdsAndPagesStopBeforeTheReader() throws Exception {
    signIn("TAPS_ADMIN");
    for (String path : List.of(URL + "?page=-1", URL + "?page=bad",
        "/api/gas/worksheets/NON_APPRAISED/0/history",
        "/api/gas/worksheets/NON_APPRAISED/not-an-id/history")) {
      mvc.perform(get(path).header("Authorization", "Bearer token"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    verifyNoInteractions(audit);
  }

  @Test
  void normalizedParentAndPageUseTheAuthenticatedGrantAndSerializeNewValues() throws Exception {
    signIn("TAPS_REGION_APPRAISER_REGION-CARIBOO");
    var item = new GasAudit.Item("R:401:3", "201", "IDIR\\SYNTHETIC",
        LocalDateTime.of(2030, 1, 1, 12, 34, 56), "Grade", " ", "<b>Synthetic comment</b>");
    when(audit.history(any(), eq(nonAppraised("123")), eq(1))).thenReturn(Optional.of(new GasAudit.HistoryPage(
        new GasAppraisal.Key(GasAppraisal.WorksheetType.NON_APPRAISED, "123"), List.of(item), 11, 1, 10)));
    mvc.perform(get("/api/gas/worksheets/NON_APPRAISED/000123/history?page=1")
        .header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.key.type").value("NON_APPRAISED"))
        .andExpect(jsonPath("$.key.worksheetId").value("123"))
        .andExpect(jsonPath("$.total").value(11))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.items[0].eventId").value("R:401:3"))
        .andExpect(jsonPath("$.items[0].rateId").value("201"))
        .andExpect(jsonPath("$.items[0].eventDate").value("2030-01-01T12:34:56"))
        .andExpect(jsonPath("$.items[0].attribute").value("Grade"))
        .andExpect(jsonPath("$.items[0].value").value(" "))
        .andExpect(jsonPath("$.items[0].comment").value("<b>Synthetic comment</b>"));
    var user = ArgumentCaptor.forClass(TapsUser.class);
    verify(audit).history(user.capture(), eq(nonAppraised("123")), eq(1));
    assertThat(user.getValue().grants().getFirst().scope().value()).isEqualTo("CARIBOO");
  }

  @Test
  void missingOrUnscopedWorksheetReturns404WhileNoHistoryReturnsEmptyPage() throws Exception {
    signIn("TAPS_ADMIN");
    when(audit.history(any(), eq(nonAppraised("123")), eq(0))).thenReturn(Optional.empty());
    mvc.perform(get(URL).header("Authorization", "Bearer token"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    when(audit.history(any(), eq(nonAppraised("123")), eq(0))).thenReturn(Optional.of(new GasAudit.HistoryPage(
        new GasAppraisal.Key(GasAppraisal.WorksheetType.NON_APPRAISED, "123"), List.of(), 0, 0, 10)));
    mvc.perform(get(URL).header("Authorization", "Bearer token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.size").value(10));
  }

  @Test
  void appraisedAndNonAppraisedHistoryKeepEqualNumericIdsInSeparateTypedKeys() throws Exception {
    signIn("TAPS_REGION_APPRAISER_REGION-CARIBOO");
    var appraised = new GasAppraisal.Key(GasAppraisal.WorksheetType.APPRAISED, "123");
    var nonAppraised = nonAppraised("123");
    var item = new GasAudit.Item("W:301:1", null, null, LocalDateTime.of(2030, 1, 1, 12, 34, 56),
        "Discount Percent", "0.0", null);
    when(audit.history(any(), eq(appraised), eq(0))).thenReturn(Optional.of(
        new GasAudit.HistoryPage(appraised, List.of(item), 1, 0, 10)));
    when(audit.history(any(), eq(nonAppraised), eq(0))).thenReturn(Optional.of(
        new GasAudit.HistoryPage(nonAppraised, List.of(), 0, 0, 10)));
    mvc.perform(get("/api/gas/worksheets/APPRAISED/000123/history").header("Authorization", "Bearer token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.key.type").value("APPRAISED"))
        .andExpect(jsonPath("$.key.worksheetId").value("123"))
        .andExpect(jsonPath("$.items[0].attribute").value("Discount Percent"))
        .andExpect(jsonPath("$.items[0].value").value("0.0"))
        .andExpect(jsonPath("$.items[0].comment").isEmpty());
    mvc.perform(get(URL).header("Authorization", "Bearer token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.key.type").value("NON_APPRAISED"))
        .andExpect(jsonPath("$.total").value(0));
    verify(audit).history(any(), eq(appraised), eq(0));
    verify(audit).history(any(), eq(nonAppraised), eq(0));
  }

  @Test
  void appraisedHistoryRequiresCapabilityAndSanitizesStoredDataFailures() throws Exception {
    String path = "/api/gas/worksheets/APPRAISED/123/history";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    signIn("TAPS_HEADQUARTERS");
    mvc.perform(get(path).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
    verifyNoInteractions(audit);
    signIn("TAPS_ADMIN");
    doThrow(new DataIntegrityViolationException("private appraised snapshot")).when(audit)
        .history(any(), eq(new GasAppraisal.Key(GasAppraisal.WorksheetType.APPRAISED, "123")), eq(0));
    String body = mvc.perform(get(path).header("Authorization", "Bearer token"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("READ_UNAVAILABLE"))
        .andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain("private", "snapshot");
  }

  @Test
  void databaseAndStoredDataFailuresUseSanitized503Responses() throws Exception {
    signIn("TAPS_ADMIN");
    for (RuntimeException failure : List.of(new DataAccessResourceFailureException("private connection and SQL"),
        new DataIntegrityViolationException("private worksheet ownership"),
        new IllegalArgumentException("private stored record"))) {
      doThrow(failure).when(audit).history(any(), any(GasAppraisal.Key.class), anyInt());
      String body = mvc.perform(get(URL).header("Authorization", "Bearer token"))
          .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("READ_UNAVAILABLE"))
          .andReturn().getResponse().getContentAsString();
      assertThat(body).doesNotContain("private", "connection", "SQL", "ownership", "stored");
    }
  }

  private static GasAppraisal.Key nonAppraised(String id) {
    return new GasAppraisal.Key(GasAppraisal.WorksheetType.NON_APPRAISED, id);
  }

  private void signIn(String role) {
    Instant now = Instant.now();
    when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "RS256")
        .subject("synthetic").issuedAt(now).expiresAt(now.plusSeconds(300)).claim("azp", "taps")
        .claim("typ", "Bearer").claim("identity_provider", "azureidir")
        .claim("idir_username", "synthetic").claim("client_roles", List.of(role)).build());
  }
}
