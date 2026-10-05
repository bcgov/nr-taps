package ca.bc.gov.nrs.taps.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.EcasAttachments;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasAttachments;
import ca.bc.gov.nrs.taps.security.SecurityConfiguration;
import ca.bc.gov.nrs.taps.security.TapsAuthenticationConverter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AttachmentController.class, properties = "taps.auth.client-id=taps")
@ActiveProfiles("oracle")
@Import({SecurityConfiguration.class, TapsAuthenticationConverter.class})
class AttachmentControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean OracleEcasAttachments attachments;
  @MockitoBean JwtDecoder decoder;

  @Test
  void permissionBoundsAndWritesAreRejectedBeforeQuerying() throws Exception {
    mvc.perform(get("/api/ecas/1001/attachments")).andExpect(status().isUnauthorized());
    signIn(List.of());
    mvc.perform(get("/api/ecas/1001/attachments").header("Authorization", "Bearer token")).andExpect(status().isForbidden());
    signIn(List.of("TAPS_ADMIN"));
    mvc.perform(post("/api/ecas/1001/attachments").header("Authorization", "Bearer token")).andExpect(status().isForbidden());
    mvc.perform(get("/api/ecas/invalid/attachments").header("Authorization", "Bearer token")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/ecas/1001/attachments?page=-1").header("Authorization", "Bearer token")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/ecas/1001/attachments?page=1000001").header("Authorization", "Bearer token")).andExpect(status().isBadRequest());
    verifyNoInteractions(attachments);
  }

  @Test
  void returnsOnlyPermittedMetadataAndPreservesLocalTimestampsAndNulls() throws Exception {
    signIn(List.of("TAPS_ADMIN"));
    var item = new EcasAttachments.Item("7001", new CodeOption("DOC", "Document"), "E",
        "C:\\private\\sample.pdf", null, 2, LocalDateTime.of(2026,1,2,13,45,56), null);
    when(attachments.forSubmission(any(), eq("1001"), eq(0)))
        .thenReturn(Optional.of(new EcasAttachments.Page("1001", AppraisalMethod.C, List.of(item), 1, 0, 50)));
    String json = mvc.perform(get("/api/ecas/1001/attachments").header("Authorization", "Bearer token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].fileName").value("sample.pdf"))
        .andExpect(jsonPath("$.items[0].createdAt").value("2026-01-02T13:45:56"))
        .andExpect(jsonPath("$.items[0].updatedAt").isEmpty()).andExpect(jsonPath("$.size").value(50))
        .andReturn().getResponse().getContentAsString();
    assertThat(json).doesNotContain("private", "submittedFileId", "fileContent", "storage", "downloadUrl");
  }

  @Test
  void missingOrInaccessibleParentsShareNotFoundAndFailuresStaySafe() throws Exception {
    signIn(List.of("TAPS_ADMIN"));
    when(attachments.forSubmission(any(), eq("1001"), eq(0))).thenReturn(Optional.empty())
        .thenThrow(new DataAccessResourceFailureException("sensitive SQL and connection details"));
    mvc.perform(get("/api/ecas/1001/attachments").header("Authorization", "Bearer token"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    String body = mvc.perform(get("/api/ecas/1001/attachments").header("Authorization", "Bearer token"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("READ_UNAVAILABLE"))
        .andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain("sensitive", "SQL", "connection");
  }

  private void signIn(List<String> roles) {
    when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "RS256")
        .subject("synthetic-user").issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(600))
        .claim("identity_provider", "azureidir").claim("idir_username", "synthetic-user")
        .claim("client_roles", roles).claim("azp", "taps").claim("typ", "Bearer").build());
  }
}
