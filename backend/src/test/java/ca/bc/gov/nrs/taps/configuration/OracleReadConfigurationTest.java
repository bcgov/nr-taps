package ca.bc.gov.nrs.taps.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.taps.read.oracle.OracleEcasInbox;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OracleReadConfigurationTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(OracleReadConfiguration.class);

  @Test
  void absentOrFalseFlagCreatesNoPoolOrReaders() {
    context.run(application -> {
      assertThat(application).doesNotHaveBean(DataSource.class);
      assertThat(application).doesNotHaveBean(OracleEcasInbox.class);
    });
    context.withPropertyValues("taps.oracle.enabled=false").run(application ->
        assertThat(application).doesNotHaveBean(DataSource.class));
  }

  @Test
  void enabledConfigurationFailsBeforeConnectingWithoutRequiredValues() {
    for (String missing : new String[] {"jdbc-url", "username", "password"}) {
      context.withPropertyValues("taps.oracle.enabled=true",
          "taps.oracle.jdbc-url=jdbc:oracle:thin:@localhost:1521/unused",
          "taps.oracle.username=synthetic", "taps.oracle.password=synthetic",
          "taps.oracle." + missing + "=").run(application -> {
            assertThat(application).hasFailed();
            assertThat(application.getStartupFailure()).hasRootCauseMessage(
                "taps.oracle." + missing + " is required when Oracle is enabled");
          });
    }
  }

  @Test
  void poolUsesBoundedTimeoutsAndTheGeneratedTruststore() {
    var properties = properties(10, 1, 10000, 10000, 30000, 20, "/mounted/trust.jks", "synthetic");
    var pool = OracleReadConfiguration.poolConfiguration(properties);
    assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
    assertThat(pool.getMinimumIdle()).isEqualTo(1);
    assertThat(pool.getConnectionTimeout()).isEqualTo(10000);
    assertThat(pool.getInitializationFailTimeout()).isPositive();
    // nr-lexis trusts the init-generated certificate without separate server DN matching.
    assertThat(pool.getDataSourceProperties()).doesNotContainKey("oracle.net.ssl_server_dn_match")
        .containsEntry("oracle.net.CONNECT_TIMEOUT", "10000")
        .containsEntry("oracle.jdbc.ReadTimeout", "30000")
        .containsEntry("javax.net.ssl.trustStore", "/mounted/trust.jks");
    assertThat(new OracleReadConfiguration().oracleJdbcTemplate(mock(DataSource.class), properties)
        .getQueryTimeout()).isEqualTo(20);
    assertThat(properties.toString()).isEqualTo("OracleProperties[redacted]");
  }

  @Test
  void rejectsUnboundedPoolAndTimeoutValuesAndIncompleteTruststore() {
    for (OracleProperties properties : new OracleProperties[] {
        properties(31, 1, 10000, 10000, 30000, 20, null, null),
        properties(10, 11, 10000, 10000, 30000, 20, null, null),
        properties(10, 1, 0, 10000, 30000, 20, null, null),
        properties(10, 1, 10000, 0, 30000, 20, null, null),
        properties(10, 1, 10000, 10000, 0, 20, null, null),
        properties(10, 1, 10000, 10000, 30000, 0, null, null),
        properties(10, 1, 10000, 10000, 30000, 20, "/mounted/trust.jks", "")}) {
      assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void startupRequiresAValidConnectionAndDoesNotExposeSqlExceptions() throws Exception {
    var dataSource = mock(DataSource.class);
    var connection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(5)).thenReturn(false);
    assertThatThrownBy(() -> new OracleReadConfiguration().warmOraclePool(dataSource).afterPropertiesSet())
        .hasMessage("Oracle startup validation failed");
    when(connection.isValid(5)).thenReturn(true);
    new OracleReadConfiguration().warmOraclePool(dataSource).afterPropertiesSet();
    when(dataSource.getConnection()).thenThrow(new SQLException("private connection detail"));
    assertThatThrownBy(() -> new OracleReadConfiguration().warmOraclePool(dataSource).afterPropertiesSet())
        .hasMessage("Oracle startup validation failed").hasNoCause();
  }

  private static OracleProperties properties(int maximum, int minimum, long checkout, int connect,
      int read, int query, String truststore, String truststorePassword) {
    return new OracleProperties("jdbc:oracle:thin:@localhost:1521/unused", "synthetic", "synthetic",
        maximum, minimum, checkout, connect, read, query, truststore, "JKS", truststorePassword);
  }
}
