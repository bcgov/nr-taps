package ca.bc.gov.nrs.taps.configuration;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("oracle")
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

  /** Lets the oracle profile start without a database, then reports it down. */
  @TestConfiguration(proxyBeanMethods = false)
  static class FailedDatabase {
    @Bean
    DataSource dataSource() throws SQLException {
      Connection connection = mock(Connection.class);
      when(connection.isValid(anyInt())).thenReturn(true);
      DataSource dataSource = mock(DataSource.class);
      when(dataSource.getConnection()).thenReturn(connection);
      return dataSource;
    }

    @Bean
    HealthIndicator dbHealthIndicator() {
      return () -> Health.down().withDetail("private", "never expose this detail").build();
    }
  }
}
