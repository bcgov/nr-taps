package ca.bc.gov.nrs.taps.security;

import java.util.Optional;

public enum IdentityProvider {
  IDIR("IDIR", "idir_username", "idir_user_guid"),
  BCEID_BUSINESS("BCEID", "bceid_username", "bceid_user_guid");

  private final String auditPrefix;
  private final String usernameClaim;
  private final String guidClaim;

  IdentityProvider(String auditPrefix, String usernameClaim, String guidClaim) {
    this.auditPrefix = auditPrefix;
    this.usernameClaim = usernameClaim;
    this.guidClaim = guidClaim;
  }

  public static Optional<IdentityProvider> fromClaim(String identityProvider) {
    if (identityProvider == null) {
      return Optional.empty();
    }
    return switch (identityProvider) {
      case "idir", "azureidir" -> Optional.of(IDIR);
      case "bceidbusiness" -> Optional.of(BCEID_BUSINESS);
      default -> Optional.empty();
    };
  }

  public String auditPrefix() {
    return auditPrefix;
  }

  public String usernameClaim() {
    return usernameClaim;
  }

  public String guidClaim() {
    return guidClaim;
  }
}
