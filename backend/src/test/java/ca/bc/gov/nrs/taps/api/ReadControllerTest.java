package ca.bc.gov.nrs.taps.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ca.bc.gov.nrs.taps.configuration.JsonConfiguration;
import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.EcasInbox;
import ca.bc.gov.nrs.taps.read.GasAppraisal;
import ca.bc.gov.nrs.taps.read.oracle.*;
import ca.bc.gov.nrs.taps.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {ReadController.class, CurrentUserController.class},
    properties = "taps.auth.client-id=taps")
@ActiveProfiles("oracle")
@Import({SecurityConfiguration.class, TapsAuthenticationConverter.class, JsonConfiguration.class})
class ReadControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean OracleEcasInbox inbox;
  @MockitoBean OracleEcasReference references;
  @MockitoBean OracleGasSearch worksheets;
  @MockitoBean OracleAppraisedSummary appraised;
  @MockitoBean OracleOtherWorksheetSummary other;
  @MockitoBean OracleLicenceMarks marks;
  @MockitoBean OracleFtaLicenceInformation information;

  @Test
  void anonymousAndUnderprivilegedRequestsStopBeforeReaders() throws Exception {
    mvc.perform(get("/api/gas/worksheets")).andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.title").value("Authentication required"))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    signIn("TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00001018", "bceidbusiness");
    for (String path : List.of("/api/gas/worksheets", "/api/gas/worksheets/APPRAISED/123",
        "/api/gas/appraised/by-ecas/123", "/api/gas/licences/A00001/marks",
        "/api/gas/licence-information?timberMark=AB1234")) {
      mvc.perform(get(path).header("Authorization", "Bearer token"))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
    signIn("", "azureidir");
    mvc.perform(post("/api/ecas/inbox").header("Authorization", "Bearer token")
        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"ALL_SUBMISSIONS\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/ecas/references/C/123").header("Authorization", "Bearer token"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(inbox, references, worksheets, appraised, other, marks, information);
  }

  @Test
  void sessionExposesEnabledReadsAndUnknownRoutesOrWritesRemainDenied() throws Exception {
    signIn("TAPS_ADMIN", "azureidir");
    mvc.perform(get("/api/me").header("Authorization", "Bearer token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.readApiEnabled").value(true))
        .andExpect(jsonPath("$.ecasMyToDoAvailable").value(true))
        .andExpect(jsonPath("$.legacyAccount").doesNotExist());
    mvc.perform(post("/api/gas/worksheets").header("Authorization", "Bearer token"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/gas/future").header("Authorization", "Bearer token"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(inbox, references, worksheets);
  }

  @Test
  void ecasSearchRetainsTheAuthenticatedGrantAndNormalizedFilters() throws Exception {
    signIn("TAPS_REGION_APPRAISER_REGION-OMINECA", "azureidir");
    when(inbox.search(any(), any(), eq(2))).thenReturn(new EcasInbox.Page(List.of(), 0, 2));
    mvc.perform(post("/api/ecas/inbox?page=2").header("Authorization", "Bearer token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"mode\":\"ALL_SUBMISSIONS\",\"licence\":\" A00001 \"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2));
    var user = ArgumentCaptor.forClass(TapsUser.class);
    var search = ArgumentCaptor.forClass(EcasInbox.Search.class);
    verify(inbox).search(user.capture(), search.capture(), eq(2));
    assertThat(user.getValue().grants()).hasSize(1);
    assertThat(user.getValue().grants().getFirst().scope().value()).isEqualTo("OMINECA");
    assertThat(search.getValue().licence()).isEqualTo("A00001");
  }

  @Test
  void invalidBodiesPathsAndParametersNeverReachReaders() throws Exception {
    signIn("TAPS_ADMIN", "azureidir");
    for (String body : List.of("{", "{\"mode\":\"UNKNOWN\"}",
        "{\"mode\":\"ALL_SUBMISSIONS\",\"cuttingPermit\":\"1\"}",
        "{\"mode\":\"ALL_SUBMISSIONS\",\"statusCodes\":[null]}",
        "{\"mode\":\"ALL_SUBMISSIONS\",\"orgUnitNumbers\":[\"bogus\"]}",
        "{\"mode\":\"ALL_SUBMISSIONS\",\"orgUnitNumbers\":[\"-1\"]}",
        "{\"mode\":\"ALL_SUBMISSIONS\",\"orgUnitNumbers\":[\"0\"]}",
        "{\"mode\":\"ALL_SUBMISSIONS\",\"orgUnitNumbers\":[\"1.5\"]}",
        "{\"mode\":\"ALL_SUBMISSIONS\",\"orgUnitNumbers\":[\"1234567890123\"]}",
        "{\"mode\":\"ALL_SUBMISSIONS\",\"licence\":\"" + "X".repeat(1100) + "\"}")) {
      mvc.perform(post("/api/ecas/inbox").header("Authorization", "Bearer token")
          .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    for (String path : List.of("/api/gas/worksheets?page=-1", "/api/gas/worksheets?page=oops",
        "/api/gas/worksheets?licence=ABCDEFGHIJK", "/api/gas/worksheets/UNKNOWN/123",
        "/api/gas/worksheets/APPRAISED/not-an-id", "/api/ecas/references/X/123",
        "/api/ecas/references/C/not-an-id", "/api/gas/licence-information")) {
      mvc.perform(get(path).header("Authorization", "Bearer token"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    verifyNoInteractions(inbox, references, worksheets, appraised, other, marks, information);
  }

  @Test
  void assignedMyToDoUsesSignedIdentityAndKeepsRequestedActorAsAFilter() throws Exception {
    signIn("TAPS_HEADQUARTERS", "azureidir");
    when(inbox.search(any(), any(), eq(0))).thenReturn(new EcasInbox.Page(List.of(), 0, 0));
    mvc.perform(post("/api/ecas/inbox").header("Authorization", "Bearer token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"mode\":\"MY_TO_DO\",\"workedOnByUserId\":\"IDIR\\\\OTHER\"}"))
        .andExpect(status().isOk());
    var user = ArgumentCaptor.forClass(TapsUser.class);
    var search = ArgumentCaptor.forClass(EcasInbox.Search.class);
    verify(inbox).search(user.capture(), search.capture(), eq(0));
    assertThat(user.getValue().legacyAccount()).isEqualTo("IDIR\\SYNTHETIC");
    assertThat(search.getValue().workedOnByUserId()).isEqualTo("IDIR\\OTHER");
    assertThat(search.getValue().mode()).isEqualTo(EcasInbox.Mode.MY_TO_DO);
  }

  @Test
  void guidFallbackDisablesAssignedListingBeforeReaderButRetainsOtherReads() throws Exception {
    signIn(Map.of("identity_provider", "azureidir", "idir_user_guid", "synthetic-guid",
        "display_name", "IDIR\\SYNTHETIC", "preferred_username", "synthetic",
        "client_roles", List.of("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ")));
    mvc.perform(get("/api/me").header("Authorization", "Bearer token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.readApiEnabled").value(true))
        .andExpect(jsonPath("$.ecasMyToDoAvailable").value(false))
        .andExpect(jsonPath("$.userId").value("IDIR\\SYNTHETIC-GUID"));
    mvc.perform(post("/api/ecas/inbox").header("Authorization", "Bearer token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"mode\":\"MY_TO_DO\",\"workedOnByUserId\":\"IDIR\\\\SYNTHETIC\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(inbox);
    when(inbox.search(any(), any(), eq(0))).thenReturn(new EcasInbox.Page(List.of(), 0, 0));
    for (String body : List.of("{\"mode\":\"ALL_SUBMISSIONS\"}", "{\"ecasId\":\"123\"}")) {
      mvc.perform(post("/api/ecas/inbox").header("Authorization", "Bearer token")
          .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }
    var user = ArgumentCaptor.forClass(TapsUser.class);
    verify(inbox, times(2)).search(user.capture(), any(), eq(0));
    assertThat(user.getAllValues()).allSatisfy(principal -> assertThat(principal.legacyAccount()).isNull());
  }

  @Test
  void guidOnlyAdministratorCanUseDefaultMyToDoWithoutAssignments() throws Exception {
    signIn(Map.of("identity_provider", "idir", "idir_user_guid", "synthetic-guid",
        "client_roles", List.of("TAPS_ADMIN")));
    mvc.perform(get("/api/me").header("Authorization", "Bearer token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.ecasMyToDoAvailable").value(true));
    when(inbox.search(any(), any(), eq(0))).thenReturn(new EcasInbox.Page(List.of(), 0, 0));
    mvc.perform(post("/api/ecas/inbox").header("Authorization", "Bearer token")
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk());
  }

  @Test
  void scopedAbsentAndDatabaseFailureResponsesDoNotExposeDetails() throws Exception {
    signIn("TAPS_ADMIN", "azureidir");
    when(appraised.byEcasId(any(), eq("123"))).thenReturn(Optional.empty());
    mvc.perform(get("/api/gas/appraised/by-ecas/123").header("Authorization", "Bearer token"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    when(worksheets.search(any(), any())).thenThrow(new DataIntegrityViolationException("SELECT private FROM secret"));
    String body = mvc.perform(get("/api/gas/worksheets").header("Authorization", "Bearer token"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("READ_UNAVAILABLE"))
        .andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain("SELECT", "private", "secret", "exception", "stack");
    doThrow(new IllegalArgumentException("invalid stored rate")).when(worksheets).search(any(), any());
    mvc.perform(get("/api/gas/worksheets").header("Authorization", "Bearer token"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void everyWorksheetFamilyDispatchesByTypedKeyAndReferenceMethod() throws Exception {
    signIn("TAPS_ADMIN", "azureidir");
    when(appraised.byTypedKey(any(), any())).thenReturn(Optional.empty());
    when(other.historic(any(), any())).thenReturn(Optional.empty());
    when(other.nonAppraised(any(), any())).thenReturn(Optional.empty());
    when(references.coast(any(), any())).thenReturn(Optional.empty());
    when(references.interior(any(), any())).thenReturn(Optional.empty());
    for (String family : List.of("APPRAISED", "HISTORIC", "NON_APPRAISED")) {
      mvc.perform(get("/api/gas/worksheets/" + family + "/123").header("Authorization", "Bearer token"))
          .andExpect(status().isNotFound());
    }
    for (String method : List.of("C", "I")) {
      mvc.perform(get("/api/ecas/references/" + method + "/123").header("Authorization", "Bearer token"))
          .andExpect(status().isNotFound());
    }
    verify(appraised).byTypedKey(any(), eq(new GasAppraisal.Key(GasAppraisal.WorksheetType.APPRAISED, "123")));
    verify(other).historic(any(), eq(new GasAppraisal.Key(GasAppraisal.WorksheetType.HISTORIC, "123")));
    verify(other).nonAppraised(any(), eq(new GasAppraisal.Key(GasAppraisal.WorksheetType.NON_APPRAISED, "123")));
    verify(references).coast(any(), eq("123"));
    verify(references).interior(any(), eq("123"));
  }

  @Test
  void independentMarksAndFtaInformationRemainAvailableWithoutWorksheets() throws Exception {
    signIn("TAPS_ADMIN", "azureidir");
    when(marks.forLicence(any(), eq("A00001")))
        .thenReturn(new GasAppraisal.LicenceMarks("A00001", List.of("AB1234", "AB5678")));
    when(information.find(any(), eq("A00001"), eq("AB1234"))).thenReturn(Optional.empty());
    mvc.perform(get("/api/gas/licences/a00001/marks").header("Authorization", "Bearer token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.timberMarks[1]").value("AB5678"));
    mvc.perform(get("/api/gas/licence-information?licence=a00001&timberMark=ab1234")
        .header("Authorization", "Bearer token")).andExpect(status().isNotFound());
    verify(information).find(any(), eq("A00001"), eq("AB1234"));
    verifyNoInteractions(worksheets);
  }

  @Test
  void storedSummaryPreservesStringIdentifiersDecimalScaleDatesAndMultipleMarks() throws Exception {
    signIn("TAPS_ADMIN", "azureidir");
    var fixture = mapper.readTree(getClass().getResourceAsStream("/contracts/synthetic-workflow.json"));
    var summary = mapper.treeToValue(fixture.get("gasMultiMarkAppraisedSummary"), GasAppraisal.AppraisedSummary.class);
    when(appraised.byEcasId(any(), eq(summary.ecasId()))).thenReturn(Optional.of(summary));
    mvc.perform(get("/api/gas/appraised/by-ecas/" + summary.ecasId()).header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ecasId").value(summary.ecasId()))
        .andExpect(jsonPath("$.timberMarks.length()").value(2))
        .andExpect(jsonPath("$.effectiveDate").value("2026-10-01"))
        .andExpect(jsonPath("$.rates[0].totalStumpageRate").isString())
        .andExpect(jsonPath("$.rates[0].totalStumpageRate").value("12.30"));
  }

  @Test
  void nonAppraisedSummaryPublishesLabelsAndExactDisplayTotals() throws Exception {
    signIn("TAPS_ADMIN", "azureidir");
    var key = new GasAppraisal.Key(GasAppraisal.WorksheetType.NON_APPRAISED, "123");
    var rate = new GasAppraisal.StoredNonAppraisedRate("456", new CodeOption("FI", "Synthetic fir"),
        new CodeOption(" ", "Logs"), new CodeOption(" ", "Ungraded"),
        new BigDecimal("999.99"), new BigDecimal("999.99"), new BigDecimal("999.99"), new BigDecimal("999.99"));
    var summary = new GasAppraisal.NonAppraisedSummary(key, "A00001", "AA0001",
        AppraisalMethod.C, null, null, null,
        new CodeOption("NEW", "Synthetic new appraisal"), null, new CodeOption("1201", "1201 - Synthetic TSB"),
        new CodeOption("A", null), null, null, List.of(rate), List.of());
    when(other.nonAppraised(any(), eq(key))).thenReturn(Optional.of(summary));
    mvc.perform(get("/api/gas/worksheets/NON_APPRAISED/123").header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.referenceType.description").value("Synthetic new appraisal"))
        .andExpect(jsonPath("$.appraisalForestZone.code").value("A"))
        .andExpect(jsonPath("$.appraisalForestZone.description").isEmpty())
        .andExpect(jsonPath("$.nonAppraisedRateType").isEmpty())
        .andExpect(jsonPath("$.referenceTypeCode").doesNotExist())
        .andExpect(jsonPath("$.rates[0].scaleSpecies.description").value("Synthetic fir"))
        .andExpect(jsonPath("$.rates[0].scaleProduct.code").value(" "))
        .andExpect(jsonPath("$.rates[0].scaleGrade.description").value("Ungraded"))
        .andExpect(jsonPath("$.rates[0].reserveStumpageRate").value("999.99"))
        .andExpect(jsonPath("$.rates[0].upsetStumpageRate").isString())
        .andExpect(jsonPath("$.rates[0].upsetStumpageRate").value("2999.97"))
        .andExpect(jsonPath("$.rates[0].totalStumpageRate").isString())
        .andExpect(jsonPath("$.rates[0].totalStumpageRate").value("3999.96"));
  }

  private void signIn(String role, String provider) {
    signIn(Map.of("identity_provider", provider, "idir_username", "synthetic",
        "bceid_username", "synthetic", "client_roles", role.isEmpty() ? List.of() : List.of(role)));
  }

  private void signIn(Map<String, Object> claims) {
    Instant now = Instant.now();
    when(jwtDecoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "RS256")
        .subject("synthetic-user").claim("azp", "taps").claim("typ", "Bearer")
        .issuedAt(now).expiresAt(now.plusSeconds(300)).claims(values -> values.putAll(claims)).build());
  }
}
