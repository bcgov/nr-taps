import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRulesProvider;
import java.util.TimeZone;

/** Runs in the final Docker build stage without application or test dependencies. */
class VerifyTimezone {

  private static final String ZONE = "America/Vancouver";

  public static void main(String[] args) {
    checkOffset("2026-01-15T12:00:00Z", "-08:00");
    checkOffset("2026-11-01T08:59:59Z", "-07:00");
    checkOffset("2026-11-01T09:00:00Z", "-07:00");
    checkOffset("2027-01-15T12:00:00Z", "-07:00");
    System.out.println(
        "Verified B.C. Pacific Time rules: Java "
            + System.getProperty("java.runtime.version")
            + ", tzdata "
            + ZoneRulesProvider.getVersions(ZONE).lastKey());
  }

  private static void checkOffset(String timestamp, String expectedOffset) {
    Instant instant = Instant.parse(timestamp);
    ZoneOffset expected = ZoneOffset.of(expectedOffset);
    ZoneOffset actual = ZoneId.of(ZONE).getRules().getOffset(instant);
    int legacyOffset = TimeZone.getTimeZone(ZONE).getOffset(instant.toEpochMilli());
    if (!actual.equals(expected) || legacyOffset != expected.getTotalSeconds() * 1000) {
      throw new IllegalStateException(
          ZONE
              + " must use "
              + expected
              + " at "
              + timestamp
              + "; update the runtime JDK to include the tzdata 2026b B.C. rules or later.");
    }
  }
}
