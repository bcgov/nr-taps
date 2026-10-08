package ca.bc.gov.nrs.taps.configuration;

import java.security.Security;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Explicit Oracle compatibility exception: re-enables RSA key exchange disabled by current JDKs.
 * All other Java restrictions stay in place. Validate the exception for each deployment and remove
 * it when no longer required; environment-specific TLS details belong in private operational records.
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
