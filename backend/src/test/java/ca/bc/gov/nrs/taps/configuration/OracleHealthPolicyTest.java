package ca.bc.gov.nrs.taps.configuration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OracleHealthPolicyTest.FailedDatabase.class)
class OracleHealthPolicyTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;

  @Test
  void oracleOutageIsObservableWithoutWithdrawingEveryReplicaOrRestartingPods() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"))
        .andExpect(jsonPath("$.components").doesNotExist());
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FailedDatabase {
    @Bean
    HealthIndicator dbHealthIndicator() {
      return () -> Health.down().withDetail("private", "never expose this detail").build();
    }
  }
}
