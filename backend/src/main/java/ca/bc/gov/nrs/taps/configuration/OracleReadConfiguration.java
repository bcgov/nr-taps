package ca.bc.gov.nrs.taps.configuration;

import ca.bc.gov.nrs.taps.read.oracle.OracleAppraisedSummary;
import ca.bc.gov.nrs.taps.read.oracle.OracleCodeLists;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasInbox;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasReference;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasOrganizations;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasAudit;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasAttachments;
import ca.bc.gov.nrs.taps.read.oracle.OracleFtaLicenceInformation;
import ca.bc.gov.nrs.taps.read.oracle.OracleGasSearch;
import ca.bc.gov.nrs.taps.read.oracle.OracleLicenceMarks;
import ca.bc.gov.nrs.taps.read.oracle.OracleOtherWorksheetSummary;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "taps.oracle.enabled", havingValue = "true")
@EnableConfigurationProperties(OracleProperties.class)
public class OracleReadConfiguration {
  @Bean(destroyMethod = "close")
  HikariDataSource oracleDataSource(OracleProperties properties) {
    return new HikariDataSource(poolConfiguration(properties));
  }

  static HikariConfig poolConfiguration(OracleProperties properties) {
    properties.validate();
    HikariConfig pool = new HikariConfig();
    pool.setPoolName("taps-oracle");
    pool.setDriverClassName("oracle.jdbc.OracleDriver");
    pool.setJdbcUrl(properties.jdbcUrl());
    pool.setUsername(properties.username());
    pool.setPassword(properties.password());
    pool.setMaximumPoolSize(properties.maximumPoolSize());
    pool.setMinimumIdle(properties.minimumIdle());
    pool.setConnectionTimeout(properties.connectionTimeoutMs());
    pool.setValidationTimeout(Math.min(5000, properties.connectionTimeoutMs()));
    pool.setInitializationFailTimeout(properties.connectionTimeoutMs());
    pool.setIdleTimeout(600000);
    pool.setMaxLifetime(1800000);
    pool.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", Integer.toString(properties.connectTimeoutMs()));
    pool.addDataSourceProperty("oracle.jdbc.ReadTimeout", Integer.toString(properties.readTimeoutMs()));
    if (properties.truststorePath() != null && !properties.truststorePath().isBlank()) {
      pool.addDataSourceProperty("javax.net.ssl.trustStore", properties.truststorePath());
      pool.addDataSourceProperty("javax.net.ssl.trustStoreType", properties.truststoreType());
      pool.addDataSourceProperty("javax.net.ssl.trustStorePassword", properties.truststorePassword());
    }
    return pool;
  }

  @Bean
  InitializingBean warmOraclePool(DataSource dataSource) {
    // Fail startup on a bad Oracle connection.
    return () -> {
      try (var connection = dataSource.getConnection()) {
        if (!connection.isValid(5)) {
          throw new IllegalStateException("Oracle startup validation failed");
        }
      } catch (SQLException exception) {
        throw new IllegalStateException("Oracle startup validation failed");
      }
    };
  }

  @Bean
  JdbcTemplate oracleJdbcTemplate(DataSource dataSource, OracleProperties properties) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.setQueryTimeout(properties.queryTimeoutSeconds());
    return jdbc;
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
