package ca.bc.gov.nrs.taps.configuration;

import java.security.Security;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * After the listener hands off a TCPS connection, the shared Oracle database only accepts
 * TLS_RSA_WITH_AES_256_CBC_SHA. Java 21.0.12 disables TLS_RSA_* by default, so re-enable those
 * suites until the database supports ECDHE; all other Java defaults stay in place.
 */
public final class OracleTlsCompatibility {

  static final String DISABLED_ALGORITHMS = "jdk.tls.disabledAlgorithms";
  static final String RSA_KEY_EXCHANGE = "TLS_RSA_*";

  private OracleTlsCompatibility() {}

  /** Call before the first TLS connection; Java reads this property once. */
  public static void allowRsaKeyExchange() {
    String disabledAlgorithms = Security.getProperty(DISABLED_ALGORITHMS);
    if (disabledAlgorithms != null) {
      Security.setProperty(DISABLED_ALGORITHMS, withoutRsaKeyExchange(disabledAlgorithms));
    }
  }

  static String withoutRsaKeyExchange(String disabledAlgorithms) {
    return Arrays.stream(disabledAlgorithms.split(","))
        .map(String::trim)
        .filter(algorithm -> !algorithm.equals(RSA_KEY_EXCHANGE))
        .collect(Collectors.joining(", "));
  }
}
