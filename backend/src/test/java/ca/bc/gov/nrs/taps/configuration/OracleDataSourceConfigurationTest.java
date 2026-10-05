package ca.bc.gov.nrs.taps.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.taps.read.oracle.OracleEcasInbox;
import ca.bc.gov.nrs.taps.read.oracle.OracleGasSearch;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

class OracleDataSourceConfigurationTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(OracleDataSourceConfiguration.class)
      .withBean(DataSource.class, OracleDataSourceConfigurationTest::validDataSource);

  private final ApplicationContextRunner pool = new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
      .withPropertyValues("spring.profiles.active=oracle", "DATABASE_HOST=oracle.example.test",
          "DATABASE_SERVICE_NAME=taps.example.test", "DATABASE_USER=synthetic",
          "DATABASE_PASSWORD=synthetic", "KEYSTORE_SECRET=synthetic");

  @Test
  void withoutTheOracleProfileThereIsNoTemplateOrReaders() {
    context.run(application -> {
      assertThat(application).doesNotHaveBean(JdbcTemplate.class);
      assertThat(application).doesNotHaveBean(OracleEcasInbox.class);
    });
    context.withPropertyValues("spring.profiles.active=local").run(application ->
        assertThat(application).doesNotHaveBean(OracleEcasInbox.class));
  }

  @Test
  void oracleProfileRegistersReadersOnATemplateWithAQueryTimeout() {
    context.withPropertyValues("spring.profiles.active=oracle").run(application -> {
      assertThat(application).hasSingleBean(OracleEcasInbox.class).hasSingleBean(OracleGasSearch.class);
      assertThat(application.getBean(JdbcTemplate.class).getQueryTimeout()).isEqualTo(20);
    });
    context.withPropertyValues("spring.profiles.active=oracle", "DATABASE_QUERY_TIMEOUT_SECONDS=5")
        .run(application -> assertThat(application.getBean(JdbcTemplate.class).getQueryTimeout()).isEqualTo(5));
  }

  @Test
  void oracleProfileBuildsATcpsPoolWithBoundedTimeoutsAndTheMountedTruststore() {
    pool.run(application -> {
      HikariDataSource dataSource = application.getBean(HikariDataSource.class);
      assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)"
          + "(HOST=oracle.example.test)(PORT=1543))(CONNECT_DATA=(SERVICE_NAME=taps.example.test)(SERVER=DEDICATED)))");
      assertThat(dataSource.getUsername()).isEqualTo("synthetic");
      assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
      assertThat(dataSource.getMinimumIdle()).isEqualTo(1);
      assertThat(dataSource.getConnectionTimeout()).isEqualTo(30000);
      // TCPS trusts the init-generated certificate; no separate server DN match.
      assertThat(dataSource.getDataSourceProperties()).doesNotContainKey("oracle.net.ssl_server_dn_match")
          .containsEntry("oracle.net.CONNECT_TIMEOUT", "10000")
          .containsEntry("oracle.jdbc.ReadTimeout", "30000")
          .containsEntry("oracle.jdbc.javaNetNio", "false")
          .containsEntry("javax.net.ssl.trustStore", "/cert/jssecacerts")
          .containsEntry("javax.net.ssl.trustStoreType", "JKS")
          .containsEntry("javax.net.ssl.trustStorePassword", "synthetic");
    });
    pool.withPropertyValues("DATABASE_PORT=2484", "DATABASE_CONNECT_TIMEOUT_MS=2000",
        "DATABASE_READ_TIMEOUT_MS=5000", "TRUSTSTORE_PATH=/mounted/trust.jks").run(application -> {
          HikariDataSource dataSource = application.getBean(HikariDataSource.class);
          assertThat(dataSource.getJdbcUrl()).contains("(PORT=2484)");
          assertThat(dataSource.getDataSourceProperties())
              .containsEntry("oracle.net.CONNECT_TIMEOUT", "2000")
              .containsEntry("oracle.jdbc.ReadTimeout", "5000")
              .containsEntry("javax.net.ssl.trustStore", "/mounted/trust.jks");
        });
  }

  @Test
  void localRunsCanPointTheProfileAtAPlainTcpDatabase() {
    pool.withInitializer(application -> application.getEnvironment().getPropertySources().addFirst(
            new SystemEnvironmentPropertySource("test-systemEnvironment",
                Map.of("SPRING_DATASOURCE_URL", "jdbc:oracle:thin:@localhost:1521/FREEPDB1"))))
        .run(application -> assertThat(application.getBean(HikariDataSource.class).getJdbcUrl())
            .isEqualTo("jdbc:oracle:thin:@localhost:1521/FREEPDB1"));
  }

  @Test
  void startupRequiresAValidConnectionAndKeepsTheSqlCause() throws Exception {
    var dataSource = mock(DataSource.class);
    var connection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(5)).thenReturn(false);
    assertThatThrownBy(() -> new OracleDataSourceConfiguration().warmOraclePool(dataSource).afterPropertiesSet())
        .hasMessage("Oracle startup validation failed");
    when(connection.isValid(5)).thenReturn(true);
    new OracleDataSourceConfiguration().warmOraclePool(dataSource).afterPropertiesSet();
    var failure = new SQLException("ORA-01017");
    when(dataSource.getConnection()).thenThrow(failure);
    assertThatThrownBy(() -> new OracleDataSourceConfiguration().warmOraclePool(dataSource).afterPropertiesSet())
        .hasMessage("Oracle startup validation failed").hasCause(failure);
  }

  private static DataSource validDataSource() {
    try {
      Connection connection = mock(Connection.class);
      when(connection.isValid(anyInt())).thenReturn(true);
      DataSource dataSource = mock(DataSource.class);
      when(dataSource.getConnection()).thenReturn(connection);
      return dataSource;
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
