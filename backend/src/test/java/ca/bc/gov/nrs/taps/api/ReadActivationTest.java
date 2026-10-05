package ca.bc.gov.nrs.taps.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Spring's boolean conversion accepts "yes"; the Oracle beans only accept "true".
@SpringBootTest(properties = {"taps.oracle.enabled=yes", "taps.auth.client-id=taps"})
@AutoConfigureMockMvc
class ReadActivationTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void signIn() {
    Instant now = Instant.now();
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
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build());
  }

  @Test
  void routesAndSessionAgreeWithTheOracleBeanCondition() throws Exception {
    mvc.perform(get("/api/me").header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readApiEnabled").value(false));
    mvc.perform(get("/api/gas/lookups").header("Authorization", "Bearer token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void errorDispatchKeepsTheStatusSpringSelected() throws Exception {
    mvc.perform(
            get("/error")
                .header("Authorization", "Bearer token")
                .with(
                    request -> {
                      request.setDispatcherType(DispatcherType.ERROR);
                      request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
                      request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/gas/lookups");
                      return request;
                    }))
        .andExpect(status().isInternalServerError());
  }
}
