package ca.bc.gov.nrs.taps.configuration;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Oracle reads, their routes and the session's readApiEnabled flag all follow the oracle profile. */
public final class OracleProfile {
  private OracleProfile() {}

  public static boolean active(Environment environment) {
    return environment.acceptsProfiles(Profiles.of("oracle"));
  }
}
