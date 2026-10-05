package ca.bc.gov.nrs.taps.configuration;

import org.springframework.core.env.Environment;

/** Reads taps.oracle.enabled the same way the Oracle beans' condition does. */
public final class OracleActivation {
  private OracleActivation() {}

  public static boolean enabled(Environment environment) {
    // A @Value boolean would also accept "yes", which leaves the beans off.
    return "true".equalsIgnoreCase(environment.getProperty("taps.oracle.enabled", "false"));
  }
}
