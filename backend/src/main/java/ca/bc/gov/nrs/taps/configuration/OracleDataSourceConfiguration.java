package ca.bc.gov.nrs.taps.configuration;

import ca.bc.gov.nrs.taps.read.oracle.OracleAppraisedSummary;
import ca.bc.gov.nrs.taps.read.oracle.OracleCodeLists;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasAttachments;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasAudit;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasInbox;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasOrganizations;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasReference;
import ca.bc.gov.nrs.taps.read.oracle.OracleFtaLicenceInformation;
import ca.bc.gov.nrs.taps.read.oracle.OracleGasSearch;
import ca.bc.gov.nrs.taps.read.oracle.OracleGasAudit;
import ca.bc.gov.nrs.taps.read.oracle.OracleLicenceMarks;
import ca.bc.gov.nrs.taps.read.oracle.OracleOtherWorksheetSummary;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** The pool itself comes from spring.datasource in application-oracle.yml. */
@Configuration(proxyBeanMethods = false)
@Profile("oracle")
public class OracleDataSourceConfiguration {
  @Bean
  InitializingBean warmOraclePool(DataSource dataSource) {
    // Fail startup on a bad Oracle connection.
    return () -> {
      if (dataSource instanceof HikariDataSource pool) {
        requireRange(pool.getMaximumPoolSize(), 1, 10, "maximum pool size");
        requireRange(pool.getMinimumIdle(), 0, pool.getMaximumPoolSize(), "minimum idle connections");
        requireRange(pool.getConnectionTimeout(), 250, 30000, "connection wait timeout");
        requireRange(Long.parseLong(String.valueOf(pool.getDataSourceProperties().get("oracle.net.CONNECT_TIMEOUT"))),
            1000, 10000, "Oracle connect timeout");
        requireRange(Long.parseLong(String.valueOf(pool.getDataSourceProperties().get("oracle.jdbc.ReadTimeout"))),
            1000, 30000, "Oracle read timeout");
      }
      try (var connection = dataSource.getConnection()) {
        if (!connection.isValid(5)) {
          throw new IllegalStateException("Oracle startup validation failed");
        }
      } catch (SQLException exception) {
        throw new IllegalStateException("Oracle startup validation failed", exception);
      }
    };
  }

  @Bean
  JdbcTemplate oracleJdbcTemplate(DataSource dataSource,
      @Value("${DATABASE_QUERY_TIMEOUT_SECONDS:20}") int queryTimeoutSeconds) {
    requireRange(queryTimeoutSeconds, 1, 20, "query timeout");
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.setQueryTimeout(queryTimeoutSeconds);
    return jdbc;
  }

  private static void requireRange(long value, long minimum, long maximum, String setting) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(setting + " must be between " + minimum + " and " + maximum);
    }
  }

  @Bean
  OracleCodeLists oracleCodeLists(JdbcTemplate jdbc) {
    return new OracleCodeLists(jdbc);
  }

  @Bean
  OracleEcasOrganizations oracleEcasOrganizations(JdbcTemplate jdbc) {
    return new OracleEcasOrganizations(jdbc);
  }

  @Bean
  OracleEcasAudit oracleEcasAudit(JdbcTemplate jdbc) { return new OracleEcasAudit(jdbc); }

  @Bean
  OracleGasAudit oracleGasAudit(JdbcTemplate jdbc) { return new OracleGasAudit(jdbc); }

  @Bean
  OracleEcasAttachments oracleEcasAttachments(JdbcTemplate jdbc) { return new OracleEcasAttachments(jdbc); }

  @Bean
  OracleAppraisedSummary oracleAppraisedSummary(JdbcTemplate jdbc) {
    return new OracleAppraisedSummary(jdbc);
  }

  @Bean
  OracleGasSearch oracleGasSearch(JdbcTemplate jdbc) {
    return new OracleGasSearch(jdbc);
  }

  @Bean
  OracleOtherWorksheetSummary oracleOtherWorksheetSummary(JdbcTemplate jdbc) {
    return new OracleOtherWorksheetSummary(jdbc);
  }

  @Bean
  OracleLicenceMarks oracleLicenceMarks(JdbcTemplate jdbc) {
    return new OracleLicenceMarks(jdbc);
  }

  @Bean
  OracleFtaLicenceInformation oracleFtaLicenceInformation(JdbcTemplate jdbc) {
    return new OracleFtaLicenceInformation(jdbc);
  }

  @Bean
  OracleEcasInbox oracleEcasInbox(JdbcTemplate jdbc) {
    return new OracleEcasInbox(jdbc);
  }

  @Bean
  OracleEcasReference oracleEcasReference(JdbcTemplate jdbc) {
    return new OracleEcasReference(jdbc);
  }
}
