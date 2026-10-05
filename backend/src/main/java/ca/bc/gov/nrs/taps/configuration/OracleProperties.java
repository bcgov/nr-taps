package ca.bc.gov.nrs.taps.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** toString is redacted so credentials stay out of logs. */
@ConfigurationProperties("taps.oracle")
public record OracleProperties(
    String jdbcUrl,
    String username,
    String password,
    @DefaultValue("10") int maximumPoolSize,
    @DefaultValue("1") int minimumIdle,
    @DefaultValue("10000") long connectionTimeoutMs,
    @DefaultValue("10000") int connectTimeoutMs,
    @DefaultValue("30000") int readTimeoutMs,
    @DefaultValue("20") int queryTimeoutSeconds,
    String truststorePath,
    @DefaultValue("JKS") String truststoreType,
    String truststorePassword) {

  public void validate() {
    require(jdbcUrl, "jdbc-url");
    require(username, "username");
    require(password, "password");
    if (!jdbcUrl.startsWith("jdbc:oracle:thin:@")) {
      throw new IllegalStateException("taps.oracle.jdbc-url must use the Oracle thin driver without credentials");
    }
    range(maximumPoolSize, 1, 30, "maximum-pool-size");
    range(minimumIdle, 0, maximumPoolSize, "minimum-idle");
    range(connectionTimeoutMs, 1000, 60000, "connection-timeout-ms");
    range(connectTimeoutMs, 1000, 60000, "connect-timeout-ms");
    range(readTimeoutMs, 1000, 120000, "read-timeout-ms");
    range(queryTimeoutSeconds, 1, 60, "query-timeout-seconds");
    if (truststorePath != null && !truststorePath.isBlank()) {
      require(truststoreType, "truststore-type");
      require(truststorePassword, "truststore-password");
    }
  }

  private static void require(String value, String property) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("taps.oracle." + property + " is required when Oracle is enabled");
    }
  }

  private static void range(long value, long min, long max, String property) {
    if (value < min || value > max) {
      throw new IllegalStateException("taps.oracle." + property + " is outside its supported range");
    }
  }

  @Override
  public String toString() {
    return "OracleProperties[redacted]";
  }
}
