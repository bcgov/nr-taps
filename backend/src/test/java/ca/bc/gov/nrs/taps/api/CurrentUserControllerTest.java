package ca.bc.gov.nrs.taps.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "management.endpoint.health.probes.enabled=true")
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
  void currentUserReturnsAuthenticatedIdentity() throws Exception {
    mvc.perform(
            get("/api/me")
                .with(jwt().jwt(token -> token.subject("user-123").claim("name", "TAPS User"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value("user-123"))
        .andExpect(jsonPath("$.name").value("TAPS User"));
  }
}
