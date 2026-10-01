package ca.bc.gov.nrs.taps.security;

import java.util.Optional;

/**
 * The sign-in types TAPS accepts. The CSS integration offers IDIR (through Azure AD, so the realm
 * reports {@code azureidir}) and Business BCeID. Basic and Personal BCeID are not enabled in this
 * proposal. The legacy archives identify BCeID users without recording which BCeID type they used;
 * migration must confirm that Business BCeID covers all external users.
 */
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

  /** Legacy WebADE wrote {@code IDIR\USER} and {@code BCEID\USER}; keep one name per person. */
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
